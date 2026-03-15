package org.dynamisengine.input.core.recording;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.dynamisengine.window.api.InputEvent;
import org.dynamisengine.input.core.recording.InputRecording.EventAtTick;

public final class InputRecorder {
    private final List<EventAtTick> entries = new ArrayList<>();

    public void record(InputEvent event, long tick) {
        entries.add(new EventAtTick(tick, Objects.requireNonNull(event, "event")));
    }

    public InputRecording toRecording() {
        return new InputRecording(entries);
    }
}
