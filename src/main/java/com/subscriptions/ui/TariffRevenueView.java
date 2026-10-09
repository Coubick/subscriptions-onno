package com.subscriptions.ui;

import com.subscriptions.domain.registers.TariffRevenue;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;

import org.springframework.stereotype.Component;

/**
 * Отчёт «Выручка по тарифам» (обороты). В onno 3.4.1 отчёт регистра берёт из {@link #fields} подписи,
 * формат и порядок измерений и ресурсов, а также формат периода. Подпись колонки периода, подсказки
 * и {@code list()} для регистров не применяются.
 */
@Component
public class TariffRevenueView implements EntityView<TariffRevenue> {

    @Override
    public Class<TariffRevenue> entity() {
        return TariffRevenue.class;
    }

    @Override
    public void fields(EntityConfigBuilder<TariffRevenue> f) {
        f.field("period").format("dd-MM-yyyy");
        f.field(TariffRevenue::getTariff).label("Тариф").order(0);
        f.field(TariffRevenue::getClient).label("Клиент").order(10);
        f.field(TariffRevenue::getAmount).label("Сумма").order(20).format("currency:RUB");
        f.field(TariffRevenue::getPeriods).label("Периодов").order(30).format("integer");
    }
}
