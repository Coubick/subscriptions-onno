package com.subscriptions.domain.jobs;

import com.subscriptions.domain.documents.Subscription;
import com.subscriptions.domain.documents.SubscriptionRepository;
import com.subscriptions.domain.enumerations.SubscriptionStatus;
import su.onno.annotations.ScheduledJob;
import su.onno.jobs.BackgroundTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Переводит подписки по датам: «Черновик» → «Активна», когда наступила дата начала, и «Активна» →
 * «Истекла», когда прошла дата окончания. Запускается каждый час, чтобы догнать пропущенную полночь
 * после простоя.
 *
 * <p>Меняет только атрибут статуса: документ сохраняется через репозиторий, проведение не
 * повторяется, движения регистров остаются прежними. {@code repository.save} сам публикует
 * {@code EntityChangedEvent}, поэтому открытые списки в UI обновятся без ручной публикации.</p>
 *
 * <p>Активируются только проведённые подписки: непроведённый черновик не оплачен.</p>
 */
@ScheduledJob(name = "SubscriptionStatus", cron = "0 0 * * * *")
@Component
public class SubscriptionStatusJob implements BackgroundTask {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionStatusJob.class);

    private final SubscriptionRepository subscriptions;

    public SubscriptionStatusJob(SubscriptionRepository subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Override
    public void execute() {
        updateStatuses(LocalDate.now());
    }

    /** Применяет переходы на дату {@code today}; возвращает число изменённых подписок. */
    public int updateStatuses(LocalDate today) {
        int changed = 0;
        for (Subscription s : subscriptions.findAllActive()) {
            SubscriptionStatus next = nextStatus(s, today);
            if (next == s.getStatus()) {
                continue;
            }
            SubscriptionStatus previous = s.getStatus();
            try {
                s.setStatus(next);
                subscriptions.save(s);
                changed++;
                log.info("Подписка {}: {} → {}", s.getNumber(), previous, next);
            } catch (RuntimeException e) {
                // Одна битая подписка не должна останавливать перевод остальных.
                log.warn("Не удалось перевести подписку {} в {}: {}", s.getNumber(), next, e.getMessage());
            }
        }
        return changed;
    }

    /** Чистая функция перехода. Черновик с уже прошедшим сроком сразу становится «Истекла». */
    static SubscriptionStatus nextStatus(Subscription s, LocalDate today) {
        SubscriptionStatus status = s.getStatus();
        if (status == SubscriptionStatus.DRAFT && s.isPosted()
                && s.getStartDate() != null && !s.getStartDate().isAfter(today)) {
            status = SubscriptionStatus.ACTIVE;
        }
        if (status == SubscriptionStatus.ACTIVE && s.getEndDate() != null && s.getEndDate().isBefore(today)) {
            status = SubscriptionStatus.EXPIRED;
        }
        return status;
    }
}
