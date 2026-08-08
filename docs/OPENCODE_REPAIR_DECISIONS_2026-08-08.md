# EchoMind: решения перед исправлением opencode-аудита

Дата: 2026-08-08

Статус: рекомендуемый implementation baseline для следующей сессии

Связанный аудит: [OPENCODE_COMMIT_AUDIT_2026-08-08.md](OPENCODE_COMMIT_AUDIT_2026-08-08.md)

## Как использовать этот документ

Этот decision record закрывает архитектурные развилки, отмеченные после аудита. Следующий агент должен считать решения ниже частью ТЗ и не заменять их более простым поведением только ради прохождения существующих тестов.

Если реализация требует изменить одно из решений, агент должен **до изменения Room schema или данных**:

1. показать конкретный конфликт с `VISION.md`/`DATA_CONTRACT.md`;
2. предложить альтернативу и её migration/deletion последствия;
3. получить решение пользователя;
4. только затем менять схему.

Destructive migration, очистка пользовательской БД, молчаливое подтверждение semantic links и скрытое удаление зависимых пользовательских объектов запрещены.

## Traceability

| Decision record | Закрывает развилку/замечания аудита |
|---|---|
| DR-01 Revision links и unique identity | P1-03, P2-02 |
| DR-02 Deletion graph | P1-05, часть P1-10 |
| DR-03 Decision state machine/authorship | P1-08, P1-09, P1-10 |
| DR-04 Import/restore | P1-11 |
| DR-05 Unsaved draft/audio | P1-02 |
| DR-06 Home relevance/dismissal | P1-07, P2-01 |
| DR-07 Unicode candidates/manual browse | P1-06, P2-10 |

## Неподвижные инварианты

1. Raw source неизменяем; исправление создаёт новый объект, а не переписывает исходный.
2. Conclusion revision append-only. Историческая версия и её подтверждённые grounds/themes после создания не меняются.
3. Подтверждение одной revision не подтверждает links или wording другой revision.
4. Любой derived/system-authored текст имеет явные author, source и status. Пользовательский ввод не маркируется как EchoMind output.
5. Удаление сначала показывает dependency plan; база не должна сохранять dangling IDs.
6. Import/background/restart не могут переводить proposal/link в `CONFIRMED`.
7. Любая Home claim содержит точные source/revision IDs и воспроизводимую причину выбора.
8. Неизмеримый критерий не закрывает milestone: сначала фиксируется oracle, затем тест, затем реализация.

## DR-01. Links при создании новой conclusion revision

### Решение

Старые `EvidenceLink` и `ThemeLink` остаются привязаны к старой revision навсегда. Операции вида `UPDATE ... SET conclusion_revision_id = newRevisionId` запрещены.

При создании v2 из v1 действуют разные правила:

- intrinsic raw source берётся из `Conclusion.rawRecordId` и остаётся видимым для каждой revision без нового semantic confirmation;
- внешний supports/contradicts link может быть скопирован только как `PROPOSED_INHERITED`/`needs review`;
- theme membership может быть скопировано только как `confirmed=false`;
- pending links не участвуют в Home counts, relevance, capability claims и confirmed graph views;
- пользователь подтверждает или отклоняет каждый inherited link для v2 отдельно;
- отклонение не меняет link v1.

Если текущая схема продолжает хранить intrinsic source как обычный evidence link, для новой revision разрешено автоматически создать только этот системный source-link. Он должен быть отличим от user-confirmed external evidence и не должен считаться внешним support.

### Уже повреждённые данные

Точное положение links до `rebase*()` восстановить из schema v5 невозможно. Migration не должна придумывать историю.

- Для conclusions с `max(version) > 1` считать подозрительными external evidence/theme links, которые находятся на current revision: schema не позволяет отличить вручную созданный link от перенесённого.
- Оставить такой link на текущей revision.
- Evidence link пометить `origin=legacy_rebase_unknown`, `status=needs_review`; theme link — `origin=legacy_rebase_unknown`, `confirmed=false`, `reviewRequired=true`. Исключить их из confirmed counts до review.
- Не копировать его назад во все прошлые revisions.
- Показать пользователю одноразовую review surface.

Если для такой маркировки добавляется поле, оно включается в schema migration и export manifest.

### Обязательные тесты

1. v1 с external support и theme -> revise -> v1 links неизменны.
2. v2 получает self-source, но external/theme links имеют pending status.
3. До review v2 links не влияют на Home/evidence coverage.
4. Confirm одного v2 link не меняет v1; reject не удаляет v1.
5. Restart и export сохраняют обе версии и statuses.
6. В DAO/repository отсутствуют `rebaseEvidenceLinks` и `rebaseThemeLinks`.

### Unique identity и migration duplicates

- Active `EvidenceLink` уникален по `(conclusionRevisionId, sourceRawRecordId)`; один source не может одновременно быть active supports и contradicts для одной revision.
- `ThemeLink` уникален по `(themeId, conclusionRevisionId)`.
- Новые записи создаются атомарным insert/upsert под composite unique index; check-then-insert вне transaction не используется.

Перед созданием unique indices migration v5 -> v6 нормализует старые duplicates:

1. Полностью одинаковые evidence rows: сохранить row с минимальным ID.
2. Одинаковый relationship, но разные statuses: confirmed имеет приоритет над proposed; сохранить минимальный ID среди confirmed.
3. Разные relationships для одной пары: сохранить relationship row с максимальным ID только как provisional `needs_review`; в `reviewMetadata` сохранить sorted исходные ID/relationship/status, чтобы пользователь видел конфликт и выбрал supports, contradicts или remove. До review link не считается confirmed.
4. Duplicate theme links: confirmed имеет приоритет; `createdAt` — минимальный исходный timestamp; сохранить минимальный ID среди строк выбранного status.

Migration test обязан содержать все четыре варианта. Нельзя просто удалить `DISTINCT`-дубликаты без проверки conflicting values.

## DR-02. Политика удаления графа

### Решение по FK

Использовать следующие правила:

| Ссылка | DB policy | Поведение приложения |
|---|---|---|
| `Conclusion.rawRecordId -> RawRecord` | `RESTRICT` | Нельзя удалить source, пока пользователь не выбрал удаление conclusion вместе с ним |
| `EvidenceLink.sourceRawRecordId -> RawRecord` | `RESTRICT` | Показать incoming links; разрешить явно unlink их и удалить raw |
| `ConclusionRevision.conclusionId -> Conclusion` | `CASCADE` | Revision является частью удаляемой conclusion |
| Evidence/theme links -> revision | `CASCADE` | Links являются подчинёнными данной revision |
| `Decision.sourceRevisionId -> ConclusionRevision` | `RESTRICT` | Удаление revision/conclusion блокируется до явного решения по dependent decisions |
| `Outcome.decisionId -> Decision` | `CASCADE` | Outcome удаляется только вместе с явно удаляемой decision |

`SET NULL` и content tombstone в первом исправлении не использовать: они оставляют пользовательский объект без доказуемых grounds либо сохраняют контент, который пользователь намеревался удалить.

### User-visible deletion plan

До записи приложение показывает counts и названия зависимостей:

- own conclusion и число revisions;
- incoming evidence links из других conclusions;
- theme links;
- decisions и outcomes, ссылающиеся на удаляемые revisions;
- proposal, legacy entry и audio.

Разрешённые действия:

1. `Cancel` — ноль изменений.
2. Для raw, используемого только как external evidence: `Unlink N relationships and delete record`; unrelated conclusions остаются.
3. Для own source: `Delete conclusion and source`; если есть decisions, пользователь дополнительно выбирает удалить перечисленные decisions/outcomes либо отменяет операцию.
4. `Delete conclusion only` сохраняет raw source, если dependent decisions предварительно разрешены.

Нельзя удалять отдельную historical revision: это переписывает append-only историю. До отдельного redaction design пользователь удаляет всю conclusion.

### Порядок операции

После явного подтверждения одна Room transaction удаляет выбранные decisions/outcomes, incoming links, conclusion subtree, proposals, raw record и legacy entry в dependency order. Несвязанные themes/conclusions не удаляются.

Audio удаляется после DB commit. Ошибка filesystem показывается пользователю и записывается в bounded orphan-cleanup queue; нельзя утверждать полный успех, пока cleanup не завершён. Cleanup не должен обходить validated app-owned audio directory или переходить по symlink/reparse point.

### Обязательные тесты

1. Raw с incoming support/contradict link нельзя удалить обычной командой.
2. Explicit unlink+delete удаляет только link и raw; target conclusion остаётся.
3. Raw own-source требует delete-together choice.
4. Decision FK блокирует conclusion deletion; explicit dependent deletion проходит.
5. Cancel и любая ошибка до commit оставляют граф byte-for-byte эквивалентным.
6. Theme сохраняется после удаления linked conclusion.
7. Уже отсутствующий audio считается достигнутым состоянием; locked/unremovable или выходящий за app-owned directory путь даёт честный partial-cleanup state, а не ложный success.

## DR-03. Decision -> Choice -> Outcome state machine

### Решение

State хранить как производное от данных, а не как дублирующую строку, которая может разойтись с полями:

```text
CREATED
  question != blank
  choice == null
  outcomes == 0

CHOSEN
  choice != blank
  outcomes == 0

OUTCOME_REPORTED
  choice != blank
  outcomes >= 1
```

`REVIEWED -> optional proposed conclusion revision` остаётся отдельным незавершённым этапом. Пока нет review object и обычного confirmation flow, checkbox полного M4-chain должен быть снят.

### Разрешённые переходы

- `createDecision`: создаёт только `CREATED`.
- Grounds можно выбрать/заменить только до первого choice.
- `recordChoice`: `CREATED -> CHOSEN`, nonblank.
- `replaceChoice`: разрешён только при `outcomes == 0`, с явным confirmation в UI.
- `recordOutcome`: разрешён только из `CHOSEN` или `OUTCOME_REPORTED`.
- Несколько outcomes разрешены как отдельные датированные reports.
- Пользователь может удалить конкретный ошибочный outcome; удаление последнего возвращает derived state `CHOSEN`.
- После появления outcome choice нельзя менять in-place. Для другого решения пользователь создаёт новую независимую decision. Связь successor/supersedes не входит в schema v6 и требует отдельного будущего decision record.
- Удаление decision явно удаляет её outcomes, но не source revision.

### Authorship suggestion

До появления реальной EchoMind generation поле из текущего диалога переименовать в user-owned `Expectation`, `Option` или `Note`. Оно не может отображаться как `EchoMind suggested`.

Будущая AI suggestion допустима только как отдельный provenance-bearing объект/поля с:

- `author=echomind`;
- `sourceRevisionIds`;
- analyzer/model version;
- createdAt;
- proposal/confirmed/rejected status.

AI suggestion без grounds сохранить нельзя.

### Repository guards

Каждый переход проверяется внутри repository transaction. UI disablement не считается защитой. Нужны отрицательные tests на outcome-before-choice, missing source, choice replacement after outcome и concurrent double submit.

## DR-04. Import/restore

### Решение первой версии

Первая restore-реализация поддерживает только **полное восстановление в пустую БД**. Merge, overwrite существующего графа и selective import запрещены и остаются отдельным product milestone.

Если target содержит хотя бы один Entry/RawRecord/Theme/Decision/Draft, restore завершается до любых записей с сообщением `Restore requires an empty profile`.

### Формат

- После schema/invariant changes увеличить manifest version с 4 до 5.
- Сохранять исходные stable IDs: на пустой БД remapping не нужен.
- Manifest содержит schema/manifest version, exportedAt, объектные counts, sorted records, audio entry metadata и SHA-256 каждого файла.
- Active capture draft и card-suppression settings включаются как явно типизированные operational/raw объекты; secrets, API endpoint, encryption keys и biometric material не экспортируются.
- Plaintext ZIP остаётся явно предупреждённым user-owned export.

Если export не смог расшифровать хотя бы один referenced audio, полный backup завершается ошибкой до публикации ZIP и перечисляет проблемные записи. `.txt` placeholder и `incomplete but successful` manifest запрещены. Отдельный content-export без audio, если он понадобится, не должен называться backup/restore archive.

### Preflight до изменения состояния

1. Проверить magic/type ZIP, supported manifest version и JSON schema.
2. Ограничить общий размер, число entries и compression ratio против zip bomb.
3. Запретить absolute paths, `..`, duplicate entry names и выход из staging directory.
4. Проверить hashes, counts, unique IDs, все FK и currentRevision pointers.
5. Проверить допустимые enum/status/relationship values.
6. Убедиться, что target profile пуст.

Любая ошибка завершает preflight без DB/file changes.

### Commit protocol

1. Распаковать только validated files в app-owned staging directory.
2. Сразу re-encrypt plaintext audio во временные final-format files; удалить plaintext staging.
3. В одной Room transaction вставить объекты в parent-before-child order с исходными IDs.
4. При DB failure удалить staged encrypted files.
5. После crash orphan cleanup удаляет только files конкретной import session без DB references.

### Обязательные oracles

- `export -> empty profile -> restore -> export` даёт структурно эквивалентный canonical manifest: те же IDs, references, statuses и content hashes; различаться могут только exportedAt/archive metadata.
- Corrupt hash, missing audio, dangling FK, duplicate ID, unsupported version, path traversal и nonempty target: 0 новых DB rows и 0 retained files.
- После success audio воспроизводится, а plaintext temp отсутствует.
- Import/restart никогда не превращает pending link/proposal в confirmed.

## DR-05. Unsaved draft и temporary audio

### Решение

Room `CaptureDraft` является durable source of truth; `SavedStateHandle` используется только для быстрой UI recreation. Для текущего single-capture flow допускается ровно один active draft.

Минимальные поля:

- stable draft ID;
- text;
- encrypted completed-audio path и duration;
- capture stage без transient `RECORDING`;
- createdAt/updatedAt.

Text сохраняется debounce не более 500 ms, а также перед navigation/background lifecycle callback. Submit атомарно создаёт Entry/RawRecord и удаляет draft в одной Room transaction; при ошибке draft остаётся.

### Audio boundary

- MediaRecorder пишет активную запись во временный plaintext файл только в `noBackupFilesDir/capture_tmp` с непредсказуемым именем.
- При нормальном Stop файл немедленно шифруется, plaintext удаляется, encrypted path записывается в draft.
- Toolbar/system Back во время записи сначала корректно завершает и шифрует запись, затем показывает `Keep draft / Discard / Cancel`.
- При process death активный `.m4a` может быть невалидным: продукт **не обещает восстановить незавершённую запись**. На следующем старте stale plaintext удаляется, а UI сообщает `Recording was interrupted`; сохранённый text draft остаётся.
- Completed encrypted audio и text draft восстанавливаются после rotation, process recreation, reboot и обычного Back.
- Автоматически удалять содержательный draft по возрасту нельзя. Только explicit discard/submit; orphan plaintext без валидного draft очищается.

Full export/restore включает active draft как raw user-owned data. Он не становится RawRecord или confirmed object до Submit.

### Измеримые тесты

1. Ввести `abc`, дождаться 500 ms, recreate process -> точное `abc`.
2. Stop recording -> process recreation -> тот же duration/path, файл encrypted и воспроизводим.
3. Kill во время recording -> partial plaintext удалён, text восстановлен, показано interrupted-state.
4. `Discard` удаляет draft и его encrypted audio; `Cancel` не меняет данные.
5. Submit failure сохраняет draft; success оставляет ровно один RawRecord и ноль drafts.

## DR-06. Home relevance, card identity и dismissal

### Что считается evidence

- Intrinsic raw source доказывает происхождение, но **не** считается external supporting evidence.
- В counts участвуют только links текущей confirmed revision со статусом confirmed.
- Pending inherited links не учитываются.
- Contradiction всегда показывается отдельно от support count.
- Empty themes и unthemed current conclusions не исчезают из coverage.

### Coverage model

Использовать typed fields, а не выводить состояние из текста:

```text
CoverageItem(
  scope = THEME(themeId) | UNTHEMED(conclusionId),
  currentRevisionIds,
  evidenceState,
  hasOutcome
)
```

| Evidence state/flag | Oracle |
|---|---|
| `EMPTY_THEME` | scope=THEME и 0 current confirmed conclusions |
| `NO_EXTERNAL_EVIDENCE` | current revision есть, external confirmed supports/contradicts = 0 |
| `SUPPORTED` | external supports > 0, contradictions = 0 |
| `CONTRADICTED` | contradictions > 0 независимо от supports |
| `hasOutcome=true` | существует grounded decision с choice и хотя бы одним outcome; это отдельный flag, а не взаимоисключающий evidence state |

UI copy должна прямо отражать state, например `No confirmed conclusions`, `No external evidence`, а не общее «EchoMind мало знает».

### Детерминированная relevance policy

Использовать rule-based tiers и не называть их semantic/AI personalization:

1. `CONTRADICTION` — eligible сразу.
2. `UNFINISHED` proposal — eligible через 24 часа без действия.
3. `THIN_EVIDENCE` — eligible через 24 часа после confirmation/последнего graph change.
4. `SUPPORTED_THEME` — eligible через 7 дней после `lastGraphChangeAt`.

Внутри tier сортировка:

1. `lastGraphChangeAt DESC`;
2. для supported themes — `externalEvidenceCount DESC`;
3. `themeId ASC`;
4. `revisionId ASC`.

Clock инъецируется; production-код не вызывает `System.currentTimeMillis()` внутри builder. `lastGraphChangeAt` равен максимуму timestamps relevant revisions, links и outcomes. Для evidence links добавить `createdAt` и `createdAtEstimated`; v5 rows получают в migration timestamp соответствующей revision и `createdAtEstimated=true`, а не время обновления приложения. Новые links получают точное время и `false`.

Если knowledge есть, но eligible card нет, Home показывает coverage/recent и состояние `Nothing needs attention now`, а не first-use empty state.

### Card identity

Card key вычисляется из стабильных данных, а не только `themeId`:

```text
SHA-256(
  cardType |
  scopeId |
  currentRevisionId-or-hypothesisId |
  sorted(relevantLinkIds + relationship + sourceRevision/version) |
  sorted(relevantOutcomeIds)
)
```

Rename темы не меняет identity. Новая revision, новый contradiction/outcome или смена card type создаёт новую identity.

### Dismiss/postpone

- Хранить disposition в Room `HomeCardDisposition`, а не в comma-separated DataStore map по themeId. Минимальные поля: `cardKey` primary/unique, cardType, scopeType/scopeId, dismissedAt, postponedUntil, createdAt.
- Dismiss подавляет только точный card key бессрочно.
- Postpone подавляет точный key до timestamp.
- Новая identity может появиться снова, потому что evidence изменился.
- После Dismiss показывается Undo.
- Settings содержит список dismissed cards с Restore.
- Suppression records входят в backup/restore как operational user choice.

Legacy DataStore suppression нельзя безопасно сопоставить с fingerprint: после старого dismissal граф мог измениться. Поэтому v6 один раз очищает legacy theme-wide entries и показывает неблокирующее сообщение `Dismissed-card preferences were reset after the relevance update`. Автоматически подавлять current/future card по старому `themeId` запрещено; лучше честно повторно показать карточку, чем скрыть новое contradiction/evidence.

### Измеримые fixtures

1. Contradiction и thin одновременно -> выбран contradiction с точными source/revision IDs.
2. Два contradictions -> newest graph change; полный tie -> меньшие themeId/revisionId.
3. Empty theme присутствует в coverage, но не выдаётся как evidence-backed card.
4. Unthemed conclusion присутствует в coverage virtual group.
5. Own source only -> `NO_EXTERNAL_EVIDENCE`.
6. Dismiss key A -> A скрыт; добавление нового contradict link создаёт key B и B eligible.
7. Postpone до `T` -> до T скрыт, в T eligible.
8. Pending inherited link не меняет coverage/card; confirmation меняет identity и результат.
9. Records одного theme/domain не увеличивают counts другого.

## DR-07. Точные oracles для Unicode candidate heuristic

Это уточняет P1-06 и P2-10. Эвристика остаётся term-overlap, не semantic similarity.

### Нормализация

1. Unicode NFKC.
2. `lowercase(Locale.ROOT)`.
3. Tokens — последовательности `\p{L}` или `\p{N}`.
4. Отбросить tokens короче 3 Unicode code points и versioned RU/EN stop-list.
5. Stemming/синонимы не обещаются в этом исправлении.

### Ranking

- `score = sharedConclusionTokens.size + sharedThemeTokens.size`;
- исключить current raw и уже linked raw на уровне query;
- сортировка: `score DESC`, `recordedAt DESC`, `rawRecordId ASC`;
- top-5 применяется после стабильной сортировки;
- полный paged/searchable manual picker работает независимо от suggestions.

### Golden fixtures

```text
current:  "Карьерный проект требует решения"
theme:    "Работа"
raw A:    "КАРЬЕРНЫЙ, проект развивается"       -> shared conclusion = 2
raw B:    "Этот проект пока остановлен"          -> shared conclusion = 1
raw C:    "Сегодня хорошая погода"               -> 0, не suggestion
```

Ожидаемый порядок: A, B. Регистр, NFKC-equivalent representation, добавление пунктуации и перестановка input rows не меняют IDs/order. При равном score/newest time выигрывает меньший `rawRecordId`.

`работа` и `карьера` не обязаны совпадать: без stemming/semantic model это честный documented limitation, а не нестабильный тест.

## Порядок реализации

### Package 1 — без schema change

Можно выполнять сразу:

- P1-01 onboarding copy;
- P1-04 Compose key;
- P1-06 Unicode tokenizer и golden tests;
- P2-04 search escaping/error states;
- P2-08 dead Home collectors/callbacks;
- P2-10 independent manual browse;
- тестовое расширение, не зависящее от новой схемы.

### Package 2 — schema v6 и graph invariants

Одним согласованным `MIGRATION_5_6` после failing migration tests:

- immutable/pending inherited links;
- composite uniqueness и deterministic dedup/review policy;
- `Decision.sourceRevisionId` FK `RESTRICT` и repository transition guards;
- durable `CaptureDraft`;
- Room `HomeCardDisposition` и одноразовая очистка несопоставимых legacy DataStore suppression с честным UI notice.

Нельзя дробить это на несовместимые schema bumps без полного migration path от v5 и fresh-install schema validation.

### Package 3 — flows и restore

- dependency-aware deletion UI;
- grounded Decision UI и outcome management;
- evidence-citing Home с fixed policy;
- manifest v5 export + empty-profile restore;
- process-death/device/accessibility tests.

## Completion gate для следующего агента

Работа не завершена, пока одновременно не выполнено следующее:

1. Каждый затронутый P1/P2 связан с конкретным test name и artifact.
2. Fresh install и `MIGRATION_5_6` дают одну и ту же Room schema identity.
3. Нет destructive migration или неописанной потери данных.
4. JVM, full connected suite, lint и assemble запущены свежо; старые XML не используются.
5. Русские/metamorphic/negative tests и все oracles из этого документа проходят.
6. `DATA_CONTRACT.md`, `ROADMAP.md`, `README.md` и export manifest обновлены только после доказанной реализации.
7. Невыполненные M3/M4/restore criteria остаются unchecked и названы честно.

## Короткая инструкция для новой сессии

> Работай по `docs/OPENCODE_COMMIT_AUDIT_2026-08-08.md` и `docs/OPENCODE_REPAIR_DECISIONS_2026-08-08.md`. Сначала проверь текущую вершину и создай traceability table `finding -> files -> test oracle`. Выполняй packages по порядку. До `MIGRATION_5_6` сначала добавь failing migration/invariant tests; destructive migration запрещена. Не переноси confirmed links между revisions, не сохраняй dangling IDs, не подтверждай proposals/links при import или background work. После каждого package запусти свежие целевые tests, а перед завершением — полный JVM/connected/lint/assemble набор. Документационные `[x]` возвращай только при наличии точного artifact. Не исправляй критерий, ослабляя test oracle или меняя продуктовый контракт без явного согласования.
