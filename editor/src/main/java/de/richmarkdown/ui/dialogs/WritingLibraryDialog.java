package de.richmarkdown.ui.dialogs;

import de.richmarkdown.io.MarkdownLibraryStore;
import de.richmarkdown.io.MarkdownLibraryStore.Entry;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.util.concurrent.Callable;

/** Bounded asynchronous CRUD for snippets/templates, including Markdown preview and file import. */
public final class WritingLibraryDialog extends Dialog<String> {
    private final MarkdownLibraryStore store;
    private final ListView<Entry> entries = new ListView<>();
    private final TextField name = new TextField();
    private final TextField shortcut = new TextField();
    private final TextArea content = new TextArea();
    private final Label status = new Label();
    private final VBox body = new VBox(8);
    private final ButtonType use = new ButtonType("Verwenden", ButtonBar.ButtonData.OK_DONE);
    private String selectedId;
    private boolean busy;

    public WritingLibraryDialog(Window owner, MarkdownLibraryStore store, MarkdownLibraryStore.Kind kind,
                                String initialMarkdown) {
        this.store = store;
        initOwner(owner);
        setTitle(kind == MarkdownLibraryStore.Kind.SNIPPET ? "Textbausteine" : "Eigene Dokumentvorlagen");
        setHeaderText("Inhalt ist normales Markdown. Optionale ${Feldname}-Felder werden vor Verwendung abgefragt.");
        setResizable(true);
        name.setPromptText("Name");
        name.setId("library-name");
        shortcut.setPromptText("Kürzel (optional, z. B. ;adresse)");
        shortcut.setId("library-shortcut");
        shortcut.setVisible(kind == MarkdownLibraryStore.Kind.SNIPPET);
        shortcut.setManaged(shortcut.isVisible());
        content.setId("library-content");
        content.setWrapText(true);
        content.setPrefRowCount(12);
        content.setText(initialMarkdown == null ? "" : initialMarkdown);
        entries.setId("library-list");
        entries.setPrefHeight(140);
        entries.getSelectionModel().selectedItemProperty().addListener((obs, previous, entry) -> {
            if (entry != null) {
                selectedId = entry.id();
                name.setText(entry.name());
                shortcut.setText(entry.shortcut());
                content.setText(entry.content());
            }
        });
        Button create = button("Neu", "Neuen Eintrag anlegen", () -> {
            entries.getSelectionModel().clearSelection();
            selectedId = null;
            name.clear(); shortcut.clear(); content.clear(); name.requestFocus();
        });
        Button save = button("Speichern", "Eintrag anlegen oder Änderungen speichern", this::save);
        Button delete = button("Löschen", "Ausgewählten Bibliothekseintrag dauerhaft entfernen", this::delete);
        Button load = button("Markdown laden…", "Markdown-Datei als Inhalt importieren", this::importFile);
        delete.disableProperty().bind(entries.getSelectionModel().selectedItemProperty().isNull());
        HBox commands = new HBox(8, create, save, delete, load);
        body.getChildren().addAll(entries, commands, name, shortcut, content, status);
        body.setPrefSize(720, 600);
        VBox.setVgrow(content, Priority.ALWAYS);
        getDialogPane().setContent(body);
        getDialogPane().getButtonTypes().addAll(use, ButtonType.CANCEL);
        ((Button) getDialogPane().lookupButton(use)).setTooltip(new Tooltip("Aktuellen Inhalt einfügen; Änderungen werden nur mit Speichern dauerhaft abgelegt"));
        ((Button) getDialogPane().lookupButton(ButtonType.CANCEL)).setTooltip(new Tooltip("Schließen ohne Inhalt zu übernehmen"));
        getDialogPane().lookupButton(use).disableProperty().bind(content.textProperty().isEmpty());
        setResultConverter(button -> button == use ? content.getText() : null);
        setOnShowing(event -> refresh(null));
    }

    private void save() {
        String id = selectedId;
        String entryName = name.getText();
        String entryShortcut = shortcut.getText();
        String markdown = content.getText();
        runIo(() -> store.save(id, entryName, entryShortcut, markdown), entry -> refresh(entry.id()));
    }

    private void delete() {
        Entry selected = entries.getSelectionModel().getSelectedItem();
        if (selected == null || busy) return;
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Eintrag „" + selected.name() + "“ wirklich löschen?", ButtonType.OK, ButtonType.CANCEL);
        confirmation.initOwner(getOwner());
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        runIo(() -> { store.delete(selected.id()); return true; }, ignored -> {
            selectedId = null; name.clear(); shortcut.clear(); content.clear(); refresh(null);
        });
    }

    private void importFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Markdown-Inhalt laden");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md", "*.markdown"));
        var file = chooser.showOpenDialog(getOwner());
        if (file == null) return;
        runIo(() -> MarkdownLibraryStore.importMarkdown(file.toPath()), markdown -> {
            selectedId = null;
            entries.getSelectionModel().clearSelection();
            name.setText(file.getName().replaceFirst("(?i)\\.(md|markdown)$", ""));
            shortcut.clear(); content.setText(markdown);
        });
    }

    private void refresh(String selectId) {
        runIo(store::list, loaded -> {
            entries.getItems().setAll(loaded);
            if (selectId != null) loaded.stream().filter(entry -> entry.id().equals(selectId))
                    .findFirst().ifPresent(entry -> entries.getSelectionModel().select(entry));
            status.setText(loaded.size() + " Einträge · maximal 128 Einträge mit jeweils 256 KiB");
        });
    }

    private <T> void runIo(Callable<T> operation, java.util.function.Consumer<T> success) {
        if (busy) return;
        busy = true;
        body.setDisable(true);
        status.setText("Bibliothek wird verarbeitet…");
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return operation.call(); } };
        task.setOnSucceeded(event -> {
            busy = false; body.setDisable(false);
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            busy = false; body.setDisable(false);
            status.setText("Fehler: " + task.getException().getMessage());
        });
        Thread.ofVirtual().name("writing-library-io").start(task);
    }

    private static Button button(String text, String help, Runnable action) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(help));
        button.setOnAction(event -> action.run());
        return button;
    }
}
