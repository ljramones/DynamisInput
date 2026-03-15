package org.dynamisengine.input.core.recording;

import java.util.List;
import java.util.Objects;
import org.dynamisengine.window.api.InputEvent;

public record InputRecording(List<EventAtTick> entries) {
    public record EventAtTick(long tick, InputEvent event) {
        public EventAtTick {
            Objects.requireNonNull(event, "event");
        }
    }

    public InputRecording {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }
}
