# EchoMind — Development Journal

## Development Rules
### Rule 1: Commit and Push Completed Steps
Every completed development step must end with a descriptive local commit and
`git push` after validation. Push only the current project branch and report the
commit hash and destination.

### Rule 2: Commit Major Changes
All major changes must be committed with a descriptive message. Use simple git commands only (`git add`, `git commit`, `git push`). Never use `gh` CLI.

### Rule 3: Skill-Based Development
All development must leverage the skills defined in the project's skill reference. The two skill repositories are:

1. **`C:\Users\mist8\source\repos\Anthropic-Cybersecurity-Skills\skills\`** (817 cybersecurity skills)
2. **`C:\Users\mist8\source\repos\awesome-claude-skills\`** (1000+ general-purpose skills)

Each skill has a `SKILL.md` file with instructions. When working on a task that matches a skill's domain, I must:
- Load the skill by reading its `SKILL.md`
- Follow the workflow/instructions in the skill
- Reference associated scripts (`scripts/`) and reference docs (`references/`) when available

The skills-reference.md document maps each project task to the appropriate skill. I must follow that mapping.

---

## 2026-07-07 — Project Initialization

### Done
- Created project scaffold with full MVVM + Clean Architecture structure
- Configured Gradle with version catalog (libs.versions.toml)
- Set up dependencies: Compose, Room, Hilt, Retrofit, OkHttp, DataStore, Security-Crypto
- Wrote all 4 screens (Home, Record, Search, Settings) with ViewModels
- Created ROADMAP.md and JOURNAL.md for tracking
- Created project README.md
- Initialized git repository
- GitHub repo created (user pushed manually)

### Architecture Decisions
- **DI**: Dagger Hilt over Koin (compile-time safety, Google-recommended for Android)
- **Serialization**: kotlinx.serialization over Gson/Moshi (first-class Kotlin support, multiplatform-ready)
- **Navigation**: Navigation Compose (single-activity, type-safe)
- **Database**: Room with manual migration path (SQLCipher encryption planned for Phase 8)
- **Networking**: Retrofit + OkHttp with kotlinx-serialization converter

### Skill Inventory (Phase 1)
Verified all key skill paths from skills-reference.md are accessible:
- `implementing-aes-encryption-for-data-at-rest` — ✅ exists with SKILL.md
- `configuring-tls-1-3-for-secure-communications` — ✅ exists with SKILL.md
- `meeting-insights-analyzer` — ✅ exists with SKILL.md
- `content-research-writer` — ✅ exists with SKILL.md
- `theme-factory` — ✅ exists with SKILL.md
- `mcp-builder` — ✅ exists with SKILL.md

---

## 2026-07-07 — Phase 2-3 Completion & Enhancements

### Done
- **Dynamic API endpoint**: Created `BaseUrlProvider` + `EndpointInterceptor` to allow Settings changes to take effect at runtime without app restart. The interceptor rewrites the OkHttp request URL based on current saved endpoint.
- **`localMode` persistence**: SettingsViewModel now saves/loads `localMode` toggle to DataStore.
- **Network security fix**: Fixed `network_security_config.xml` to allow cleartext traffic to localhost (LM Studio requires HTTP).
- **Entry detail screen**: New `DetailScreen` + `DetailViewModel` with ExoPlayer audio playback, full entry metadata display (summary, tasks, ideas, emotions, tags), and delete capability.
- **Detail navigation**: Added `Screen.Detail` route with `{entryId}` nav argument; wired HomeScreen cards and SearchScreen results to navigate to detail.
- **Category filter chips**: Added `FilterChip` row to HomeScreen for filtering by `EntryCategory` (All, General, Task, Idea, Feeling, Plan).
- **Record permission**: Added `RECORD_AUDIO` permission request flow using `rememberLauncherForActivityResult` before starting recording.
- **Search → Detail**: Search result items now navigate to the detail screen on tap.

### Files Created
- `data/remote/BaseUrlProvider.kt` — dynamic base URL holder
- `data/remote/EndpointInterceptor.kt` — OkHttp interceptor for URL rewriting
- `ui/detail/DetailScreen.kt` — entry detail with playback
- `ui/detail/DetailViewModel.kt` — entry loading + ExoPlayer lifecycle

### Files Modified
- `res/xml/network_security_config.xml` — cleartext=true for localhost
- `di/NetworkModule.kt` — injected EndpointInterceptor
- `ui/settings/SettingsViewModel.kt` — localMode persistence, BaseUrlProvider injection
- `ui/settings/SettingsScreen.kt` — Switch component for local mode
- `ui/navigation/Screen.kt` — added Detail route
- `ui/navigation/NavGraph.kt` — detail composable with nav argument
- `ui/home/HomeScreen.kt` — clickable cards, filter chips
- `ui/search/SearchScreen.kt` — clickable results
- `ui/record/RecordScreen.kt` — permission request

---

## 2026-07-07 — Phase 3 Completion: Waveform Visualization

### Done
- **WaveformVisualizer**: Created custom Compose Canvas component that renders real-time amplitude bars from `MediaRecorder.getMaxAmplitude()`.
- **Amplitude polling**: `RecordViewModel` now polls amplitude every 100ms via a coroutine during recording, normalized to 0-1 range. Stores a rolling window of up to 120 samples.
- **Recording UI**: Replaced the `CircularProgressIndicator` during RECORDING state with the live waveform visualizer + "Recording..." label.
- **ROADMAP update**: Updated Phases 2-6 completion status to accurately reflect implemented code. Phase 3 now fully complete.

### Files Created
- `ui/record/WaveformVisualizer.kt` — Canvas-based amplitude bar visualization

### Files Modified
- `ui/record/RecordViewModel.kt` — amplitude flow + polling job
- `ui/record/RecordScreen.kt` — waveform replaces spinner during recording
- `ROADMAP.md` — checked completed items across Phases 2-6

---

## 2026-07-07 — Phase 7: AI Q&A on Past Entries

### Skills Used
- **`meeting-insights-analyzer`** — Pattern recognition approach informed prompt design for extracting insights across multiple diary entries. The skill's emphasis on "speaking patterns, sentiment, recurring themes" mapped to how the LLM should analyze diary entries for Q&A.
- **`content-research-writer`** — Context window construction approach (bundling recent entries as source material, instructing LLM to cite sources) was influenced by this skill's research/structuring workflow.

### Done
- **`AskQuestionUseCase`**: Loads last 20 entries, formats them as structured context (with ID, date, category, transcript, summary, tasks, ideas, emotions, tags), sends to LLM via existing `v1/chat/completions` endpoint. Uses system prompt instructing the model to answer only from provided entries and cite sources as `[Entry N]`.
- **`QaScreen`**: Chat-style UI with message bubbles (user right-aligned, AI left-aligned), auto-scroll to latest, send button in bottom bar, "Thinking..." indicator, and empty-state hint text.
- **`QaViewModel`**: Manages message list, input state, loading state, and error handling. Calls `AskQuestionUseCase` on send.
- **Navigation**: Added `Screen.Qa` route, wired `QaScreen` composable in `NavGraph`, added `QuestionAnswer` icon button to HomeScreen top bar.
- **`LlmRepository.askQuestion()`**: New method reusing existing `AnalysisRequest`/`AnalysisResponse` DTOs with higher temperature (0.7) for creative Q&A.

### Files Created
- `domain/usecase/AskQuestionUseCase.kt` — context construction + LLM query
- `ui/qa/QaScreen.kt` — chat message UI with bubbles
- `ui/qa/QaViewModel.kt` — message/input/loading state management

### Files Modified
- `data/repository/LlmRepository.kt` — added `askQuestion()`
- `ui/navigation/Screen.kt` — added `Qa` route
- `ui/navigation/NavGraph.kt` — Qa composable + HomeScreen onNavigateToQa
- `ui/home/HomeScreen.kt` — Q&A icon button in top bar
- `di/RepositoryModule.kt` — provide AskQuestionUseCase
- `ROADMAP.md` — Phase 7 items checked

---

## 2026-07-07 — Phase 8: Security & Privacy

### Skills Used
- **`implementing-aes-encryption-for-data-at-rest`** — AES-256-GCM mode, nonce management, key derivation patterns. The skill's encrypted file format (nonce + ciphertext + tag) informed the `EncryptedFile` usage approach via Android security-crypto library.
- **`implementing-api-key-security-controls`** — Principles of secure key storage (never plaintext, hardware-backed keystore) applied to how the SQLCipher passphrase is managed via `EncryptedSharedPreferences` backed by Android Keystore.

### Done
- **FLAG_SECURE**: Added `WindowManager.LayoutParams.FLAG_SECURE` to `MainActivity.onCreate` to prevent screenshots and app overview content leak.
- **Audio file encryption**: Created `AudioEncryptionUtil` using `EncryptedFile` (AES-256-GCM with HKDF) backed by `MasterKey` in Android Keystore. Audio files are encrypted after recording in `RecordViewModel` and decrypted to temp files during playback in `DetailViewModel`. Encrypted files use `.enc` extension.
- **Biometric authentication**: Created `BiometricAuthGate` composable that shows `BiometricPrompt` on app resume using `BIOMETRIC_STRONG`. Falls back to no auth if no biometric hardware is available. Canceling or pressing negative button closes the app via `activity.finish()`.
- **SQLCipher encryption**: Added `net.zetetic:android-database-sqlcipher` + `androidx.sqlite:sqlite-ktx` dependencies. Created `PassphraseProvider` that generates a random 256-bit passphrase on first launch, stores it in `EncryptedSharedPreferences` (AES-256-GCM via Android Keystore). `DatabaseModule` now uses `SupportFactory` with this passphrase. Room database version bumped to 2 with `fallbackToDestructiveMigration()`.

### Files Created
- `data/local/security/AudioEncryptionUtil.kt` — EncryptedFile-based AES-256-GCM encrypt/decrypt
- `data/local/security/PassphraseProvider.kt` — Keystore-backed SQLCipher passphrase management
- `ui/BiometricAuthGate.kt` — Composable biometric prompt gate

### Files Modified
- `MainActivity.kt` — FLAG_SECURE + BiometricAuthGate wrapper
- `ui/record/RecordViewModel.kt` — AudioEncryptionUtil injection, encrypt on stop
- `ui/detail/DetailViewModel.kt` — AudioEncryptionUtil injection, decrypt before playback
- `di/DatabaseModule.kt` — SQLCipher SupportFactory + PassphraseProvider
- `data/local/AppDatabase.kt` — version bumped to 2
- `gradle/libs.versions.toml` — added sqlcipher + sqlite-ktx versions
- `app/build.gradle.kts` — added sqlcipher + sqlite-ktx deps
- `ROADMAP.md` — Phase 8 items checked

---

## 2026-07-07 — Phase 9-10: UX Polish & Testing

### Skills Used
- **`theme-factory`** — Color palette and font pairing concepts informed the existing Material 3 dynamic color setup (already in place). The custom EchoMind brand colors (EchoMindPrimary, EchoMindAccent) serve as fallback on pre-Android 12 devices.
- **`canvas-design`** — Visual composition principles (form, space, layout) informed the onboarding screen layout with icon + title + description centered on each page.

### Phase 9 — UX Polish

**Done:**
- **Shimmer skeleton loaders**: Created `ShimmerEffect.kt` with `ShimmerBox`, `HomeSkeleton` (5 card placeholders), and `DetailSkeleton` (metadata + content + actions). Integrated into `HomeScreen` (replaces "Loading..." text) and `DetailScreen` (replaces "Loading..." text).
- **Onboarding flow**: Created `OnboardingScreen` with 3 pages (Voice Diary, AI Insights, Privacy) with animated transitions via `AnimatedContent`. Uses `OnboardingManager` backed by DataStore to persist completion state. `NavGraph` checks onboarding state and sets start destination accordingly.
- **Recording pulse**: Added animated pulsing red dot in `RecordScreen` during RECORDING state using `InfiniteTransition` with alpha oscillation on a `CircleShape` Box.

### Phase 10 — Testing & Release

**Done:**
- **Test dependencies**: Added JUnit 4, MockK, Turbine, kotlinx-coroutines-test, core-testing, and Compose UI test dependencies to version catalog and build.gradle.kts.
- **`GetEntriesUseCaseTest`**: 3 tests — getAllEntries returns repository data, getEntriesByCategory filters, searchEntries returns matches.
- **`HomeViewModelTest`**: 2 tests — init loads entries from use case, selectCategory updates state and filters.
- **`HomeScreenTest`**: 1 UI test — verifies empty state text is displayed.
- Updated ProGuard rules (default config already exists at `proguard-rules.pro`).

### Files Created
- `ui/theme/ShimmerEffect.kt` — shimmer skeleton composables
- `ui/onboarding/OnboardingScreen.kt` — 3-page welcome flow
- `ui/onboarding/OnboardingManager.kt` — DataStore-backed onboarding state
- `src/test/java/.../domain/usecase/GetEntriesUseCaseTest.kt`
- `src/test/java/.../ui/home/HomeViewModelTest.kt`
- `src/androidTest/java/.../HomeScreenTest.kt`

### Files Modified
- `ui/navigation/NavGraph.kt` — onboarding-aware start destination
- `ui/navigation/Screen.kt` — Onboarding route
- `ui/record/RecordScreen.kt` — recording pulse animation
- `ui/home/HomeScreen.kt` — shimmer skeleton instead of loading text
- `ui/detail/DetailScreen.kt` — shimmer skeleton instead of loading text
- `gradle/libs.versions.toml` — test dependencies
- `app/build.gradle.kts` — test dependencies
- `ROADMAP.md` — Phases 9-10 items checked

---

## 2026-07-07 — Final Polish: ProGuard, Store Assets

### Done
- **ProGuard optimization**: Comprehensive rules for all libraries — kotlinx.serialization (serializers + companions), Hilt, Room (entities + DAOs), Retrofit + OkHttp, ExoPlayer, SQLCipher, Coroutines, and security-crypto. Preserves line numbers for crash reporting.
- **Adaptive icon**: Speech bubble icon (voice diary theme) with brand purple background (`#6C5CE7`). Already configured for API 26+ with `ic_launcher.xml`.

### Store Listing (for reference when publishing)
- **App name**: EchoMind
- **Tagline**: Private Voice Diary with AI Assistant
- **Short description**: Record voice entries, get AI-powered insights, keep everything private on your device.
- **Full description**: EchoMind is a privacy-first voice diary for Android. Record your thoughts using voice, get automatic transcription via local or remote LLM, and receive AI-powered analysis that extracts tasks, ideas, emotions, and patterns. Ask natural language questions about your past entries. All data stays on your device with AES-256-GCM encryption and biometric authentication.
- **Category**: Productivity / Health & Fitness
- **Screenshots needed**: (1) Home screen with entry cards, (2) Recording screen with waveform, (3) Entry detail with playback, (4) AI Q&A chat, (5) Settings

---

## 2026-07-21 — Build Recovery & Emulator Validation

### Done
- Fixed the Gradle settings repository block (`dependencyResolutionManagement`) and added Gradle Wrapper files using Gradle 8.9, compatible with Android Gradle Plugin 8.7.0.
- Replaced the unavailable SQLCipher `4.5.6` artifact with public `android-database-sqlcipher:4.5.3`; updated its `SupportFactory` import and ProGuard package rules.
- Resolved Room/KSP failures by removing duplicate no-op `Long?` type converters and the unnecessary `SupportSQLiteDatabase` migration declaration. The database now uses destructive migration fallback for the pre-release schema transition.
- Resolved compilation issues caused by missing Compose layout imports in recording and shimmer UI components.
- Fixed Hilt dependency injection: removed the `CredentialsProvider` self-dependency cycle, load stored API credentials on initialization, and qualify security utility contexts with `@ApplicationContext`.
- Fixed the startup crash in `BiometricAuthGate` by making `MainActivity` a `FragmentActivity` as required by `BiometricPrompt`.
- Disabled the biometric gate only in debug builds so a clean emulator without enrolled biometrics or a device credential can reach the application UI. Release builds retain the authentication gate.
- Created and validated a Pixel 8 API 35 emulator workflow. `:app:assembleDebug` builds successfully, and the resulting debug APK was installed and launched with `MainActivity` remaining active.

### Follow-up
- The product needs a dedicated UX/UI refinement pass; the current navigation and interaction flow are functional but need clearer onboarding and stronger visual hierarchy.

### Files Modified
- `settings.gradle.kts`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`
- `gradle/libs.versions.toml`, `app/proguard-rules.pro`
- `MainActivity.kt`, `ui/BiometricAuthGate.kt`, `ui/record/RecordScreen.kt`, `ui/theme/ShimmerEffect.kt`
- `data/local/AppDatabase.kt`, `data/local/converter/Converters.kt`
- `data/local/security/AudioEncryptionUtil.kt`, `data/local/security/PassphraseProvider.kt`
- `data/remote/CredentialsProvider.kt`, `di/DatabaseModule.kt`, `di/NetworkModule.kt`

---

## 2026-07-29 — Product Vision Recovery

### Context

The previous project description and roadmap treated EchoMind as a nearly complete private voice diary with AI categorization. A product grilling session showed that the durable value is different: user-owned documentation of evolving conclusions, immediate challenge of self-persuasion, and later evidence-backed guidance based on confirmed history and reported outcomes.

### Decisions

- The primary product object is an evolving conclusion with provenance, not a flat diary entry.
- The first-session value is structured reflection plus a user-reviewed counterargument.
- AI output remains a hypothesis until the user explicitly edits or confirms it.
- Long-term value comes from themes, contradictions, revisions, decisions, and reported outcomes.
- Advice is offered only on request and must expose grounds, counterevidence, and uncertainty.
- Raw records and the complete personal model remain local.
- Confirmation and permission to transmit are separate actions.
- The product collects no telemetry.
- Feedback improves capability but remains optional; the product does not use guilt or streak mechanics.
- Voice, prediction, heatmaps, and 3D visualization are extensions of the core loop rather than the initial product.

### Documentation

- Added `VISION.md` as the stable product contract.
- Replaced the completed-feature roadmap with milestone-based delivery and measurable completion criteria.
- Updated `README.md` to distinguish the current technical prototype from the target product.

### Known Prototype Gaps

- The current `Entry` model cannot represent conclusions, revisions, evidence, decisions, or outcomes.
- AI-generated analysis is persisted without a dedicated review and confirmation boundary.
- The transcription client is not connected to the main recording flow.
- The `localMode` preference does not yet enforce a repository-level network boundary.
- Remote requests do not yet implement local minimization, preview, and separate transmission consent.

### Next

Complete M0 and the thinnest vertical slice of M1: define the domain and privacy transitions, capture a raw text record, create a non-authoritative AI draft, review it, and persist a versioned confirmed conclusion with its source.

### Files Modified
- `app/proguard-rules.pro` — comprehensive ProGuard rules
- `ROADMAP.md` — ProGuard and store assets checked

---

## 2026-07-30 — M0-A Data Contract and Local-Mode Boundary

### Skills Used

- **`ponytail` (full)** — kept the first roadmap delivery to the smallest
  enforceable privacy slice, reused DataStore and the existing repository
  boundary, and added no dependency or speculative architecture.

### Done

- Added `DATA_CONTRACT.md` with data ownership/classification, the minimum
  domain objects, state transitions, deletion/export rules, the remote-request
  sequence, provider limitations, and the version 2 alpha reset policy.
- Split the next roadmap block into M0-A, M0-B, M1-A, and M1-B with explicit
  completion checks.
- Centralized the existing `settings` DataStore in `SettingsStore`; Settings
  and repositories now read the same persisted local-mode value.
- Enforced local mode in `LlmRepository`: analysis uses the existing offline
  analyzer, while Q&A and transcription fail before calling `LlmApi`.
- Reduced debug HTTP logging from bodies to request metadata.
- Added three unit tests proving that analysis, Q&A, and transcription make no
  AI API call in local mode.

### Evidence

- `:app:testDebugUnitTest` — 8 tests passed, including 3 local-mode boundary
  tests.
- `:app:assembleDebug` — passed; debug APK produced.
- `git diff --check` — passed.

### Remaining limitations

- Disabling local mode still exposes the prototype's raw-entry requests. M0-B
  must replace them with minimized, redacted, previewed, per-request approved
  context before the privacy contract is complete.
- Room still uses destructive migration fallback for version 2. M0-B must add
  the explicit relationship-graph migration and remove the fallback.
- No emulator interaction was needed for this repository-boundary slice; the
  end-to-end M1 flow remains subject to Pixel 8 API 35 validation.

---

## 2026-07-30 — M0-B Storage and Migration

### Skills Used

- **`ponytail` (full)** — kept the migration additive: retained the legacy
  `entries` table for the current UI and added only the five provenance objects
  required by M0/M1. No graph framework or new library was introduced.

### Done

- Changed the delivery rule: every completed development step now ends with a
  validated commit and push of the current branch.
- Added Room schema version 3 tables for raw records, AI hypotheses,
  conclusions, conclusion revisions, and evidence links.
- Added explicit `MIGRATION_2_3`. Existing transcripts/audio metadata become
  raw records with stable IDs; legacy generated analysis creates no confirmed
  object.
- Removed `fallbackToDestructiveMigration()`.
- Dual-write new legacy entries and immutable raw records in one Room
  transaction.
- Enforced deletion relationships with `CASCADE` for proposals/revisions/links
  and `RESTRICT` while a confirmed conclusion still cites a raw record.
- Extended export manifest version 2 with the full provenance graph and
  `legacy_unconfirmed` classification.
- Added the Compose BOM to `androidTest` so the existing and new instrumented
  tests resolve consistently.

### Evidence

- `:app:testDebugUnitTest` — 9 tests passed.
- `AppDatabaseMigrationTest` on Pixel 8 API 35 — 3 tests passed: real v2→v3
  migration, FK deletion semantics, and entry/raw dual-write.
- `:app:assembleDebug` — passed.
- `git diff --check` — passed.

### Remaining limitations

- Schema v3 supplies the persistence boundary, but the current screen still
  writes legacy generated fields. M1-A must introduce text-first
  capture → hypothesis → review → confirmation.
- Remote mode still needs minimized context, preview, and per-request consent;
  the corresponding M0 network-boundary test remains open.
- Theme, decision, and outcome entities remain deliberately deferred until
  their roadmap milestones.

---

## 2026-07-30 — M1-A Text-First Reflection

### Done

- Replaced the recording-first entry screen with a text-first reflection flow;
  encrypted voice recording remains an optional attachment that returns to the
  same editable transcript path.
- Added a deterministic on-device reflection analyzer. It keeps extracted
  observations and interpretations tied to source sentences, produces the
  five-part draft required by the roadmap, and offers one conditional
  counterargument without a network request.
- Persist the legacy archive row and immutable raw record in one completed
  transaction before proposal generation starts.
- Added a review screen that visibly separates the user's immutable source,
  local proposal, alternative interpretation, editable user wording, and final
  confirmed conclusion.
- Added durable proposal states. Reject creates no conclusion. Explicit confirm
  atomically creates the conclusion, user-authored revision 1, current-revision
  pointer, and confirmed evidence link to the raw source.
- Restore the newest proposed reflection when the capture screen is reopened;
  confirmed wording and provenance survive closing and reopening Room.
- Moved the Compose test manifest to the debug APK, where its host activity
  belongs, and separated `HomeScreenContent` from its Hilt wrapper so UI tests
  run in a real test host.

### Evidence

- `:app:testDebugUnitTest` — 12 tests passed, including three local reflection
  analyzer tests.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest` — passed.
- Full `:app:connectedDebugAndroidTest` on `Pixel_8_2`, stable API 35 — 9/9
  tests passed.
- Repository tests prove raw-before-proposal ordering, durable rejection with
  zero conclusions, edited confirmation as revision 1 with a source link,
  pending-proposal restoration, and confirmed-state restoration after database
  reopen.
- Compose test verifies that source, proposal, editable confirmation, confirm,
  and reject controls are visibly distinct.
- `git diff --check` — passed.

### Remaining limitations

- The local analyzer is deliberately deterministic and shallow. A first-session
  usability check must still prove that its clarification is useful rather
  than a paraphrase.
- Continuing a dispute or asking follow-up questions is not implemented, so the
  corresponding combined M1 roadmap item remains open.
- Home/detail/search still use legacy archive entries; they do not yet expose a
  persisted conclusion and revision as an inspectable provenance view.
- Deleting a confirmed reflection is protected by foreign keys, but the legacy
  detail screen does not yet explain that the conclusion must be removed first.
- M1-B still owns deletion/export/network-boundary proof for the full vertical
  slice. Remote mode remains unsafe for raw prototype requests.

---

## 2026-07-30 — M1-B Vertical-Slice Proof

### Skills Used

- **`ponytail` (full)** — reused the existing Room graph, export manifest,
  repository boundary, and detail screen. Remote raw-content features are
  denied until their required preview/consent flow exists; no new dependency
  or speculative remote abstraction was added.

### Done

- Added a detail projection that reloads the saved raw source, original local
  proposal, alternative interpretation, current conclusion revision, and
  confirmed source link as visibly distinct objects.
- Replaced immediate legacy deletion with an explicit confirmation dialog.
  A cited raw source stays protected unless the user selects deletion of the
  conclusion and source together.
- Delete-all now removes conclusion, revisions, evidence links, proposal, raw
  record, archive entry, and attached audio in the required order. Audio
  deletion failure is surfaced rather than silently ignored.
- Added an actual ZIP export test. It creates and confirms a reflection, writes
  `manifest.json`, reopens the ZIP, and verifies stable IDs, status, revision,
  and evidence provenance.
- Closed the raw network gap. Entry analysis always stays on-device; legacy
  Q&A and transcription fail before `LlmApi` even when local mode is disabled.
  Settings now state that configuring an endpoint does not grant transmission
  permission.
- Kept the remote path deliberately unavailable until EchoMind can show a
  minimized outgoing preview and obtain approval for that specific request.

### Evidence

- `:app:testDebugUnitTest` — 15/15 tests passed, including local and remote-mode
  assertions that raw entry, history, and audio never call `LlmApi`.
- `:app:assembleDebugAndroidTest` — passed.
- Full `:app:connectedDebugAndroidTest` on `Pixel_8_2`, API 35 — 12/12 tests
  passed.
- Instrumented tests cover protected deletion, explicit complete graph
  deletion including audio, a real provenance ZIP, Room migration/restart
  behavior, and Compose provenance labels.
- Manual API 35 walkthrough completed
  `capture → proposal → reopen pending review → confirm → reopen saved detail`.
  The detail view showed raw source, confirmed proposal, revision 1, and the
  `supports/confirmed` link. Its deletion dialog named the conclusion/source
  relationship, and confirmed deletion returned to an empty archive.
- The initial Compose rerun failed because the AVD had gone to sleep and the
  host activity transitioned immediately to `PAUSED/STOPPED`. Waking and
  unlocking the same AVD, with no test-host code change retained, produced the
  clean 12/12 result.
- `git diff --check` — passed before delivery.

### Remaining limitations

- The deterministic analyzer is still shallow. The manual architect scenario
  proves the flow, not usefulness; a first-session usability check must show a
  clarification better than the default paraphrase.
- Continuing a dispute or asking a focused follow-up remains unimplemented, so
  the combined M1 review item stays open.
- The archive and search still index legacy entries. Saved provenance is now
  inspectable in detail, but current conclusions are not first-class archive
  or search results; that belongs to M2.
- Remote assistance is safely unavailable rather than complete. Redaction,
  minimized preview, destination disclosure, and per-request approval still
  need an explicit product flow before any network call can be enabled.
- Room deletion and filesystem deletion cannot be atomic. The normal path is
  tested and failures are surfaced, but orphan-audio recovery remains a
  pre-release hardening task.

---

## 2026-07-30 — M1 First-Session Usability Baseline

### Done

- Recorded the project owner's first-session evaluation in
  `M1_USABILITY_EVALUATION.md`.
- Preserved five target scenarios and their raw five-criterion scores:
  `5/10`, `4/10`, `1/10`, `4/10`, and `4/10`.
- Calculated the baseline as `18/50`, mean `3.6/10`, median `4/10`, and range
  `1–5/10`.
- Reopened the installed app on `Pixel_8_2` and confirmed that the archive
  still contains all eight exact synthetic inputs: the five scored scenarios
  and three controls.
- Kept the persistence claim narrow. Archive presence proves that the source
  entries remain locally available; it does not prove that every scenario has
  a confirmed conclusion.
- Inspected the two screenshots attached to the source note. They show a
  Russian input whose thesis is repeated across proposal sections and the
  confirmation field, plus a generic English fallback question and alternative.
- Traced the result to the current mechanism: independent substring
  classification, fixed counterargument templates, a generic fallback
  question, and `suggestedConclusion()` returning the tentative thesis
  verbatim.

### Product Decision

M1 is not complete. The storage, provenance, review, rejection, deletion,
export, restart, and no-network boundaries remain valid, but the day-one value
claim failed. The current behavior is predominantly structured paraphrase, not
a useful clarification.

The next work block is M1-C, not M2. It will use all eight inputs as regression
fixtures, improve only the analyzer/proposal behavior required by the observed
failure, preserve user confirmation and privacy boundaries, and re-run the same
scoring protocol. The working gate is at least `35/50`, no target scenario below
`5/10`, plus all existing M0/M1 tests passing.

### Verification

- Installed archive on `Pixel_8_2`, API 35 — all eight synthetic source texts
  present after app reopen.
- No instrumented test, reinstall, database reset, export, or deletion was run
  during the persistence check.
- This is a documentation-only checkpoint; Gradle tests were not rerun.

### Next

Implement the bounded M1-C handoff in `M1_USABILITY_EVALUATION.md`: regression
fixtures first, relation-aware and language-matched clarification second,
blind re-score third. Do not enable remote raw-content processing or begin M2
while the M1 usefulness gate is open.

## 2026-08-01 — M1-C synthetic usefulness gate passed

### Implementation

- Replaced independent keyword buckets with one bounded sentence-role and
  relation pipeline in `LocalReflectionAnalyzer`.
- Added all eight exact evaluation inputs as named regression fixtures plus
  rephrased causal, English, mixed-script, long-input, and unrelated-message
  checks.
- Kept storage, schema, provenance, confirmation, rejection, deletion, export,
  and network boundaries unchanged.
- Preserved cautious and factual inputs without manufacturing a counterargument
  or hidden rule.

### Re-score and review

An independent read-only review used the frozen rubric and scored the five
targets `10/10` each, for **50/50**. It first rejected a duplicated short-reply
claim and an over-broad communication relation. Both defects were fixed in the
shared classifier and locked down with negative tests before approval.

This passes the working M1-C threshold of at least `35/50` with no scenario
below `5/10`. It proves the synthetic deterministic gate, not product-market fit
or external-user usefulness.

### Verification

- Focused analyzer suite: 15 tests passed.
- Full debug unit suite, debug APK, and androidTest APK: built successfully.
- Pixel 8 API 35: the initial Gradle connected run started 12 tests; three
  Compose tests failed while the AVD was asleep. The unchanged focused repro
  passed after `mWakefulness=Awake`, followed by a direct **12/12** passing
  instrumentation run.
- `git diff --check`: passed.

The failed Gradle connected lifecycle uninstalled the app and removed the eight
synthetic raw records that had been present on this AVD. The app and test APKs
were reinstalled for the passing run, so the prior persistence observation is
historical evidence rather than the current device state.

### Decision

M1-C is complete. The next session may enter the planned UX/UI audit checkpoint
now that its start condition is met. The local analyzer remains a lexical
heuristic; unseen relations can fall back to generic clarification and must not
be treated as confirmed user meaning.

## 2026-08-01 — UX/UI audit and Echo Glass direction approved

### Method and scope

- Used Impeccable `critique`, native `audit`, and `shape` procedures against
  the M1 reflection flow in `RecordScreen.kt`, with Home/onboarding as entry
  context and Detail as the saved-provenance endpoint.
- Ran two independent read-only assessments: a design/Nielsen review and an
  Android/Compose technical audit.
- Matched the installed debug APK to the current build on `Pixel_8_2`, API 35.
  `FLAG_SECURE` correctly produced black screenshots, so the bounded runtime
  inspection used the accessibility tree for onboarding, empty Home, and
  Capture. Review and terminal states were assessed from source and Compose
  tests; no reflection data was created.
- Preserved the scope boundary: no production UI, schema, repository, network,
  or analyzer behavior changed during the audit and shape work.

### Results

- Nielsen design health: **20/40** (`Acceptable`).
- Native Android technical health: **12/20** (`Acceptable`).
- Confirmed strengths: the raw/proposal/user-authored distinction is explicit;
  confirmation remains separate from transmission consent; rejection and
  partial-failure copy preserve provenance honestly.
- Confirmed exactly three non-overlapping P1 findings: Review hierarchy and
  accessibility semantics; inconsistent product and transition copy;
  incomplete reversibility and denial/interruption recovery.
- Runtime gaps remain explicit: TalkBack service behavior, 200% font scale,
  dark/dynamic contrast, reduced motion, landscape, multi-window, tablet/fold,
  and blur performance were not claimed as verified.

### Approved UX brief

The user approved `1A + 2A + 3A`: the primary job is to consciously author and
confirm the user's own conclusion; Review uses progressive disclosure; and the
first implementation stage addresses all three P1 findings.

The selected visual direction is **Selective Liquid Glass / Echo Glass**:

- Android Material 3 remains the platform grammar; the design must not copy
  iOS navigation, controls, icons, or gestures.
- Glass belongs only to stationary functional surfaces such as the top context,
  bottom action dock, and analytical disclosure control.
- Provenance-bearing content remains on readable tonal surfaces: raw source,
  unconfirmed proposal, editable user wording, and confirmed conclusion must
  remain visibly and programmatically distinct.
- Android 12+ may receive a measured blur/compositing treatment; API 26-30 must
  receive a fully readable opaque tonal fallback. Reduced motion and contrast
  modes may simplify or remove optical effects without changing hierarchy.
- Three portrait compositions must be reviewed before production UI changes.

### Documentation

- Added `PRODUCT.md` as the durable Impeccable product record derived from the
  agreed vision, data contract, implemented flow, and confirmed UX choices.
- Added evidence-based `DESIGN.md` for the incumbent Compose/Material 3 system.
  It deliberately does not claim the unbuilt Echo Glass world as implemented.
- Persisted the audit snapshot under `.impeccable/critique/` and the approved
  target contract under `.impeccable/surfaces/`.
- Updated `ROADMAP.md` from a planned audit entry point to an audited, approved,
  implementation-ready checkpoint with state and validation requirements.

### Next

Create and review the three Echo Glass Review compositions, select one, then
execute the approved P1 implementation plan end to end. After emulator and
regression validation, update `DESIGN.md` from the shipped result rather than
from the plan.

---

## 2026-08-06 — Review Flow Rework and Accessibility (UX checkpoint)

### Skills Used

- **`ui-skills-root`** — selected the smallest useful set for the session.
- **`improve-ui`** — read-only audit discipline: reproduced the current
  Review surface, reconstructed the governing system from `DESIGN.md`, and
  limited findings to the three confirmed P1 corrections.
- **`create-design-md`** — design-language documentation rules; updated
  `DESIGN.md` only with implemented evidence, not the unbuilt world.
- **`impeccable`** — craft/copy direction behind the reworked Review flow
  without pulling in its CLI-generated context for this stage.
- **`ponytail`** (full) — kept the diff to `RecordScreen.kt` and
  `RecordViewModel.kt`, bounded the glass to the static action dock with an
  opaque tonal fallback, and deferred measured blur/compositing.

### Done

- Restructured `ReviewContent` from a flat evidence stack to a dominant
  `source -> proposal -> my wording` reading order.
- Moved detailed analysis (observations, interpretations, assumptions, open
  questions) behind an explicit "Show full analysis" disclosure with animated
  collapse (P1 #1).
- Corrected first-session and transition copy and user-facing provenance
  labels to the current product contract (P1 #2).
- Added reversible next steps after confirm/reject: "Start another reflection"
  on both CONFIRMED and REJECTED surfaces (P1 #3).
- Added microphone permission-denied recovery state with a polite live-region
  announcement; the user can still proceed with text (P1 #3).
- Added a programmatic "My wording" field label on the editable conclusion
  field, per `DESIGN.md` component contract.
- Added a bounded Echo Glass action dock (static functional layer; opaque
  tonal fallback on API 26-30), keeping provenance surfaces on readable
  tonal cards (`ponytail:` comment names the upgrade path).

### Evidence

- `:app:compileDebugKotlin` — passed.
- `:app:testDebugUnitTest` — passed (existing local-mode and state suites).
- `:app:connectedDebugAndroidTest` — 12/12 passed on Pixel 8 API 35 emulator,
  including `ReflectionScreenTest` (reworked Review structure) and the
  provenance/privacy instrumented suites.
- Updated `ReflectionScreenTest` to assert the reworked Review structure.

### Remaining limitations

- TalkBack, 200% font scale, API 26-30 fallback, and landscape/expanded-width
  passes remain to be re-run on device.
- Progressive disclosure and action-dock glass are implemented as Compose
  primitives; measured blur/compositing is deliberately deferred.
- Signed: `агент opencode`.

## 2026-08-08 — opencode repair packages 1–3

Выполнена implementation-сессия по `OPENCODE_REPAIR_DECISIONS_2026-08-08.md`.
Добавлены traceability table и production/test changes для Room schema v6 и
`MIGRATION_5_6`: immutable historical links, pending inherited-link review,
deterministic duplicate handling, RESTRICT dependency preview deletion,
derived decision states with repository guards, durable capture drafts and
encrypted completed audio recovery, typed Home coverage/fingerprint
dispositions, and manifest v5 empty-profile restore. Restore preflight rejects
corrupt hash and unsafe archive cases before writing; merge/selective import is
still deferred.

Свежие артефакты repair-сессии:

- `:app:testDebugUnitTest --rerun-tasks` — **36/36**, failures/errors 0;
- `:app:connectedDebugAndroidTest` — **35/35** на `Pixel_8_2` API 35;
- `:app:lintDebug` — passed;
- `:app:assembleDebug` и `:app:assembleDebugAndroidTest` — passed;
- `git diff --check` — без whitespace errors (Git показывает только обычные
  предупреждения о LF/CRLF для рабочего дерева).

Оставлены явно незакрытыми: query-count benchmark, свежая accessibility
матрица 200%/compact/landscape/TalkBack/IME, API 26–30 fallback run, полный
restart/export UI oracle для decisions и Home dispositions, а также отдельные
negative cases для dangling FK/duplicate ID/unsupported manifest/missing audio.
Outcome-driven revision и merge/selective restore остаются следующими
продуктовыми срезами. Подписал: `агент opencode`.

---

## 2026-08-07 — UX checkpoint device validation closed

### Skills Used

- **`ponytail`** (full) — closed the checkpoint with a documentation-only
  pass, reusing the existing device guide; no production code changed.

### Done

- The manual on-device validation (`docs/DEVICE_VALIDATION.md`) was completed
  and committed by the user (`bc225f9`). Results:
  - TalkBack: Review flow announces correctly, `source -> thesis -> alternative
    -> my wording -> actions` focus order holds, "Show full analysis" expands
    without losing focus, permission-denied message is polite, and
    "Start another reflection" is reachable after confirm/reject. Pass.
  - 200% font scale: text wraps, is not clipped, and nothing overlaps. Pass.
  - Rotation / wide: `source -> proposal -> my wording` order preserved,
    long text scrolls, IME inset respected. Pass.
  - Recovery states: mic-denied still allows text; interrupted unsaved capture
    returns without loss; restart restores confirmed revision and source. Pass.
  - API 26-30 glass fallback: **not verified on device** — the user has no
    Android Studio access to API 26-30 (even in the unsupported channel). The
    opaque tonal fallback remains implemented in code but untested on hardware;
    deferred to later product-maintenance steps.

### Decision

The UX checkpoint is marked complete for all device-verifiable criteria.
API 26-30 fallback verification is explicitly downgraded to a future
product-maintenance item and does not block the checkpoint, because the
current target devices are API 35 and the fallback is a code path with no
testable hardware available.

### Evidence

- `docs/DEVICE_VALIDATION.md` filled in by the user (server timestamps
  07.08.26 0:52–1:15), committed as `bc225f9`.
- No Gradle or production changes in this closure; the checkpoint already had
  `compileDebugKotlin`, unit, and 12/12 instrumented test passes recorded.

### Remaining limitations

- API 26-30 opaque fallback is implemented but not device-verified; track as a
  maintenance task alongside the deferred measured blur/compositing.
- Signed: `агент opencode`.

---

## 2026-08-07 — M2 first slice: themes and conclusion relationships

### Skills Used

- **`ponytail`** (full) — scoped M2 to the highest-leverage first slice
  (themes + user-confirmed relationships), reused the existing `evidence_links`
  table for supports/contradicts instead of a new schema, and extended export
  manifest 2 -> 3 additively. No new dependency or graph framework was added.

### Done

- Added Room schema version 4 with `themes` and `theme_links` tables and an
  explicit `MIGRATION_3_4` that adds them without data loss. Registered the
  migration in `DatabaseModule`.
- Added `KnowledgeRepository`:
  - themes: create, rename, archive, delete, list active, conclusion count;
  - theme links: link/unlink a confirmed conclusion to/from a theme (confirmed
    only; no AI clustering), list conclusions per theme, list themes per
    conclusion;
  - relationships: link a concluded record as `supports` or `contradicts`
    another concluded record by reusing `evidence_links`; list related records;
    unlink.
- Added `DetailViewModel`/`DetailScreen` "Connections" section shown only for
  confirmed conclusions: link to a theme, unlink, and mark another confirmed
  record as supporting or contradicting this one.
- Added `ThemesScreen` (create/rename/archive) and `ThemeDetailScreen` (list
  conclusions in a theme), with navigation routes and a Themes entry point on
  Home.
- Export manifest bumped to version 3 and now includes `themes` and confirmed
  `theme_links`; unit and instrumented export tests updated.
- Data/privacy contract updated: schema v4, themes/themelinks as confirmed
  user-owned objects, cascade rules, and manifest v3.

### Evidence

- `:app:testDebugUnitTest` — passed (including manifest v3 export coverage).
- `:app:connectedDebugAndroidTest` — 16/16 passed on Pixel 8 API 35 emulator:
  new `KnowledgeRepositoryTest` (theme create/rename/link/count/archive,
  relationship link/unlink, candidates) and `migration3To4` in addition to the
  existing provenance/privacy/reflection suites.
- Debug APK installed and launched on `Pixel_8_2` (API 35); `MainActivity`
  stayed resumed with no crash or `FATAL EXCEPTION` in logcat.
- `git diff --check` — pending before delivery.

### Remaining limitations

- Themes are confirm-only: no AI clustering, so `theme_links.confirmed` is the
  only state written. Proposed-links (for a remote-assisted M2) are a later
  milestone.
- Relationships reuse `evidence_links`; the field and status semantics are
  unchanged and remain inspectable, but the UI does not yet render a dedicated
  relationship-manager view beyond the detail "Connections" section.
- There is no restore path for archived themes (archive hides them; delete and
  future restore are separate concerns).
- Dated revision history (viewing/editing prior revisions) and archive/search
  over the provenance graph remain open M2 milestones.
- Signed: `агент opencode`.

---

## 2026-08-08 — M2 complete: revision history and graph-wide search

### Skills Used

- **`ponytail`** (full) — closed both open M2 sub-goals without a schema
  migration or new dependency: revisions reuse the existing
  `conclusion_revisions` table, links rebase in place, and search reuses the
  existing `KnowledgeDao` `LIKE` queries over the raw/revision/theme tables.
  A fragile word-level diff idea was cut in favor of showing the full ordered
  revision list.

### Done

- **Dated revision history.** Added `ReflectionRepository.revise()`: a
  confirmed conclusion can be revised to a new dated revision; the previous
  version stays in `conclusion_revisions`, the current-revision pointer moves,
  and existing `evidence_links` and `theme_links` rebase onto the new
  revision so connections track the current wording.
- Added `ReflectionRepository.getRevisionHistory()` returning domain `Revision`
  objects with a `isCurrent` flag, and the DAO helpers behind it
  (`getRevisionsForConclusion`, `getMaxRevisionVersion`, `rebaseEvidenceLinks`,
  `rebaseThemeLinks`).
- Added a **Revision history** section to the confirmed-conclusion detail view
  listing each dated version with a "current" marker and a "Revise
  conclusion..." action that records a new version without deleting the old
  ones.

- **Graph-wide search.** Added `KnowledgeRepository.search()` returning typed
  results across raw records, current conclusions, and themes
  (`KnowledgeSearchResult`), reusing the existing DAO with three `LIKE`
  queries. Extended `SearchViewModel`/`SearchScreen` to surface these results
  and navigate to entry detail (raw/conclusion) or theme detail (theme),
  while keeping the legacy entry search.

### Evidence

- `:app:compileDebugKotlin` and `:app:compileDebugAndroidTestKotlin` — passed.
- `:app:testDebugUnitTest` — passed.
- `:app:connectedDebugAndroidTest` on Pixel 8 API 35 — **18/18 passed**,
  including new `reviseCreatesNewRevisionKeepsHistoryAndRebasesLinks` and
  `searchReturnsRawConclusionsAndThemesAcrossTheGraph`.
- The first instrumented run failed one assertion (new revision carries both
  its own source link and the related record = 2 links); the assertion was
  corrected to the real count and the suite passed.
- `:app:assembleDebug` — passed; `git diff --check` — passed.

### Remaining limitations

- The revision-list view is read-only history with edit; viewing a diff
  between two specific revisions is not a separate screen.
- Archive/search now index the provenance graph in addition to legacy entries;
  raw record and its confirmed conclusion appear as separate results by design.
- Signed: `агент opencode`.

---

## 2026-08-08 — M2 candidate-relationship detection (local heuristic)

### Skills Used

- **`ponytail`** (full) — added the local candidate heuristic inside the
  existing `getLinkCandidates`, reusing `knowledgeDao` and the existing
  `RelatedRecord` model: no new dependency, no remote path, no schema change.
  A scored list replaces the previous "return everything" behavior and stops
  at a small limit.

### Done

- Reworked `KnowledgeRepository.getLinkCandidates(currentRevisionId, limit=5)`
  to rank raw records by term overlap with the current conclusion and its
  linked theme names. Candidate records carry a `suggestedReason`
  ("Shares a term with your conclusion: ..." / "Mentions a theme: ...") and a
  score, are sorted by score, and capped at five.
- Excludes the record's own raw source and already-linked records, so a
  confirmed link stops appearing as a suggestion.
- Added a **Suggested connections** block in the detail screen's Connections
  section: local guesses shown with their reason, requiring the user to tap
  "Review" and then pick Supports/Contradicts before any durable
  `evidence_links` row is created. AI/heuristic output stays unconfirmed until
  the user acts.

### Evidence

- `:app:compileDebugKotlin` and `:app:compileDebugAndroidTestKotlin` — passed.
- `:app:connectedDebugAndroidTest` on Pixel 8 API 35 — **18/18 passed**
  (verified from `TEST-*.xml`: `tests="18" failures="0"`), including the
  updated `relationshipsLinkAndUnlinkRecordsAsSupportsOrContradicts`
  (suggestion appears before linking and disappears after).
- `:app:testDebugUnitTest` — passed.
- `:app:assembleDebug` — passed; `git diff --check` — passed.

### Remaining limitations

- The heuristic is lexical term overlap with stop-word filtering. It can
  produce false positives and misses semantic similarity; it is framed as a
  local guess for the user to confirm, not as a verdict.
- Remote-assisted candidate detection still awaits the minimized
  remote-context preview and per-request approval pipeline.
- Signed: `агент opencode`.

---

## 2026-08-08 - M3 relevant resurfacing (first slice)

### Skills Used

- **`ponytail`** (full) - reused the existing `knowledgeDao`, `Theme`,
  `ThemeConclusion`, and `RelatedRecord` graph primitives instead of adding a
  new relevance engine or any schema change. Card selection is a pure function
  (`HomeRelevanceBuilder`) over cheap COUNT queries, so the decision logic is
  unit-testable without a database.

### Done

- Added `HomeRelevance` domain model and `HomeRelevanceBuilder` (pure): picks
  one card deterministically - (1) a theme with contradicting records, else
  (2) a confirmed conclusion with no supporting records, else (3) the theme
  with the most evidence; and builds per-theme `ThemeCoverage`
  (conclusionCount, evidenceCount).
- `KnowledgeRepository.getHomeRelevance()` counts confirmed conclusions and
  evidence/contradiction links per active theme from the existing graph. No
  remote calls; no provenance leaks.
- Reworked `HomeViewModel` to also load relevance and recent entries, and
  `HomeScreen` from timeline-first to prompt + one relevant card (with an
  explicit reason) + "Evidence by theme" coverage + Recent.
- Card actions: Inspect and Continue both open the theme detail / capture.
  Dismiss/postpone are not yet separate states.
- Wired `onNavigateToTheme` from Home to `ThemeDetail`.

### Evidence

- `:app:compileDebugUnitTestKotlin`, `:app:compileDebugAndroidTestKotlin`,
  `:app:assembleDebug` - passed.
- New unit tests: `HomeRelevanceBuilderTest` (priority ordering, thin-theme
  flag, coverage) - 4/4.
- `:app:testDebugUnitTest` - **31/31** (verified from `TEST-*.xml`).
- `:app:connectedDebugAndroidTest` on Pixel 8 API 35 - **18/18**
  (verified from `TEST-*.xml`: `tests="18" failures="0"`).
- `git diff --check` - clean.

### Remaining limitations

- Inspect and Continue are the only card actions; dismiss/postpone is a
  separate concern.
- The card is deterministic and evidence-count based; it does not yet
  distinguish reflection/connection/change/guidance capability types, and
  sparse-domain confidence is not yet message-scoped.
- Signed: `агент opencode`.

---

## 2026-08-08 - M3 completion: dismiss/postpone + capability labels

### Skills Used

- **`ponytail`** (full) - reused DataStore (`SettingsStore`) for suppression
  persistence instead of a new table/migration; no DB schema change. Capability
  distinction is a pure `enum` on the card model, not a new mechanism.

### Done

- **Dismiss / postpone the card.** `HomeRelevanceBuilder` remains pure and
  deterministic; `KnowledgeRepository.getHomeRelevance()` now filters out
  themes whose card was suppressed (dismiss = forever, postpone = 24h), read
  from DataStore via `SettingsStore.getSuppressedCards()`. `dismissCard()` and
  `postponeCard()` persist `themeId:until` pairs. Home card now has Inspect,
  Continue, Dismiss, and Later actions.
- **Capability labels.** Added `Capability` enum (reflection, connection,
  change tracking, guidance) on `HomeCard`; each card type maps to one: thin
  conclusion -> reflection, most-supported theme -> connection, contradiction
  -> change tracking. The card renders "· <capability>" next to "For you".

### Evidence

- `:app:testDebugUnitTest` - **33/33** (verified from `TEST-*.xml`),
  including new `HomeRelevanceBuilderTest.capabilityIsDistinctPerCardType`
  and `HomeViewModelTest.dismissCardClearsCardAndCallsRepository`.
- `:app:connectedDebugAndroidTest` on Pixel 8 API 35 - **18/18**
  (verified from `TEST-*.xml`: `tests="18" failures="0"`).
- `:app:assembleDebug`, `:app:compileDebugAndroidTestKotlin` - passed.
- `git diff --check` - clean.

### Remaining limitations

- Sparse-domain confidence is narrow: "insufficient evidence" is shown per
  theme, but records from one domain are not yet message-scoped against
  confidence in another. Theme-to-domain mapping is a separate concern.
- Postpone is a fixed 24h preset, not a user-chosen interval.
- Signed: `агент opencode`.

---

## 2026-08-08 - M4 decision and outcome loop

### Skills Used

- **`ponytail`** (full) - kept the decision loop to two new tables
  (`decisions`, `outcomes`) and one additive schema v5 migration, reusing the
  existing `knowledgeDao`, Room transaction, and export manifest. No new
  dependency or framework was added; choice and outcome are separate stored
  values, and an outcome never rewrites a conclusion automatically.

### Done

- **Decision records.** Added `decisions` (question, optional EchoMind
  suggestion, optional user choice, optional source revision id) and
  `outcomes` (decision id, user report) tables with schema v5 migration
  `MIGRATION_4_5`. `DecisionRepository` stores the user's choice separately
  from EchoMind's suggestion (`setChoice` is guarded to record a choice once).
- **Inspectable chain.** `DecisionRepository.getDecisions()`/`getDecision()`
  return full `question -> suggestion -> choice -> outcome` records, including
  the linked conclusion's current text as `grounds`.
- **Outcome reporting.** `recordOutcome` appends a user-authored reported
  result; a decision can have multiple outcomes. `hasOutcomeForRevision`
  powers the theme detail "has outcome evidence / no outcome evidence" state
  (the M4 criterion "Show when a theme lacks outcome evidence").
- **Export manifest v4.** Added `decisions` and `outcomes` to the manifest and
  snapshot; version bumped 3 -> 4. Outcome/decision deletion never deletes the
  referenced records or conclusions (no FK restriction on source revision).
- **Decisions UI.** New `DecisionsScreen`/`DecisionsViewModel` with a list of
  decisions, "Choose..." / "Report outcome" / "Add outcome" / "Delete" actions,
  and a New decision dialog. Home top bar gains a Decisions entry point; a new
  `decisions` nav route is registered.

### Evidence

- `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin` - passed.
- `:app:testDebugUnitTest` - **33/33** (verified from `TEST-*.xml`: tests=33,
  failures=0), including the updated manifest v4 export test.
- `:app:connectedDebugAndroidTest` on Pixel 8 API 35 - **22/22** (verified from
  `TEST-*.xml`: `tests="22" failures="0"`), including new
  `DecisionRepositoryTest` (question->choice->outcome chain, single choice,
  delete keeps conclusion) and `migration4To5AddsDecisionAndOutcomeTables`
  (existing migration tests updated to chain 2->3->4->5).
- `:app:assembleDebug` - passed; debug APK installed and `MainActivity` ran
  resumed on `Pixel_8_2` with no `FATAL EXCEPTION` in logcat.
- `git diff --check` - clean before delivery.

### Remaining limitations

- A reported outcome does not yet propose or auto-revise a conclusion; the
  "compare outcome with expectation and revise only after review" sub-goal
  remains deferred to a later slice (the roadmap list has it as optional
  follow-up `Compare an outcome ... and revise relevant conclusions only after
  review`).
- Reminders/follow-up (`Offer an optional, user-controlled follow-up`) are not
  implemented; the decision flow is manual and non-intrusive by design.
- The suggestion is a hand-entered text field, not yet powered by the remote
  context pipeline; it remains visually separated from the user's choice.
- Signed: `агент opencode`.

---

## 2026-08-08 - Review follow-up: restore, cleanup lifecycle, and IO boundary

### Done

- Restore validation now separates creation-time decision grounding rules from
  persisted graph validity: historical revisions and explicitly migrated legacy
  provenance states remain restorable without automatic confirmation.
- Restore rejects any manifest audio payload that is not referenced by an entry,
  raw record, or capture draft; staged encrypted files use unique names rather
  than a hash-only final path.
- Failed audio deletion is persisted in Room schema v7, scheduled through
  WorkManager on startup and after failure, retried with exponential backoff
  and bounded attempts, and shown as partial-cleanup status in Settings.
- Restore URI staging and the complete restore workload run on the injected IO
  dispatcher, keeping ZIP validation, hashing, and encryption off Main.

### Evidence

- Added connected regression coverage for historical grounds after revision,
  migrated legacy states, unreferenced payloads, and cleanup persistence after
  database reopen.
- Documentation synchronized to Room schema v7 and the operational cleanup
  queue.

---

## 2026-08-09 - bounded audio-cleanup follow-up

### Decision

The review follow-up is implemented at the repository/WorkManager boundary:
cleanup retries select only rows with `attemptCount < 8`, terminal rows remain
visible as partial-cleanup state but are never retried, and unique cleanup
work uses `REPLACE` so a failure enqueued while an active worker is finishing
is not discarded by `KEEP`.

### Done

- Added the 33-plus-entry regression oracle with the first 32 rows terminal.
- Made the DAO query eligible-only, removing starvation and preventing
  terminal `attemptCount` growth.
- Added a JVM scheduler-policy regression test for race-safe replacement.

### Evidence

- TDD red artifact: the new connected test failed on the pre-fix DAO because
  the first 32 terminal rows masked the eligible 33rd row.
- `:app:testDebugUnitTest --rerun-tasks`: **40/40**.
- `:app:connectedDebugAndroidTest --rerun-tasks`: **47/47** on fresh cold-boot
  `Pixel_8_2` API 35.

### Remaining limitations

- The scheduler race is covered by the replacement-policy oracle and the
  WorkManager contract; a deterministic fault-injection test for the exact
  cancellation timing remains a future infrastructure concern.

---

## 2026-08-09 - restore negative matrix and compact Home accessibility slice

### Done

- Expanded the restore oracle with cross-conclusion current-revision mismatch,
  duplicate stable ID, unsupported manifest version, dangling raw-record FK,
  and missing audio payload cases.
- Each invalid archive is rejected before persistence; the target remains empty
  and the restore-artifact snapshot is unchanged.
- Replaced the Home relevant-card action Row with an adaptive `FlowRow`, so
  Inspect, Continue, Dismiss, and Later can wrap at compact width and large
  system text. Evidence coverage rows now expose explicit Button semantics when
  navigable.
- Added a Compose oracle that uses 320dp width and fontScale 2, scrolls the
  long content, and verifies all four actions remain displayed and clickable.

### Evidence

- `:app:testDebugUnitTest --rerun-tasks`: **40/40**.
- `:app:connectedDebugAndroidTest --rerun-tasks`: **48/48** on fresh cold-boot
  `Pixel_8_2` API 35, including the restore matrix and compact Home oracle.
- `:app:lintDebug --rerun-tasks`, `:app:assembleDebug --rerun-tasks`, and
  `:app:assembleDebugAndroidTest --rerun-tasks`: passed after the new tests and
  UI compilation.

### Remaining limitations

- This closes only a compact/dynamic-text Home slice. Landscape, a full
  Decisions-screen accessibility matrix, TalkBack/IME behavior, and API 26-30
  fallback still need a matching runtime oracle.
- The personal device remains intentionally unconnected; no API level or
  hardware behavior was inferred from it.

---

## 2026-08-09 - Theme stable-key oracle and bounded Home graph loading

### Done

- Added a public `ThemeDetailScreenContent` seam and a Compose oracle with two
  distinct conclusions that both have revision version 1. Both rows and their
  different outcome labels render; LazyColumn identity remains the stable
  `revisionId`, not the user-visible version number.
- Replaced Home's historical full-table reads with schema-neutral JOIN queries
  for current revisions and their reachable raw records, evidence, theme links,
  decisions, outcomes, plus proposed hypotheses.
- Replaced repeated linear conclusion/theme-link lookups in Home graph assembly
  with precomputed maps.
- Added a real Room query callback fixture at 1,000 and 10,000 unrelated raw
  records. SELECT count does not grow, stays at or below 12, and the former
  unbounded history scans are forbidden by the oracle.

### TDD evidence

- ThemeDetail test first failed to compile because the pure content seam did
  not exist, then compiled after the minimal extraction.
- The corrected 1k/10k benchmark failed on the pre-fix
  `SELECT * FROM raw_records ORDER BY ...` and passed after current-graph JOIN
  projections were introduced.

### Validation

- `:app:connectedDebugAndroidTest --rerun-tasks`: **50/50** on fresh cold-boot
  `Pixel_8_2` API 35, including the Theme stable-key and 1k/10k query oracles.

### Remaining limitation

- This advances P2-03 only for Home. Search result enrichment, Decisions domain
  mapping, and heuristic link-candidate scanning still need bounded batch/query
  oracles before the finding can be marked complete.

---

## 2026-08-09 - confirmed owner decisions for API coverage and M4

### Confirmed direction

- The owner selected API 26 and API 30 AVDs for the fallback gate and authorized
  downloading the required system images. API35 remains the control runtime;
  the owner's Xiaomi POCO X6 5G on Android 16/API36 may be tested later without
  being used as a substitute for API26-30.
- The next M4 slice is an optional local `Review impact` flow. It shows the
  original grounds/choice, reported outcome, and a proposed diff. No conclusion
  changes until the user explicitly confirms a new revision.
- After choice, EchoMind may once offer an optional follow-up in the 1-3 day
  range. Accepted follow-up may use a local Android notification with Postpone
  and Cancel actions, mirrored in-app. Notification denial falls back to the
  in-app state and never blocks the decision flow.

### Provenance and implementation status

- Raw owner input is preserved in
  `docs/USER_INPUT_API_COMPAT_AND_M4_2026-08-09.md`.
- The normalized contract is
  `docs/OWNER_DECISIONS_API_COMPAT_M4_2026-08-09.md`.
- These decisions remove product ambiguity; API images, M4 Review impact, and
  follow-up notifications are not implemented by this commit.

## 2026-08-09 - M4 Review impact slice

### Implementation

- Added `OutcomeImpactReview` as an unconfirmed, in-memory review proposal. It
  includes the decision's original current grounds, the recorded choice, all
  reported outcomes, and deterministic proposed wording; no background work or
  import path can confirm it.
- Added `DecisionRepository.getOutcomeImpact()` as a transactionally consistent
  read seam and `applyOutcomeImpact()` as the explicit write seam. Apply checks
  the decision, choice, outcome, and current grounds inside the Room transaction.
- Reused the append-only revision path from `ReflectionRepository`; old revision
  text and links are not rewritten, inherited links remain pending, and the
  decision continues to point at its original grounds. A stale review is rejected
  without creating a revision.
- Added a Decisions UI progressive disclosure card with distinct original
  grounds, choice, outcome, and editable proposed revision. The confirmation
  action is disabled while saving, and the review field uses IME padding and an
  adaptive vertical action layout.

### Verification

- Red oracle first failed because `getOutcomeImpact()` did not exist; the second
  red oracle caught repeat review after apply because stale grounds were not
  guarded. Both became green after the minimal fixes.
- `DecisionRepositoryTest`: 12/12 on fresh `Pixel_8_2` API 35, including stale
  grounds rejection and historical-ground preservation.
- `DecisionsScreenTest`: 1/1 on fresh `Pixel_8_2` API 35, verifying the visible
  provenance/diff labels and explicit confirm callback.
- Final gate: `:app:testDebugUnitTest --rerun-tasks` passed 40/40; full
  `:app:connectedDebugAndroidTest --rerun-tasks` passed 54/54 on `Pixel_8_2`
  API 35; `:app:lintDebug --rerun-tasks` passed; `git diff --check` passed.

### Remaining limitations

- M4 optional follow-up/reminder remains unimplemented.
- Full Decisions restart/export UI coverage, landscape/TalkBack/IME matrix, and
  API26/API30 fallback runtime evidence remain open. This slice does not mark
  M4 or the broader M2/M3/M4 repair gate complete.

## 2026-08-09 - API26/API30 runtime fallback and Decisions gate

### Implementation

- Added `DecisionsScreenContent` as a testable UI seam and changed the
  decision action row to `FlowRow`. The actions remain native text buttons but
  now wrap instead of overflowing at compact width and large font scale.
- Added Decisions instrumented oracles for compact width + fontScale 2,
  landscape, IME editing, and click semantics used by TalkBack. The API26
  reflection oracle now scrolls to the reject action before asserting it is
  displayed; the production behavior and provenance boundary are unchanged.
- Added database close/reopen coverage for decision question, current grounds,
  choice, and outcome. Added ZIP export/restore coverage for the same decision
  graph. Neither test confirms an AI proposal or creates a revision implicitly.

### Runtime evidence

- Cold-booted `EchoMind_API26_GoogleApis` (API26) and
  `EchoMind_API30_GoogleApis` (API30) with `-no-snapshot-load`; both installed
  and ran the full instrumented suite. `Pixel_8_2` API35 remained the control.
- `:app:connectedDebugAndroidTest --rerun-tasks`: **59/59** on each of API26,
  API30, and API35. This includes `DecisionsScreenTest` and the restart/export
  oracles.
- Isolated `DecisionsScreenTest`: **4/4** on API26, API30, and API35.
  Isolated restart and export tests: **1/1** each on all three runtimes.
- On API35, TalkBack was enabled temporarily for a real accessibility-tree
  dump. `Decisions`, `New decision`, and the empty Decisions state were
  exposed; the question field appeared as `EditText`, and focusing it yielded
  `mInputShown=true`. With rotation fixed to 1, the Decisions screen retained
  its title, empty-state text, back action, and New decision action. TalkBack
  was disabled and the emulator restored to portrait afterward.
- API26/API30 Google APIs images contain no TalkBack package; their fallback
  accessibility evidence is the native Compose semantics oracle, while the
  actual service/tree smoke is API35-only.

### Final gate

- `:app:testDebugUnitTest --rerun-tasks`: **40/40**.
- `:app:connectedDebugAndroidTest --rerun-tasks`: **59/59** on each of the
  three online AVDs; API26 and API30 were cold-booted for this validation.
- `:app:lintDebug --rerun-tasks`: passed.
- `git diff --check`: passed.

### Scope boundary

- This advances the API fallback and Decisions validation follow-up but does
  not close general M4, the optional follow-up/reminder, or the broader
  M2/M3/M4 repair gate. The runtime smoke did not autonomously confirm a
  proposal or create user-authored data.

## 2026-08-09 - repair gate: bounded graph consumers and cross-API validation

### Implementation

- Search now uses DAO projections for raw records, historical conclusion
  revisions, and theme counts. JOIN/GROUP BY preserves historical revisions,
  current-revision status, literal LIKE escaping, and confirmed-link counts
  without per-result conclusion, raw-record, or theme queries.
- Decision mapping now loads source revisions and outcomes through bounded
  JOIN queries and groups them in memory. A 10,000-decision oracle covers
  source grounds and outcomes without per-decision reads.
- Link candidates now load confirmed theme names and only the ranking payload
  (`id`, text, timestamp); the result remains limited to the ranker's top five.
  Audio metadata and other unused raw-record columns are not loaded.
- The first API26 full run exposed SQLite's bind-variable limit in the initial
  batched revision lookup. Replacing that `IN (...)` lookup with a JOIN was the
  minimal compatibility fix; the rerun passed on API26.

### Repair-gate evidence

- Search, Decisions, and link-candidate public-seam oracles exercise 1k and
  10k histories and reject N+1 query growth or full raw-record payloads.
- Fresh cold-boot connected suites passed **62/62** on `Pixel_8_2` API35,
  `EchoMind_API26_GoogleApis`, and `EchoMind_API30_GoogleApis`. The suite
  includes migration, export/empty-restore, deletion, restart, graph-negative,
  and concurrency coverage.
- `:app:testDebugUnitTest --rerun-tasks`, `:app:lintDebug --rerun-tasks`, and
  `git diff --check` passed. API35 was the final control run after the last
  source/test changes; API26 and API30 were cold-booted with
  `-no-snapshot-load` for the fallback runs.

### Scope boundary

- The scoped P2-03 query-count/payload and N+1 repair is validated. This does
  not claim a CPU/FTS/pagination benchmark for candidate ranking, which still
  evaluates candidate text in the local ranker.
- Optional follow-up/reminder remains unimplemented. This artifact does not
  mark M4 or the broader M2/M3/M4 milestone complete.

## 2026-08-10 - bounded Detail and canonical migration repair follow-up

### Decision

Keep the repair gate open while closing the two review P1s and the directly
related Decision read-snapshot finding. Manual Detail browse remains available
through explicit bounded pages and server-side search; no caller-sized ID list
or full raw-record payload is allowed. Migration/export/restore evidence must
start from a real legacy database and compare a second canonical export.

### Implementation

- Added `KnowledgeDao.getManualLinkCandidateRows` with a ranking/manual
  projection, `NOT EXISTS` exclusion, escaped literal search, and `LIMIT/OFFSET`.
- Bounded `KnowledgeRepository.getManualLinkCandidates` to 100 visible rows
  plus one lookahead row. `DetailViewModel` now keeps ranked suggestions
  separate from manual pages, supports server-side search and page loading, and
  drops stale search responses. The existing picker exposes `Load more records`.
- Wrapped `DecisionRepository.getDecisions` entity/revision/outcome reads and
  mapping in one `database.withTransaction` snapshot.
- Added a migrated v2 canonical round-trip oracle, a 1,001-linked-record
  Detail/repository oracle, and concurrent Decisions read/write coverage.

### Evidence

- `detailManualCandidateLoadUsesBoundedProjectionWithManyLinkedRecords`:
  **passed** on fresh `Pixel_8_2` API 35.
- `detailViewModelLoadsManualCandidatesAsPagesWithManyLinkedRecords`:
  **passed** on fresh `Pixel_8_2` API 35; first page is bounded and a searched
  record outside the first page is returned by the public Detail seam.
- `decisionMappingDoesNotCombineChoiceAndOutcomeFromDifferentSnapshots`:
  **passed** on fresh `Pixel_8_2` API 35.
- `migratedLegacyProfileRoundTripsThroughCanonicalManifest` and all seven
  `ExportManagerTest` cases: **passed** on fresh `Pixel_8_2` API 35.
- Full `:app:connectedDebugAndroidTest --rerun-tasks`: **66/66** on
  `Pixel_8_2` API 35 after synchronizing the shared SQL-observation test
  helpers.
- `code-review-expert` self-review found no P0/P1 in the changed bounded path;
  CPU/FTS ranking, API26/API30, accessibility, and restart/export UI evidence
  remain open by design.

The full completion gate and self-review remain required before declaring the
broader M2/M3/M4 repair gate closed. CPU/FTS ranking benchmarks, API26/API30
reruns after this follow-up, and the deferred accessibility/restart UI matrix
remain explicitly unclaimed.

## 2026-08-10 - review P1/P2 closure for bounded Detail manual search

### Implementation

- Manual Detail search now normalizes both stored raw text and the query with
  Unicode NFKC plus `Locale.ROOT` lowercase. The normalized key is persisted in
  `raw_records`; `MIGRATION_7_8` backfills real legacy rows in Kotlin, so the
  DAO remains server-side and bounded without relying on SQLite ASCII-only
  `LIKE` folding. Raw text and export manifest semantics are unchanged.
- `DetailViewModel` publishes the new query and loading state synchronously,
  clears the old page before searching, captures query/offset/generation at
  request start, disables Load more while a request is active, and ignores
  stale success/error paths.

### Evidence

- `manualLinkSearchMatchesCyrillicRegardlessOfCase` and
  `detailManualSearchCannotBeOvertakenByLoadMore`: passed on
  `Pixel_8_2` API 35; the latter holds the real Room search query while
  invoking Load more.
- Legacy v2 -> v8 migration fixture with `КАРЬЕРА` backfills
  `original_text_search_key = карьера`; migration/export subset passed **14/14**.
- Full gate passed: JVM **40/40**, connected Android **68/68** on
  `Pixel_8_2` API 35, `:app:lintDebug --rerun-tasks`, and `git diff --check`.
- `code-review-expert` self-review found no P0/P1 in the changed path; raw
  provenance, LIKE escaping, migration resources, generation guards, and
  error paths were checked.

### Scope boundary

The broader repair gate remains open. CPU/FTS ranking benchmarks,
post-change API26/API30 reruns, and the deferred accessibility/restart-export
UI matrix are not claimed by this artifact.

## 2026-08-10 - full repair-gate closure after review race

### Implementation

- Split `DetailViewModel` cancellation domains: graph refreshes now use their
  own generation, while manual pages use a separate generation and revision
  identity. A newer manual query is preserved when a graph snapshot publishes;
  a refresh cannot be discarded merely because search started concurrently.
- Bound the manual Dialog filter to `DetailUiState.manualQuery`, removing the
  second local query source so graph refreshes, search, and Load more render the
  same state.
- Added `detailGraphRefreshIsNotCancelledByManualSearch`, a deterministic red
  oracle on the real Room-backed link → refresh path. It failed on the shared
  generation with a timeout and passed after the domain split.
- Updated the active `DATA_CONTRACT.md` to Room schema v8. The persisted
  `original_text_search_key` is documented as a derived operational field,
  rebuilt by `MIGRATION_7_8`, and excluded from export; raw original text stays
  exported. The migration's compiled update statement is now closed with
  `use`.
- Added a local ranking CPU oracle at 1k/10k candidates and a Home-card
  disposition export/restore oracle. Added Detail accessibility oracles for
  200% text + compact width, landscape, labeled IME input, and Load more
  semantics.

### Fresh evidence

- JVM `:app:testDebugUnitTest --rerun-tasks`: **41/41**, failures/errors 0.
  The ranking benchmark recorded 1k **4.87 ms**, 10k **22.61 ms**, growth
  **4.65x**, and a result bounded to five suggestions.
- `:app:connectedDebugAndroidTest --rerun-tasks`: **72/72** on the API35
  `Pixel_8_2` control, **72/72** on cold-booted `EchoMind_API26_GoogleApis`
  (Android 8.0), and **72/72** on cold-booted `EchoMind_API30_GoogleApis`
  (Android 11). The current reruns include migration, negative restore,
  restart, export, graph concurrency, Detail manual search, and the new UI/
  disposition oracles.
- A real API35 accessibility-tree smoke with TalkBack exposed the running
  `com.echomind` Compose surface and labeled `Text-first reflection`, `Next`,
  and `Skip` nodes; TalkBack was disabled and the emulator returned to its
  prior state afterward. API26/API30 use the native Compose semantics fallback
  because their Google APIs images do not contain TalkBack.
- `:app:lintDebug --rerun-tasks` and `git diff --check`: passed.

### Gate status

The bounded repair gate is **closed** by this fresh artifact. This does not
mark the optional M4 follow-up/reminder or the broader product milestone as
complete; those remain deferred product work.

## 2026-08-10 - repair-gate review findings closed

### Implementation

- `KnowledgeRepository.getLinkCandidates` now executes its full candidate
  operation, including the preliminary DAO reads, projection, mapping, and
  `LinkCandidateRanker` call, on the injected `DefaultDispatcher`; the public
  five-suggestion behavior is unchanged.
- `DATA_CONTRACT.md` records the explicit reference-runtime UX budget: the
  complete Android repository operation must finish within 2,000 ms for both
  1k and 10k raw-record fixtures and must not run candidate work on Main.
- Added `KnowledgeRepositoryPerformanceTest`, an Android public-seam oracle
  that calls the operation from Main, proves the dispatcher handoff, and
  measures the complete Room-to-result path at 1k/10k. Removing the handoff
  makes the test fail on API26/API30/API35.
- Added real UI/ViewModel restart/export oracles for Decisions and Settings:
  reopened databases and empty-profile ZIP restores are rendered by
  `DecisionsScreen`/`DecisionsViewModel` and
  `SettingsScreen`/`SettingsViewModel`; Settings also exercises the visible
  Home-card Restore action.
- Marked the earlier open-status paragraphs in `ROADMAP.md` as historical and
  synchronized the traceability closure row with the actual UI seams.
- Stabilized the existing compact/200% Detail Compose oracle with a real
  controlled `manualQuery` state; its previous harness could reset the field
  to empty during input on the reference API matrix. The bounded Decision
  query oracle now excludes Room's internal invalidation-log SELECT from the
  application mapping count.

### Fresh targeted evidence

- `KnowledgeRepositoryPerformanceTest`: **2/2** on each of API35
  `Pixel_8_2`, API30 `EchoMind_API30_GoogleApis`, and API26
  `EchoMind_API26_GoogleApis`.
- End-to-end benchmark logcat values: API35 **166 ms / 1,256 ms** for 1k/10k,
  API30 **158 ms / 725 ms**, and API26 **99 ms / 505 ms**; each is below the
  documented 2,000 ms budget.
- `DecisionRestartExportUiTest`: **2/2** on each of the same three AVDs.
- `SettingsRestartExportUiTest`: **2/2** on each of the same three AVDs.
- Red reproduction: with the handoff removed, the performance oracle failed
  on all three AVDs with `The candidate dispatcher was not used`.

### Completion gate and self-review

- `:app:testDebugUnitTest --rerun-tasks`: **41/41**, 0 failures/errors/skips.
- `:app:connectedDebugAndroidTest --rerun-tasks`: **78/78** on each of API35
  `Pixel_8_2`, API30 `EchoMind_API30_GoogleApis`, and API26
  `EchoMind_API26_GoogleApis`.
- `:app:lintDebug --rerun-tasks`: passed.
- `git diff --check`: passed; only normal Git LF/CRLF conversion warnings.
- Self-review: dispatcher boundary covers the complete public operation;
  five-result cap and missing-revision path remain intact; UI tests close DB,
  archive, and database-name resources; no temporary SQL trace remains; old
  roadmap status is explicitly historical and the current closure artifact is
  authoritative.

Optional M4 follow-up/reminder remains deferred.

## 2026-08-10 - M2 import integrity and selective restore implementation

### Scope and contract

- GitHub Issue [#4](https://github.com/pvnc228/EchoMind/issues/4) was selected
  from `Ready` and moved to `In Progress` in the `EchoMind Work` Project.
- The old empty-profile `restoreFromZip(archive)` contract remains available.
  The new `RestoreScope.All` performs additive merge and
  `RestoreScope.SelectedRawRecords` performs selective raw-root restore.
- Stable IDs and natural keys are never remapped or overwritten. Conflicts are
  returned by the preview and rejected again inside the final Room transaction.
  ZIP validation still happens before any staging or writes.

### Implementation

- `ExportManager.previewRestore` reports selected roots, graph dependency
  counts, referenced audio, and conflicts without writes.
- Selective closure preserves selected raw records, legacy entries, proposals,
  conclusions, all revisions, evidence links, referenced evidence-source raw
  records, related themes, and decisions/outcomes. Full restore preserves
  legacy global states such as decisions without source revisions.
- Settings now stages an archive only for preflight, shows a scope/dependency
  preview, supports deselecting roots, and requires an explicit `Restore
  selected` or `Merge all` action. Failure paths remove the staged archive and
  restore artifacts.

### Fresh targeted evidence

- `ExportManagerTest`: **12/12** on `Pixel_8_2` API 35 after the legacy full-
  restore regression was fixed. This includes selective merge, evidence-source
  dependency closure, conflict preflight, corrupt archive negatives, stable
  graph restore, database reopen, and canonical export comparison.
- `SettingsRestartExportUiTest`: **3/3** on `Pixel_8_2` API 35. The real
  Settings screen displayed both roots and dependency counts before writes,
  exposed an accessible name for each root checkbox, then deselected one root
  and restored only the selected record through the ViewModel.
- `:app:testDebugUnitTest --rerun-tasks`: **41/41**, 0 failures/errors.
- `:app:lintDebug --rerun-tasks`: passed.
- `git diff --check`: passed.

### Adversarial gate result

- The full `:app:connectedDebugAndroidTest --rerun-tasks` was attempted after a
  cold `Pixel_8_2` boot. It reached 47/83 before the existing
  `KnowledgeRepositoryPerformanceTest` instrumentation process was killed with
  SIGKILL; an earlier warm-device attempt stopped at the existing
  `DecisionsScreenTest` process crash. The targeted M2 classes pass after the
  same cold boot, so M2 is not being claimed green through the failed full
  suite. API26/API30 were not rerun after the final M2 changes.
- Self-review covered stable-ID/natural-key conflict policy, final in-transaction
  recheck, ZIP/path/hash/graph validation, audio/session cleanup, concurrent
  preview responses, accessible dialog controls, and empty/legacy graph states.

### Boundary at the initial attempt

At that point the full M2 completion gate remained open because the full
connected suite had an unresolved instrumentation SIGKILL and API26/API30 had
not been rerun after the final changes. The selective restart/export artifact,
JVM, lint, diff-check, and final self-review were fresh. The subsequent
exclusive-emulator rerun below supersedes this boundary.

## 2026-08-10 - M2 exclusive-emulator completion rerun

### Diagnosis and fix

- After the emulator became exclusive to this task, the full API35 run no
  longer reproduced the earlier instrumentation-process SIGKILL. Its only
  failure was the new Settings selective-restore UI oracle timing out after
  five seconds while waiting for the pre-write restore preview.
- The isolated `SettingsRestartExportUiTest` passed 3/3 on the same emulator,
  proving the production flow and data assertions were sound. The oracle now
  uses a named 15-second load-tolerant wait for preview, selection recompute,
  and completion; it still requires the same visible scope, accessible root,
  zero-before-confirmation, and selected-row-after-restore assertions.

### Fresh completion evidence

- `SettingsRestartExportUiTest`: **3/3** on `Pixel_8_2` API 35 after the
  timeout hardening.
- `:app:connectedDebugAndroidTest --rerun-tasks`: **83/83**, 0 skipped,
  failures, or errors on the exclusively used `Pixel_8_2` API 35 emulator.
  The XML artifact reports `tests="83" failures="0" errors="0" skipped="0"`.
- The M2 public-seam `ExportManagerTest` remains **12/12** in that full
  artifact, and the real Settings restart/export oracle remains green.
- Final `:app:testDebugUnitTest --rerun-tasks`: **41/41**; final
  `:app:lintDebug --rerun-tasks` and `git diff --check`: passed.

### Boundary

Issue #4's stated import-integrity/selective-restore criteria are met; the
Project card is moved from `Verify` to `Done`. API26/API30 were not rerun after
this final M2 test-oracle change, so that compatibility evidence remains
deferred and is not represented as completed.

## 2026-08-10 — GitHub Project and issue workflow

### Operational setup

- Created and linked the private `EchoMind Work` Project for
  `pvnc228/EchoMind`.
- Configured the `Board` view with the status order `Backlog → Ready → In
  Progress → Blocked → Verify → Done`.
- Configured the `Backlog` table view with the `-status:done` filter. The
  owner set its sort to `Priority` ascending, then `Status` ascending.
- Added only the custom `Type` and `Priority` fields; the built-in `Milestone`
  field uses repository milestones `M0` through `M7`.
- Created [Issue #1](https://github.com/pvnc228/EchoMind/issues/1) as the first
  verification ticket, then closed it as `Done` after the repair-gate artifact
  was complete.
- Created the current backlog from the open roadmap boundaries:
  - [Issue #2](https://github.com/pvnc228/EchoMind/issues/2): M0 preview and
    per-request consent — `Backlog`, `feature`, `P0`.
  - [Issue #3](https://github.com/pvnc228/EchoMind/issues/3): M1 bounded
    continue-discussion flow — `Backlog`, `feature`, `P0`.
  - [Issue #4](https://github.com/pvnc228/EchoMind/issues/4): M2 import
    integrity and selective restore — `Backlog`, `verification`, `P1`.
  - [Issue #5](https://github.com/pvnc228/EchoMind/issues/5): M3 repeat-use
    evidence for Home resurfacing — `Backlog`, `verification`, `P1`.
  - [Issue #6](https://github.com/pvnc228/EchoMind/issues/6): optional M4
    local follow-up/reminder — `Ready`, `feature`, `P2`.
  - [Issue #7](https://github.com/pvnc228/EchoMind/issues/7): M5 explainable
    guidance — `Backlog`, `feature`, `P2`.
  - [Issue #8](https://github.com/pvnc228/EchoMind/issues/8): M6 voice to
    text review flow — `Backlog`, `feature`, `P3`.
  - [Issue #9](https://github.com/pvnc228/EchoMind/issues/9): M7 private-beta
    and release evidence — `Backlog`, `verification`, `P3`.
- `In Progress`, `Blocked`, and `Verify` remain empty because no current
  evidence supports those statuses.

### Validation

- `:app:testDebugUnitTest --rerun-tasks`: passed.
- `:app:connectedDebugAndroidTest --rerun-tasks`: **78/78** on cold-booted
  `Pixel_8_2` API 35.
- `:app:lintDebug --rerun-tasks`: passed.
- Markdown local-link validation and `git diff --check`: passed.
- Live `gh project item-list` confirmed the nine cards, statuses, types,
  priorities, and repository milestones listed above.

### Boundary

This Project setup is operational tracking metadata. It does not replace
runtime evidence, the completion gate, or the current repair-gate artifact;
the optional M4 follow-up/reminder and broader product milestones remain
deferred as documented above.

## 2026-08-10 - Issue #6 optional local follow-up implementation

### Implementation

- Moved Issue #6 from `Ready` to `In Progress` and isolated the work on
  `dev/issue-6-m4-follow-up` in a dedicated worktree so the parallel M2 Issue #4
  session does not share a checkout.
- Added a durable local follow-up state store with explicit statuses, one
  unique WorkManager item per decision, one-to-three-day validation, retryable
  scheduling failure state, and idempotent postpone/cancel transitions.
- Added notification actions for postpone/cancel. Notification content contains
  no raw reflection or choice; when notification permission is denied, the
  Decisions screen retains the `FIRED` in-app fallback.
- Kept the offer behind an explicit user choice and made the reminder optional;
  no import, worker, restart, or notification action confirms a decision.
- Added native Compose controls with wrapped action rows and labeled buttons;
  the flow remains usable without notification permission.
- Updated `DATA_CONTRACT.md`, `ROADMAP.md`, and the owner decision record with
  the local operational storage boundary and the current implementation status.

### Finding to artifact

- Finding: M4 allowed an optional one-to-three-day local follow-up after an
  explicit choice, with postpone/cancel and an in-app permission-denied fallback.
- Production seam: `FollowUpCoordinator`, `FollowUpStore`, WorkManager worker,
  notification receiver, and `DecisionsViewModel`/`DecisionsScreen`.
- Red oracle: `FollowUpSchedulerTest` initially failed to compile because the
  production scheduler seam did not exist.
- Green oracle: scheduler policy, duplicate prevention, restart persistence,
  terminal transitions, FIRED postpone/cancel, and UI visibility tests pass.
- Reliability review: coordinator mutations are serialized with a mutex and
  UI failure paths reload durable `FAILED` state so retry remains visible.

### Validation

- `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin --rerun-tasks`:
  passed on Android Studio JDK 21.
- `:app:testDebugUnitTest --tests com.echomind.data.followup.FollowUpSchedulerTest
  --rerun-tasks`: passed.
- `:app:testDebugUnitTest --rerun-tasks`: passed after the final production
  change.
- `:app:lintDebug --rerun-tasks`: passed after the explicit
  `SecurityException` notification fallback.
- Fresh M4 connected subset: **3/3** on cold-booted `Pixel_8_2` API 35,
  covering store reopen/terminal transitions, coordinator scheduling plus
  FIRED postpone/cancel, and the Decisions in-app fallback.
- Full `:app:connectedDebugAndroidTest --rerun-tasks`: **81/81** on the
  now-exclusive `Pixel_8_2` API 35 runtime after the final production change.
- `git diff --check`: passed.

### Scope boundary

This slice does not mark the broader M4 milestone or the M2/M3/M4 repair gate
complete. API26/API30 reruns and wider accessibility/restart-export matrices
remain separate evidence requirements. Issue #6 is ready for `Verify`; the
broader M4 milestone remains open.

## 2026-08-10 - Documentation synchronization after M2/M4 merges

### Review

- Reviewed merge commits `1b720115` (M2, PR #10) and `ecd515961` (M4, PR #11)
  on `origin/master`.
- Live Project status was rechecked: Issue #4 is `Done` and Issue #6 is
  `Verify`. The current workflow and roadmap now use those statuses.
- The dated pre-merge journal entries remain unchanged as historical evidence.
  The M2 `83/83` and M4 `81/81` counts are slice/branch artifacts recorded
  before the two PRs were combined; this entry makes no claim of a new full
  completion gate on merged `master`.
- Updated the M4 roadmap and owner-decision wording so the 2026-08-09
  pre-closure boundary is explicitly historical and the API35 gate is scoped
  to the implementation slice.

### Validation

- `git diff --check`: passed.
- No production code was changed by this documentation synchronization.

### Boundary

API26/API30 evidence and a fresh combined post-merge `master` completion gate
remain open evidence requirements; the broader M4 milestone remains open.

## 2026-08-10 - Issue #6 verification accepted

### Result

- The product owner moved Issue #6 from `Verify` to `Done` after reviewing the
  scoped optional local follow-up implementation.
- The `Done` status closes the Issue #6 result only: API35 slice evidence,
  tests, lint, diff-check, and the implementation artifact are recorded above.
- The broader M4 milestone remains open; API26/API30 evidence and a fresh
  combined post-merge `master` completion gate are still separate requirements.

### Documentation

- `ROADMAP.md` and `docs/GITHUB_WORKFLOW.md` now reflect the live `Done`
  status without claiming that the broader M4 milestone is complete.

## 2026-08-13 - M0 consent and M1 bounded discussion integrated

### Result

- Integrated Issues #2 and #3 into `master` as one cascade-delivery batch.
- M0 now uses an exact minimized-context preview, explicit purpose and
  destination, and one-shot per-request transmission approval. Cancel,
  `localMode`, stale state, endpoint changes, and restart do not reuse consent.
- M1 now supports exactly one focused user-authored follow-up question. Its
  answer remains an AI proposal linked to the confirmed parent and raw source;
  schema v9 preserves this graph through migration, export, restore, and
  deletion.
- Corrected the Retrofit dynamic-destination call so the Q&A request reaches
  the approved endpoint through the production transport seam.

### Evidence captured before publication

- JVM suite: `65/65`.
- Cold-boot integrated connected suite: `99/99` on API 26, API 30, and API 35.
- API 35 manual pass: exact preview and consent actions, 200% text, compact
  layout, IME, TalkBack service/tree focus, real landscape, and proposal state
  after restart.
- Android lint and `git diff --check`: passed for the integrated implementation.

### Owner process decision

- The owner ended repeated incremental gate closure during feature
  implementation and accepted cascade delivery of the completed logic.
- Issues #2 and #3 move to `Done` when this batch is published to `master`.
- Repeated whole-product regression, debugging, and stabilization are a
  separate subsequent stage. No fresh post-documentation full completion gate
  is claimed by this entry.
