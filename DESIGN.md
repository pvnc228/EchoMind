---
version: alpha
name: EchoMind
description: Evidence-based design language for the EchoMind Android application.
---

## Overview

EchoMind is a private, text-first Android environment for turning a raw reflection into an inspectable proposal and, only after explicit user review, a confirmed conclusion. Its interface uses native Jetpack Compose and Material 3 in an Operate mode: the interaction must make the next reversible action clear while keeping authorship, provenance, and local processing visible.

## Colors

Use `MaterialTheme.colorScheme` roles for application surfaces and controls so dynamic color, the static fallback schemes, and dark theme remain coherent.

Within the reflection flow, use `surfaceVariant` for raw user material, `secondaryContainer` for unconfirmed local structure, and `primaryContainer` only for the user-confirmed conclusion. Color supports the distinction but never carries provenance or status without a text label.

## Typography

Use the Material typography roles owned by `EchoMindTheme`. Screen and outcome headings use headline roles, section headings use title roles, reflection content uses body roles, and provenance or status labels use label roles. Preserve system font scaling; do not introduce page-local fixed text sizes.

## Layout

Use `Scaffold` and Material top app bars for screen structure. The compact reflection flow follows one linear reading order: capture, local processing, source, proposal and alternative, editable user wording, then an explicit confirm or reject result.

Long reflection content must remain vertically scrollable, and text-entry surfaces must respect IME insets. Expanded layouts may constrain the reading measure or place evidence in a comparison layout, but must preserve the same source-to-proposal-to-conclusion order.

## Components

Use Material text fields for user-authored text, filled buttons for the single primary action at a stage, text buttons for reversible secondary actions, and cards for provenance-bearing content.

Every provenance card has both a human-readable ownership/status label and its content. Raw source, local proposal, user-edited wording, and confirmed conclusion remain distinct even when their wording is identical.

Loading, success, rejection, error, disabled, permission, and restored-draft states are part of the component contract. State changes must be exposed to accessibility services, and the field that becomes the confirmed conclusion must have its own programmatic label.

### Review surface (implemented)

The review surface reads as a dominant `source -> proposal -> my wording`
path rather than a flat stack. Detailed analysis (observations,
interpretations, assumptions, open questions) sits behind an explicit
"Show full analysis" disclosure, so the authorship decision stays primary.

The progressive disclosure toggle and the action dock are the only glass
layer: a static, bounded surface with an opaque tonal fallback on API 26-30.
Blur or compositing is not applied to scrolling content, and no callable
surface relies on glass alone to signal state. After confirm or reject, a
reversible "Start another reflection" action is available.

The review surface may offer exactly one focused follow-up question. The
question is labelled as user-authored and the resulting local response remains
an EchoMind proposal until the ordinary confirm or reject action.

Remote Q&A uses a separate consent preview. It shows purpose, exact provider
destination, exact minimized content, provider-retention warning, `Allow once`,
and `Cancel`. Confirmation of a conclusion never implies this consent.

## Do's and Don'ts

- Do describe analysis as a local, unconfirmed proposal until the user explicitly confirms or edits it.
- Do preserve the immutable raw source and the link from a conclusion to its source.
- Do keep confirmation of meaning separate from permission to transmit data.
- Do keep text capture primary and voice capture optional.
- Don't present AI wording as the user's belief or silently turn it into a confirmed conclusion.
- Don't invent missing context, hidden motives, diagnoses, certainty, or unsupported product capability.
- Don't add decorative complexity, engagement scoring, or graph views that obscure the reflection task.
