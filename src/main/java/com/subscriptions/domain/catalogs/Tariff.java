package com.subscriptions.domain.catalogs;

import su.onno.annotations.AccessControl;
import su.onno.annotations.Attribute;
import su.onno.annotations.Catalog;
import su.onno.model.CatalogObject;
import su.onno.rules.BusinessRule;
import su.onno.rules.Validated;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Тарифный план. Цена и длительность задаются за один период; сколько периодов купить,
 * решает строка подписки. Наименование тарифа хранится во встроенном поле {@code description}.
 */
@Catalog(name = "Tariffs", title = "Тарифы", codePrefix = "TR-", context = "Subscriptions")
@AccessControl(readRoles = {"MANAGER"}, writeRoles = {"MANAGER"})
@Getter
@Setter
public class Tariff extends CatalogObject implements Validated {

    @Attribute(displayName = "Цена за период", required = true, precision = 15, scale = 2, min = 0)
    private BigDecimal pricePerPeriod;

    @Attribute(displayName = "Длительность периода, дн.", required = true, min = 1)
    private Integer periodDays;

    /**
     * Обязательный: создание через REST без этого поля не применяет инициализатор и записало бы NULL.
     * Хуже того, репозиторий читает NULL как {@code true}: Spring Data не перезаписывает инициализатор
     * значением {@code null}. {@code Boolean}, а не {@code boolean}, чтобы пропуск поля ловила проверка
     * {@code required}, а правило подписки сравнивало через {@code Boolean.TRUE.equals}.
     */
    @Attribute(displayName = "Доступен для подключения", required = true)
    private Boolean availableForConnection = true;

    @Override
    public List<BusinessRule> rules() {
        return List.of(BusinessRule.onField("description", "Укажите наименование тарифа",
                () -> getDescription() != null && !getDescription().isBlank()));
    }
}
