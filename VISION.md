# EchoMind — Product Vision

**Status:** agreed product direction

**Last updated:** 2026-07-29

> EchoMind documents not only what a person thinks, but how and why those thoughts change.

## Product promise

EchoMind is a private thinking environment that turns short reflections into a visible, evolving, and traceable model of the user's conclusions.

It helps the user:

- separate an observation from its interpretation;
- notice hidden assumptions and self-persuasion;
- preserve conclusions together with their context and grounds;
- see recurring themes, contradictions, and changes over time;
- receive explainable guidance for subjective decisions when enough relevant evidence exists.

EchoMind never makes a decision for the user. It shows relevant history, competing interpretations, and uncertainty so that the user can decide with less repetitive mental load.

## The problem

Reflective people already use notes, journals, ChatGPT, Claude, and saved media to work with their thoughts. Their material is still fragmented:

- AI conversations remain buried in chat histories;
- model memory is opaque and cannot be inspected as a coherent personal record;
- ordinary notes preserve text but rarely preserve relationships or changes of mind;
- a polished self-description can survive even after the evidence for it has changed;
- repeated low-stakes deliberation consumes attention that could be spent on harder work.

The missing product is not another diary or chatbot. It is user-owned documentation of an evolving thought process.

## Target user

The first EchoMind user:

- already reflects through notes, journals, or conversations with an LLM;
- regularly encounters ideas worth preserving after reading, watching, working, or talking;
- is willing to spend about one minute capturing a meaningful thought;
- wants an AI that can challenge an interpretation instead of merely agreeing;
- values inspectable sources and control over personal data.

EchoMind is not designed for a user who expects passive surveillance, automatic life decisions, clinical diagnosis, or useful personal predictions without providing any reflection or feedback.

## The unit of value

The primary product object is not a note. It is an **evolving conclusion** with provenance.

The model distinguishes:

- **raw record** — what the user originally wrote or recorded;
- **observation** — an event or fact reported by the user;
- **interpretation** — the meaning currently assigned to an observation;
- **AI hypothesis** — an unconfirmed suggestion made by the system;
- **confirmed conclusion** — a formulation explicitly accepted or edited by the user;
- **grounds** — source records, reported outcomes, and related conclusions;
- **counterevidence** — material that weakens or contradicts a conclusion;
- **revision** — a dated change to a conclusion;
- **theme** — a recurring line that connects several conclusions;
- **decision and outcome** — what the user chose and what they later reported happened.

An AI hypothesis must never silently become part of the user's model.

## Core interaction loop

1. **Capture.** The user spends roughly one minute describing a situation and the main flow of thought. Text is the primary input; voice is optional.
2. **Structure.** EchoMind separates the tentative thesis, observations, grounds, assumptions, and open questions.
3. **Challenge.** It proposes one meaningful counterargument or alternative interpretation when justified by the material.
4. **Review.** The user accepts, edits, rejects, or continues the dispute.
5. **Confirm.** Only the user-approved formulation becomes a confirmed conclusion.
6. **Connect.** EchoMind links the conclusion to relevant prior material and preserves its revision history.
7. **Resurface.** At an appropriate time, the system returns a recurring motive, contradiction, or unfinished question and explains why it is relevant.
8. **Advise on request.** When asked, EchoMind offers guidance based on traceable grounds and states uncertainty or lack of evidence.
9. **Calibrate.** If the user later reports the outcome of a decision, the system updates the relevant conclusions and the reliability of future guidance.

The user may stop after any step. Refusing feedback does not disable the basic reflection and retrieval experience; it only limits the depth of predictions that EchoMind can honestly make.

## Two horizons of value

### Immediate value

From the first record, EchoMind can help expose a hidden assumption, clarify a conclusion, and preserve the result. The user should not have to wait months for the product to become useful.

### Compounding value

With an accumulated history, EchoMind can:

- detect recurring themes;
- surface contradictions between old and recent conclusions;
- show how a view changed and what caused the change;
- return relevant prior reasoning;
- support subjective decisions with comparable past situations;
- improve guidance using reported outcomes.

Accumulated data increases capability only in the domains it actually covers. Many records about books do not justify confidence about friendships or health.

## Home experience and visible development

The home screen should prioritize meaning rather than data volume:

- a prompt to capture or continue a thought;
- one relevant theme, contradiction, or unfinished line with visible sources;
- fast access to a new record and the archive;
- an inspectable view of what EchoMind can and cannot currently support.

Development is shown per theme through real evidence such as confirmed conclusions, revisions, contradictions, and reported outcomes. EchoMind must not claim to "know the user by 73%" or use an arbitrary engagement level.

Visualizations, including maps and heatmaps, are useful only when they help answer a semantic question. They are not the product's primary value.

## Guidance contract

EchoMind:

- gives advice only after an explicit user request;
- cites the records and conclusions used;
- separates reported facts, user interpretations, and AI hypotheses;
- presents uncertainty and relevant counterevidence;
- asks for more context or refuses to advise when evidence is insufficient;
- treats personality labels as hypotheses, not identities;
- allows every derived conclusion to be inspected, corrected, or deleted.

EchoMind does not:

- decide on behalf of the user;
- invent missing context;
- diagnose mental health conditions;
- attribute hidden motives or trauma as facts;
- present a personality as fixed;
- optimize for agreement, dependency, or time spent in the app.

## Privacy contract

The privacy model is local-first and must be enforced by the product, not left as a settings label.

- Raw records and the complete personal model remain on the user's device.
- No product telemetry is collected.
- Confirming a conclusion does not grant permission to transmit it.
- Any remote request is purpose-bound and initiated by the user.
- Redaction and context minimization happen before transmission.
- The user sees and approves the exact derived context that will leave the device.
- A remote model receives only the minimum required fragment and is never treated as permanent memory.
- If safe context is insufficient, EchoMind asks the user for additional information or declines to answer; it does not upload raw history.
- Export and deletion cover both raw records and every derived object.

When a third-party endpoint is configured, EchoMind must clearly state that the provider's retention policy is outside EchoMind's control.

## Product principles

1. **Useful on day one, better with history.**
2. **Evidence before confidence.**
3. **The user authors meaning; AI proposes.**
4. **Simple capture outside, rich model inside.**
5. **Every derived claim is inspectable and reversible.**
6. **Honest insufficiency is better than persuasive invention.**
7. **Feedback is an optional value exchange, not a moral obligation.**
8. **Semantic usefulness comes before decorative complexity.**
9. **Privacy claims must correspond to enforced data flow.**

## Non-goals

EchoMind is not:

- a generic voice diary;
- a task manager or productivity dashboard;
- an autonomous life coach;
- a therapist or diagnostic tool;
- an opaque "digital twin";
- a social network;
- a system that passively collects the user's life;
- a 3D visualization searching for a use case.

Voice capture, richer visualization, and prediction are possible extensions. They must support the core loop rather than replace it.

## Measures of success

The product succeeds when:

- a first-time user reaches a confirmed clarification in one session;
- the user voluntarily brings a second thought because the first interaction was useful;
- a resurfaced theme helps recover reasoning that would otherwise have been lost;
- every piece of guidance can show its grounds and uncertainty;
- reported outcomes improve later guidance within the same domain;
- privacy invariants remain true under automated and manual verification.

Time spent, entry count, and model complexity are not success metrics by themselves.
