package org.dynamisengine.input.core;

import org.dynamisengine.input.api.ActionId;
import org.dynamisengine.input.api.gesture.GestureFrame;
import org.dynamisengine.input.api.gesture.GestureId;
import org.dynamisengine.input.api.gesture.InputGesture;
import org.dynamisengine.input.api.frame.InputFrame;

import java.util.*;

/**
 * Evaluates {@link InputGesture} patterns against {@link InputFrame} snapshots.
 *
 * Call {@link #evaluate(InputFrame)} once per tick after producing the frame.
 * Returns the set of gesture IDs that fired this tick.
 *
 * All timing is tick-based. No wall-clock dependencies. Fully deterministic.
 * Safe for recording/replay.
 *
 * INTERNAL STATE:
 *   Each registered gesture has a small state machine tracking its progress.
 *   State is reset appropriately on completion or timeout.
 */
public final class GestureRecognizer {

    private final Map<GestureId, InputGesture> gestures = new LinkedHashMap<>();
    private final Map<GestureId, GestureState> states = new HashMap<>();

    /** Register a named gesture. */
    public void register(GestureId id, InputGesture gesture) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(gesture);
        gestures.put(id, gesture);
        states.put(id, createState(gesture));
    }

    /** Unregister a gesture. */
    public void unregister(GestureId id) {
        gestures.remove(id);
        states.remove(id);
    }

    /**
     * Evaluate all registered gestures against the current frame.
     * Returns the set of gesture IDs that fired this tick.
     * Deterministic: same frame sequence → same results.
     */
    public Set<GestureId> evaluate(InputFrame frame) {
        Set<GestureId> fired = new LinkedHashSet<>();
        for (var entry : gestures.entrySet()) {
            GestureId id = entry.getKey();
            InputGesture gesture = entry.getValue();
            GestureState state = states.get(id);
            if (state.evaluate(gesture, frame)) {
                fired.add(id);
            }
        }
        return fired;
    }

    /**
     * Evaluate all registered gestures and return a structured {@link GestureFrame}.
     *
     * Includes fired gestures, active holds, and sequence progress.
     * Deterministic: same frame sequence → same GestureFrame.
     *
     * CONFLICT/PRECEDENCE POLICY:
     *   All gestures evaluate independently. There is no implicit suppression.
     *   A Tap and DoubleTap on the same action CAN both fire if timing allows.
     *   A Chord does NOT consume its component actions' Tap gestures.
     *   Games should register non-conflicting gesture sets, or resolve conflicts
     *   in their own game logic layer above the recognizer.
     */
    public GestureFrame evaluateFrame(InputFrame frame) {
        Set<GestureId> fired = evaluate(frame);

        // Collect active holds
        Set<GestureId> activeHolds = new LinkedHashSet<>();
        for (var entry : gestures.entrySet()) {
            GestureId id = entry.getKey();
            GestureState state = states.get(id);
            if (state instanceof HoldState hs && hs.isActive()) {
                activeHolds.add(id);
            }
        }

        // Collect sequence progress
        Map<GestureId, Integer> seqProgress = new LinkedHashMap<>();
        for (var entry : gestures.entrySet()) {
            GestureId id = entry.getKey();
            GestureState state = states.get(id);
            if (state instanceof SequenceState ss && ss.currentStep > 0) {
                seqProgress.put(id, ss.currentStep);
            }
        }

        return new GestureFrame(frame.tick(), fired, activeHolds, seqProgress);
    }

    /**
     * Reset all gesture states. Call when the input context changes
     * (e.g., context stack push/pop) to prevent stale gesture state
     * from leaking across context boundaries.
     *
     * CONTEXT-CHANGE RESET POLICY:
     *   When the game switches input contexts (gameplay → menu, menu → gameplay),
     *   all gesture state machines reset to their initial state. This prevents:
     *   - A hold-in-progress from firing after context switch
     *   - A partial sequence from completing in a different context
     *   - A double-tap first-tap from carrying across contexts
     */

    /** Reset all gesture states. */
    public void reset() {
        for (var entry : gestures.entrySet()) {
            states.put(entry.getKey(), createState(entry.getValue()));
        }
    }

    // -- State machines -------------------------------------------------------

    private static GestureState createState(InputGesture gesture) {
        return switch (gesture) {
            case InputGesture.Tap t -> new TapState();
            case InputGesture.Hold h -> new HoldState();
            case InputGesture.DoubleTap dt -> new DoubleTapState();
            case InputGesture.Chord c -> new ChordState();
            case InputGesture.Sequence s -> new SequenceState();
        };
    }

    private sealed interface GestureState {
        /** Returns true if the gesture fired this tick. */
        boolean evaluate(InputGesture gesture, InputFrame frame);
    }

    // -- Tap ------------------------------------------------------------------

    private static final class TapState implements GestureState {
        private long pressStartTick = -1;

        @Override
        public boolean evaluate(InputGesture gesture, InputFrame frame) {
            var tap = (InputGesture.Tap) gesture;
            ActionId action = tap.action();

            if (frame.pressed(action)) {
                pressStartTick = frame.tick();
            }

            if (frame.released(action) && pressStartTick >= 0) {
                long duration = frame.tick() - pressStartTick;
                pressStartTick = -1;
                return duration <= tap.maxTicks();
            }

            // If held too long, invalidate
            if (frame.down(action) && pressStartTick >= 0) {
                if (frame.tick() - pressStartTick > tap.maxTicks()) {
                    pressStartTick = -1;
                }
            }

            return false;
        }
    }

    // -- Hold -----------------------------------------------------------------

    private static final class HoldState implements GestureState {
        private long pressStartTick = -1;
        private boolean hasFired = false;

        /** True if the hold has fired and the action is still held. */
        boolean isActive() { return hasFired && pressStartTick >= 0; }

        @Override
        public boolean evaluate(InputGesture gesture, InputFrame frame) {
            var hold = (InputGesture.Hold) gesture;
            ActionId action = hold.action();

            if (frame.pressed(action)) {
                pressStartTick = frame.tick();
                hasFired = false;
            }

            if (!frame.down(action)) {
                pressStartTick = -1;
                hasFired = false;
                return false;
            }

            if (!hasFired && pressStartTick >= 0) {
                long duration = frame.tick() - pressStartTick;
                if (duration >= hold.minTicks()) {
                    hasFired = true;
                    return true;
                }
            }

            return false;
        }
    }

    // -- DoubleTap ------------------------------------------------------------

    private static final class DoubleTapState implements GestureState {
        private long firstPressStartTick = -1;
        private long firstReleaseTick = -1;
        private long secondPressStartTick = -1;
        private boolean waitingForSecondTap = false;

        @Override
        public boolean evaluate(InputGesture gesture, InputFrame frame) {
            var dt = (InputGesture.DoubleTap) gesture;
            ActionId action = dt.action();

            if (!waitingForSecondTap) {
                // Looking for first tap
                if (frame.pressed(action)) {
                    firstPressStartTick = frame.tick();
                }
                if (frame.released(action) && firstPressStartTick >= 0) {
                    long duration = frame.tick() - firstPressStartTick;
                    if (duration <= dt.maxTapTicks()) {
                        firstReleaseTick = frame.tick();
                        waitingForSecondTap = true;
                    }
                    firstPressStartTick = -1;
                }
            } else {
                // Looking for second tap
                // Check gap timeout
                if (frame.tick() - firstReleaseTick > dt.gapTicks() && !frame.down(action)) {
                    waitingForSecondTap = false;
                    return false;
                }

                if (frame.pressed(action)) {
                    long gap = frame.tick() - firstReleaseTick;
                    if (gap <= dt.gapTicks()) {
                        secondPressStartTick = frame.tick();
                    } else {
                        // Gap too large — restart as new first tap
                        firstPressStartTick = frame.tick();
                        waitingForSecondTap = false;
                    }
                }

                if (frame.released(action) && secondPressStartTick >= 0) {
                    long duration = frame.tick() - secondPressStartTick;
                    secondPressStartTick = -1;
                    waitingForSecondTap = false;
                    firstPressStartTick = -1;
                    return duration <= dt.maxTapTicks();
                }
            }

            return false;
        }
    }

    // -- Chord ----------------------------------------------------------------

    private static final class ChordState implements GestureState {
        private boolean wasFired = false;

        @Override
        public boolean evaluate(InputGesture gesture, InputFrame frame) {
            var chord = (InputGesture.Chord) gesture;
            boolean allDown = true;
            for (ActionId action : chord.actions()) {
                if (!frame.down(action)) {
                    allDown = false;
                    break;
                }
            }

            if (allDown && !wasFired) {
                wasFired = true;
                return true;
            }

            if (!allDown) {
                wasFired = false;
            }

            return false;
        }
    }

    // -- Sequence -------------------------------------------------------------

    private static final class SequenceState implements GestureState {
        private int currentStep = 0;
        private long firstStepTick = -1;

        @Override
        public boolean evaluate(InputGesture gesture, InputFrame frame) {
            var seq = (InputGesture.Sequence) gesture;
            List<ActionId> steps = seq.steps();

            // Check timeout
            if (currentStep > 0 && firstStepTick >= 0) {
                if (frame.tick() - firstStepTick > seq.timeoutTicks()) {
                    currentStep = 0;
                    firstStepTick = -1;
                }
            }

            // Check if current step action was pressed this tick
            ActionId expectedAction = steps.get(currentStep);
            if (frame.pressed(expectedAction)) {
                if (currentStep == 0) {
                    firstStepTick = frame.tick();
                }
                currentStep++;

                if (currentStep >= steps.size()) {
                    // Sequence complete
                    currentStep = 0;
                    firstStepTick = -1;
                    return true;
                }
            }

            return false;
        }
    }
}
