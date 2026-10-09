package com.subscriptions.domain.jobs;

import com.subscriptions.domain.catalogs.Client;
import com.subscriptions.domain.catalogs.ClientRepository;
import com.subscriptions.domain.catalogs.Tariff;
import com.subscriptions.domain.catalogs.TariffRepository;
import com.subscriptions.domain.documents.Payment;
import com.subscriptions.domain.documents.PaymentRepository;
import com.subscriptions.domain.documents.Subscription;
import com.subscriptions.domain.documents.SubscriptionLine;
import com.subscriptions.domain.documents.SubscriptionRepository;
import com.subscriptions.domain.enumerations.PaymentMethod;
import com.subscriptions.domain.enumerations.SubscriptionStatus;
import com.subscriptions.domain.registers.ClientAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import su.onno.events.EntityChangedEvent;
import su.onno.posting.PostingService;
import su.onno.types.Ref;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Задание в настоящем контексте: сохранение статуса, движения не меняются, событие публикуется. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:subscriptions-test;DB_CLOSE_DELAY=-1")
@RecordApplicationEvents
class SubscriptionStatusJobIT {

    @Autowired SubscriptionStatusJob job;
    @Autowired ClientRepository clients;
    @Autowired TariffRepository tariffs;
    @Autowired PaymentRepository payments;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired ClientAccountRepository accountRegister;
    @Autowired PostingService posting;
    @Autowired ApplicationEvents events;

    private Ref<Client> client;
    private Ref<Tariff> tariff;

    @BeforeEach
    void setUp() {
        Client c = new Client();
        c.setDescription("ООО Задание");
        client = Ref.of(Client.class, clients.save(c).getId());

        Tariff t = new Tariff();
        t.setDescription("Месяц");
        t.setPricePerPeriod(new BigDecimal("100.00"));
        t.setPeriodDays(30);
        tariff = Ref.of(Tariff.class, tariffs.save(t).getId());

        Payment p = new Payment();
        p.setClient(client);
        p.setAmount(new BigDecimal("1000.00"));
        p.setMethod(PaymentMethod.CARD);
        posting.post(payments.save(p));
    }

    private Subscription subscription(LocalDate start, boolean post) {
        SubscriptionLine line = new SubscriptionLine();
        line.setTariff(tariff);
        line.setPrice(new BigDecimal("100.00"));
        line.setPeriods(1);
        line.setPeriodDays(30);
        Subscription s = new Subscription();
        s.setClient(client);
        s.setStartDate(start);
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

    @Test
    void activatesThenExpiresWithoutTouchingMovements() {
        LocalDate today = LocalDate.now();
        Subscription s = subscription(today, true);
        var movementsBefore = accountRegister.getRecordsByDocument(s.getId());

        job.updateStatuses(today);
        Subscription activated = reload(s);
        assertThat(activated.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(activated.isPosted()).isTrue();

        job.updateStatuses(today.plusDays(31));
        Subscription expired = reload(s);
        assertThat(expired.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(expired.isPosted()).isTrue();

        assertThat(accountRegister.getRecordsByDocument(s.getId()))
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(movementsBefore);
    }

    @Test
    void savePublishesEntityChangedEvent() {
        Subscription s = subscription(LocalDate.now(), true);
        events.clear();

        job.updateStatuses(LocalDate.now());

        assertThat(events.stream(EntityChangedEvent.class))
                .anyMatch(e -> EntityChangedEvent.UPDATED.equals(e.changeType()) && s.getId().equals(e.id()));
    }

    @Test
    void leavesUnpostedDraftAlone() {
        Subscription s = subscription(LocalDate.now(), false);

        job.updateStatuses(LocalDate.now());

        assertThat(reload(s).getStatus()).isEqualTo(SubscriptionStatus.DRAFT);
    }

    @Test
    void skipsDeletionMarkedSubscriptions() {
        LocalDate today = LocalDate.now();
        Subscription s = subscription(today.minusDays(100), false);
        s.setStatus(SubscriptionStatus.ACTIVE);
        s = subscriptions.save(s);
        subscriptions.delete(s);

        job.updateStatuses(today);

        Subscription deleted = reload(s);
        assertThat(deleted.isDeletionMark()).isTrue();
        assertThat(deleted.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }
}
