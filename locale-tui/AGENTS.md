# Locale TUI — Localization Tooling

## What Lives Here

Python-based TUI for managing and translating Android string resources across locales. Not a Gradle module — standalone tool.

## Key Files

| File | Purpose |
|------|---------|
| `src/main.py` | Entry point |
| `src/` | TUI screens, translation logic |
| `pyproject.toml` | Python deps, scripts |
| `config.yml` | Locale config |

## Deviations from Root

- **Language:** Python, not Kotlin
- **Runtime:** Requires Python 3.12+
- **Package manager:** `uv`, not Gradle
- **Test runner:** `pytest`, not JUnit

## Commands

```bash
uv sync          # install deps
uv run pytest    # run tests
uv run locale-tui  # launch TUI
```