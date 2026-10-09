package com.subscriptions.domain.documents;

import com.subscriptions.domain.enumerations.SubscriptionStatus;
import su.onno.posting.PostingService;
import su.onno.validation.ValidationException;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Отмена подписки с указанием причины. Проведённую подписку сначала распроводит: деньги возвращаются
 * на лицевой счёт, выручка сторнируется. Затем ставит статус «Отменена» и записывает причину в примечание.
 *
 * <p>Проведение и сохранение — разные транзакции (проведение onno идёт отдельной транзакцией JDBI),
 * поэтому шаги идут последовательно: сначала отмена проведения фиксируется, потом сохраняется статус.</p>
 */
@Service
public class SubscriptionCancellation {

    private final SubscriptionRepository subscriptions;
    private final PostingService posting;

    public SubscriptionCancellation(SubscriptionRepository subscriptions, PostingService posting) {
        this.subscriptions = subscriptions;
        this.posting = posting;
    }

    /** Подписку можно отменить, пока она не отменена и не истекла. */
    public static boolean cancellable(SubscriptionStatus status) {
        return status != SubscriptionStatus.CANCELLED && status != SubscriptionStatus.EXPIRED;
    }

    public Subscription cancel(UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("Укажите причину отмены", "reason");
        }
        Subscription subscription = load(id);
        if (!cancellable(subscription.getStatus())) {
            throw new ValidationException("Отменить можно только черновик или активную подписку");
        }
        if (subscription.isPosted()) {
            posting.unpost(subscription);
            subscription = load(id);
        }
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setComment(reason.strip());
        return subscriptions.save(subscription);
    }

    private Subscription load(UUID id) {
        return subscriptions.findActiveById(id)
                .orElseThrow(() -> new ValidationException("Подписка не найдена или удалена"));
    }
}
