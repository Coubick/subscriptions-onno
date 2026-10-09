# subscriptions-onno

Сервис учёта подписок клиентов на тарифные планы на [onno-framework](https://github.com/onno-erp/onno-framework).
Схема БД, REST API, веб-интерфейс и миграции генерируются из типизированной Java-метамодели:
таблицы, DTO, контроллеры и формы вручную не пишутся.

## Функциональность

- **Справочники и документы:** клиенты, тарифы, платежи, подписки. В подписке одна или несколько
  строк, каждая — тариф, купленный на N периодов.
- **Проведение.** Платёж пополняет лицевой счёт клиента (регистр остатков `ClientAccount`).
  Подписка списывает итог с лицевого счёта и признаёт выручку по каждой строке (регистр оборотов
  `TariffRevenue`: тариф × клиент, сумма и число периодов).
- **Отменённая подписка не создаёт движений:** деньги не списываются, выручка не признаётся.
  Проведённую подписку нельзя перевести в «Отменена» обычным сохранением — для этого есть действие.
- **Действие «Отменить подписку»** в строке списка и в меню формы подписки (для черновиков и активных).
  Спрашивает причину в модальном окне, у проведённой подписки снимает проведение (деньги возвращаются
  на лицевой счёт, выручка сторнируется), ставит статус «Отменена» и записывает причину в примечание.
- **Срок действия** = дата начала + max(дней в периоде × периодов) среди строк. Тариф на 365 дней и
  тариф на 30 дней в одном документе дают подписку на 365 дней.
- **Нельзя оформить подписку при нехватке денег:** проведение отклоняется с понятной ошибкой.
  Регистр остатков дополнительно не даёт уйти в минус.
- **Бизнес-правила:** клиент обязателен, хотя бы одна строка, в строке выбран тариф, число периодов
  больше нуля, тариф доступен для подключения.
- **Автоподстановка:** дата документа и дата начала при создании; цена и длительность периода строки
  из тарифа; суммы, итог и дата окончания пересчитываются при каждом сохранении.
- **Статусы меняются автоматически по датам.** Регламентное задание `SubscriptionStatus` (каждый час):
  проведённый «Черновик» → «Активна» с даты начала, «Активна» → «Истекла» после даты окончания.
  Меняется только статус, движения регистров не трогаются.
- **Интерфейс:** боковое меню «Подписки / Финансы / Отчёты», списки с фильтрами и итогами, формы
  в две колонки, деньги в рублях, даты `dd-MM-yyyy`, цветные статусы, подсказки у полей.
  Отчёты: остатки лицевых счетов и выручка в разрезе тарифов и клиентов.
- **Главная страница-дашборд** (`/`): выбор периода; KPI «Остаток лицевых счетов», «Выручка за период»
  (с изменением к предыдущему периоду) и «Активных подписок»; диаграмма выручки по тарифам;
  последние подписки и платежи.

## Запуск

Нужна **Java 21** (Gradle wrapper в репозитории, ставить Gradle не нужно).

```bash
./gradlew :bootRun
```

Откройте http://localhost:8080/ui и войдите как `admin` / `admin` (демо-пользователь, только для разработки).
База — файловая H2 в `./data/` (не коммитится); схема создаётся при первом старте.

## Тесты

```bash
./gradlew test
```

38 тестов: unit-тесты чистых правил и расчётов (без Spring) и `@SpringBootTest` на H2 в памяти для
хуков, проведения и регламентного задания. Тестовая БД своя, файл `./data` тесты не трогают.

### Трассировка «требование ТЗ → тест»

| Требование ТЗ | Тесты |
| --- | --- |
| Платёж пополняет лицевой счёт | `SubscriptionPostingIT.paymentTopsUpAndSubscriptionWritesOffAndRecognisesRevenue` |
| Подписка списывает с лицевого счёта и признаёт выручку | `SubscriptionPostingIT.paymentTopsUpAndSubscriptionWritesOffAndRecognisesRevenue` |
| Нельзя оформить при нехватке денег | `SubscriptionPostingIT.beforePostRejectsInsufficientFundsWithValidationError`, `repostOfFullySpentBalanceIsNotFalselyRejected` |
| Отменённая подписка не создаёт движений | `SubscriptionPostingIT.cancelledSubscriptionCreatesNoMovements`, `postedSubscriptionCannotBeCancelledBySave`, `unpostThenCancelIsAllowed`; `SubscriptionRulesTest.cancellingPostedSubscriptionIsRejected`, `cancellingUnpostedSubscriptionIsAllowed` |
| Срок = начало + максимальный срок среди строк | `SubscriptionRulesTest.recalculateComputesAmountsTotalAndEndDateByLongestLine`; `SubscriptionPostingIT.saveRunsOnFillingAndBeforeWrite`, `saveSubstitutesPriceAndPeriodFromTariff` |
| Правило: клиент обязателен | `SubscriptionRulesTest.clientIsRequired` |
| Правило: хотя бы одна строка | `SubscriptionRulesTest.atLeastOneLineIsRequired`; `SubscriptionPostingIT.saveRejectsBrokenRules` |
| Правило: число периодов больше нуля | `SubscriptionRulesTest.periodsMustBePositive`, `everyLineNeedsTariff`, `validSubscriptionPassesAllRules` |
| Правило: тариф доступен для подключения | `SubscriptionPostingIT.unavailableTariffIsRejected`, `availableTariffPasses`, `tariffWithoutAvailabilityFlagCannotBeSaved`, `withdrawingTariffDoesNotBlockSavingPostedSubscription` |
| Автоподстановка даты документа и даты начала | `SubscriptionPostingIT.saveRunsOnFillingAndBeforeWrite` |
| Автоподстановка цены строки из тарифа | `SubscriptionPostingIT.saveSubstitutesPriceAndPeriodFromTariff`, `draftFollowsTariffButPostedKeepsSnapshot` |
| Пересчёт сумм и даты окончания при сохранении | `SubscriptionRulesTest.recalculateComputesAmountsTotalAndEndDateByLongestLine`; `SubscriptionPostingIT.saveRunsOnFillingAndBeforeWrite` |
| Задание: перевод в «Активна» по дате | `SubscriptionStatusJobTest.postedDraftBecomesActiveOnStartDate`, `draftWithFutureStartStaysDraft`, `unpostedDraftIsNotActivated`; `SubscriptionStatusJobIT.leavesUnpostedDraftAlone` |
| Задание: перевод в «Истекла» по дате | `SubscriptionStatusJobTest.activeStaysActiveOnEndDateAndExpiresTheDayAfter`, `postedDraftWithPastPeriodExpiresInOneRun`, `cancelledAndExpiredAreFinal` |
| Отмена подписки с причиной (ActionSpec) | `SubscriptionCancellationIT.cancellingPostedSubscriptionRefundsReversesRevenueAndSetsStatus`, `detailActionCancelsUnpostedDraft`, `cancellingDraftOnWithdrawnTariffIsAllowed`, `blankReasonIsRejectedAndNothingChanges`, `expiredSubscriptionCannotBeCancelledEvenIfButtonBypassed`, `actionsAreDeclaredWithScopesFormAndVisibility` |
| Задание меняет только статус, не движения | `SubscriptionStatusJobIT.activatesThenExpiresWithoutTouchingMovements`, `savePublishesEntityChangedEvent`, `skipsDeletionMarkedSubscriptions` |

## Архитектура

```text
src/main/java/com/subscriptions/
  SubscriptionsApp.java          точка входа Spring Boot
  domain/
    catalogs/                    Client, Tariff (@Catalog) и их репозитории
    enumerations/                ClientStatus, SubscriptionStatus, PaymentMethod (@Enumeration, цвета статусов)
    documents/                   Payment, Subscription + SubscriptionLine (@Document, @TabularSection),
                                 SubscriptionPricing, SubscriptionCancellation, репозитории
    registers/                   ClientAccount (BALANCE), TariffRevenue (TURNOVER),
                                 AccountBalanceService, репозитории регистров
    jobs/                        SubscriptionStatusJob (@ScheduledJob)
  support/SpringBeans.java       мост к Spring-контексту для хуков документов
  ui/                            EntityView для каждой сущности и отчёта, MainLayout (меню),
                                 действия отмены в SubscriptionView, DashboardPage (главная)
src/main/resources/application.yaml
src/test/java/com/subscriptions/ тесты
```

- **Проведение — типизированный Java-код** в `handlePosting`: движения пишутся только через
  `PostingContext`, без побочных эффектов.
- **`SpringBeans` — осознанный обход.** Хуки (`beforeWrite`, `beforePost`, `rules()`) выполняются на
  объектах, которые фреймворк создаёт рефлексией, поэтому `@Autowired` в них не работает. Мост —
  `@Component implements ApplicationContextAware` со статическим `get(Class)`; этот приём описан в
  справочнике onno. Используется только в хуках `Subscription`; сервисы и задание получают
  зависимости через конструктор.
- **`SubscriptionPricing`** — сервис строк подписки над `TariffRepository`. Подставляет цену и
  длительность периода из тарифа (только у непроведённой подписки: у проведённой это снимок на момент
  оплаты) и проверяет, что выбранные тарифы доступны для подключения. Тарифы читаются одним запросом.
- **`SubscriptionCancellation`** — отмена подписки: распроведение (если проведена), статус «Отменена»,
  причина в примечание. Проведение onno идёт отдельной транзакцией, поэтому шаги последовательные:
  сначала фиксируется отмена проведения, затем сохраняется статус. Обработчики действий в
  `SubscriptionView` только вызывают сервис и переводят ошибки в ответ формы; видимость кнопки —
  лишь отображение, статус перепроверяется в сервисе.
- **`AccountBalanceService`** читает остаток лицевого счёта для проверки средств до проведения: без неё
  нехватка денег дала бы HTTP 500 из ядра вместо понятной ошибки 400. При перепроведении к остатку
  прибавляется собственное списание документа, иначе документ отклонялся бы ложно.
- **Репозитории объявлены вручную:** onno не генерирует их сам — Spring Data создаёт бин только для
  объявленного интерфейса, а реализацию строит фреймворк.

## Ограничения

- **Язык сообщений.** Системные сообщения ядра onno английские (например, `Статус is required` при
  пропуске обязательного поля), сообщения бизнес-правил приложения — русские.
- **Сохранение не перепроводит документ.** Движения проведённого документа после правки остаются
  прежними (стандартное поведение ERP). Для перепроведения после правки нажмите «Провести» — сработает repost.
- **Цена из тарифа подставляется после сохранения**, а не в момент выбора тарифа: в onno нет хука,
  который заполнял бы поля формы на лету.
- **KPI-карточки дашборда считаются за всё время.** «Остаток лицевых счетов» и «Активных подписок»
  от выбора периода не зависят (так устроены карточки `metric`/`count` в onno); выручка за период,
  диаграмма и их сравнение с прошлым периодом берут окно из выбора периода.
- **Обходы ограничений виджетов onno 3.4.1 на дашборде.** Диаграмма по регистру группирует обороты
  по сырому значению измерения (на оси были бы UUID тарифов), поэтому группировка задана по колонке
  с подписью `tariff_display`; тарифы с одинаковым названием сольются в один столбец. Ось Y шириной
  40px не вмещает «20 тыс. ₽», поэтому рубли вынесены в заголовок и подпись ряда. Даты в списках
  последних документов фреймворк выводит в фиксированном английском формате («Oct 9»).
- **Отчёты регистров** настраиваются в onno 3.4.1 частично: подписи, формат и порядок колонок и формат
  даты берутся из `EntityView`, а настройки списка и подсказки для регистров не применяются.
- **В «Активна» переводятся только проведённые подписки:** непроведённый черновик не оплачен.
- **Флаг «Доступен для подключения» обязателен.** Если в старой локальной БД есть тариф с пустым
  флагом, onno при старте пропустит `SET NOT NULL` для колонки и предупредит в логе; на новой БД
  ограничение создаётся сразу.

## Использованные скиллы onno

Скиллы и справочники из [`onno-plugin`](https://github.com/onno-erp/onno-framework/tree/main/onno-plugin/skills).
Проект делали два ИИ-агента по очереди.

**Cline** (каркас, модель, регистры и проведение): `onno`, `onno-modeling`, `onno-catalogs-enums`,
`onno-documents-lines`, `onno-registers`, `onno-posting`, `onno-ui`, `onno-testing-release`.

**Claude Code** (доработка проведения, правила, тесты, регламентное задание, UI, действия):

- `onno` — общий плейбук и `reference/cheatsheet.md` (хуки без DI и мост к Spring-контексту, `PostingContext`, `RegisterRepository`).
- `onno-registers` — регистры остатков и оборотов, чтение остатков из сервиса.
- `onno-ui-entity-views` — `EntityView`: колонки, форматы, подсказки, фильтры, стили строк, действия.

Плюс документация фреймворка: `AGENTS.md`, `docs/GOTCHAS.md`, `docs/CONFIGURATION.md`, `onno-ui-starter/README.md`.
