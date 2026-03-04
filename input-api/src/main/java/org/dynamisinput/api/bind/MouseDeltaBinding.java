package org.dynamisinput.api.bind;

public record MouseDeltaBinding(Component component, float sensitivity) implements InputBinding {
    public enum Component { X, Y }

    public MouseDeltaBinding {
        if (component == null) {
            throw new IllegalArgumentException("component must be non-null");
        }
        if (!Float.isFinite(sensitivity)) {
            throw new IllegalArgumentException("sensitivity must be finite");
        }
    }
}
