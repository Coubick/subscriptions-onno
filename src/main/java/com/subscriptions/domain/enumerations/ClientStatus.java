package com.subscriptions.domain.enumerations;

import su.onno.annotations.EnumLabel;
import su.onno.annotations.Enumeration;

/** Статус клиента. Набор значений задан кодом, поэтому это перечисление, а не справочник. */
@Enumeration(name = "ClientStatuses", title = "Статус клиента")
public enum ClientStatus {

    /** Потенциальный клиент: заведён, но ещё не оплачивал. */
    @EnumLabel(value = "Потенциальный", color = "#2563EB") LEAD,

    /** Действующий клиент. */
    @EnumLabel(value = "Активный", color = "#059669") ACTIVE,

    /** Приоритетный клиент. */
    @EnumLabel(value = "VIP", color = "#7C3AED") VIP,

    /** Есть риск потерять клиента. */
    @EnumLabel(value = "Риск оттока", color = "#D97706") AT_RISK,

    /** Не пользуется сервисом. */
    @EnumLabel(value = "Неактивный", color = "#6B7280") INACTIVE
}
