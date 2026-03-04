package org.dynamisinput.api.bind;

/**
 * Marker for semantic bindings resolved from the window InputEvent stream.
 */
public sealed interface InputBinding permits AxisComposite2D, KeyBinding, MouseButtonBinding, MouseDeltaBinding {
}
