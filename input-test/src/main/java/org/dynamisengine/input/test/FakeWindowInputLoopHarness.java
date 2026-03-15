package org.dynamisengine.input.test;

import java.util.Objects;
import java.util.function.Consumer;
import org.dynamisengine.window.api.Window;
import org.dynamisengine.window.api.WindowConfig;
import org.dynamisengine.window.test.FakeWindow;
import org.dynamisengine.window.test.FakeWindowSystem;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.runtime.InputRuntime;

public final class FakeWindowInputLoopHarness {
    private final FakeWindow fakeWindow;
    private final InputRuntime runtime;

    public FakeWindowInputLoopHarness(InputRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        Window window = new FakeWindowSystem().create(WindowConfig.defaults());
        this.fakeWindow = (FakeWindow) window;
    }

    public InputFrame runTick(long tick, Consumer<FakeWindow> enqueue) {
        enqueue.accept(fakeWindow);
        fakeWindow.pollEvents().inputEvents().forEach(event -> runtime.feed(event, tick));
        return runtime.frame(tick);
    }
}
