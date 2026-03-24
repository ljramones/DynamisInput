package org.dynamisengine.input.api.bind;

import org.dynamisengine.input.api.AxisId;

/**
 * Binds a pair of game axes to a gamepad stick with radial deadzone and shaping.
 *
 * Unlike {@link GamepadAxisBinding} which shapes each axis independently,
 * this binding computes a radial deadzone on the combined stick magnitude
 * before redistributing to X and Y. This is the AAA standard for movement sticks.
 *
 * @param xAxis      axis ID for the horizontal component
 * @param yAxis      axis ID for the vertical component
 * @param stickXAxis raw stick X axis (e.g., {@code GamepadAxes.LEFT_X})
 * @param stickYAxis raw stick Y axis (e.g., {@code GamepadAxes.LEFT_Y})
 * @param shaping    analog shaping (deadzone, curve, sensitivity)
 * @param deadzoneMode  RADIAL (recommended) or AXIAL
 * @param gamepadId  gamepad index, or -1 for any
 */
public record GamepadStickBinding(
        AxisId xAxis,
        AxisId yAxis,
        int stickXAxis,
        int stickYAxis,
        AnalogShaping shaping,
        DeadzoneMode deadzoneMode,
        int gamepadId) implements InputBinding {

    /** Bind left stick with radial deadzone and default shaping on any gamepad. */
    public static GamepadStickBinding leftStick(AxisId xAxis, AxisId yAxis) {
        return new GamepadStickBinding(xAxis, yAxis, 0, 1,
                AnalogShaping.DEFAULT, DeadzoneMode.RADIAL, -1);
    }

    /** Bind right stick with radial deadzone and default shaping on any gamepad. */
    public static GamepadStickBinding rightStick(AxisId xAxis, AxisId yAxis) {
        return new GamepadStickBinding(xAxis, yAxis, 2, 3,
                AnalogShaping.DEFAULT, DeadzoneMode.RADIAL, -1);
    }

    public GamepadStickBinding {
        if (xAxis == null || yAxis == null) throw new IllegalArgumentException("axis IDs must be non-null");
        if (shaping == null) throw new IllegalArgumentException("shaping must not be null");
        if (deadzoneMode == null) throw new IllegalArgumentException("deadzoneMode must not be null");
    }
}
