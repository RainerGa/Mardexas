/* Copyright (C) 2026 Rainer Garbotz. SPDX-License-Identifier: GPL-3.0-or-later */
package de.richmarkdown.api;

import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.ui.EditorWindow;
import de.richmarkdown.ui.FormatToolBar;
import de.richmarkdown.ui.UiMode;
import de.richmarkdown.ui.WindowCommandRegistry;
import javafx.application.Platform;
import javafx.scene.control.MenuBar;
import javafx.scene.layout.BorderPane;
import java.util.Objects;

/**
 * Complete Mardexas document surface inside a host-owned scene, tab or split pane.
 * All construction, access and disposal must occur on the JavaFX application thread.
 * No Application is launched and no host window is shown, hidden or closed.
 * For a lightweight control without shell services, use {@link MarkdownEditor} directly.
 * Close this pane when permanently removing it; temporary reparenting needs no close.
 */
public final class EditorPane extends BorderPane implements AutoCloseable {
    /** Local presentation; never changes another editor's UI mode preference. */
    public enum Presentation { CLASSIC, RIBBON, TOOLBAR, EDITOR_ONLY }
    private static final class Context extends EditorWindow {
        Context() { super(null, false); }
    }
    private final Context context;
    private final java.util.List<javafx.scene.Node> extraChrome;
    private Presentation presentation;
    private boolean closed;

    /** Creates a classic editor with outline, status and all document commands. */
    public EditorPane() { this(Presentation.CLASSIC); }

    /** Creates an independently configured editor. */
    public EditorPane(Presentation presentation) {
        checkThread();
        Objects.requireNonNull(presentation, "presentation");
        context = new Context();
        var surface = context.getCommandSurface();
        extraChrome = context.getChrome().getChildren().stream()
                .filter(node -> node != surface.getMenuBar() && node != context.getToolBar() && node != surface.getRibbon()).toList();
        // Same shared shell as the standalone application. Its Stage stays unshown;
        // the actual nodes, CSS, accelerators and dialog ownership belong to the host.
        var scene = context.getScene();
        var root = context.getEditorRoot();
        root.getStylesheets().addAll(scene.getStylesheets());
        scene.setRoot(new BorderPane());
        context.setScene(null);
        setCenter(root);
        var registry = context.getCommandRegistry();
        for (String id : new String[]{"menu.quit", "menu.minimize", "menu.maximize", "menu.closeAll",
                "tabs.new", "tabs.next", "tabs.previous", "tabs.sideBySide", "tabs.closeSideView", "tabs.reopenClosed", "tabs.close"}) {
            if (registry.ids().contains(id)) registry.setAllowed(id, false);
        }
        registry.setHandler("ui.mode.classic", () -> setPresentation(Presentation.CLASSIC));
        registry.setHandler("ui.mode.ribbon", () -> setPresentation(Presentation.RIBBON));
        context.getToolBar().setNewDocumentHandler(() -> registry.execute("menu.new"));
        context.getToolBar().setOpenFileHandler(() -> registry.execute("menu.open"));
        context.getToolBar().setSaveHandler(() -> registry.execute("menu.save"));
        setPresentation(presentation);
    }

    /** Live document facade; uses UTF-8 Markdown and exposes asynchronous rich-text actions. */
    public MarkdownEditor getEditor() { requireOpen(); return context.getEditor(); }
    /** Shared commands, state, icons and host handler/capability hooks. */
    public WindowCommandRegistry getCommands() { requireOpen(); return context.getCommandRegistry(); }
    /** Live format toolbar, for optional host composition. Do not give it two parents. */
    public FormatToolBar getToolBar() { requireOpen(); return context.getToolBar(); }
    /** Source menu hierarchy for {@link EditorMenus}; do not move its MenuItems into host menus. */
    MenuBar menuSource() { requireOpen(); return context.getCommandSurface().getMenuBar(); }
    public Presentation getPresentation() { requireOpen(); return presentation; }

    /** Changes chrome without rebuilding the document or losing undo history. */
    public void setPresentation(Presentation value) {
        requireOpen(); Objects.requireNonNull(value, "presentation");
        var surface = context.getCommandSurface();
        var chrome = context.getChrome();
        // Remove hidden MenuBars too: invisible MenuBars still register Scene accelerators.
        chrome.getChildren().clear();
        if (value == Presentation.CLASSIC || value == Presentation.RIBBON) {
            chrome.getChildren().addAll(surface.getMenuBar(), context.getToolBar(), surface.getRibbon());
        } else if (value == Presentation.TOOLBAR) chrome.getChildren().add(context.getToolBar());
        if (value != Presentation.EDITOR_ONLY) chrome.getChildren().addAll(extraChrome);
        surface.setMode(value == Presentation.RIBBON ? UiMode.RIBBON : UiMode.CLASSIC);
        context.getEditorRoot().setTop(value == Presentation.EDITOR_ONLY ? null : chrome);
        presentation = value;
    }

    public void setOutlineVisible(boolean visible) { requireOpen(); context.setStructureViewVisible(visible); }
    public void setLineNumbersVisible(boolean visible) { requireOpen(); context.setLineNumbersVisible(visible); }
    public void setStatusVisible(boolean visible) {
        requireOpen(); context.getStatusBar().setVisible(visible); context.getStatusBar().setManaged(visible);
    }
    public void setReadOnly(boolean value) { requireOpen(); context.setReadOnly(value); }
    public boolean isReadOnly() { requireOpen(); return context.isReadOnly(); }
    /** Host policy for discarding unsaved changes; null restores the owned confirmation dialog. */
    public void setConfirmationHandler(EditorWindow.ConfirmationHandler handler) { requireOpen(); context.setConfirmationHandler(handler); }
    /** Overrides shell file/validation and failed-command errors; null restores owned dialogs. */
    public void setErrorHandler(java.util.function.Consumer<String> handler) {
        requireOpen(); context.setErrorHandler(handler);
        context.getCommandRegistry().setErrorHandler(handler == null ? null : error -> handler.accept(error.getMessage()));
    }
    /** Ask before removing a tab; {@link #close()} itself never discards/asks/saves implicitly. */
    public boolean mayClose() { requireOpen(); return context.mayCloseDocument(); }
    /** Read-only preview of lifecycle state, usable for host menu activation. */
    public boolean isClosed() { checkThread(); return closed; }

    private void requireOpen() {
        checkThread();
        if (closed) throw new IllegalStateException("EditorPane is closed");
    }
    static void checkThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Use the JavaFX application thread");
    }
    /** Stops owned checks/speech/subscriptions. Idempotent; does not terminate JavaFX. */
    @Override public void close() {
        checkThread();
        if (closed) return;
        closed = true;
        try { context.disposeFeatures(); }
        finally { setCenter(null); }
    }
}
