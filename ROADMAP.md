# EchoMind — Development Roadmap

**Project**: Private voice diary with AI assistant for Android
**Stack**: Kotlin, Jetpack Compose, MVVM + Clean Architecture, Room, Hilt, Retrofit

---

## Phase 1: Foundation ✅
- [x] Project scaffold (Gradle, modules, build files)
- [x] Dependency injection (Dagger Hilt)
- [x] Navigation (Navigation Compose)
- [x] Theme setup (Material 3, dynamic colors)

## Phase 2: Local Data Storage
- [ ] Room database with encrypted entries
- [ ] Entry CRUD operations
- [ ] Repository pattern implementation
- [ ] Data migrations

## Phase 3: Audio Recording & Playback
- [ ] MediaRecorder integration
- [ ] Audio file management (private storage)
- [ ] Playback with ExoPlayer
- [ ] Recording UI with waveform visualization

## Phase 4: LLM Integration (Local/Remote)
- [ ] Retrofit client for OpenAI-compatible API
- [ ] Whisper transcription via LM Studio / API
- [ ] Text analysis (tasks, ideas, emotions extraction)
- [ ] Loading states and error handling

## Phase 5: AI-Powered Structuring
- [ ] Prompt engineering for diary analysis
- [ ] Structured JSON parsing from LLM responses
- [ ] Automatic categorization (tasks, ideas, feelings, plans)
- [ ] Smart tagging

## Phase 6: Home Screen & Search
- [ ] Entry list (LazyColumn with category filters)
- [ ] Full-text search with debounce
- [ ] Detail screen per entry
- [ ] Filter chips by category/emotion

## Phase 7: AI Q&A on Past Entries
- [ ] Chat-style interface
- [ ] Context window construction (recent N entries)
- [ ] Natural language queries ("What worried me last week?")
- [ ] Source entry linking in responses

## Phase 8: Security & Privacy
- [ ] Biometric authentication on launch
- [ ] SQLCipher database encryption
- [ ] Audio file encryption (AES-256-GCM)
- [ ] FLAG_SECURE (prevent screenshots)
- [ ] Local-only mode toggle

## Phase 9: UX Polish
- [ ] Skeleton loaders & transitions
- [ ] Dark/light theme
- [ ] Material You (dynamic color)
- [ ] Onboarding flow
- [ ] Animations (entry/exit, recording pulse)

## Phase 10: Testing & Release
- [ ] Unit tests (ViewModel, UseCases, Repository)
- [ ] UI tests (Compose Test)
- [ ] Performance profiling
- [ ] ProGuard optimization
- [ ] Google Play assets (icon, screenshots, description)

---

## Security Skills Integration (from skills-reference)
- [ ] AES-256-GCM encryption for diary entries
- [ ] TLS 1.3 for server channel (OkHttp cert pinning)
- [ ] API key security (generation, rotation)
- [ ] Rate limiting on server
- [ ] OAuth 2.0 + PKCE for server auth
- [ ] Prompt injection detection for voice input
- [ ] Llama Guard for on-device safety

## Design References
- `skills/meeting-insights-analyzer` → emotion detection adaptation
- `skills/content-research-writer` → diary structuring
- `skills/theme-factory` → color palettes
- `skills/artifacts-builder` → server dashboard UI
