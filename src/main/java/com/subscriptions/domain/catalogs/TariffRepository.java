package com.subscriptions.domain.catalogs;

import su.onno.repository.CatalogRepository;

/** Репозиторий справочника тарифов: подстановка цены в подписку и проверка доступности тарифа. */
public interface TariffRepository extends CatalogRepository<Tariff> {
}
