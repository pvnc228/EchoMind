# Аудит коммитов с подписью `(агент opencode)`

Дата аудита: 2026-08-08

Проверенная вершина: `85e7bfe` (`master`, совпадала с `origin/master` на момент проверки)

Режим работы: review-only — production-код не исправлялся

## Итог

Проверены все 9 коммитов, сообщение которых содержит `(агент opencode)`: 4622 добавления и 270 удалений. Сборка и имеющиеся тесты проходят, однако заявления о завершении UX checkpoint, M2, M3 и полного M4-среза не подтверждаются реализацией.

Открыто 22 замечания:

- P0: 0;
- P1: 11 — нарушения ключевых продуктовых контрактов, происхождения данных и целостности графа, а также воспроизводимый риск падения UI;
- P2: 10 — существенные пробелы UX, производительности, поиска, тестирования и документации;
- P3: 1 — технический долг и ослабленные quality gates.

До исправления P1 нельзя считать M2/M3 завершёнными, а M4 — доказанным end-to-end срезом. Коммит `806fd47` является корректным исправлением документации и сам по себе новых открытых дефектов не добавляет.

## Шкала приоритетов

- **P0** — критическая потеря данных, уязвимость или полная неработоспособность; исправлять немедленно.
- **P1** — ломает основной сценарий, заявленный milestone или обязательный инвариант; исправить до следующего продуктового этапа.
- **P2** — заметный дефект надёжности, UX, производительности либо проверяемости; исправить после блокирующих инвариантов.
- **P3** — локальный технический долг, предупреждение или косметическая проблема.

## Проверенный объём

| Коммит | Заявленная работа | Результат аудита |
|---|---|---|
| `192f9da` | Echo Glass и закрытие P1 UX-проблем | Требует доработки: P1-01, P1-02, P2-07 |
| `79be9d7` | Закрытие UX checkpoint по device validation | Требует доработки: подтверждены несуществующие recovery/copy guarantees; P1-01, P1-02, P2-07 |
| `872f424` | M2 themes и relationships | Требует доработки: P1-04, P1-05, P2-01, P2-02 |
| `8996314` | Завершение M2: revisions и search | Требует доработки: P1-03, P1-11, P2-03, P2-04, P2-05 |
| `5eaa42e` | Локальная эвристика кандидатов связей | Требует доработки: P1-06, P2-02, P2-03, P2-05, P2-10 |
| `806fd47` | Исправление числа instrumented tests | Корректирующий docs-коммит; открытых дефектов не найдено |
| `dbaf17d` | Первый срез M3 Home resurfacing | Требует доработки: P1-07, P2-03, P2-05, P2-08 |
| `4150573` | Завершение M3: dismiss/postpone/capabilities | Требует доработки: P1-07, P2-01, P2-06, P2-09 |
| `85e7bfe` | Первый срез M4 decisions/outcomes | Требует доработки: P1-08, P1-09, P1-10, P2-03, P2-05, P2-06, P2-09 |

Между `192f9da` и `79be9d7` находится пользовательский коммит `bc225f9`. Он не приписан opencode и не оценивался как его работа, но учтён как контекст для последующего заявления opencode о закрытии checkpoint.

## Как проверялось

1. Поиск точного множества коммитов через `git log --grep="(агент opencode)"`, разбор каждого patch и его документационных заявлений.
2. Сопоставление реализации с `VISION.md`, `PRODUCT.md`, `DESIGN.md`, `DATA_CONTRACT.md` и критериями `ROADMAP.md`.
3. Проверка текущего кода, Room-сущностей/DAO/миграций, репозиториев, Compose UI, экспорта и тестов.
4. Свежая сборка на JDK Android Studio 21:
   - `:app:testDebugUnitTest --rerun-tasks` — **33/33**, 0 failures/errors/skips;
   - `:app:connectedDebugAndroidTest` на Pixel 8 API 35 — **22/22**, 0 failures/errors/skips;
   - `:app:lintDebug` — 0 errors, 61 warnings;
   - `:app:assembleDebug` и `:app:assembleDebugAndroidTest` — успешно.
5. Чистая установка и runtime-проверка onboarding: на экране реально показаны `Voice Diary` и обещание `Every entry is automatically transcribed`.
6. Отдельная проверка токенизации тем же regex-механизмом: русская строка распадается в пустые токены, английская — в ожидаемые слова.

Зелёные 33/33 и 22/22 — это подтверждение только существующего набора тестов. Они не доказывают перечисленные в roadmap широкие критерии: большая часть новых UI-сценариев и отрицательных графовых инвариантов тестами не покрыта.

## P1 — блокирующие замечания

### P1-01. Onboarding остаётся voice-first и обещает несуществующую автоматическую транскрипцию

**Затронутые коммиты:** `192f9da`, `79be9d7`.

**Доказательство:** `app/src/main/java/com/echomind/ui/onboarding/OnboardingScreen.kt:44-45` показывает `Voice Diary` и «Every entry is automatically transcribed». Это же увидено на чистой установке. В `README.md:18-29` продукт объявлен text-first, voice — опциональным, а автоматическая транскрипция прямо названа неподключённой. `PRODUCT.md:23` задаёт тот же контракт.

**Почему это ошибка:** первый экран формирует ложное обещание функции и описывает другой продукт. Коммиты закрыли P1 «first-session copy», не изменив главный first-run экран.

**Шаги исправления:**

1. Переписать onboarding вокруг text-first reflection и явно назвать voice необязательным вложением.
2. Удалить обещание automatic transcription до появления реально подключённого и проверенного потока.
3. Вынести ключевую product-copy в проверяемые ресурсы, чтобы README/product contract и UI не расходились.
4. Добавить Compose-тест чистого onboarding, который проверяет новую формулировку и отсутствие старых обещаний.

**Критерий приёмки:** чистая установка показывает text-first позиционирование; строки `Voice Diary` и `automatically transcribed` отсутствуют во всех пользовательских поверхностях и тестах.

### P1-02. «Interrupted unsaved capture recovery» не реализован, хотя отмечен как пройденный

**Затронутые коммиты:** `192f9da`, `79be9d7`.

**Доказательство:** кнопка Back безусловно вызывает `onNavigateBack` в `RecordScreen.kt:96-104`; `BackHandler` отсутствует. Текст и capture-state живут только в `MutableStateFlow` (`RecordViewModel.kt:50-51`), `SavedStateHandle` или persisted draft нет. При `onCleared` незаписанный audio-файл удаляется (`RecordViewModel.kt:310-317`). В `ROADMAP.md:413-428` recovery checkpoint объявлен реализованным и прошедшим device validation.

**Почему это ошибка:** введённый, но не отправленный текст и незавершённая голосовая запись теряются при Back/process death. Восстановление уже сохранённого `PROPOSED` объекта не является восстановлением **unsaved capture**.

**Шаги исправления:**

1. Зафиксировать UX-политику для Back: `Keep draft / Discard / Cancel` либо автоматический локальный draft с явным статусом.
2. Сохранять текст и метаданные незавершённого capture через `SavedStateHandle`; для process death — через отдельную локальную draft-сущность или DataStore/Room.
3. Не удалять временное audio до явного discard; добавить управляемую очистку orphan-файлов после завершённой транзакции.
4. Обработать системный Back, toolbar Back, rotation и recreation одинаково.
5. Добавить тесты для текста, audio, rotation, process recreation и явного discard.

**Критерий приёмки:** после каждого прерывания пользователь либо восстанавливает введённое, либо явно подтверждает его удаление; roadmap ссылается на конкретные автоматические и ручные проверки.

### P1-03. Новая ревизия переписывает историческое происхождение старой

**Затронутый коммит:** `8996314`.

**Доказательство:** после вставки v2 `ReflectionRepository.revise()` вызывает `rebaseEvidenceLinks` и `rebaseThemeLinks` (`ReflectionRepository.kt:196-227`). DAO выполняет `UPDATE ... SET conclusion_revision_id = new` (`KnowledgeDao.kt:94-104`). Контракт называет `ConclusionRevision` append-only (`DATA_CONTRACT.md:65`) и требует трассировки каждой ревизии (`ROADMAP.md:145-149`).

**Почему это ошибка:** v1 теряет свои evidence/theme links, а v2 получает их без отдельного подтверждения, даже если wording радикально изменён. История больше не отвечает на вопрос «на каких основаниях была принята именно эта версия», а экспорт фиксирует уже переписанное прошлое.

**Шаги исправления:**

1. Запретить изменение связей старой ревизии; считать их частью append-only snapshot.
2. При создании v2 либо не наследовать связи, либо предложить пользователю явно подтвердить копирование каждой связи.
3. Изменение/удаление semantic link моделировать отдельным versioned событием или новой связью, а не `UPDATE` старой.
4. Определить migration-политику для уже затронутых БД. Точное прошлое восстановить из текущей схемы нельзя, поэтому такие связи нужно пометить как требующие review, а не выдумывать историю.
5. Добавить тесты: v1 сохраняет links после v2; v2 не получает их молча; export/restart сохраняют обе версии.

**Критерий приёмки:** для любой ревизии после серии edits доступны её собственные неизменённые grounds/themes и дата подтверждения.

### P1-04. Theme detail падает на обычных данных из-за неуникального Compose key

**Затронутый коммит:** `872f424`.

**Доказательство:** `LazyColumn` использует ключ `"${themeId}-${revisionVersion}"` (`ThemeDetailScreen.kt:85-90`). Две разные conclusions в одной теме обычно обе начинаются с revision 1, поэтому получают один ключ.

**Почему это ошибка:** ключ Compose должен быть уникальным среди элементов. Обычный сценарий «две conclusions v1 в одной теме» создаёт duplicate key и может завершить композицию исключением.

**Шаги исправления:**

1. Использовать стабильный уникальный `revisionId`; сделать его обязательным для persisted `ThemeConclusion`.
2. Не использовать version как глобальную идентичность: version уникален только внутри одной conclusion.
3. Добавить Compose-тест темы с двумя conclusions v1 и проверку обновления/удаления элементов.

**Критерий приёмки:** экран стабильно показывает несколько conclusions с одинаковым номером revision и сохраняет состояние строк при recomposition.

### P1-05. Удаление raw record не учитывает входящие evidence links

**Затронутые коммиты:** `872f424`, усилено `5eaa42e`.

**Доказательство:** `EvidenceLinkEntity.source_raw_record_id` имеет FK `RESTRICT` (`KnowledgeEntities.kt:108-127`). `EntryRepository.deleteEntry()` удаляет только conclusion, принадлежащую удаляемому raw record, затем сам raw (`EntryRepository.kt:46-60`); входящие ссылки из других conclusions не запрашиваются и не удаляются. После M2 любой raw record может быть grounds для другой conclusion.

**Почему это ошибка:** удаление такой записи завершается FK-ошибкой и rollback. Пользователь не видит, что запись используется чужой conclusion, и не получает управляемого способа удалить/отвязать зависимость. Это противоречит критерию «delete a relationship without damaging unrelated records» и обещанию удаления всех новых объектов.

**Шаги исправления:**

1. Добавить DAO-запрос входящих links по `source_raw_record_id` и preflight deletion plan.
2. В UI показать зависимые conclusions/themes и предложить: отменить, сначала отвязать выбранные links или удалить всё явно подтверждённым каскадом по контракту.
3. Выполнять выбранную операцию одной Room-транзакцией; файловую очистку оставить отдельным подтверждаемым этапом.
4. Покрыть удаление own conclusion, incoming support/contradict links, нескольких зависимостей и rollback.

**Критерий приёмки:** удаление никогда не заканчивается необъяснённой FK-ошибкой и не повреждает несвязанные данные.

### P1-06. Локальная эвристика связей не работает на русском тексте

**Затронутый коммит:** `5eaa42e`.

**Доказательство:** `KnowledgeRepository.tokenize()` делит текст по `Regex("\\W+")` (`KnowledgeRepository.kt:182-195`). Для используемого Java/Kotlin regex `\w` без Unicode-флага — ASCII word class; кириллические буквы становятся разделителями. Контрольная проверка дала пустые русские токены и корректные английские `career`, `project`. Тесты эвристики используют только английские примеры.

**Почему это ошибка:** основная языковая среда пользователя не получает ни одной candidate relationship. Русский stop-list не помогает, потому что до него не доходит ни одного слова.

**Шаги исправления:**

1. Извлекать Unicode-буквы/цифры через `\p{L}`/`\p{N}` либо ICU BreakIterator; нормализовать регистр через `Locale.ROOT`.
2. Проверить реальную UTF-8 кодировку русского stop-list и добавить русскую морфологическую нормализацию хотя бы на уровне устойчивого stemming/лемматизации либо явно ограничить обещание.
3. Добавить русские positive, negative и rephrased/metamorphic fixtures, смешанную кириллицу/латиницу и пунктуацию.
4. Не считать один общий частотный термин достаточным основанием без пользовательского review.

**Критерий приёмки:** семантически одинаковые русские формулировки дают устойчивые candidates, нерелевантные — не дают; результат не зависит от пунктуации и регистра.

### P1-07. M3-карточка не цитирует источники и не умеет честно показывать sparse knowledge

**Затронутые коммиты:** `dbaf17d`, `4150573`.

**Доказательство:** `HomeCard` содержит только theme/count/text, но не revision/source IDs (`HomeRelevance.kt:27-35`). `getHomeRelevance()` рассматривает только active themes и выбрасывает темы без links (`KnowledgeRepository.kt:209-239`); standalone confirmed conclusions вообще не участвуют. Карточка `THIN_THEME` утверждает, что conclusion не имеет evidence (`HomeRelevance.kt:65-75`), но Inspect ведёт на theme detail, где показаны только wording/version и outcome label, без raw/evidence records (`ThemeDetailScreen.kt:90-118`). `Continue` просто открывает общий blank/new reflection flow без `themeId` или `revisionId` (`HomeScreen.kt:156-164`), поэтому выбранная линия мысли не продолжается. Выбор «relevant now» также не использует время или изменение контекста: DAO сортирует themes по имени, а builder берёт первый contradiction/thin candidate либо максимум простого count. Roadmap требует цитировать relevant records и conclusion versions (`ROADMAP.md:195-200`).

**Почему это ошибка:** counts не являются provenance. Пользователь не может проверить конкретное основание карточки. Пустые темы и unthemed conclusions исчезают из coverage, поэтому отсутствие данных превращается в «knowledge absent», а не в честный insufficient-evidence state.

**Шаги исправления:**

1. Возвращать из query конкретные `revisionId`, `sourceRawRecordId`, relationship и короткий безопасный excerpt для выбранной карточки.
2. На карточке или Inspect-поверхности показывать кликабельный список источников и версию conclusion; `Continue` должен передавать выбранный контекст либо честно называться `New reflection`.
3. Строить coverage по всем активным themes, включая 0 links, и отдельно учитывать unthemed confirmed conclusions.
4. Разделить состояния: no knowledge, ungrouped knowledge, theme without conclusions, conclusion with only own source, supporting/contradicting external evidence.
5. Определить проверяемую relevance policy с recency/change/unfinished-state, стабильным tie-break и честным «why now», а не использовать алфавитный порядок как смысловой приоритет.
6. Добавить repository и Compose-тесты на каждый sparse/contradiction case и на переход к точному источнику.

**Критерий приёмки:** каждая resurfaced claim трассируется до конкретных записей/ревизий, а пустые и малодоказательные домены явно показаны как недостаточно подтверждённые.

### P1-08. Production UI не может создать решение с grounds

**Затронутый коммит:** `85e7bfe`.

**Доказательство:** NewDecisionDialog всегда вызывает `viewModel.add(question, suggestion, null)` (`DecisionsScreen.kt:123-129`). Другого production-вызова, передающего реальный `sourceRevisionId`, нет; Theme detail не предлагает создать decision из conclusion. Repository поддерживает параметр, но он достижим только из тестов (`DecisionRepository.kt:18-37`).

**Почему это ошибка:** пользовательский сценарий не создаёт заявленную цепочку `question -> grounds -> ...`; все UI-created decisions ungrounded. Поэтому `hasOutcomeForRevision()` и label outcome evidence на theme detail почти всегда ложны для реального использования.

**Шаги исправления:**

1. Добавить entry point `Create decision from this conclusion` из detail/theme flow с реальным `revisionId`.
2. В общем Decisions flow дать явный picker/search подтверждённых revisions или честный режим `No grounds selected`.
3. Показывать выбранные grounds до сохранения и позволять отменить/заменить их с понятным provenance-событием.
4. Добавить end-to-end Compose/instrumented тест: conclusion -> decision -> choice -> outcome -> theme outcome marker.

**Критерий приёмки:** пользователь без тестового API создаёт grounded decision, и эта связь видна с обеих сторон после restart/export.

### P1-09. Пользовательский текст ложно приписывается EchoMind

**Затронутый коммит:** `85e7bfe`.

**Доказательство:** пользователь вручную вводит текст в поле `EchoMind suggestion (optional)` (`DecisionsScreen.kt:325-340`), после чего карточка показывает `EchoMind suggested: ...` (`DecisionsScreen.kt:171-176`). Ни генерации EchoMind, ни metadata источника/модели, ни обязательных grounds в этом пути нет.

**Почему это ошибка:** нарушается центральный контракт авторства: система представляет пользовательский ввод как AI proposal. Позже невозможно доказать, кто сформулировал совет и на каких данных.

**Шаги исправления:**

1. До появления настоящей suggestion pipeline переименовать поле в user-owned `Expectation/Option/Note` и показывать соответствующий provenance label.
2. Для реальной EchoMind suggestion завести отдельный тип с `author`, `sourceRevisionIds`, analyzer/model version, createdAt и confirmation status.
3. Не позволять UI создать системное утверждение через обычный пользовательский text field.
4. Добавить semantics/UI-тесты всех ownership labels и export-тест metadata.

**Критерий приёмки:** по каждой строке однозначно видно, кто её ввёл или сгенерировал; пользовательский текст никогда не маркируется как EchoMind output.

### P1-10. Decision graph допускает dangling reference и логически невозможные состояния

**Затронутый коммит:** `85e7bfe`.

**Доказательство:** `DecisionEntity.sourceRevisionId` имеет индекс, но не FK (`KnowledgeEntities.kt:188-204`). После удаления conclusion/revision decision сохраняет старый ID; `toDomain()` молча превращает grounds text в `null`, а export продолжает выгружать несуществующий ID (`DecisionRepository.kt:84-99`). Кроме того, `recordOutcome()` проверяет только существование decision и разрешает outcome до choice (`DecisionRepository.kt:48-57`); UI всегда показывает `Report outcome`, даже когда choice отсутствует (`DecisionsScreen.kt:180-228`). Choice write-once, отдельного исправления choice и управления отдельным outcome нет.

**Почему это ошибка:** граф теряет referential integrity, а цепочка может иметь `outcome` без `choice`. Это не просто неполный UI: в хранилище появляются состояния, которые противоречат заявленной модели решения и не могут быть корректно объяснены.

**Шаги исправления:**

1. Выбрать явную FK-политику для удаления source revision: предпочтительно `RESTRICT` с deletion review либо `SET NULL` вместе с immutable tombstone/snapshot; не оставлять сырой dangling ID.
2. Добавить Room migration, проверку orphan IDs и управляемую remediation уже существующих строк.
3. Описать state machine (`QUESTION -> GROUNDED? -> CHOSEN -> OUTCOME_REPORTED -> REVIEWED`) и валидировать переходы в repository, а не только в UI.
4. Разрешить исправление choice/outcome через новую versioned запись либо явное delete/recreate с audit trail.
5. Добавить отрицательные тесты: outcome-before-choice, missing source, source deletion, restart/export/import.

**Критерий приёмки:** база не может сохранить dangling grounds или outcome-before-choice; любой edit/review остаётся трассируемым.

### P1-11. M2 объявлен complete без реализации restore/import

**Затронутые коммиты:** `8996314`, `5eaa42e` и их документационные обновления.

**Доказательство:** `ROADMAP.md:141` отмечает backup/deletion/export новых объектов выполненными, а критерий `ROADMAP.md:149` требует, чтобы export **и restore** сохраняли graph identity/provenance. В production есть `ExportManager`, но нет importer/restore entry point, parser, ID remapping или round-trip test. Совпадения `restore` в тестах относятся к перезапуску repository, а не импорту backup.

**Почему это ошибка:** экспортированный ZIP нельзя восстановить. Поэтому критерий round-trip data safety не выполнен и M2 не может быть закрыт.

**Шаги исправления:**

1. Спроектировать versioned import manifest с валидацией схемы, checksums и политикой конфликтов ID.
2. Импортировать весь граф одной транзакцией с old-ID -> new-ID mapping и проверкой всех FK до commit.
3. Добавить explicit user preview/confirmation, rollback при любой ошибке и отчёт об отклонённых объектах.
4. Проверить round trip: export -> wipe fresh DB -> import -> структурное сравнение raw/hypotheses/conclusions/revisions/evidence/themes/decisions/outcomes.
5. До появления этой проверки вернуть M2 в статус incomplete и не использовать слово backup/restore для однонаправленного export.

**Критерий приёмки:** свежая установка восстанавливает экспорт без потери идентичности и provenance; повреждённый/неподдерживаемый архив fail-closed и не оставляет частичную БД.

## P2 — существенные замечания

### P2-01. Archive themes — односторонний «чёрный ящик»

**Затронутый коммит:** `872f424`.

**Доказательство:** после `archiveTheme` тема исключается запросом `WHERE archived_at IS NULL`; production UI/ViewModel не имеет списка архивных тем, restore или delete. Repository-метод `deleteTheme()` недостижим из UI. Диалог честно говорит, что restore/delete появятся позже (`ThemesScreen.kt:83-96`), но roadmap одновременно отмечает deletion/backup всех новых объектов выполненными. Параллельно M3 `dismissCard()` записывает только `themeId -> Long.MAX_VALUE` (`KnowledgeRepository.kt:242-247`): отмены нет, а будущая карточка другого типа для той же темы также будет навсегда скрыта.

**Почему это ошибка:** пользователь может скрыть тему или relevance card без доступного пути вернуть её. Suppression по одному `themeId` стирает различие между старой dismissed подсказкой и новым contradiction/изменившимся evidence той же темы.

**Шаги исправления:** добавить Archived themes, `unarchiveTheme`, явный delete с dependency preview и тесты archive/restart/restore/delete/export. Для cards хранить стабильную identity/fingerprint конкретной причины и версии данных, предоставить Undo/manage dismissed cards и определить, когда существенное новое evidence может создать новую карточку. До этого не отмечать полный lifecycle темы/dismissal завершённым.

### P2-02. Semantic links не защищены уникальными ограничениями

**Затронутые коммиты:** `872f424`, `5eaa42e`.

**Доказательство:** `theme_links` индексирует поля раздельно, но не имеет unique `(theme_id, conclusion_revision_id)` (`KnowledgeEntities.kt:154-186`). То же для `(conclusion_revision_id, source_raw_record_id)` у `evidence_links`. Repository делает check-then-insert вне единой атомарной операции (`KnowledgeRepository.kt:63-73`, `108-126`).

**Почему это ошибка:** двойной tap или параллельные coroutines могут обе пройти проверку и вставить дубликаты. Counts/coverage раздуваются, unlink может удалить сразу несколько строк, export содержит неоднозначный граф.

**Шаги исправления:** добавить composite unique indices; перед migration детерминированно дедуплицировать строки; заменить check-then-insert на атомарный insert/upsert с понятной conflict policy; добавить concurrent/double-tap тесты с различными ID.

### P2-03. Новые запросы масштабируются как N+1 и загружают граф без границ

**Затронутые коммиты:** `872f424`, `8996314`, `5eaa42e`, `dbaf17d`, `85e7bfe`.

**Доказательство:** candidates загружают **все** raw records, затем токенизируют/сортируют их и только после этого применяют `take(5)` (`KnowledgeRepository.kt:149-180`). Home выполняет themes -> links -> revision -> conclusion -> evidence вложенными запросами (`KnowledgeRepository.kt:214-238`). Search отдельно догружает conclusion/theme metadata (`KnowledgeRepository.kt:253-279`). Decision mapping делает query outcomes на каждое решение (`DecisionRepository.kt:69-99`).

**Почему это ошибка:** ценность продукта предполагает растущую историю, но стоимость экранов растёт как число объектов и связей; заметная Kotlin-обработка после Room query выполняется в coroutine вызывающего ViewModel.

**Шаги исправления:** создать DAO projections с `JOIN/GROUP BY`, batch queries и bounded limits; для поиска рассмотреть Room FTS; применять top-N до загрузки больших payload; вынести тяжёлую нормализацию на `Dispatchers.Default`; добавить benchmark/trace на 1k/10k records и query-count assertions.

### P2-04. Search трактует `%`/`_` как wildcard и скрывает ошибки knowledge search

**Затронутый коммит:** `8996314`.

**Доказательство:** DAO строит `LIKE '%' || :query || '%'` без escaping (`KnowledgeDao.kt:188-204`), поэтому пользовательские `%` и `_` меняют смысл запроса. Запрос читает все matching revisions, но repository затем молча отбрасывает все не-current versions (`KnowledgeRepository.kt:261-270`), хотя этап назван graph-wide revision search. `SearchViewModel.kt:53-58` превращает любую ошибку knowledge search в пустой список, а `SearchScreen` вообще не отображает `uiState.error`. Одна и та же запись дополнительно появляется как knowledge `RawRecord` и как legacy `Entry` без секций или дедупликации (`SearchScreen.kt:66-99`).

**Почему это ошибка:** поиск возвращает неожиданные широкие/дублированные результаты, старые revision nodes остаются недоступными, а повреждение графовой части выглядит как «ничего не найдено» или как неполный успешный ответ.

**Шаги исправления:** выбрать literal-search/FTS семантику; при LIKE экранировать `%`, `_` и escape-char через `ESCAPE`; явно решить, должны ли исторические revisions находиться напрямую, и реализовать это в DAO; объединить результаты в типизированные секции с stable keys/deduplication; в UI различать complete/partial/error result и очищать старую ошибку после success; добавить тесты специальных символов, Unicode, historical revision, ошибки одной из двух подсистем и ограничения размера результата.

### P2-05. Тесты не покрывают новые пользовательские поверхности и дают ложную уверенность

**Затронутые коммиты:** `872f424`–`85e7bfe`.

**Доказательство:** из нового Home UI Compose-тест проверяет только empty state (`HomeScreenTest.kt:16-29`). Нет UI-тестов RelevantCard, Themes/ThemeDetail, Connections, graph search navigation и Decisions. Repository-тест поиска меняет местами аргументы `linkConclusionToTheme(themeId, revisionId)` (`KnowledgeRepositoryTest.kt:151-155`), но проходит, потому что оба autogenerated ID равны 1. Нет тестов duplicate key, incoming-link deletion, русской токенизации, restore или production grounded-decision flow.

**Почему это ошибка:** число 22/22 подтверждает happy paths, но не широкие completion claims. Перепутанные однотипные `Long` создают особенно опасный false positive.

**Шаги исправления:** добавить тестовую матрицу по найденным P1; всегда создавать разные ID и использовать named arguments; добавить Compose navigation/state restoration/dynamic-font тесты; проверять DB invariants и export round trip, а не только count/type результата.

### P2-06. Action rows не адаптируются к 200% font scale и узкой ширине

**Затронутые коммиты:** `dbaf17d`, `4150573`, `85e7bfe`.

**Доказательство:** RelevantCard помещает четыре кнопки `Inspect / Continue / Dismiss / Later` в один не переносящий строки `Row` (`HomeScreen.kt:266-270`). Decision card аналогично собирает действия в один Row (`DecisionsScreen.kt:213-228`). Верхняя панель Home одновременно содержит пять `IconButton` действий (`HomeScreen.kt:103-118`), что уже близко к ширине compact device до учёта title/padding. Для новых M3/M4 экранов нет scaled-font Compose/runtime тестов.

**Почему это ошибка:** суммарная intrinsic/min-touch ширина элементов превышает доступную ширину телефона при крупном шрифте; Row не делает wrap, поэтому действия выходят за границы или обрезаются.

**Шаги исправления:** использовать `FlowRow`, адаптивную Column или overflow menu; второстепенные top-bar actions перенести в navigation/overflow; сохранить touch targets и логический focus order; добавить screenshot/semantics тесты при 200% font, compact width, landscape и длинной локализованной copy.

### P2-07. Echo Glass action dock не соответствует собственной спецификации

**Затронутые коммиты:** `192f9da`, `79be9d7`.

**Доказательство:** весь Review — `Column.verticalScroll`, а `ActionDock` находится внутри неё (`RecordScreen.kt:336-400`), то есть прокручивается вместе с контентом. Комментарий обещает opaque fallback API 26-30, но реализация для всех API одинаково использует `surface.copy(alpha = 0.92f)` без version branch (`RecordScreen.kt:411-419`). Disclosure и в collapsed, и в expanded state называется `Show full analysis` и не имеет явной expanded semantics (`RecordScreen.kt:360-376`).

**Почему это ошибка:** заявленный stationary functional layer не stationary; low-API fallback фактически не реализован; accessibility state control не сообщается корректно. Device-validation документ не может доказать отсутствующую ветку кода.

**Шаги исправления:** вынести dock в `Scaffold.bottomBar`/фиксированный overlay с корректными insets; реализовать реально opaque low-API style либо убрать ложное обещание glass; менять Show/Hide и выставить expanded/stateDescription semantics; повторить TalkBack, 200%, scroll/IME и API fallback tests.

### P2-08. Home продолжает бессрочно собирать неиспользуемый legacy timeline state

**Затронутый коммит:** `dbaf17d`.

**Доказательство:** `HomeUiState.entries` и `selectedCategory` остаются в ViewModel (`HomeViewModel.kt:18-27`), а новый Home UI их не читает. Тем не менее `load()` запускает бесконечный collect всех entries/category (`HomeViewModel.kt:42-78`); `selectCategory()` в production UI больше не вызывается. `RelevantCard` также принимает `onOpen`, вызывающая сторона его передаёт, но composable callback не использует (`HomeScreen.kt:156-165`, `223-274`).

**Почему это ошибка:** после перехода на meaning-first Home остались скрытый legacy subscription и мёртвые API/callback. Они выполняют лишние DB updates, усложняют состояние и могут порождать несколько collectors при повторном `load/selectCategory`.

**Шаги исправления:** удалить неиспользуемые fields/collector/API либо вернуть обоснованный consumer; для повторных загрузок использовать cancel/flatMapLatest; добавить тест, что один refresh не создаёт параллельные subscriptions.

### P2-09. Документация противоречит коду и сама себе

**Затронуты почти все feature-коммиты.**

**Доказательство:**

- `README.md:26` всё ещё объявляет Room v3, хотя текущая схема v5;
- таблица current persisted fields в `DATA_CONTRACT.md:28-48` не содержит decisions/outcomes и suppressed-card DataStore, а header/status остались от M0/2026-08-07; ниже тот же документ уже говорит о schema v5 (`DATA_CONTRACT.md:55-70`);
- `PRODUCT.md:33` ограничивает текущий scope M1, хотя roadmap закрывает M2/M3/M4;
- `ROADMAP.md:305-327` всё ещё называет next milestone M2 после коммитов M3/M4;
- M4 checkbox объявляет полную цепочку до revision завершённой и одновременно в скобках говорит, что revision deferred (`ROADMAP.md:229-233`); reminders отмечены выполненными при отсутствии reminder/follow-up реализации.

**Почему это ошибка:** roadmap является входом для следующего агента. Противоречивый источник правды приводит к дальнейшей работе поверх ложных completion claims.

**Шаги исправления:** после исправления кода синхронно обновить README/PRODUCT/DATA_CONTRACT/ROADMAP/JOURNAL; до этого снять неверные `[x]` и пометить этапы partial; для каждого статуса добавить ссылку на конкретный test/runtime artifact и честно перечислить deferred criteria.

### P2-10. Heuristic suggestions заменили ручной выбор вместо того, чтобы дополнять его

**Затронутый коммит:** `5eaa42e`.

**Доказательство:** до коммита `getLinkCandidates()` возвращал raw records остальных confirmed conclusions. После коммита он возвращает только records с положительным token-overlap и обрезает список до 5 (`KnowledgeRepository.kt:149-180`). Кнопка `Link a record...` существует лишь при непустом списке и выбирает `otherEntries.first()` (`DetailScreen.kt:363-367`); отдельного browse/search picker для остальных записей нет.

**Почему это ошибка:** если эвристика не видит overlap — в том числе для всей кириллицы из P1-06 — пользователь полностью теряет ручной путь связать известную ему запись. Даже при английском тексте запись за пределами top-5 недоступна. Низконадёжная подсказка стала gate для user-owned semantic action.

**Шаги исправления:** разделить API на полный paged/searchable manual candidate source и отдельный ranked suggestions source; всегда показывать `Browse/search records`; не выбирать молча первый элемент; исключать уже linked/current record на уровне query; тестировать no-overlap, >5 records, rejected suggestion и ручную связь произвольной записи.

**Критерий приёмки:** пользователь может связать любую допустимую запись независимо от результата локальной эвристики; suggestions лишь ускоряют выбор и никогда не ограничивают его.

## P3 — технический долг

### P3-01. Quality gate зелёный с предупреждениями и локальными дефектами качества

**Затронутые коммиты:** преимущественно `dbaf17d`–`85e7bfe`.

**Доказательство:** свежий lint содержит 61 warning, включая 3 `ObsoleteLintCustomCheck`, из-за которых часть navigation lint-checks пропускается; есть новые deprecated icon/test opt-in warnings. В `KnowledgeRepository.kt:209` и `:250` сигнатура и первая инструкция склеены на одной строке. Не все 61 warning созданы opencode, но новые коммиты не удерживают clean baseline и добавляют собственный шум.

**Почему это ошибка:** пропущенные custom checks уменьшают доказательность lint, а постоянный warning noise скрывает новые регрессии.

**Шаги исправления:** отдельно инвентаризировать baseline до первого opencode-коммита и новые warnings; исправить только добавленные регрессии; обновить совместимые lint/navigation/AGP версии в отдельном проверяемом изменении; включить `diff lint`/warning budget и форматирование в CI.

## Исправленные внутри проверенной серии проблемы

Эти пункты не входят в 22 открытых замечания:

1. `5eaa42e` заявил 19/19 instrumented tests без соответствующего нового теста; `806fd47` затем корректно заменил число на подтверждённые XML 18/18. Это хороший corrective commit, но показывает, почему test counts нужно брать из свежего artifact.
2. Повреждённые подписи opencode в записях журнала, появившиеся в `dbaf17d`/`4150573`, были исправлены в `85e7bfe`.
3. На текущей вершине свежие наборы действительно дают 33/33 JVM и 22/22 instrumented. Замечания выше остаются, потому что соответствующих тестовых сценариев в этих наборах нет.

## Рекомендуемый порядок исправления в следующей сессии

### Фаза A — сначала восстановить инварианты данных

1. P1-03: сделать revision links исторически неизменяемыми и определить migration/remediation.
2. P1-05: спроектировать deletion dependency plan для incoming evidence.
3. P1-10 и P2-02: добавить FK/state-machine/unique constraints и Room migration.
4. P1-11: спроектировать и доказать полный export/import round trip.

Не начинать UI-полировку до прохождения migration tests и отрицательных repository tests: иначе новые экраны закрепят неверную модель данных.

### Фаза B — вернуть достижимость продуктовых сценариев

1. P1-01 и P1-02: честный onboarding и реальное unsaved-draft recovery.
2. P1-06, P2-05 и P2-10: Unicode-токенизация, русская/metamorphic test matrix и независимый manual browse.
3. P1-07: source-citing Home и честные sparse states.
4. P1-08 и P1-09: grounded decision entry point и строгие ownership labels.

### Фаза C — UX, масштабирование и тестовая доказательность

1. P2-01, P2-06, P2-07: reversible archive, adaptive actions, корректный fixed dock/accessibility.
2. P2-03, P2-04, P2-08: batch/FTS/bounded queries, явные search errors, удаление legacy collectors.
3. P2-05: end-to-end Compose/device tests всех M2–M4 critical paths.

### Фаза D — только после зелёного completion audit обновить статусы

Синхронизировать `README.md`, `PRODUCT.md`, `DATA_CONTRACT.md`, `ROADMAP.md`, `JOURNAL.md`; вернуть `[x]` лишь критериям, для которых есть свежий автоматический или runtime artifact. Отдельно оставить API 26-30 как **непроверенный**, пока реально не выполнен соответствующий device run.

## Минимальный регрессионный набор после исправлений

- Room migration от v3/v4/v5 с реальными данными и проверкой всех FK/unique constraints.
- Revision v1/v2 с разными grounds/themes: прошлое не меняется.
- Deletion matrix: own conclusion, incoming support, incoming contradiction, decision source, audio failure/rollback.
- Export -> wipe -> import -> graph equality и corrupted archive fail-closed.
- Русские, английские, mixed-script, punctuation и no-overlap candidate cases; ручной link остаётся доступен независимо от heuristic score.
- Theme detail с двумя conclusions v1.
- Home: empty theme, unthemed conclusion, own-source-only, support, contradiction, dismissed/postponed/restart.
- Decision: UI-grounded creation, provenance labels, choice-before-outcome, source deletion policy, restart/export/import.
- Compose: 200% font, compact width, landscape, TalkBack focus/state descriptions, IME и process recreation unsaved draft.

После выполнения этого набора можно повторно оценивать M2/M3/M4 как завершённые. До тех пор корректный статус — реализованы отдельные срезы, но milestone contracts не закрыты.
