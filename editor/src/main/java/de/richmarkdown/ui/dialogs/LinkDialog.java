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
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * Erfasst die Ziel-URL, die auf die aktuelle Textauswahl angewendet werden soll.
 *
 * <p>Der Dialog validiert das URL-Schema nicht. Die Ausgabe sollte über die
 * Sicherheitsprüfung in {@link de.richmarkdown.editor.MarkdownEditor}
 * beziehungsweise die Link-Callback-Allowlist verarbeitet werden.
 */
public class LinkDialog extends Dialog<String> {

    private final TextField urlField = new TextField();
    private final Label error = new Label();

    /** Erstellt den lokalisierten Dialog zur Eingabe einer Link-URL. */
    public LinkDialog() {
        setTitle(I18n.text("dialog.link.title"));
        setHeaderText(I18n.text("dialog.link.header"));

        urlField.setPromptText(I18n.text("dialog.link.url"));
        urlField.setAccessibleText(I18n.text("dialog.link.url"));
        urlField.setId("link-url");

        error.getStyleClass().add("rme-dialog-error");
        error.setWrapText(true);
        error.setVisible(false);
        error.setManaged(false);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label(I18n.text("dialog.link.url")), 0, 0);
        grid.add(urlField, 1, 0);

        VBox content = new VBox(8, grid, error);
        content.setId("link-dialog-content");
        getDialogPane().setContent(content);

        DialogButtons.applyDialogStyles(getDialogPane());
        getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL);

        Button ok = (Button) getDialogPane().lookupButton(DialogButtons.OK);
        ok.setDefaultButton(true);
        ok.setDisable(true);

        urlField.textProperty().addListener((observable, oldValue, newValue) -> {
            boolean valid = newValue != null && !newValue.isBlank();
            ok.setDisable(!valid);
            error.setVisible(!valid);
            error.setManaged(!valid);
            error.setText(valid ? "" : I18n.text("dialog.validation.required"));
        });

        setResultConverter(buttonType ->
                buttonType == DialogButtons.OK ? urlField.getText() : null);

        setOnShown(event -> urlField.requestFocus());
    }

    /**
     * Liefert das Eingabefeld der Link-URL.
     *
     * @return einzeiliges URL-Eingabefeld
     */
    public TextField getUrlField() {
        return urlField;
    }
}
