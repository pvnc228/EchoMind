# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

EchoMind is for people who already reflect through notes, journals, or conversations with an LLM and want to preserve meaningful thoughts without surrendering authorship or control of personal data. The primary user arrives with a situation or interpretation they want to understand and is willing to spend about one minute capturing it.

## Product Purpose

EchoMind turns a raw reflection into an inspectable local proposal and then, only through explicit user review, into a user-authored confirmed conclusion with provenance. The primary first-session success is that the user can distinguish their original words, EchoMind's suggestion, and the wording they choose to endorse, then confirm or reject it with a clear reversible next step.

## Positioning

EchoMind is not another diary or chatbot. Its distinctive mechanism is a local, traceable transition from raw source to unconfirmed proposal to edited or confirmed conclusion. AI proposes; the user authors meaning. Confirmation never grants permission to transmit data.

## Operating Context

The primary flow is text-first Android capture followed by local structuring, one grounded alternative or question, review, one optional bounded follow-up, edit, confirm or reject, and inspection of the saved source relationship. Voice is an optional attachment. The user may be interrupted and return to a pending proposal; later product stages connect confirmed conclusions across time.

## Capabilities and Constraints

- Jetpack Compose and Material 3 are the native UI foundation.
- Raw records and the complete personal model remain on device.
- The current reflection analyzer is deterministic and local; its output is never authoritative.
- Raw source, local proposal, user-edited wording, and confirmed conclusion must remain visibly and programmatically distinct.
- Confirm, reject, revise, deletion, export, restart recovery, and any future transmission approval are separate state transitions.
- Remote Q&A may transmit only the exact minimized confirmed context shown in a preview after separate one-request consent. Raw records, unconfirmed proposals, and the complete personal model remain blocked.
- The first UX implementation scope is the M1 reflection flow, not the future meaning-first Home, graph views, personalization, prediction, or broad visualization.

## Brand Commitments

The product name is EchoMind. Its voice is calm, precise, non-diagnostic, and agency-preserving. It states uncertainty and insufficiency honestly and avoids engagement pressure, personality certainty, or claims that it knows the user better than the evidence supports.

## Evidence on Hand

- `VISION.md` defines the agreed product direction and non-goals.
- `DATA_CONTRACT.md` defines ownership, provenance, confirmation, deletion, export, and network boundaries.
- `M1_USABILITY_EVALUATION.md` records the frozen first-session usefulness rubric and the completed M1-C synthetic gate.
- `JOURNAL.md` records implementation and emulator evidence through the M0 consent and M1 bounded-discussion slices.
- The Compose implementation and tests cover capture, local proposal, review, confirm, reject, saved provenance, and restart behavior.
- No external-user usefulness study, testimonials, clinical evidence, or product-market-fit evidence exists and none may be invented.

## Product Principles

1. Useful on day one, better with history.
2. Evidence before confidence.
3. The user authors meaning; AI proposes.
4. Simple capture outside, rich and inspectable provenance inside.
5. Privacy, reversibility, and honest insufficiency are product behavior, not decorative claims.
6. BYOK and Zero Vendor Lock-in: Zero bundled SaaS keys or proprietary provider SDKs. All optional remote capabilities use open standards, local-first options (self-hosted / Ollama), and user-owned credentials stored only on-device.

## Accessibility & Inclusion

The primary flow must work with TalkBack, logical focus order, explicit state announcements, Android touch targets, dynamic text, sufficient contrast, reduced motion, IME insets, and long content. Permission denial, loading, disabled, error, confirmed, rejected, restored, and interrupted states are part of the accessibility contract.
