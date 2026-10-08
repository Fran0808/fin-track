# FinTrack dashboard

The dashboard displays personal cashflow, transactions and Android pairing.
It uses React, TypeScript, Vite and Tailwind; exact versions live in
[package.json](package.json) and its lockfile.

## Setup and verification

Follow the [local development guide](../docs/development.md) for prerequisites,
backend configuration, startup commands and module checks. The development server
runs on port 5173 and proxies `/api` to `localhost:8080` through
[vite.config.ts](vite.config.ts). Keep the proxy and OAuth URLs aligned when changing ports.

## Searching movements by date

In Movements, use the visible date filter to choose the selected month, a specific
day, or an inclusive date range. Choose the dates and press `Buscar por fecha`.
Ranges can span multiple months. The final date includes the entire day.
The date filter combines with text, movement type and advanced filters; pagination
and CSV export retain the same applied range. Invalid or incomplete ranges cannot
be applied. `Limpiar filtros` or `Mes seleccionado` restores the selected month;
changing the global month also clears custom dates. Dashboard analytics continue
to use the globally selected month.

## Entry points

| Area | Source |
| --- | --- |
| Session lifecycle | [AuthContext](src/contexts/AuthContext.tsx) |
| HTTP requests | [API service](src/services/api.ts) |
| Response types | [Shared types](src/types/index.ts) |
| Views | [View components](src/components/views) |
| Financial formatting | [Formatters](src/utils/formatters.ts) |
| Tests | [UI tests](tests) |

Read [dashboard instructions](AGENTS.md) before changes. Use the
[cashflow rules](../docs/domain/cashflow.md) for financial interpretation and
[contracts](../docs/contracts.md) for changes involving backend responses.
