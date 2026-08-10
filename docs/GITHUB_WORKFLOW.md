# GitHub workflow EchoMind

**Status:** current operational workflow
**Last updated:** 2026-08-10

This document describes the current GitHub Project and issue workflow for
EchoMind. It is project-management metadata; a Project status or an issue
checkbox is not evidence that a product milestone is complete. Product status
comes from the current source, fresh runtime evidence, and the corresponding
entries in `JOURNAL.md` and `ROADMAP.md`.

## Project

- Repository: [pvnc228/EchoMind](https://github.com/pvnc228/EchoMind)
- Project: [EchoMind Work](https://github.com/users/pvnc228/projects/1)
- Board view: `Board`
- Table view: `Backlog`
- Table filter: `-status:done`
- Table sort: `Priority` ascending, then `Status` ascending

The Board status order is:

```text
Backlog → Ready → In Progress → Blocked → Verify → Done
```

The project uses only the following fields for issue triage:

| Field | Options |
|---|---|
| `Type` | `feature`, `bug`, `verification`, `decision` |
| `Priority` | `P0`, `P1`, `P2`, `P3` |
| `Milestone` | repository milestones `M0` through `M7` |

`Milestone` is GitHub's built-in repository milestone field. The `M0`–`M7`
options therefore live in the repository, not in a duplicate Project field.

## Current board contents

The board is populated from the still-open work in `ROADMAP.md` and the
deferred boundaries recorded in `JOURNAL.md`. The current live mapping is:

| Issues | Status | Type | Priority | Milestone | Scope |
|---|---|---|---|---|---|
| [#1](https://github.com/pvnc228/EchoMind/issues/1) | `Done` | `verification` | `P1` | `M4` | Scoped repair gate closure |
| [#6](https://github.com/pvnc228/EchoMind/issues/6) | `Ready` | `feature` | `P2` | `M4` | Optional local follow-up/reminder |
| [#2](https://github.com/pvnc228/EchoMind/issues/2) | `Backlog` | `feature` | `P0` | `M0` | Minimized context preview and per-request consent |
| [#3](https://github.com/pvnc228/EchoMind/issues/3) | `Backlog` | `feature` | `P0` | `M1` | Bounded continue-discussion flow |
| [#4](https://github.com/pvnc228/EchoMind/issues/4) | `In Progress` | `verification` | `P1` | `M2` | Import integrity and selective restore |
| [#5](https://github.com/pvnc228/EchoMind/issues/5) | `Backlog` | `verification` | `P1` | `M3` | Repeat-use evidence for Home resurfacing |
| [#7](https://github.com/pvnc228/EchoMind/issues/7) | `Backlog` | `feature` | `P2` | `M5` | Explainable guidance on explicit request |
| [#8](https://github.com/pvnc228/EchoMind/issues/8) | `Backlog` | `feature` | `P3` | `M6` | Voice capture through the text review flow |
| [#9](https://github.com/pvnc228/EchoMind/issues/9) | `Backlog` | `verification` | `P3` | `M7` | Private-beta and release evidence |

`Blocked` and `Verify` are intentionally empty. Issue #4 is active in
`In Progress`; it is not yet in `Verify` because the full completion gate and
fresh cross-API evidence are still pending.

Issue #1 is a tracking card for the closed scoped repair gate. Its closure
does not close M4 or the broader M2/M3/M4 product milestones. Issue #6 is the
next agreed product slice; the other cards remain documented backlog work.

## Working cycle

1. Read the issue, `AGENTS.md`, the governing product/data documents, and the
   relevant decision record.
2. Keep one issue focused on one verifiable result.
3. Create a branch from the issue and keep production changes linked to it:

   ```powershell
   gh issue view 6 --repo pvnc228/EchoMind --comments
   gh issue develop 6 --repo pvnc228/EchoMind --name issue-6-m4-follow-up --checkout
   ```

4. Open a pull request with `Closes #N` only when merging that pull request
   should close the issue:

   ```powershell
   gh pr create `
     --base master `
     --title "Verify repair gate" `
     --body "Closes #1

   Validation:
   - unit tests
   - connected tests
   - lint
   - diff check
   - JOURNAL.md updated"
   ```

5. Move the card to `Blocked` with the concrete reason when progress stops.
   Move it to `Verify` when implementation is complete but evidence is still
   being checked. Use `Done` only after the required evidence and journal
   entry exist; merge then closes the linked issue when the PR contains
   `Closes #N`.

## Evidence and privacy boundaries

- Project fields and statuses are planning metadata, not runtime evidence.
- Do not mark a product milestone complete from a green card alone.
- Keep fresh test, device, accessibility, performance, restart, and export
  evidence in the repository journal and current roadmap status.
- Never put raw reflections, raw audio, API keys, credentials, or personal
  export contents into GitHub issues, pull requests, or Project fields.
- GitHub issue text must remain limited to implementation context, criteria,
  links to repository documents, and reproducible evidence references.

## Canonical documents

- Product intent: `VISION.md` and `PRODUCT.md`
- Data/privacy rules: `DATA_CONTRACT.md`
- Current product status: `ROADMAP.md`
- Evidence and historical implementation record: `JOURNAL.md`
- Architectural decisions: the relevant record under `docs/`

Historical journal entries and superseded roadmap snapshots remain historical;
they are not rewritten to make an old status look current.
