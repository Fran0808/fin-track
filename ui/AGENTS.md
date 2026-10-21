# Dashboard instructions

These instructions apply to dashboard changes and supplement [the root agreements](../AGENTS.md).
Their sources and review triggers are in the [instruction register](../docs/development/instruction-maintenance.md#instruction-register).

## Boundaries

- React, TypeScript, Vite and Tailwind are the established stack. Resolve actual versions from `package.json` and its lockfile.
- Keep HTTP calls in `src/services/api.ts` and shared response types in `src/types/index.ts`.
- Keep session lifecycle in `AuthContext`; preserve the protection against delayed 401 responses invalidating a newer session.
- The development proxy forwards `/api` to the backend. Review both proxy and deployment setup before changing API addresses.
- Follow [cashflow semantics](../docs/domain/cashflow.md): show period expenses, incoming money and net flow; do not label them bank balances.
- Update types and tests alongside backend contract changes; see [contracts](../docs/architecture/contracts.md).

## Design and interaction

- Preserve the existing product identity and component conventions when making focused changes.
- Keep currency figures aligned with tabular numerals and use the shared formatters.
- Use semantic HTML, accessible names, visible keyboard focus and sufficient contrast.
- Use native accessible dialogs for overlays and preserve keyboard dismissal and focus behavior.
- Support small screens, reduced motion, loading, empty and error states where the changed interaction requires them.
- Write clear Spanish user-facing copy; keep internal identifiers in English.

## Verification

- From `ui`, run `npm test`, `npm run lint` and `npm run build` for application changes.
- Test meaningful behavior changes, especially authentication, filters, financial formatting and dialogs.
- Inspect the changed UI at relevant screen sizes when the task changes layout or interaction, and report any visual checks that could not be performed.
- [ui/README.md](README.md) lists entry points; [development](../docs/development/local.md) covers setup and known verification gaps.
