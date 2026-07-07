# EchoMind — Development Journal

## Development Rules

### Rule 1: Manual Git Push
The user manually pushes all changes to GitHub. I never execute `git push` or any remote git operations. I can initialize local repos, stage, and commit — but pushing is always manual.

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

### Next Steps
1. Biometric authentication on launch (Phase 8)
2. SQLCipher database encryption (Phase 8)
3. Audio file encryption (AES-256-GCM) (Phase 8)
4. FLAG_SECURE (prevent screenshots) (Phase 8)
5. Onboarding flow (Phase 9)
6. Data migrations for Room (v1→v2)
