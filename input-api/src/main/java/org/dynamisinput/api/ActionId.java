package org.dynamisinput.api;

import java.util.Objects;

public record ActionId(String value) {
    public ActionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ActionId value must be non-blank");
        }
    }
}
