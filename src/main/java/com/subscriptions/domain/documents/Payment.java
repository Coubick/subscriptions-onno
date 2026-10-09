package com.subscriptions.domain.documents;

import com.subscriptions.domain.catalogs.Client;
import com.subscriptions.domain.enumerations.PaymentMethod;
import com.subscriptions.domain.registers.ClientAccount;
import su.onno.annotations.AccessControl;
import su.onno.annotations.Attribute;
import su.onno.annotations.Document;
import su.onno.lifecycle.OnFillingHandler;
import su.onno.lifecycle.Postable;
import su.onno.model.DocumentObject;
import su.onno.posting.PostingContext;
import su.onno.types.Ref;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Платёж клиента: пополняет его лицевой счёт.
 *
 * <p>Проведение записывает приход в регистр {@link ClientAccount}.</p>
 */
@Document(name = "Payments", title = "Платежи", numberPrefix = "PAY-", context = "Subscriptions")
@AccessControl(readRoles = {"MANAGER"}, writeRoles = {"MANAGER"})
@Getter
@Setter
public class Payment extends DocumentObject implements OnFillingHandler, Postable {

    @Attribute(displayName = "Клиент", required = true)
    private Ref<Client> client;

    @Attribute(displayName = "Сумма", required = true, precision = 15, scale = 2, min = 0.01)
    private BigDecimal amount;

    @Attribute(displayName = "Способ оплаты", required = true)
    private PaymentMethod method;

    /** Дата документа подставляется при создании. Идемпотентно: заданную дату не затираем. */
    @Override
    public void onFilling() {
        if (getDate() == null) {
            setDate(LocalDateTime.now());
        }
    }

    /** Проведение: приход на лицевой счёт клиента. */
    @Override
    public void handlePosting(PostingContext context) {
        context.movements(ClientAccount.class).addReceipt(r -> {
            r.setClient(client);
            r.setAmount(amount);
        });
    }
}
