package org.dynamisengine.input.core;

import org.dynamisengine.window.api.GamepadAxes;
import org.dynamisengine.window.api.GamepadButtons;
import org.dynamisengine.window.api.InputEvent;
import org.dynamisengine.window.api.InputEvent.InputAction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates randomized input event sequences for stress testing.
 *
 * Simulates:
 *   - Random key presses/releases
 *   - Random mouse button presses/releases
 *   - Random gamepad button presses/releases
 *   - Random gamepad axis values (including drift near zero)
 *   - Gamepad connect/disconnect churn
 *   - Event bursts (many events in one tick)
 */
final class ChaosInputSource {

    private final ThreadLocalRandom rng = ThreadLocalRandom.current();
    private final int maxGamepads;
    private final boolean[] gamepadConnected;

    ChaosInputSource(int maxGamepads) {
        this.maxGamepads = maxGamepads;
        this.gamepadConnected = new boolean[maxGamepads];
    }

    /**
     * Generate a random batch of events for one tick.
     *
     * @param eventCount approximate number of events to generate
     * @return list of random input events
     */
    List<InputEvent> generateTick(int eventCount) {
        List<InputEvent> events = new ArrayList<>(eventCount);
        for (int i = 0; i < eventCount; i++) {
            events.add(generateRandomEvent());
        }
        return events;
    }

    /**
     * Generate a connect/disconnect churn event.
     */
    InputEvent generateDeviceChurn() {
        int id = rng.nextInt(maxGamepads);
        if (gamepadConnected[id]) {
            gamepadConnected[id] = false;
            return new InputEvent.GamepadDisconnected(id);
        } else {
            gamepadConnected[id] = true;
            return new InputEvent.GamepadConnected(id, "ChaosGamepad-" + id);
        }
    }

    private InputEvent generateRandomEvent() {
        int type = rng.nextInt(6);
        return switch (type) {
            case 0 -> randomKeyEvent();
            case 1 -> randomMouseButtonEvent();
            case 2 -> randomGamepadButtonEvent();
            case 3 -> randomGamepadAxisEvent();
            case 4 -> randomCursorEvent();
            case 5 -> randomScrollEvent();
            default -> randomKeyEvent();
        };
    }

    private InputEvent randomKeyEvent() {
        int keyCode = rng.nextInt(32, 348); // GLFW key range
        InputAction action = rng.nextBoolean() ? InputAction.PRESS : InputAction.RELEASE;
        return new InputEvent.Key(keyCode, keyCode, action, 0);
    }

    private InputEvent randomMouseButtonEvent() {
        int button = rng.nextInt(0, 5);
        InputAction action = rng.nextBoolean() ? InputAction.PRESS : InputAction.RELEASE;
        return new InputEvent.MouseButton(button, action, 0);
    }

    private InputEvent randomGamepadButtonEvent() {
        int gamepadId = rng.nextInt(maxGamepads);
        int button = rng.nextInt(GamepadButtons.COUNT);
        InputAction action = rng.nextBoolean() ? InputAction.PRESS : InputAction.RELEASE;
        return new InputEvent.GamepadButton(gamepadId, button, action);
    }

    private InputEvent randomGamepadAxisEvent() {
        int gamepadId = rng.nextInt(maxGamepads);
        int axis = rng.nextInt(GamepadAxes.COUNT);
        // Mix of drift values near zero and real deflections
        float value = rng.nextBoolean()
                ? rng.nextFloat(-0.05f, 0.05f)  // drift
                : rng.nextFloat(-1.0f, 1.0f);   // real input
        return new InputEvent.GamepadAxis(gamepadId, axis, value);
    }

    private InputEvent randomCursorEvent() {
        return new InputEvent.CursorMoved(rng.nextDouble(0, 1920), rng.nextDouble(0, 1080));
    }

    private InputEvent randomScrollEvent() {
        return new InputEvent.Scroll(rng.nextDouble(-3, 3), rng.nextDouble(-3, 3));
    }
}
