# OpenCode workspace integration

## Overview

RikkaHub treats a served OpenCode instance as a remote agent and workspace
runtime. The first slice adds OpenCode entries to the existing Workspace screen
and proves connection, project selection, remote session creation, prompting,
message retrieval, and abort without mirroring OpenCode state.

## Scope

The first slice includes:

- Mixed local and OpenCode entries in the existing Workspace screen.
- Native connection setup with name, server URL, credentials, and connection
  testing.
- Remote project/location selection using conservative persisted location data.
- OpenCode health, project, session, prompt, message, and abort operations.
- A remote session projection in RikkaHub that keeps OpenCode authoritative.
- Native-only credential storage that never enters browser-safe settings output.

The first slice excludes shell, PTY, SSE events, permissions UI, experimental
`/workspace` endpoints, and a shared local/remote workspace abstraction.

## Architecture

`WorkspaceEntity` remains the local/proot model. OpenCode uses separate models
and concrete UI row kinds while the Workspace screen unifies selection only.

```text
OpenCodeConnectionEntity
  id, name, baseUrl, authSecretRef

OpenCodeWorkspaceRefEntity
  id, connectionId, remoteDirectory, remoteProjectId?
```

`remoteDirectory` is the durable reconnect key. `remoteProjectId` is optional
metadata and is not required for identity unless the server provides a stable
value. No OpenCode project or session database is copied into Room.

The components remain conceptually separate even if the initial implementation
keeps orchestration compact:

- `OpenCodeConnectionStore` handles Room records and native credential
  references.
- `OpenCodeClient` handles HTTP requests and OpenCode response mapping.
- `OpenCodeRepository` orchestrates connection tests, project discovery,
  session lifecycle, prompting, message retrieval, and abort.

## OpenCode client contract

The client method names mirror the stable server API:

- `health()` maps `GET /global/health` and returns connectivity plus server
  version information.
- `listProjects()` maps `GET /project`.
- `createSession()` maps session creation.
- `listSessions()` and `getSession()` map session lookup operations.
- `getMessages()` maps remote message retrieval.
- `prompt()` maps `POST /session/:id/message` and waits for the assistant result.
- `abortSession()` maps the session abort operation.

The client normalizes base URLs, sends credentials only from native storage,
checks HTTP and protocol errors, and exposes typed results to the repository.
It does not contain UI or Room logic.

## Data flow

Connection setup validates the URL, stores the credential reference natively,
calls `health()`, and then discovers projects. Selecting a remote project
stores only the connection reference and conservative remote location data.

An OpenCode-backed interaction follows this flow:

```text
select remote workspace
  -> create remote session
  -> prompt remote session
  -> receive synchronous assistant result
  -> retrieve or project messages into the RikkaHub UI
```

OpenCode owns the remote transcript. RikkaHub stores references and any local
projection needed for navigation, not a second authoritative conversation.

## UI behavior

The Workspace screen groups entries by concrete kind while preserving one
selection surface:

- Local rows retain current behavior.
- OpenCode rows show a remote indicator, host name, selected location, and
  connection status.
- Add workspace offers local creation and Add OpenCode.
- Add OpenCode collects name, URL, and credentials, tests the connection, and
  optionally selects a remote project/location.
- Entry menus support editing and deletion without exposing credentials.
- Remote capability differences are represented explicitly rather than hidden
  behind local workspace semantics.

## Error handling and security

Connection failures show an offline or error state without deleting the saved
reference. Missing or invalid remote locations remain visible so the user can
repair them. Session and prompt failures remain associated with the remote
workspace and do not create a local transcript implicitly.

Credentials use the existing native secret-storage pattern. URL, display name,
status, and remote location can be projected to UI, but secret values must not
be included in settings APIs, logs, Room entities, or web responses.

## Verification

The implementation must provide:

- Unit coverage for client response mapping and protocol/error handling.
- Repository tests using a fake client and store.
- A device check that preserves installed app data and verifies the mixed
  Workspace screen.
- A live OpenCode check covering health, project discovery, session creation,
  prompt, and message retrieval against the configured PC server.

The implementation must not claim end-to-end completion until a real remote
OpenCode session returns an assistant response through the native client.

## Future extensions

After the first slice is real, add files/search, events, permissions, shell,
and PTY according to observed use. Consider shared capability interfaces only
after both local and OpenCode implementations expose proven common concepts.
