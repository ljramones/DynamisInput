# DynamisInput — Requirements (v1)

This document defines the requirements for **DynamisInput** as a first-class engine subsystem.

**Core philosophy:** input is a **deterministic simulation signal** (timestamped, ordered, tick-aligned), not a pile of callbacks.

DynamisInput **does not capture OS input directly**. Raw input capture belongs to **DynamisWindow**, which emits `InputEvent`s (and provides a CI-safe FakeWindow). DynamisInput owns **mapping + semantics + determinism + tooling hooks**.

---

## Scope & Ownership

### DynamisInput owns
- Semantic input mapping: **Actions**, **Axes**, **Contexts**
- Context stack resolution and consumption rules
- Action state transitions (pressed/released/held) and per-tick snapshots
- Modifiers/triggers framework (deadzone, curves, hold/tap, etc.)
- Deterministic recording and replay of raw input events
- CI-safe test harnesses driven by `window-test` FakeWindow

### DynamisInput does NOT own
- OS/window backends (GLFW/AWT/etc.) → **DynamisWindow**
- Rendering/UI widgets → **DynamisUI**
- ECS/gameplay logic → **systems consume InputFrame**
- Persistence format and save slots → **DynamisSession** (Input can provide serializable maps)

---

## Non-Goals (v1)
- Gamepad/haptics/gyro backend implementations (Window may add later)
- IME composition implementation (Window may provide text events later)
- Editor UI for rebinding (expose hooks; UI is separate)

---

## Terminology

- **InputEvent**: raw event from DynamisWindow (key, mouse button, mouse move, scroll, etc.)
- **Action**: semantic boolean-like control (e.g., Jump, Interact, Fire)
- **Axis**: semantic analog control (e.g., MoveX, MoveY, LookX, LookY)
- **Context**: mapping layer (Gameplay, Menu, Debug) with priority/consumption rules
- **InputFrame**: immutable per-tick snapshot produced by DynamisInput

---

## Must (v1 “AAA credible”)

### R1 — Deterministic, tick-aligned output
- DynamisInput produces exactly one **InputFrame per simulation tick**.
- Given the same InputEvent stream and tick schedule, InputFrames are identical.
- Input processing must not depend on wall-clock timing for correctness.

**Acceptance**
- Record → replay produces identical per-tick InputFrames for N ticks.

---

### R2 — Semantic Actions and Axes
- Game code reads **ActionId** / **AxisId**, never raw key codes.
- Actions support:
  - `pressed` (edge)
  - `released` (edge)
  - `down` (level)
- Axes support:
  - float value per tick (e.g., -1..1)

**Acceptance**
- WASD bindings can drive MoveX/MoveY.
- Mouse delta bindings can drive LookX/LookY.

---

### R3 — Context stack (layering + consumption)
- Support multiple mapping contexts with priority:
  - Top context is evaluated first.
  - Context can be **consuming** (blocks lower contexts) or **passthrough**.
- Example:
  - Menu context consumes “Confirm/Back” and prevents Gameplay “Fire/Jump”.

**Acceptance**
- With Menu pushed, Gameplay “Jump” does not fire even if the key is pressed.

---

### R4 — Rebindable, data-driven InputMaps
- Bindings live in a runtime `InputMap` structure per context.
- InputMap is serializable-friendly (data model is stable and explicit).
- No reflection-based binding discovery.

**Acceptance**
- InputMap can be programmatically modified at runtime.
- InputFrame responds to new bindings without restart.

---

### R5 — Deterministic recording + replay (raw stream)
- Recorder stores **raw InputEvent + tick** (not semantic actions).
- Replayer feeds the same raw stream into InputProcessor.
- This supports bug repro and CI golden tests.

**Acceptance**
- Recording a short session and replaying reproduces the same action edges and axes.

---

### R6 — CI-safe integration harness (FakeWindow)
- Include tests that drive input via DynamisWindow `window-test` FakeWindow.
- No native dependencies required for core tests.

**Acceptance**
- A test can enqueue raw input events into FakeWindow and assert InputFrames over ticks.

---

## Should (v1.1 “wow”)

### S1 — Triggers and Modifiers framework
Provide architecture for:
- Triggers: tap, hold, double-tap, chord
- Modifiers: deadzone, sensitivity curve, invert, smoothing

**Acceptance**
- Implement at least: Hold trigger + Deadzone modifier.

---

### S2 — Text input separation (API path)
- Text entry must be separate from key events.
- Define an API for committed text events even if Window doesn’t publish them yet.

**Acceptance**
- InputFrame exposes a per-tick text buffer (`List<String>` or `List<TextEvent>`).

---

### S3 — Device abstraction beyond keyboard/mouse (model only)
Even if Window v1 is KB/mouse only, input model should support:
- `DeviceId`
- `DeviceType`
- `ControlId` / `ControlType`

**Acceptance**
- Core types don’t assume keyboard-only semantics.

---

## Later (v2 “full AAA”)

### L1 — Hotplug + per-device profiles
- Device connect/disconnect events
- Per-device calibration (deadzones, curves, anti-deadzone)
- Per-user profile overrides

### L2 — Combos / sequences / buffering
- Chords
- Sequence inputs (fighting game style)
- Input buffering window for responsiveness

### L3 — Accessibility profiles
- Hold-to-toggle per action
- Sticky modifiers
- Reduced rapid input filters
- One-handed presets

### L4 — Input debugger overlay
- Built as a DynamisUI debug panel consuming Input diagnostics:
  - active contexts
  - resolved bindings for an action
  - last N raw events
  - “why didn’t Jump fire?” reasons

### L5 — Late-latch sampling knobs
- Explicit “late sample” for camera look
- Multi-rate sampling support if needed

---

## Performance Requirements

### P1 — Allocation-free hot path
- Feeding events and producing InputFrames should avoid per-event allocations.
- InputFrame should be immutable per tick.

### P2 — Predictable ordering
- Input events are processed in deterministic order within a tick.
- Context resolution order is explicit and stable.

---

## Integration Requirements

### I1 — WorldEngine tick integration
- WorldEngine should read `InputFrame` at the start of each tick.
- Tests should prove InputFrames drive deterministic tick behavior in headless mode.

### I2 — Session persistence (future)
- InputMap/bindings should be serializable so Session can save user rebinds.
- Session format ownership stays in DynamisSession; Input provides data model.

---

## Acceptance Checklist (v1)
- [ ] InputFrame per tick with pressed/released/down and axes
- [ ] Context stack with consumption rules
- [ ] Rebindable InputMaps (data-driven model)
- [ ] Recorder + replayer with deterministic match
- [ ] FakeWindow-driven tests proving end-to-end behavior
