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
| P1-04 | pending dedicated UI duplicate-key oracle; production key uses `revisionId` |
| P1-05 | implemented graph/dependency preview; `externalEvidenceDeletionRequiresPreviewAndExplicitUnlink` passes, while app-owned audio path validation and bounded orphan-cleanup queue remain pending |
| P1-06 | implemented; `LinkCandidateRankerTest` содержит русские NFKC/Locale.ROOT golden и metamorphic cases |
| P1-07 | implemented; `HomeRelevanceBuilderTest` и connected `homeCoverageKeepsTypedStatesAndDispositionUsesExactFingerprint` |
| P1-08 | implemented path; grounded decision UI/repository/export fields exist, но full restart/export UI oracle остаётся pending |
| P1-09 | implemented; suggestion metadata is guarded and user-owned decision text is not labelled as EchoMind output |
| P1-10 | implemented; repository state guards cover missing grounds, outcome-before-choice, choice replacement after outcome, and nonblank choice |
| P1-11 | implemented round-trip and empty-profile preflight; connected negatives cover corrupt hash and unsafe archive paths; dangling FK, duplicate ID, unsupported version and missing-audio cases remain pending |
| P2-01 | implemented exact fingerprint Room dispositions and one-time legacy reset; full restart/manage-dismissed UI oracle remains pending |
| P2-02 | implemented; fresh connected migration suite covers the four duplicate/conflict classes and schema identity |
| P2-04 | implemented; connected wildcard/historical-revision search oracle passes |
| P2-08 | implemented; Home flow uses the typed repository batch and no legacy collector state |
| P2-10 | implemented; manual browse is independent of ranked top-5 suggestions and does not auto-confirm links |
| P2-03 | pending; no query-count benchmark fixture yet |
| P2-05 | partially implemented; the new negative/metamorphic seams have distinct tests, but no single exhaustive cross-package inventory exists |
| P2-06 | pending; accessibility matrix for 200% text, compact width and landscape is not freshly executed |
| P2-07 | pending; API 26–30 fallback and TalkBack/IME oracle is not freshly executed |
| P3-01 | implemented; fresh `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, JVM 36/36 and connected 35/35 artifacts captured |

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

## Completion artifacts

- Package 1: целевые JVM/Compose тесты и `git diff --check`.
- Package 2: fresh install schema identity и `MIGRATION_5_6` migration/invariant suite.
- Package 3: repository/UI/import tests и fresh device evidence.
- Final gate: свежие `testDebugUnitTest`, полный `connectedDebugAndroidTest`,
  `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`; затем синхронизация
  `DATA_CONTRACT.md`, `ROADMAP.md`, `README.md`, `JOURNAL.md` и export manifest.

Ни один milestone не отмечается завершённым до появления соответствующего
артефакта.
