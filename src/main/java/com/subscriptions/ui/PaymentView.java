package com.subscriptions.ui;

import com.subscriptions.domain.catalogs.Client;
import com.subscriptions.domain.documents.Payment;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;
import su.onno.ui.ListSpec;

import org.springframework.stereotype.Component;

/** Платежи клиентов: проведение пополняет лицевой счёт. */
@Component
public class PaymentView implements EntityView<Payment> {

    @Override
    public Class<Payment> entity() {
        return Payment.class;
    }

    @Override
    public void list(ListSpec<Payment> list) {
        list.columns(Payment::getNumber, Payment::getDate, Payment::getClient, Payment::getMethod,
                        Payment::getAmount, Payment::isPosted)
                .label(Payment::getNumber, "Номер")
                .label(Payment::getDate, "Дата")
                .label(Payment::getClient, "Клиент")
                .label(Payment::getMethod, "Способ оплаты")
                .label(Payment::getAmount, "Сумма")
                .label(Payment::isPosted, "Проведён")
                .sortBy(Payment::getDate, true)
                .groupable(Payment::getClient, Payment::getMethod)
                .aggregate(Payment::getAmount, ListSpec.Agg.SUM, "Итого");

        list.filter(Payment::getDate).label("Дата").dateRange();
        list.filter(Payment::getMethod).label("Способ оплаты").multiOptions();

        list.rowStyle(row -> row.bool(Payment::isPosted) ? null : ListSpec.RowStyle.MUTED);
    }

    @Override
    public void fields(EntityConfigBuilder<Payment> f) {
        f.field(Payment::getNumber).label("Номер").order(0).width("half");
        f.field(Payment::getDate).label("Дата").order(10).width("half").format("dd-MM-yyyy");
        f.refField(Payment::getClient).order(20).width("half").refSecondary(Client::getEmail)
                .hint("Чей лицевой счёт пополняется.");
        f.field(Payment::getMethod).order(30).width("half");
        f.field(Payment::getAmount).order(40).width("half").format("currency:RUB").placeholder("0.00")
                .hint("Зачисляется на лицевой счёт при проведении.");
        f.field(Payment::isPosted).label("Проведён");

        f.action("post").primary();
    }
}
