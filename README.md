# EchoMind

EchoMind is a private Android thinking environment that turns short reflections into traceable, evolving conclusions. It helps a user inspect assumptions, preserve changes of mind, reconnect related ideas, and eventually receive evidence-backed guidance without handing decisions to an AI.

> The repository currently contains a working voice-diary prototype that is being realigned with the product vision. The existing AI and privacy controls do not yet enforce every guarantee described in `VISION.md`.

## Documentation

- [Product vision](VISION.md)
- [Product roadmap](ROADMAP.md)
- [Development journal](JOURNAL.md)

## Current Prototype

- Encrypted local diary entries and audio recordings
- Voice recording with manual transcript entry
- OpenAI-compatible text analysis, transcription client, and Q&A
- Entry timeline, search, filters, details, and export
- Local fallback text analysis
- Biometric release gate and screenshot protection

Automatic transcription is not yet connected to the main recording flow. The saved `localMode` preference is also not yet a hard repository-level network boundary; both are tracked explicitly in the roadmap.

## Tech Stack

- **Language**: Kotlin 2.0+
- **UI**: Jetpack Compose + Material 3 (dynamic colors)
- **Architecture**: MVVM + Clean Architecture
- **DI**: Dagger Hilt
- **Database**: Room + SQLCipher
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

## Getting Started

1. Open in Android Studio (Ladybug+ recommended)
2. Sync Gradle (AGP 8.7, Kotlin 2.0.20)
3. Run on device/emulator (API 26+)
4. For prototype LLM features: run LM Studio locally or configure an OpenAI-compatible endpoint in Settings

## Validation

Use Android Studio's embedded JDK 21 with the Gradle 8.9 wrapper:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

## License

MIT
