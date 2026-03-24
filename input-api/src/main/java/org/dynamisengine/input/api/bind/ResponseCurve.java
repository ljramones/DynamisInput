package org.dynamisengine.input.api.bind;

/**
 * Response curve applied to analog input after deadzone processing.
 *
 * The input value (post-deadzone, in [0.0, 1.0]) is transformed by the curve
 * before being multiplied by sensitivity. This shapes how the stick "feels":
 *
 * LINEAR:    output = input.           Uniform response. Good for menus.
 * QUADRATIC: output = input^2.         Gentle near center, fast at extremes. Good for aiming.
 * CUBIC:     output = input^3.         Very gentle center. Pro-level precision aiming.
 * SMOOTH:    output = 3*input^2 - 2*input^3 (smoothstep). Soft start and end. Natural feel.
 */
public enum ResponseCurve {
    LINEAR,
    QUADRATIC,
    CUBIC,
    SMOOTH;

    /**
     * Apply this curve to a value in [0.0, 1.0].
     * Deterministic — no allocation, no branching on hot path (switch is compiled to tableswitch).
     */
    public float apply(float t) {
        return switch (this) {
            case LINEAR -> t;
            case QUADRATIC -> t * t;
            case CUBIC -> t * t * t;
            case SMOOTH -> t * t * (3.0f - 2.0f * t);
        };
    }
}
