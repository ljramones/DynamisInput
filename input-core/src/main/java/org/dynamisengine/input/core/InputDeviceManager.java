package org.dynamisengine.input.core;

import org.dynamisengine.input.api.device.*;
import org.dynamisengine.window.api.InputEvent;

import java.util.*;

/**
 * Tracks connected input devices and manages player slot assignments.
 *
 * Consumes {@link InputEvent.GamepadConnected} and {@link InputEvent.GamepadDisconnected}
 * events to maintain a live device registry. Keyboard and mouse are always present.
 *
 * ASSIGNMENT POLICY:
 *   Default: first-connected-wins. The first gamepad connected is assigned to the
 *   lowest unassigned player slot. Manual reassignment is supported.
 *
 * DISCONNECT POLICY:
 *   When a gamepad disconnects, its player slot assignment is preserved (not cleared).
 *   If the same gamepad reconnects, it regains its slot. This prevents losing assignment
 *   during brief USB disconnects. Call {@link #clearAssignment(PlayerId)} to explicitly
 *   release a slot.
 *
 * THREAD SAFETY:
 *   All methods are called from the engine lifecycle or input processing thread.
 *   Not designed for concurrent access from multiple threads.
 */
public final class InputDeviceManager {

    /** All known devices, keyed by device ID. */
    private final Map<InputDeviceId, InputDeviceInfo> devices = new LinkedHashMap<>();

    /** Player slot → assigned device. Null if slot is unassigned. */
    private final Map<PlayerId, InputDeviceId> playerAssignments = new LinkedHashMap<>();

    /** Device → player slot reverse lookup. */
    private final Map<InputDeviceId, PlayerId> deviceToPlayer = new HashMap<>();

    /** Maximum number of player slots. */
    private final int maxPlayers;

    /** Listeners notified on device and assignment changes. */
    private final List<DeviceEventListener> listeners = new ArrayList<>();

    // -- Telemetry counters ---------------------------------------------------

    private long totalEventsProcessed = 0L;
    private int eventsThisTick = 0;
    private long totalSnapshots = 0L;
    private long deviceEventCount = 0L;

    /**
     * @param maxPlayers maximum number of player slots (typically 4)
     */
    public InputDeviceManager(int maxPlayers) {
        if (maxPlayers < 1) throw new IllegalArgumentException("maxPlayers must be >= 1");
        this.maxPlayers = maxPlayers;

        // Keyboard and mouse are always present
        devices.put(InputDeviceId.KEYBOARD,
                new InputDeviceInfo(InputDeviceId.KEYBOARD, "Keyboard", true));
        devices.put(InputDeviceId.MOUSE,
                new InputDeviceInfo(InputDeviceId.MOUSE, "Mouse", true));

        // Default: keyboard + mouse assigned to player 1
        assignDevice(PlayerId.PLAYER_1, InputDeviceId.KEYBOARD);
        assignDevice(PlayerId.PLAYER_1, InputDeviceId.MOUSE);
    }

    // -- Device tracking ------------------------------------------------------

    /**
     * Process a raw input event for device tracking.
     * Call this for every event before passing to the InputProcessor.
     */
    public void processEvent(InputEvent event) {
        totalEventsProcessed++;
        eventsThisTick++;
        if (event instanceof InputEvent.GamepadConnected gc) {
            onGamepadConnected(gc.gamepadId(), gc.name());
        } else if (event instanceof InputEvent.GamepadDisconnected gd) {
            onGamepadDisconnected(gd.gamepadId());
        }
    }

    /** Call at the start of each tick to reset per-tick counters. */
    public void beginTick() {
        eventsThisTick = 0;
    }

    /** Call after producing a snapshot to increment the counter. */
    public void recordSnapshot() {
        totalSnapshots++;
    }

    private void onGamepadConnected(int gamepadId, String name) {
        InputDeviceId id = InputDeviceId.gamepad(gamepadId);
        InputDeviceInfo info = new InputDeviceInfo(id, name, true);
        InputDeviceInfo previous = devices.put(id, info);

        if (previous == null || !previous.connected()) {
            // Fire Connected first, then auto-assign
            fireEvent(new DeviceEvent.Connected(info));
            if (!deviceToPlayer.containsKey(id)) {
                PlayerId slot = findFirstUnassignedSlot();
                if (slot != null) {
                    assignDevice(slot, id);
                }
            }
        }
    }

    private void onGamepadDisconnected(int gamepadId) {
        InputDeviceId id = InputDeviceId.gamepad(gamepadId);
        InputDeviceInfo previous = devices.get(id);
        if (previous != null) {
            devices.put(id, new InputDeviceInfo(id, previous.displayName(), false));
            fireEvent(new DeviceEvent.Disconnected(id));
            // Assignment preserved — see disconnect policy in class doc
        }
    }

    // -- Player assignment ----------------------------------------------------

    /**
     * Assign a device to a player slot.
     * A player can have multiple devices (e.g., keyboard + mouse).
     * A device can only be assigned to one player at a time.
     */
    public void assignDevice(PlayerId player, InputDeviceId device) {
        if (player.index() >= maxPlayers) {
            throw new IllegalArgumentException("Player slot " + player.index() +
                    " exceeds maxPlayers=" + maxPlayers);
        }

        // Remove from previous player if reassigning
        PlayerId previousPlayer = deviceToPlayer.get(device);
        if (previousPlayer != null && !previousPlayer.equals(player)) {
            // Device was on another player — that's a reassignment
        }

        deviceToPlayer.put(device, player);
        playerAssignments.put(player, device); // last device wins as "primary"
        fireEvent(new DeviceEvent.Assigned(player, device));
    }

    /**
     * Clear all device assignments for a player slot.
     */
    public void clearAssignment(PlayerId player) {
        deviceToPlayer.entrySet().removeIf(e -> e.getValue().equals(player));
        playerAssignments.remove(player);
        fireEvent(new DeviceEvent.Unassigned(player));
    }

    /**
     * Get the player slot a device is assigned to, or empty if unassigned.
     */
    public Optional<PlayerId> getPlayerForDevice(InputDeviceId device) {
        return Optional.ofNullable(deviceToPlayer.get(device));
    }

    /**
     * Get all devices assigned to a player slot.
     */
    public List<InputDeviceId> getDevicesForPlayer(PlayerId player) {
        List<InputDeviceId> result = new ArrayList<>();
        for (var entry : deviceToPlayer.entrySet()) {
            if (entry.getValue().equals(player)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Get all currently connected devices.
     */
    public List<InputDeviceInfo> getConnectedDevices() {
        return devices.values().stream()
                .filter(InputDeviceInfo::connected)
                .toList();
    }

    /**
     * Get all known devices (including disconnected).
     */
    public List<InputDeviceInfo> getAllDevices() {
        return List.copyOf(devices.values());
    }

    /**
     * Check if a specific gamepad is connected.
     */
    public boolean isConnected(InputDeviceId device) {
        InputDeviceInfo info = devices.get(device);
        return info != null && info.connected();
    }

    // -- Telemetry capture ----------------------------------------------------

    /**
     * Capture a structured telemetry snapshot of the input device subsystem.
     */
    public InputTelemetry captureTelemetry(int activeContextCount) {
        Map<PlayerId, List<InputDeviceId>> assignments = new LinkedHashMap<>();
        for (var entry : deviceToPlayer.entrySet()) {
            assignments.computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                    .add(entry.getKey());
        }
        return new InputTelemetry(
                getConnectedDevices(),
                Map.copyOf(assignments),
                totalEventsProcessed,
                eventsThisTick,
                totalSnapshots,
                activeContextCount,
                deviceEventCount);
    }

    // -- Event listener -------------------------------------------------------

    /** Register a listener for device events. */
    public void addListener(DeviceEventListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    /** Remove a listener. */
    public void removeListener(DeviceEventListener listener) {
        listeners.remove(listener);
    }

    private void fireEvent(DeviceEvent event) {
        deviceEventCount++;
        for (DeviceEventListener listener : listeners) {
            try {
                listener.onDeviceEvent(event);
            } catch (Exception e) {
                // Listener exceptions never affect device tracking
            }
        }
    }

    // -- Helpers --------------------------------------------------------------

    private PlayerId findFirstUnassignedSlot() {
        Set<PlayerId> assignedSlots = new HashSet<>(deviceToPlayer.values());
        for (int i = 0; i < maxPlayers; i++) {
            PlayerId slot = new PlayerId(i);
            if (!assignedSlots.contains(slot)) return slot;
        }
        return null; // all slots taken
    }

    // -- Device events --------------------------------------------------------

    /** Events fired by the device manager. */
    public sealed interface DeviceEvent {
        record Connected(InputDeviceInfo device) implements DeviceEvent {}
        record Disconnected(InputDeviceId deviceId) implements DeviceEvent {}
        record Assigned(PlayerId player, InputDeviceId device) implements DeviceEvent {}
        record Unassigned(PlayerId player) implements DeviceEvent {}
    }

    /** Listener for device management events. */
    @FunctionalInterface
    public interface DeviceEventListener {
        void onDeviceEvent(DeviceEvent event);
    }
}
