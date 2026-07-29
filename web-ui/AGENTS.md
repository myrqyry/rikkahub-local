# Web UI — React Web Interface

## What Lives Here

React web UI companion for the app — built with React Router 7, TypeScript, Tailwind CSS v4, and pnpm. Not a Gradle module.

## Key Files

| File | Purpose |
|------|---------|
| `app/` | React components, pages, routes |
| `app/routes.ts` | Route definitions |
| `app/components/` | Shared UI components |
| `app/stores/` | Zustand stores |
| `package.json` | NPM deps and scripts |
| `tsconfig.json` | TypeScript config |

## Deviations from Root

- **Language:** TypeScript/React, not Kotlin
- **Package manager:** `pnpm`, not Gradle
- **Lint:** `oxlint` (not Android lint), run `pnpm run lint`
- **Typecheck:** `pnpm run typecheck`
- **Format:** `pnpm run fmt`

## Commands

```bash
pnpm install        # install deps
pnpm run dev        # dev server
pnpm run build      # production build
pnpm run typecheck  # type checking
pnpm run lint       # lint
pnpm run fmt        # format
```

## Watch Out For

- `react-router` routes in `app/routes.ts` — route changes affect the entire app
- Tailwind v4 with `@tailwindcss/vite` — not the classic PostCSS setup