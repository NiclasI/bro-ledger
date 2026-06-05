package se.niclas.broledger.ui;

import javafx.scene.Scene;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Layered hotkey dispatch for the main window scene.
 *
 * Contexts are pushed onto a stack; the topmost context wins on a key match.
 * Consume the event on match so focused controls (TableView, TextField) don't
 * also react.
 *
 * Usage:
 *   manager.push("global",   Map.of(CTRL_O, this::openSave, ...));
 *   manager.push("overview", Map.of(ENTER, this::openCard, ...));
 *   manager.pop("overview");
 */
class HotkeyManager {

    private record HotkeyContext(String name, Map<KeyCombination, Runnable> bindings) {}

    private final Deque<HotkeyContext> stack = new ArrayDeque<>();

    void push(String name, Map<KeyCombination, Runnable> bindings) {
        stack.push(new HotkeyContext(name, bindings));
    }

    void pop(String name) {
        stack.removeIf(ctx -> ctx.name().equals(name));
    }

    /** Attaches a single KEY_PRESSED filter to the scene. Call once after scene creation. */
    void register(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            for (HotkeyContext ctx : stack) {
                for (Map.Entry<KeyCombination, Runnable> entry : ctx.bindings().entrySet()) {
                    if (entry.getKey().match(event)) {
                        entry.getValue().run();
                        event.consume();
                        return;
                    }
                }
            }
        });
    }

    /** Convenience builder to reduce verbosity at call sites. */
    @SafeVarargs
    static Map<KeyCombination, Runnable> bindings(Map.Entry<KeyCombination, Runnable>... entries) {
        Map<KeyCombination, Runnable> map = new LinkedHashMap<>();
        for (var e : entries) map.put(e.getKey(), e.getValue());
        return map;
    }
}
