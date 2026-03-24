package org.dynamisengine.input.api.gesture;

/**
 * Typed identity for a named gesture (e.g., "dodge", "heavyAttack", "konamiCode").
 *
 * @param value the gesture name (must not be blank)
 */
public record GestureId(String value) {

    public GestureId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("GestureId must not be blank");
    }
}
