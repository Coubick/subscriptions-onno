package com.subscriptions.domain.catalogs;

import su.onno.repository.CatalogRepository;

/** Репозиторий справочника клиентов. Сохранение через него запускает onFilling, beforeWrite и rules. */
public interface ClientRepository extends CatalogRepository<Client> {
}
