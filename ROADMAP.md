# EchoMind — Development Roadmap

**Project**: Private voice diary with AI assistant for Android
**Stack**: Kotlin, Jetpack Compose, MVVM + Clean Architecture, Room, Hilt, Retrofit

---

## Phase 1: Foundation ✅
- [x] Project scaffold (Gradle, modules, build files)
- [x] Dependency injection (Dagger Hilt)
- [x] Navigation (Navigation Compose)
- [x] Theme setup (Material 3, dynamic colors)

## Phase 2: Local Data Storage ✅
- [x] Room database (schema, DAO, entities)
- [x] Entry CRUD operations (insert, read, update, delete)
- [x] Repository pattern implementation
- [ ] Data migrations (v1→v2)
- [ ] SQLCipher encryption (see Phase 8)

## Phase 3: Audio Recording & Playback ✅
- [x] MediaRecorder integration
- [x] Audio file management (private storage)
- [x] Playback with ExoPlayer
- [x] Recording UI with waveform visualization

## Phase 4: LLM Integration (Local/Remote) ✅
- [x] Retrofit client for OpenAI-compatible API
- [x] Whisper transcription via LM Studio / API
- [x] Text analysis (tasks, ideas, emotions extraction)
- [x] Loading states and error handling

## Phase 5: AI-Powered Structuring ✅
- [x] Prompt engineering for diary analysis
- [x] Structured JSON parsing from LLM responses
- [x] Automatic categorization (tasks, ideas, feelings, plans)
- [x] Smart tagging

## Phase 6: Home Screen & Search ✅
- [x] Entry list (LazyColumn with category filters)
- [x] Full-text search with debounce
- [x] Detail screen per entry
- [x] Filter chips by category

## Phase 7: AI Q&A on Past Entries ✅
- [x] Chat-style interface (QaScreen with message bubbles + input)
- [x] Context window construction (last 20 entries as LLM context)
- [x] Natural language queries ("What worried me last week?")
- [x] Source entry linking (entry IDs cited in responses)

## Phase 8: Security & Privacy ✅
- [x] Biometric authentication on launch (BiometricPrompt gate composable)
- [x] SQLCipher database encryption (SupportFactory + MasterKey-derived passphrase)
- [x] Audio file encryption (AES-256-GCM via EncryptedFile)
- [x] FLAG_SECURE (prevent screenshots in app overview)
- [x] Local-only mode toggle (Settings toggle + network config)

## Phase 9: UX Polish ✅
- [x] Skeleton loaders & transitions (shimmer effect on HomeScreen + DetailScreen)
- [x] Dark/light theme (system theme detection, Material 3 dynamic colors)
- [x] Material You (dynamic color on Android 12+, custom palette fallback)
- [x] Onboarding flow (3-page welcome with skip, DataStore persistence)
- [x] Animations (recording pulse dot via InfiniteTransition, AnimatedContent transitions)

## Phase 10: Testing & Release ✅
- [x] Unit tests (GetEntriesUseCaseTest, HomeViewModelTest)
- [x] UI tests (HomeScreenTest - compose)
- [x] ProGuard optimization (rules for Room, Retrofit, ExoPlayer, SQLCipher, serialization, Hilt)
- [x] Google Play assets (adaptive icon, store listing text drafted)
- [ ] Performance profiling (manual - run profiler on device)

---

## Skill References (Phase by Phase)

### Security & Encryption (Phase 8)
- `Anthropic-Cybersecurity-Skills/skills/implementing-aes-encryption-for-data-at-rest` — AES-256-GCM for diary entries
- `Anthropic-Cybersecurity-Skills/skills/configuring-tls-1-3-for-secure-communications` — TLS 1.3 for server channel
- `Anthropic-Cybersecurity-Skills/skills/implementing-api-key-security-controls` — API key management
- `Anthropic-Cybersecurity-Skills/skills/implementing-api-rate-limiting-and-throttling` — Rate limiting
- `Anthropic-Cybersecurity-Skills/skills/configuring-oauth2-authorization-flow` — OAuth 2.0 + PKCE
- `Anthropic-Cybersecurity-Skills/skills/defending-llms-with-guardrails` — Llama Guard
- `Anthropic-Cybersecurity-Skills/skills/detecting-ai-model-prompt-injection-attacks` — Prompt injection defense
- `Anthropic-Cybersecurity-Skills/skills/exploiting-insecure-data-storage-in-mobile` — Anti-patterns audit
- `Anthropic-Cybersecurity-Skills/skills/conducting-mobile-app-penetration-test` — Full pentest

### AI / Transcript Analysis (Phase 4-5, 7)
- `awesome-claude-skills/meeting-insights-analyzer` — Emotion/pattern detection from transcripts
- `awesome-claude-skills/content-research-writer` — Structuring unstructured text

### Design (Phase 9)
- `awesome-claude-skills/theme-factory` — Color palettes & font pairings
- `awesome-claude-skills/canvas-design` — Onboarding/mood illustrations
- `awesome-claude-skills/brand-guidelines` — Brand identity system

### Server Backend (if needed)
- `awesome-claude-skills/mcp-builder` — Self-hosted MCP server
- `awesome-claude-skills/artifacts-builder` — Dashboard UI (React/TS)
- `awesome-claude-skills/webapp-testing` — Playwright tests

### File & Data Organization
- `awesome-claude-skills/file-organizer` — Diary entry organization patterns

### Meta (Skill Creation)
- `awesome-claude-skills/skill-creator` — Package custom project skills
