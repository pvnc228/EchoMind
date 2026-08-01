---
target: EchoMind M1 reflection flow
total_score: 20
max_score: 40
na_heuristics:
p0_count: 0
p1_count: 3
timestamp: 2026-08-01T12-56-09Z
slug: c-main-java-com-echomind-ui-record-recordscreen-kt
---
## EchoMind M1 reflection flow — UX critique

### Design Health Score

| # | Heuristic | Score | Key issue |
|---|---|---:|---|
| 1 | Visibility of system status | 2 | Confirm and reject reuse a processing message about structuring the original text. |
| 2 | Match with the real world | 3 | Capture copy is natural; saved provenance exposes storage terminology. |
| 3 | User control and freedom | 2 | Back and reject exist, but immediate revise, undo, or continue-challenge actions do not. |
| 4 | Consistency and standards | 2 | Material controls are consistent, but onboarding, Home, and reflection describe different products. |
| 5 | Error prevention | 2 | Blank actions are prevented; unsaved capture can be abandoned without warning. |
| 6 | Recognition rather than recall | 3 | Source, proposal, alternative, user wording, and actions remain co-located and labelled. |
| 7 | Flexibility and efficiency | 1 | The flow has one rigid review path. |
| 8 | Aesthetic and minimalist design | 2 | Capture is focused; Review and Detail become long, equally weighted stacks. |
| 9 | Error recognition and recovery | 2 | Saved work is usually preserved, but permission denial is silent and errors offer generic retry. |
| 10 | Help and documentation | 1 | Inline guidance exists, but provenance vocabulary and decision consequences lack contextual explanation. |
| **Total** |  | **20/40** | **Acceptable; significant UX work remains.** |

Native technical health is **12/20**: Accessibility 2/4, Performance 3/4, Appearance and Theming 3/4, Platform Conformance 3/4, Adaptivity 1/4.

### Design Specificity Verdict

EchoMind is concept-specific but visually interchangeable. The interaction copy and state model clearly belong to the product, while the presentation remains mostly stock Material 3: default components, dynamic device colors, repeated cards, and generic hierarchy. The product has authored semantics but not yet an authored visual language.

### Overall Impression

Capture is the strongest surface: one reflective question, one primary text field, a local-processing promise, and optional voice capture. Review is the emotional and cognitive valley. It turns a potentially vulnerable thought into a taxonomy dump and asks the user to compare, interpret, edit, and commit at once. Rejection is unusually honest and reassuring; confirmation and saved Detail underuse the peak-end moment.

### What Works

- Raw words, local proposal, editable user wording, confirmation, and rejection are explicitly separated.
- Confirmation does not smuggle in transmission consent; the assessed flow is local.
- Failure and rejection preserve provenance honestly, including whether the raw text was already saved.

### Priority Issues

1. **P1 — Review hides the authorship decision inside a flat, partially inaccessible evidence stack.** `RecordScreen.kt:306-343` and `RecordScreen.kt:418-489` give source, five analysis categories, alternative, and the user field nearly equal weight. The conclusion field has no programmatically associated label, and stage changes have no live announcement or deliberate focus move. The highest-leverage correction is one dominant `source → proposal → my wording` path, with analytical detail available on demand and explicit TalkBack semantics.

2. **P1 — Product and status language is not trustworthy as one system.** `OnboardingScreen.kt:41-55` still teaches a voice diary with broad AI insights; `RecordScreen.kt:142-147` reports that the original is being structured during confirm and reject; Detail exposes system terms such as revision and source-link status. Align the first-session promise, transition-specific status, and user-facing provenance vocabulary with the text-first local authorship contract.

3. **P1 — Reversibility and recovery stop at the decision boundary.** `RecordScreen.kt:333-400` offers Confirm or Reject followed only by Done; there is no continue-challenge, immediate revise, or undo route. Microphone denial produces no UI state at `RecordScreen.kt:65-73`, and unsaved typed capture has no draft recovery or leave warning. Preserve explicit confirmation while giving both outcomes a clear reversible next step and covering denial/interruption states.

### Persona Red Flags

- **Jordan, first-timer:** onboarding teaches the wrong product; provenance vocabulary requires translation; the empty Home competes with Ask AI before the core task is learned.
- **Sam, accessibility-dependent:** standard controls and bounds are good, but the conclusion field, asynchronous stage announcements, and focus restoration are incomplete; TalkBack, large text, and contrast remain runtime-unverified.
- **Casey, distracted mobile:** the FAB and restored submitted proposal are strong, but unsaved capture can be lost and the Review primary action sits after a long analytical scroll.

### Minor Observations

- The native Material 3 foundation is sound: Scaffold, TopAppBar, Material controls, IME padding, lifecycle-aware state, dynamic color, and dark-theme fallback.
- The reflection layout has no readable-width or window-size policy; tablet, multi-window, foldable, 200% font, and landscape behavior remain unverified.
- `FLAG_SECURE` correctly prevented screenshots; runtime evidence therefore used the API 35 accessibility tree for onboarding, empty Home, and Capture, while Review states were source/test assessed.

### Questions to Consider

- Should the first implementation pass focus on the Review authorship hierarchy, or widen immediately to onboarding and recovery copy?
- Should analytical detail remain visible by default, or collapse behind an explicit “How EchoMind structured this” disclosure?
- After Confirm or Reject, should the primary reversible next step be Revise, Continue examining, or Undo?
