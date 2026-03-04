# Repository Guidelines

## Scope and Ownership
- Raw input capture belongs to **DynamisWindow** (`window-api` / `window-test`).
- `DynamisInput` consumes window `InputEvent` data and maps it into semantic actions, axes, contexts, and per-tick `InputFrame` snapshots.
- Deterministic record/replay is non-negotiable for bug reproduction and CI.

## Core Constraints
- Keep processing tick-aligned and deterministic.
- No renderer dependencies.
- No math/vector framework dependencies or custom math types.
- Favor plain, explicit Java records/interfaces for API contracts.

## Module Layout
- `input-api`: IDs, bindings, `InputMap`, `InputFrame`, processor contracts.
- `input-core`: default resolver/processor plus deterministic recording/replay.
- `input-runtime`: runtime facade used by game loop integration.
- `input-test`: CI-safe fake window harnesses and integration tests.
- `docs/requirements.md`: authoritative feature and acceptance criteria.

## Build and Test
- Validate scaffold: `mvn validate`
- Run full checks: `mvn -e test`
- JDK is pinned in `.java-version` and Maven is configured for Java preview flags.

## Conventions
- Java style: 4-space indentation, no tabs.
- Package names: lowercase (`org.dynamisinput...`).
- Type names: `PascalCase`; methods/fields: `camelCase`.
- Commits should stay phase-scoped and keep tests green before each commit.
