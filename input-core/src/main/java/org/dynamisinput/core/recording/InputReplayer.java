package org.dynamisinput.core.recording;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.dynamisinput.api.InputProcessor;
import org.dynamisinput.api.frame.InputFrame;

public final class InputReplayer {
    public Map<Long, InputFrame> replay(InputRecording recording, InputProcessor processor) {
        Objects.requireNonNull(recording, "recording");
        Objects.requireNonNull(processor, "processor");

        Map<Long, InputFrame> frames = new LinkedHashMap<>();
        for (InputRecording.EventAtTick eventAtTick : recording.entries()) {
            processor.feed(eventAtTick.event(), eventAtTick.tick());
            frames.put(eventAtTick.tick(), processor.snapshot(eventAtTick.tick()));
        }
        return frames;
    }
}
