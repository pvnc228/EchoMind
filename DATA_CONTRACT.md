# EchoMind Data and Privacy Contract

**Status:** active implementation contract

**Last updated:** 2026-08-10

This document turns the promises in [VISION.md](VISION.md) into storage and
network rules. Room schema version 8 includes the provenance graph, immutable
and pending link metadata, decision guards, durable capture drafts,
fingerprint-keyed Home-card dispositions, and the Unicode-aware manual-search
key. It also persists failed audio cleanup attempts for WorkManager retry; this
queue and the derived search key are operational and are not exported.
Export manifest version 5 restores only into an empty profile; merge and
selective import remain deferred.

## Retrieval performance boundary

The Detail ranked-link operation is a non-blocking suggestion aid, not a
manual archive browser. Its public repository seam must keep the five-result
cap and execute candidate projection, mapping, and CPU ranking off the Main
dispatcher. On the reference `Pixel_8_2` runtime, the end-to-end operation is
budgeted at **2,000 ms or less for both 1,000 and 10,000 raw records**. The
Android 1k/10k benchmark measures the complete repository call, including its
Room projection, rather than timing the pure ranker in isolation. This is a
UX safety budget for the reference runtime, not a latency guarantee for every
device.

## Data classes

- **Raw:** the user's original text or audio. The user owns it. It stays on the
  device and is included in a full export.
- **Derived proposal:** AI- or heuristic-generated structure that the user has
  not confirmed. EchoMind owns its provenance; the user may edit, reject, or
  delete it. It is never treated as a belief.
- **Confirmed:** wording or links explicitly accepted or edited by the user.
  The user owns it. Confirmation does not grant transmission permission.
- **Identifying or secret:** credentials, endpoint details, encryption keys,
  and any content that can identify the user. It is never exported by default.
- **Operational:** timestamps, identifiers, status, and version metadata needed
  to make transitions and deletion inspectable.

## Current persisted fields

| Store | Field | Class | Owner | Export |
|---|---|---|---|---|
| Room `entries` | `id`, `created_at`, `duration_ms` | Operational | EchoMind | Yes |
| Room `entries` | `transcript`, `audio_path` content | Raw | User | Yes |
| Room `entries` | `summary`, `tasks`, `ideas`, `emotions`, `category`, `tags` | Derived proposal; legacy code does not record confirmation | User | Yes, labelled `legacy_unconfirmed` |
| Room `raw_records` | original text, encrypted audio reference, duration, creation time | Raw + operational | User | Yes |
| Room `raw_records` | `original_text_search_key` (NFKC + `Locale.ROOT` lowercase) | Derived operational search index; rebuilt from `original_text` | EchoMind | No |
| Room `ai_hypotheses` | draft JSON, counterargument, status, source and creation metadata | Derived proposal + operational | User | Yes |
| Room `conclusions` | raw source, current revision pointer, creation time | Confirmed + operational | User | Yes |
| Room `conclusion_revisions` | versioned text, author, creation time | Confirmed + operational | User | Yes |
| Room `evidence_links` | revision/source IDs, relationship, status, origin, review metadata, graph timestamp | Derived or confirmed according to status | User | Yes |
| Room `themes` | user-owned name, creation, archived time | Confirmed + operational | User | Yes |
| Room `theme_links` | theme/revision IDs, confirmed flag, origin, review-required flag, creation time | Confirmed or pending + operational | User | Yes |
| Room `decisions` | question, choice, source revision, derived state, suggestion provenance | User-owned decision; system suggestion requires author/source/status | User | Yes |
| Room `capture_drafts` | text, encrypted completed-audio path, duration, capture stage, timestamps | Raw operational draft; never confirmed automatically | User | Yes |
| Room `home_card_dispositions` | fingerprint, card/scope identity, dismiss/postpone timestamps | Operational user choice | User | Yes |
| Room `audio_cleanup_queue` | app-owned audio path, entry ID, failure time, attempt count | Operational partial-cleanup state | User | No |
| Encrypted audio file | recorded audio | Raw | User | Yes, decrypted only in the warned plaintext export |
| DataStore `settings` | `local_mode` | Operational privacy choice | User | No |
| DataStore `settings` | `api_endpoint` | Identifying configuration | User | No |
| DataStore `follow_up` | decision ID, trigger time, reminder status | Operational local follow-up state | User | No |
| Encrypted preferences | API key | Secret | User | Never |
| Encrypted preferences | SQLCipher passphrase | Secret | EchoMind | Never |
| DataStore `onboarding` | completion flag | Operational | EchoMind | No |
| External files | generated ZIP export | Raw + derived copy | User | The ZIP is the export and is plaintext |

Debug HTTP logging records request metadata only. Request and response bodies
must not be logged.

## Local entities

Schema version 3 implemented the first five objects; schema version 4 added
themes and theme links (M2); schema version 5 added decisions and outcomes
(M4); schema version 6 adds graph-review metadata, durable drafts, and Home
dispositions; schema version 7 adds the bounded audio-cleanup retry state;
schema version 8 adds `raw_records.original_text_search_key`, a derived,
non-exported key used for bounded Unicode-aware manual search. `MIGRATION_7_8`
backfills that key from every legacy row using the same NFKC/ROOT-lowercase
normalization used for new records. Export manifests continue to carry the raw
original text, never this derived search key.
`MIGRATION_5_6` preserves rows and deterministically deduplicates
conflicting link pairs; it does not use destructive fallback. Historical links
are never moved to a new revision; inherited links are pending until reviewed.

| Object | Minimum fields | Rule |
|---|---|---|
| `RawRecord` | `id`, original text, optional encrypted audio reference, creation time | Immutable content; corrections create another record |
| `AiHypothesis` | `id`, source record ID, structured draft, counterargument, creation time, status | Always visibly unconfirmed |
| `Conclusion` | `id`, source record ID, creation time, current revision ID | Exists only after explicit confirmation |
| `ConclusionRevision` | `id`, conclusion ID, text, creation time, author | Append-only; user edits create a revision |
| `EvidenceLink` | `id`, conclusion revision ID, source object ID, supports/contradicts, confirmation status | AI links remain proposals until confirmed |
| `Theme` | `id`, user-owned name, creation time, archived time | A durable theme is created or renamed by the user |
| `ThemeLink` | `id`, theme ID, conclusion revision ID, confirmation status, origin, review-required flag | A copied membership remains pending until the user confirms it |
| `Decision` | `id`, question, choice, source revision, suggestion provenance, creation time | Derived state is `CREATED -> CHOSEN -> OUTCOME_REPORTED`; grounded system suggestions require metadata |
| `Outcome` | `id`, decision ID, user report, creation time | Optional and user-authored; it never rewrites a conclusion automatically |

Themes, decisions, and outcomes refer to confirmed revisions rather than
copying their text.

## State transitions

```text
RawRecord
  -> AiHypothesis.PROPOSED
       -> EDITED -> CONFIRMED -> Conclusion + ConclusionRevision(v1)
       -> CONFIRMED          -> Conclusion + ConclusionRevision(v1)
       -> REJECTED           -> no Conclusion

ConclusionRevision(vN)
  -> REVISED -> ConclusionRevision(vN+1)
  -> DELETED -> remove the conclusion aggregate, keep unrelated records

ThemeLink.PROPOSED
  -> CONFIRMED -> durable membership
  -> REJECTED  -> no durable membership

Decision.CREATED
  -> CHOSEN -> OUTCOME_REPORTED -> Outcome
  -> REVIEWED -> optional proposed conclusion revision
```

Only a user action can perform `CONFIRMED`. Generation, retry, restart, import,
or background work cannot perform it. Rejection is durable enough to prevent a
stale proposal from reappearing as confirmed, but it creates no conclusion.
An outcome can propose a revision, but the existing conclusion changes only
after the normal review and confirmation transition.

The implemented M1-A transition is:

1. `captureRawText` commits the archive entry and immutable `RawRecord` before
   any analysis begins;
2. the on-device deterministic analyzer stores a structured
   `AiHypothesis(PROPOSED)` and one alternative interpretation without making a
   network request;
3. editing changes only the user's confirmation field; the stored proposal
   remains inspectable as the original system wording;
4. reject changes the proposal to `REJECTED` and creates no conclusion;
5. explicit confirm atomically changes the proposal to `CONFIRMED`, creates a
   `Conclusion`, appends user-authored `ConclusionRevision(version=1)`, updates
   the current revision pointer, and adds a confirmed source evidence link.

The newest proposed reflection is restored when the capture screen is reopened.
Confirmed wording and its source/revision relationship survive database reopen.

## Deletion and export

- Deleting a proposal removes only that proposal.
- Deleting a conclusion removes its revisions and evidence links. Its raw
  source remains unless the user explicitly selects it too.
- Deleting a raw record removes its encrypted audio and dependent unconfirmed
  proposals. A foreign-key restriction rejects deletion while a conclusion
  cites it. The detail screen explains that relationship and requires an
  explicit choice to delete the conclusion and source together or cancel.
  The repository deletes conclusion, revisions, and links before the raw
  record and archive entry in one Room transaction, then removes attached
  audio. A filesystem failure is surfaced instead of silently claiming success.
- Deleting a theme, decision, or outcome never deletes the records or
  conclusions it references.
- Deleting a conclusion (with its revisions) cascades the removal of its
  confirmed theme links and evidence links; the theme itself stays.
- Export uses stable IDs and includes raw records, hypotheses with statuses,
  conclusions, revisions, evidence links, themes, pending/confirmed theme
  links, decisions, outcomes, active capture draft, Home dispositions, and
  encrypted-audio filenames. Manifest version 5 includes content hashes and
  explicit audio metadata. Secrets, endpoint configuration, and encryption
  keys are excluded.

## Remote-request pipeline

Remote access is denied by default and evaluated at the repository boundary:

1. derive the smallest task-specific context locally;
2. redact direct identifiers locally;
3. show the exact outgoing context and provider destination;
4. obtain consent for this request only;
5. re-check that local mode is off;
6. send the approved derived context;
7. discard request-only state after completion.

Raw records, raw audio, the complete personal model, secrets, and unconfirmed
background requests never pass this boundary. Legacy entry analysis stays
on-device even when local mode is off. Legacy Q&A and transcription fail before
`LlmApi` in both modes: local mode reports that networking is disabled, while
remote mode reports that minimized preview and per-request approval are
required. Remote assistance remains unavailable until that pipeline exists.

Remote providers may retain prompts, responses, network metadata, or abuse
monitoring records under their own policies. EchoMind cannot verify or delete
provider-side copies. The preview must name the configured destination and
state this limitation before consent.

## Version 2 to 3 migration

`MIGRATION_2_3` creates the provenance tables and copies each legacy entry's ID,
transcript, audio reference, duration, and creation time into a `RawRecord`.
It creates no hypothesis, conclusion, revision, or evidence link. Legacy
generated fields remain in `entries` and export as unconfirmed.

Destructive fallback has been removed. Version 2 databases migrate in place.
Older unsupported alpha schemas fail closed and use this explicit reset path:

1. optionally export current entries from Settings, acknowledging that the ZIP
   is plaintext;
2. clear the app's storage or uninstall it;
3. install the current build and review any imported material as unconfirmed.

The instrumented migration test verifies raw preservation and zero confirmed
conclusions after upgrade.
