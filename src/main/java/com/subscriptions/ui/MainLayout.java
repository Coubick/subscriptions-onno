package com.subscriptions.ui;

import com.subscriptions.domain.catalogs.Client;
import com.subscriptions.domain.catalogs.Tariff;
import com.subscriptions.domain.documents.Payment;
import com.subscriptions.domain.documents.Subscription;
import com.subscriptions.domain.registers.ClientAccount;
import com.subscriptions.domain.registers.TariffRevenue;
import su.onno.ui.Layout;
import su.onno.ui.LayoutSpec;
import su.onno.ui.NavStyle;

import org.springframework.stereotype.Component;

/** Основная оболочка: боковое меню из трёх разделов. Сущность видна в меню, только если раздел её перечисляет. */
@Component
public class MainLayout implements Layout {

    @Override
    public void configure(LayoutSpec spec) {
        spec.shell().nav(NavStyle.SIDEBAR).brand("Подписки");

        spec.section("Подписки").icon("repeat")
                .catalog(Client.class, "users")
                .catalog(Tariff.class, "tag")
                .document(Subscription.class, "file-text");

        spec.section("Финансы").icon("wallet")
                .document(Payment.class, "credit-card");

        spec.section("Отчёты").icon("bar-chart-3")
                .register(ClientAccount.class, "landmark")
                .register(TariffRevenue.class, "trending-up");
    }
}
