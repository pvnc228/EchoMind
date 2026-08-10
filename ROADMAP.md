# EchoMind — Product Roadmap

**Last updated:** 2026-08-10

**Product direction:** [VISION.md](VISION.md)

**Data/privacy contract:** [DATA_CONTRACT.md](DATA_CONTRACT.md)

**GitHub workflow:** [docs/GITHUB_WORKFLOW.md](docs/GITHUB_WORKFLOW.md)

This roadmap replaces the former feature checklist for a "voice diary with AI." Existing code is treated as a technical prototype. A capability is complete only when it supports the product loop in `VISION.md` and satisfies its evidence, privacy, and verification criteria.

## Current baseline

### Reusable foundation

- Android application built with Kotlin, Jetpack Compose, Hilt, Room, and Retrofit.
- Local entry CRUD, timeline, category filtering, search, detail view, and export.
- Versioned conclusions, proposals, revisions, and source links with inspectable
  provenance in the detail view.
- Transactional graph deletion and manifest version 2 export for the complete
  M1 relationship chain.
- Audio recording, encrypted audio storage, and playback.
- OpenAI-compatible endpoints for text analysis, Q&A, and transcription.
- SQLCipher database encryption, Android Keystore integration, biometric release gate, and `FLAG_SECURE`.
- Deterministic on-device reflection and fallback analyzers.
- Repository-level deny-by-default boundary for every legacy raw-content AI
  request.
- Unit and Compose UI test foundation.
- Verified debug build and emulator startup on Pixel 8 API 35.

### Product gaps

- The legacy `Entry` archive remains alongside the M1 provenance graph and does
  not yet model themes, contradictions, decisions, or outcomes.
- The current recording flow asks the user to type a transcript; the implemented transcription client is not connected to that flow.
- Q&A and remote transcription are deliberately unavailable because the
  prototype has no minimized-context preview or per-request transmission
  approval.
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
- [x] Add tests proving that raw records cannot cross the remote boundary.

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

- [x] Make text-first thought capture the primary flow.
- [x] Preserve the original text as an immutable raw record.
- [x] Generate a structured draft containing:
  - tentative thesis;
  - observations;
  - interpretations;
  - assumptions;
  - open questions.
- [x] Offer one relevant counterargument or alternative interpretation without manufacturing disagreement.
- [ ] Let the user edit, accept, reject, or continue discussing the draft.
- [x] Save only the accepted formulation as a confirmed conclusion.
- [x] Display the raw source, AI proposal, user edits, and final conclusion as distinct objects.
- [x] Provide graceful local-only behavior when remote assistance is unavailable.

### Completion criteria

- [x] The "Walter White / architect" scenario can be completed end to end.
- [x] The user can identify which words are theirs and which were proposed by AI.
- [x] Rejecting an AI hypothesis leaves no confirmed claim behind.
- [x] A confirmed conclusion retains its source and revision history after restart.
- [x] A first-session usability check demonstrates a concrete clarification
  rather than a paraphrased note.

The 2026-07-30 synthetic first-session baseline failed the remaining product
criterion: five scored scenarios totalled `18/50` (mean `3.6/10`). All eight
source inputs persisted locally, so this is an analyzer-value failure rather
than a storage or confirmation-flow failure. See
[M1_USABILITY_EVALUATION.md](M1_USABILITY_EVALUATION.md).

## M2 — Traceable personal knowledge model

**Priority:** P1

**Outcome:** conclusions become a connected, inspectable history rather than isolated summaries.

### Scope

- [x] Link conclusions to supporting and contradicting records.
- [x] Create and edit themes without treating AI clustering as truth.
- [x] Preserve dated revisions and show what changed.
- [x] Add archive and search across raw records, conclusions, and themes.
- [x] Detect candidate relationships locally or with minimized remote context
  (local heuristic is implemented; remote candidates await the approval pipeline).
- [x] Require user confirmation for durable semantic links inferred by AI.
- [x] Extend export, deletion, and backup to all new objects.

### Completion criteria

- A conclusion can be traced to every source and revision.
- A user can correct or delete a relationship without damaging unrelated records.
- Contradictory conclusions can coexist without one being silently overwritten.
- Search can recover both the original record and the current conclusion.
- Export and restore preserve graph identity and provenance.

**Status (2026-08-07):** the first M2 slice is implemented and verified:
user-owned themes (schema v4), confirmed theme links, and user-managed
supports/contradicts relationships between confirmed conclusions (reusing
`evidence_links`), plus export manifest v3 for all new objects.
16/16 instrumented tests pass on Pixel 8 API 35.

**Historical status (2026-08-08, repair slice):** dated revision history,
graph-wide search, immutable/pending revision links, explicit
dependency-preview deletion, local candidate detection, and manifest v5
empty-profile restore were implemented. Inherited links remain pending until a
user reviews them. Merge/selective restore was deferred at that point.

**Status (2026-08-10, M2 implementation):** `ExportManager` now exposes a
validated, read-only restore preview plus additive `All` and
`SelectedRawRecords` scopes. Stable-ID/natural-key conflicts fail closed before
staging or the Room transaction; selected evidence-source dependencies and
legacy full-restore states are preserved. Settings shows the preview and lets
the user select roots before confirming. Fresh API35 public-seam, Settings UI,
selective restore database-reopen/canonical-export, and full connected oracles
pass. The full `Pixel_8_2` API35 suite is **83/83** after hardening the UI
oracle's load-sensitive wait; the earlier SIGKILL was not reproduced when the
emulator was used exclusively. Issue #4's import/restore criteria are met and
the card is moved to `Done`. API26/API30 were not rerun after this final M2
change, so the cross-API compatibility matrix remains explicitly deferred.

## M3 — Relevant resurfacing and visible capability

**Priority:** P1

**Outcome:** the home screen returns one meaningful line of thought and honestly shows what EchoMind currently knows.

**Status (2026-08-08, repair slice):** the home screen uses typed coverage
states, exact time thresholds, deterministic tie-breaks, and fingerprint-keyed
Room dispositions. It includes empty/unthemed coverage and exposes source and
revision IDs. Legacy theme-wide suppression is reset once. Accessibility matrix
validation and a full restart/manage-dismissed UI oracle remain pending, so M3
is not marked complete.

### Scope

- [x] Replace the timeline-first home screen with:
  - a prompt to capture or continue a thought;
  - one evidence-backed relevant card;
  - fast access to capture and archive.
- [x] Explain why a theme, contradiction, or unfinished question is shown now.
- [x] Let the user inspect sources, continue, dismiss, or postpone the card.
- [x] Show evidence coverage separately per theme.
- [x] Distinguish reflection, connection, change tracking, and guidance capabilities.
- [x] Avoid global personality percentages, streak pressure, and arbitrary levels.

### Completion criteria

- A resurfaced card cites the relevant records and conclusion versions.
- The card remains useful without a 3D graph or heatmap.
- Sparse domains clearly report insufficient evidence.
- Records from one domain do not create apparent confidence in another.
- The user can understand the suggested next action within 30 seconds.

## M4 — Decision and outcome loop

**Priority:** P2

**Outcome:** EchoMind learns from reported consequences, not only from the user's self-description.

**Status (2026-08-08, first repair slice):** decisions and outcomes are
first-class, inspectable objects. A decision must be grounded in a current
revision; user choice and reported outcomes are guarded by the derived
`CREATED → CHOSEN → OUTCOME_REPORTED` state machine. System suggestions require
explicit author/source/status metadata and are not created by the ordinary user
flow. Export carries the metadata. The full restart/export UI oracle remained
deferred until the Review impact slice below.

**Status (2026-08-09, Review impact slice):** the optional local Review impact
flow is implemented and verified. It reads the original current grounds, choice,
and reported outcomes in one transaction, builds an unconfirmed deterministic
proposal, shows the original/proposed wording separately, and appends a new
revision only after explicit confirmation. Stale grounds are rejected without a
write; historical revisions and the decision's original grounds remain intact.
The full Decisions restart/export UI oracle and API26/API30 runtime evidence
remain open, so M4 and the M2/M3/M4 repair gate are not marked complete.

**Owner decision (2026-08-09):** следующий M4 slice — необязательный локальный
`Review impact`: outcome сравнивается с исходными grounds/choice, proposal
показывается как diff, а новая revision появляется только после явного
подтверждения. После choice приложение может один раз предложить follow-up на
1-3 дня; принятый reminder локальный, opt-in, с действиями postpone/cancel в
notification и приложении. Решение зафиксировано в
[`docs/OWNER_DECISIONS_API_COMPAT_M4_2026-08-09.md`](docs/OWNER_DECISIONS_API_COMPAT_M4_2026-08-09.md),
а Review impact реализован в отдельном срезе ниже; follow-up остаётся
не реализованным.

### Scope

- [x] Let the user turn a question into an explicit decision record.
- [x] Store the user's choice separately from EchoMind's suggestion.
- [ ] Offer an optional, user-controlled follow-up.
- [x] Capture the reported outcome with minimal friction.
- [x] Compare an outcome with the original expectation and revise relevant conclusions only after review.
- [x] Show when a theme lacks outcome evidence.

### Completion criteria

- [x] The complete chain `question → grounds → suggestion → choice → outcome → revision` is inspectable through an explicit local Review impact proposal and confirmation; automatic revision remains forbidden.
- [x] Ignoring follow-up never blocks basic reflection or retrieval.
- [x] Reminders are opt-in, dismissible, and do not use guilt or streak mechanics.
- [x] Advice quality is never inferred from entry count alone.

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

M0 through M1-C are implemented and verified. The frozen synthetic re-score
reached `50/50`, so the bounded usefulness gate is closed without claiming
external-user validation or product-market fit.

The approved UX implementation checkpoint (audit, Echo Glass Review rework,
accessibility, and device validation) is complete and closed on 2026-08-07;
see the checkpoint section below.

Remote raw-content paths remain blocked until a minimized preview and
per-request approval flow exists.

Do not begin personalization, prediction, 3D visualization, or broad UI polish
before this product proof is complete.

The scoped repair completion gate for **M2/M3/M4** is closed by the fresh
2026-08-10 artifact below. This closes the repair gate only; the broader
M2/M3/M4 product milestones remain unclosed where their own completion
criteria are still deferred. The implemented slice includes user-owned
themes, immutable/pending links, dated revisions, graph-wide search, typed
Home coverage, guarded decisions, bounded Detail browsing, and empty-profile
restore.

The live implementation backlog is tracked in
[`docs/GITHUB_WORKFLOW.md`](docs/GITHUB_WORKFLOW.md): Issue #6 is the next
`Ready` M4 slice, while Issues #2–#5 and #7–#9 represent the remaining M0,
M1, M2, M3, M5, M6, and M7 work. Issue #1 is the completed repair-gate card.

The non-blocking audio-cleanup follow-up from the 2026-08-08 review is also
implemented and verified (2026-08-09): terminal retry rows no longer starve
later eligible rows, terminal attempts do not grow, and concurrent cleanup
enqueue uses race-safe replacement. This does not independently close the
broader M2/M3/M4 product milestones; their remaining product criteria are
tracked separately from the repair gate.

**Historical snapshot (superseded by the 2026-08-10 closure artifact below):**
The next technical follow-up was advanced but not closed (2026-08-09): restore
now has connected negative coverage for graph/archive integrity failures, Home
relevant-card actions have a fresh compact-width/fontScale-2 Compose oracle,
and Decisions actions use adaptive wrapping. The Decisions gate now has
instrumented compact/landscape/IME/accessibility-semantics coverage, an actual
API35 TalkBack accessibility-tree smoke, database-restart and ZIP
export/restore oracles, plus cold-boot API26 and API30 runtime evidence. The
M4 Review impact flow remains covered by repository negative/positive oracles
and a Compose provenance oracle, but this does not close the general M4 or
broader M2/M3/M4 completion gate.
Owner input for those deferred choices was recorded and confirmed in
[`docs/USER_INPUT_API_COMPAT_AND_M4_2026-08-09.md`](docs/USER_INPUT_API_COMPAT_AND_M4_2026-08-09.md)
and
[`docs/OWNER_DECISIONS_API_COMPAT_M4_2026-08-09.md`](docs/OWNER_DECISIONS_API_COMPAT_M4_2026-08-09.md).
The owner selected API26+API30 AVD validation and authorized the required system
image downloads. The agreed Google APIs x86_64 images are provisioned in
`EchoMind_API26_GoogleApis` and `EchoMind_API30_GoogleApis`; API35 remains the
control runtime. The API26/API30 images do not provide a TalkBack package, so
their accessibility fallback is covered by the Compose semantics oracle while
the actual TalkBack tree smoke runs on API35.

The 2026-08-09 repair-gate artifact and the 2026-08-10 bounded repair follow-ups
cover Search projections, Decisions JOIN mapping, ranking payload projections,
and the real Detail manual-candidate path. Detail now uses `NOT EXISTS`, a
bounded projection page, server-side search, explicit page loading, and separate
graph/manual concurrency domains; the 1k/10k public seams reject N+1 growth,
caller-sized bind lists, and full raw-record payloads. Decisions mapping is read
in one Room transaction, and the migrated v2 → export → empty restore → export
path compares canonical manifests. The active data contract now records Room
schema v8 and classifies the derived Unicode search key as non-exported.

The scoped repair gate is closed by the 2026-08-10 artifact: the Android
public `KnowledgeRepository.getLinkCandidates` seam measures the complete
Room projection, mapping, and ranking operation at 1k/10k within its explicit
2,000 ms reference-runtime UX budget and proves candidate work is off Main;
API26/API30 have fresh full-suite reruns; Detail/Home/Decisions accessibility
oracles cover compact/200%/landscape/IME semantics; the API35 TalkBack tree
smoke passed; and real Decisions/Settings screen+ViewModel restart/export
oracles cover database reopen and empty-profile ZIP restore. This closes the
repair gate only; the optional M4 follow-up/reminder and the broader product
milestone remain deferred. See
the matching entries in `JOURNAL.md` and
`docs/OPENCODE_REPAIR_TRACEABILITY_2026-08-08.md`.

**Historical snapshot (superseded by the 2026-08-10 closure artifact above):**
The 2026-08-10 review follow-up closed the Unicode manual-search regression and
the Search/Load more race. Manual Detail search uses a persisted NFKC plus
`Locale.ROOT` lowercase key with a v7 -> v8 legacy backfill; the raw text and
export contract remain unchanged. `DetailViewModel` publishes query/loading
state before launching the request, captures the query and offset, disables
pagination during an active request, and rejects stale success/error paths.
Fresh evidence was recorded in `JOURNAL.md`: JVM 40/40, connected 68/68 on
Pixel_8_2 API 35, lint, and diff-check. This does not close the broader repair
gate; that sentence is historical and is superseded by the closure artifact
above, which records the Android public-seam CPU benchmark and real
restart/export screen/ViewModel oracles. Optional M4 follow-up/reminder and the
broader product milestone remain deferred.

## UX/UI audit and approved implementation checkpoint

**Status:** audit complete and UX direction approved on 2026-08-01; the P1
implementation and device validation are complete, and the checkpoint is closed
on 2026-08-07.

**Start condition:** run the primary audit after the M1-C re-score gate is met.
Before that gate, a session may capture the current flow and document usability
evidence, but it must not spend the product-proof budget on broad visual polish.

### Start here

1. Read `VISION.md`, `DATA_CONTRACT.md`, this roadmap, and the relevant
   `JOURNAL.md` decisions before inspecting UI code.
2. Use `ui-skills-root` to select no more than three skills for the session.
   The expected starting set is `impeccable` (`shape` or `critique`),
   `improve-ui`, and `create-design-md`; use `frontend-design` only after the
   user confirms a direction.
3. Trace one coherent flow on Pixel 8 API 35:
   `capture → structure → challenge → review → confirm/reject`.
4. Inspect the governing Jetpack Compose theme, shared primitives, real copy,
   and rendered states. Do not apply web-only `design-lab` route assumptions to
   the Android application.
5. Produce an evidence-based `DESIGN.md` and prioritized design plans before
   changing production UI.

### Audit scope

- Make raw user text, AI hypotheses, user edits, and confirmed conclusions
  unmistakably different without weakening their provenance links.
- Test whether the primary action and the next reversible choice are clear at
  every step, including reject and continue-discussion paths.
- Cover first-run, empty, analyzing, local-only, network-blocked, error,
  long-text, permission, and restart-recovery states.
- Review microcopy for agency: AI proposes; the user authors meaning. Semantic
  confirmation and permission to transmit data must remain separate actions.
- Check Android accessibility, touch targets, focus order, screen-reader
  semantics, contrast, reduced motion, and dynamic text.
- Treat the M3 home experience and advanced provenance/graph views as separate
  surfaces; do not combine them into the first audit.

### Completion criteria

- A user-confirmed UX brief names the primary job, success condition, anti-goals,
  platform constraints, and states the builder must not invent.
- `DESIGN.md` records the evidenced Compose/Material 3 design language rather
  than generic aesthetic preferences.
- The audit reports at most three verified, non-overlapping findings for the
  selected flow and identifies the highest-leverage correction.
- The approved implementation plan includes default, loading, empty, error,
  disabled, permission, and long-content behavior plus accessibility checks.
- Any implemented follow-up is validated on the emulator and cannot blur the
  raw/proposed/confirmed boundary or the privacy contract.

### Audit result and approved direction

The primary flow scored `20/40` on the bounded Nielsen review and `12/20` on
the native Android technical audit. The review confirmed three non-overlapping
P1 findings:

1. Review presents source, five analysis groups, alternative, user wording,
   and actions as a flat evidence stack; the authorship decision and its
   accessibility semantics are not dominant.
2. First-session and transition copy is inconsistent with the current product
   contract, including stale voice/AI framing, incorrect processing messages,
   and storage-facing provenance language.
3. Confirm/reject lack a clear reversible next step; permission denial and
   interrupted unsaved capture do not have complete recovery states.

The user approved the `1A + 2A + 3A` direction:

- optimize for consciously authoring and confirming the user's own conclusion;
- make Review a dominant `source -> proposal -> my wording` path with analysis
  behind explicit progressive disclosure;
- address all three P1 findings in one implementation stage.

The approved visual direction is **Selective Liquid Glass / Echo Glass**. It
adapts the idea to Android rather than copying iOS: glass is limited to the
functional layer (top context, action dock, and disclosure controls), while
raw source, unconfirmed proposal, editable wording, and confirmed conclusion
remain readable Material tonal surfaces. Material components, icons, system
navigation, dynamic color, dark theme, and Android accessibility conventions
remain authoritative.

The Review flow rework (P1 #1 `source -> proposal -> my wording` with
progressive disclosure, P1 #2 copy/provenance labels, P1 #3 recoverable
post-confirm/reject and permission-denied states) is implemented in
`RecordScreen.kt` / `RecordViewModel.kt`, with a bounded Echo Glass action
dock. Desktop compile, unit tests, and the full instrumented suite (12/12)
pass on the Pixel 8 API 35 emulator, including the reworked `ReflectionScreenTest`.
TalkBack and scaled-font passes remain before this line is marked fully
validated. Signed: `агент opencode`.

**Device validation result (2026-08-07):** the manual on-device pass
(`docs/DEVICE_VALIDATION.md`) is complete and committed (`bc225f9`). TalkBack,
200% font scale, rotation/wide, and recovery-state passes all succeeded. The
API 26-30 glass fallback is implemented but not device-verified (no accessible
API 26-30 environment); it is deferred to product-maintenance rather than
blocking the checkpoint, whose target device is API 35. The checkpoint is
marked complete on 2026-08-07. Signed: `агент opencode`.

`PRODUCT.md` now records durable product truth. `DESIGN.md` records the
evidenced incumbent Compose/Material 3 language; it must be updated with Echo
Glass only after the direction is implemented and visually validated. The
target-specific contract is stored in the Impeccable surface brief for
`RecordScreen.kt`.

### Approved implementation plan

1. Produce three portrait compositions inside the confirmed Echo Glass world:
   a glass action dock, an analytical disclosure lens, and an adaptive expanded
   layout. Select one before production UI edits.
2. Correct onboarding/product framing, transition-specific status copy, and
   user-facing provenance terminology.
3. Restructure Review around source, proposal/alternative, and editable user
   wording; move detailed analysis behind an explicit disclosure.
4. Add programmatic field labels, TalkBack announcements and focus handling,
   permission-denied recovery, unsaved-capture protection, and reversible
   post-confirm/reject actions.
5. Implement bounded glass primitives for stationary functional surfaces.
   Android 12+ may use measured blur/compositing; API 26-30 uses an opaque
   tonal fallback. Do not blur scrolling content or make color the only state
   signal.
6. Validate default, loading, empty, error, disabled, permission, confirmed,
   rejected, restored, interrupted, and long-content states; include TalkBack,
   200% font scale, contrast, reduced motion, IME, landscape/expanded width,
   API fallback, and Pixel 8 API 35 performance.
7. Re-run the existing provenance/privacy regression suites, complete the
   bounded visual finish review, then update `DESIGN.md` and this roadmap with
   implemented evidence.

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

- [x] Capture and persist immutable raw text before analysis.
- [x] Produce one local structured draft and counterargument.
- [x] Let the user edit, reject, or explicitly confirm the proposal.
- [x] Persist the confirmed wording as revision 1 linked to its source.

### M1-B — Vertical-slice proof

- [x] Verify restart, rejection, deletion, export, and network-boundary behavior.
- [x] Validate the full flow on Pixel 8 API 35.
- [x] Record evidence and remaining limitations in `JOURNAL.md`.

### M1-C — Useful clarification

- [x] Record a scored synthetic baseline and verify that all eight raw inputs
  persist locally.
- [x] Add the eight evaluation inputs as analyzer regression fixtures.
- [x] Remove duplicated classifications and verbatim suggested conclusions.
- [x] Generate grounded, scenario-specific, language-matched clarification.
- [x] Protect cautious and observational control inputs from manufactured
  disagreement.
- [x] Meet the documented re-score gate without regressing M0/M1 boundaries.
