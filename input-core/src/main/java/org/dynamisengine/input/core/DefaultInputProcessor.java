package org.dynamisengine.input.core;

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
import org.dynamisengine.window.api.InputEvent;
import org.dynamisengine.window.api.InputEvent.InputAction;
import org.dynamisengine.input.api.ActionId;
import org.dynamisengine.input.api.AxisId;
import org.dynamisengine.input.api.ContextId;
import org.dynamisengine.input.api.InputProcessor;
import org.dynamisengine.input.api.bind.AnalogShaping;
import org.dynamisengine.input.api.bind.AxisComposite2D;
import org.dynamisengine.input.api.bind.DeadzoneMode;
import org.dynamisengine.input.api.bind.GamepadAxisBinding;
import org.dynamisengine.input.api.bind.GamepadButtonBinding;
import org.dynamisengine.input.api.bind.GamepadStickBinding;
import org.dynamisengine.input.api.bind.InputBinding;
import org.dynamisengine.input.api.bind.KeyBinding;
import org.dynamisengine.input.api.bind.MouseButtonBinding;
import org.dynamisengine.input.api.bind.MouseDeltaBinding;
import org.dynamisengine.input.api.context.InputMap;
import org.dynamisengine.input.api.frame.InputFrame;
import org.dynamisengine.input.api.frame.InputFrame.ActionState;

/**
 * Deterministic event-to-frame processor.
 *
 * Consumes raw {@link InputEvent}s from DynamisWindow, resolves them through
 * the context stack and binding configuration, and produces immutable
 * {@link InputFrame} snapshots at tick boundaries.
 *
 * Supports keyboard, mouse, and gamepad inputs with full analog shaping
 * (deadzones, response curves, sensitivity, inversion).
 */
public final class DefaultInputProcessor implements InputProcessor {
    private final Map<ContextId, InputMap> mapsByContext;
    private final Deque<ContextId> contextStack = new ArrayDeque<>();
    private final Map<Long, List<InputEvent>> eventsByTick = new HashMap<>();
    private final Map<Long, InputFrame> frameCache = new HashMap<>();

    // Keyboard state
    private final Set<Integer> downKeys = new HashSet<>();
    private final Map<Integer, Integer> keyModifiersByCode = new HashMap<>();

    // Mouse state
    private final Set<Integer> downMouseButtons = new HashSet<>();
    private final Map<Integer, Integer> mouseModifiersByButton = new HashMap<>();

    // Gamepad state — keyed by (gamepadId << 16 | button/axis)
    private final Set<Long> downGamepadButtons = new HashSet<>();
    private final Map<Long, Float> gamepadAxisValues = new HashMap<>();

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
        var pressedGamepadButtons = new HashSet<Long>();
        var releasedGamepadButtons = new HashSet<Long>();

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
            } else if (event instanceof InputEvent.GamepadButton gpButton) {
                long key = gamepadButtonKey(gpButton.gamepadId(), gpButton.button());
                if (gpButton.action() == InputAction.PRESS) {
                    if (downGamepadButtons.add(key)) {
                        pressedGamepadButtons.add(key);
                    }
                } else if (gpButton.action() == InputAction.RELEASE) {
                    if (downGamepadButtons.remove(key)) {
                        releasedGamepadButtons.add(key);
                    }
                }
            } else if (event instanceof InputEvent.GamepadAxis gpAxis) {
                long key = gamepadAxisKey(gpAxis.gamepadId(), gpAxis.axis());
                gamepadAxisValues.put(key, gpAxis.value());
            }
            // GamepadConnected/Disconnected: no state change needed for frame resolution
        }

        Map<ActionId, ActionState> actions = new LinkedHashMap<>();
        Map<AxisId, Float> axes = new LinkedHashMap<>();

        for (ContextId contextId : contextStack.reversed()) {
            InputMap map = mapsByContext.get(contextId);
            if (map == null) continue;

            // Resolve action bindings
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
                    } else if (binding instanceof GamepadButtonBinding gpBinding) {
                        pressed |= isGamepadButtonMatched(gpBinding, pressedGamepadButtons);
                        released |= isGamepadButtonMatched(gpBinding, releasedGamepadButtons);
                        down |= isGamepadButtonDown(gpBinding);
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

            // Resolve axis bindings
            for (Map.Entry<AxisId, List<InputBinding>> entry : map.axisBindings().entrySet()) {
                AxisId axisId = entry.getKey();
                float value = 0.0f;
                for (InputBinding binding : entry.getValue()) {
                    value += resolveAxisContribution(binding, axisId, mouseDeltaX, mouseDeltaY);
                }
                axes.merge(axisId, value, Float::sum);
            }

            if (map.consuming()) break;
        }

        return new InputFrame(tick, actions, axes, List.of());
    }

    // -- Gamepad helpers ------------------------------------------------------

    private boolean isGamepadButtonMatched(GamepadButtonBinding binding, Set<Long> buttonSet) {
        if (binding.gamepadId() == -1) {
            // Match any gamepad
            for (long key : buttonSet) {
                if ((key & 0xFFFF) == binding.button()) return true;
            }
            return false;
        }
        return buttonSet.contains(gamepadButtonKey(binding.gamepadId(), binding.button()));
    }

    private boolean isGamepadButtonDown(GamepadButtonBinding binding) {
        if (binding.gamepadId() == -1) {
            for (long key : downGamepadButtons) {
                if ((key & 0xFFFF) == binding.button()) return true;
            }
            return false;
        }
        return downGamepadButtons.contains(gamepadButtonKey(binding.gamepadId(), binding.button()));
    }

    private float resolveAxisContribution(InputBinding binding, AxisId axisId,
                                           float mouseDeltaX, float mouseDeltaY) {
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
            float delta = mouseDeltaBinding.component() == MouseDeltaBinding.Component.X
                    ? mouseDeltaX : mouseDeltaY;
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
        if (binding instanceof GamepadAxisBinding gpAxis) {
            float raw = getGamepadAxisValue(gpAxis.gamepadId(), gpAxis.axis());
            return gpAxis.shaping().shape(raw);
        }
        if (binding instanceof GamepadStickBinding stick) {
            float rawX = getGamepadAxisValue(stick.gamepadId(), stick.stickXAxis());
            float rawY = getGamepadAxisValue(stick.gamepadId(), stick.stickYAxis());

            if (stick.deadzoneMode() == DeadzoneMode.RADIAL) {
                float[] shaped = stick.shaping().shapeRadial(rawX, rawY);
                if (axisId.equals(stick.xAxis())) return shaped[0];
                if (axisId.equals(stick.yAxis())) return shaped[1];
            } else {
                // Axial: shape each axis independently
                if (axisId.equals(stick.xAxis())) return stick.shaping().shape(rawX);
                if (axisId.equals(stick.yAxis())) return stick.shaping().shape(rawY);
            }
        }
        return 0.0f;
    }

    private float getGamepadAxisValue(int gamepadId, int axis) {
        if (gamepadId == -1) {
            // Any gamepad — find first non-zero value
            for (var entry : gamepadAxisValues.entrySet()) {
                if ((entry.getKey() & 0xFFFF) == axis) {
                    float val = entry.getValue();
                    if (val != 0.0f) return val;
                }
            }
            return 0.0f;
        }
        return gamepadAxisValues.getOrDefault(gamepadAxisKey(gamepadId, axis), 0.0f);
    }

    private static long gamepadButtonKey(int gamepadId, int button) {
        return ((long) gamepadId << 16) | (button & 0xFFFF);
    }

    private static long gamepadAxisKey(int gamepadId, int axis) {
        return ((long) gamepadId << 16) | (axis & 0xFFFF);
    }

    private static boolean modifiersMatch(int actual, int required) {
        return (actual & required) == required;
    }
}
