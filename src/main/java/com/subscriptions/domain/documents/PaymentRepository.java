package com.subscriptions.domain.documents;

import su.onno.repository.DocumentRepository;

/** Репозиторий платежей. Сохранение через него запускает onFilling, beforeWrite и rules. */
public interface PaymentRepository extends DocumentRepository<Payment> {
}
