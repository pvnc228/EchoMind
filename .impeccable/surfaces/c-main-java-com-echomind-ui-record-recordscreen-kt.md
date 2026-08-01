---
version: 1
slug: "c-main-java-com-echomind-ui-record-recordscreen-kt"
primary_target: "app/src/main/java/com/echomind/ui/record/RecordScreen.kt"
related_targets: ["app/src/main/java/com/echomind/ui/onboarding/OnboardingScreen.kt","app/src/main/java/com/echomind/ui/home/HomeScreen.kt","app/src/main/java/com/echomind/ui/detail/DetailScreen.kt"]
---

## Scope and mode

- Target: `RecordScreen.kt`, with onboarding/Home as entry context and Detail as the saved-provenance endpoint.
- Visitor mode: Operate.
- Scope: the M1 text-first reflection flow only; future meaning-first Home, graph/provenance explorer, personalization, prediction, and remote assistance stay out of scope.

## Audience, job, and proof

- A reflective Android user arrives with a situation or interpretation they want to understand, often with limited attention and potentially sensitive text.
- Primary job: turn that thought into wording the user consciously endorses or rejects in about one minute.
- Success: the user can immediately distinguish raw words, EchoMind's local proposal, and their own editable wording; confirm/reject and the next reversible action are clear without weakening provenance.
- Product-specific proof: local processing, explicit authorship labels, preserved source links, and confirmation that is separate from any transmission consent.

## Chosen direction and memorable moment

- Direction: **Selective Liquid Glass / Echo Glass**, adapted to Android Material 3 rather than copied from iOS.
- Structural thesis: a dominant `source -> proposal/alternative -> my wording` path; observations, interpretations, assumptions, and questions sit behind explicit progressive disclosure.
- Glass marks the functional layer only: top context, bottom action dock, and analytical disclosure controls. Provenance-bearing content remains on readable tonal surfaces.
- Memorable moment: the user's editable wording is visually calm and unmistakably theirs while the glass action dock offers Confirm, Reject, or Continue examining without covering the evidence.

## States and interaction contract

- Cover capture, restoring, saving raw source, local structuring, review, confirming, rejecting, confirmed, rejected, error, disabled, permission denied, permission permanently denied, interrupted unsaved capture, restored proposal, long content, and audio attached/recording.
- Confirm and Reject remain explicit authorship transitions. After either outcome, provide an immediate reversible route: revise/undo after confirmation and continue examining/undo after rejection.
- State transitions receive exact progress copy, TalkBack announcements, and deliberate focus placement. The user-authored conclusion field has its own programmatic label.
- Preserve text-first capture and treat voice as optional. Never introduce a network-send affordance into this flow.

## Platform and visual constraints

- Keep Material components, Material icons, system Back, edge-to-edge/insets, IME behavior, dynamic color, dark theme, 48 dp targets, and Android adaptive layout conventions authoritative.
- Android 12+ may use bounded, performance-measured blur/compositing on stationary controls. API 26-30 uses a readable opaque tonal fallback. Do not blur scrolling cards or use transparency/color as the only state signal.
- Honor reduced motion, increased contrast, TalkBack, 200% font scale, compact/landscape/expanded widths, and long Russian/English content.
- `FLAG_SECURE` remains enabled; visual QA uses isolated Compose test surfaces plus accessibility-tree verification in the real activity.

## Delivery and unresolved implementation choices

- Before code, present three portrait compositions within this confirmed world: glass action dock, analytical disclosure lens, and adaptive expanded layout. The user selects one composition.
- A bounded technical spike decides whether the current Compose stack is sufficient for the glass layer or whether a narrowly justified library/Compose upgrade is needed. The builder must not add a dependency or raise SDK/tooling versions without evidence from that spike.
- Finish requires regression tests for provenance/privacy, semantics and state tests, Pixel 8 API 35 runtime validation, API 26-30 fallback evidence, a bounded visual finish review, and `DESIGN.md` updated from the implemented result.
