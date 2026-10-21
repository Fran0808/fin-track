# Backend guide

Read [backend instructions](../../api/AGENTS.md) before editing.

| Work | Reference |
| --- | --- |
| Package layout and service boundaries | [Architecture](../architecture/overview.md#backend-package-organization) |
| Authentication and endpoint/client changes | [Contracts](../architecture/contracts.md) |
| Money interpretation and deduplication | [Cashflow rules](../domain/cashflow.md) |
| Categories, pending movements and rules | [Classification](../domain/classification.md) |
| Cards, accounts and assignment | [Financial instruments](../domain/financial-instruments.md) |
| Tests and disposable databases | [Backend verification](../development/local.md#backend-verification) |
| Production startup and schema | [Deployment](../development/deployment.md) |

Source entry points: [controllers](../../api/src/main/java/com/store/api/controller),
[services](../../api/src/main/java/com/store/api/service),
[DTOs](../../api/src/main/java/com/store/api/model/dto), and
[tests](../../api/src/test/java/com/store/api). Build configuration is authoritative
in [pom.xml](../../api/pom.xml). For current work, read [progress](../PROGRESS.md).
