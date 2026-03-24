package org.dynamisengine.input.core;

import org.dynamisengine.input.api.*;
import org.dynamisengine.input.api.bind.*;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.window.api.GamepadAxes;
import org.dynamisengine.window.api.GamepadButtons;
import org.dynamisengine.window.api.InputEvent;
import org.dynamisengine.window.api.InputEvent.InputAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class GamepadInputTest {

    private static final ActionId JUMP = new ActionId("jump");
    private static final ActionId FIRE = new ActionId("fire");
    private static final AxisId MOVE_X = new AxisId("moveX");
    private static final AxisId MOVE_Y = new AxisId("moveY");
    private static final AxisId LOOK_X = new AxisId("lookX");
    private static final AxisId LOOK_Y = new AxisId("lookY");
    private static final AxisId TRIGGER_L = new AxisId("triggerL");
    private static final ContextId GAMEPLAY = new ContextId("gameplay");

    // -- Gamepad button tests ------------------------------------------------

    @Test
    void gamepadButtonPressAndRelease() {
        var processor = createProcessor(
                Map.of(JUMP, List.of(new GamepadButtonBinding(GamepadButtons.A))),
                Map.of());

        processor.feed(new InputEvent.GamepadButton(0, GamepadButtons.A, InputAction.PRESS), 1);
        InputFrame frame = processor.snapshot(1);
        assertThat(frame.pressed(JUMP)).isTrue();
        assertThat(frame.down(JUMP)).isTrue();

        processor.feed(new InputEvent.GamepadButton(0, GamepadButtons.A, InputAction.RELEASE), 2);
        frame = processor.snapshot(2);
        assertThat(frame.released(JUMP)).isTrue();
        assertThat(frame.down(JUMP)).isFalse();
    }

    @Test
    void gamepadButtonAnyGamepadMatches() {
        var processor = createProcessor(
                Map.of(JUMP, List.of(new GamepadButtonBinding(GamepadButtons.A, -1))),
                Map.of());

        // Button from gamepad 2 should still match
        processor.feed(new InputEvent.GamepadButton(2, GamepadButtons.A, InputAction.PRESS), 1);
        assertThat(processor.snapshot(1).pressed(JUMP)).isTrue();
    }

    @Test
    void gamepadButtonSpecificGamepadFilters() {
        var processor = createProcessor(
                Map.of(JUMP, List.of(new GamepadButtonBinding(GamepadButtons.A, 0))),
                Map.of());

        // Button from gamepad 1 should NOT match binding for gamepad 0
        processor.feed(new InputEvent.GamepadButton(1, GamepadButtons.A, InputAction.PRESS), 1);
        assertThat(processor.snapshot(1).pressed(JUMP)).isFalse();

        // Button from gamepad 0 should match
        processor.feed(new InputEvent.GamepadButton(0, GamepadButtons.A, InputAction.PRESS), 2);
        assertThat(processor.snapshot(2).pressed(JUMP)).isTrue();
    }

    @Test
    void gamepadAndKeyboardBindSameAction() {
        var processor = createProcessor(
                Map.of(JUMP, List.of(
                        new KeyBinding(32, 0),  // spacebar
                        new GamepadButtonBinding(GamepadButtons.A))),
                Map.of());

        // Keyboard press triggers jump
        processor.feed(new InputEvent.Key(32, 0, InputAction.PRESS, 0), 1);
        assertThat(processor.snapshot(1).pressed(JUMP)).isTrue();

        // Gamepad press also triggers jump
        processor.feed(new InputEvent.Key(32, 0, InputAction.RELEASE, 0), 2);
        processor.feed(new InputEvent.GamepadButton(0, GamepadButtons.A, InputAction.PRESS), 2);
        assertThat(processor.snapshot(2).pressed(JUMP)).isTrue();
    }

    // -- Gamepad axis tests --------------------------------------------------

    @Test
    void gamepadAxisWithDefaultShaping() {
        var processor = createProcessor(Map.of(), Map.of(
                TRIGGER_L, List.of(new GamepadAxisBinding(GamepadAxes.LEFT_TRIGGER))));

        processor.feed(new InputEvent.GamepadAxis(0, GamepadAxes.LEFT_TRIGGER, 0.8f), 1);
        float value = processor.snapshot(1).axis(TRIGGER_L);
        assertThat(value).isGreaterThan(0.0f);
    }

    @Test
    void gamepadAxisDeadzoneFiltersSmallValues() {
        var shaping = new AnalogShaping(0.2f, 0.95f, ResponseCurve.LINEAR, 1.0f, false);
        var processor = createProcessor(Map.of(), Map.of(
                MOVE_X, List.of(new GamepadAxisBinding(GamepadAxes.LEFT_X, shaping))));

        // Value below inner deadzone → 0
        processor.feed(new InputEvent.GamepadAxis(0, GamepadAxes.LEFT_X, 0.1f), 1);
        assertThat(processor.snapshot(1).axis(MOVE_X)).isEqualTo(0.0f);

        // Value above inner deadzone → nonzero
        processor.feed(new InputEvent.GamepadAxis(0, GamepadAxes.LEFT_X, 0.5f), 2);
        assertThat(processor.snapshot(2).axis(MOVE_X)).isGreaterThan(0.0f);
    }

    @Test
    void gamepadAxisInversion() {
        var shaping = new AnalogShaping(0.0f, 1.0f, ResponseCurve.LINEAR, 1.0f, true);
        var processor = createProcessor(Map.of(), Map.of(
                LOOK_Y, List.of(new GamepadAxisBinding(GamepadAxes.RIGHT_Y, shaping))));

        processor.feed(new InputEvent.GamepadAxis(0, GamepadAxes.RIGHT_Y, 0.5f), 1);
        assertThat(processor.snapshot(1).axis(LOOK_Y)).isNegative();
    }

    // -- Gamepad stick (radial deadzone) tests --------------------------------

    @Test
    void gamepadStickRadialDeadzone() {
        var stick = GamepadStickBinding.leftStick(MOVE_X, MOVE_Y);
        var processor = createProcessor(Map.of(), Map.of(
                MOVE_X, List.of(stick),
                MOVE_Y, List.of(stick)));

        // Tiny deflection inside radial deadzone → both axes 0
        processor.feed(new InputEvent.GamepadAxis(0, GamepadAxes.LEFT_X, 0.05f), 1);
        processor.feed(new InputEvent.GamepadAxis(0, GamepadAxes.LEFT_Y, 0.05f), 1);
        InputFrame frame = processor.snapshot(1);
        assertThat(frame.axis(MOVE_X)).isEqualTo(0.0f);
        assertThat(frame.axis(MOVE_Y)).isEqualTo(0.0f);

        // Large deflection → nonzero
        processor.feed(new InputEvent.GamepadAxis(0, GamepadAxes.LEFT_X, 0.8f), 2);
        processor.feed(new InputEvent.GamepadAxis(0, GamepadAxes.LEFT_Y, 0.3f), 2);
        frame = processor.snapshot(2);
        assertThat(frame.axis(MOVE_X)).isGreaterThan(0.0f);
    }

    @Test
    void gamepadStickAxialDeadzone() {
        var shaping = AnalogShaping.DEFAULT;
        var stick = new GamepadStickBinding(MOVE_X, MOVE_Y,
                GamepadAxes.LEFT_X, GamepadAxes.LEFT_Y,
                shaping, DeadzoneMode.AXIAL, -1);
        var processor = createProcessor(Map.of(), Map.of(
                MOVE_X, List.of(stick),
                MOVE_Y, List.of(stick)));

        // X below deadzone, Y above deadzone
        processor.feed(new InputEvent.GamepadAxis(0, GamepadAxes.LEFT_X, 0.05f), 1);
        processor.feed(new InputEvent.GamepadAxis(0, GamepadAxes.LEFT_Y, 0.8f), 1);
        InputFrame frame = processor.snapshot(1);
        assertThat(frame.axis(MOVE_X)).isEqualTo(0.0f); // X filtered
        assertThat(frame.axis(MOVE_Y)).isGreaterThan(0.0f); // Y passes
    }

    @Test
    void gamepadStickFullDeflectionNormalizes() {
        var shaping = new AnalogShaping(0.0f, 1.0f, ResponseCurve.LINEAR, 1.0f, false);
        var stick = new GamepadStickBinding(MOVE_X, MOVE_Y,
                GamepadAxes.LEFT_X, GamepadAxes.LEFT_Y,
                shaping, DeadzoneMode.RADIAL, -1);
        var processor = createProcessor(Map.of(), Map.of(
                MOVE_X, List.of(stick),
                MOVE_Y, List.of(stick)));

        // Full diagonal deflection — corner can exceed magnitude 1.0
        processor.feed(new InputEvent.GamepadAxis(0, GamepadAxes.LEFT_X, 1.0f), 1);
        processor.feed(new InputEvent.GamepadAxis(0, GamepadAxes.LEFT_Y, 1.0f), 1);
        InputFrame frame = processor.snapshot(1);

        // Combined magnitude should be clamped to 1.0 before distribution
        float x = frame.axis(MOVE_X);
        float y = frame.axis(MOVE_Y);
        float mag = (float) Math.sqrt(x * x + y * y);
        assertThat(mag).isLessThanOrEqualTo(1.01f); // small epsilon for float
    }

    // -- Analog shaping unit tests -------------------------------------------

    @Test
    void analogShapingLinear() {
        var shaping = new AnalogShaping(0.0f, 1.0f, ResponseCurve.LINEAR, 1.0f, false);
        assertThat(shaping.shape(0.5f)).isCloseTo(0.5f, within(0.001f));
        assertThat(shaping.shape(-0.5f)).isCloseTo(-0.5f, within(0.001f));
        assertThat(shaping.shape(0.0f)).isEqualTo(0.0f);
        assertThat(shaping.shape(1.0f)).isCloseTo(1.0f, within(0.001f));
    }

    @Test
    void analogShapingQuadratic() {
        var shaping = new AnalogShaping(0.0f, 1.0f, ResponseCurve.QUADRATIC, 1.0f, false);
        assertThat(shaping.shape(0.5f)).isCloseTo(0.25f, within(0.001f));
        assertThat(shaping.shape(1.0f)).isCloseTo(1.0f, within(0.001f));
    }

    @Test
    void analogShapingDeadzoneRemapsRange() {
        var shaping = new AnalogShaping(0.2f, 0.8f, ResponseCurve.LINEAR, 1.0f, false);
        // 0.2 is inner edge → 0.0 output
        assertThat(shaping.shape(0.2f)).isEqualTo(0.0f);
        // 0.5 is midpoint of live zone [0.2, 0.8] → 0.5 output
        assertThat(shaping.shape(0.5f)).isCloseTo(0.5f, within(0.001f));
        // 0.8 is outer edge → 1.0 output
        assertThat(shaping.shape(0.8f)).isCloseTo(1.0f, within(0.01f));
        // Beyond outer → clamped to 1.0
        assertThat(shaping.shape(1.0f)).isCloseTo(1.0f, within(0.001f));
    }

    @Test
    void analogShapingSensitivityScales() {
        var shaping = new AnalogShaping(0.0f, 1.0f, ResponseCurve.LINEAR, 2.0f, false);
        assertThat(shaping.shape(0.5f)).isCloseTo(1.0f, within(0.001f));
    }

    @Test
    void analogShapingRejectsInvalid() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new AnalogShaping(-0.1f, 1.0f, ResponseCurve.LINEAR, 1.0f, false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new AnalogShaping(0.5f, 0.5f, ResponseCurve.LINEAR, 1.0f, false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new AnalogShaping(0.0f, 1.0f, null, 1.0f, false));
    }

    @Test
    void responseCurveApplyValues() {
        assertThat(ResponseCurve.LINEAR.apply(0.5f)).isEqualTo(0.5f);
        assertThat(ResponseCurve.QUADRATIC.apply(0.5f)).isEqualTo(0.25f);
        assertThat(ResponseCurve.CUBIC.apply(0.5f)).isEqualTo(0.125f);
        assertThat(ResponseCurve.SMOOTH.apply(0.0f)).isEqualTo(0.0f);
        assertThat(ResponseCurve.SMOOTH.apply(1.0f)).isEqualTo(1.0f);
        assertThat(ResponseCurve.SMOOTH.apply(0.5f)).isCloseTo(0.5f, within(0.001f));
    }

    // -- Replay determinism --------------------------------------------------

    @Test
    void gamepadEventsReplayDeterministically() {
        var processor1 = createProcessor(
                Map.of(JUMP, List.of(new GamepadButtonBinding(GamepadButtons.A))),
                Map.of(MOVE_X, List.of(new GamepadAxisBinding(GamepadAxes.LEFT_X))));

        var processor2 = createProcessor(
                Map.of(JUMP, List.of(new GamepadButtonBinding(GamepadButtons.A))),
                Map.of(MOVE_X, List.of(new GamepadAxisBinding(GamepadAxes.LEFT_X))));

        // Same events to both processors
        var events = List.<InputEvent>of(
                new InputEvent.GamepadButton(0, GamepadButtons.A, InputAction.PRESS),
                new InputEvent.GamepadAxis(0, GamepadAxes.LEFT_X, 0.7f));

        for (InputEvent e : events) {
            processor1.feed(e, 1);
            processor2.feed(e, 1);
        }

        InputFrame f1 = processor1.snapshot(1);
        InputFrame f2 = processor2.snapshot(1);

        assertThat(f1.pressed(JUMP)).isEqualTo(f2.pressed(JUMP));
        assertThat(f1.axis(MOVE_X)).isEqualTo(f2.axis(MOVE_X));
    }

    // -- Helpers --------------------------------------------------------------

    private DefaultInputProcessor createProcessor(
            Map<ActionId, List<InputBinding>> actionBindings,
            Map<AxisId, List<InputBinding>> axisBindings) {
        InputMap map = new InputMap(GAMEPLAY, actionBindings, axisBindings, false);
        var processor = new DefaultInputProcessor(Map.of(GAMEPLAY, map));
        processor.pushContext(GAMEPLAY);
        return processor;
    }
}
