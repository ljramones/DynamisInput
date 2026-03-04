package org.dynamisinput.api.bind;

public record KeyBinding(int keyCode, int requiredModifiers) implements InputBinding {
    public KeyBinding {
        if (keyCode < 0) {
            throw new IllegalArgumentException("keyCode must be >= 0");
        }
        if (requiredModifiers < 0) {
            throw new IllegalArgumentException("requiredModifiers must be >= 0");
        }
    }
}
