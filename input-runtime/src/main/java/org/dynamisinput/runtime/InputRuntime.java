package org.dynamisinput.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.dynamis.window.api.InputEvent;
import org.dynamisinput.api.ContextId;
import org.dynamisinput.api.InputProcessor;
import org.dynamisinput.api.context.InputMap;
import org.dynamisinput.api.frame.InputFrame;
import org.dynamisinput.core.DefaultInputProcessor;

public final class InputRuntime {
    private final InputProcessor processor;

    private InputRuntime(InputProcessor processor) {
        this.processor = processor;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void feed(InputEvent event, long tick) {
        processor.feed(event, tick);
    }

    public InputFrame frame(long tick) {
        return processor.snapshot(tick);
    }

    public static final class Builder {
        private InputProcessor processor;
        private final Map<ContextId, InputMap> inputMaps = new HashMap<>();
        private final List<ContextId> initialContexts = new ArrayList<>();

        public Builder processor(InputProcessor processor) {
            this.processor = Objects.requireNonNull(processor, "processor");
            return this;
        }

        public Builder initialMap(InputMap map) {
            Objects.requireNonNull(map, "map");
            inputMaps.put(map.contextId(), map);
            return this;
        }

        public Builder initialMaps(List<InputMap> maps) {
            Objects.requireNonNull(maps, "maps").forEach(this::initialMap);
            return this;
        }

        public Builder initialContext(ContextId contextId) {
            initialContexts.add(Objects.requireNonNull(contextId, "contextId"));
            return this;
        }

        public Builder initialContexts(List<ContextId> contexts) {
            initialContexts.addAll(List.copyOf(Objects.requireNonNull(contexts, "contexts")));
            return this;
        }

        public InputRuntime build() {
            InputProcessor resolved = processor != null ? processor : new DefaultInputProcessor(inputMaps);
            for (ContextId contextId : initialContexts) {
                resolved.pushContext(contextId);
            }
            return new InputRuntime(resolved);
        }
    }
}
