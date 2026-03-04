package org.dynamisinput.api.bind;

import org.dynamisinput.api.AxisId;

public record AxisComposite2D(
        AxisId xAxis,
        AxisId yAxis,
        int leftKeyCode,
        int rightKeyCode,
        int downKeyCode,
        int upKeyCode,
        float sensitivity
) implements InputBinding {
    public AxisComposite2D {
        if (xAxis == null || yAxis == null) {
            throw new IllegalArgumentException("axis ids must be non-null");
        }
        if (leftKeyCode < 0 || rightKeyCode < 0 || downKeyCode < 0 || upKeyCode < 0) {
            throw new IllegalArgumentException("key codes must be >= 0");
        }
        if (!Float.isFinite(sensitivity)) {
            throw new IllegalArgumentException("sensitivity must be finite");
        }
    }
}
