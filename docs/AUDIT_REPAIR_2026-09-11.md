# Remaining audit repairs — 2026-09-11

Scope: [issue #15](https://github.com/pvnc228/EchoMind/issues/15), following the
review of Antigravity's uncommitted audit changes on top of `16b5068`.
This follow-up does not declare the entire A01–A13 audit or any product milestone closed.

## Resource ownership

Application startup and audio producers use one synchronized, once-only cleanup
barrier in the singleton `AudioEncryptionUtil`. Capture initialization awaits it
on IO before exposing CAPTURE; decryption crosses it before creating plaintext.
A delayed startup callback cannot sweep a new recording or preview. Interrupted
recording notification survives background cleanup and is consumed by capture.
Completed encrypted drafts are excluded from this sweep.

Preview decryption registers its File reference inside the IO block. Cancellation
at the IO-to-Main return therefore leaves the owning finally block able to delete
the file. Prepared-file cleanup runs on IO and survives ViewModel cancellation.
Approval enters its owning try/finally undispatched: cancellation before network
dispatch cannot strand a file removed from the pending-preview map. Cancellation
invalidates the operation generation; stale completions cannot publish results.
Errors loading engine settings release the busy state.
Retry after startup failure also crosses the cleanup barrier and reloads the
durable draft before enabling capture.

## Retrieval

Q&A and Guidance use the current-conclusion SQL projection. Query errors are
reported, not converted into a legacy raw/history scan. Mock fixtures target the
current public query instead of requiring fallback behavior in production.
Guidance uses bounded evidence, decision and outcome queries. Intrinsic source
exclusion happens in SQL before the external-evidence LIMIT.

LIMIT bounds returned rows, not SQLite LIKE scan CPU. Related-record expansion
still performs a bounded number of lookups; this change does not claim that all
N+1 queries were eliminated or that large-history performance was benchmarked.

## Evidence and reproduction

Permanent oracles:

- `AudioStartupCleanupTest`: old orphans are removed, delayed/repeated cleanup
  preserves new recording/preview files and completed encrypted drafts.
- `RecordViewModelTest`: cancellation at the decrypt return; cancellation between
  approval and send dispatch; settings failure; original concurrent-request,
  replacement-preview and encryption-failure checks.
- `LlmRepositoryTest` / `GuidanceRepositoryTest`: database failures never invoke
  unbounded search/evidence fallback.
- `AuditRepairIntegrationTest` on Android: real encrypted draft, Room,
  RecordViewModel, AudioRepository and LlmRepository through preview/approval,
  exact transmitted-byte comparison, temporary-file deletion, substitution
  rejection; sixth grounded candidate through Room and bounded external links;
  real Room context minimization and identifier redaction.

Red logs are retained locally under `.audit/2026-09-10/`:
`repair-startup-red.log`, `repair-cancel-red.log`, `repair-retrieval-red.log`,
`repair-integration-red.log`, `repair-settings-red.log`,
`repair-send-cancel-red.log`. The Room oracle failed with 2 external links where
3 were required before the SQL correction. The initial two Android integration
tests then passed on Pixel_8_2 API 35.

The final gate is run after source, test and documentation changes:

```text
:app:testDebugUnitTest --rerun-tasks
:app:connectedDebugAndroidTest --rerun-tasks
:app:lintDebug --rerun-tasks
git diff --check
```

Final runner output: `.audit/2026-09-10/repair-final-gate.log`.
Machine-readable counts, exit codes and source fingerprint:
`.audit/2026-09-10/repair-final-gate.json` (generated from the completed run).
This is the repository's normal gate; the historical `.audit` source-set harness
is not injected. Its old mock of `search()` no longer matches the typed current
query; its identifier-redaction behavior is covered through real Room here.

Boundary: network transport is a test double using synthetic bytes; actual
Gemini/OpenAI service compatibility and recognition quality are not verified.
On-device file transcription remains fail-closed. New manual accessibility,
API26/API30, process-kill and large-history performance runs are not claimed.
