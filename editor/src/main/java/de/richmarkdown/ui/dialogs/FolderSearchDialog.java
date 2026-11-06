package de.richmarkdown.ui.dialogs;

import de.richmarkdown.findreplace.FolderSearch;
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
import static de.richmarkdown.ui.FeatureUi.text;

/** One cancellable worker per open dialog, bounded raw-Markdown search and reviewed replacements. */
public final class FolderSearchDialog extends Stage {
    final TextField directory = new TextField();
    final TextField query = new TextField();
    final TextField replacement = new TextField();
    final CheckBox regex = new CheckBox(text("Regulärer Ausdruck", "Regular expression"));
    final CheckBox matchCase = new CheckBox(text("Groß-/Kleinschreibung", "Match case"));
    final CheckBox words = new CheckBox(text("Ganze Wörter", "Whole words"));
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
        setTitle(text("In Markdown-Ordner suchen und ersetzen", "Search and replace in Markdown folder"));
        directory.setPromptText(text("Verzeichnis", "Folder")); directory.setId("folder-search-directory");
        query.setPromptText(text("Suchbegriff", "Query")); query.setId("folder-search-query");
        replacement.setPromptText(text("Ersatz", "Replacement")); replacement.setId("folder-search-replacement");
        Button choose = FeatureUi.button(text("Ordner…", "Folder…"), text("Suchverzeichnis auswählen", "Choose search folder"), () -> {
            DirectoryChooser chooser = new DirectoryChooser();
            var chosen = chooser.showDialog(this); if (chosen != null) directory.setText(chosen.getAbsolutePath());
        });
        search = FeatureUi.button(text("Suchen", "Search"), text("Markdown-Dateien rekursiv suchen; maximal 32 MiB", "Search Markdown files recursively; maximum 32 MiB"), this::search);
        search.setId("folder-search-start");
        cancel = FeatureUi.button(text("Abbrechen", "Cancel"), text("Laufende Suche oder Ersetzung abbrechen", "Cancel the running search or replacement"), this::cancelWork);
        apply = FeatureUi.button(text("Ausgewählte Dateien ersetzen…", "Replace selected files…"), text("Vorschau prüfen; jede geänderte Datei erhält eine Sicherungskopie", "Review preview; each changed file gets a backup"), this::confirmApply);
        apply.setId("folder-search-apply");
        Button all = FeatureUi.button(text("Alle auswählen", "Select all"), text("Alle gefundenen Dateien auswählen", "Select every matching file"), () -> {
            files.getItems().forEach(file -> selected.add(file.file())); files.refresh();
        });
        Button none = FeatureUi.button(text("Keine auswählen", "Select none"), text("Dateiauswahl leeren", "Clear file selection"), () -> { selected.clear(); files.refresh(); });
        Button close = FeatureUi.button(text("Schließen", "Close"), text("Dialog schließen und laufende Arbeit abbrechen", "Close dialog and cancel running work"), this::close);
        files.setId("folder-search-files");
        files.setCellFactory(ignored -> new ListCell<>() {
            private final CheckBox check = new CheckBox();
            @Override protected void updateItem(FolderSearch.FileMatches file, boolean empty) {
                super.updateItem(file, empty);
                if (empty || file == null) { setGraphic(null); return; }
                check.setText(file.file() + " (" + file.hits().size() + ")");
                check.setSelected(selected.contains(file.file()));
                check.setTooltip(new Tooltip(text("Datei für Ersetzung auswählen", "Select file for replacement")));
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
        setScene(new Scene(body, 1050, 620));
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
            setBusy(true); status.setText(text("Suche läuft…", "Searching…"));
            running = worker.submit(() -> {
                try {
                    var result = FolderSearch.search(root, request);
                    Platform.runLater(() -> {
                        if (token != revision) return;
                        resultQuery = request; files.getItems().setAll(result.files()); selected.clear();
                        result.files().forEach(file -> selected.add(file.file())); files.refresh();
                        status.setText(result.matches() + text(" Treffer in ", " matches in ") + result.files().size()
                                + text(" Dateien. ", " files. ") + String.join("\n", result.warnings()));
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
                String result = text("ORIGINAL (Ausschnitt)\n", "ORIGINAL (excerpt)\n") + beforeExcerpt
                        + text("\n\nERGEBNIS (Ausschnitt)\n", "\n\nRESULT (excerpt)\n") + afterExcerpt;
                Platform.runLater(() -> { if (token == revision) preview.setText(result); });
            } catch (Exception error) { failed(token, error); }
        });
    }

    private void confirmApply() {
        if (busy || resultQuery == null) return;
        var chosen = files.getItems().stream().filter(file -> selected.contains(file.file())).toList();
        if (chosen.isEmpty()) { status.setText(text("Keine Dateien ausgewählt.", "No files selected.")); return; }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                chosen.size() + text(" Dateien mit der angezeigten Suche ersetzen? Sicherungskopien (*.before-replace-*.bak) bleiben im jeweiligen Ordner. Bereits geöffnete, ungespeicherte Dokumente werden nicht verändert.",
                        " files will be replaced using the displayed search. Backups (*.before-replace-*.bak) remain beside each file. Open, unsaved editor documents are unchanged."), ButtonType.OK, ButtonType.CANCEL);
        confirmation.initOwner(this); confirmation.setHeaderText(text("Ersetzungen übernehmen", "Apply replacements"));
        confirmation.showAndWait().filter(ButtonType.OK::equals).ifPresent(ignored -> apply(chosen));
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
                    status.setText(text("Ersetzt. Sicherungen:\n", "Replaced. Backups:\n") + backups.stream().map(Path::toString).collect(java.util.stream.Collectors.joining("\n")));
                    files.getItems().clear(); selected.clear(); preview.clear(); resultQuery = null; setBusy(false);
                });
            } catch (Exception error) { failed(token, error); }
        });
    }
    private void failed(long token, Exception error) {
        Platform.runLater(() -> {
            if (token != revision) return;
            setBusy(false); status.setText(error.getMessage() + text("\nBei abgebrochener Ersetzung können bereits bearbeitete Dateien Sicherungen im gleichen Ordner haben.",
                    "\nAfter an interrupted replacement, files already processed may have backups in their folders."));
        });
    }
    void cancelWork() {
        revision++; if (running != null) running.cancel(true); running = null; setBusy(false);
        status.setText(text("Abgebrochen. Bereits vorgenommene Ersetzungen haben Sicherungskopien.", "Cancelled. Replacements already made have backups."));
    }
    private void setBusy(boolean value) {
        busy = value; search.setDisable(value); apply.setDisable(value || resultQuery == null); cancel.setDisable(!value);
        directory.setDisable(value); query.setDisable(value); replacement.setDisable(value);
        regex.setDisable(value); matchCase.setDisable(value); words.setDisable(value);
    }
}
