/*
 * Copyright (C) 2026 Rainer Garbotz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.richmarkdown.ui.dialogs;

import de.richmarkdown.i18n.I18n;
import de.richmarkdown.io.MarkdownLibraryStore;
import de.richmarkdown.io.MarkdownLibraryStore.Entry;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
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

import java.text.MessageFormat;
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
    private String selectedId;
    private boolean busy;
    private boolean selecting;
    private String baseline = "";
    private final javafx.collections.ObservableList<Entry> allEntries = javafx.collections.FXCollections.observableArrayList();
    private final TextField filter = new TextField();
    private final javafx.beans.property.BooleanProperty working = new javafx.beans.property.SimpleBooleanProperty();
    private String snapshot() { return name.getText() + "\0" + shortcut.getText() + "\0" + content.getText(); }
    private boolean mayDiscard() { return snapshot().equals(baseline) || WorkflowDialogs.discard(getDialogPane().getScene().getWindow()); }
    private void clean() { baseline = snapshot(); }


    public WritingLibraryDialog(Window owner, MarkdownLibraryStore store, MarkdownLibraryStore.Kind kind,
                                String initialMarkdown) {
        this.store = store;
        initOwner(owner);
        setTitle(kind == MarkdownLibraryStore.Kind.SNIPPET
                ? I18n.text("dialog.writingLibrary.snippet.title")
                : I18n.text("dialog.writingLibrary.template.title"));
        setHeaderText(I18n.text("dialog.writingLibrary.header"));
        setResizable(true);
        name.setPromptText(I18n.text("dialog.writingLibrary.label.name"));
        name.setAccessibleText(I18n.text("dialog.writingLibrary.label.name"));
        name.setId("library-name");
        shortcut.setPromptText(I18n.text("dialog.writingLibrary.label.shortcut"));
        shortcut.setAccessibleText(I18n.text("dialog.writingLibrary.label.shortcut"));
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
            if (selecting) return;
            if (entry != null) {
                if (!mayDiscard()) { selecting = true; entries.getSelectionModel().select(previous); selecting = false; return; }
                selectedId = entry.id();
                name.setText(entry.name());
                shortcut.setText(entry.shortcut());
                content.setText(entry.content());
                clean();
            }
        });
        Button create = button(I18n.text("dialog.writingLibrary.button.new"),
                I18n.text("dialog.writingLibrary.button.new"),
                () -> {
                    if (!mayDiscard()) return;
                    entries.getSelectionModel().clearSelection();
                    selectedId = null;
                    name.clear();
                    shortcut.clear();
                    content.clear();
                    clean();
                    name.requestFocus();
                });
        Button save = button(I18n.text("dialog.writingLibrary.button.save"),
                I18n.text("dialog.writingLibrary.button.save"), this::save);
        Button delete = button(I18n.text("dialog.writingLibrary.button.delete"),
                I18n.text("dialog.writingLibrary.button.delete"), this::delete);
        Button load = button(I18n.text("dialog.writingLibrary.button.load"),
                I18n.text("dialog.writingLibrary.button.load"), this::importFile);
        delete.disableProperty().bind(entries.getSelectionModel().selectedItemProperty().isNull());
        Button export = WorkflowDialogs.button("export", this::exportFile);
        Button preview = WorkflowDialogs.button("preview", () -> {
            Dialog<Void> dialog = new Dialog<>(); dialog.initOwner(getDialogPane().getScene().getWindow()); dialog.setTitle(WorkflowDialogs.text("preview"));
            dialog.setResizable(true); dialog.getDialogPane().setContent(WorkflowDialogs.markdownPreview(content.getText()));
            dialog.getDialogPane().getButtonTypes().add(DialogButtons.CLOSE); DialogButtons.applyDialogStyles(dialog.getDialogPane()); dialog.show();
        });
        HBox commands = new HBox(8, create, save, delete, load, export, preview);
        filter.setPromptText(WorkflowDialogs.text("search")); filter.setId("library-filter");
        var filtered = new javafx.collections.transformation.FilteredList<>(allEntries, entry -> true);
        entries.setItems(filtered);
        filter.textProperty().addListener((obs, old, value) -> {
            if (selecting) return;
            String query = value.toLowerCase(java.util.Locale.ROOT);
            selecting = true;
            filtered.setPredicate(entry -> (entry.name() + " " + entry.shortcut()).toLowerCase(java.util.Locale.ROOT).contains(query));
            selecting = false;
        });
        body.getChildren().addAll(filter, entries, commands, name, shortcut, content, status);
        body.setPrefSize(720, 600);
        VBox.setVgrow(content, Priority.ALWAYS);
        getDialogPane().setContent(body);

        DialogButtons.applyDialogStyles(getDialogPane());
        getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL);
        Button use = (Button) getDialogPane().lookupButton(DialogButtons.OK);
        use.setDefaultButton(true);
        use.setTooltip(new Tooltip(I18n.text("dialog.codeAssistant.button.ok.tooltip")));
        ((Button) getDialogPane().lookupButton(DialogButtons.CANCEL))
                .setTooltip(new Tooltip(I18n.text("dialog.codeAssistant.button.cancel.tooltip")));
        use.setText(WorkflowDialogs.text("insert"));
        use.disableProperty().bind(content.textProperty().isEmpty().or(working));
        getDialogPane().lookupButton(DialogButtons.CANCEL).disableProperty().bind(working);
        setOnCloseRequest(event -> { if (busy || getResult() == null && !mayDiscard()) event.consume(); });
        clean();
        setResultConverter(button -> button == DialogButtons.OK ? content.getText() : null);
        setOnShowing(event -> refresh(null));
        setOnShown(event -> name.requestFocus());
    }

    private void save() {
        String id = selectedId;
        String entryName = name.getText();
        String entryShortcut = shortcut.getText();
        String markdown = content.getText();
        runIo(() -> store.save(id, entryName, entryShortcut, markdown), entry -> { clean(); refresh(entry.id()); });
    }

    private void delete() {
        Entry selected = entries.getSelectionModel().getSelectedItem();
        if (selected == null || busy) return;
        ButtonType discard = DialogButtons.DISCARD;
        ButtonType keep = DialogButtons.KEEP;
        var confirmation = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION,
                MessageFormat.format(I18n.text("dialog.writingLibrary.confirm.delete"), selected.name()), discard, keep);
        confirmation.initOwner(getOwner());
        confirmation.setTitle(I18n.text("dialog.writingLibrary.button.delete"));
        confirmation.setHeaderText(null);
        if (confirmation.showAndWait().orElse(keep) != discard) return;
        runIo(() -> {
            store.delete(selected.id());
            return true;
        }, ignored -> {
            selectedId = null;
            name.clear();
            shortcut.clear();
            content.clear();
            clean(); refresh(null);
        });
    }

    private void importFile() {
        if (!mayDiscard()) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.text("dialog.writingLibrary.button.load"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md", "*.markdown"));
        var file = chooser.showOpenDialog(getOwner());
        if (file == null) return;
        runIo(() -> MarkdownLibraryStore.importMarkdown(file.toPath()), markdown -> {
            selectedId = null;
            entries.getSelectionModel().clearSelection();
            name.setText(file.getName().replaceFirst("(?i)\\.(md|markdown)$", ""));
            shortcut.clear();
            content.setText(markdown);
        });
    }

    private void exportFile() {
        FileChooser chooser = new FileChooser(); chooser.setTitle(WorkflowDialogs.text("export"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md"));
        chooser.setInitialFileName("snippet.md");
        var target = chooser.showSaveDialog(getDialogPane().getScene().getWindow());
        if (target == null) return;
        String markdown = content.getText();
        runIo(() -> { java.nio.file.Files.writeString(target.toPath(), markdown, java.nio.charset.StandardCharsets.UTF_8); return true; },
                ignored -> status.setText(target.toString()));
    }

    private void refresh(String selectId) {
        runIo(store::list, loaded -> {
            allEntries.setAll(loaded);
            if (selectId != null) {
                loaded.stream().filter(entry -> entry.id().equals(selectId))
                        .findFirst().ifPresent(entry -> entries.getSelectionModel().select(entry));
            }
            status.setText(MessageFormat.format(I18n.text("dialog.writingLibrary.status.count"), loaded.size()));
        });
    }

    private <T> void runIo(Callable<T> operation, java.util.function.Consumer<T> success) {
        if (busy) return;
        busy = true; working.set(true);
        body.setDisable(true);
        status.setText(I18n.text("dialog.writingLibrary.status.processing"));
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return operation.call();
            }
        };
        task.setOnSucceeded(event -> {
            busy = false; working.set(false);
            body.setDisable(false);
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            busy = false; working.set(false);
            body.setDisable(false);
            status.setText(MessageFormat.format(I18n.text("dialog.writingLibrary.status.error"), task.getException().getMessage()));
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
