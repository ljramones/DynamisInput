package org.dynamisinput.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IdentityTypeTest {
    @Test
    void actionIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new ActionId(" "));
        assertDoesNotThrow(() -> new ActionId("Jump"));
    }

    @Test
    void axisIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new AxisId("\t"));
        assertDoesNotThrow(() -> new AxisId("MoveX"));
    }

    @Test
    void contextIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new ContextId(""));
        assertDoesNotThrow(() -> new ContextId("Gameplay"));
    }
}
