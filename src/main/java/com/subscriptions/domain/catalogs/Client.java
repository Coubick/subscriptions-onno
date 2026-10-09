package com.subscriptions.domain.catalogs;

import com.subscriptions.domain.enumerations.ClientStatus;
import su.onno.annotations.AccessControl;
import su.onno.annotations.Attribute;
import su.onno.annotations.Catalog;
import su.onno.lifecycle.OnFillingHandler;
import su.onno.model.CatalogObject;
import su.onno.rules.BusinessRule;
import su.onno.rules.Validated;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * Клиент компании: владелец лицевого счёта, плательщик и покупатель подписок.
 * Наименование клиента хранится во встроенном поле {@code description}.
 */
@Catalog(name = "Clients", title = "Клиенты", codePrefix = "CL-", context = "Subscriptions")
@AccessControl(readRoles = {"MANAGER"}, writeRoles = {"MANAGER"})
@Getter
@Setter
public class Client extends CatalogObject implements OnFillingHandler, Validated {

    @Attribute(displayName = "Статус", required = true)
    private ClientStatus status = ClientStatus.LEAD;

    @Attribute(displayName = "E-mail", length = 200, email = true)
    private String email;

    @Attribute(displayName = "Телефон", length = 50)
    private String phone;

    @Attribute(displayName = "Дата регистрации")
    private LocalDate registrationDate;

    /** Дата регистрации подставляется при создании. Идемпотентно: заданное значение не затираем. */
    @Override
    public void onFilling() {
        if (registrationDate == null) {
            registrationDate = LocalDate.now();
        }
    }

    @Override
    public List<BusinessRule> rules() {
        return List.of(BusinessRule.onField("description", "Укажите наименование клиента",
                () -> getDescription() != null && !getDescription().isBlank()));
    }
}
