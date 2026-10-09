package com.subscriptions.ui;

import com.subscriptions.domain.registers.ClientAccount;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;

import org.springframework.stereotype.Component;

/**
 * Отчёт «Лицевой счёт клиентов» (остатки и движения). В onno 3.4.1 отчёт регистра берёт из {@link #fields} подписи,
 * формат и порядок измерений и ресурсов, а также формат периода. Подпись колонки периода, подсказки
 * и {@code list()} для регистров не применяются.
 */
@Component
public class ClientAccountView implements EntityView<ClientAccount> {

    @Override
    public Class<ClientAccount> entity() {
        return ClientAccount.class;
    }

    @Override
    public void fields(EntityConfigBuilder<ClientAccount> f) {
        f.field("period").format("dd-MM-yyyy");
        f.field(ClientAccount::getClient).label("Клиент").order(0);
        f.field(ClientAccount::getAmount).label("Сумма").order(10).format("currency:RUB");
    }
}
