package org.dynamisinput.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.dynamis.window.api.InputEvent;
import org.dynamis.window.api.InputEvent.InputAction;
import org.dynamisinput.api.ActionId;
import org.dynamisinput.api.AxisId;
import org.dynamisinput.api.ContextId;
import org.dynamisinput.api.InputProcessor;
import org.dynamisinput.api.bind.AxisComposite2D;
import org.dynamisinput.api.bind.InputBinding;
import org.dynamisinput.api.bind.KeyBinding;
import org.dynamisinput.api.bind.MouseButtonBinding;
import org.dynamisinput.api.bind.MouseDeltaBinding;
import org.dynamisinput.api.context.InputMap;
import org.dynamisinput.api.frame.InputFrame;
import org.dynamisinput.api.frame.InputFrame.ActionState;

public final class DefaultInputProcessor implements InputProcessor {
    private final Map<ContextId, InputMap> mapsByContext;
    private final Deque<ContextId> contextStack = new ArrayDeque<>();
    private final Map<Long, List<InputEvent>> eventsByTick = new HashMap<>();
    private final Map<Long, InputFrame> frameCache = new HashMap<>();

    private final Set<Integer> downKeys = new HashSet<>();
    private final Set<Integer> downMouseButtons = new HashSet<>();
    private final Map<Integer, Integer> keyModifiersByCode = new HashMap<>();
    private final Map<Integer, Integer> mouseModifiersByButton = new HashMap<>();

    private long lastComputedTick = Long.MIN_VALUE;

    public DefaultInputProcessor(Map<ContextId, InputMap> mapsByContext) {
        this.mapsByContext = new HashMap<>(Objects.requireNonNull(mapsByContext, "mapsByContext"));
    }

    @Override
    public void feed(InputEvent event, long tick) {
        Objects.requireNonNull(event, "event");
        if (lastComputedTick != Long.MIN_VALUE && tick <= lastComputedTick) {
            throw new IllegalStateException("Cannot feed tick " + tick + " after snapshot(" + lastComputedTick + ")");
        }
        eventsByTick.computeIfAbsent(tick, ignored -> new ArrayList<>()).add(event);
    }

    @Override
    public void pushContext(ContextId id) {
        Objects.requireNonNull(id, "id");
        if (!mapsByContext.containsKey(id)) {
            throw new IllegalArgumentException("Unknown context: " + id.value());
        }
        contextStack.addLast(id);
    }

    @Override
    public void popContext(ContextId id) {
        Objects.requireNonNull(id, "id");
        contextStack.removeLastOccurrence(id);
    }

    @Override
    public InputFrame snapshot(long tick) {
        if (frameCache.containsKey(tick)) {
            return frameCache.get(tick);
        }
        long start = lastComputedTick == Long.MIN_VALUE ? tick : lastComputedTick + 1;
        if (lastComputedTick == Long.MIN_VALUE) {
            start = tick;
        }
        for (long current = start; current <= tick; current++) {
            frameCache.put(current, resolveForTick(current));
            lastComputedTick = current;
        }
        return frameCache.get(tick);
    }

    private InputFrame resolveForTick(long tick) {
        var pressedKeys = new HashSet<Integer>();
        var releasedKeys = new HashSet<Integer>();
        var pressedButtons = new HashSet<Integer>();
        var releasedButtons = new HashSet<Integer>();

        float mouseDeltaX = 0.0f;
        float mouseDeltaY = 0.0f;
        double previousX = 0.0;
        double previousY = 0.0;
        boolean hasCursorPosition = false;

        for (InputEvent event : eventsByTick.getOrDefault(tick, List.of())) {
            if (event instanceof InputEvent.Key key) {
                if (key.action() == InputAction.PRESS) {
                    if (downKeys.add(key.keyCode())) {
                        pressedKeys.add(key.keyCode());
                    }
                    keyModifiersByCode.put(key.keyCode(), key.modifiers());
                } else if (key.action() == InputAction.REPEAT) {
                    downKeys.add(key.keyCode());
                    keyModifiersByCode.put(key.keyCode(), key.modifiers());
                } else if (key.action() == InputAction.RELEASE) {
                    if (downKeys.remove(key.keyCode())) {
                        releasedKeys.add(key.keyCode());
                    }
                    keyModifiersByCode.remove(key.keyCode());
                }
            } else if (event instanceof InputEvent.MouseButton mouseButton) {
                if (mouseButton.action() == InputAction.PRESS) {
                    if (downMouseButtons.add(mouseButton.button())) {
                        pressedButtons.add(mouseButton.button());
                    }
                    mouseModifiersByButton.put(mouseButton.button(), mouseButton.modifiers());
                } else if (mouseButton.action() == InputAction.REPEAT) {
                    downMouseButtons.add(mouseButton.button());
                    mouseModifiersByButton.put(mouseButton.button(), mouseButton.modifiers());
                } else if (mouseButton.action() == InputAction.RELEASE) {
                    if (downMouseButtons.remove(mouseButton.button())) {
                        releasedButtons.add(mouseButton.button());
                    }
                    mouseModifiersByButton.remove(mouseButton.button());
                }
            } else if (event instanceof InputEvent.CursorMoved cursorMoved) {
                if (hasCursorPosition) {
                    mouseDeltaX += (float) (cursorMoved.x() - previousX);
                    mouseDeltaY += (float) (cursorMoved.y() - previousY);
                }
                previousX = cursorMoved.x();
                previousY = cursorMoved.y();
                hasCursorPosition = true;
            }
        }

        Map<ActionId, ActionState> actions = new LinkedHashMap<>();
        Map<AxisId, Float> axes = new LinkedHashMap<>();

        for (ContextId contextId : contextStack.reversed()) {
            InputMap map = mapsByContext.get(contextId);
            if (map == null) {
                continue;
            }

            for (Map.Entry<ActionId, List<InputBinding>> entry : map.actionBindings().entrySet()) {
                ActionId actionId = entry.getKey();
                boolean pressed = false;
                boolean released = false;
                boolean down = false;

                for (InputBinding binding : entry.getValue()) {
                    if (binding instanceof KeyBinding keyBinding) {
                        boolean modifierMatches = modifiersMatch(
                                keyModifiersByCode.getOrDefault(keyBinding.keyCode(), 0),
                                keyBinding.requiredModifiers());
                        pressed |= pressedKeys.contains(keyBinding.keyCode()) && modifierMatches;
                        released |= releasedKeys.contains(keyBinding.keyCode());
                        down |= downKeys.contains(keyBinding.keyCode()) && modifierMatches;
                    } else if (binding instanceof MouseButtonBinding mouseButtonBinding) {
                        boolean modifierMatches = modifiersMatch(
                                mouseModifiersByButton.getOrDefault(mouseButtonBinding.button(), 0),
                                mouseButtonBinding.requiredModifiers());
                        pressed |= pressedButtons.contains(mouseButtonBinding.button()) && modifierMatches;
                        released |= releasedButtons.contains(mouseButtonBinding.button());
                        down |= downMouseButtons.contains(mouseButtonBinding.button()) && modifierMatches;
                    }
                }

                ActionState existing = actions.get(actionId);
                if (existing == null) {
                    actions.put(actionId, new ActionState(pressed, released, down));
                } else {
                    actions.put(actionId, new ActionState(
                            existing.pressed() || pressed,
                            existing.released() || released,
                            existing.down() || down));
                }
            }

            for (Map.Entry<AxisId, List<InputBinding>> entry : map.axisBindings().entrySet()) {
                AxisId axisId = entry.getKey();
                float value = 0.0f;
                for (InputBinding binding : entry.getValue()) {
                    value += resolveAxisContribution(binding, axisId, mouseDeltaX, mouseDeltaY);
                }
                axes.merge(axisId, value, Float::sum);
            }

            if (map.consuming()) {
                break;
            }
        }

        return new InputFrame(tick, actions, axes, List.of());
    }

    private float resolveAxisContribution(InputBinding binding, AxisId axisId, float mouseDeltaX, float mouseDeltaY) {
        if (binding instanceof KeyBinding keyBinding) {
            return downKeys.contains(keyBinding.keyCode()) && modifiersMatch(
                    keyModifiersByCode.getOrDefault(keyBinding.keyCode(), 0),
                    keyBinding.requiredModifiers()) ? 1.0f : 0.0f;
        }
        if (binding instanceof MouseButtonBinding mouseButtonBinding) {
            return downMouseButtons.contains(mouseButtonBinding.button()) && modifiersMatch(
                    mouseModifiersByButton.getOrDefault(mouseButtonBinding.button(), 0),
                    mouseButtonBinding.requiredModifiers()) ? 1.0f : 0.0f;
        }
        if (binding instanceof MouseDeltaBinding mouseDeltaBinding) {
            float delta = mouseDeltaBinding.component() == MouseDeltaBinding.Component.X ? mouseDeltaX : mouseDeltaY;
            return delta * mouseDeltaBinding.sensitivity();
        }
        if (binding instanceof AxisComposite2D composite2D) {
            if (axisId.equals(composite2D.xAxis())) {
                int positive = downKeys.contains(composite2D.rightKeyCode()) ? 1 : 0;
                int negative = downKeys.contains(composite2D.leftKeyCode()) ? 1 : 0;
                return (positive - negative) * composite2D.sensitivity();
            }
            if (axisId.equals(composite2D.yAxis())) {
                int positive = downKeys.contains(composite2D.upKeyCode()) ? 1 : 0;
                int negative = downKeys.contains(composite2D.downKeyCode()) ? 1 : 0;
                return (positive - negative) * composite2D.sensitivity();
            }
        }
        return 0.0f;
    }

    private static boolean modifiersMatch(int actual, int required) {
        return (actual & required) == required;
    }
}
