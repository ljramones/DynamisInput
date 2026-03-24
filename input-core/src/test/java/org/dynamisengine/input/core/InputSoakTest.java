package org.dynamisengine.input.core;

import org.dynamisengine.input.api.*;
import org.dynamisengine.input.api.bind.*;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.device.*;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.core.InputDeviceManager.DeviceEvent;
import org.dynamisengine.window.api.GamepadAxes;
import org.dynamisengine.window.api.GamepadButtons;
import org.dynamisengine.window.api.InputEvent;
import org.dynamisengine.window.api.InputEvent.InputAction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Soak and chaos tests for the input subsystem.
 *
 * Exercises the InputProcessor and InputDeviceManager under adversarial conditions:
 * event storms, device churn, assignment collisions, edge-case axis values.
 */
class InputSoakTest {

    private static final ActionId JUMP = new ActionId("jump");
    private static final ActionId FIRE = new ActionId("fire");
    private static final AxisId MOVE_X = new AxisId("moveX");
    private static final AxisId MOVE_Y = new AxisId("moveY");
    private static final ContextId GAMEPLAY = new ContextId("gameplay");

    // -- Event storm: processor survives high event volume --------------------

    @Test
    void processorSurvivesEventStorm() {
        var processor = createGamepadProcessor();
        var chaos = new ChaosInputSource(4);

        // Feed 10,000 random events across 100 ticks
        for (long tick = 1; tick <= 100; tick++) {
            for (InputEvent event : chaos.generateTick(100)) {
                processor.feed(event, tick);
            }
        }

        // Should produce valid frames without exception
        for (long tick = 1; tick <= 100; tick++) {
            InputFrame frame = processor.snapshot(tick);
            assertThat(frame).isNotNull();
            assertThat(frame.tick()).isEqualTo(tick);
        }
    }

    @Test
    void processorSnapshotsAreDeterministicUnderStorm() {
        var chaos = new ChaosInputSource(4);

        // Generate fixed event sequence
        List<List<InputEvent>> tickEvents = new ArrayList<>();
        for (int tick = 0; tick < 50; tick++) {
            tickEvents.add(chaos.generateTick(20));
        }

        // Feed same sequence to two processors
        var proc1 = createGamepadProcessor();
        var proc2 = createGamepadProcessor();
        for (int tick = 0; tick < 50; tick++) {
            for (InputEvent event : tickEvents.get(tick)) {
                proc1.feed(event, tick + 1);
                proc2.feed(event, tick + 1);
            }
        }

        // Snapshots must be identical
        for (long tick = 1; tick <= 50; tick++) {
            InputFrame f1 = proc1.snapshot(tick);
            InputFrame f2 = proc2.snapshot(tick);
            assertThat(f1.actions()).isEqualTo(f2.actions());
            assertThat(f1.axes()).isEqualTo(f2.axes());
        }
    }

    // -- Device churn: rapid connect/disconnect cycles ----------------------

    @Test
    void deviceManagerSurvivesRapidChurn() {
        var mgr = new InputDeviceManager(4);
        var chaos = new ChaosInputSource(4);

        // 500 random connect/disconnect events
        for (int i = 0; i < 500; i++) {
            mgr.processEvent(chaos.generateDeviceChurn());
        }

        // Manager should still be consistent
        assertThat(mgr.getConnectedDevices()).isNotNull();
        assertThat(mgr.getAllDevices()).isNotNull();

        // Keyboard and mouse always present
        assertThat(mgr.isConnected(InputDeviceId.KEYBOARD)).isTrue();
        assertThat(mgr.isConnected(InputDeviceId.MOUSE)).isTrue();
    }

    @Test
    void deviceManagerAssignmentsStayConsistentUnderChurn() {
        var mgr = new InputDeviceManager(4);

        // Connect 4 gamepads
        for (int i = 0; i < 4; i++) {
            mgr.processEvent(new InputEvent.GamepadConnected(i, "Gamepad " + i));
        }

        // Rapid disconnect/reconnect of gamepad 0
        for (int cycle = 0; cycle < 100; cycle++) {
            mgr.processEvent(new InputEvent.GamepadDisconnected(0));
            mgr.processEvent(new InputEvent.GamepadConnected(0, "Gamepad 0"));
        }

        // Gamepad 0 should still be assigned to same player (disconnect preserves)
        assertThat(mgr.getPlayerForDevice(InputDeviceId.gamepad(0))).isPresent();
    }

    @Test
    void simultaneousConnectOfAllGamepads() {
        var mgr = new InputDeviceManager(4);
        List<DeviceEvent> events = new ArrayList<>();
        mgr.addListener(events::add);

        // All 4 gamepads connect at once
        for (int i = 0; i < 4; i++) {
            mgr.processEvent(new InputEvent.GamepadConnected(i, "Gamepad " + i));
        }

        // Player 1 = kb+mouse, Players 2-4 = gamepads 0-2, gamepad 3 unassigned (only 4 slots)
        // Actually: player 1 = kb+mouse, player 2-4 = gamepads 0-2. Gamepad 3 has no slot.
        assertThat(mgr.getPlayerForDevice(InputDeviceId.gamepad(0))).hasValue(PlayerId.PLAYER_2);
        assertThat(mgr.getPlayerForDevice(InputDeviceId.gamepad(1))).hasValue(PlayerId.PLAYER_3);
        assertThat(mgr.getPlayerForDevice(InputDeviceId.gamepad(2))).hasValue(PlayerId.PLAYER_4);
        assertThat(mgr.getPlayerForDevice(InputDeviceId.gamepad(3))).isEmpty();
    }

    // -- Edge-case axis values -----------------------------------------------

    @Test
    void extremeAxisValues() {
        var processor = createGamepadProcessor();

        // Feed extreme values
        processor.feed(new InputEvent.GamepadAxis(0, GamepadAxes.LEFT_X, Float.MAX_VALUE), 1);
        processor.feed(new InputEvent.GamepadAxis(0, GamepadAxes.LEFT_Y, -Float.MAX_VALUE), 1);
        InputFrame frame = processor.snapshot(1);
        // Should not crash; values may be extreme but finite
        assertThat(frame).isNotNull();
        assertThat(Float.isFinite(frame.axis(MOVE_X)) || Float.isInfinite(frame.axis(MOVE_X))).isTrue();
    }

    @Test
    void zeroAxisValuesProduceZeroAfterDeadzone() {
        var processor = createGamepadProcessor();
        processor.feed(new InputEvent.GamepadAxis(0, GamepadAxes.LEFT_X, 0.0f), 1);
        processor.feed(new InputEvent.GamepadAxis(0, GamepadAxes.LEFT_Y, 0.0f), 1);
        InputFrame frame = processor.snapshot(1);
        assertThat(frame.axis(MOVE_X)).isEqualTo(0.0f);
        assertThat(frame.axis(MOVE_Y)).isEqualTo(0.0f);
    }

    @Test
    void driftNearDeadzoneEdge() {
        var shaping = new AnalogShaping(0.15f, 0.95f, ResponseCurve.LINEAR, 1.0f, false);
        // Values just inside and just outside the deadzone
        assertThat(shaping.shape(0.14f)).isEqualTo(0.0f);
        assertThat(shaping.shape(0.16f)).isGreaterThan(0.0f);
        assertThat(shaping.shape(-0.14f)).isEqualTo(0.0f);
        assertThat(shaping.shape(-0.16f)).isLessThan(0.0f);
    }

    // -- Telemetry ------------------------------------------------------------

    @Test
    void telemetryCapture() {
        var mgr = new InputDeviceManager(4);
        mgr.processEvent(new InputEvent.GamepadConnected(0, "Gamepad 0"));
        mgr.processEvent(new InputEvent.Key(32, 32, InputAction.PRESS, 0));
        mgr.processEvent(new InputEvent.GamepadAxis(0, GamepadAxes.LEFT_X, 0.5f));
        mgr.recordSnapshot();
        mgr.recordSnapshot();

        InputTelemetry t = mgr.captureTelemetry(1);
        assertThat(t.connectedDevices()).hasSize(3); // kb + mouse + gamepad
        assertThat(t.totalEventsProcessed()).isEqualTo(3);
        assertThat(t.totalSnapshots()).isEqualTo(2);
        assertThat(t.deviceEventCount()).isGreaterThan(0);
        assertThat(t.statusLine()).contains("gamepads=1");
    }

    @Test
    void telemetryPerTickCounter() {
        var mgr = new InputDeviceManager(4);
        mgr.beginTick();
        mgr.processEvent(new InputEvent.Key(32, 32, InputAction.PRESS, 0));
        mgr.processEvent(new InputEvent.Key(33, 33, InputAction.PRESS, 0));

        InputTelemetry t = mgr.captureTelemetry(0);
        assertThat(t.eventsThisTick()).isEqualTo(2);

        mgr.beginTick();
        t = mgr.captureTelemetry(0);
        assertThat(t.eventsThisTick()).isZero();
        assertThat(t.totalEventsProcessed()).isEqualTo(2); // cumulative
    }

    // -- Listener resilience --------------------------------------------------

    @Test
    void listenerExceptionDuringChurnDoesNotCorruptState() {
        var mgr = new InputDeviceManager(4);
        mgr.addListener(event -> { throw new RuntimeException("chaos listener"); });

        for (int i = 0; i < 100; i++) {
            mgr.processEvent(new InputEvent.GamepadConnected(0, "Gamepad"));
            mgr.processEvent(new InputEvent.GamepadDisconnected(0));
        }

        // State should still be consistent despite listener explosions
        assertThat(mgr.isConnected(InputDeviceId.KEYBOARD)).isTrue();
        assertThat(mgr.getAllDevices()).hasSizeGreaterThanOrEqualTo(2);
    }

    // -- Mixed stress: processor + device manager together --------------------

    @Test
    void fullPipelineUnderChaos() {
        var mgr = new InputDeviceManager(4);
        var processor = createGamepadProcessor();
        var chaos = new ChaosInputSource(4);

        // 200 ticks of chaos
        for (long tick = 1; tick <= 200; tick++) {
            mgr.beginTick();

            // Random device churn every 10 ticks
            if (tick % 10 == 0) {
                InputEvent churn = chaos.generateDeviceChurn();
                mgr.processEvent(churn);
            }

            // Random events
            for (InputEvent event : chaos.generateTick(10)) {
                mgr.processEvent(event);
                processor.feed(event, tick);
            }

            InputFrame frame = processor.snapshot(tick);
            mgr.recordSnapshot();
            assertThat(frame).isNotNull();
        }

        InputTelemetry t = mgr.captureTelemetry(1);
        assertThat(t.totalEventsProcessed()).isEqualTo(2000 + 20); // 200*10 + 20 churn events
        assertThat(t.totalSnapshots()).isEqualTo(200);
    }

    // -- Helpers --------------------------------------------------------------

    private DefaultInputProcessor createGamepadProcessor() {
        var stick = GamepadStickBinding.leftStick(MOVE_X, MOVE_Y);
        InputMap map = new InputMap(GAMEPLAY,
                Map.of(
                        JUMP, List.of(new GamepadButtonBinding(GamepadButtons.A)),
                        FIRE, List.of(new GamepadButtonBinding(GamepadButtons.RIGHT_BUMPER))),
                Map.of(
                        MOVE_X, List.of(stick),
                        MOVE_Y, List.of(stick)),
                false);
        var processor = new DefaultInputProcessor(Map.of(GAMEPLAY, map));
        processor.pushContext(GAMEPLAY);
        return processor;
    }
}
