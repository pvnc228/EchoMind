# EchoMind — Private Voice Diary with AI Assistant

A privacy-first Android voice diary app that records your thoughts, transcribes them using a local/remote LLM, and automatically structures them into tasks, ideas, emotions, and plans.

## Tech Stack

- **Language**: Kotlin 2.0+
- **UI**: Jetpack Compose + Material 3 (dynamic colors)
- **Architecture**: MVVM + Clean Architecture
- **DI**: Dagger Hilt
- **Database**: Room (SQLCipher encryption planned)
- **Network**: Retrofit + OkHttp (OpenAI-compatible API)
- **Audio**: MediaRecorder / ExoPlayer
- **Security**: Android Keystore, BiometricPrompt, DataStore

## Project Structure

```
app/src/main/java/com/echomind/
├── data/
│   ├── local/          # Room DB, DAO, Entities, Converters
│   ├── remote/         # LLM API client (Retrofit)
│   └── repository/     # Repository implementations
├── domain/
│   ├── model/          # Business models
│   └── usecase/        # Use cases
├── di/                 # Hilt DI modules
└── ui/
    ├── home/           # Entry list
    ├── record/         # Voice recording
    ├── search/         # Full-text search
    ├── settings/       # API config, privacy toggles
    ├── theme/          # Material 3 theme
    └── navigation/     # Nav graph
```

## Phases

| Phase | Status |
|-------|--------|
| 1 — Project Foundation | ✅ Complete |
| 2 — Local Data Storage | ⏳ Next |
| 3 — Audio Recording | ⏳ |
| 4 — LLM Integration | ⏳ |
| 5 — AI Structuring | ⏳ |
| 6 — Home & Search | ⏳ |
| 7 — AI Q&A | ⏳ |
| 8 — Security & Privacy | ⏳ |
| 9 — UX Polish | ⏳ |
| 10 — Testing & Release | ⏳ |

See [ROADMAP.md](ROADMAP.md) for full details and skill references.
See [JOURNAL.md](JOURNAL.md) for development journal and rules.

## Getting Started

1. Open in Android Studio (Ladybug+ recommended)
2. Sync Gradle (AGP 8.7, Kotlin 2.0.20)
3. Run on device/emulator (API 26+)
4. For LLM features: run LM Studio locally or configure a custom API endpoint in Settings

## License

MIT
