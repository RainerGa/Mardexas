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
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Window;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog zur Auswahl einer Dokumentvorlage (AddFeatures #17).
 *
 * <p>Vorlagen werden als {@code .md}-Dateien aus dem Klassenpfad
 * ({@code /templates/}) geladen. Der Anwender waehlt eine Vorlage aus; der
 * Dialog liefert den Markdown-Inhalt der ausgewaehlten Datei zurueck.
 */
public class TemplateDialog extends Dialog<String> {

    private final List<TemplateInfo> templates = new ArrayList<>();
    private boolean templatesLoaded;

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

        ListView<TemplateInfo> list = new ListView<>();
        list.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        list.setCellFactory(param -> new TemplateCell());

        TextArea preview = new TextArea();
        preview.setEditable(false);
        preview.setWrapText(true);
        preview.setPrefRowCount(12);
        preview.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        list.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                preview.setText(sel.content());
            }
        });
        VBox content = new VBox(8.0, list, preview);
        content.setPrefSize(700, 500);
        VBox.setVgrow(preview, javafx.scene.layout.Priority.ALWAYS);
        VBox.setVgrow(list, javafx.scene.layout.Priority.NEVER);
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                TemplateInfo selected = list.getSelectionModel().getSelectedItem();
                return selected != null ? selected.content() : null;
            }
            return null;
        });

        setOnShowing(event -> {
            if (!templatesLoaded) {
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

    private void loadTemplates() {
        // Vorlagen-Hartcodierung ueber bekannte Dateinamen (kein Ressourcen-Listing
        // im Modulsystem verfuegbar)
        String[] names = {
                "article", "letter", "meeting-notes", "readme"
        };
        for (String name : names) {
            String content = loadTemplateContent(name);
            if (content != null) {
                templates.add(new TemplateInfo(name, labelFor(name), content));
            }
        }
    }

    private String loadTemplateContent(String name) {
        try (InputStream is = getClass().getResourceAsStream("/templates/" + name + ".md")) {
            if (is == null) {
                return null;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
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
