package org.dynamisengine.input.core;

import org.dynamisengine.input.api.device.*;
import org.dynamisengine.input.core.InputDeviceManager.DeviceEvent;
import org.dynamisengine.window.api.InputEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class InputDeviceManagerTest {

    // -- Initial state --------------------------------------------------------

    @Test
    void keyboardAndMouseAlwaysPresent() {
        var mgr = new InputDeviceManager(4);
        assertThat(mgr.isConnected(InputDeviceId.KEYBOARD)).isTrue();
        assertThat(mgr.isConnected(InputDeviceId.MOUSE)).isTrue();
        assertThat(mgr.getConnectedDevices()).hasSize(2);
    }

    @Test
    void keyboardAndMouseDefaultToPlayer1() {
        var mgr = new InputDeviceManager(4);
        assertThat(mgr.getPlayerForDevice(InputDeviceId.KEYBOARD))
                .hasValue(PlayerId.PLAYER_1);
        assertThat(mgr.getPlayerForDevice(InputDeviceId.MOUSE))
                .hasValue(PlayerId.PLAYER_1);
        assertThat(mgr.getDevicesForPlayer(PlayerId.PLAYER_1))
                .contains(InputDeviceId.KEYBOARD, InputDeviceId.MOUSE);
    }

    // -- Gamepad connect/disconnect -------------------------------------------

    @Test
    void gamepadConnectAddsDevice() {
        var mgr = new InputDeviceManager(4);
        mgr.processEvent(new InputEvent.GamepadConnected(0, "Xbox Controller"));

        assertThat(mgr.isConnected(InputDeviceId.gamepad(0))).isTrue();
        assertThat(mgr.getConnectedDevices()).hasSize(3); // kb + mouse + gamepad
    }

    @Test
    void gamepadAutoAssignsToFirstFreeSlot() {
        var mgr = new InputDeviceManager(4);
        // Player 1 has keyboard + mouse. First gamepad should get player 2.
        mgr.processEvent(new InputEvent.GamepadConnected(0, "Gamepad 0"));

        assertThat(mgr.getPlayerForDevice(InputDeviceId.gamepad(0)))
                .hasValue(PlayerId.PLAYER_2);
    }

    @Test
    void multipleGamepadsAssignSequentially() {
        var mgr = new InputDeviceManager(4);
        mgr.processEvent(new InputEvent.GamepadConnected(0, "Gamepad 0"));
        mgr.processEvent(new InputEvent.GamepadConnected(1, "Gamepad 1"));

        assertThat(mgr.getPlayerForDevice(InputDeviceId.gamepad(0)))
                .hasValue(PlayerId.PLAYER_2);
        assertThat(mgr.getPlayerForDevice(InputDeviceId.gamepad(1)))
                .hasValue(PlayerId.PLAYER_3);
    }

    @Test
    void gamepadBeyondMaxPlayersNotAssigned() {
        var mgr = new InputDeviceManager(2); // only 2 slots
        mgr.processEvent(new InputEvent.GamepadConnected(0, "Gamepad 0")); // → player 2
        mgr.processEvent(new InputEvent.GamepadConnected(1, "Gamepad 1")); // no slot

        assertThat(mgr.getPlayerForDevice(InputDeviceId.gamepad(0)))
                .hasValue(PlayerId.PLAYER_2);
        assertThat(mgr.getPlayerForDevice(InputDeviceId.gamepad(1)))
                .isEmpty();
    }

    @Test
    void gamepadDisconnectPreservesAssignment() {
        var mgr = new InputDeviceManager(4);
        mgr.processEvent(new InputEvent.GamepadConnected(0, "Gamepad 0"));
        assertThat(mgr.getPlayerForDevice(InputDeviceId.gamepad(0)))
                .hasValue(PlayerId.PLAYER_2);

        mgr.processEvent(new InputEvent.GamepadDisconnected(0));
        assertThat(mgr.isConnected(InputDeviceId.gamepad(0))).isFalse();

        // Assignment preserved despite disconnect
        assertThat(mgr.getPlayerForDevice(InputDeviceId.gamepad(0)))
                .hasValue(PlayerId.PLAYER_2);
    }

    @Test
    void gamepadReconnectRegainsSlot() {
        var mgr = new InputDeviceManager(4);
        mgr.processEvent(new InputEvent.GamepadConnected(0, "Gamepad 0"));
        mgr.processEvent(new InputEvent.GamepadDisconnected(0));
        mgr.processEvent(new InputEvent.GamepadConnected(0, "Gamepad 0"));

        // Should still be player 2 (not assigned a new slot)
        assertThat(mgr.getPlayerForDevice(InputDeviceId.gamepad(0)))
                .hasValue(PlayerId.PLAYER_2);
        assertThat(mgr.isConnected(InputDeviceId.gamepad(0))).isTrue();
    }

    // -- Manual assignment ----------------------------------------------------

    @Test
    void manualAssignmentOverridesAutoAssign() {
        var mgr = new InputDeviceManager(4);
        mgr.processEvent(new InputEvent.GamepadConnected(0, "Gamepad 0"));
        // Auto-assigned to player 2; now manually reassign to player 1
        mgr.assignDevice(PlayerId.PLAYER_1, InputDeviceId.gamepad(0));

        assertThat(mgr.getPlayerForDevice(InputDeviceId.gamepad(0)))
                .hasValue(PlayerId.PLAYER_1);
    }

    @Test
    void clearAssignmentReleasesSlot() {
        var mgr = new InputDeviceManager(4);
        mgr.processEvent(new InputEvent.GamepadConnected(0, "Gamepad 0"));
        mgr.clearAssignment(PlayerId.PLAYER_2);

        assertThat(mgr.getPlayerForDevice(InputDeviceId.gamepad(0))).isEmpty();
        assertThat(mgr.getDevicesForPlayer(PlayerId.PLAYER_2)).isEmpty();
    }

    @Test
    void assignDeviceBeyondMaxPlayersThrows() {
        var mgr = new InputDeviceManager(2);
        assertThatIllegalArgumentException().isThrownBy(
                () -> mgr.assignDevice(new PlayerId(5), InputDeviceId.gamepad(0)));
    }

    // -- Event listener -------------------------------------------------------

    @Test
    void listenerReceivesConnectDisconnectEvents() {
        var mgr = new InputDeviceManager(4);
        // Register after construction so we don't get the kb/mouse assignment events
        List<InputDeviceManager.DeviceEvent> events = new ArrayList<>();
        mgr.addListener(events::add);

        mgr.processEvent(new InputEvent.GamepadConnected(0, "Gamepad 0"));
        mgr.processEvent(new InputEvent.GamepadDisconnected(0));

        // Expected: Connected + Assigned(player2, gamepad0) + Disconnected
        assertThat(events).hasSize(3);
        assertThat(events.get(0)).isInstanceOf(DeviceEvent.Connected.class);
        assertThat(events.get(1)).isInstanceOf(DeviceEvent.Assigned.class);
        assertThat(events.get(2)).isInstanceOf(DeviceEvent.Disconnected.class);
    }

    @Test
    void listenerExceptionDoesNotAffectTracking() {
        var mgr = new InputDeviceManager(4);
        mgr.addListener(event -> { throw new RuntimeException("bad listener"); });

        // Should not throw
        mgr.processEvent(new InputEvent.GamepadConnected(0, "Gamepad 0"));
        assertThat(mgr.isConnected(InputDeviceId.gamepad(0))).isTrue();
    }

    // -- Device info ----------------------------------------------------------

    @Test
    void getAllDevicesIncludesDisconnected() {
        var mgr = new InputDeviceManager(4);
        mgr.processEvent(new InputEvent.GamepadConnected(0, "Gamepad 0"));
        mgr.processEvent(new InputEvent.GamepadDisconnected(0));

        assertThat(mgr.getAllDevices()).hasSize(3); // kb + mouse + gamepad(disconnected)
        assertThat(mgr.getConnectedDevices()).hasSize(2); // only kb + mouse
    }

    @Test
    void inputDeviceIdEquality() {
        assertThat(InputDeviceId.gamepad(0)).isEqualTo(InputDeviceId.gamepad(0));
        assertThat(InputDeviceId.gamepad(0)).isNotEqualTo(InputDeviceId.gamepad(1));
        assertThat(InputDeviceId.KEYBOARD).isNotEqualTo(InputDeviceId.MOUSE);
    }

    @Test
    void playerIdConstants() {
        assertThat(PlayerId.PLAYER_1.index()).isZero();
        assertThat(PlayerId.PLAYER_4.index()).isEqualTo(3);
        assertThatIllegalArgumentException().isThrownBy(() -> new PlayerId(-1));
    }
}
