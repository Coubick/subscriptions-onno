package com.subscriptions.ui;

import com.subscriptions.domain.documents.Payment;
import com.subscriptions.domain.documents.Subscription;
import com.subscriptions.domain.enumerations.SubscriptionStatus;
import com.subscriptions.domain.registers.ClientAccount;
import com.subscriptions.domain.registers.TariffRevenue;
import su.onno.repository.EnumerationPersistence;
import su.onno.ui.Page;
import su.onno.ui.PageBuilder;

import org.springframework.stereotype.Component;

/**
 * Главная страница («/»): показатели, выручка по тарифам и последние документы.
 *
 * <p>KPI-карточки ({@code metric}/{@code count}) считаются на сервере за всё время: остаток — это
 * сумма движений со знаком (приход минус расход). Выручка за период — виджет {@code stat}: он, как и
 * диаграмма, берёт окно из общего выбора периода вверху страницы.</p>
 */
@Component
public class DashboardPage implements Page {

    private static final String RUB = "RUB";
    private static final String LOCALE = "ru-RU";

    @Override
    public String route() {
        return "/";
    }

    @Override
    public void compose(PageBuilder b) {
        b.title("Обзор").subtitle("Лицевые счета, выручка и подписки");

        b.widget("Период").type("timeRange").width("full").order(-10)
                .config("presets", "7d,30d,90d,1y,all")
                .config("default", "30d");

        b.widget("Остаток лицевых счетов").type("metric").width("1/3").order(0)
                .register(ClientAccount.class)
                .metricField(ClientAccount::getAmount)
                .config("metric", "sum").config("currency", RUB).config("locale", LOCALE)
                .hint("Сумма текущих остатков всех клиентов: платежи минус списания за подписки.");

        b.widget("Выручка за период").type("stat").width("1/3").order(1)
                .register(TariffRevenue.class)
                .metricField(TariffRevenue::getAmount)
                .config("metric", "sum").config("groupByDate", "day")
                .config("currency", RUB).config("locale", LOCALE)
                .hint("Выручка проведённых подписок за выбранный период и изменение к предыдущему.");

        b.widget("Активных подписок").type("count").width("1/3").order(2)
                .document(Subscription.class)
                // Перечисление хранится как детерминированный UUID, фильтр сравнивает именно его.
                .config("filter", "status = '"
                        + EnumerationPersistence.resolveId(SubscriptionStatus.class, SubscriptionStatus.ACTIVE) + "'")
                .hint("Подписки в статусе «Активна».");

        b.chart("Выручка по тарифам, ₽", TariffRevenue.class).width("full").order(3).rowBreak()
                .category(TariffRevenue::getTariff)
                .sum(TariffRevenue::getAmount).label("Выручка, ₽")
                // Без currency: ось Y в onno шириной 40px, «20 тыс. ₽» в неё не помещается, «20 тыс.» — да.
                .bar().locale(LOCALE)
                .hint("За выбранный период.")
                // onno 3.4.1 группирует обороты регистра по сырому значению измерения, и на оси
                // оказываются UUID тарифов. Строки оборотов уже несут подпись в tariff_display.
                .config("groupBy", "tariff_display");

        b.widget("Последние подписки").type("list").width("1/2").order(4).rowBreak()
                .document(Subscription.class)
                .maxItems(8)
                .dateField("date")
                .config("titleTemplate", "{number} — {clientDisplay}")
                .config("secondaryField", "statusDisplay")
                .config("amountField", "total")
                .config("currency", RUB).config("locale", LOCALE);

        b.widget("Последние платежи").type("list").width("1/2").order(5)
                .document(Payment.class)
                .maxItems(8)
                .dateField("date")
                .config("titleTemplate", "{number} — {clientDisplay}")
                .config("secondaryField", "methodDisplay")
                .config("amountField", "amount")
                .config("currency", RUB).config("locale", LOCALE);
    }
}
