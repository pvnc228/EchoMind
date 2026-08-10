# Постоянная инструкция исполнителю EchoMind

Перед изменениями прочитай `VISION.md`, `PRODUCT.md`, `DATA_CONTRACT.md`,
`ROADMAP.md` и релевантный decision record из `docs/`.

Для production-изменений используй skill `tdd`: сначала воспроизводящий
красный oracle, затем минимальный production fix.

При неясной причине сбоя используй `diagnosing-bugs`; не исправляй симптомы
без доказанного механизма.

Перед заявлением о завершении проведи self-review через `code-review-expert`:
проверь invariants, races, error paths, ресурсы, безопасность и документацию.

Для UI используй `ui-skills-root` и `impeccable`; для затронутых экранов —
`fixing-accessibility` и реальные проверки 200% text, compact/landscape,
TalkBack/IME.

Raw source и historical revisions неизменяемы. Исправление создаёт новую
revision; provenance нельзя переносить или переписывать.

AI output — только proposal до явного действия пользователя. Import, retry,
restart и background worker никогда ничего не подтверждают.

Согласие отправить raw data — отдельная граница; `localMode` и подтверждение
вывода не заменяют consent на передачу.

Все DB graph writes выполняй атомарно. Caller-provided IDs перепроверяй внутри
transaction; запрещай extra/stale IDs.

Любая миграция обязана сохранять реальные legacy states. Проверяй fresh schema
и полный путь `migration → export → empty restore → export`.

File operations ограничивай canonical app-owned roots. DB commit и filesystem
cleanup имеют явный partial-failure state и реальный lifecycle consumer.

ZIP/import проверяй полностью до записей: размеры, entry count, paths, hashes,
exact references, graph invariants, rollback без retained files.

File I/O, ZIP, crypto и крупные вычисления не выполняй на Main. Любые
batches/retries должны исключать starvation и иметь terminal policy.

Для детерминированной логики добавляй negative, metamorphic, Russian/Unicode и
concurrency tests; fixture-specific правила запрещены.

`assembleAndroidTest` не доказывает запуск тестов.

Completion gate:

```text
:app:testDebugUnitTest --rerun-tasks
:app:connectedDebugAndroidTest --rerun-tasks
:app:lintDebug --rerun-tasks
git diff --check
```

Используй Android Studio JDK 21, Gradle 8.9 и `Pixel_8_2` API 35. Offline
emulator и старые XML не являются evidence.

Не отмечай milestone/P1/P2 закрытым до свежего artifact. Незапущенные device,
accessibility, performance или restart проверки называй незапущенными.

## Правило против преждевременного закрытия gate

Зелёные тесты сами по себе не доказывают закрытие замечания. Для каждого
finding перед реализацией зафиксируй цепочку:

`finding -> production seam -> red oracle -> green oracle -> runtime evidence -> artifact`.

Обязательные проверки:

1. Oracle должен проходить через production public seam. Тест чистого ranker,
   mapper, DAO или заранее созданного списка не доказывает end-to-end путь
   repository/UI; benchmark обязан включать заявленный Room/IO/CPU путь.
2. Для UI persistence-тесты DAO/repository не заменяют экран и ViewModel.
   После reopen/restore нужно проверить реальные пользовательские semantics,
   отображение и действие, относящиеся к finding.
3. Red oracle должен ломаться при удалении именно исправления либо при
   возврате старого механизма. Если oracle проходит без production fix, он не
   подтверждает исправление и должен быть усилен.
4. Полный completion gate запускай после последнего изменения кода, тестов и
   документации. Не переноси старые counts, benchmark values или status из
   предыдущего artifact без повторной проверки.
5. Перед verdict выполняй отдельный adversarial self-review: что тест обходит,
   какая работа всё ещё выполняется на Main, покрыт ли error/resource path,
   не заменена ли UI-проверка data-only проверкой и нет ли противоречивых
   current/historical абзацев в документах.
6. Если критерий не проверен реальным runtime evidence, он остаётся open или
   явно deferred. Нельзя превращать план, намерение или косвенный proxy в
   resolved только потому, что общий test count зелёный.

Этот protocol появился после review, который обнаружил три такие ошибки:
desktop-only ranker benchmark вместо production public-seam benchmark,
repository/DAO data tests вместо restart/export UI-flow и противоречивый
текущий статус ROADMAP. Исполнитель обязан рассматривать эти случаи как
регрессионные примеры для всех будущих repair-gate задач.

## GitHub issue workflow

- Каждое production-изменение должно быть связано с GitHub Issue; порядок
  работы с GitHub Project описан в `docs/GITHUB_WORKFLOW.md`.
- Перед началом работы прочитать Issue и связанные документы проекта.
- Одна Issue должна описывать один проверяемый результат.
- Перед началом выбрать одну карточку из `Ready` и сразу перевести её в
  `In Progress`.
- Не брать карточку, которая уже находится в `In Progress`; для параллельной
  работы использовать отдельную Issue и отдельную ветку/worktree.
- Не выполнять параллельные изменения в одном локальном checkout.
- После реализации и локальной проверки перевести карточку в `Verify`.
- Pull Request обязан содержать `Closes #N`, если merge должен закрыть Issue.
- Issue нельзя переводить в `Done` без тестов, runtime evidence и записи
  результата в `JOURNAL.md`.
- Если работа заблокирована, обновить Status на `Blocked` и описать конкретную
  причину в Issue.
- Статус Project, поля и зелёные карточки не заменяют свежий artifact и не
  закрывают product milestone.
- Не помещать raw reflections, raw audio, secrets или персональные export
  contents в GitHub Issue, Pull Request или Project fields.
