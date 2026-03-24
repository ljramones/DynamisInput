package org.dynamisengine.input.api.bind;

/**
 * Binds a game action to a gamepad button.
 *
 * @param button     button identifier (see {@code GamepadButtons} constants)
 * @param gamepadId  gamepad index to match, or -1 for any gamepad
 */
public record GamepadButtonBinding(int button, int gamepadId) implements InputBinding {

    /** Bind to a specific button on any connected gamepad. */
    public GamepadButtonBinding(int button) {
        this(button, -1);
    }
}
