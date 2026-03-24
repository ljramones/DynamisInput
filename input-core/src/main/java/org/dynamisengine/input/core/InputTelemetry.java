package org.dynamisengine.input.core;

import org.dynamisengine.input.api.device.InputDeviceId;
import org.dynamisengine.input.api.device.InputDeviceInfo;
import org.dynamisengine.input.api.device.PlayerId;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of input subsystem telemetry.
 *
 * Captured at a point in time. Safe to read from any thread after capture.
 *
 * @param connectedDevices    currently connected devices
 * @param playerAssignments   player slot → list of assigned device IDs
 * @param totalEventsProcessed total raw events fed to the processor
 * @param eventsThisTick      events in the most recent tick
 * @param totalSnapshots       total InputFrame snapshots produced
 * @param activeContextCount   number of contexts on the stack
 * @param deviceEventCount     total device connect/disconnect/assign events
 */
public record InputTelemetry(
        List<InputDeviceInfo> connectedDevices,
        Map<PlayerId, List<InputDeviceId>> playerAssignments,
        long totalEventsProcessed,
        int eventsThisTick,
        long totalSnapshots,
        int activeContextCount,
        long deviceEventCount) {

    /**
     * Compact status line for profiler overlay.
     */
    public String statusLine() {
        int gamepads = (int) connectedDevices.stream()
                .filter(d -> d.id().type() == org.dynamisengine.input.api.device.InputDeviceType.GAMEPAD)
                .count();
        int assignedPlayers = playerAssignments.size();
        return String.format("Input | devices=%d (gamepads=%d) | players=%d | events=%d | snapshots=%d",
                connectedDevices.size(), gamepads, assignedPlayers,
                totalEventsProcessed, totalSnapshots);
    }
}
