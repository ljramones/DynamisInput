package org.dynamisengine.input.core;

import org.dynamisengine.input.api.ActionId;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.api.frame.InputFrame.ActionState;
import org.dynamisengine.input.api.gesture.GestureId;
import org.dynamisengine.input.api.gesture.InputGesture;
import org.dynamisengine.input.api.AxisId;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class GestureRecognizerTest {

    private static final ActionId JUMP = new ActionId("jump");
    private static final ActionId ATTACK = new ActionId("attack");
    private static final ActionId BLOCK = new ActionId("block");
    private static final ActionId UP = new ActionId("up");
    private static final ActionId DOWN = new ActionId("down");
    private static final ActionId LEFT = new ActionId("left");
    private static final ActionId RIGHT = new ActionId("right");

    private static final GestureId DODGE = new GestureId("dodge");
    private static final GestureId HEAVY_ATTACK = new GestureId("heavyAttack");
    private static final GestureId DOUBLE_JUMP = new GestureId("doubleJump");
    private static final GestureId PARRY = new GestureId("parry");
    private static final GestureId KONAMI = new GestureId("konami");

    // -- Tap ------------------------------------------------------------------

    @Test
    void tapFiresOnQuickRelease() {
        var recognizer = new GestureRecognizer();
        recognizer.register(DODGE, new InputGesture.Tap(JUMP, 5));

        assertThat(recognizer.evaluate(frame(1, pressed(JUMP)))).isEmpty();
        assertThat(recognizer.evaluate(frame(2, down(JUMP)))).isEmpty();
        assertThat(recognizer.evaluate(frame(3, released(JUMP)))).containsExactly(DODGE);
    }

    @Test
    void tapDoesNotFireIfHeldTooLong() {
        var recognizer = new GestureRecognizer();
        recognizer.register(DODGE, new InputGesture.Tap(JUMP, 3));

        recognizer.evaluate(frame(1, pressed(JUMP)));
        recognizer.evaluate(frame(2, down(JUMP)));
        recognizer.evaluate(frame(3, down(JUMP)));
        recognizer.evaluate(frame(4, down(JUMP)));
        // Released at tick 5 — duration 4 ticks > maxTicks 3
        assertThat(recognizer.evaluate(frame(5, released(JUMP)))).isEmpty();
    }

    @Test
    void tapFiresAtExactMaxTicks() {
        var recognizer = new GestureRecognizer();
        recognizer.register(DODGE, new InputGesture.Tap(JUMP, 3));

        recognizer.evaluate(frame(1, pressed(JUMP)));
        recognizer.evaluate(frame(2, down(JUMP)));
        recognizer.evaluate(frame(3, down(JUMP)));
        // Released at tick 4 — duration exactly 3 ticks = maxTicks
        assertThat(recognizer.evaluate(frame(4, released(JUMP)))).containsExactly(DODGE);
    }

    // -- Hold -----------------------------------------------------------------

    @Test
    void holdFiresAfterThreshold() {
        var recognizer = new GestureRecognizer();
        recognizer.register(HEAVY_ATTACK, new InputGesture.Hold(ATTACK, 5));

        recognizer.evaluate(frame(1, pressed(ATTACK)));
        assertThat(recognizer.evaluate(frame(2, down(ATTACK)))).isEmpty();
        assertThat(recognizer.evaluate(frame(3, down(ATTACK)))).isEmpty();
        assertThat(recognizer.evaluate(frame(5, down(ATTACK)))).isEmpty();
        // Tick 6: duration = 5 ticks >= minTicks
        assertThat(recognizer.evaluate(frame(6, down(ATTACK)))).containsExactly(HEAVY_ATTACK);
    }

    @Test
    void holdFiresOnlyOnce() {
        var recognizer = new GestureRecognizer();
        recognizer.register(HEAVY_ATTACK, new InputGesture.Hold(ATTACK, 3));

        recognizer.evaluate(frame(1, pressed(ATTACK)));
        recognizer.evaluate(frame(2, down(ATTACK)));
        recognizer.evaluate(frame(3, down(ATTACK)));
        assertThat(recognizer.evaluate(frame(4, down(ATTACK)))).containsExactly(HEAVY_ATTACK);
        // Should not fire again
        assertThat(recognizer.evaluate(frame(5, down(ATTACK)))).isEmpty();
        assertThat(recognizer.evaluate(frame(6, down(ATTACK)))).isEmpty();
    }

    @Test
    void holdResetsOnRelease() {
        var recognizer = new GestureRecognizer();
        recognizer.register(HEAVY_ATTACK, new InputGesture.Hold(ATTACK, 3));

        recognizer.evaluate(frame(1, pressed(ATTACK)));
        recognizer.evaluate(frame(4, down(ATTACK))); // fires
        recognizer.evaluate(frame(5, released(ATTACK))); // reset

        // New hold should fire again
        recognizer.evaluate(frame(6, pressed(ATTACK)));
        recognizer.evaluate(frame(7, down(ATTACK)));
        recognizer.evaluate(frame(8, down(ATTACK)));
        assertThat(recognizer.evaluate(frame(9, down(ATTACK)))).containsExactly(HEAVY_ATTACK);
    }

    // -- DoubleTap ------------------------------------------------------------

    @Test
    void doubleTapFiresOnSecondRelease() {
        var recognizer = new GestureRecognizer();
        recognizer.register(DOUBLE_JUMP, new InputGesture.DoubleTap(JUMP, 3, 5));

        // First tap: press at 1, release at 2 (duration 1 <= 3)
        recognizer.evaluate(frame(1, pressed(JUMP)));
        assertThat(recognizer.evaluate(frame(2, released(JUMP)))).isEmpty();

        // Second tap: press at 4 (gap 2 <= 5), release at 5 (duration 1 <= 3)
        recognizer.evaluate(frame(4, pressed(JUMP)));
        assertThat(recognizer.evaluate(frame(5, released(JUMP)))).containsExactly(DOUBLE_JUMP);
    }

    @Test
    void doubleTapFailsIfGapTooLong() {
        var recognizer = new GestureRecognizer();
        recognizer.register(DOUBLE_JUMP, new InputGesture.DoubleTap(JUMP, 3, 2));

        recognizer.evaluate(frame(1, pressed(JUMP)));
        recognizer.evaluate(frame(2, released(JUMP)));
        // Gap of 5 ticks > gapTicks 2
        recognizer.evaluate(frame(3, idle()));
        recognizer.evaluate(frame(4, idle()));
        recognizer.evaluate(frame(5, idle()));
        recognizer.evaluate(frame(7, pressed(JUMP)));
        assertThat(recognizer.evaluate(frame(8, released(JUMP)))).isEmpty();
    }

    @Test
    void doubleTapFailsIfSecondTapTooSlow() {
        var recognizer = new GestureRecognizer();
        recognizer.register(DOUBLE_JUMP, new InputGesture.DoubleTap(JUMP, 2, 5));

        // First tap: quick
        recognizer.evaluate(frame(1, pressed(JUMP)));
        recognizer.evaluate(frame(2, released(JUMP)));

        // Second tap: held too long (duration 3 > maxTapTicks 2)
        recognizer.evaluate(frame(3, pressed(JUMP)));
        recognizer.evaluate(frame(4, down(JUMP)));
        recognizer.evaluate(frame(5, down(JUMP)));
        assertThat(recognizer.evaluate(frame(6, released(JUMP)))).isEmpty();
    }

    // -- Chord ----------------------------------------------------------------

    @Test
    void chordFiresWhenAllDown() {
        var recognizer = new GestureRecognizer();
        recognizer.register(PARRY, new InputGesture.Chord(List.of(ATTACK, BLOCK)));

        // Attack down first
        recognizer.evaluate(frame(1, actions(ATTACK, true, false, true)));
        // Both down
        assertThat(recognizer.evaluate(frame(2,
                actions(ATTACK, false, false, true, BLOCK, true, false, true))))
                .containsExactly(PARRY);
    }

    @Test
    void chordFiresOnlyOncePerActivation() {
        var recognizer = new GestureRecognizer();
        recognizer.register(PARRY, new InputGesture.Chord(List.of(ATTACK, BLOCK)));

        // Both pressed
        assertThat(recognizer.evaluate(frame(1,
                actions(ATTACK, true, false, true, BLOCK, true, false, true))))
                .containsExactly(PARRY);

        // Still both down — should NOT re-fire
        assertThat(recognizer.evaluate(frame(2,
                actions(ATTACK, false, false, true, BLOCK, false, false, true))))
                .isEmpty();
    }

    @Test
    void chordRefresAfterReleaseAndRepress() {
        var recognizer = new GestureRecognizer();
        recognizer.register(PARRY, new InputGesture.Chord(List.of(ATTACK, BLOCK)));

        recognizer.evaluate(frame(1, actions(ATTACK, true, false, true, BLOCK, true, false, true)));
        recognizer.evaluate(frame(2, actions(ATTACK, false, true, false))); // attack released
        assertThat(recognizer.evaluate(frame(3,
                actions(ATTACK, true, false, true, BLOCK, false, false, true))))
                .containsExactly(PARRY);
    }

    @Test
    void chordOrderDoesNotMatter() {
        var recognizer = new GestureRecognizer();
        recognizer.register(PARRY, new InputGesture.Chord(List.of(ATTACK, BLOCK)));

        // Block first, then attack
        recognizer.evaluate(frame(1, actions(BLOCK, true, false, true)));
        assertThat(recognizer.evaluate(frame(2,
                actions(BLOCK, false, false, true, ATTACK, true, false, true))))
                .containsExactly(PARRY);
    }

    // -- Sequence -------------------------------------------------------------

    @Test
    void sequenceFiresOnCompletion() {
        var recognizer = new GestureRecognizer();
        recognizer.register(KONAMI, new InputGesture.Sequence(
                List.of(UP, UP, DOWN, DOWN), 20));

        recognizer.evaluate(frame(1, pressed(UP)));
        recognizer.evaluate(frame(3, pressed(UP)));
        recognizer.evaluate(frame(5, pressed(DOWN)));
        assertThat(recognizer.evaluate(frame(7, pressed(DOWN))))
                .containsExactly(KONAMI);
    }

    @Test
    void sequenceTimesOut() {
        var recognizer = new GestureRecognizer();
        recognizer.register(KONAMI, new InputGesture.Sequence(
                List.of(UP, UP, DOWN, DOWN), 5));

        recognizer.evaluate(frame(1, pressed(UP)));
        recognizer.evaluate(frame(2, pressed(UP)));
        // Ticks pass...timeout at tick 1+5=6
        recognizer.evaluate(frame(7, pressed(DOWN))); // reset due to timeout, this becomes step 0 check
        assertThat(recognizer.evaluate(frame(8, pressed(DOWN)))).isEmpty();
    }

    @Test
    void sequenceResetsOnWrongInput() {
        var recognizer = new GestureRecognizer();
        recognizer.register(KONAMI, new InputGesture.Sequence(
                List.of(UP, UP, DOWN), 20));

        recognizer.evaluate(frame(1, pressed(UP)));     // step 0 ✓
        recognizer.evaluate(frame(2, pressed(LEFT)));   // wrong — ignored (not UP), stays at step 1
        recognizer.evaluate(frame(3, pressed(UP)));     // step 1 ✓
        assertThat(recognizer.evaluate(frame(4, pressed(DOWN))))
                .containsExactly(KONAMI); // step 2 ✓ — fires
    }

    // -- Multiple gestures simultaneously ------------------------------------

    @Test
    void multipleGesturesCanFireSameTick() {
        var recognizer = new GestureRecognizer();
        var tapDodge = new GestureId("tapDodge");
        var holdCharge = new GestureId("holdCharge");

        recognizer.register(tapDodge, new InputGesture.Tap(JUMP, 5));
        recognizer.register(holdCharge, new InputGesture.Hold(ATTACK, 3));

        // Set up: jump pressed at tick 1, attack pressed at tick 1
        recognizer.evaluate(frame(1,
                actions(JUMP, true, false, true, ATTACK, true, false, true)));
        recognizer.evaluate(frame(2,
                actions(JUMP, false, false, true, ATTACK, false, false, true)));
        recognizer.evaluate(frame(3,
                actions(JUMP, false, false, true, ATTACK, false, false, true)));

        // Tick 4: jump released (tap fires), attack still held (hold fires at tick 4 = 3 ticks held)
        Set<GestureId> fired = recognizer.evaluate(frame(4,
                actions(JUMP, false, true, false, ATTACK, false, false, true)));

        assertThat(fired).containsExactlyInAnyOrder(tapDodge, holdCharge);
    }

    // -- Replay determinism --------------------------------------------------

    @Test
    void gesturesAreReplayDeterministic() {
        List<InputFrame> frames = List.of(
                frame(1, pressed(JUMP)),
                frame(2, down(JUMP)),
                frame(3, released(JUMP)),
                frame(5, pressed(JUMP)),
                frame(6, released(JUMP)));

        var r1 = new GestureRecognizer();
        var r2 = new GestureRecognizer();
        r1.register(DOUBLE_JUMP, new InputGesture.DoubleTap(JUMP, 3, 5));
        r2.register(DOUBLE_JUMP, new InputGesture.DoubleTap(JUMP, 3, 5));

        List<Set<GestureId>> results1 = new ArrayList<>();
        List<Set<GestureId>> results2 = new ArrayList<>();
        for (InputFrame f : frames) {
            results1.add(r1.evaluate(f));
            results2.add(r2.evaluate(f));
        }

        assertThat(results1).isEqualTo(results2);
    }

    // -- Validation -----------------------------------------------------------

    @Test
    void gestureValidation() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new InputGesture.Tap(null, 5));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new InputGesture.Tap(JUMP, 0));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new InputGesture.Hold(JUMP, 0));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new InputGesture.Chord(List.of(JUMP))); // needs at least 2
        assertThatIllegalArgumentException().isThrownBy(
                () -> new InputGesture.Sequence(List.of(UP), 10)); // needs at least 2
    }

    // -- Frame building helpers -----------------------------------------------

    private static InputFrame frame(long tick, Map<ActionId, ActionState> actions) {
        return new InputFrame(tick, actions, Map.of(), List.of());
    }

    private static Map<ActionId, ActionState> pressed(ActionId id) {
        return Map.of(id, new ActionState(true, false, true));
    }

    private static Map<ActionId, ActionState> down(ActionId id) {
        return Map.of(id, new ActionState(false, false, true));
    }

    private static Map<ActionId, ActionState> released(ActionId id) {
        return Map.of(id, new ActionState(false, true, false));
    }

    private static Map<ActionId, ActionState> idle() {
        return Map.of();
    }

    private static Map<ActionId, ActionState> actions(ActionId id,
            boolean pressed, boolean released, boolean down) {
        return Map.of(id, new ActionState(pressed, released, down));
    }

    private static Map<ActionId, ActionState> actions(
            ActionId id1, boolean p1, boolean r1, boolean d1,
            ActionId id2, boolean p2, boolean r2, boolean d2) {
        Map<ActionId, ActionState> map = new LinkedHashMap<>();
        map.put(id1, new ActionState(p1, r1, d1));
        map.put(id2, new ActionState(p2, r2, d2));
        return map;
    }
}
