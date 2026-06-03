# README_AGENT.md

Fallback instruction file for OpenCode or any other coding agent.

## Non-Negotiable Rules

- Nova / Luna only. Do not mix this project with Jarvis.
- Nova is the male assistant and Luna is the female assistant.
- Keep the project phone-first and eventually smartwatch-supported.
- Keep everything local/on-device as much as possible with zero backend cost by default.
- Do not add paid services, hardcoded secrets, or cloud dependencies unless explicitly requested.
- Keep Flutter frontend/backend-free unless a future backend is explicitly approved.
- Prefer free/open-source tools.
- Keep folder structure clean, add tests for behavior changes, and maintain docs in `docs/`.
- No fake code and no placeholder-only architecture unless clearly marked as `TODO`.

## Contractor Workflow

- The owner gives high-level tasks to Codex.
- Codex inspects, plans, and decides whether helpers are needed.
- Gemini is for review and research only.
- OpenCode is for local edits, file creation, small bugs, and repetitive refactors.
- Cursor is optional visual IDE and rules support.
- Codex makes the final implementation decisions and reports validation plus the next step.

## Agent Behavior

- Make small, safe, local edits.
- Preserve useful existing content.
- Avoid unrelated changes.
- Update docs when architecture or workflow changes.
