package com.subscriptions.domain.documents;

import com.subscriptions.domain.catalogs.Client;
import com.subscriptions.domain.enumerations.SubscriptionStatus;
import su.onno.annotations.AccessControl;
import su.onno.annotations.Attribute;
import su.onno.annotations.Document;
import su.onno.annotations.TabularSection;
import com.subscriptions.domain.registers.AccountBalanceService;
import com.subscriptions.domain.registers.ClientAccount;
import com.subscriptions.domain.registers.TariffRevenue;
import com.subscriptions.support.SpringBeans;
import su.onno.lifecycle.BeforePostHandler;
import su.onno.lifecycle.BeforeWriteHandler;
import su.onno.validation.ValidationException;
import su.onno.lifecycle.OnFillingHandler;
import su.onno.lifecycle.Postable;
import su.onno.model.DocumentObject;
import su.onno.posting.PostingContext;
import su.onno.rules.BusinessRule;
import su.onno.rules.Validated;
import su.onno.types.Ref;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Подписка клиента на один или несколько тарифов. Каждая строка - тариф, купленный на N периодов.
 *
 * <ul>
 *   <li>{@link OnFillingHandler}: дата документа и дата начала при создании.</li>
 *   <li>{@link BeforeWriteHandler}: у непроведённой подписки подставляет в строки цену и длительность периода
 *       из тарифа ({@link SubscriptionPricing}); затем при каждом сохранении пересчитывает суммы строк, итог
 *       и дату окончания. Срок действия равен дате начала плюс максимальный срок среди строк
 *       (дней в периоде × периодов).</li>
 *   <li>{@link Validated}: клиент обязателен, нужна хотя бы одна строка, у строки заданы тариф и число
 *       периодов больше нуля, тариф доступен для подключения (до проведения); проведённую подписку нельзя перевести в «Отменена» без отмены проведения.</li>
 *   <li>{@link BeforePostHandler}: проверяет, что на лицевом счёте хватает средств.</li>
 *   <li>{@link Postable}: списывает сумму с {@link ClientAccount} и пишет выручку в {@link TariffRevenue}.</li>
 * </ul>
 *
 * <p>Хуки создаёт фреймворк, а не Spring, поэтому сервисы берутся через {@link SpringBeans}.</p>
 */
@Document(name = "Subscriptions", title = "Подписки", numberPrefix = "SUB-", context = "Subscriptions")
@AccessControl(readRoles = {"MANAGER"}, writeRoles = {"MANAGER"})
@Getter
@Setter
public class Subscription extends DocumentObject
        implements OnFillingHandler, BeforeWriteHandler, BeforePostHandler, Validated, Postable {

    @Attribute(displayName = "Клиент", required = true)
    private Ref<Client> client;

    @Attribute(displayName = "Статус", required = true)
    private SubscriptionStatus status = SubscriptionStatus.DRAFT;

    @Attribute(displayName = "Дата начала")
    private LocalDate startDate;

    @Attribute(displayName = "Дата окончания")
    private LocalDate endDate;

    @Attribute(displayName = "Итого", precision = 15, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Attribute(displayName = "Примечание", length = 1000)
    private String comment;

    @TabularSection(name = "lines")
    private List<SubscriptionLine> lines = new ArrayList<>();

    /** Идемпотентно: onFilling выполняется при каждом сохранении нового документа. */
    @Override
    public void onFilling() {
        if (getDate() == null) {
            setDate(LocalDateTime.now());
        }
        if (startDate == null) {
            startDate = LocalDate.now();
        }
    }

    /**
     * Проведённая подписка уже оплачена: её цены — снимок на момент проведения, и смена цены тарифа
     * их не трогает. Отменённая тоже сохраняет цены, по которым её оформляли. Остальные следуют
     * за тарифом при каждом сохранении.
     */
    @Override
    public void beforeWrite() {
        if (!isPosted() && status != SubscriptionStatus.CANCELLED) {
            SpringBeans.get(SubscriptionPricing.class).applyTariffs(lines);
        }
        recalculate();
    }

    /** Суммы строк, итог и дата окончания. Чистый расчёт без обращения к справочникам. */
    void recalculate() {
        BigDecimal sum = BigDecimal.ZERO;
        long maxDays = 0;
        for (SubscriptionLine line : lines) {
            int periods = line.getPeriods() == null ? 0 : line.getPeriods();
            BigDecimal price = line.getPrice() == null ? BigDecimal.ZERO : line.getPrice();
            BigDecimal amount = price.multiply(BigDecimal.valueOf(periods));
            line.setAmount(amount);
            sum = sum.add(amount);
            if (line.getPeriodDays() != null) {
                maxDays = Math.max(maxDays, (long) line.getPeriodDays() * periods);
            }
        }
        this.total = sum;
        // Дату окончания можно посчитать, только если известны дата начала и срок хотя бы одной строки.
        this.endDate = startDate != null && maxDays > 0 ? startDate.plusDays(maxDays) : null;
    }

    @Override
    public List<BusinessRule> rules() {
        return List.of(
                BusinessRule.onField("client", "Выберите клиента", () -> client != null),
                new BusinessRule("lines-required", "Добавьте хотя бы одну строку",
                        () -> lines != null && !lines.isEmpty()),
                new BusinessRule("line-tariff-required", "В каждой строке должен быть выбран тариф",
                        () -> lines.stream().allMatch(l -> l.getTariff() != null)),
                new BusinessRule("line-periods-positive", "Число периодов в каждой строке должно быть больше нуля",
                        () -> lines.stream().allMatch(l -> l.getPeriods() != null && l.getPeriods() > 0)),
                // Проверяется только при оформлении: снятие тарифа с продажи не должно блокировать
                // сохранение оплаченных (например, перевод в «Истекла» заданием) и отменённых подписок.
                new BusinessRule("line-tariff-available", "Тариф в строке недоступен для подключения",
                        () -> isPosted() || status == SubscriptionStatus.CANCELLED
                                || SpringBeans.get(SubscriptionPricing.class).allTariffsAvailable(lines)),
                // Обычное сохранение не перепроводит документ: движения проведённой подписки остались бы
                // в регистрах. Отменить можно только после отмены проведения.
                BusinessRule.onField("status", "Сначала отмените проведение",
                        () -> !(isPosted() && status == SubscriptionStatus.CANCELLED)));
    }

    /**
     * Проверка средств до проведения. Без неё нехватка денег дала бы {@code IllegalStateException} из
     * регистра и HTTP 500; {@link ValidationException} превращается в 400 с понятным текстом.
     * Фреймворковая проверка отрицательного остатка остаётся как страховка от гонки.
     *
     * <p>При перепроведении собственное списание документа ещё в остатке и будет сторнировано,
     * поэтому доступная сумма равна остатку плюс это списание.</p>
     */
    @Override
    public void beforePost() {
        if (status == SubscriptionStatus.CANCELLED || client == null || total == null) {
            return;
        }
        AccountBalanceService balances = SpringBeans.get(AccountBalanceService.class);
        BigDecimal available = balances.balanceOf(client).add(balances.sumMovementsOfDocument(client, getId()));
        if (available.compareTo(total) < 0) {
            throw new ValidationException("Недостаточно средств на лицевом счёте");
        }
    }

    /**
     * Проведение: списание {@code total} с лицевого счёта клиента и выручка по каждой строке.
     * Отменённая подписка движений не создаёт.
     */
    @Override
    public void handlePosting(PostingContext context) {
        if (status == SubscriptionStatus.CANCELLED) {
            return;
        }
        context.movements(ClientAccount.class).addExpense(r -> {
            r.setClient(client);
            r.setAmount(total);
        });
        var revenue = context.movements(TariffRevenue.class);
        for (SubscriptionLine line : lines) {
            revenue.addReceipt(r -> {
                r.setTariff(line.getTariff());
                r.setClient(client);
                r.setAmount(line.getAmount());
                r.setPeriods(BigDecimal.valueOf(line.getPeriods()));
            });
        }
    }
}
