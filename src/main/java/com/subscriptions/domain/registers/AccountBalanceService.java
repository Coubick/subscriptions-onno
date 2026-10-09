package com.subscriptions.domain.registers;

import com.subscriptions.domain.catalogs.Client;
import su.onno.model.MovementType;
import su.onno.types.Ref;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Чтение остатка лицевого счёта клиента. */
@Service
public class AccountBalanceService {

    private final ClientAccountRepository account;

    public AccountBalanceService(ClientAccountRepository account) {
        this.account = account;
    }

    /** Текущий остаток клиента; ноль, если движений ещё не было. */
    public BigDecimal balanceOf(Ref<Client> client) {
        return account.getBalance(f -> f.where(ClientAccount::getClient, client)).stream()
                .map(ClientAccount::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Сколько активные движения документа уже сняли со счёта клиента: расход минус приход.
     * Ноль, если документ не проведён. Нужно при перепроведении: собственное списание документа
     * уже сидит в остатке и будет сторнировано, поэтому его прибавляют к {@link #balanceOf}.
     */
    public BigDecimal sumMovementsOfDocument(Ref<Client> client, UUID documentId) {
        if (documentId == null) {
            return BigDecimal.ZERO;
        }
        return account.getRecordsByDocument(documentId).stream()
                .filter(r -> r.isActive() && r.getAmount() != null && client.equals(r.getClient()))
                .map(r -> r.getMovementType() == MovementType.EXPENSE ? r.getAmount() : r.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
