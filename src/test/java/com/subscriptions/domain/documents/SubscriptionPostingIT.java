package com.subscriptions.domain.documents;

import com.subscriptions.domain.catalogs.Client;
import com.subscriptions.domain.catalogs.ClientRepository;
import com.subscriptions.domain.catalogs.Tariff;
import com.subscriptions.domain.catalogs.TariffRepository;
import com.subscriptions.domain.enumerations.PaymentMethod;
import com.subscriptions.domain.enumerations.SubscriptionStatus;
import com.subscriptions.domain.registers.AccountBalanceService;
import com.subscriptions.domain.registers.ClientAccountRepository;
import com.subscriptions.domain.registers.TariffRevenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import su.onno.posting.PostingService;
import su.onno.types.Ref;
import su.onno.validation.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Хуки Subscription и Payment в настоящем контексте: сохранение через репозиторий и проведение. */
// Своя in-memory БД вместо файла ./data, чтобы тесты не трогали данные разработчика.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:subscriptions-test;DB_CLOSE_DELAY=-1")
class SubscriptionPostingIT {

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
        c.setDescription("ООО Тест");
        client = Ref.of(Client.class, clients.save(c).getId());

        Tariff t = new Tariff();
        t.setDescription("Базовый");
        t.setPricePerPeriod(new BigDecimal("100.00"));
        t.setPeriodDays(30);
        tariff = Ref.of(Tariff.class, tariffs.save(t).getId());
    }

    private void pay(String amount) {
        Payment p = new Payment();
        p.setClient(client);
        p.setAmount(new BigDecimal(amount));
        p.setMethod(PaymentMethod.CARD);
        posting.post(payments.save(p));
    }

    private Subscription saveSubscription(String price, int periods) {
        SubscriptionLine line = new SubscriptionLine();
        line.setTariff(tariff);
        line.setPrice(new BigDecimal(price));
        line.setPeriods(periods);
        line.setPeriodDays(30);
        Subscription s = new Subscription();
        s.setClient(client);
        s.getLines().add(line);
        return subscriptions.save(s);
    }

    private Subscription reload(Subscription s) {
        return subscriptions.findById(s.getId()).orElseThrow();
    }

    private Ref<Tariff> tariffWithAvailability(Boolean available) {
        Tariff t = new Tariff();
        t.setDescription("Тариф " + available);
        t.setPricePerPeriod(new BigDecimal("100.00"));
        t.setPeriodDays(30);
        t.setAvailableForConnection(available);
        return Ref.of(Tariff.class, tariffs.save(t).getId());
    }

    private Subscription subscriptionOn(Ref<Tariff> onTariff) {
        SubscriptionLine line = new SubscriptionLine();
        line.setTariff(onTariff);
        line.setPeriods(1);
        Subscription s = new Subscription();
        s.setClient(client);
        s.getLines().add(line);
        return s;
    }

    @Test
    void unavailableTariffIsRejected() {
        Subscription s = subscriptionOn(tariffWithAvailability(false));

        assertThatThrownBy(() -> subscriptions.save(s))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("недоступен для подключения");
    }

    @Test
    void tariffWithoutAvailabilityFlagCannotBeSaved() {
        // NULL в БД репозиторий прочитал бы как true (инициализатор), поэтому флаг обязателен.
        assertThatThrownBy(() -> tariffWithAvailability(null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Доступен для подключения");
    }

    @Test
    void availableTariffPasses() {
        Subscription saved = subscriptions.save(subscriptionOn(tariffWithAvailability(true)));

        assertThat(reload(saved).getTotal()).isEqualByComparingTo("100.00");
    }

    @Test
    void withdrawingTariffDoesNotBlockSavingPostedSubscription() {
        pay("100.00");
        Ref<Tariff> onTariff = tariffWithAvailability(true);
        Subscription s = subscriptions.save(subscriptionOn(onTariff));
        posting.post(s);

        Tariff t = tariffs.findById(onTariff.id()).orElseThrow();
        t.setAvailableForConnection(false);
        tariffs.save(t);

        Subscription posted = reload(s);
        posted.setStatus(SubscriptionStatus.EXPIRED);
        subscriptions.save(posted);
        assertThat(reload(s).getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void saveSubstitutesPriceAndPeriodFromTariff() {
        SubscriptionLine line = new SubscriptionLine();
        line.setTariff(tariff);
        line.setPeriods(2);
        Subscription s = new Subscription();
        s.setClient(client);
        s.getLines().add(line);

        Subscription saved = reload(subscriptions.save(s));

        SubscriptionLine stored = saved.getLines().get(0);
        assertThat(stored.getPrice()).isEqualByComparingTo("100.00");
        assertThat(stored.getPeriodDays()).isEqualTo(30);
        assertThat(stored.getAmount()).isEqualByComparingTo("200.00");
        assertThat(saved.getTotal()).isEqualByComparingTo("200.00");
        assertThat(saved.getEndDate()).isEqualTo(saved.getStartDate().plusDays(60));
    }

    @Test
    void draftFollowsTariffButPostedKeepsSnapshot() {
        pay("1000.00");
        Subscription draft = saveSubscription("100.00", 1);
        Subscription posted = saveSubscription("100.00", 1);
        posting.post(posted);

        Tariff t = tariffs.findById(tariff.id()).orElseThrow();
        t.setPricePerPeriod(new BigDecimal("150.00"));
        tariffs.save(t);

        assertThat(reload(subscriptions.save(reload(draft))).getTotal()).isEqualByComparingTo("150.00");

        posting.repost(reload(posted));
        assertThat(reload(posted).getTotal()).isEqualByComparingTo("100.00");
        assertThat(balances.balanceOf(client)).isEqualByComparingTo("900.00");
    }

    @Test
    void saveRunsOnFillingAndBeforeWrite() {
        Subscription s = reload(saveSubscription("100.00", 3));

        assertThat(s.getDate()).isNotNull();
        assertThat(s.getStartDate()).isEqualTo(LocalDate.now());
        assertThat(s.getTotal()).isEqualByComparingTo("300.00");
        assertThat(s.getEndDate()).isEqualTo(LocalDate.now().plusDays(90));
    }

    @Test
    void saveRejectsBrokenRules() {
        Subscription s = new Subscription();
        s.setClient(client);
        assertThatThrownBy(() -> subscriptions.save(s)).isInstanceOf(ValidationException.class);
    }

    @Test
    void paymentTopsUpAndSubscriptionWritesOffAndRecognisesRevenue() {
        pay("500.00");
        Subscription s = saveSubscription("100.00", 3);

        posting.post(s);

        assertThat(balances.balanceOf(client)).isEqualByComparingTo("200.00");
        assertThat(revenueRegister.getRecordsByDocument(s.getId()))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getTariff()).isEqualTo(tariff);
                    assertThat(r.getAmount()).isEqualByComparingTo("300.00");
                    assertThat(r.getPeriods()).isEqualByComparingTo("3");
                });
    }

    @Test
    void beforePostRejectsInsufficientFundsWithValidationError() {
        pay("100.00");
        Subscription s = saveSubscription("100.00", 3);

        assertThatThrownBy(() -> posting.post(s))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Недостаточно средств");
        assertThat(balances.balanceOf(client)).isEqualByComparingTo("100.00");
    }

    @Test
    void repostOfFullySpentBalanceIsNotFalselyRejected() {
        pay("300.00");
        Subscription s = saveSubscription("100.00", 3);
        posting.post(s);
        assertThat(balances.balanceOf(client)).isEqualByComparingTo("0.00");

        Subscription posted = reload(s);
        assertThat(balances.sumMovementsOfDocument(client, posted.getId())).isEqualByComparingTo("300.00");
        posting.repost(posted);

        assertThat(balances.balanceOf(client)).isEqualByComparingTo("0.00");
    }

    @Test
    void cancelledSubscriptionCreatesNoMovements() {
        Subscription s = saveSubscription("100.00", 3);
        s.setStatus(SubscriptionStatus.CANCELLED);
        s = subscriptions.save(s);

        posting.post(s);

        assertThat(accountRegister.getRecordsByDocument(s.getId())).isEmpty();
        assertThat(revenueRegister.getRecordsByDocument(s.getId())).isEmpty();
    }

    @Test
    void postedSubscriptionCannotBeCancelledBySave() {
        pay("300.00");
        Subscription s = saveSubscription("100.00", 3);
        posting.post(s);

        Subscription posted = reload(s);
        assertThat(posted.isPosted()).isTrue();
        posted.setStatus(SubscriptionStatus.CANCELLED);

        assertThatThrownBy(() -> subscriptions.save(posted))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Сначала отмените проведение");
        assertThat(balances.balanceOf(client)).isEqualByComparingTo("0.00");
    }

    @Test
    void unpostThenCancelIsAllowed() {
        pay("300.00");
        Subscription s = saveSubscription("100.00", 3);
        posting.post(s);
        posting.unpost(reload(s));

        Subscription unposted = reload(s);
        unposted.setStatus(SubscriptionStatus.CANCELLED);
        subscriptions.save(unposted);

        assertThat(reload(s).getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(balances.balanceOf(client)).isEqualByComparingTo("300.00");
    }
}
