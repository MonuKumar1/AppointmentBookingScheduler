# Prompt Provenance Log

This folder records the exact prompt used at each AIDLC phase transition, so the
AI-usage trail is auditable end to end: requirement → design → task → code → test.

## Convention

One file per phase transition, named `NN-phase-name.md`:

- `01-constitution.md`
- `02-specify.md`
- `03-plan.md`
- `04-tasks.md`
- `05-implement-<task-id>.md` (one per implementation task, or batched if tasks were run together)
- `06-test.md`

Each file contains:

```markdown
## Prompt
<exact text sent to the AI agent, including slash command invocation>

## Timestamp
<ISO 8601>

## Output artifact
<path to the file(s) this prompt produced or decisions it informed, e.g. specs/001-appointment-booking/spec.md>

## Response summary
<excerpt or short summary of what the AI produced>

## Human review note
<disposition: accepted / edited / rejected, what changed, and why>

## Verification
<how the output was checked independently of the AI: test run, manual trace against the brief/spec, etc.>
```

Spec Kit's own `/speckit-*` skills generate the documentation artifacts
(`spec.md`, `plan.md`, `tasks.md`, etc.) but do not log the prompts that
triggered them — this folder is the layer that closes that gap for
assessment/audit purposes.
