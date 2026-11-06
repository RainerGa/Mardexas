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
 * Bestätigungsdialog beim Klick auf einen HTTP/HTTPS/mailto-Link.
 *
 * <p>Zeigt die Ziel-URL an und fragt den Benutzer, ob der Link im
 * Standardbrowser geöffnet werden soll. Verhalten analog zu Microsoft Word
 * oder LibreOffice Writer.</p>
 */
public class LinkConfirmDialog extends Dialog<javafx.scene.control.ButtonType> {

    private final TextField urlField = new TextField();

    /**
     * Erstellt den lokalisierten Bestätigungsdialog für die angegebene URL.
     *
     * @param url die Ziel-URL, die geöffnet werden soll
     */
    public LinkConfirmDialog(String url) {
        setTitle(I18n.text("dialog.linkConfirm.title"));
        setHeaderText(I18n.text("dialog.linkConfirm.header"));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label(I18n.text("dialog.linkConfirm.url")), 0, 0);
        grid.add(urlField, 1, 0);

        urlField.setText(url);
        urlField.setEditable(false);
        urlField.setPrefColumnCount(50);
        urlField.setId("urlField");

        getDialogPane().setContent(grid);

        getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL);

        setResultConverter(buttonType ->
                buttonType == DialogButtons.OK ? DialogButtons.OK : null);
    }
}