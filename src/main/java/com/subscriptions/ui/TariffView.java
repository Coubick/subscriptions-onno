package com.subscriptions.ui;

import com.subscriptions.domain.catalogs.Tariff;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;
import su.onno.ui.ListSpec;

import org.springframework.stereotype.Component;

/** Справочник тарифов. Недоступные для подключения тарифы приглушены в списке. */
@Component
public class TariffView implements EntityView<Tariff> {

    @Override
    public Class<Tariff> entity() {
        return Tariff.class;
    }

    @Override
    public void list(ListSpec<Tariff> list) {
        list.columns(Tariff::getCode, Tariff::getDescription, Tariff::getPricePerPeriod,
                        Tariff::getPeriodDays, Tariff::getAvailableForConnection)
                .label(Tariff::getCode, "Код")
                .label(Tariff::getDescription, "Тариф")
                .label(Tariff::getPricePerPeriod, "Цена за период")
                .label(Tariff::getPeriodDays, "Период, дн.")
                .label(Tariff::getAvailableForConnection, "Доступен")
                .sortBy(Tariff::getDescription);

        list.rowStyle(row -> row.bool(Tariff::getAvailableForConnection) ? null : ListSpec.RowStyle.MUTED);
    }

    @Override
    public void fields(EntityConfigBuilder<Tariff> f) {
        f.field(Tariff::getCode).label("Код").order(0).width("half")
                .hint("Присваивается автоматически при создании.");
        f.field(Tariff::getDescription).label("Наименование").order(10).width("half")
                .placeholder("Например, «Стандарт»");
        f.field(Tariff::getPricePerPeriod).order(20).width("half").format("currency:RUB")
                .placeholder("0.00")
                .hint("Подставляется в строки непроведённых подписок при сохранении.");
        f.field(Tariff::getPeriodDays).order(30).width("half").placeholder("30")
                .hint("Срок одного периода. Срок подписки — максимум «дней × периодов» среди её строк.");
        f.field(Tariff::getAvailableForConnection).order(40).widget("switch")
                .hint("Снимите, чтобы прекратить продажу тарифа.");
    }
}
