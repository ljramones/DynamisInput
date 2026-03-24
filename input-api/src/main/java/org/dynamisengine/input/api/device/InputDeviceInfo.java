package org.dynamisengine.input.api.device;

/**
 * Describes a connected input device.
 *
 * @param id          unique device identity
 * @param displayName human-readable name (e.g., "Xbox Wireless Controller")
 * @param connected   true if currently connected
 */
public record InputDeviceInfo(InputDeviceId id, String displayName, boolean connected) {

    public InputDeviceInfo {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (displayName == null) throw new IllegalArgumentException("displayName must not be null");
    }
}
