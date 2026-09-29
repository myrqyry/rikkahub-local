<!-- meristem-template:v1 -->
# Meristem Project State

This directory holds durable project context that should survive chats, agents, devices, and time.

## Authority map

- `PROJECT.md` — stable project purpose, architecture boundaries, invariants, and verification expectations.
- `DECISIONS.md` — consequential decisions whose rationale matters for future work.
- `LEARNINGS.md` — reusable project-local lessons supported by evidence.
- `ERRORS.md` — costly or recurring failures worth preserving so they are not rediscovered.
- `FEATURE_REQUESTS.md` — requested capabilities that are not yet implemented.

Current execution state does **not** belong here by default. Keep ordinary session detail in the active interaction. Create or update `../ACTIVE_WORK.md` only when ongoing work is multi-step, cross-session, cross-agent, or expensive to reconstruct.

## Evidence and authority

Current repository/runtime truth outranks stale notes. User decisions outrank inferred preferences. Recurrence nominates a lesson for review; it does not automatically grant authority to rewrite project instructions.

Do not store credentials, tokens, secrets, or unnecessary sensitive data here.
