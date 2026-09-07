# OpenCode workspace integration implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` (recommended) or
> `superpowers:executing-plans` to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add OpenCode-backed remote workspaces to the existing Workspace
screen and prove the stable session lifecycle from Android to a served OpenCode
instance.

**Architecture:** Keep `WorkspaceEntity` and local/proot behavior unchanged.
Add separate OpenCode connection and remote-location records, a protocol-only
HTTP client, a persistence-only connection store, and an orchestration
repository. The UI presents local and remote entries together but preserves
their concrete capabilities.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Koin, OkHttp, kotlinx
serialization, coroutines, Android Keystore-backed secret storage, JUnit.

## Global Constraints

- Use stable OpenCode endpoints only: health, projects, sessions, messages,
  prompt, and abort.
- Do not use experimental `/workspace` endpoints.
- Do not add shell, PTY, SSE/events, permissions UI, or a shared workspace
  abstraction in this slice.
- `WorkspaceEntity` remains the local/proot model and existing local behavior
  must remain unchanged.
- OpenCode remains authoritative for remote sessions and transcripts.
- Persist remote location conservatively: canonical remote directory plus an
  optional upstream project ID, never the full OpenCode `Project` object.
- Credentials remain native-only and must not appear in Room entities, logs,
  settings APIs, or web responses.
- Preserve installed app data during device verification; use `adb install -r`.
- Do not claim end-to-end completion until a real remote OpenCode prompt returns
  an assistant response through the native client.

---

### Task 1: Define OpenCode domain records and database wiring

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/opencode/OpenCodeModels.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/data/db/entity/OpenCodeConnectionEntity.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/data/db/entity/OpenCodeWorkspaceRefEntity.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/data/db/dao/OpenCodeConnectionDAO.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/data/db/dao/OpenCodeWorkspaceRefDAO.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/opencode/OpenCodeModelsTest.kt`

**Interfaces:**
- Produce `OpenCodeConnectionEntity(id, name, baseUrl, authSecretRef,
  createdAt, updatedAt, lastHealthStatus, serverVersion)`.
- Produce `OpenCodeWorkspaceRefEntity(id, connectionId, name,
  remoteDirectory, remoteProjectId, createdAt, updatedAt)`.
- Produce DAO operations `listConnectionsFlow()`, `getConnection(id)`,
  `upsertConnection(entity)`, `deleteConnection(id)`,
  `listRefsFlow()`, `getRef(id)`, `upsertRef(entity)`, and `deleteRef(id)`.
- Produce `OpenCodeWorkspaceRow` data needed by the mixed Workspace screen;
  do not change `WorkspaceEntity`.

- [ ] **Step 1: Add failing model tests**

Test that a connection preserves its normalized display fields, a workspace
reference retains `remoteDirectory` when `remoteProjectId` is null, and no
model contains a plaintext credential field.

- [ ] **Step 2: Run the focused test**

Run: `./gradlew :app:testDebugUnitTest --tests '*OpenCodeModelsTest'`

Expected: FAIL because the new types do not exist.

- [ ] **Step 3: Add entities and DAOs**

Use Room entities with foreign-key cleanup from a connection to its references.
Store only the credential reference string, never the secret itself. Use
`Flow<List<...>>` for list operations and suspend methods for point lookups and
mutations.

- [ ] **Step 4: Register the schema changes**

Add the entities and DAOs to the existing database/module registration and use
the repository's next migration number. Add a migration test using the exact
production database chain and generated schema tables.

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*OpenCodeModelsTest'`

Expected: PASS. Commit with:
`git add app/src/main/java/me/rerere/rikkahub/data/opencode app/src/main/java/me/rerere/rikkahub/data/db app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt app/src/test && git commit -m "feat(opencode): add remote workspace records"`

### Task 2: Implement native credential storage and OpenCode HTTP client

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/opencode/OpenCodeSecretStore.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/data/opencode/OpenCodeClient.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/data/opencode/OpenCodeHttpClient.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/opencode/OpenCodeClientTest.kt`

**Interfaces:**
- `interface OpenCodeSecretStore { suspend fun put(id: String, value: String); suspend fun get(id: String): String?; suspend fun delete(id: String) }`
- `interface OpenCodeClient { suspend fun health(): OpenCodeHealth; suspend fun listProjects(): List<OpenCodeProject>; suspend fun createSession(directory: String?): OpenCodeSession; suspend fun listSessions(directory: String?): List<OpenCodeSession>; suspend fun getSession(sessionId: String, directory: String?): OpenCodeSession; suspend fun getMessages(sessionId: String, directory: String?): List<OpenCodeMessage>; suspend fun prompt(sessionId: String, directory: String?, text: String): OpenCodeMessage; suspend fun abortSession(sessionId: String, directory: String?): Boolean }`
- `OpenCodeHttpClient` implements the interface using OkHttp and kotlinx
  serialization; it owns URL joining, auth headers, JSON encoding, HTTP status
  checks, and response mapping.

- [ ] **Step 1: Write response-mapping tests**

Use a fake `Call.Factory` or the existing HTTP test pattern to test health
version mapping, project directory mapping, synchronous prompt response
mapping, structured API errors, and URL normalization for a base URL with and
without a trailing slash.

- [ ] **Step 2: Run the focused test and verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests '*OpenCodeClientTest'`

Expected: FAIL before the client and mappers exist.

- [ ] **Step 3: Implement the secret store**

Use the existing Android-native secure storage mechanism already used for
provider credentials. Store only an opaque `authSecretRef` in Room. Redact URL
authorization values and response bodies from exceptions and logs.

- [ ] **Step 4: Implement the stable client methods**

Map `health()` to `GET /global/health`, `listProjects()` to `GET /project`,
session methods to the stable session routes, `prompt()` to
`POST /session/:id/message`, and `abortSession()` to the stable abort route.
Pass the selected remote directory only where the OpenCode API accepts a
directory query or body field. Keep the method names exactly as declared above.

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*OpenCodeClientTest'`

Expected: PASS. Commit with:
`git add app/src/main/java/me/rerere/rikkahub/data/opencode app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt app/src/test && git commit -m "feat(opencode): add stable server client"`

### Task 3: Add connection store and orchestration repository

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/opencode/OpenCodeConnectionStore.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/data/opencode/OpenCodeRepository.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/opencode/OpenCodeRepositoryTest.kt`

**Interfaces:**
- `OpenCodeConnectionStore` provides persistence-only CRUD and secret-reference
  operations; it must not call the network.
- `OpenCodeRepository` provides `observeWorkspaceRows()`,
  `createConnection(name: String, baseUrl: String, credential: String?)`,
  `testConnection(connectionId: String): OpenCodeHealth`,
  `discoverProjects(connectionId: String): List<OpenCodeProject>`,
  `selectProject(connectionId: String, project: OpenCodeProject): OpenCodeWorkspaceRefEntity`,
  `createSession(refId: String): OpenCodeSession`,
  `prompt(refId: String, sessionId: String, text: String): OpenCodeMessage`,
  `getMessages(refId: String, sessionId: String): List<OpenCodeMessage>`, and
  `abortSession(refId: String, sessionId: String): Boolean`.

- [ ] **Step 1: Write repository tests with fakes**

Test that `createConnection` trims and normalizes the URL, stores a secret
reference instead of the credential, `testConnection` updates health status,
`selectProject` persists only directory and optional ID, and prompt failures do
not delete the saved reference.

- [ ] **Step 2: Run the focused test and verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests '*OpenCodeRepositoryTest'`

Expected: FAIL because the store and repository do not exist.

- [ ] **Step 3: Implement the persistence-only store**

Delegate directly to the DAOs and secret store. Keep Room entities out of the
HTTP client.

- [ ] **Step 4: Implement orchestration**

Resolve a connection and reference, load the secret only at request time, call
the client, and update health/status metadata. Return typed errors that retain
the remote reference. Do not copy OpenCode messages into the RikkaHub
conversation database.

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*OpenCodeRepositoryTest'`

Expected: PASS. Commit with:
`git add app/src/main/java/me/rerere/rikkahub/data/opencode app/src/test && git commit -m "feat(opencode): orchestrate remote sessions"`

### Task 4: Add mixed local and OpenCode Workspace UI

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspacePage.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspaceVM.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/OpenCodeConnectionDialog.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/Screen.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/me/rerere/rikkahub/ui/pages/extensions/workspace/WorkspaceVMTest.kt`

**Interfaces:**
- `WorkspaceVM` exposes one UI list containing concrete local and remote row
  types, plus `createOpenCodeConnection`, `testOpenCodeConnection`,
  `selectOpenCodeProject`, and `deleteOpenCodeEntry` actions.
- `OpenCodeConnectionDialog` accepts callbacks for dismiss, test, project
  selection, and save; it never receives a plaintext credential after save.

- [ ] **Step 1: Add ViewModel tests**

Test that local rows still navigate to `Screen.WorkspaceDetail`, remote rows
carry connection status and location, and failed remote tests leave the row
visible with an error state.

- [ ] **Step 2: Run the focused test and verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests '*WorkspaceVMTest'`

Expected: FAIL before the mixed row model exists.

- [ ] **Step 3: Add the remote connection flow**

Add an Add Workspace choice for local or OpenCode. Collect name, URL, and
credentials, test the connection, then show discovered project directories and
persist the selected location. Display connection status and server version
without displaying the secret.

- [ ] **Step 4: Render concrete row kinds**

Preserve current local cards and actions. Add a remote card with a remote icon,
host name, selected directory, status, and edit/delete menu. Route remote open
to a dedicated remote workspace detail entry point, not the local shell detail
screen.

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*WorkspaceVMTest'`

Expected: PASS. Commit with:
`git add app/src/main/java/me/rerere/rikkahub/ui app/src/main/java/me/rerere/rikkahub/Screen.kt app/src/main/java/me/rerere/rikkahub/di/AppModule.kt app/src/main/res/values/strings.xml app/src/test && git commit -m "feat(workspace): show OpenCode workspaces"`

### Task 5: Add remote session detail and prompt projection

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/OpenCodeWorkspacePage.kt`
- Create: `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/workspace/OpenCodeWorkspaceVM.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/Screen.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/me/rerere/rikkahub/ui/pages/extensions/workspace/OpenCodeWorkspaceVMTest.kt`

**Interfaces:**
- `OpenCodeWorkspaceVM` exposes selected remote location, session state,
  messages, `sendPrompt(text: String)`, `refreshMessages()`, and
  `abortSession()`.

- [ ] **Step 1: Write session projection tests**

Test the sequence create session, prompt, retrieve messages, and abort using a
fake repository. Assert that the UI displays the remote assistant response and
does not insert a RikkaHub-owned conversation record.

- [ ] **Step 2: Run the focused test and verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests '*OpenCodeWorkspaceVMTest'`

Expected: FAIL before the remote detail ViewModel exists.

- [ ] **Step 3: Implement the ViewModel**

Keep the remote session ID in ViewModel state for the active screen. Create a
session lazily on the first prompt, call synchronous `prompt()`, then refresh
messages from OpenCode. Surface typed errors and preserve the selected remote
reference.

- [ ] **Step 4: Implement the minimal Compose page**

Show remote host/location, session messages, prompt input, send, refresh, and
abort. Reuse existing message and loading components where they fit, but do
not route the page through local shell or local conversation persistence.

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*OpenCodeWorkspaceVMTest'`

Expected: PASS. Commit with:
`git add app/src/main/java/me/rerere/rikkahub/ui app/src/main/res/values/strings.xml app/src/test && git commit -m "feat(workspace): prompt OpenCode sessions"`

### Task 6: Verify the complete slice on device and against OpenCode

**Files:**
- Modify only if verification exposes a real defect in the files above.

- [ ] **Step 1: Run the complete Android test/build gate**

Run: `./gradlew :app:testDebugUnitTest assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Install without changing app identity or data**

Run: `adb -s 56290DLCH002PE install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`

Expected: streamed install succeeds; no uninstall, `pm clear`, database reset,
or application ID change occurs.

- [ ] **Step 3: Restore the OpenCode device tunnel**

Run: `adb -s 56290DLCH002PE forward tcp:18080 tcp:8080`

Expected: the configured RikkaHub MCP endpoint remains reachable at
`http://127.0.0.1:18080/mcp` and the OpenCode PC server remains reachable using
its configured URL.

- [ ] **Step 4: Exercise the live OpenCode lifecycle**

From the device UI, create an OpenCode connection, verify health/version,
select a project directory, create a remote session, send a prompt, and verify
the returned assistant message. Verify abort on a second prompt if the server
responds quickly enough to make it observable.

- [ ] **Step 5: Verify credential boundaries**

Inspect only redacted UI/log/settings outputs. Confirm the credential does not
appear in Room dumps, `/api/settings`, logcat, or the Workspace projection.

- [ ] **Step 6: Record verification and commit any defect fixes**

Run `git status --short` and `git diff --check`. Record tests, device checks,
unexecuted checks, and residual risks in the implementation report. Do not
claim completion if the live remote prompt did not return an assistant
response.

## Plan self-review

The plan covers the approved scope: stable client methods, conservative remote
location persistence, separate store/client/repository boundaries, mixed
Workspace UI, native-only credentials, synchronous prompt and message
projection, and device/live verification. It excludes the experimental
Workspace API, shell, PTY, events, permissions, and generalized capability
abstractions. All referenced files, interfaces, and test commands are named;
there are no placeholder steps.
