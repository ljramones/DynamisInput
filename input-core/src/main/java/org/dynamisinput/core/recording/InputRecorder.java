package org.dynamisinput.core.recording;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.dynamis.window.api.InputEvent;
import org.dynamisinput.core.recording.InputRecording.EventAtTick;

public final class InputRecorder {
    private final List<EventAtTick> entries = new ArrayList<>();

    public void record(InputEvent event, long tick) {
        entries.add(new EventAtTick(tick, Objects.requireNonNull(event, "event")));
    }

    public InputRecording toRecording() {
        return new InputRecording(entries);
    }
}
