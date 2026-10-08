# subscriptions-onno

Сервис подписок на [onno-framework](https://github.com/onno-erp/onno-framework): клиенты, тарифы,
платежи, подписки, лицевой счёт и выручка по тарифам. Схема БД, REST и UI генерируются из
типизированной Java-метамодели.

## Стек

- Java 21, Spring Boot 3.4.4, Gradle (wrapper)
- onno 3.4.1 (`su.onno:onno-framework-starter`, `onno-ui-starter`, `onno-auth-starter`)
- H2 (файловая БД в `./data/`, не коммитится)

## Запуск

```bash
./gradlew :bootRun
```

Приложение стартует на http://localhost:8080. Демо-вход: `admin` / `admin` (только для разработки).

## Структура

```text
src/main/java/com/subscriptions/   приложение и (далее) доменная модель
src/main/resources/application.yaml
src/test/java/com/subscriptions/   тесты
```

## Статус

- [x] Скелет проекта