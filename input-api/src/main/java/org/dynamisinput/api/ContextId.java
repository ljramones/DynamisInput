package org.dynamisinput.api;

import java.util.Objects;

public record ContextId(String value) {
    public ContextId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ContextId value must be non-blank");
        }
    }
}
