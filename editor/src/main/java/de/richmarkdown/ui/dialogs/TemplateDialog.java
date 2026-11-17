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

        preview = new TextArea();
        preview.setEditable(false);
        preview.setWrapText(true);
        preview.setPrefRowCount(12);
        preview.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        list.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                preview.setText(sel.content());
            }
        });

        Button directoryButton = new Button(I18n.text("dialog.template.button.directory"));
        directoryButton.setTooltip(new javafx.scene.control.Tooltip(I18n.text("dialog.template.setting")));
        directoryButton.setOnAction(event -> chooseDirectory());
        directoryStatus.setWrapText(true);
        HBox directoryBar = new HBox(8, directoryButton, directoryStatus);
        directoryBar.setId("template-directory-bar");

        VBox content = new VBox(8.0, directoryBar, list, preview);
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

        setOnShowing(event -> {
            if (!templatesLoaded) {
                updateDirectoryStatus();
                loadTemplates();
                list.getItems().setAll(templates);
                templatesLoaded = true;
                if (!templates.isEmpty()) {
                    list.getSelectionModel().select(0);
                    preview.setText(templates.get(0).content());
                }
            }
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
        templatesLoaded = false;
        updateDirectoryStatus();
        loadTemplates();
        list.getItems().setAll(templates);
        if (!templates.isEmpty()) {
            list.getSelectionModel().select(0);
            preview.setText(templates.get(0).content());
        } else {
            preview.setText("");
        }
        templatesLoaded = true;
    }

    private void loadTemplates() {
        Map<String, TemplateInfo> byId = new LinkedHashMap<>();
        for (String name : CLASSPATH_NAMES) {
            String content = loadTemplateContent("/templates/" + name + ".md");
            if (content != null) {
                byId.put(name, new TemplateInfo(name, labelFor(name), content));
            }
        }
        if (configuredDirectory != null && Files.isDirectory(configuredDirectory)) {
            try (var stream = Files.list(configuredDirectory)) {
                for (Path file : stream.filter(Files::isRegularFile)
                        .filter(path -> java.util.Objects.toString(path.getFileName(), "").endsWith(".md"))
                        .sorted(java.util.Comparator.comparing(path -> java.util.Objects.toString(path.getFileName(), ""))).toList()) {
                    String filename = java.util.Objects.toString(file.getFileName(), "");
                    String id = filename.substring(0, filename.length() - 3);
                    try {
                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        byId.put(id, new TemplateInfo(id, id, content));
                    } catch (IOException | SecurityException ignored) {
                        // A single unreadable template must not hide the built-in templates.
                    }
                }
            } catch (IOException | SecurityException | java.io.UncheckedIOException ignored) {
                // Fall back to bundled templates if directory access fails.
            }
        }
        templates.clear();
        templates.addAll(byId.values());
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
