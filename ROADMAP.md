# EchoMind — Product Roadmap

**Last updated:** 2026-07-29

**Product direction:** [VISION.md](VISION.md)

**Data/privacy contract:** [DATA_CONTRACT.md](DATA_CONTRACT.md)

This roadmap replaces the former feature checklist for a "voice diary with AI." Existing code is treated as a technical prototype. A capability is complete only when it supports the product loop in `VISION.md` and satisfies its evidence, privacy, and verification criteria.

## Current baseline

### Reusable foundation

- Android application built with Kotlin, Jetpack Compose, Hilt, Room, and Retrofit.
- Local entry CRUD, timeline, category filtering, search, detail view, and export.
- Audio recording, encrypted audio storage, and playback.
- OpenAI-compatible endpoints for text analysis, Q&A, and transcription.
- SQLCipher database encryption, Android Keystore integration, biometric release gate, and `FLAG_SECURE`.
- Local fallback text analyzer.
- Unit and Compose UI test foundation.
- Verified debug build and emulator startup on Pixel 8 API 35.

### Product gaps

- The data model is a flat diary `Entry`; it cannot represent a conclusion, revision, source, contradiction, decision, or outcome.
- AI analysis writes generated fields directly into an entry without a user confirmation boundary.
- The current recording flow asks the user to type a transcript; the implemented transcription client is not connected to that flow.
- Q&A sends a context window of recent entries but does not operate on a traceable, user-confirmed model.
- The `localMode` setting is persisted but does not enforce a network boundary in the repository layer.
- Remote processing does not yet provide local redaction, a minimized-context preview, or separate transmission consent.
- The home screen is a diary timeline, not a prompt plus relevant evidence-backed resurfacing.
- Current categories (`task`, `idea`, `feeling`, `plan`) do not express evolving conclusions.

Until these gaps are closed, the repository is a working technical prototype, not a completed implementation of the new vision.

## Delivery rules

Every milestone must:

- produce a usable end-to-end user outcome, not only infrastructure;
- preserve provenance from raw record to confirmed conclusion;
- prevent unconfirmed AI output from entering the user model;
- include migration, export, and deletion behavior for new data;
- include automated tests for core state transitions and privacy boundaries;
- be validated on an emulator or device before being marked complete;
- update `JOURNAL.md` with decisions, evidence, and remaining limitations.

No milestone is complete because a screen exists or an LLM returned plausible text.

## M0 — Data and privacy contract

**Priority:** P0

**Outcome:** the architecture can enforce the promises in `VISION.md` before more AI behavior is added.

### Scope

- [x] Classify raw, derived, confirmed, identifying, and exportable data.
- [x] Define the local entities and state transitions for:
  - raw records;
  - AI hypotheses;
  - confirmed conclusions;
  - revisions;
  - evidence and counterevidence links;
  - themes;
  - decisions and outcomes.
- [x] Define deletion and export semantics for the entire relationship graph.
- [x] Replace destructive pre-release migration behavior with an explicit migration strategy before valuable user data exists.
- [x] Turn local mode into an enforced network policy rather than a UI preference.
- [x] Define the remote-request pipeline: local minimization, redaction, preview, explicit consent, request, and disposal.
- [x] Document third-party endpoint limitations, including provider-side retention outside EchoMind's control.
- [ ] Add tests proving that raw records cannot cross the remote boundary.

### Completion criteria

- Every persisted field has a data classification and owner.
- A state diagram covers proposal, edit, confirmation, rejection, revision, and deletion.
- Network tests fail if a raw record or complete personal model is included in a remote request.
- Local mode blocks all AI network calls at the repository boundary.
- Existing prototype data has a documented migration or explicit pre-release reset path.

## M1 — Day-one reflection loop

**Priority:** P0

**Outcome:** one short text entry can produce a useful, user-confirmed clarification in a single session.

### Scope

- [ ] Make text-first thought capture the primary flow.
- [ ] Preserve the original text as an immutable raw record.
- [ ] Generate a structured draft containing:
  - tentative thesis;
  - observations;
  - interpretations;
  - assumptions;
  - open questions.
- [ ] Offer one relevant counterargument or alternative interpretation without manufacturing disagreement.
- [ ] Let the user edit, accept, reject, or continue discussing the draft.
- [ ] Save only the accepted formulation as a confirmed conclusion.
- [ ] Display the raw source, AI proposal, user edits, and final conclusion as distinct objects.
- [ ] Provide graceful local-only behavior when remote assistance is unavailable.

### Completion criteria

- The "Walter White / architect" scenario can be completed end to end.
- The user can identify which words are theirs and which were proposed by AI.
- Rejecting an AI hypothesis leaves no confirmed claim behind.
- A confirmed conclusion retains its source and revision history after restart.
- A first-session usability check demonstrates a concrete clarification rather than a paraphrased note.

## M2 — Traceable personal knowledge model

**Priority:** P1

**Outcome:** conclusions become a connected, inspectable history rather than isolated summaries.

### Scope

- [ ] Link conclusions to supporting and contradicting records.
- [ ] Create and edit themes without treating AI clustering as truth.
- [ ] Preserve dated revisions and show what changed.
- [ ] Add archive and search across raw records, conclusions, and themes.
- [ ] Detect candidate relationships locally or with minimized remote context.
- [ ] Require user confirmation for durable semantic links inferred by AI.
- [ ] Extend export, deletion, and backup to all new objects.

### Completion criteria

- A conclusion can be traced to every source and revision.
- A user can correct or delete a relationship without damaging unrelated records.
- Contradictory conclusions can coexist without one being silently overwritten.
- Search can recover both the original record and the current conclusion.
- Export and restore preserve graph identity and provenance.

## M3 — Relevant resurfacing and visible capability

**Priority:** P1

**Outcome:** the home screen returns one meaningful line of thought and honestly shows what EchoMind currently knows.

### Scope

- [ ] Replace the timeline-first home screen with:
  - a prompt to capture or continue a thought;
  - one evidence-backed relevant card;
  - fast access to capture and archive.
- [ ] Explain why a theme, contradiction, or unfinished question is shown now.
- [ ] Let the user inspect sources, continue, dismiss, or postpone the card.
- [ ] Show evidence coverage separately per theme.
- [ ] Distinguish reflection, connection, change tracking, and guidance capabilities.
- [ ] Avoid global personality percentages, streak pressure, and arbitrary levels.

### Completion criteria

- A resurfaced card cites the relevant records and conclusion versions.
- The card remains useful without a 3D graph or heatmap.
- Sparse domains clearly report insufficient evidence.
- Records from one domain do not create apparent confidence in another.
- The user can understand the suggested next action within 30 seconds.

## M4 — Decision and outcome loop

**Priority:** P2

**Outcome:** EchoMind learns from reported consequences, not only from the user's self-description.

### Scope

- [ ] Let the user turn a question into an explicit decision record.
- [ ] Store the user's choice separately from EchoMind's suggestion.
- [ ] Offer an optional, user-controlled follow-up.
- [ ] Capture the reported outcome with minimal friction.
- [ ] Compare an outcome with the original expectation and revise relevant conclusions only after review.
- [ ] Show when a theme lacks outcome evidence.

### Completion criteria

- The complete chain `question → grounds → suggestion → choice → outcome → revision` is inspectable.
- Ignoring follow-up never blocks basic reflection or retrieval.
- Reminders are opt-in, dismissible, and do not use guilt or streak mechanics.
- Advice quality is never inferred from entry count alone.

## M5 — Explainable personal guidance

**Priority:** P2

**Outcome:** on explicit request, EchoMind can offer cautious guidance supported by the user's relevant history.

### Scope

- [ ] Retrieve relevant confirmed conclusions, counterevidence, and comparable outcomes.
- [ ] Construct the smallest sufficient context for a local or remote model.
- [ ] Show the exact context before any remote transmission.
- [ ] Produce guidance with citations, uncertainty, and alternative interpretations.
- [ ] Refuse or ask focused questions when the evidence is insufficient.
- [ ] Keep advice domain-specific and prevent unsupported personality or clinical claims.
- [ ] Let the user rate usefulness and report an eventual outcome without turning feedback into an obligation.

### Completion criteria

- Every substantive guidance claim is linked to visible grounds.
- Removing a cited ground changes or lowers the stated confidence.
- The system does not produce guidance until the user asks.
- Safety tests reject diagnosis, hidden-motive claims, and unsupported certainty.
- A curated evaluation set includes helpful answers, appropriate refusals, contradictions, and privacy-sensitive prompts.

## M6 — Input and visualization expansion

**Priority:** P3

**Outcome:** additional modalities make the proven loop easier to use without redefining the product.

### Scope

- [ ] Connect optional voice capture to transcription and the same review flow as text.
- [ ] Keep the transcript editable and retain the raw/derived distinction.
- [ ] Evaluate on-device transcription before allowing raw audio to leave the device.
- [ ] Add two-dimensional theme and revision views only for validated user questions.
- [ ] Consider heatmaps or 3D visualization only after proving that they improve navigation or understanding.
- [ ] Evaluate selective import from notes or chat exports with explicit review before model ingestion.

### Completion criteria

- Text and voice produce the same inspectable conclusion workflow.
- No modality bypasses confirmation or privacy boundaries.
- A visualization must answer a named user question better than a list or timeline in usability testing.
- Imported material remains unconfirmed until reviewed by the user.

## M7 — Private beta and release evidence

**Priority:** after M1–M3 are stable

**Outcome:** the product promise is tested with reflective users rather than inferred from implementation completeness.

### Scope

- [ ] Run first-session tests focused on reaching a genuine clarification.
- [ ] Test whether users voluntarily return with a second thought.
- [ ] Test whether resurfacing recovers useful forgotten reasoning.
- [ ] Evaluate remote-context previews for comprehension and informed consent.
- [ ] Profile startup, database operations, encryption, and long-history retrieval.
- [ ] Complete accessibility, backup/restore, failure recovery, and release security review.
- [ ] Gather research feedback without product telemetry; participation and any shared material must be explicit.

### Completion criteria

- Users can explain the difference between a raw record, AI hypothesis, and confirmed conclusion.
- At least one repeated-use study demonstrates immediate and compounding value.
- Privacy and deletion behavior pass a documented verification checklist.
- Release claims match the behavior of the shipped build.

## Next implementation slice

The first implementation block should be M0 plus the thinnest vertical part of M1:

1. approve the domain model and privacy state transitions;
2. implement raw text capture;
3. create an AI draft without persisting it as truth;
4. add review and explicit confirmation;
5. persist a versioned conclusion with its source;
6. verify restart, rejection, deletion, export, and network-boundary behavior.

Do not begin personalization, prediction, 3D visualization, or broad UI polish before this slice demonstrates day-one value.

## Execution stages

### M0-A — Contract and network boundary

- [x] Classify current and planned data in `DATA_CONTRACT.md`.
- [x] Define confirmation, rejection, revision, deletion, export, and remote-request transitions.
- [x] Share the persisted local-mode setting with the repository layer.
- [x] Block analysis, Q&A, and transcription network calls while local mode is enabled.
- [x] Stop debug logging of personal request and response bodies.
- [x] Add repository tests proving that local mode performs no AI API call.

### M0-B — Storage and migration

- [x] Add the minimum raw record, hypothesis, conclusion, revision, and evidence-link tables.
- [x] Migrate version 2 entries without treating legacy generated fields as confirmed.
- [x] Remove destructive migration fallback.
- [x] Extend deletion and export to the new relationship graph.

### M1-A — Text-first reflection

- [ ] Capture and persist immutable raw text before analysis.
- [ ] Produce one local structured draft and counterargument.
- [ ] Let the user edit, reject, or explicitly confirm the proposal.
- [ ] Persist the confirmed wording as revision 1 linked to its source.

### M1-B — Vertical-slice proof

- [ ] Verify restart, rejection, deletion, export, and network-boundary behavior.
- [ ] Validate the full flow on Pixel 8 API 35.
- [ ] Record evidence and remaining limitations in `JOURNAL.md`.
