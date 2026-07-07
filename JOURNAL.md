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

### Next Steps
1. Implement Room database encryption with SQLCipher
2. Add onboarding flow
3. AI Q&A chat interface on past entries
4. Biometric authentication on launch
