# SESSION-STATE.md — Active Working Memory

**WAL Target** — Write to this file before responding when corrections, decisions, or key details appear.

---

## Session: 2026-07-28 — Initial Setup

**Current Task:** Setting up proactive agent architecture (AGENTS.md, SESSION-STATE.md, SOUL.md, USER.md, MEMORY.md, working-buffer.md)

### Key Decisions
- Using Hal Stack 🦞 proactive agent framework
- Using WAL Protocol for state persistence
- Working Buffer at danger zone (>60% context)

### Active Files
- `/home/myrqyry/MQR/rikkahub-local/AGENTS.md` — operating rules
- `/home/myrqyry/MQR/rikkahub-local/SESSION-STATE.md` — this file
- `/home/myrqyry/MQR/rikkahub-local/SOUL.md` — identity
- `/home/myrqyry/MQR/rikkahub-local/USER.md` — human context
- `/home/myrqyry/MQR/rikkahub-local/MEMORY.md` — long-term memory
- `/home/myrqyry/MQR/rikkahub-local/memory/working-buffer.md` — danger zone log

### Corrections Log
- 2026-07-28: Proactive agent files must be gitignored (added to .gitignore)
- 2026-07-28: User wants agent files TRACKED in git. Removed from .gitignore. User likes nested AGENTS.md files.

### Preferences
- (to be discovered)

### Decisions
- Use proactive agent architecture with WAL, Working Buffer, and Heartbeat
- Base files stored in project root
- Created modular AGENTS.md system: root + 14 module-specific files
- Agent files tracked in git (not gitignored)

### Open Questions
- What kind of work does the user primarily do on this project?
- What are their preferred patterns (e.g., test-first, documentation-first)?