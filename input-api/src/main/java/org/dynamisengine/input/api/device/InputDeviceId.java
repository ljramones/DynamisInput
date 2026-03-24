package org.dynamisengine.input.api.device;

/**
 * Typed identity for an input device.
 *
 * Combines device type and platform-assigned index into a single identity.
 * Keyboard and mouse are singletons (index 0). Gamepads have per-port indices.
 *
 * @param type  device classification
 * @param index platform-assigned index (0 for keyboard/mouse, 0-N for gamepads)
 */
public record InputDeviceId(InputDeviceType type, int index) {

    /** The singleton keyboard device. */
    public static final InputDeviceId KEYBOARD = new InputDeviceId(InputDeviceType.KEYBOARD, 0);

    /** The singleton mouse device. */
    public static final InputDeviceId MOUSE = new InputDeviceId(InputDeviceType.MOUSE, 0);

    /** Create a gamepad device ID for the given platform index. */
    public static InputDeviceId gamepad(int index) {
        return new InputDeviceId(InputDeviceType.GAMEPAD, index);
    }

    public InputDeviceId {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (index < 0) throw new IllegalArgumentException("index must be >= 0");
    }
}
