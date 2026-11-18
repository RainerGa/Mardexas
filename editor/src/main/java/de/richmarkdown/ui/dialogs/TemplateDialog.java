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
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dialog zur Auswahl einer Dokumentvorlage (AddFeatures #17).
 *
 * <p>Vorlagen werden aus dem Klassenpfad ({@code /templates/}) geladen und,
 * sofern konfiguriert, aus einem Dateisystem-Verzeichnis. Der Anwender wählt
 * eine Vorlage aus; der Dialog liefert den Markdown-Inhalt der ausgewählten
 * Datei zurück. Ein konfiguriertes, ungültiges Verzeichnis wird sicher
 * ignoriert (Degradation auf Klassenpfad-Vorlagen).
 */
public class TemplateDialog extends Dialog<String> {

    private static final String DIRECTORY_KEY = "templates.directory";
    private static final String[] CLASSPATH_NAMES = {
            "article", "letter", "meeting-notes", "readme"
    };

    private final List<TemplateInfo> templates = new ArrayList<>();
    private boolean templatesLoaded;
    private Path configuredDirectory;
    private final Label directoryStatus = new Label();
    private ListView<TemplateInfo> list;
    private TextArea preview;
    private final javafx.collections.ObservableList<TemplateInfo> allTemplates = javafx.collections.FXCollections.observableArrayList();
    private final javafx.scene.control.TextField filter = new javafx.scene.control.TextField();
    private final Label origin = new Label();
    private final Label warnings = new Label();
    private final Map<String, String> sources = new LinkedHashMap<>();
    private javafx.concurrent.Task<LoadedTemplates> loading;
    private long loadRevision;
    private record LoadedTemplates(List<TemplateInfo> templates, Map<String, String> sources, String warnings) { }
    public boolean isTemplatesLoaded() { return templatesLoaded; }

    /**
     * Erstellt den Vorlagen-Dialog.
     *
     * @param owner Besitzer-Fenster (dient als Dialog-Owner)
     */
    public TemplateDialog(Window owner) {
        initOwner(owner);
        setTitle(I18n.text("dialog.template.title"));
        setHeaderText(I18n.text("dialog.template.header"));
        setResizable(true);

        configuredDirectory = readConfiguredDirectory();

        list = new ListView<>();
        list.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        list.setCellFactory(param -> new TemplateCell());

        var filtered = new javafx.collections.transformation.FilteredList<>(allTemplates, item -> true);
        list.setItems(filtered); filter.setPromptText(WorkflowDialogs.text("search"));
        filter.textProperty().addListener((obs, old, value) -> { filtered.setPredicate(item -> item.label().toLowerCase(java.util.Locale.ROOT).contains(value.toLowerCase(java.util.Locale.ROOT))); list.getSelectionModel().selectFirst(); });
        preview = new TextArea();
        preview.setEditable(false);
        preview.setWrapText(true);
        preview.setPrefRowCount(12);
        preview.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        list.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                preview.setText(sel.content());
                origin.setText(WorkflowDialogs.text("source") + ": " + sources.getOrDefault(sel.id(), "/templates/" + sel.id() + ".md"));
            }
        });

        Button directoryButton = new Button(I18n.text("dialog.template.button.directory"));
        directoryButton.setTooltip(new javafx.scene.control.Tooltip(I18n.text("dialog.template.setting")));
        directoryButton.setOnAction(event -> chooseDirectory());
        directoryStatus.setWrapText(true);
        HBox directoryBar = new HBox(8, directoryButton, directoryStatus);
        directoryBar.setId("template-directory-bar");

        Button reset = WorkflowDialogs.button("reset", () -> { I18n.setPreference(DIRECTORY_KEY, ""); configuredDirectory = null; listReload(); });
        Button reload = WorkflowDialogs.button("reload", this::listReload);
        Button rendered = WorkflowDialogs.button("preview", () -> {
            TemplateInfo selected = list.getSelectionModel().getSelectedItem(); if (selected == null) return;
            javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>(); dialog.initOwner(getDialogPane().getScene().getWindow()); dialog.setTitle(selected.label()); dialog.setResizable(true);
            dialog.getDialogPane().setContent(WorkflowDialogs.markdownPreview(selected.content())); dialog.getDialogPane().getButtonTypes().add(DialogButtons.CLOSE); DialogButtons.applyDialogStyles(dialog.getDialogPane()); dialog.show();
        });
        warnings.setWrapText(true);
        VBox content = new VBox(8.0, directoryBar, new HBox(8, filter, reload, reset, rendered), list, origin, preview, warnings);
        content.setPrefSize(700, 500);
        VBox.setVgrow(preview, Priority.ALWAYS);
        VBox.setVgrow(list, Priority.NEVER);
        getDialogPane().setContent(content);

        DialogButtons.applyDialogStyles(getDialogPane());
        getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL);

        Button ok = (Button) getDialogPane().lookupButton(DialogButtons.OK);
        ok.setDefaultButton(true);
        ok.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());

        setResultConverter(dialogButton -> {
            if (dialogButton == DialogButtons.OK) {
                TemplateInfo selected = list.getSelectionModel().getSelectedItem();
                return selected != null ? selected.content() : null;
            }
            return null;
        });

        setOnShowing(event -> { if (!templatesLoaded) listReload(); });
        setOnHidden(event -> {
            if (loading != null && !templatesLoaded) { loadRevision++; loading.cancel(true); }
        });
        setOnShown(event -> list.requestFocus());
    }

    /**
     * Liefert das konfigurierte Vorlagenverzeichnis oder {@code null}.
     *
     * @return konfiguriertes Verzeichnis oder {@code null}
     */
    public Path getConfiguredDirectory() {
        return configuredDirectory;
    }

    /** Opens the shared templates-directory setting, also available from File → Settings. */
    public static void configureDirectory(Window owner) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(I18n.text("dialog.template.directory.title"));
        Path current = readConfiguredDirectory();
        if (current != null) chooser.setInitialDirectory(current.toFile());
        var chosen = chooser.showDialog(owner);
        if (chosen != null) I18n.setPreference(DIRECTORY_KEY, chosen.toPath().toString());
    }

    private void chooseDirectory() {
        configureDirectory(getDialogPane().getScene().getWindow());
        configuredDirectory = readConfiguredDirectory();
        listReload();
    }

    private void listReload() {
        if (loading != null) loading.cancel(true);
        long revision = ++loadRevision;
        templatesLoaded = false;
        updateDirectoryStatus();
        Path directory = configuredDirectory;
        allTemplates.clear(); preview.clear(); origin.setText(""); warnings.setText(I18n.text("dialog.writingLibrary.status.processing"));
        loading = new javafx.concurrent.Task<>() {
            @Override protected LoadedTemplates call() { return loadTemplates(directory); }
        };
        var request = loading;
        request.setOnSucceeded(event -> {
            if (revision != loadRevision) return;
            var loaded = request.getValue();
            templates.clear(); templates.addAll(loaded.templates()); sources.clear(); sources.putAll(loaded.sources());
            warnings.setText(loaded.warnings()); allTemplates.setAll(templates); templatesLoaded = true;
            list.getSelectionModel().selectFirst();
        });
        request.setOnFailed(event -> {
            if (revision != loadRevision) return;
            warnings.setText(request.getException().getMessage()); templatesLoaded = true;
        });
        Thread.ofVirtual().name("template-loader").start(request);
    }

    private LoadedTemplates loadTemplates(Path configuredDirectory) {
        StringBuilder warnings = new StringBuilder();
        Map<String, String> sources = new LinkedHashMap<>();
        Map<String, TemplateInfo> byId = new LinkedHashMap<>();
        for (String name : CLASSPATH_NAMES) {
            String content = loadTemplateContent("/templates/" + name + ".md");
            if (content != null) {
                byId.put(name, new TemplateInfo(name, labelFor(name), content));
                sources.put(name, "/templates/" + name + ".md");
            }
        }
        if (configuredDirectory != null && Files.isDirectory(configuredDirectory)) {
            try (var stream = Files.list(configuredDirectory)) {
                for (Path file : stream.limit(1_001).filter(Files::isRegularFile)
                        .filter(path -> java.util.Objects.toString(path.getFileName(), "").toLowerCase(java.util.Locale.ROOT).matches(".*\\.(md|markdown)$"))
                        .limit(201).sorted(java.util.Comparator.comparing(path -> java.util.Objects.toString(path.getFileName(), ""))).toList()) {
                    if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
                    String filename = java.util.Objects.toString(file.getFileName(), "");
                    if (byId.size() >= 204) { warnings.append("200 ").append(WorkflowDialogs.text("files")); break; }
                    String id = filename.substring(0, filename.lastIndexOf('.'));
                    try {
                        String content;
                        try (InputStream input = Files.newInputStream(file)) {
                            byte[] bytes = input.readNBytes(262_145);
                            if (bytes.length > 262_144) throw new IOException("256 KiB: " + file);
                            content = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                        }
                        byId.put(id, new TemplateInfo(id, id, content));
                        sources.put(id, file.toString());
                    } catch (IOException | SecurityException ignored) {
                        warnings.append(file).append(": ").append(ignored.getMessage()).append("\n");
                        // A single unreadable template must not hide the built-in templates.
                    }
                }
            } catch (IOException | SecurityException | java.io.UncheckedIOException ignored) {
                warnings.append(ignored.getMessage());
                // Fall back to bundled templates if directory access fails.
            }
        }
        return new LoadedTemplates(List.copyOf(byId.values()), Map.copyOf(sources), warnings.toString());
    }

    private String loadTemplateContent(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                return null;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static Path readConfiguredDirectory() {
        String value = I18n.preference(DIRECTORY_KEY, null);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(value);
            return Files.isDirectory(path) ? path : null;
        } catch (InvalidPathException | SecurityException invalid) {
            return null;
        }
    }

    private void updateDirectoryStatus() {
        configuredDirectory = readConfiguredDirectory();
        String configured = I18n.preference(DIRECTORY_KEY, "");
        directoryStatus.setText(configuredDirectory != null ? configuredDirectory.toString()
                : configured.isBlank() ? "" : I18n.text("dialog.template.directory.missing"));
    }

    private String labelFor(String name) {
        return switch (name) {
            case "article" -> I18n.text("template.article");
            case "letter" -> I18n.text("template.letter");
            case "meeting-notes" -> I18n.text("template.meetingNotes");
            case "readme" -> I18n.text("template.readme");
            default -> name;
        };
    }

    /** Metadaten einer Vorlage. */
    private record TemplateInfo(String id, String label, String content) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** Listenzelle: zeigt Label und eine Kurz-Vorschau des Inhalts. */
    private static class TemplateCell extends ListCell<TemplateInfo> {
        private final Text label = new Text();
        private final Text preview = new Text();

        TemplateCell() {
            label.getStyleClass().add("rme-template-label");
            preview.getStyleClass().add("rme-template-preview");
            setGraphic(new javafx.scene.control.Label());
        }

        @Override
        protected void updateItem(TemplateInfo item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                label.setText(item.label());
                String shortPreview = item.content().length() > 80
                        ? item.content().substring(0, 80) + "…"
                        : item.content();
                preview.setText(shortPreview);
                VBox box = new VBox(4.0, label, preview);
                setGraphic(box);
            }
        }
    }
}
