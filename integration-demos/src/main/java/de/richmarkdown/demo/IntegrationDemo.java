/* Copyright (C) 2026 Rainer Garbotz. SPDX-License-Identifier: GPL-3.0-or-later */
package de.richmarkdown.demo;

import de.richmarkdown.api.EditorMenus;
import de.richmarkdown.api.EditorPane;
import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.features.editing.DocumentEdits;
import de.richmarkdown.features.editing.EditingCommands;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.List;

/** Ten executable compositions; select with --case=RIBBON, etc. See docs/API.md. */
public final class IntegrationDemo extends Application {
    public enum Mode { BARE, TOOLBAR, CLASSIC, RIBBON, HOST_MENUS, TAB_LOCAL, TABS_HOST_MENUS, SPLIT_PREVIEW, CONTEXT_MENU, HOST_DIALOG }
    public static final String SAMPLE = "# Embedded Mardexas\n\nA **Markdown** document in your own application.\n\n## Chapter\n\nEdit this paragraph.\n";
    private Example example;

    @Override public void start(Stage stage) {
        if (getParameters().getNamed().containsKey("catalog")) {
            de.richmarkdown.i18n.I18n.setSettingsStore(de.richmarkdown.settings.ApplicationSettings.inMemory());
            de.richmarkdown.i18n.I18n.setCurrentLanguage("en");
        }
        String name = getParameters().getNamed().getOrDefault("case", "CLASSIC");
        example = create(Mode.valueOf(name));
        stage.setTitle("Mardexas integration — " + name);
        stage.setScene(new Scene(example.root(), 1100, 750));
        stage.show();
        stage.setOnCloseRequest(event -> { if (!example.mayClose()) event.consume(); });
        String catalog = getParameters().getNamed().get("catalog");
        if (catalog != null) {
            if (example.panes().isEmpty()) throw new IllegalArgumentException("Catalog needs a full EditorPane, e.g. --case=CLASSIC");
            try { java.nio.file.Files.writeString(java.nio.file.Path.of(catalog), commandReference(example.panes().getFirst())); }
            catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            example.close(); javafx.application.Platform.exit();
        }
    }
    @Override public void stop() { if (example != null) example.close(); }
    public static void main(String[] args) { launch(args); }

    /** Generates the complete discoverable command reference from the actual public API. */
    public static String commandReference(EditorPane pane) {
        StringBuilder result = new StringBuilder("# Editor command reference\n\nGenerated from the public command registry. "
                + "Labels follow the selected language; IDs are stable. Availability and selected state depend on the active document. "
                + "Embedded window/tab commands require a host handler and explicit permission.\n\n"
                + "| Command ID | Label | Shortcut | Toggle |\n| --- | --- | --- | --- |\n");
        pane.getCommands().commands().stream().sorted(java.util.Comparator.comparing(de.richmarkdown.ui.EditorCommand::id)).forEach(command ->
                result.append("| `").append(command.id()).append("` | ").append(command.label().replace("|", "\\|").replace('\n', ' '))
                        .append(" | ").append(command.shortcut() == null ? "" : command.shortcut().getDisplayText().replace("|", "\\|"))
                        .append(" | ").append(command.isToggle() ? "yes" : "").append(" |\n"));
        return result.toString();
    }

    /** Owned integration session; close when removing this part of the host UI. */
    public static final class Example implements AutoCloseable {
        private Parent root;
        private final List<EditorPane> panes = new ArrayList<>();
        private final List<MarkdownEditor> documents = new ArrayList<>();
        private final List<Runnable> cleanup = new ArrayList<>();
        private Example() { }
        public Parent root() { return root; }
        public List<MarkdownEditor> documents() { return List.copyOf(documents); }
        public List<EditorPane> panes() { return List.copyOf(panes); }
        public boolean mayClose() { return panes.stream().allMatch(EditorPane::mayClose); }
        private EditorPane pane(EditorPane.Presentation presentation) {
            EditorPane pane = new EditorPane(presentation);
            panes.add(pane); document(pane.getEditor()); return pane;
        }
        private MarkdownEditor document(MarkdownEditor editor) { documents.add(editor); editor.setMarkdown(SAMPLE); return editor; }
        @Override public void close() {
            cleanup.forEach(Runnable::run); cleanup.clear();
            panes.forEach(EditorPane::close);
        }
    }

    /** Constructs only the selected integration, without creating or opening a host window. */
    public static Example create(Mode mode) {
        Example result = new Example();
        switch (mode) {
            case BARE -> result.root = result.document(new MarkdownEditor()).getControl();
            case TOOLBAR -> {
                MarkdownEditor editor = result.document(new MarkdownEditor());
                Button bold = new Button("Bold"); bold.setOnAction(event -> editor.toggleBold());
                Button upper = new Button("UPPERCASE");
                upper.setOnAction(event -> new EditingCommands(editor).transform(DocumentEdits.Transform.UPPER));
                result.root = new BorderPane(editor.getControl(), new ToolBar(bold, upper), null, null, null);
            }
            case CLASSIC -> result.root = result.pane(EditorPane.Presentation.CLASSIC);
            case RIBBON -> result.root = result.pane(EditorPane.Presentation.RIBBON);
            case HOST_MENUS -> {
                EditorPane pane = result.pane(EditorPane.Presentation.TOOLBAR);
                MenuBar menus = new MenuBar(new Menu("Host application"));
                EditorMenus binding = new EditorMenus(menus); binding.setActiveEditor(pane);
                result.cleanup.add(binding::close);
                result.root = new BorderPane(pane, menus, null, null, null);
            }
            case TAB_LOCAL, TABS_HOST_MENUS -> {
                TabPane tabs = new TabPane();
                boolean hostMenus = mode == Mode.TABS_HOST_MENUS;
                MenuBar menus = new MenuBar(new Menu("Host application"));
                EditorMenus binding = new EditorMenus(menus);
                result.cleanup.add(binding::close);
                for (int i = 1; i <= 2; i++) {
                    EditorPane pane = result.pane(hostMenus ? EditorPane.Presentation.TOOLBAR : EditorPane.Presentation.CLASSIC);
                    MarkdownEditor document = pane.getEditor();
                    Tab tab = new Tab("Document " + i, pane);
                    tab.setOnCloseRequest(event -> { if (!pane.mayClose()) event.consume(); });
                    tab.setOnClosed(event -> { pane.close(); result.panes.remove(pane); result.documents.remove(document); });
                    tabs.getTabs().add(tab);
                    pane.getCommands().setHandler("tabs.close", () -> {
                        if (pane.mayClose()) { tabs.getTabs().remove(tab); pane.close(); result.panes.remove(pane); result.documents.remove(document); }
                    });
                    pane.getCommands().setAllowed("tabs.close", true);
                    for (int direction : new int[]{-1, 1}) {
                        String id = direction < 0 ? "tabs.previous" : "tabs.next";
                        pane.getCommands().setHandler(id, () -> tabs.getSelectionModel().select(Math.floorMod(tabs.getSelectionModel().getSelectedIndex() + direction, tabs.getTabs().size())));
                        pane.getCommands().setAllowed(id, true);
                    }
                }
                if (hostMenus) {
                    tabs.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, value) -> binding.setActiveEditor(value == null ? null : (EditorPane) value.getContent()));
                    binding.setActiveEditor((EditorPane) tabs.getSelectionModel().getSelectedItem().getContent());
                }
                result.root = new BorderPane(tabs, hostMenus ? menus : null, null, null, null);
            }
            case SPLIT_PREVIEW -> {
                EditorPane writing = result.pane(EditorPane.Presentation.CLASSIC);
                EditorPane preview = result.pane(EditorPane.Presentation.EDITOR_ONLY);
                preview.setReadOnly(true); preview.setOutlineVisible(false); preview.setStatusVisible(false);
                // A separate document/undo history, not the same Node with two parents.
                Button refresh = new Button("Refresh read-only preview");
                refresh.setId("refresh-preview");
                refresh.setOnAction(event -> preview.getEditor().setMarkdown(writing.getEditor().getMarkdownSource()));
                result.root = new BorderPane(new SplitPane(writing, preview), new ToolBar(refresh), null, null, null);
            }
            case CONTEXT_MENU -> {
                EditorPane pane = result.pane(EditorPane.Presentation.EDITOR_ONLY);
                ContextMenu menu = new ContextMenu();
                for (String id : List.of("menu.undo", "menu.redo", "menu.bold", "menu.italic")) {
                    var command = pane.getCommands().get(id);
                    MenuItem item = new MenuItem(command.label());
                    item.disableProperty().bind(command.enabledProperty().not());
                    item.setGraphic(de.richmarkdown.ui.CommandIcons.create(id, 18));
                    item.setOnAction(event -> pane.getCommands().execute(id)); menu.getItems().add(item);
                    result.cleanup.add(() -> item.disableProperty().unbind());
                }
                pane.setOnContextMenuRequested(event -> { pane.getCommands().refresh(); menu.show(pane, event.getScreenX(), event.getScreenY()); event.consume(); });
                result.cleanup.add(menu::hide); result.root = pane;
            }
            case HOST_DIALOG -> {
                Button open = new Button("Open editor in host dialog");
                open.setOnAction(event -> {
                    EditorPane pane = result.pane(EditorPane.Presentation.RIBBON);
                    Dialog<ButtonType> dialog = new Dialog<>(); dialog.initOwner(open.getScene().getWindow());
                    dialog.setTitle("Host-owned editor"); dialog.setResizable(true);
                    dialog.getDialogPane().setContent(pane); dialog.getDialogPane().setPrefSize(1000, 650);
                    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                    MarkdownEditor document = pane.getEditor();
                    dialog.setOnCloseRequest(close -> { if (!pane.mayClose()) close.consume(); });
                    dialog.setOnHidden(hidden -> { pane.close(); result.panes.remove(pane); result.documents.remove(document); });
                    result.cleanup.add(() -> { dialog.setOnCloseRequest(null); dialog.close(); });
                    dialog.show();
                });
                result.root = new VBox(new Label("The host owns the dialog and its lifecycle."), open);
            }
        }
        return result;
    }

}
