package com.subscriptions.ui;

import com.subscriptions.domain.catalogs.Client;
import com.subscriptions.domain.catalogs.Tariff;
import com.subscriptions.domain.documents.Subscription;
import com.subscriptions.domain.documents.SubscriptionLine;
import com.subscriptions.domain.enumerations.SubscriptionStatus;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;
import su.onno.ui.ListSpec;

import org.springframework.stereotype.Component;

/** Подписки: шапка, строки тарифов и проведение со списанием с лицевого счёта. */
@Component
public class SubscriptionView implements EntityView<Subscription> {

    private static final String MONEY = "currency:RUB";
    private static final String DATE = "dd-MM-yyyy";

    @Override
    public Class<Subscription> entity() {
        return Subscription.class;
    }

    @Override
    public void list(ListSpec<Subscription> list) {
        list.columns(Subscription::getNumber, Subscription::getDate, Subscription::getClient,
                        Subscription::getStatus, Subscription::getStartDate, Subscription::getEndDate,
                        Subscription::getTotal, Subscription::isPosted)
                .label(Subscription::getNumber, "Номер")
                .label(Subscription::getDate, "Дата")
                .label(Subscription::getClient, "Клиент")
                .label(Subscription::getStatus, "Статус")
                .label(Subscription::getStartDate, "Начало")
                .label(Subscription::getEndDate, "Окончание")
                .label(Subscription::getTotal, "Итого")
                .label(Subscription::isPosted, "Проведена")
                .sortBy(Subscription::getDate, true)
                .groupable(Subscription::getStatus, Subscription::getClient)
                .aggregate(Subscription::getTotal, ListSpec.Agg.SUM, "Итого");

        list.filter(Subscription::getStatus).label("Статус").multiOptions();
        list.filter(Subscription::getDate).label("Дата").dateRange();

        list.rowStyle(row -> switch (row.enumValue(Subscription::getStatus, SubscriptionStatus.class)) {
            case CANCELLED -> ListSpec.RowStyle.MUTED;
            case EXPIRED -> ListSpec.RowStyle.WARNING;
            case null, default -> null;
        });
    }

    @Override
    public void fields(EntityConfigBuilder<Subscription> f) {
        f.field(Subscription::getNumber).label("Номер").order(0).width("half");
        f.field(Subscription::getDate).label("Дата").order(10).width("half").format(DATE);
        f.refField(Subscription::getClient).order(20).width("half").refSecondary(Client::getEmail)
                .hint("С лицевого счёта этого клиента списывается итог при проведении.");
        f.field(Subscription::getStatus).order(30).width("half")
                .hint("«Активна» и «Истекла» ставит регламентное задание по датам. "
                        + "«Отменена» — вручную, после отмены проведения.");
        f.field(Subscription::getStartDate).order(40).width("half").format(DATE)
                .hint("По умолчанию — дата создания.");
        f.field(Subscription::getEndDate).order(50).width("half").format(DATE)
                .hint("Считается при сохранении: дата начала плюс самый длинный срок среди строк.");
        f.field(Subscription::getTotal).order(60).width("half").format(MONEY)
                .hint("Сумма строк. Списывается с лицевого счёта при проведении.");
        f.field(Subscription::isPosted).label("Проведена");

        f.rowRefField(Subscription::getLines, SubscriptionLine::getTariff).label("Тариф")
                .refSecondary(Tariff::getPricePerPeriod)
                .hint("Цена и длительность периода подставятся из тарифа при сохранении.");
        f.rowField(Subscription::getLines, SubscriptionLine::getPeriods).label("Периодов").placeholder("1");
        f.rowField(Subscription::getLines, SubscriptionLine::getPrice).label("Цена за период").format(MONEY)
                .hint("Из тарифа. У проведённой подписки не меняется.");
        f.rowField(Subscription::getLines, SubscriptionLine::getPeriodDays).label("Дней в периоде")
                .hint("Из тарифа.");
        f.rowField(Subscription::getLines, SubscriptionLine::getAmount).label("Сумма").format(MONEY)
                .hint("Цена × периодов.");

        f.action("post").primary();
    }
}
