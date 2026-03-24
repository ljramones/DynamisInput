package org.dynamisengine.input.api.bind;

/**
 * Deadzone computation mode for analog stick inputs.
 *
 * AXIAL: each axis (X, Y) has independent deadzones. Simple but allows
 *        diagonal drift and feels "sticky" near cardinal directions.
 *
 * RADIAL: deadzone is computed on the combined stick magnitude (sqrt(x^2 + y^2)).
 *         Feels natural for movement — small circular deadzone in the center,
 *         full 360-degree coverage. This is the AAA standard.
 */
public enum DeadzoneMode {
    /** Independent per-axis deadzone. */
    AXIAL,
    /** Radial magnitude-based deadzone (recommended). */
    RADIAL
}
