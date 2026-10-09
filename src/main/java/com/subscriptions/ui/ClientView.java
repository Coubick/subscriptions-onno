package com.subscriptions.ui;

import com.subscriptions.domain.catalogs.Client;
import com.subscriptions.domain.enumerations.ClientStatus;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;
import su.onno.ui.ListSpec;

import org.springframework.stereotype.Component;

/** Справочник клиентов: список с фильтром по статусу и форма в две колонки. */
@Component
public class ClientView implements EntityView<Client> {

    @Override
    public Class<Client> entity() {
        return Client.class;
    }

    @Override
    public void list(ListSpec<Client> list) {
        list.columns(Client::getCode, Client::getDescription, Client::getStatus, Client::getEmail,
                        Client::getPhone, Client::getRegistrationDate)
                .label(Client::getCode, "Код")
                .label(Client::getDescription, "Клиент")
                .label(Client::getStatus, "Статус")
                .label(Client::getEmail, "E-mail")
                .label(Client::getPhone, "Телефон")
                .label(Client::getRegistrationDate, "Дата регистрации")
                .sortBy(Client::getDescription)
                .groupable(Client::getStatus);

        list.filter(Client::getStatus).label("Статус").multiOptions();

        list.rowStyle(row -> switch (row.enumValue(Client::getStatus, ClientStatus.class)) {
            case AT_RISK -> ListSpec.RowStyle.WARNING;
            case INACTIVE -> ListSpec.RowStyle.MUTED;
            case null, default -> null;
        });
    }

    @Override
    public void fields(EntityConfigBuilder<Client> f) {
        f.field(Client::getCode).label("Код").order(0).width("half")
                .hint("Присваивается автоматически при создании.");
        f.field(Client::getDescription).label("Наименование").order(10).width("half")
                .placeholder("ООО «Ромашка» или Иванов Иван");
        f.field(Client::getStatus).order(20).width("half")
                .hint("Стадия отношений с клиентом.");
        f.field(Client::getRegistrationDate).order(30).width("half").format("dd-MM-yyyy")
                .hint("Подставляется текущей датой при создании.");
        f.field(Client::getEmail).order(40).width("half").placeholder("client@example.com");
        f.field(Client::getPhone).order(50).width("half").placeholder("+7 900 000-00-00");
    }
}
