package org.dynamisinput.api.frame;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.dynamisinput.api.ActionId;
import org.dynamisinput.api.AxisId;

public record InputFrame(
        long tick,
        Map<ActionId, ActionState> actions,
        Map<AxisId, Float> axes,
        List<String> text
) {
    public record ActionState(boolean pressed, boolean released, boolean down) {}

    public InputFrame {
        actions = Map.copyOf(Objects.requireNonNull(actions, "actions"));
        axes = Map.copyOf(Objects.requireNonNull(axes, "axes"));
        text = List.copyOf(Objects.requireNonNull(text, "text"));
    }

    public boolean pressed(ActionId id) {
        return actions.getOrDefault(id, new ActionState(false, false, false)).pressed();
    }

    public boolean released(ActionId id) {
        return actions.getOrDefault(id, new ActionState(false, false, false)).released();
    }

    public boolean down(ActionId id) {
        return actions.getOrDefault(id, new ActionState(false, false, false)).down();
    }

    public float axis(AxisId id) {
        return axes.getOrDefault(id, 0.0f);
    }
}
