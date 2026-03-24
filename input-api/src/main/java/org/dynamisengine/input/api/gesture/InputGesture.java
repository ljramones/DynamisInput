package org.dynamisengine.input.api.gesture;

import org.dynamisengine.input.api.ActionId;

import java.util.List;

/**
 * Describes an input pattern to recognize above the raw action/axis layer.
 *
 * Gestures are evaluated per-tick against the resolved {@link org.dynamisengine.input.api.frame.InputFrame}.
 * All timing is expressed in ticks (not wall-clock) for deterministic replay.
 *
 * Sealed hierarchy covers the standard game input grammar:
 *   Tap, Hold, DoubleTap, Chord, Sequence
 */
public sealed interface InputGesture {

    /**
     * Tap: action pressed and released within a window.
     *
     * Fires on the release tick if the total down-duration was <= maxTicks.
     * Does NOT fire on press — you must wait for the release to distinguish tap from hold.
     *
     * @param action    the action to monitor
     * @param maxTicks  maximum down-duration to qualify as a tap (e.g., 10 ticks = ~167ms at 60fps)
     */
    record Tap(ActionId action, int maxTicks) implements InputGesture {
        public Tap {
            if (action == null) throw new IllegalArgumentException("action must not be null");
            if (maxTicks < 1) throw new IllegalArgumentException("maxTicks must be >= 1");
        }
    }

    /**
     * Hold: action held down for at least minTicks.
     *
     * Fires once when the hold threshold is reached. Does not re-fire while held.
     * Resets when the action is released.
     *
     * @param action    the action to monitor
     * @param minTicks  minimum continuous down-duration to qualify as a hold (e.g., 30 ticks = 500ms at 60fps)
     */
    record Hold(ActionId action, int minTicks) implements InputGesture {
        public Hold {
            if (action == null) throw new IllegalArgumentException("action must not be null");
            if (minTicks < 1) throw new IllegalArgumentException("minTicks must be >= 1");
        }
    }

    /**
     * DoubleTap: two taps within a window.
     *
     * Fires on the second release if both taps were within maxTapTicks each,
     * and the gap between the first release and second press was <= gapTicks.
     *
     * @param action      the action to monitor
     * @param maxTapTicks maximum down-duration per tap
     * @param gapTicks    maximum ticks between first release and second press
     */
    record DoubleTap(ActionId action, int maxTapTicks, int gapTicks) implements InputGesture {
        public DoubleTap {
            if (action == null) throw new IllegalArgumentException("action must not be null");
            if (maxTapTicks < 1) throw new IllegalArgumentException("maxTapTicks must be >= 1");
            if (gapTicks < 0) throw new IllegalArgumentException("gapTicks must be >= 0");
        }
    }

    /**
     * Chord: multiple actions all held simultaneously.
     *
     * Fires on the tick where ALL actions are down. Fires once per chord activation
     * (re-fires only after at least one action is released and all are pressed again).
     *
     * Order does not matter — only simultaneous down-state.
     *
     * @param actions  the actions that must all be down (min 2)
     */
    record Chord(List<ActionId> actions) implements InputGesture {
        public Chord {
            if (actions == null || actions.size() < 2)
                throw new IllegalArgumentException("chord requires at least 2 actions");
            actions = List.copyOf(actions);
        }
    }

    /**
     * Sequence: actions pressed in order within a timeout.
     *
     * Each step must be pressed (edge) in the correct order. The entire sequence
     * must complete within timeoutTicks of the first step. Fires on the tick
     * where the final step is pressed.
     *
     * @param steps        ordered list of actions (e.g., [UP, UP, DOWN, DOWN])
     * @param timeoutTicks maximum ticks from first step to last step
     */
    record Sequence(List<ActionId> steps, int timeoutTicks) implements InputGesture {
        public Sequence {
            if (steps == null || steps.size() < 2)
                throw new IllegalArgumentException("sequence requires at least 2 steps");
            steps = List.copyOf(steps);
            if (timeoutTicks < 1) throw new IllegalArgumentException("timeoutTicks must be >= 1");
        }
    }
}
