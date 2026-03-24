package org.dynamisengine.input.api.bind;

/**
 * Marker for semantic bindings resolved from the window InputEvent stream.
 *
 * Sealed hierarchy covers all supported input sources:
 *   Keyboard:  KeyBinding, AxisComposite2D
 *   Mouse:     MouseButtonBinding, MouseDeltaBinding
 *   Gamepad:   GamepadButtonBinding, GamepadAxisBinding, GamepadStickBinding
 */
public sealed interface InputBinding permits
        AxisComposite2D,
        KeyBinding,
        MouseButtonBinding,
        MouseDeltaBinding,
        GamepadButtonBinding,
        GamepadAxisBinding,
        GamepadStickBinding {
}
