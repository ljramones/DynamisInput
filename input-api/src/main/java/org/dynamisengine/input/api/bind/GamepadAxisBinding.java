package org.dynamisengine.input.api.bind;

/**
 * Binds a game axis to a gamepad analog axis (stick or trigger).
 *
 * @param axis       axis identifier (see {@code GamepadAxes} constants)
 * @param shaping    analog shaping configuration (deadzone, curve, sensitivity)
 * @param gamepadId  gamepad index to match, or -1 for any gamepad
 */
public record GamepadAxisBinding(int axis, AnalogShaping shaping, int gamepadId) implements InputBinding {

    /** Bind with default shaping on any gamepad. */
    public GamepadAxisBinding(int axis) {
        this(axis, AnalogShaping.DEFAULT, -1);
    }

    /** Bind with custom shaping on any gamepad. */
    public GamepadAxisBinding(int axis, AnalogShaping shaping) {
        this(axis, shaping, -1);
    }

    public GamepadAxisBinding {
        if (shaping == null) throw new IllegalArgumentException("shaping must not be null");
    }
}
