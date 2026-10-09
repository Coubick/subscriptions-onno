package com.subscriptions.domain.documents;

import com.subscriptions.domain.catalogs.Client;
import com.subscriptions.domain.catalogs.Tariff;
import com.subscriptions.domain.enumerations.SubscriptionStatus;
import org.junit.jupiter.api.Test;
import su.onno.rules.BusinessRule;
import su.onno.types.Ref;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Чистые правила и расчёты Subscription, без Spring. */
class SubscriptionRulesTest {

    private static SubscriptionLine line(String price, int periods, Integer periodDays) {
        SubscriptionLine line = new SubscriptionLine();
        line.setTariff(Ref.of(Tariff.class, UUID.randomUUID()));
        line.setPrice(new BigDecimal(price));
        line.setPeriods(periods);
        line.setPeriodDays(periodDays);
        return line;
    }

    private static Subscription valid() {
        Subscription s = new Subscription();
        s.setClient(Ref.of(Client.class, UUID.randomUUID()));
        s.getLines().add(line("100.00", 1, 30));
        return s;
    }

    private static List<String> failed(Subscription s) {
        return s.rules().stream()
                .filter(r -> !r.condition().getAsBoolean())
                .map(BusinessRule::name)
                .toList();
    }

    @Test
    void validSubscriptionPassesAllRules() {
        assertThat(failed(valid())).isEmpty();
    }

    @Test
    void clientIsRequired() {
        Subscription s = valid();
        s.setClient(null);
        assertThat(failed(s)).hasSize(1);
        assertThat(s.rules().stream().filter(r -> !r.condition().getAsBoolean()).findFirst().orElseThrow().field())
                .isEqualTo("client");
    }

    @Test
    void atLeastOneLineIsRequired() {
        Subscription s = valid();
        s.getLines().clear();
        assertThat(failed(s)).containsExactly("lines-required");
    }

    @Test
    void everyLineNeedsTariff() {
        Subscription s = valid();
        s.getLines().get(0).setTariff(null);
        assertThat(failed(s)).containsExactly("line-tariff-required");
    }

    @Test
    void periodsMustBePositive() {
        Subscription s = valid();
        s.getLines().get(0).setPeriods(0);
        assertThat(failed(s)).containsExactly("line-periods-positive");
    }

    @Test
    void cancellingPostedSubscriptionIsRejected() {
        Subscription s = valid();
        s.setPosted(true);
        s.setStatus(SubscriptionStatus.CANCELLED);
        BusinessRule rule = s.rules().stream().filter(r -> !r.condition().getAsBoolean()).findFirst().orElseThrow();
        assertThat(rule.field()).isEqualTo("status");
        assertThat(rule.message()).isEqualTo("Сначала отмените проведение");
    }

    @Test
    void cancellingUnpostedSubscriptionIsAllowed() {
        Subscription s = valid();
        s.setStatus(SubscriptionStatus.CANCELLED);
        assertThat(failed(s)).isEmpty();
    }

    @Test
    void beforeWriteRecalculatesAmountsTotalAndEndDateByLongestLine() {
        Subscription s = valid();
        s.getLines().clear();
        s.getLines().add(line("1000.00", 1, 365));
        s.getLines().add(line("100.00", 3, 30));
        s.setStartDate(LocalDate.of(2026, 1, 1));

        s.beforeWrite();

        assertThat(s.getLines().get(0).getAmount()).isEqualByComparingTo("1000.00");
        assertThat(s.getLines().get(1).getAmount()).isEqualByComparingTo("300.00");
        assertThat(s.getTotal()).isEqualByComparingTo("1300.00");
        assertThat(s.getEndDate()).isEqualTo(LocalDate.of(2027, 1, 1));
    }
}
