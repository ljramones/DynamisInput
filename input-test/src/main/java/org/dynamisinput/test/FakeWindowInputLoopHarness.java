package org.dynamisinput.test;

import java.util.Objects;
import java.util.function.Consumer;
import org.dynamis.window.api.Window;
import org.dynamis.window.api.WindowConfig;
import org.dynamis.window.test.FakeWindow;
import org.dynamis.window.test.FakeWindowSystem;
import org.dynamisinput.api.frame.InputFrame;
import org.dynamisinput.runtime.InputRuntime;

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
