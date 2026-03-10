# DynamisInput Architecture Review

Date: 2026-03-10  
Scope: Deep boundary ratification for `DynamisInput` (review/documentation only)

## 1. Repo Overview

Observed modules:

- `input-api`
- `input-core`
- `input-runtime`
- `input-test`

Observed implementation shape:

- `input-api` defines input IDs, binding contracts, input map/context model, processor contract, and immutable per-tick `InputFrame`.
- `input-core` implements deterministic event-to-frame processing (`DefaultInputProcessor`) and deterministic recording/replay (`InputRecorder`, `InputRecording`, `InputReplayer`).
- `input-runtime` provides runtime facade/builder (`InputRuntime`) for map/context initialization and frame access.
- `input-test` provides CI-safe FakeWindow integration harness (`FakeWindowInputLoopHarness`) via `window-test`.

Dependency signals from poms/docs/code:

- Input captures raw events from `DynamisWindow` contracts (`window-api`, `window-test`) and does not implement OS capture itself.
- Main modules depend on `DynamisCore` and `DynamisWindow` APIs.
- No direct dependencies on `DynamisUI`, `DynamisWorldEngine`, `DynamisScripting`, `DynamisSession`, or `DynamisContent`.
- `docs/requirements.md` and `AGENTS.md` explicitly state ownership boundaries aligned with input mapping/determinism and offload raw capture to Window.

## 2. Strict Ownership Statement

### What DynamisInput should own

- Raw-event consumption boundary from Window layer.
- Normalized input semantics (actions, axes, contexts, bindings).
- Deterministic tick-aligned input frame synthesis.
- Context stack resolution and consumption rules.
- Deterministic record/replay of raw input event streams for debugging/CI.

### What is appropriate for an input subsystem

- Translating raw key/mouse events into semantic action/axis outputs.
- Input-map and context management APIs.
- Input runtime facade for game-loop integration.
- Input test harnesses that validate deterministic behavior against fake window event sources.

### What DynamisInput must never own

- UI presentation/runtime widget policy.
- World/gameplay authority or orchestration policy.
- Scripting control policy.
- Session/profile persistence ownership (it may expose serializable map models only).
- Render-planning or GPU authority.

## 3. Dependency Rules

### Allowed dependencies for DynamisInput

- `DynamisCore` for common base concerns.
- `DynamisWindow` (`window-api`/`window-test`) for raw input event ingestion.

### Forbidden dependencies for DynamisInput

- Direct dependencies on UI presentation implementation policy (`DynamisUI` internals).
- Direct dependencies on world/scripting/session/content authorities.
- Render/GPU planning/execution dependencies.

### Who may depend on DynamisInput

- `DynamisUI` for input-to-UI event translation layers (adapter boundary).
- `DynamisWorldEngine` or gameplay systems for action/axis frame consumption.
- `DynamisScripting` only via narrow input bindings/actions, not raw-device policy.

### Boundary requirements

- Raw input authority ends at Window capture; DynamisInput starts at normalized interpretation.
- UI event translation should be an adapter boundary above Input (not input owning UI behavior).
- World/gameplay/scripting should consume `InputFrame` outputs, not embed input mapping policy in their layers.

## 4. Public vs Internal Boundary

### Canonical public surface (recommended)

- `input-api` contracts:
  - IDs (`ActionId`, `AxisId`, `ContextId`)
  - mapping model (`InputMap`, binding records)
  - processor contract (`InputProcessor`)
  - frame model (`InputFrame`)

- `input-runtime.InputRuntime` as integration facade.

### Internal/implementation surface (should remain internal)

- `input-core.DefaultInputProcessor` processing details.
- recording/replay implementation classes in `input-core.recording`.
- test harness classes in `input-test`.

### Boundary concern

- No JPMS module descriptors are present; package encapsulation is conventional rather than enforced.
- `input-runtime` directly defaults to `DefaultInputProcessor`, which is practical now but keeps concrete implementation visible to downstream modules.

## 5. Policy Leakage / Overlap Findings

## Major clean boundaries confirmed

- Clear and explicit split with `DynamisWindow`: Input consumes events, does not capture OS input.
- Deterministic processing and replay are implemented directly and aligned with requirements.
- No direct ownership drift into UI/world/scripting/session/content subsystems in current code.
- API guard test prevents identity/math duplication drift in input API.

## Policy leakage / overlap identified

- **DynamisUI boundary is implied, not formalized:** no explicit adapter contract for raw `InputFrame` -> UI event translation in this repo; this should remain above Input.
- **DynamisWorldEngine / gameplay boundary is contract-light:** requirement mentions world tick consumption, but integration contract is documented more than codified.
- **Session overlap risk (future, low):** requirements mention serializable-friendly maps for rebinding; ensure persistence format ownership remains in Session.
- **DynamisWindow coupling risk (watch):** input-api presently depends on concrete Window event shape (`InputEvent`) which is acceptable but should remain one-way and minimal.

## 6. Ratification Result

**Judgment: ratified with constraints**

Why:

- The repo strongly matches input-device/input-mapping authority with deterministic semantics.
- It currently avoids drifting into UI/world/scripting/session/render authority.
- Constraints are needed to keep translation/orchestration layers above Input and prevent future policy absorption.

## 7. Recommended Next Step

1. Preserve the current boundary: Window captures, Input normalizes, higher layers consume.
2. In subsequent boundary reviews, explicitly ratify:
   - Input → UI event translation adapter seam.
   - Input → WorldEngine control consumption seam.
3. Next repo to review: **DynamisWindow** (to lock the upstream half of the input boundary and prevent bidirectional drift).

---

This document is a boundary-ratification review artifact. It does not perform refactors in this pass.
