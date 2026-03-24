package org.dynamisengine.input.api.gesture;

import java.util.Map;
import java.util.Set;

/**
 * Per-tick gesture evaluation result.
 *
 * Produced by the GestureRecognizer after evaluating all registered gestures
 * against the current InputFrame. Immutable, deterministic, replay-safe.
 *
 * @param tick           the tick this result corresponds to
 * @param firedGestures  gestures that activated this tick (edge — fires once per activation)
 * @param activeHolds    gestures of type Hold that are currently held past threshold (sustained state)
 * @param sequenceProgress  for active sequences: gesture ID → current step index (0-based).
 *                          Empty for completed or inactive sequences.
 */
public record GestureFrame(
        long tick,
        Set<GestureId> firedGestures,
        Set<GestureId> activeHolds,
        Map<GestureId, Integer> sequenceProgress) {

    public GestureFrame {
        firedGestures = Set.copyOf(firedGestures);
        activeHolds = Set.copyOf(activeHolds);
        sequenceProgress = Map.copyOf(sequenceProgress);
    }

    /** True if the given gesture fired (activated) this tick. */
    public boolean fired(GestureId id) {
        return firedGestures.contains(id);
    }

    /** True if the given hold gesture is currently active (past threshold, still held). */
    public boolean holding(GestureId id) {
        return activeHolds.contains(id);
    }

    /** Current step index for a sequence gesture, or -1 if not in progress. */
    public int sequenceStep(GestureId id) {
        return sequenceProgress.getOrDefault(id, -1);
    }

    /** True if any gesture fired this tick. */
    public boolean anyFired() {
        return !firedGestures.isEmpty();
    }
}
