package org.dynamisinput.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.dynamisinput.api.bind.KeyBinding;
import org.dynamisinput.api.bind.MouseDeltaBinding;
import org.dynamisinput.api.context.InputMap;
import org.junit.jupiter.api.Test;

class InputMapTest {
    @Test
    void storesActionAndAxisBindings() {
        var jump = new ActionId("Jump");
        var lookX = new AxisId("LookX");

        var map = new InputMap(
                new ContextId("Gameplay"),
                Map.of(jump, List.of(new KeyBinding(32, 0))),
                Map.of(lookX, List.of(new MouseDeltaBinding(MouseDeltaBinding.Component.X, 0.2f))),
                true
        );

        assertTrue(map.consuming());
        assertEquals(1, map.actionBindings().size());
        assertEquals(1, map.axisBindings().size());
        assertEquals(32, ((KeyBinding) map.actionBindings().get(jump).getFirst()).keyCode());
    }
}
