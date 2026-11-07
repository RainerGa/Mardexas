package de.richmarkdown.ui;

import java.lang.ref.WeakReference;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.MenuBar;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.shape.Path;

/** Switches editor chrome without rebuilding the editor or its services. */
public final class CommandSurfaceController implements AutoCloseable {
    private final EditorWindow window;
    private final MenuBar menuBar;
    private final RibbonView ribbon;
    private final WindowCommandRegistry registry;
    private final Runnable unsubscribe;
    private final boolean followApplicationMode;
    private final EventHandler<KeyEvent> keys = this::keyPressed;
    private UiMode mode = UiMode.CLASSIC;
    private boolean closed;
    private long layoutRevision;

    CommandSurfaceController(EditorWindow window, MenuBar menuBar, WindowCommandRegistry registry) {
        this(window, menuBar, registry, true);
    }
    CommandSurfaceController(EditorWindow window, MenuBar menuBar, WindowCommandRegistry registry, boolean followApplicationMode) {
        this.window = window; this.menuBar = menuBar; this.registry = registry;
        this.followApplicationMode = followApplicationMode;
        ribbon = new RibbonView(window, registry);
        window.getChrome().getChildren().add(2, ribbon);
        WeakReference<CommandSurfaceController> weak = new WeakReference<>(this);
        unsubscribe = followApplicationMode ? UiModeService.subscribe(value -> {
            CommandSurfaceController target = weak.get(); if (target != null) target.apply(value);
        }, failure -> {
            CommandSurfaceController target = weak.get();
            if (target != null) FeatureUi.error(target.window.getDialogOwner(), failure);
        }) : () -> { };
        if (!followApplicationMode) apply(UiMode.CLASSIC);
        window.getEditorRoot().addEventFilter(KeyEvent.KEY_PRESSED, keys);
    }
    private void keyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.F10 || event.getCode() == KeyCode.ALT) {
            if (mode == UiMode.RIBBON) ribbon.getTabs().requestFocus(); else menuBar.requestFocus();
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE && mode == UiMode.RIBBON) {
            window.getEditorControl().requestFocus();
        }
    }
    public UiMode getMode() { return mode; }
    public MenuBar getMenuBar() { return menuBar; }
    public RibbonView getRibbon() { return ribbon; }
    public void setMode(UiMode value) {
        java.util.Objects.requireNonNull(value, "mode");
        if (!closed) {
            if (followApplicationMode) UiModeService.change(value, failure -> FeatureUi.error(window.getDialogOwner(), failure));
            else apply(value);
        }
    }
    private void apply(UiMode value) {
        if (closed) return;
        var flow = window.getEditorControl().lookup(".virtual-flow") instanceof VirtualFlow<?> visible ? visible : null;
        var first = flow == null ? null : flow.getFirstVisibleCell();
        int index = first == null ? -1 : first.getIndex();
        double offset = first == null ? 0 : first.localToScene(first.getBoundsInLocal()).getMinY()
                - flow.localToScene(flow.getBoundsInLocal()).getMinY();
        mode = value;
        for (UiMode option : UiMode.values()) {
            var item = registry.item("ui.mode." + option.name().toLowerCase(java.util.Locale.ROOT));
            if (item instanceof javafx.scene.control.RadioMenuItem radio) radio.setSelected(value == option);
        }
        registry.refresh();
        menuBar.setVisible(value == UiMode.CLASSIC); menuBar.setManaged(value == UiMode.CLASSIC);
        window.getToolBar().setVisible(value == UiMode.CLASSIC); window.getToolBar().setManaged(value == UiMode.CLASSIC);
        ribbon.setVisible(value == UiMode.RIBBON); ribbon.setManaged(value == UiMode.RIBBON);
        long revision = ++layoutRevision;
        Scene scene = window.getEditorControl().getScene();
        if (scene == null || index < 0) return;
        Runnable[] afterLayout = new Runnable[1];
        afterLayout[0] = () -> {
            scene.removePostLayoutPulseListener(afterLayout[0]);
            if (closed || revision != layoutRevision || window.getEditorControl().getScene() != scene) return;
            // Scroll fractions shift the visible paragraph when the viewport height changes.
            // Restore its actual cell and pixel offset once the new chrome has been laid out.
            flow.scrollToTop(index); flow.scrollPixels(-offset); flow.layout();
            keepCaretVisible(flow);
        };
        scene.addPostLayoutPulseListener(afterLayout[0]);
        Platform.requestNextPulse();
    }
    private void keepCaretVisible(VirtualFlow<?> flow) {
        if (!window.getEditorControl().isFocused()) return;
        Bounds viewport = flow.localToScene(flow.getBoundsInLocal());
        window.getEditorControl().lookupAll(".caret").stream()
                .filter(Path.class::isInstance).map(Path.class::cast)
                .filter(path -> !path.getElements().isEmpty()).findFirst().ifPresent(path -> {
                    Bounds caret = path.localToScene(path.getBoundsInLocal());
                    if (caret.getMinY() < viewport.getMinY()) flow.scrollPixels(caret.getMinY() - viewport.getMinY());
                    else if (caret.getMaxY() > viewport.getMaxY()) flow.scrollPixels(caret.getMaxY() - viewport.getMaxY());
                });
    }
    @Override public void close() {
        if (!closed) {
            closed = true; layoutRevision++; unsubscribe.run(); ribbon.close();
            window.getEditorRoot().removeEventFilter(KeyEvent.KEY_PRESSED, keys);
        }
    }
}
