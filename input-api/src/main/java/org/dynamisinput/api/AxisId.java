package org.dynamisinput.api;

import java.util.Objects;

public record AxisId(String value) {
    public AxisId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("AxisId value must be non-blank");
        }
    }
}
