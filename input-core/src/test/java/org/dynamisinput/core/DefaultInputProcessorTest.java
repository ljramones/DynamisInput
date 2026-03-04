package org.dynamisinput.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.Test;

class DefaultInputProcessorTest {
    @Test
    void pressEdgeIsTrueOnlyForPressTick() {
        ActionId jump = new ActionId("Jump");
        ContextId gameplay = new ContextId("Gameplay");

        var processor = new DefaultInputProcessor(Map.of(
                gameplay,
                new InputMap(gameplay, Map.of(jump, List.of(new KeyBinding(32, 0))), Map.of(), false)
        ));
        processor.pushContext(gameplay);

        processor.feed(new InputEvent.Key(32, 0, InputAction.PRESS, 0), 10);

        assertTrue(processor.snapshot(10).pressed(jump));
        assertTrue(processor.snapshot(10).down(jump));
        assertFalse(processor.snapshot(11).pressed(jump));
        assertTrue(processor.snapshot(11).down(jump));
    }

    @Test
    void releaseEdgeIsTrueOnlyForReleaseTick() {
        ActionId jump = new ActionId("Jump");
        ContextId gameplay = new ContextId("Gameplay");

        var processor = new DefaultInputProcessor(Map.of(
                gameplay,
                new InputMap(gameplay, Map.of(jump, List.of(new KeyBinding(32, 0))), Map.of(), false)
        ));
        processor.pushContext(gameplay);

        processor.feed(new InputEvent.Key(32, 0, InputAction.PRESS, 0), 20);
        processor.feed(new InputEvent.Key(32, 0, InputAction.RELEASE, 0), 21);

        assertFalse(processor.snapshot(20).released(jump));
        assertTrue(processor.snapshot(21).released(jump));
        assertFalse(processor.snapshot(22).released(jump));
        assertFalse(processor.snapshot(22).down(jump));
    }

    @Test
    void consumingContextBlocksLowerContexts() {
        ActionId fire = new ActionId("Fire");
        ContextId gameplay = new ContextId("Gameplay");
        ContextId menu = new ContextId("Menu");

        var processor = new DefaultInputProcessor(Map.of(
                gameplay,
                new InputMap(gameplay, Map.of(fire, List.of(new KeyBinding(70, 0))), Map.of(), false),
                menu,
                new InputMap(menu, Map.of(), Map.of(), true)
        ));
        processor.pushContext(gameplay);
        processor.pushContext(menu);

        processor.feed(new InputEvent.Key(70, 0, InputAction.PRESS, 0), 30);

        assertFalse(processor.snapshot(30).pressed(fire));
        processor.popContext(menu);
        assertTrue(processor.snapshot(31).down(fire));
    }

    @Test
    void recordReplayProducesIdenticalFrameHashes() {
        ActionId jump = new ActionId("Jump");
        ContextId gameplay = new ContextId("Gameplay");

        InputMap gameplayMap = new InputMap(gameplay, Map.of(jump, List.of(new KeyBinding(32, 0))), Map.of(), false);

        var recorder = new org.dynamisinput.core.recording.InputRecorder();
        recorder.record(new InputEvent.Key(32, 0, InputAction.PRESS, 0), 1);
        recorder.record(new InputEvent.Key(32, 0, InputAction.RELEASE, 0), 2);

        var recording = recorder.toRecording();

        var original = new DefaultInputProcessor(Map.of(gameplay, gameplayMap));
        original.pushContext(gameplay);

        var replayed = new DefaultInputProcessor(Map.of(gameplay, gameplayMap));
        replayed.pushContext(gameplay);

        var originalHashes = recording.entries().stream().map(eventAtTick -> {
            original.feed(eventAtTick.event(), eventAtTick.tick());
            return frameHash(original.snapshot(eventAtTick.tick()), jump);
        }).toList();

        var replayer = new org.dynamisinput.core.recording.InputReplayer();
        var replayFrames = replayer.replay(recording, replayed);
        var replayHashes = replayFrames.values().stream()
                .map(frame -> frameHash(frame, jump))
                .toList();

        assertEquals(originalHashes, replayHashes);
    }

    private static int frameHash(org.dynamisinput.api.frame.InputFrame frame, ActionId action) {
        return java.util.Objects.hash(frame.tick(), frame.pressed(action), frame.released(action), frame.down(action));
    }
}
