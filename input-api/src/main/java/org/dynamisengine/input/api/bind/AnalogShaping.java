package org.dynamisengine.input.api.bind;

/**
 * Analog input shaping configuration for sticks and triggers.
 *
 * Applied after raw platform values are received, before mapping to game axes.
 * Controls deadzone, response curve, sensitivity, and inversion.
 *
 * PROCESSING ORDER:
 *   1. Raw value from platform [-1.0, 1.0] for sticks, [0.0, 1.0] for triggers
 *   2. Deadzone applied (inner: values below threshold → 0, outer: values above → 1.0)
 *   3. Remap to [0.0, 1.0] range within the live zone
 *   4. Response curve applied (shapes feel)
 *   5. Multiply by sensitivity
 *   6. Apply inversion if enabled
 *   7. Result written to axis value
 *
 * @param innerDeadzone  values below this magnitude are zeroed (0.0-1.0, typical: 0.1-0.2)
 * @param outerDeadzone  values above this magnitude are clamped to 1.0 (0.0-1.0, typical: 0.9-0.95)
 * @param responseCurve  curve applied to shaped value
 * @param sensitivity    final multiplier (1.0 = normal)
 * @param inverted       if true, output is negated
 */
public record AnalogShaping(
        float innerDeadzone,
        float outerDeadzone,
        ResponseCurve responseCurve,
        float sensitivity,
        boolean inverted) {

    /** Default shaping: 15% inner deadzone, 95% outer, linear, sensitivity 1.0. */
    public static final AnalogShaping DEFAULT =
            new AnalogShaping(0.15f, 0.95f, ResponseCurve.LINEAR, 1.0f, false);

    /** Precision aiming: 10% inner, 90% outer, quadratic curve. */
    public static final AnalogShaping PRECISION =
            new AnalogShaping(0.10f, 0.90f, ResponseCurve.QUADRATIC, 1.0f, false);

    /** Aggressive: 5% inner, 98% outer, linear, high sensitivity. */
    public static final AnalogShaping AGGRESSIVE =
            new AnalogShaping(0.05f, 0.98f, ResponseCurve.LINEAR, 2.0f, false);

    public AnalogShaping {
        if (innerDeadzone < 0 || innerDeadzone > 1)
            throw new IllegalArgumentException("innerDeadzone must be in [0, 1]");
        if (outerDeadzone < 0 || outerDeadzone > 1)
            throw new IllegalArgumentException("outerDeadzone must be in [0, 1]");
        if (innerDeadzone >= outerDeadzone)
            throw new IllegalArgumentException("innerDeadzone must be < outerDeadzone");
        if (!Float.isFinite(sensitivity))
            throw new IllegalArgumentException("sensitivity must be finite");
        if (responseCurve == null)
            throw new IllegalArgumentException("responseCurve must not be null");
    }

    /**
     * Apply shaping to a single axis value.
     * Deterministic, zero-allocation.
     *
     * @param raw raw axis value (typically [-1.0, 1.0])
     * @return shaped value
     */
    public float shape(float raw) {
        float sign = raw >= 0 ? 1.0f : -1.0f;
        float magnitude = Math.abs(raw);

        // Deadzone
        if (magnitude < innerDeadzone) return 0.0f;
        if (magnitude > outerDeadzone) magnitude = 1.0f;
        else {
            // Remap [innerDeadzone, outerDeadzone] → [0.0, 1.0]
            float range = outerDeadzone - innerDeadzone;
            magnitude = (magnitude - innerDeadzone) / range;
        }

        // Response curve
        magnitude = responseCurve.apply(magnitude);

        // Sensitivity and inversion
        float result = magnitude * sensitivity * sign;
        return inverted ? -result : result;
    }

    /**
     * Apply radial deadzone to a 2D stick (X, Y pair).
     * Computes magnitude from both axes, applies deadzone on the combined magnitude,
     * then redistributes to X and Y preserving direction.
     * Deterministic, zero-allocation.
     *
     * @param rawX raw X axis value [-1.0, 1.0]
     * @param rawY raw Y axis value [-1.0, 1.0]
     * @return shaped [x, y] as a 2-element float array
     */
    public float[] shapeRadial(float rawX, float rawY) {
        float magnitude = (float) Math.sqrt(rawX * rawX + rawY * rawY);
        if (magnitude < 0.0001f) return new float[]{0.0f, 0.0f};

        // Normalize direction
        float nx = rawX / magnitude;
        float ny = rawY / magnitude;

        // Clamp magnitude to [0, 1] — stick can slightly exceed 1.0 in corners
        if (magnitude > 1.0f) magnitude = 1.0f;

        // Deadzone on magnitude
        if (magnitude < innerDeadzone) return new float[]{0.0f, 0.0f};
        float shaped;
        if (magnitude > outerDeadzone) shaped = 1.0f;
        else {
            float range = outerDeadzone - innerDeadzone;
            shaped = (magnitude - innerDeadzone) / range;
        }

        // Response curve
        shaped = responseCurve.apply(shaped);

        // Sensitivity and redistribute to axes
        float sx = nx * shaped * sensitivity;
        float sy = ny * shaped * sensitivity;

        return new float[]{
                inverted ? -sx : sx,
                inverted ? -sy : sy
        };
    }
}
