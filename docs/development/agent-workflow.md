# Agent workflow

Applies when implementing or handing off repository work. Read only the sections
needed for the task. Authority and financial/security constraints remain in
[AGENTS.md](../../AGENTS.md); instruction sources and review conditions are in
[maintenance](instruction-maintenance.md).

## Communication and scope

Explain the intended change and rationale before editing. Implement authorized
changes directly, verify them and explain important behavior decisions. Provide
line-by-line teaching only when requested. Ask for essential missing information
or authorization outside the requested scope; preserve unrelated work.

## Code style and commit proposals

- Use English for identifiers, comments and log messages; user-facing copy may be Spanish.
- Avoid emojis in source, comments, logs, documentation and commits unless explicitly requested as UI content.
- Propose small functional commit groups containing the relevant implementation,
  regression coverage and documentation; explain staging dependencies.
- Provide explicit `git add <paths>` and `git commit -m "<type>(<scope>): <subject>"`
  commands from the repository root. The user performs commits and pushes.
- Use Conventional Commits: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`,
  `style` or `ci`.

## Checkpoints and concurrency

At task start, inspect Git and [current progress](../PROGRESS.md), then read the
affected module's instructions. Record owner/scope when work is concurrent.
Separate overlapping changes into worktrees and explicitly isolate databases and
devices too; a worktree does not isolate those resources.

At a meaningful checkpoint, before interruption, or on completion, update the
current work item with objective, owner/scope, status, changed paths, actual checks
and results, limitations, blockers and the next executable action. Use statuses
such as `in progress`, `blocked`, `implemented; verification pending` or
`verified for stated scope`.

Keep the global index brief and link detailed area evidence. Archive a superseded
checkpoint only when its evidence is useful; do not grow an endless global log.
Temporary tool/skill limitations belong in progress, not module instructions.
Never put credentials or identifiable financial data in a checkpoint.

Only mark verified what was checked against the relevant revision; later changes
invalidate affected earlier checks. Identify interrupted and partially checked
work explicitly. Do not discard unrelated changes to create a clean checkpoint.

Uncommitted checkpoints survive locally, but another clone receives them only
after the user commits and transfers the changes. These practices support ACID
goals for agent work; filesystem edits are not atomic transactions and no rollback
guarantee follows from them.

## Verification and maintenance

Select checks through [local development](local.md) and the
[script catalogue](../../scripts/README.md); CI is defined in
[the workflow](../../.github/workflows/ci.yml). For documentation-only changes,
check links, consistency and the diff without full application builds.
When instructions change or a review becomes due, use
[instruction maintenance](instruction-maintenance.md) rather than append another
global rule.
