# EchoMind

EchoMind is a private Android thinking environment that turns short reflections into traceable, evolving conclusions. It helps a user inspect assumptions, preserve changes of mind, reconnect related ideas, and eventually receive evidence-backed guidance without handing decisions to an AI.

> The repository is an active product prototype. Its primary capture path now
> enforces explicit confirmation and the local provenance graph is inspectable;
> minimized remote Q&A and remote voice transcription are available behind an
> exact preview and one-request transmission consent. On-device transcription
> remains unevaluated.

## Documentation

- [Product vision](VISION.md)
- [Product roadmap](ROADMAP.md)
- [Data and privacy contract](DATA_CONTRACT.md)
- [Development journal](JOURNAL.md)
- [GitHub Project and issue workflow](docs/GITHUB_WORKFLOW.md)

## Current Prototype

- Text-first reflection capture with optional encrypted voice attachment
- Immutable raw text persisted before local analysis
- On-device structured proposal: thesis, observations, interpretations,
  assumptions, open questions, and one alternative interpretation
- Explicit edit, confirm, and reject boundary
- One bounded local follow-up question whose result remains an unconfirmed proposal
- Exact minimized-context preview and one-request consent for remote Q&A
- Optional voice capture with remote transcription behind a preview + one-shot consent boundary, returning an editable transcript
- Confirmed conclusion stored as revision 1 with its raw source
- OpenAI-compatible text analysis, transcription client, and Q&A
- Entry timeline, search, filters, details, and export
- Room v9 provenance storage for raw records, hypotheses, conclusions, revisions,
  immutable/pending evidence and theme links, decisions, drafts, and Home
  dispositions, the derived Unicode-aware search key, and persisted
  audio-cleanup retries
- Biometric release gate and screenshot protection

Remote audio transcription is connected to the recording flow behind a preview
and one-shot consent; on-device transcription remains unevaluated.
Archive/detail, deletion, Decisions, Home coverage, bounded Detail browsing,
and empty-profile restore now expose the implemented provenance boundaries.
The scoped repair gate, selective restore, optional M4 reminder, M0 minimized
context consent, M1 bounded discussion, M5 explainable guidance, and M6 voice
slices are implemented. `localMode` still blocks repository network calls.
Broader product milestones and the separate whole-product
debugging/verification stage remain open.

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
    ├── record/         # Text-first reflection and optional voice capture
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

Manual on-device passes (TalkBack, scaled text, landscape, API 26-30 glass
fallback, recovery states) live in
[docs/DEVICE_VALIDATION.md](docs/DEVICE_VALIDATION.md). Automated suites:

Use Android Studio's embedded JDK 21 with the Gradle 8.9 wrapper:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

## License

MIT
