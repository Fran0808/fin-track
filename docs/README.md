# Project documentation

Read the reference that matches the task. Runtime behavior and dependency versions
are defined by source and build files; update these documents when those change.

## Start by task

| Task | Start here |
| --- | --- |
| Resume work or determine what is pending | [Progress](PROGRESS.md), then Git status/diff |
| Understand boundaries or change a contract | [Architecture](architecture/overview.md), [contracts](architecture/contracts.md) |
| Change backend behavior or investigate Java tests | [API guide](api/README.md) |
| Change notification capture or diagnose delivery | [Android guide](android/README.md) |
| Change dashboard behavior | [UI guide](ui/README.md) |
| Run locally or verify changes | [Local development](development/local.md), [scripts](../scripts/README.md) |
| Maintain instructions or hand off agent work | [Instruction lifecycle](development/instruction-maintenance.md), [workflow](development/agent-workflow.md) |
| Evaluate a new agent session using only the repository | [Evaluation record and repeat procedure](development/agent-evaluation.md) |
| Deploy or prepare a database | [Deployment](development/deployment.md) |
| Interpret money or classification | [Cashflow](domain/cashflow.md), then the relevant domain reference below |

`architecture/` describes boundaries and contracts; `development/` describes
execution and deployment; `api/`, `android/` and `ui/` route module-specific work;
`domain/` defines business meaning. `PROGRESS.md` is the global checkpoint.
Link the owning document instead of copying rules into multiple guides.

| Reference | Use it for |
| --- | --- |
| [Project progress](PROGRESS.md) | Current priorities, task checkpoints, evidence and next actions. |
| [Script catalogue](../scripts/README.md) | Supported entry points, effects, prerequisites and regression checks. |
| [Architecture](architecture/overview.md) | Module boundaries, ingestion paths and compatibility identifiers. |
| [Development](development/local.md) | Native Windows setup, commands, verification and script limitations. |
| [Production deployment](development/deployment.md) | Required settings, HTTPS, schema preparation and deployment verification. |
| [Android notification recovery](android/notification-recovery.md) | Capture identity, account isolation, legacy review and device verification. |
| [Android capture progress](android/capture-progress.md) | Implementation status, decisions, verification evidence and remaining acceptance work. |
| [Contracts](architecture/contracts.md) | Authentication, transaction payloads and changes affecting multiple modules. |
| [Cashflow rules](domain/cashflow.md) | Financial interpretation, internal transfers, currencies and deduplication. |
| [Cards and accounts](domain/financial-instruments.md) | Product lifecycle, assignment, suggestions, API and isolated schema verification. |
| [Pending movements and rules](domain/classification.md) | Inbox criteria, automatic classification, category lifecycle and schema preparation. |
| [Docker guide](../docker/README.md) | Compose configuration, existing volumes and container operations. |

Agent instructions are maintained separately in [the root agreements](../AGENTS.md)
and the [backend](../api/AGENTS.md), [Android](../android/AGENTS.md) and
[dashboard](../ui/AGENTS.md) instruction files. They contain actionable rules;
this directory contains project tracking and project-specific reference material.

## Documentation maintenance

- Keep commands next to their prerequisites and identify whether they access live data.
- Prefer links to DTOs, build files and CI over copied lists that can drift.
- Distinguish implemented behavior from planned improvements and known limitations.
- Store anonymized examples only; never include live credentials or private financial records.
- Record project-impacting decisions and acceptance status in the relevant project record.
- For instruction changes and due audits, follow [instruction maintenance](development/instruction-maintenance.md); keep lifecycle metadata there rather than in entry files.
- Root and module `AGENTS.md` files are shared repository instructions. Track temporary tooling/skill limitations and acceptance work in [progress](PROGRESS.md).
