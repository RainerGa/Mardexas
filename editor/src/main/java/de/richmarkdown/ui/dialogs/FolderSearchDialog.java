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

import de.richmarkdown.findreplace.FolderSearch;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.ui.FeatureUi;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

/** One cancellable worker per open dialog, bounded raw-Markdown search and reviewed replacements. */
public final class FolderSearchDialog extends Stage {
    final TextField directory = new TextField();
    final TextField query = new TextField();
    final TextField replacement = new TextField();
    final TextField patterns = new TextField("*.md,*.markdown");
    final TextField exclusions = new TextField(".git,node_modules,target");
    private final java.util.Map<Path, Set<FolderSearch.Hit>> selectedHits = new java.util.HashMap<>();
    private final ListView<FolderSearch.Hit> hits = new ListView<>();
    private record Backup(Path path, FolderSearch.FileMatches original, FolderSearch.Query query, String replacement) { }
    private final java.util.List<Backup> backups = new java.util.ArrayList<>();
    private final Button restore = WorkflowDialogs.button("backup", this::restoreBackups);
    final CheckBox regex = new CheckBox(I18n.text("dialog.folderSearch.checkbox.regex"));
    final CheckBox matchCase = new CheckBox(I18n.text("dialog.folderSearch.checkbox.matchCase"));
    final CheckBox words = new CheckBox(I18n.text("dialog.folderSearch.checkbox.words"));
    final ListView<FolderSearch.FileMatches> files = new ListView<>();
    final TextArea preview = new TextArea();
    final Label status = new Label();
    private final Set<Path> selected = new HashSet<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "mardexas-folder-search"); thread.setDaemon(true); return thread;
    });
    private Future<?> running;
    private long revision;
    private boolean busy;
    private FolderSearch.Query resultQuery;
    private final Button search;
    private final Button apply;
    private final Button cancel;

    public FolderSearchDialog(Window owner, Consumer<Path> opener) {
        if (owner != null) initOwner(owner);
        setTitle(I18n.text("dialog.folderSearch.title"));
        directory.setPromptText(I18n.text("dialog.folderSearch.label.directory")); directory.setId("folder-search-directory");
        query.setPromptText(I18n.text("dialog.folderSearch.label.query")); query.setId("folder-search-query");
        replacement.setPromptText(I18n.text("dialog.folderSearch.label.replacement")); replacement.setId("folder-search-replacement");
        Button choose = FeatureUi.button(I18n.text("dialog.folderSearch.button.choose"),
                I18n.text("dialog.folderSearch.tooltip.choose"), () -> {
            DirectoryChooser chooser = new DirectoryChooser();
            var chosen = chooser.showDialog(this); if (chosen != null) directory.setText(chosen.getAbsolutePath());
        });
        search = FeatureUi.button(I18n.text("dialog.folderSearch.button.search"),
                I18n.text("dialog.folderSearch.tooltip.search"), this::search);
        search.setId("folder-search-start");
        cancel = FeatureUi.button(I18n.text("dialog.folderSearch.button.cancel"),
                I18n.text("dialog.folderSearch.tooltip.cancel"), this::cancelWork);
        apply = FeatureUi.button(I18n.text("dialog.folderSearch.button.apply"),
                I18n.text("dialog.folderSearch.tooltip.apply"), this::confirmApply);
        apply.setId("folder-search-apply");
        Button all = FeatureUi.button(I18n.text("dialog.folderSearch.button.all"),
                I18n.text("dialog.folderSearch.tooltip.all"), () -> {
            files.getItems().forEach(file -> { selected.add(file.file()); selectedHits.put(file.file(), new HashSet<>(file.hits())); }); files.refresh(); hits.refresh(); updatePreview();
        });
        Button none = FeatureUi.button(I18n.text("dialog.folderSearch.button.none"),
                I18n.text("dialog.folderSearch.tooltip.none"), () -> { selected.clear(); selectedHits.clear(); files.refresh(); hits.refresh(); updatePreview(); });
        Button close = FeatureUi.button(I18n.text("dialog.folderSearch.button.close"),
                I18n.text("dialog.folderSearch.tooltip.close"), this::close);
        files.setId("folder-search-files");
        files.setCellFactory(ignored -> new ListCell<>() {
            private final CheckBox check = new CheckBox();
            @Override protected void updateItem(FolderSearch.FileMatches file, boolean empty) {
                super.updateItem(file, empty);
                if (empty || file == null) { setGraphic(null); return; }
                check.setText(file.file() + " (" + file.hits().size() + ")");
                check.setSelected(selected.contains(file.file()));
                check.setTooltip(new Tooltip(I18n.text("dialog.folderSearch.tooltip.check")));
                check.setOnAction(event -> { if (check.isSelected()) { selected.add(file.file()); selectedHits.put(file.file(), new HashSet<>(file.hits())); }
                    else { selected.remove(file.file()); selectedHits.remove(file.file()); } hits.refresh(); updatePreview(); });
                setGraphic(check);
            }
        });
        hits.setCellFactory(view -> new ListCell<>() {
            private final CheckBox check = new CheckBox();
            @Override protected void updateItem(FolderSearch.Hit hit, boolean empty) {
                super.updateItem(hit, empty);
                if (empty || hit == null) { setGraphic(null); return; }
                var file = files.getSelectionModel().getSelectedItem();
                check.setText(WorkflowDialogs.text("line") + " " + hit.line() + ": " + hit.context());
                check.setSelected(file != null && selected.contains(file.file()) && selectedHits.getOrDefault(file.file(), Set.of()).contains(hit));
                check.setOnAction(event -> {
                    if (file == null) return;
                    var choices = selectedHits.computeIfAbsent(file.file(), path -> new HashSet<>());
                    if (check.isSelected()) choices.add(hit); else choices.remove(hit);
                    if (choices.isEmpty()) selected.remove(file.file()); else selected.add(file.file());
                    files.refresh(); updatePreview();
                }); setGraphic(check);
            }
        });
        hits.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            if (value != null) { preview.requestFocus(); } // The full before/after preview remains available below.
        });
        files.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            hits.getItems().setAll(value == null ? List.of() : value.hits()); updatePreview();
        });
        files.setOnMouseClicked(event -> {
            var file = files.getSelectionModel().getSelectedItem();
            if (event.getClickCount() == 2 && file != null && opener != null) opener.accept(file.file());
        });
        replacement.textProperty().addListener((obs, old, value) -> updatePreview());
        javafx.beans.InvalidationListener invalidate = ignored -> {
            if (!busy) {
                revision++;
                if (running != null) running.cancel(true);
                resultQuery = null; apply.setDisable(true); preview.clear();
            }
        };
        query.textProperty().addListener(invalidate); directory.textProperty().addListener(invalidate);
        regex.selectedProperty().addListener(invalidate); matchCase.selectedProperty().addListener(invalidate);
        words.selectedProperty().addListener(invalidate); patterns.textProperty().addListener(invalidate); exclusions.textProperty().addListener(invalidate);
        preview.setEditable(false); preview.setWrapText(false); preview.setId("folder-search-preview");
        status.setWrapText(true);
        patterns.setPromptText(WorkflowDialogs.text("patterns")); exclusions.setPromptText(WorkflowDialogs.text("exclusions"));
        patterns.setAccessibleText(patterns.getPromptText()); exclusions.setAccessibleText(exclusions.getPromptText());
        SplitPane split = new SplitPane(files, new VBox(8, hits, preview)); split.setDividerPositions(.40);
        VBox body = new VBox(8, new HBox(8, directory, choose), new HBox(8, query, replacement),
                new HBox(12, regex, matchCase, words), new HBox(8, new Label(WorkflowDialogs.text("patterns")), patterns), new HBox(8, new Label(WorkflowDialogs.text("exclusions")), exclusions), new HBox(8, search, cancel, all, none), split, status,
                new HBox(8, apply, restore, close));
        HBox.setHgrow(directory, Priority.ALWAYS); HBox.setHgrow(query, Priority.ALWAYS); HBox.setHgrow(replacement, Priority.ALWAYS);
        VBox.setVgrow(split, Priority.ALWAYS); body.setPadding(new Insets(12));
        Scene scene = new Scene(body, 1050, 620);
        var appCss = getClass().getResource("/de/richmarkdown/ui/app.css");
        if (appCss != null) {
            scene.getStylesheets().add(appCss.toExternalForm());
        }
        var dialogCss = getClass().getResource("/de/richmarkdown/ui/dialogs.css");
        if (dialogCss != null) {
            scene.getStylesheets().add(dialogCss.toExternalForm());
        }
        DialogButtons.applyPanelStyles(body, search);
        directory.setAccessibleText(directory.getPromptText());
        query.setAccessibleText(query.getPromptText());
        replacement.setAccessibleText(replacement.getPromptText());
        setScene(scene);
        setOnShown(event -> directory.requestFocus());
        setBusy(false);
        setOnHidden(event -> { cancelWork(); worker.shutdownNow(); files.getItems().clear(); selected.clear(); });
    }

    void search() {
        if (busy) return;
        try {
            Path root = Path.of(directory.getText());
            FolderSearch.Query request = new FolderSearch.Query(query.getText(), regex.isSelected(), matchCase.isSelected(), words.isSelected());
            request.pattern();
            List<String> requestedPatterns = java.util.Arrays.stream(patterns.getText().split(",")).map(String::strip).filter(value -> !value.isEmpty()).toList();
            Set<String> excluded = new HashSet<>(java.util.Arrays.stream(exclusions.getText().split(",")).map(String::strip).filter(value -> !value.isEmpty()).toList());
            for (String pattern : requestedPatterns) root.getFileSystem().getPathMatcher("glob:" + pattern);
            long token = ++revision;
            setBusy(true); status.setText(I18n.text("dialog.folderSearch.status.searching"));
            running = worker.submit(() -> {
                try {
                    var result = FolderSearch.search(root, request, requestedPatterns, excluded, count -> {
                        if (count % 20 == 0) Platform.runLater(() -> { if (token == revision) status.setText(WorkflowDialogs.text("files") + ": " + count); });
                    });
                    Platform.runLater(() -> {
                        if (token != revision) return;
                        resultQuery = request; files.getItems().setAll(result.files()); selected.clear(); selectedHits.clear();
                        result.files().forEach(file -> { selected.add(file.file()); selectedHits.put(file.file(), new HashSet<>(file.hits())); }); files.refresh();
                        status.setText(result.matches() + I18n.text("dialog.folderSearch.status.matchesIn")
                                + result.files().size() + I18n.text("dialog.folderSearch.status.files")
                                + String.join("\n", result.warnings()));
                        setBusy(false);
                        if (!files.getItems().isEmpty()) files.getSelectionModel().selectFirst();
                        else preview.clear();
                    });
                } catch (Exception error) { failed(token, error); }
            });
        } catch (Exception error) { status.setText(error.getMessage()); }
    }

    private void updatePreview() {
        var file = files.getSelectionModel().getSelectedItem();
        if (file == null || resultQuery == null || busy) return;
        // A preview is bounded by the engine's per-file limit and computed off the UI thread.
        String substitute = replacement.getText();
        FolderSearch.Query request = resultQuery;
        long token = ++revision;
        if (running != null) running.cancel(true);
        var selection = selectedFile(file);
        running = worker.submit(() -> {
            try {
                String after = FolderSearch.replacement(selection, request, substitute);
                String beforeExcerpt = file.original().substring(0, Math.min(16_000, file.original().length()));
                String afterExcerpt = after.substring(0, Math.min(16_000, after.length()));
                String result = I18n.text("dialog.folderSearch.preview.original") + "\n" + beforeExcerpt
                        + "\n\n" + I18n.text("dialog.folderSearch.preview.result") + "\n" + afterExcerpt;
                Platform.runLater(() -> { if (token == revision) preview.setText(result); });
            } catch (Exception error) { failed(token, error); }
        });
    }

    private void confirmApply() {
        if (busy || resultQuery == null) return;
        var chosen = files.getItems().stream().filter(file -> selected.contains(file.file())).map(this::selectedFile).filter(file -> !file.hits().isEmpty()).toList();
        if (chosen.isEmpty()) { status.setText(I18n.text("dialog.folderSearch.status.noFiles")); return; }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                chosen.size() + I18n.text("dialog.folderSearch.confirm.apply.message"),
                DialogButtons.OK, DialogButtons.CANCEL);
        confirmation.initOwner(this);
        confirmation.setHeaderText(I18n.text("dialog.folderSearch.confirm.apply.header"));
        DialogButtons.applyDialogStyles(confirmation.getDialogPane());
        confirmation.showAndWait().filter(DialogButtons.OK::equals).ifPresent(ignored -> apply(chosen));
    }
    void apply(List<FolderSearch.FileMatches> chosen) {
        if (busy || resultQuery == null || chosen.isEmpty()) return;
        var request = resultQuery; String substitute = replacement.getText(); long token = ++revision;
        setBusy(true);
        running = worker.submit(() -> {
            try {
                var backups = FolderSearch.apply(chosen, request, substitute, (file, backup) ->
                        Platform.runLater(() -> { this.backups.add(new Backup(backup, file, request, substitute)); restore.setDisable(busy); }));
                Platform.runLater(() -> {
                    if (token != revision) return;
                    status.setText(I18n.text("dialog.folderSearch.status.replaced") + backups.stream().map(Path::toString).collect(java.util.stream.Collectors.joining("\n")));
                    files.getItems().clear(); selected.clear(); preview.clear(); resultQuery = null; setBusy(false);
                });
            } catch (Exception error) { failed(token, error); }
        });
    }
    private FolderSearch.FileMatches selectedFile(FolderSearch.FileMatches file) {
        Set<FolderSearch.Hit> choices = selectedHits.getOrDefault(file.file(), Set.of());
        return new FolderSearch.FileMatches(file.file(), file.original(), file.hits().stream().filter(choices::contains).toList());
    }
    private void restoreBackups() {
        if (busy || backups.isEmpty()) return;
        ListView<Backup> list = new ListView<>(); list.getItems().setAll(backups);
        list.setCellFactory(view -> new ListCell<>() { @Override protected void updateItem(Backup backup, boolean empty) {
            super.updateItem(backup, empty); setText(empty || backup == null ? null : backup.original().file() + " ← " + backup.path());
        }});
        Dialog<Backup> dialog = new Dialog<>(); dialog.initOwner(this); dialog.setTitle(WorkflowDialogs.text("backup")); dialog.setResizable(true);
        dialog.getDialogPane().setContent(list); dialog.getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL);
        DialogButtons.applyDialogStyles(dialog.getDialogPane());
        dialog.getDialogPane().lookupButton(DialogButtons.OK).disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        dialog.setResultConverter(type -> type == DialogButtons.OK ? list.getSelectionModel().getSelectedItem() : null);
        dialog.showAndWait().ifPresent(backup -> {
            long token = ++revision; setBusy(true);
            running = worker.submit(() -> {
                try {
                    Path undo = FolderSearch.restore(backup.path(), backup.original(), backup.query(), backup.replacement());
                    Platform.runLater(() -> { if (token != revision) return; backups.remove(backup); status.setText(backup.original().file() + " ← " + backup.path() + "\n" + WorkflowDialogs.text("backup") + ": " + undo); resultQuery = null; files.getItems().clear(); selectedHits.clear(); selected.clear(); preview.clear(); setBusy(false); });
                } catch (Exception failure) { failed(token, failure); }
            });
        });
    }
    private void failed(long token, Exception error) {
        Platform.runLater(() -> {
            if (token != revision) return;
            setBusy(false);             status.setText(error.getMessage() + I18n.text("dialog.folderSearch.status.warning"));
        });
    }
    void cancelWork() {
        revision++; if (running != null) running.cancel(true); running = null; setBusy(false);
        status.setText(I18n.text("dialog.folderSearch.status.cancelled"));
    }
    private void setBusy(boolean value) {
        busy = value; search.setDisable(value); apply.setDisable(value || resultQuery == null); cancel.setDisable(!value);
        patterns.setDisable(value); exclusions.setDisable(value); hits.setDisable(value); files.setDisable(value); restore.setDisable(value || backups.isEmpty());
        directory.setDisable(value); query.setDisable(value); replacement.setDisable(value);
        regex.setDisable(value); matchCase.setDisable(value); words.setDisable(value);
    }
}
