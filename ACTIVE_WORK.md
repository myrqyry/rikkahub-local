<!-- meristem-template:v1 -->
# Active Work

## Outcome

Complete and verify the current MCP/device-tool and constrained-routing work without
disturbing existing Android state or unrelated provider/model behavior.

## Why it matters

The current working tree contains a cross-cutting implementation spanning the
stateless MCP HTTP path, device MCP tools, web settings/server wiring, local-LLM
decision routing, related tests/docs, and supporting local agent skills. These
changes need coherent verification before they are treated as complete.

## Current state

**Branch context:** `HEAD` is `master` at `1b025681`, equal to `origin/master`; no
branch-only commits exist.

**Current working tree:** there are no staged changes. Unstaged edits include
preferences, dependency wiring, web settings/server and MCP adapter/route code,
strings, tests, and the architecture reference. Untracked work includes device MCP
tools/tests/docs, local-LLM decision code/tests, supporting `.agents/skills/` files,
and `opencode.jsonc`. The Meristem substrate is now repaired with a private local
state lane.

**Verification state:** no validation has been run against this current dirty tree
as part of substrate initialization. The ignored `SESSION-STATE.md` contains older
Models-page decisions and is not evidence that the current MCP/routing work is
complete.

## Locked decisions

- Preserve installed app data, persistent storage, application identity, and
  existing model/provider lifecycle behavior.
- Keep MCP authorization, route handling, and device-tool boundaries explicit;
  do not create silent success or unsafe fallback paths.
- Keep the current uncommitted implementation distinguishable from already-landed
  branch history until tests/builds verify it.

## Constraints / do-not-regress

- Follow root and nearest-module `AGENTS.md` rules, especially persistent-data
  preservation and verification reporting.

## Current objective

1. Inspect and finish the current stateless MCP/device-tool and routing changes.
2. Run the narrowest relevant tests, then broader build/type validation as practical.
3. Verify the real runtime path and report unverified device behavior or remaining
   integration gaps before claiming completion.

## Verification

Verification is incomplete. The initializer and doctor have been run, but no build,
unit test, lint, or device check has been run against the current dirty tree.

## Open uncertainty

The intended final scope, test/device availability, and whether all untracked
supporting files belong in the eventual change remain to be confirmed by the next
implementation/verification pass.
