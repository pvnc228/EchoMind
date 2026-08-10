# EchoMind: traceability плана исправлений opencode

Дата: 2026-08-08

Источник решений: `OPENCODE_REPAIR_DECISIONS_2026-08-08.md`.

Этот документ связывает замечание аудита с production seam, изменяемыми файлами
и независимым test oracle. Статус означает состояние текущей repair-сессии, а не
документированный milestone.

### Текущий статус repair-сессии

Колонка `Статус` в таблицах ниже — исходный baseline до реализации. Актуальная
оценка приведена здесь, чтобы отделить реализованный seam от ещё не закрытого
oracle:

| Finding | Текущее состояние и доказательство |
|---|---|
| P1-01 | implemented; `OnboardingScreenTest` проверяет text-first copy и отсутствие Voice Diary/automatic transcription |
| P1-02 | implemented Room draft and encrypted completed-audio path; connected repository coverage exists, but a fresh device process-death/interrupted-recording oracle remains pending |
| P1-03 | implemented; `reviseCreatesNewRevisionKeepsHistoricalLinksAndDoesNotRebaseThem` плюс отдельный pending-link review flow |
| P1-04 | implemented; `ThemeDetailScreenTest` renders two distinct conclusions at revision v1 and production LazyColumn keys remain `revisionId` |
| P1-05 | implemented graph/dependency preview; `externalEvidenceDeletionRequiresPreviewAndExplicitUnlink` passes, app-owned audio path validation and persisted bounded cleanup queue pass, including the 33-plus terminal/eligible regression |
| P1-06 | implemented; `LinkCandidateRankerTest` содержит русские NFKC/Locale.ROOT golden и metamorphic cases |
| P1-07 | implemented; `HomeRelevanceBuilderTest` и connected `homeCoverageKeepsTypedStatesAndDispositionUsesExactFingerprint` |
| P1-08 | implemented path; grounded decision UI/repository/export fields exist, но full restart/export UI oracle остаётся pending |
| P1-09 | implemented; suggestion metadata is guarded and user-owned decision text is not labelled as EchoMind output |
| P1-10 | implemented; repository state guards cover missing grounds, outcome-before-choice, choice replacement after outcome, and nonblank choice |
| P1-11 | implemented round-trip and empty-profile preflight; connected negatives now cover corrupt hash, unsafe path, dangling FK, duplicate ID, unsupported version, cross-conclusion current revision and missing-audio cases; migrated v2 → export → empty restore → export now compares canonical manifests |
| P2-01 | implemented exact fingerprint Room dispositions and one-time legacy reset; full restart/manage-dismissed UI oracle remains pending |
| P2-02 | implemented; fresh connected migration suite covers the four duplicate/conflict classes and schema identity |
| P2-04 | implemented; connected wildcard/historical-revision search oracle passes |
| P2-08 | implemented; Home flow uses the typed repository batch and no legacy collector state |
| P2-10 | implemented; manual browse is independent of ranked top-5 suggestions and does not auto-confirm links |
| P2-03 | partially verified; Home, Search, Decisions mapping, ranking payload, and Detail manual-candidate paths have bounded query/payload oracles; Detail uses paged `NOT EXISTS` manual browse, while CPU/FTS benchmarking for local ranking remains deferred |
| P2-05 | partially implemented; the new negative/metamorphic seams have distinct tests, but no single exhaustive cross-package inventory exists |
| P2-06 | partially verified; Home actions remain reachable at 320dp with fontScale 2 and evidence rows expose Button semantics on fresh API35 Compose coverage; landscape, Decisions and long-label matrix remain pending |
| P2-07 | pending execution; owner selected API26+API30 AVD coverage and authorized system-image downloads, but fallback and TalkBack/IME oracles are not yet run |
| P3-01 | implemented; fresh `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, JVM 40/40 and connected 50/50 artifacts captured |

### 2026-08-09 follow-up artifact

The non-blocking audio-cleanup follow-up is implemented without a schema bump:
`KnowledgeDao.getPendingAudioCleanup` selects only retryable rows below the
attempt limit, `AudioCleanupScheduler` uses `ExistingWorkPolicy.REPLACE`, and
`audioCleanupSkipsTerminalRowsAndRetriesEligibleRowsBeyondTheBatchWindow`
passes on fresh `Pixel_8_2` API 35. The remaining M2/M3/M4 completion oracles
listed below are not changed by this follow-up.

The restore negative matrix is now also covered on fresh `Pixel_8_2` API 35:
cross-conclusion current-revision mismatch, duplicate stable ID, unsupported
manifest version, dangling raw-record foreign key, and missing audio payload
are rejected before persistence. Each case leaves the target database empty and
does not add restore artifacts.

The Home compact/dynamic-text slice is partially verified on the same API35
runtime: the four relevant-card actions remain reachable through the
scrollable content at 320dp and fontScale 2, and evidence navigation rows use
explicit Button semantics. This is not API26-30, landscape, TalkBack, IME, or
DecisionsScreen evidence; those remain open rather than being inferred.

The Theme detail duplicate-key oracle now renders two different conclusions
whose revision version is `1`; stable `revisionId` keys keep both rows and
their distinct outcome labels visible. Home graph loading now uses JOIN queries
limited to current revisions and their reachable raw/evidence/theme/decision
rows plus proposed hypotheses. The 1k/10k fixture proves SELECT count does not
grow with unrelated history and rejects the former unbounded table scans.
At the time of this baseline, P2-03 remained partial because search,
Decisions mapping, and heuristic candidate scanning still required their own
bounded-query work.

### 2026-08-09 repair-gate performance artifact

The previously open P2-03 query/payload slice is now covered by fresh public
repository seams. Search uses three result projections; Decisions mapping uses
bounded JOIN loads for source revisions and outcomes; link candidates use a
joined theme-name lookup plus a raw-record ranking projection without audio
payload. The 1k/10k fixtures preserve result semantics and reject N+1 query
growth and full raw-record entity loading.

The first API26 full run caught SQLite's bind-variable limit in the initial
revision `IN (...)` batching. The JOIN replacement passed the rerun, and the
full connected suite passed 62/62 on each of cold-booted API26, API30, and the
API35 control. This closes the scoped query-count/payload repair, not CPU/FTS/
pagination benchmarking for local ranking or the broader product milestones.

## 2026-08-10 bounded repair follow-up

The Detail manual-candidate path no longer materializes a caller-sized `NOT IN`
list or `RawRecordEntity` archive. `KnowledgeDao.getManualLinkCandidateRows`
uses a three-column projection, `NOT EXISTS`, literal LIKE escaping, and
`LIMIT/OFFSET`; `KnowledgeRepository` bounds pages to 100 visible rows plus a
one-row lookahead. `DetailViewModel` keeps ranked suggestions separate from
manual pages, provides server-side search and `Load more records`, and ignores
stale search responses by generation.

The new public seams are:

- `detailManualCandidateLoadUsesBoundedProjectionWithManyLinkedRecords`
  (1,001 linked records; no full raw projection and no `NOT IN` bind list);
- `detailViewModelLoadsManualCandidatesAsPagesWithManyLinkedRecords` (real
  DetailViewModel load, bounded first page, server-side search);
- `decisionMappingDoesNotCombineChoiceAndOutcomeFromDifferentSnapshots`
  (concurrent readers/writer; every observed outcome has its choice);
- `migratedLegacyProfileRoundTripsThroughCanonicalManifest` (actual v2
  migration, export, empty restore, second export, canonical manifest equality).

Targeted Pixel 8 API 35 runs passed the Detail actual-flow oracle, the full
KnowledgeRepository class, DecisionRepository race oracle, and all seven
ExportManager tests. The subsequent full `:app:connectedDebugAndroidTest
--rerun-tasks` run passed **66/66** on `Pixel_8_2` API 35. The broader repair
gate remains open: this follow-up does not claim CPU/FTS benchmarking,
API26/API30 reruns after these source changes, or the deferred
accessibility/restart UI matrix.

## Package 1 — без изменения схемы

| Finding | Production seam / файлы | Test oracle | Статус |
|---|---|---|---|
| P1-01 | `ui/onboarding/OnboardingScreen.kt`, `res/values/strings.xml` | Clean onboarding содержит text-first copy, не содержит `Voice Diary` и `automatically transcribed` | pending |
| P1-04 | `ui/themes/ThemeDetailScreen.kt`, `domain/model/Theme.kt` | Две разные conclusions с revision v1 одновременно отображаются и не создают duplicate key | pending |
| P1-06 | `data/repository/KnowledgeRepository.kt` | NFKC/Locale.ROOT Unicode golden fixtures дают A, B в точном порядке; C отсутствует; punctuation/case/reordering не меняют результат | pending |
| P2-04 | `data/local/dao/KnowledgeDao.kt`, `data/repository/KnowledgeRepository.kt`, `ui/search/*` | `%`, `_` и escape-char ищутся буквально; historical revisions имеют согласованную семантику; knowledge error виден как error, не empty success | pending |
| P2-08 | `ui/home/HomeViewModel.kt`, `ui/home/HomeScreen.kt` | Один refresh не создаёт параллельные legacy collectors; dead state/callback не участвуют в production flow | pending |
| P2-10 | `data/repository/KnowledgeRepository.kt`, `ui/detail/DetailScreen.kt`, navigation | No-overlap и записи за top-5 всё равно доступны через manual browse/search; suggestion только ранжирует и не подтверждает | pending |
| P2-05 | существующие JVM/Compose/repository test seams | Каждый новый negative/metamorphic oracle имеет разные IDs и проверяет поведение через repository/UI seam | pending |

## Package 2 — `MIGRATION_5_6` и graph invariants

| Finding | Production seam / файлы | Test oracle | Статус |
|---|---|---|---|
| P1-03 | `ReflectionRepository`, `KnowledgeDao`, link entities, `AppDatabase` | v1 links остаются неизменными; v2 получает только self-source автоматически, внешние/theme links pending; `rebase*` отсутствуют | pending |
| P2-02 | `KnowledgeEntities`, `KnowledgeDao`, `AppDatabase` | Migration детерминированно обрабатывает 4 duplicate/conflict cases; новые links атомарны и unique | pending |
| P1-10 | `DecisionEntity`, `KnowledgeDao`, `DecisionRepository`, `AppDatabase` | FK `source_revision_id` RESTRICT; outcome-before-choice, missing source и invalid transitions отклоняются repository-level guards | pending |
| P1-02 | `RecordViewModel`, `RecordScreen`, `CaptureDraft` entity/DAO, `AppDatabase` | Text/audio draft переживает recreation/reboot; interrupted recording показывает interrupted state; discard/cancel/submit имеют точные outcomes | pending |
| P2-01 | Home suppression entity/DAO, `HomeViewModel`, `Settings` | Legacy theme-wide suppression очищается один раз; disposition хранится по fingerprint; dismiss/undo/postpone/restart работают по exact key | pending |
| P1-05 | `EntryRepository`, `KnowledgeDao`, deletion-plan domain/UI | Ordinary delete блокируется dependency preview; explicit unlink/delete меняет только выбранные links; cancel/failure не меняет граф | pending |

## Package 3 — product flows и restore

| Finding | Production seam / файлы | Test oracle | Статус |
|---|---|---|---|
| P1-07 | `HomeRelevance`, `KnowledgeRepository`, `HomeScreen`, `ThemeDetailScreen` | Coverage включает empty/unthemed/no-external/support/contradicted; карточка показывает source/revision IDs и deterministic why-now | pending |
| P1-08 | `DecisionsScreen`, navigation, `DecisionRepository` | Пользовательский путь conclusion → grounded decision → choice → outcome доступен без test API и виден после restart/export | pending |
| P1-09 | `DecisionsScreen`, `Decision`, export models | User-owned text никогда не называется `EchoMind suggested`; system suggestion требует author/source/status metadata | pending |
| P1-11 | `ExportManager`, settings restore entry point, import domain, `AppDatabase` | Manifest v5 round-trip на пустом профиле сохраняет IDs/statuses/content hashes; corrupt/nonempty/dangling/path traversal даёт 0 DB rows и 0 retained files | pending |
| P2-03 | DAO projections/batch queries, repositories | Query count и bounded payload остаются ограниченными на 1k/10k fixtures; результаты не требуют N+1 загрузки | pending |
| P2-06 | `HomeScreen`, `DecisionsScreen` | 200% font, compact width, landscape и длинные labels сохраняют доступные действия/touch targets/focus order | pending |
| P2-07 | `RecordScreen`, Echo Glass action dock | Dock stationary, API 26–30 opaque fallback, expanded semantics и scroll/IME/TalkBack oracle проходят | pending |
| P3-01 | lint/build configuration and source baseline | Новые warnings отделены от baseline; completion artifact содержит свежий lint и отсутствие новых локальных quality defects | pending |

## 2026-08-10 review findings: bounded Detail manual search

| Review finding | Production seam | Regression oracle | Status |
|---|---|---|---|
| P1: SQLite LIKE did not provide Unicode case-insensitive manual search | `KnowledgeEntities`, `AppDatabase` v8 migration, `KnowledgeDao`, `KnowledgeRepository` | `manualLinkSearchMatchesCyrillicRegardlessOfCase`; migrated v2 fixture asserts `КАРЬЕРА` -> `карьера` | resolved |
| P2: Search and Load more could issue different queries and discard the new result | `DetailViewModel`, `DetailScreen` | `detailManualSearchCannotBeOvertakenByLoadMore` with a held Room query and synchronous state assertions | resolved |

The full gate for this follow-up passed with JVM 40/40, connected
68/68 on `Pixel_8_2` API 35, lint, and `git diff --check`. The broader repair
gate remains open for CPU/FTS ranking benchmarks, post-change API26/API30
runs, and the deferred accessibility/restart-export UI matrix.

## Completion artifacts

- Package 1: целевые JVM/Compose тесты и `git diff --check`.
- Package 2: fresh install schema identity и `MIGRATION_5_6` migration/invariant suite.
- Package 3: repository/UI/import tests и fresh device evidence.
- Final gate: свежие `testDebugUnitTest`, полный `connectedDebugAndroidTest`,
  `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`; затем синхронизация
  `DATA_CONTRACT.md`, `ROADMAP.md`, `README.md`, `JOURNAL.md` и export manifest.

Ни один milestone не отмечается завершённым до появления соответствующего
артефакта.
