# EchoMind Data and Privacy Contract

**Status:** M0 implementation contract

**Last updated:** 2026-07-30

This document turns the promises in [VISION.md](VISION.md) into storage and
network rules. Room schema version 3 implements the minimum provenance model
alongside the legacy `entries` table used by the current UI.

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
| Room `ai_hypotheses` | draft JSON, counterargument, status, source and creation metadata | Derived proposal + operational | User | Yes |
| Room `conclusions` | raw source, current revision pointer, creation time | Confirmed + operational | User | Yes |
| Room `conclusion_revisions` | versioned text, author, creation time | Confirmed + operational | User | Yes |
| Room `evidence_links` | revision/source IDs, relationship, confirmation status | Derived or confirmed according to status | User | Yes |
| Encrypted audio file | recorded audio | Raw | User | Yes, decrypted only in the warned plaintext export |
| DataStore `settings` | `local_mode` | Operational privacy choice | User | No |
| DataStore `settings` | `api_endpoint` | Identifying configuration | User | No |
| Encrypted preferences | API key | Secret | User | Never |
| Encrypted preferences | SQLCipher passphrase | Secret | EchoMind | Never |
| DataStore `onboarding` | completion flag | Operational | EchoMind | No |
| External files | generated ZIP export | Raw + derived copy | User | The ZIP is the export and is plaintext |

Debug HTTP logging records request metadata only. Request and response bodies
must not be logged.

## Local entities

Schema version 3 implements the first five objects. Later milestones add the
remaining objects without changing the confirmation boundary.

| Object | Minimum fields | Rule |
|---|---|---|
| `RawRecord` | `id`, original text, optional encrypted audio reference, creation time | Immutable content; corrections create another record |
| `AiHypothesis` | `id`, source record ID, structured draft, counterargument, creation time, status | Always visibly unconfirmed |
| `Conclusion` | `id`, source record ID, creation time, current revision ID | Exists only after explicit confirmation |
| `ConclusionRevision` | `id`, conclusion ID, text, creation time, author | Append-only; user edits create a revision |
| `EvidenceLink` | `id`, conclusion revision ID, source object ID, supports/contradicts, confirmation status | AI links remain proposals until confirmed |
| `Theme` | `id`, user-owned name, creation time, archived time | A durable theme is created or renamed by the user |
| `ThemeLink` | `id`, theme ID, conclusion ID, confirmation status | AI clustering produces only a proposed link |
| `Decision` | `id`, question, user choice, creation time, optional source revision IDs | Stores the user's choice separately from any suggestion |
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
  -> OUTCOME_REPORTED -> Outcome
  -> REVIEWED -> optional proposed conclusion revision
```

Only a user action can perform `CONFIRMED`. Generation, retry, restart, import,
or background work cannot perform it. Rejection is durable enough to prevent a
stale proposal from reappearing as confirmed, but it creates no conclusion.
An outcome can propose a revision, but the existing conclusion changes only
after the normal review and confirmation transition.

## Deletion and export

- Deleting a proposal removes only that proposal.
- Deleting a conclusion removes its revisions and evidence links. Its raw
  source remains unless the user explicitly selects it too.
- Deleting a raw record removes its encrypted audio and dependent unconfirmed
  proposals. A foreign-key restriction rejects deletion while a conclusion
  cites it; M1 must ask the user to delete that conclusion first or cancel.
- Deleting a theme, decision, or outcome never deletes the records or
  conclusions it references.
- Export uses stable IDs and includes raw records, hypotheses with statuses,
  conclusions, revisions, evidence links, and encrypted-audio filenames.
  Manifest version 2 keeps legacy generated fields under
  `analysisStatus=legacy_unconfirmed`. Secrets, endpoint configuration, and
  encryption keys are excluded.

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
background requests never pass this boundary. The current prototype still
sends raw material when local mode is disabled; remote mode therefore remains
an acknowledged M0 gap until this pipeline replaces those calls.

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
