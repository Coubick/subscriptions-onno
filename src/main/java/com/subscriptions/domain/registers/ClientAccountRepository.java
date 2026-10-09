package com.subscriptions.domain.registers;

import su.onno.repository.RegisterRepository;

/**
 * Типизированный репозиторий регистра лицевого счёта. Реализацию генерирует onno по объявленному
 * интерфейсу. Контракт включает остатки, обороты, движения документа и запись движений, но вне
 * проведения код приложения только читает: движения пишет {@code handlePosting}.
 */
public interface ClientAccountRepository extends RegisterRepository<ClientAccount> {
}
