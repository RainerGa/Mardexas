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
            files.getItems().forEach(file -> selected.add(file.file())); files.refresh();
        });
        Button none = FeatureUi.button(I18n.text("dialog.folderSearch.button.none"),
                I18n.text("dialog.folderSearch.tooltip.none"), () -> { selected.clear(); files.refresh(); });
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
                check.setOnAction(event -> { if (check.isSelected()) selected.add(file.file()); else selected.remove(file.file()); });
                setGraphic(check);
            }
        });
        files.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> updatePreview());
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
        words.selectedProperty().addListener(invalidate);
        preview.setEditable(false); preview.setWrapText(false); preview.setId("folder-search-preview");
        status.setWrapText(true);
        SplitPane split = new SplitPane(files, preview); split.setDividerPositions(.40);
        VBox body = new VBox(8, new HBox(8, directory, choose), new HBox(8, query, replacement),
                new HBox(12, regex, matchCase, words), new HBox(8, search, cancel, all, none), split, status,
                new HBox(8, apply, close));
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
            long token = ++revision;
            setBusy(true); status.setText(I18n.text("dialog.folderSearch.status.searching"));
            running = worker.submit(() -> {
                try {
                    var result = FolderSearch.search(root, request);
                    Platform.runLater(() -> {
                        if (token != revision) return;
                        resultQuery = request; files.getItems().setAll(result.files()); selected.clear();
                        result.files().forEach(file -> selected.add(file.file())); files.refresh();
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
        running = worker.submit(() -> {
            try {
                String after = FolderSearch.replacement(file, request, substitute);
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
        var chosen = files.getItems().stream().filter(file -> selected.contains(file.file())).toList();
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
                var backups = FolderSearch.apply(chosen, request, substitute);
                Platform.runLater(() -> {
                    if (token != revision) return;
                    status.setText(I18n.text("dialog.folderSearch.status.replaced") + backups.stream().map(Path::toString).collect(java.util.stream.Collectors.joining("\n")));
                    files.getItems().clear(); selected.clear(); preview.clear(); resultQuery = null; setBusy(false);
                });
            } catch (Exception error) { failed(token, error); }
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
        directory.setDisable(value); query.setDisable(value); replacement.setDisable(value);
        regex.setDisable(value); matchCase.setDisable(value); words.setDisable(value);
    }
}
