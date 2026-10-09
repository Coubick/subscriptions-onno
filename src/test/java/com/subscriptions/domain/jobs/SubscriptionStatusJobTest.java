package com.subscriptions.domain.jobs;

import com.subscriptions.domain.documents.Subscription;
import com.subscriptions.domain.enumerations.SubscriptionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.subscriptions.domain.enumerations.SubscriptionStatus.ACTIVE;
import static com.subscriptions.domain.enumerations.SubscriptionStatus.CANCELLED;
import static com.subscriptions.domain.enumerations.SubscriptionStatus.DRAFT;
import static com.subscriptions.domain.enumerations.SubscriptionStatus.EXPIRED;
import static org.assertj.core.api.Assertions.assertThat;

/** Чистая функция перехода статусов, без Spring. */
class SubscriptionStatusJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    private static Subscription sub(SubscriptionStatus status, boolean posted, LocalDate start, LocalDate end) {
        Subscription s = new Subscription();
        s.setStatus(status);
        s.setPosted(posted);
        s.setStartDate(start);
        s.setEndDate(end);
        return s;
    }

    @Test
    void postedDraftBecomesActiveOnStartDate() {
        assertThat(SubscriptionStatusJob.nextStatus(sub(DRAFT, true, TODAY, TODAY.plusDays(30)), TODAY))
                .isEqualTo(ACTIVE);
    }

    @Test
    void draftWithFutureStartStaysDraft() {
        assertThat(SubscriptionStatusJob.nextStatus(sub(DRAFT, true, TODAY.plusDays(1), TODAY.plusDays(31)), TODAY))
                .isEqualTo(DRAFT);
    }

    @Test
    void unpostedDraftIsNotActivated() {
        assertThat(SubscriptionStatusJob.nextStatus(sub(DRAFT, false, TODAY, TODAY.plusDays(30)), TODAY))
                .isEqualTo(DRAFT);
    }

    @Test
    void activeStaysActiveOnEndDateAndExpiresTheDayAfter() {
        Subscription s = sub(ACTIVE, true, TODAY.minusDays(30), TODAY);
        assertThat(SubscriptionStatusJob.nextStatus(s, TODAY)).isEqualTo(ACTIVE);
        assertThat(SubscriptionStatusJob.nextStatus(s, TODAY.plusDays(1))).isEqualTo(EXPIRED);
    }

    @Test
    void postedDraftWithPastPeriodExpiresInOneRun() {
        assertThat(SubscriptionStatusJob.nextStatus(sub(DRAFT, true, TODAY.minusDays(60), TODAY.minusDays(30)), TODAY))
                .isEqualTo(EXPIRED);
    }

    @Test
    void cancelledAndExpiredAreFinal() {
        assertThat(SubscriptionStatusJob.nextStatus(sub(CANCELLED, false, TODAY.minusDays(60), TODAY.minusDays(30)), TODAY))
                .isEqualTo(CANCELLED);
        assertThat(SubscriptionStatusJob.nextStatus(sub(EXPIRED, true, TODAY.minusDays(60), TODAY.minusDays(30)), TODAY))
                .isEqualTo(EXPIRED);
    }
}
