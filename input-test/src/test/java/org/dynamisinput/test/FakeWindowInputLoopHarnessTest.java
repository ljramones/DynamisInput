package org.dynamisinput.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.dynamis.window.api.InputEvent;
import org.dynamis.window.api.InputEvent.InputAction;
import org.dynamisinput.api.ActionId;
import org.dynamisinput.api.ContextId;
import org.dynamisinput.api.bind.KeyBinding;
import org.dynamisinput.api.context.InputMap;
import org.dynamisinput.runtime.InputRuntime;
import org.junit.jupiter.api.Test;

class FakeWindowInputLoopHarnessTest {
    @Test
    void fakeWindowEventsProduceDeterministicFramesAcrossTicks() {
        ActionId jump = new ActionId("Jump");
        ContextId gameplay = new ContextId("Gameplay");

        InputMap gameplayMap = new InputMap(
                gameplay,
                Map.of(jump, List.of(new KeyBinding(32, 0))),
                Map.of(),
                false
        );

        InputRuntime runtime = InputRuntime.builder()
                .initialMap(gameplayMap)
                .initialContext(gameplay)
                .build();

        FakeWindowInputLoopHarness harness = new FakeWindowInputLoopHarness(runtime);

        var tick1 = harness.runTick(1, window -> window.pushInputEvent(new InputEvent.Key(32, 0, InputAction.PRESS, 0)));
        var tick2 = harness.runTick(2, window -> {
        });
        var tick3 = harness.runTick(3, window -> window.pushInputEvent(new InputEvent.Key(32, 0, InputAction.RELEASE, 0)));
        var tick4 = harness.runTick(4, window -> {
        });

        assertTrue(tick1.pressed(jump));
        assertTrue(tick1.down(jump));

        assertFalse(tick2.pressed(jump));
        assertTrue(tick2.down(jump));

        assertTrue(tick3.released(jump));
        assertFalse(tick3.down(jump));

        assertFalse(tick4.pressed(jump));
        assertFalse(tick4.released(jump));
        assertFalse(tick4.down(jump));
    }
}
