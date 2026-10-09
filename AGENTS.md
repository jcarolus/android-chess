# AGENTS.md

## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues on jcarolus/android-chess, using the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default label names: needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Coding standards

### Unit tests

- **Write unit tests for** pure logic such as PGN parsing, and for the local engine C++ code.
- **Do not write unit tests for** Activities, dialogs and layout.
- **Online code (Lichess, FICS):** do not add fake `LichessApi` seams or other test doubles to make it testable.

### Comments

- Comments describe what the code does and why, so they stay true as long as the code does.
- Do not write comments about the issue, task or change at hand (e.g. "fix for #123", "added for the challenge flow"). That belongs in the commit message or the issue.
