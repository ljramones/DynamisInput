package org.dynamisinput.api;

import org.dynamis.window.api.InputEvent;
import org.dynamisinput.api.frame.InputFrame;

public interface InputProcessor {
    void feed(InputEvent event, long tick);

    void pushContext(ContextId id);

    void popContext(ContextId id);

    InputFrame snapshot(long tick);
}
