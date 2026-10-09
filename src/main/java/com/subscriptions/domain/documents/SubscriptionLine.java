package com.subscriptions.domain.documents;

import com.subscriptions.domain.catalogs.Tariff;
import su.onno.annotations.Attribute;
import su.onno.model.TabularSectionRow;
import su.onno.types.Ref;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Строка подписки: тариф, купленный на N периодов. Отдельный конкретный класс для табличной части
 * {@link Subscription#getLines()}: Spring Data JDBC отображает класс строки в одну дочернюю таблицу,
 * поэтому повторно использовать его в другом документе нельзя.
 *
 * <p>{@code amount} и {@code periodDays} производные. Сумму и итог считает
 * {@link Subscription#beforeWrite()}, вручную их вводить не нужно.</p>
 */
@Getter
@Setter
public class SubscriptionLine extends TabularSectionRow {

    @Attribute(displayName = "Тариф", required = true)
    private Ref<Tariff> tariff;

    @Attribute(displayName = "Периодов", precision = 6, scale = 0, min = 1)
    private Integer periods = 1;

    /** Цена за один период. У непроведённой подписки подставляется из тарифа при сохранении. */
    @Attribute(displayName = "Цена", precision = 15, scale = 2, min = 0)
    private BigDecimal price = BigDecimal.ZERO;

    @Attribute(displayName = "Сумма", precision = 15, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    /**
     * Снимок длительности одного периода тарифа в днях: подставляется из тарифа вместе с ценой, чтобы
     * проведённая подписка не зависела от последующих правок тарифа.
     */
    @Attribute(displayName = "Дней в периоде", precision = 6, scale = 0, min = 1)
    private Integer periodDays;
}
