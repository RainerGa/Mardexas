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
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

/**
 * Erfasst die Ziel-URL eines in das Dokument einzufügenden Bildes.
 *
 * <p>Bei Bestätigung wird der Feldinhalt ohne Normalisierung oder
 * Netzwerkprüfung als Ergebnis zurückgegeben. Validierung und Verwendung der
 * URL liegen bei der aufrufenden Ebene.
 */
public class ImageDialog extends Dialog<String> {

    private final TextField urlField = new TextField();

    /** Erstellt den lokalisierten Dialog zur Eingabe einer Bild-URL. */
    public ImageDialog() {
        setTitle(I18n.text("dialog.image.title"));
        setHeaderText(I18n.text("dialog.image.header"));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label(I18n.text("dialog.image.path")), 0, 0);
        grid.add(urlField, 1, 0);
        getDialogPane().setContent(grid);

        getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL);
        setResultConverter(buttonType ->
                buttonType == DialogButtons.OK ? urlField.getText() : null);
    }

    /**
     * Liefert das Eingabefeld der Bild-URL.
     *
     * @return einzeiliges URL-Eingabefeld
     */
    public TextField getUrlField() {
        return urlField;
    }
}
