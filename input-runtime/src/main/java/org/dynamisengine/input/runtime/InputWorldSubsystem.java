package org.dynamisengine.input.runtime;

import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.core.DefaultInputProcessor;
import org.dynamisengine.input.core.InputDeviceManager;
import org.dynamisengine.window.api.InputEvent;
import org.dynamisengine.window.api.WindowEvents;
import org.dynamisengine.worldengine.api.WorldContext;
import org.dynamisengine.worldengine.api.lifecycle.DynamisInitException;
import org.dynamisengine.worldengine.api.lifecycle.DynamisShutdownException;
import org.dynamisengine.worldengine.api.lifecycle.DynamisTickException;
import org.dynamisengine.worldengine.api.telemetry.SubsystemHealth;
import org.dynamisengine.worldengine.api.telemetry.WorldTelemetrySnapshot;
import org.dynamisengine.worldengine.runtime.subsystem.WorldSubsystem;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * WorldSubsystem adapter for DynamisInput.
 *
 * <p>Bridges raw window events into the input processing pipeline
 * each tick: feeds events to the processor, produces input frame
 * snapshots, and tracks device state.
 *
 * <p>Requires a supplier of {@link WindowEvents} — typically from
 * the window subsystem's {@code lastEvents()} method.
 */
public final class InputWorldSubsystem implements WorldSubsystem {

    private final Supplier<WindowEvents> eventSupplier;
    private final DefaultInputProcessor processor;
    private final InputDeviceManager deviceManager;
    private volatile boolean initialized = false;
    private volatile long lastTick = -1;
    private volatile InputFrame lastFrame;

    /**
     * @param eventSupplier provides window events each tick (e.g. windowSubsystem::lastEvents)
     * @param processor     the input processor for this application
     */
    public InputWorldSubsystem(Supplier<WindowEvents> eventSupplier,
                                DefaultInputProcessor processor) {
        this.eventSupplier = eventSupplier;
        this.processor = processor;
        this.deviceManager = new InputDeviceManager(4);
    }

    @Override public String name() { return WorldTelemetrySnapshot.INPUT; }
    @Override public Set<String> dependencies() { return Set.of("Window"); }

    @Override
    public void initialize(WorldContext context) throws DynamisInitException {
        initialized = true;
    }

    @Override public void start() {}

    @Override
    public void tick(long tick, float deltaSeconds) throws DynamisTickException {
        lastTick = tick;
        deviceManager.beginTick();
        WindowEvents events = eventSupplier.get();
        for (InputEvent event : events.inputEvents()) {
            deviceManager.processEvent(event);
            processor.feed(event, tick);
        }
        lastFrame = processor.snapshot(tick);
    }

    @Override public void stop() {}
    @Override public void shutdown() { initialized = false; }

    @Override
    public SubsystemHealth health() {
        if (!initialized) return SubsystemHealth.absent(name());
        return SubsystemHealth.healthy(name(), lastTick);
    }

    @Override
    public Optional<Object> captureTelemetry() {
        if (initialized) {
            deviceManager.recordSnapshot();
            return Optional.of(deviceManager.captureTelemetry(0));
        }
        return Optional.empty();
    }

    public InputFrame lastFrame() { return lastFrame; }
}
