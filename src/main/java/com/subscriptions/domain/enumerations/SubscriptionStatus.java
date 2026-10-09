package com.subscriptions.domain.enumerations;

import su.onno.annotations.EnumLabel;
import su.onno.annotations.Enumeration;

/**
 * Жизненный цикл подписки. Переходы DRAFT → ACTIVE → EXPIRED выполняет регламентное задание
 * по датам; CANCELLED ставится вручную и исключает любые движения по регистрам.
 */
@Enumeration(name = "SubscriptionStatuses", title = "Статус подписки")
public enum SubscriptionStatus {

    /** Оформлена, дата начала ещё не наступила. */
    @EnumLabel(value = "Черновик", color = "#6B7280") DRAFT,

    /** Действует: дата начала наступила, срок не истёк. */
    @EnumLabel(value = "Активна", color = "#059669") ACTIVE,

    /** Срок действия истёк. */
    @EnumLabel(value = "Истекла", color = "#D97706") EXPIRED,

    /** Отменена: деньги не списываются, выручка не признаётся. */
    @EnumLabel(value = "Отменена", color = "#DC2626") CANCELLED
}
