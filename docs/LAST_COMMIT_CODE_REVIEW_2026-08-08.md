# Code review `8605d91` и сводка для следующего агента

Дата ревью: 2026-08-08  
Коммит: `8605d918ac3c0aa0f957dce3b12f657c9b7f906c` (`Implement opencode repair packages`)  
Режим: review-only; production-код не исправлялся  
Итог: **REQUEST_CHANGES**

## Проверенный объём и свежие артефакты

- 51 изменённый файл: 3955 добавлений, 479 удалений.
- Сопоставлены `OPENCODE_COMMIT_AUDIT_2026-08-08.md`,
  `OPENCODE_REPAIR_DECISIONS_2026-08-08.md`, traceability, data contract,
  roadmap и production/test diff последнего коммита.
- `:app:testDebugUnitTest --rerun-tasks`: **36/36**, failures/errors 0.
- `:app:connectedDebugAndroidTest`: **35/35** на Pixel 8 API 35.
- `:app:lintDebug --rerun-tasks`: BUILD SUCCESSFUL.
- `git diff --check HEAD^ HEAD`: без whitespace errors.

Зелёные suites подтверждают существующие happy paths, но перечисленные ниже
сценарии ими не покрыты. Коммит нельзя принимать как завершённый repair gate до
исправления P1.

## P1 — блокирующие замечания

### LC-P1-01. Production submit не использует атомарную операцию draft -> RawRecord

**Где:** `RecordViewModel.kt:114-147`, `RecordViewModel.kt:187-204`,
`ReflectionRepository.kt:46-62`.

`ReflectionRepository.submitCaptureDraft()` правильно создаёт RawRecord и
удаляет draft в одной Room transaction, но production-код её не вызывает.
`submitThought()` сначала вызывает `captureRawText()`, затем строит proposal и
только после этого отдельно удаляет draft. Если proposal падает, RawRecord уже
сохранён, draft остаётся; успешный `retry()` draft также не удаляет. После
recreation пользователь снова видит capture и может создать дубликат исходной
записи.

**Исправление:** использовать одну transaction только для submit draft ->
Entry/RawRecord/delete draft; анализ запускать после commit. Retry должен
продолжать уже созданный RawRecord, не возвращая сохранённый draft.

**Обязательный oracle:** ошибка анализатора после DB insert оставляет один
RawRecord и ноль draft после успешного retry; повторный запуск приложения не
предлагает повторно отправить тот же draft.

### LC-P1-02. Keep/Discard могут потерять или воскресить draft

**Где:** `RecordScreen.kt:99-136`, `RecordViewModel.kt:273-281`,
`RecordViewModel.kt:355-391`.

`Keep draft` сразу закрывает экран без принудительной записи: последний
debounced update отменяется в `onCleared()`. `Discard` запускает
`clearCaptureDraft()` в `viewModelScope`, после чего UI немедленно удаляет
экран; coroutine может быть отменена. Pending `draftSaveJob` перед discard не
отменяется/не join-ится и способен повторно вставить уже удалённый draft.
Дополнительно, очистка текста до пустой строки не сохраняется из-за раннего
`return`, поэтому старый текст восстанавливается после recreation.

**Исправление:** сериализовать draft writes; Keep и Discard должны завершить
DB/file operation до navigation. Discard сначала отменяет и дожидается pending
save, затем удаляет draft/audio. Пустое состояние должно удалить старый draft,
а не игнорироваться.

**Обязательные oracles:**

1. Изменить текст и сразу нажать Keep -> после recreation точный последний текст.
2. Сохранить `abc`, стереть до пустого и recreate -> `abc` не возвращается.
3. Нажать Discard до истечения 500 ms -> draft не появляется после restart, audio отсутствует.

### LC-P1-03. Deletion choice может удалить несвязанные decisions и evidence links

**Где:** `EntryRepository.kt:102-138`.

Проверка требует, чтобы все зависимости текущего plan присутствовали в choice,
но не запрещает лишние IDs. Затем repository без проверки удаляет каждый ID из
caller-provided sets. Передав валидный plan плюс ID чужой decision или link,
можно удалить несвязанный объект в той же успешной transaction.

**Исправление:** перечитать deletion plan внутри transaction и требовать точное
подмножество разрешённых IDs; отклонять любой extra/stale ID до первой записи.
Проверять ожидаемые row counts.

**Обязательный oracle:** choice с одним посторонним decision/link ID падает, а
весь граф остаётся byte-for-byte эквивалентным baseline.

### LC-P1-04. Restore preflight принимает недопустимые graph states

**Где:** `ExportManager.kt:270-357`, особенно `validateIdsAndReferences()`.

Проверяются существование parent IDs и уникальность link pairs, но не
проверяются `Conclusion.currentRevisionId`, принадлежность current revision
данной conclusion, допустимые status/relationship/origin values, derived
decision state и singleton draft ID/stage. Например, manifest, где conclusion A
указывает на revision conclusion B, проходит preflight и Room transaction:
`current_revision_id` не защищён FK. После restore приложение показывает чужую
revision как текущую для A.

**Исправление:** реализовать полный invariant validator из DR-04 до staging и
DB writes: current pointers, revision ownership/version uniqueness, enums,
suggestion metadata, outcome-before-choice, draft `id == 1` и допустимый stage.

**Обязательные oracles:** каждый invalid manifest даёт 0 новых DB rows и 0
retained files; отдельно проверить dangling/mismatched current revision,
invalid relationship/status, outcome without choice и draft ID != 1.

### LC-P1-05. Repository всё ещё создаёт decisions без current grounds

**Где:** `DecisionRepository.kt:19-55`,
`DecisionRepositoryTest.kt:94-151`.

`sourceRevisionId` остаётся optional с default `null`; если ID передан,
проверяется только существование revision, но не то, что она current. Три
connected-теста прямо создают ungrounded decisions и тем самым закрепляют
поведение, противоречащее DR-03 и новому roadmap-тексту о grounded decision.
UI requirement не заменяет repository guard.

**Исправление:** новый decision требует current revision. Nullable поле можно
оставить только для чтения legacy rows с явным legacy state. Добавить отдельную
операцию замены grounds, разрешённую до первого choice.

**Обязательные oracles:** null, dangling и historical/non-current revision
отклоняются repository-level; смена grounds после choice отклоняется.

### LC-P1-06. Home non-theme scopes отображаются и маршрутизируются неверно

**Где:** `HomeScreen.kt:168-179`, `HomeScreen.kt:301-341`,
`HomeRelevance.kt:14-29`, `KnowledgeRepository.kt:378-402`.

Для `UNTHEMED` compatibility getter возвращает `themeId = 0`, но coverage row
всегда вызывает theme route и выводит пустое `name`. Unfinished proposals тоже
добавляются в общий coverage как пустые non-theme items. Inspect для unthemed
card передаёт `rawRecordId` в detail route, который ожидает legacy `entryId`;
для unfinished card список source IDs пуст и кнопка Inspect ничего не делает.

**Исправление:** добавить typed navigation target (`Theme`, `Entry`,
`ReflectionProposal`) и передавать правильный legacy entry/hypothesis ID.
Unfinished proposals не смешивать с coverage current conclusions. Для
unthemed coverage показывать осмысленное имя и detail action, не theme 0.

**Обязательные oracles:** fixture с различными rawRecordId/entryId;
unthemed row открывает правильный Entry, unfinished Inspect открывает нужный
proposal, ни один non-theme item не вызывает theme route 0.

## P2 — существенные замечания

### LC-P2-01. Deletion preview и filesystem boundary не доведены до DR-02

`EntryDeletionPlan` не содержит theme-link/proposal details, хотя preview должен
перечислять их. После commit `File(path).delete()` выполняется без проверки
app-owned audio root/symlink и без bounded orphan-cleanup queue. Расширить plan,
добавить path validator и честное persisted partial-cleanup state.

### LC-P2-02. Decision transition surface неполна

`DecisionsScreen.kt:226-237` открывает Report outcome даже до choice; repository
отказывает, после чего экран переходит в общий error state. Нет удаления
конкретного ошибочного outcome и UI-confirmation для replaceChoice/grounds,
хотя DR-03 перечисляет эти переходы. Скрыть/disable invalid action и реализовать
оставшиеся переходы с UI/repository tests.

### LC-P2-03. Add-to-theme молча игнорирует pending conflict

`KnowledgeDao.insertThemeLink()` теперь использует `OnConflictStrategy.IGNORE`,
а `KnowledgeRepository.linkConclusionToTheme()` не проверяет возвращённый ID.
Если для пары уже есть pending inherited link, обычный Add ничего не меняет и
не сообщает пользователю. Либо исключать pending theme из picker, либо трактовать
явный Add как review/confirm существующей строки; silent success запрещён.

### LC-P2-04. Archive resource limits применяются слишком поздно и не полностью

`SettingsViewModel.restoreData()` сначала без лимита копирует URI в cache, а
`readAndValidateArchive()` читает весь manifest JSON до проверки entry count.
Нет лимита manifest/archive size, compression-ratio check и явного запрета
duplicate ZIP entry names из DR-04. Добавить bounded streaming copy и ранний ZIP
central-directory preflight до JSON decode/staging.

### LC-P2-05. Home dismissal UX не соответствует DR-06

После dismiss `HomeViewModel` просто обнуляет card; Undo отсутствует. Когда
knowledge есть, но eligible card нет, Home не показывает обязательное
`Nothing needs attention now`. Добавить reversible dismiss и явное спокойное
empty-attention state; проверить restart/Restore из Settings.

## План следующей implementation-сессии

Работать пакетами; после каждого пакета сначала получить красные oracles,
затем минимально исправить production seam.

1. **Capture/draft safety.** Закрыть LC-P1-01 и LC-P1-02, включая immediate
   Keep/Discard, blank edit, submit failure/retry и process recreation.
2. **Deletion authorization.** Закрыть LC-P1-03, затем LC-P2-01. Проверить
   extra/stale IDs, transaction rollback, app-owned audio boundary и cleanup state.
3. **Restore preflight.** Закрыть LC-P1-04 и LC-P2-04. Добавить полный набор
   negative archives до любых файлов/DB writes; затем audio playback round-trip.
4. **Decision invariants.** Закрыть LC-P1-05 и LC-P2-02. Переписать текущие
   ungrounded fixtures на реальные current revisions; legacy rows тестировать отдельно.
5. **Typed Home routing.** Закрыть LC-P1-06 и LC-P2-05; использовать разные
   raw/entry/conclusion/theme IDs, чтобы fixture не маскировал ошибочный route.
6. **Pending-link UX.** Закрыть LC-P2-03 и проверить concurrent duplicate insert.
7. **Оставшиеся честно отмеченные gates.** Query-count benchmark на 1k/10k,
   200%/compact/landscape/TalkBack/IME, API 26-30 fallback, full restart/export
   UI oracles для decisions и Home dispositions.
8. **Финальная валидация.** Свежие JVM, полный connected suite, lint, assemble
   app/androidTest и `git diff --check`; затем синхронизировать DATA_CONTRACT,
   ROADMAP, README, JOURNAL и traceability только по фактическим artifacts.

## Критерий завершения следующей сессии

- Все LC-P1 закрыты distinct negative/metamorphic tests и production fix.
- Ни один reject/preflight/delete failure не оставляет новых DB rows или files.
- Draft Keep/Discard/Submit детерминированы при немедленной navigation/recreation.
- Decision без current grounds создать нельзя ни через UI, ни repository.
- Home actions используют typed IDs и не содержат no-op/theme-0 routes.
- Полный свежий gate зелёный; незапущенные device/accessibility checks не
  помечены выполненными.
## First repair package status - 2026-08-08 (superseded)

The first repair package implemented the changes below; a later review found
additional restore, lifecycle, and documentation gaps. This section is not an
acceptance verdict.

- P2-01: deletion previews include theme links and reflection proposals; audio deletion is restricted to canonical app-owned roots, and failures are persisted in the bounded retry-batch `audio_cleanup_queue` (Room migration 6->7).
- P2-02: outcome reporting is gated on a choice; concrete outcomes can be removed; replacing grounds or a choice requires explicit confirmation in the UI.
- P2-03: repository-level theme linking checks the DAO insert result, so a duplicate or pending link is surfaced instead of silently ignored.
- P2-04: restore staging and ZIP preflight enforce archive/manifest/payload limits, duplicate-name rejection, safe paths, hash/size checks, and compression-ratio limits.
- P2-05: Home dismissal is reversible with Undo, and knowledge with no eligible card renders `Nothing needs attention now`.

Fresh validation after the repair:

- `:app:testDebugUnitTest`: 39 tests passed, failures/errors 0.
- `:app:connectedDebugAndroidTest`: 43/43 on Pixel 8 API 35.
- `:app:lintDebug`: successful.
- `git diff --check`: clean.

## Follow-up review resolution - 2026-08-08

The follow-up findings are addressed in the working tree:

- P1: restore accepts historical decision grounds and explicitly migrated legacy graph states without applying creation-time invariants to persisted data.
- P2: manifest audio payloads must match the exact set of graph references; unreferenced payloads are rejected before restore.
- P2: failed audio cleanup is persisted in Room v7, consumed by startup WorkManager with exponential backoff and bounded attempts, and exposed as partial-cleanup status in Settings.
- P2: restore URI staging and the ZIP/hash/encryption workload run on the injected IO dispatcher.
- Documentation now describes Room schema v7 and the non-exported cleanup queue.

Final validation:

- `:app:testDebugUnitTest`: 39 tests passed, failures/errors 0.
- `:app:connectedDebugAndroidTest`: 46/46 on Pixel 8 API 35.
- `:app:lintDebug`: successful.
- `git diff --check`: clean.
