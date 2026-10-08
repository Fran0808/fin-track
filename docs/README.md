# Project documentation

Read the reference that matches the task. Runtime behavior and dependency versions
are defined by source and build files; update these documents when those change.

| Reference | Use it for |
| --- | --- |
| [Architecture](architecture.md) | Module boundaries, ingestion paths and compatibility identifiers. |
| [Development](development.md) | Native Windows setup, commands, verification and script limitations. |
| [Android notification recovery](android-notification-recovery.md) | Capture identity, account isolation, legacy review and device verification. |
| [Contracts](contracts.md) | Authentication, transaction payloads and changes affecting multiple modules. |
| [Cashflow rules](domain/cashflow.md) | Financial interpretation, internal transfers, currencies and deduplication. |
| [Cards and accounts](domain/financial-instruments.md) | Product lifecycle, assignment, suggestions, API and isolated schema verification. |
| [Docker guide](../docker/README.md) | Compose configuration, existing volumes and container operations. |

Agent instructions are maintained separately in [the root agreements](../AGENTS.md)
and the [backend](../api/AGENTS.md), [Android](../android/AGENTS.md) and
[dashboard](../ui/AGENTS.md) instruction files. They contain actionable rules;
this directory contains explanations and reference material.

## Documentation maintenance

- Keep commands next to their prerequisites and identify whether they access live data.
- Prefer links to DTOs, build files and CI over copied lists that can drift.
- Distinguish implemented behavior from planned improvements and known limitations.
- Store anonymized examples only; never include live credentials or private financial records.
- Add a runbook when a diagnosis becomes repeatable, and an ADR when a decision has alternatives and lasting consequences.
- Local skills live in `.agents/skills/`. The current `skills/` ignore rule also excludes this directory from Git. Skill adaptation, test isolation, script corrections and agent evals remain pending.
