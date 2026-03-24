package org.dynamisengine.input.core;

import org.dynamisengine.input.api.ActionId;
import org.dynamisengine.input.api.AxisId;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.api.frame.InputFrame.ActionState;
import org.dynamisengine.input.api.gesture.GestureFrame;
import org.dynamisengine.input.api.gesture.GestureId;
import org.dynamisengine.input.api.gesture.InputGesture;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.*;

/**
 * Stress tests for the gesture recognizer.
 */
class GestureStressTest {

    // -- Large gesture set ---------------------------------------------------

    @Test
    void manyRegisteredGesturesDoNotDegrade() {
        var recognizer = new GestureRecognizer();

        // Register 200 gestures of mixed types
        for (int i = 0; i < 50; i++) {
            ActionId action = new ActionId("action" + i);
            recognizer.register(new GestureId("tap" + i), new InputGesture.Tap(action, 5));
            recognizer.register(new GestureId("hold" + i), new InputGesture.Hold(action, 10));
            recognizer.register(new GestureId("chord" + i), new InputGesture.Chord(
                    List.of(action, new ActionId("action" + ((i + 1) % 50)))));
            recognizer.register(new GestureId("seq" + i), new InputGesture.Sequence(
                    List.of(action, new ActionId("action" + ((i + 1) % 50))), 20));
        }

        // Evaluate 500 frames with random action states
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (long tick = 1; tick <= 500; tick++) {
            Map<ActionId, ActionState> actions = new LinkedHashMap<>();
            for (int i = 0; i < 50; i++) {
                if (rng.nextBoolean()) {
                    boolean pressed = rng.nextInt(3) == 0;
                    boolean released = !pressed && rng.nextInt(3) == 0;
                    boolean down = pressed || (!released && rng.nextBoolean());
                    actions.put(new ActionId("action" + i), new ActionState(pressed, released, down));
                }
            }
            InputFrame frame = new InputFrame(tick, actions, Map.of(), List.of());
            GestureFrame gf = recognizer.evaluateFrame(frame);
            assertThat(gf).isNotNull();
            assertThat(gf.tick()).isEqualTo(tick);
        }
    }

    // -- Register/unregister churn -------------------------------------------

    @Test
    void registerUnregisterChurnDoesNotCorrupt() {
        var recognizer = new GestureRecognizer();
        ActionId jump = new ActionId("jump");

        for (int i = 0; i < 200; i++) {
            GestureId id = new GestureId("gesture" + (i % 20));
            if (i % 3 == 0) {
                recognizer.unregister(id);
            } else {
                recognizer.register(id, new InputGesture.Tap(jump, 5));
            }
        }

        // Should still evaluate without error
        InputFrame frame = new InputFrame(1,
                Map.of(jump, new ActionState(true, false, true)),
                Map.of(), List.of());
        Set<GestureId> fired = recognizer.evaluate(frame);
        assertThat(fired).isNotNull();
    }

    // -- Overlapping tap/double-tap on same action ---------------------------

    @Test
    void tapAndDoubleTapOnSameActionBothFire() {
        var recognizer = new GestureRecognizer();
        ActionId jump = new ActionId("jump");
        GestureId quickJump = new GestureId("quickJump");
        GestureId doubleJump = new GestureId("doubleJump");

        recognizer.register(quickJump, new InputGesture.Tap(jump, 5));
        recognizer.register(doubleJump, new InputGesture.DoubleTap(jump, 5, 10));

        // First tap: press at 1, release at 2
        recognizer.evaluate(frame(1, jump, true, false, true));
        Set<GestureId> r2 = recognizer.evaluate(frame(2, jump, false, true, false));
        // Tap fires on first release
        assertThat(r2).contains(quickJump);
        assertThat(r2).doesNotContain(doubleJump);

        // Second tap: press at 4, release at 5
        recognizer.evaluate(frame(4, jump, true, false, true));
        Set<GestureId> r5 = recognizer.evaluate(frame(5, jump, false, true, false));
        // Both Tap and DoubleTap fire
        assertThat(r5).contains(quickJump);
        assertThat(r5).contains(doubleJump);
    }

    // -- Context-change reset ------------------------------------------------

    @Test
    void resetClearsPartialSequence() {
        var recognizer = new GestureRecognizer();
        ActionId up = new ActionId("up");
        ActionId down = new ActionId("down");
        GestureId combo = new GestureId("upDown");

        recognizer.register(combo, new InputGesture.Sequence(List.of(up, down), 20));

        // Start sequence: up pressed
        recognizer.evaluate(frame(1, up, true, false, true));

        // Context change — reset all state
        recognizer.reset();

        // "down" should NOT complete the sequence (state was reset)
        Set<GestureId> fired = recognizer.evaluate(frame(3, down, true, false, true));
        assertThat(fired).isEmpty();
    }

    @Test
    void resetClearsPartialHold() {
        var recognizer = new GestureRecognizer();
        ActionId attack = new ActionId("attack");
        GestureId heavyAttack = new GestureId("heavyAttack");

        recognizer.register(heavyAttack, new InputGesture.Hold(attack, 5));

        // Start holding
        recognizer.evaluate(frame(1, attack, true, false, true));
        recognizer.evaluate(frame(2, attack, false, false, true));
        recognizer.evaluate(frame(3, attack, false, false, true));

        // Context change — reset
        recognizer.reset();

        // Continue holding — should NOT fire (counter reset)
        recognizer.evaluate(frame(4, attack, false, false, true));
        recognizer.evaluate(frame(5, attack, false, false, true));
        Set<GestureId> fired = recognizer.evaluate(frame(6, attack, false, false, true));
        assertThat(fired).isEmpty();
    }

    @Test
    void resetClearsDoubleTapFirstTap() {
        var recognizer = new GestureRecognizer();
        ActionId jump = new ActionId("jump");
        GestureId doubleJump = new GestureId("doubleJump");

        recognizer.register(doubleJump, new InputGesture.DoubleTap(jump, 5, 10));

        // First tap
        recognizer.evaluate(frame(1, jump, true, false, true));
        recognizer.evaluate(frame(2, jump, false, true, false));

        // Context change
        recognizer.reset();

        // Second tap should NOT complete double-tap (first tap state was reset)
        recognizer.evaluate(frame(4, jump, true, false, true));
        Set<GestureId> fired = recognizer.evaluate(frame(5, jump, false, true, false));
        assertThat(fired).isEmpty();
    }

    // -- GestureFrame structured result --------------------------------------

    @Test
    void gestureFrameReportsActiveHold() {
        var recognizer = new GestureRecognizer();
        ActionId attack = new ActionId("attack");
        GestureId heavyAttack = new GestureId("heavyAttack");
        recognizer.register(heavyAttack, new InputGesture.Hold(attack, 3));

        recognizer.evaluateFrame(frame(1, attack, true, false, true));
        recognizer.evaluateFrame(frame(2, attack, false, false, true));
        recognizer.evaluateFrame(frame(3, attack, false, false, true));
        GestureFrame gf = recognizer.evaluateFrame(frame(4, attack, false, false, true));

        assertThat(gf.fired(heavyAttack)).isTrue();
        assertThat(gf.holding(heavyAttack)).isTrue();

        // Next tick: still holding, but doesn't re-fire
        GestureFrame gf2 = recognizer.evaluateFrame(frame(5, attack, false, false, true));
        assertThat(gf2.fired(heavyAttack)).isFalse();
        assertThat(gf2.holding(heavyAttack)).isTrue();
    }

    @Test
    void gestureFrameReportsSequenceProgress() {
        var recognizer = new GestureRecognizer();
        ActionId up = new ActionId("up");
        ActionId down = new ActionId("down");
        ActionId left = new ActionId("left");
        GestureId combo = new GestureId("combo");
        recognizer.register(combo, new InputGesture.Sequence(List.of(up, down, left), 20));

        GestureFrame gf0 = recognizer.evaluateFrame(frame(1, up, true, false, true));
        assertThat(gf0.sequenceStep(combo)).isEqualTo(1); // completed step 0, now at step 1

        GestureFrame gf1 = recognizer.evaluateFrame(frame(2, down, true, false, true));
        assertThat(gf1.sequenceStep(combo)).isEqualTo(2);

        GestureFrame gf2 = recognizer.evaluateFrame(frame(3, left, true, false, true));
        assertThat(gf2.fired(combo)).isTrue();
        assertThat(gf2.sequenceStep(combo)).isEqualTo(-1); // completed, reset
    }

    // -- Helpers --------------------------------------------------------------

    private static InputFrame frame(long tick, ActionId action,
                                     boolean pressed, boolean released, boolean down) {
        return new InputFrame(tick,
                Map.of(action, new ActionState(pressed, released, down)),
                Map.of(), List.of());
    }
}
