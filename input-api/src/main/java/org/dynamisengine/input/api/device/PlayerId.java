package org.dynamisengine.input.api.device;

/**
 * Identifies a logical player slot.
 *
 * Player slots are independent of physical devices. A player slot can have
 * zero or more devices assigned to it (e.g., keyboard + mouse for player 1,
 * gamepad for player 2).
 *
 * @param index player slot index (0-based)
 */
public record PlayerId(int index) {

    public static final PlayerId PLAYER_1 = new PlayerId(0);
    public static final PlayerId PLAYER_2 = new PlayerId(1);
    public static final PlayerId PLAYER_3 = new PlayerId(2);
    public static final PlayerId PLAYER_4 = new PlayerId(3);

    public PlayerId {
        if (index < 0) throw new IllegalArgumentException("index must be >= 0");
    }
}
