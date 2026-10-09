package com.subscriptions.domain.registers;

import com.subscriptions.domain.catalogs.Client;
import com.subscriptions.domain.catalogs.Tariff;
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
 * Выручка по тарифам (регистр оборотов): сколько денег и периодов принёс каждый тариф по каждому клиенту
 * за выбранный интервал дат.
 */
@AccumulationRegister(name = "TariffRevenue", title = "Выручка по тарифам",
        type = AccumulationType.TURNOVER, context = "Subscriptions")
@AccessControl(readRoles = {"MANAGER"})
@Getter
@Setter
public class TariffRevenue extends AccumulationRecord {

    @Dimension(displayName = "Тариф")
    private Ref<Tariff> tariff;

    @Dimension(displayName = "Клиент")
    private Ref<Client> client;

    @Resource(displayName = "Сумма", precision = 15, scale = 2)
    private BigDecimal amount;

    /** Число проданных периодов. Целое, но ресурс регистра хранится как BigDecimal. */
    @Resource(displayName = "Периодов", precision = 15, scale = 0)
    private BigDecimal periods;
}
