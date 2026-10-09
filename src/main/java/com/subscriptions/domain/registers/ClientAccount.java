package com.subscriptions.domain.registers;

import com.subscriptions.domain.catalogs.Client;
import su.onno.annotations.AccessControl;
import su.onno.annotations.AccumulationRegister;
import su.onno.annotations.Dimension;
import su.onno.annotations.Resource;
import su.onno.model.AccumulationRecord;
import su.onno.model.AccumulationType;
import su.onno.types.Ref;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Лицевой счёт клиента (регистр остатков). Платёж увеличивает остаток, проведённая подписка списывает.
 *
 * <p>{@code allowNegative} не задан намеренно: фреймворк не даст провести документ, если остаток
 * клиента стал бы отрицательным.</p>
 */
@AccumulationRegister(name = "ClientAccount", title = "Лицевой счёт клиентов",
        type = AccumulationType.BALANCE, context = "Subscriptions")
@AccessControl(readRoles = {"MANAGER"})
@Getter
@Setter
public class ClientAccount extends AccumulationRecord {

    @Dimension(displayName = "Клиент")
    private Ref<Client> client;

    @Resource(displayName = "Сумма", precision = 15, scale = 2)
    private BigDecimal amount;
}
