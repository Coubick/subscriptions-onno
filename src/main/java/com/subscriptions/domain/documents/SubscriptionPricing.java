package com.subscriptions.domain.documents;

import com.subscriptions.domain.catalogs.Tariff;
import com.subscriptions.domain.catalogs.TariffRepository;
import su.onno.types.Ref;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Работа строк подписки со справочником тарифов: подстановка цены и длительности периода
 * ({@link Subscription#beforeWrite()}) и проверка доступности тарифа ({@link Subscription#rules()}).
 * Хуки получают сервис через мост к Spring-контексту.
 */
@Service
public class SubscriptionPricing {

    private final TariffRepository tariffs;

    public SubscriptionPricing(TariffRepository tariffs) {
        this.tariffs = tariffs;
    }

    /** Снимок тарифа в каждую строку с выбранным тарифом. Тарифы читаются одним запросом. */
    public void applyTariffs(List<SubscriptionLine> lines) {
        Map<UUID, Tariff> byId = tariffsOf(lines);
        for (SubscriptionLine line : lines) {
            Tariff tariff = line.getTariff() == null ? null : byId.get(line.getTariff().id());
            if (tariff != null) {
                line.setPrice(tariff.getPricePerPeriod());
                line.setPeriodDays(tariff.getPeriodDays());
            }
        }
    }

    /**
     * Все выбранные в строках тарифы существуют и доступны для подключения. {@code null} в флаге
     * доступности считается «недоступен». Строки без тарифа проверяет отдельное правило.
     */
    public boolean allTariffsAvailable(List<SubscriptionLine> lines) {
        Map<UUID, Tariff> byId = tariffsOf(lines);
        return lines.stream()
                .map(SubscriptionLine::getTariff)
                .filter(Objects::nonNull)
                .map(ref -> byId.get(ref.id()))
                .allMatch(t -> t != null && Boolean.TRUE.equals(t.getAvailableForConnection()));
    }

    private Map<UUID, Tariff> tariffsOf(List<SubscriptionLine> lines) {
        List<UUID> ids = lines.stream()
                .map(SubscriptionLine::getTariff)
                .filter(Objects::nonNull)
                .map(Ref::id)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return tariffs.findAllById(ids).stream()
                .collect(Collectors.toMap(Tariff::getId, Function.identity()));
    }
}
