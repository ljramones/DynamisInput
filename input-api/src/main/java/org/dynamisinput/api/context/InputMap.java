package org.dynamisinput.api.context;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.dynamisinput.api.ActionId;
import org.dynamisinput.api.AxisId;
import org.dynamisinput.api.ContextId;
import org.dynamisinput.api.bind.InputBinding;

public record InputMap(
        ContextId contextId,
        Map<ActionId, List<InputBinding>> actionBindings,
        Map<AxisId, List<InputBinding>> axisBindings,
        boolean consuming
) {
    public InputMap {
        Objects.requireNonNull(contextId, "contextId");
        actionBindings = copy(actionBindings, "actionBindings");
        axisBindings = copy(axisBindings, "axisBindings");
    }

    private static <K> Map<K, List<InputBinding>> copy(Map<K, List<InputBinding>> source, String fieldName) {
        Objects.requireNonNull(source, fieldName);
        return source.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())
                ));
    }
}
