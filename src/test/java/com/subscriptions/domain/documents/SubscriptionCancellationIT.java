package com.subscriptions.domain.documents;

import com.subscriptions.domain.catalogs.Client;
import com.subscriptions.domain.catalogs.ClientRepository;
import com.subscriptions.domain.catalogs.Tariff;
import com.subscriptions.domain.catalogs.TariffRepository;
import com.subscriptions.domain.enumerations.PaymentMethod;
import com.subscriptions.domain.enumerations.SubscriptionStatus;
import com.subscriptions.domain.registers.AccountBalanceService;
import com.subscriptions.domain.registers.ClientAccountRepository;
import com.subscriptions.domain.registers.TariffRevenue;
import com.subscriptions.domain.registers.TariffRevenueRepository;
import com.subscriptions.ui.SubscriptionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import su.onno.posting.PostingService;
import su.onno.repository.EnumerationPersistence;
import su.onno.types.Ref;
import su.onno.ui.ActionContext;
import su.onno.ui.ActionRejectedException;
import su.onno.ui.ActionResult;
import su.onno.ui.ActionRow;
import su.onno.ui.ActionScope;
import su.onno.ui.ActionSpec;
import su.onno.ui.InputSpec;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Действия «Отменить подписку» из SubscriptionView: вызываются настоящие обработчики ActionSpec. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:subscriptions-test;DB_CLOSE_DELAY=-1")
class SubscriptionCancellationIT {

    @Autowired SubscriptionView view;
    @Autowired ClientRepository clients;
    @Autowired TariffRepository tariffs;
    @Autowired PaymentRepository payments;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired ClientAccountRepository accountRegister;
    @Autowired TariffRevenueRepository revenueRegister;
    @Autowired AccountBalanceService balances;
    @Autowired PostingService posting;

    private Ref<Client> client;
    private Ref<Tariff> tariff;

    @BeforeEach
    void setUp() {
        Client c = new Client();
        c.setDescription("ООО Отмена");
        client = Ref.of(Client.class, clients.save(c).getId());

        Tariff t = new Tariff();
        t.setDescription("Отменяемый");
        t.setPricePerPeriod(new BigDecimal("100.00"));
        t.setPeriodDays(30);
        tariff = Ref.of(Tariff.class, tariffs.save(t).getId());

        Payment p = new Payment();
        p.setClient(client);
        p.setAmount(new BigDecimal("500.00"));
        p.setMethod(PaymentMethod.CARD);
        posting.post(payments.save(p));
    }

    private Subscription subscription(boolean post) {
        SubscriptionLine line = new SubscriptionLine();
        line.setTariff(tariff);
        line.setPeriods(3);
        Subscription s = new Subscription();
        s.setClient(client);
        s.getLines().add(line);
        s = subscriptions.save(s);
        if (post) {
            posting.post(s);
        }
        return reload(s);
    }

    private Subscription reload(Subscription s) {
        return subscriptions.findById(s.getId()).orElseThrow();
    }

    private ActionSpec.Action action(String key) {
        ActionSpec spec = new ActionSpec();
        view.actions(spec);
        return spec.actions().stream().filter(a -> a.key().equals(key)).findFirst().orElseThrow();
    }

    private ActionResult run(String key, Subscription s, String reason) {
        ActionContext ctx = new ActionContext("document", "Subscriptions", s.getId(), "admin",
                reason == null ? Map.of() : Map.of("reason", reason));
        return action(key).handler().apply(ctx);
    }

    private static ActionRow rowWithStatus(SubscriptionStatus status) {
        UUID id = EnumerationPersistence.resolveId(SubscriptionStatus.class, status);
        return new ActionRow(Map.of("status", id.toString()));
    }

    @Test
    void cancellingPostedSubscriptionRefundsReversesRevenueAndSetsStatus() {
        Subscription s = subscription(true);
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now().plusDays(1);
        assertThat(balances.balanceOf(client)).isEqualByComparingTo("200.00");
        assertThat(revenueRegister.getTurnover(from, to, f -> f.where(
                TariffRevenue::getTariff, tariff))).isNotEmpty();

        ActionResult result = run("cancel", s, "Клиент передумал");

        assertThat(result.refresh()).isTrue();
        Subscription cancelled = reload(s);
        assertThat(cancelled.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(cancelled.isPosted()).isFalse();
        assertThat(cancelled.getComment()).isEqualTo("Клиент передумал");
        assertThat(balances.balanceOf(client)).isEqualByComparingTo("500.00");
        assertThat(accountRegister.getRecordsByDocument(s.getId())).noneMatch(r -> r.isActive());
        assertThat(revenueRegister.getRecordsByDocument(s.getId())).noneMatch(r -> r.isActive());
        assertThat(revenueRegister.getTurnover(from, to, f -> f.where(
                TariffRevenue::getTariff, tariff))).isEmpty();
        // Цены отменённой подписки не пересчитываются по тарифу.
        assertThat(cancelled.getTotal()).isEqualByComparingTo("300.00");
    }

    @Test
    void detailActionCancelsUnpostedDraft() {
        Subscription s = subscription(false);

        run("cancelTop", s, "Ошибка оформления");

        Subscription cancelled = reload(s);
        assertThat(cancelled.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(cancelled.getComment()).isEqualTo("Ошибка оформления");
        assertThat(balances.balanceOf(client)).isEqualByComparingTo("500.00");
    }

    @Test
    void cancellingDraftOnWithdrawnTariffIsAllowed() {
        Subscription s = subscription(false);
        Tariff t = tariffs.findById(tariff.id()).orElseThrow();
        t.setAvailableForConnection(false);
        tariffs.save(t);

        run("cancel", s, "Тариф снят с продажи");

        assertThat(reload(s).getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void blankReasonIsRejectedAndNothingChanges() {
        Subscription s = subscription(true);

        assertThatThrownBy(() -> run("cancel", s, "   "))
                .isInstanceOf(ActionRejectedException.class);

        Subscription unchanged = reload(s);
        assertThat(unchanged.getStatus()).isEqualTo(SubscriptionStatus.DRAFT);
        assertThat(unchanged.isPosted()).isTrue();
        assertThat(balances.balanceOf(client)).isEqualByComparingTo("200.00");
    }

    @Test
    void expiredSubscriptionCannotBeCancelledEvenIfButtonBypassed() {
        Subscription s = subscription(true);
        s.setStatus(SubscriptionStatus.EXPIRED);
        subscriptions.save(s);

        assertThatThrownBy(() -> run("cancel", s, "Поздно"))
                .isInstanceOf(ActionRejectedException.class);

        assertThat(reload(s).getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(reload(s).isPosted()).isTrue();
    }

    @Test
    void actionsAreDeclaredWithScopesFormAndVisibility() {
        ActionSpec.Action row = action("cancel");
        ActionSpec.Action detail = action("cancelTop");
        assertThat(row.scope()).isEqualTo(ActionScope.ROW);
        assertThat(detail.scope()).isEqualTo(ActionScope.DETAIL);

        for (ActionSpec.Action a : new ActionSpec.Action[] {row, detail}) {
            InputSpec.InputField reason = a.form().get(0);
            assertThat(reason.key()).isEqualTo("reason");
            assertThat(reason.required()).isTrue();
            assertThat(a.visibleFn().test(rowWithStatus(SubscriptionStatus.DRAFT))).isTrue();
            assertThat(a.visibleFn().test(rowWithStatus(SubscriptionStatus.ACTIVE))).isTrue();
            assertThat(a.visibleFn().test(rowWithStatus(SubscriptionStatus.CANCELLED))).isFalse();
            assertThat(a.visibleFn().test(rowWithStatus(SubscriptionStatus.EXPIRED))).isFalse();
        }
    }
}
