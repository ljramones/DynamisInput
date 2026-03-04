package org.dynamisinput.api.bind;

public record MouseButtonBinding(int button, int requiredModifiers) implements InputBinding {
    public MouseButtonBinding {
        if (button < 0) {
            throw new IllegalArgumentException("button must be >= 0");
        }
        if (requiredModifiers < 0) {
            throw new IllegalArgumentException("requiredModifiers must be >= 0");
        }
    }
}
