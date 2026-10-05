<!-- meristem-template:v1 -->
# Decisions

Record decisions only when the choice or its rationale is likely to matter later.

Use entries like:

## YYYY-MM-DD — Decision

**Decision:**  
**Why:**  
**Alternatives considered:**  
**Constraints / consequences:**  
**Evidence / references:**  
**Supersedes:** none

## 2026-09-20 — Local System One is the primary fuzzy decision layer

**Decision:**  
Use the reasoning ladder `deterministic -> local System One -> explicitly approved remote System One -> generative`. System-One work is limited to typed boolean, choice, and score judgments. Generative models remain responsible for synthesis.

**Why:**  
RikkaHub already prefers deterministic fast paths and typed agent plans. A small local decision layer fills the gap between exact code and expensive generative reasoning while keeping frequent routing/relevance/rule judgments private and cheap.

**Alternatives considered:**  
Use a full LLM for every fuzzy decision; center hosted Jev as the decision service; make the decision model an authority layer.

**Constraints / consequences:**  
Decision models never grant capabilities or execute tools. Local is primary. Remote decision providers require explicit authorization/data-boundary approval. Confidence is evidence, not authority. The typed contract/routing policy is implemented first; a production local model adapter must be evaluated before it is described as live.

**Evidence / references:**  
`local-llm/src/main/java/me/rerere/locallm/decision/SystemOneDecision.kt`; `docs/references/architecture.md`; shared Meristem skill `system-one-decisions`.

**Supersedes:** none

## 2026-10-05 — Local model selection uses measured execution profiles, not fixed device tiers

**Decision:**
Route Android local-inference selection through a measured `DeviceExecutionProfile` and pure `LiteRtExecutionPlanner`. Live available memory is the hard admission signal; catalog minimum-RAM bands are advisory ranking evidence. Persisted accelerator choices are revalidated against current capabilities before reuse, while native runtime fallback remains the final execution truth.

**Why:**
RikkaHub already had the right ingredients (live `availMem`, accelerator probes, memory admission, model metadata, runtime fallback) but they were independent decisions. A single profile/planner boundary prevents contradictory model/backend choices and gives future quantized variants one place to plug in without copying hardcoded device tiers.

**Alternatives considered:**
Copy Brownie's 6/8/12 GB tiers; select only from total RAM; trust cached accelerator labels indefinitely; implement advertised Q4/Q8 switching without verified matching artifacts.

**Constraints / consequences:**
The planner is evidence/policy, not capability authority. Runtime fallback is reported as degraded execution, not proof the preferred backend worked. LiteRT-LM backend labels must map to real SDK backends; 0.17.0 has no NNAPI backend, so NNAPI remains available only to compute/task adapters that truly support it. Do not claim adaptive quantization until verified Q4/Q8 artifacts exist.

**Evidence / references:**
`local-llm/.../litert/compute/DeviceExecutionProfile.kt`; `local-llm/.../litert/LiteRtExecutionPlanner.kt`; `ExecutionProfileProbe.kt`; `LiteRtRuntime.ensureLoaded`; `docs/references/architecture.md`.

**Supersedes:** none
