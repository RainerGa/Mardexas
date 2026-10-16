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
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Erfasst mehrzeiligen Quelltext für einen neuen Codeblock.
 *
 * <p>Bei Bestätigung liefert {@link Dialog#showAndWait()} den eingegebenen
 * Inhalt unverändert zurück. Bei Abbruch ist das Ergebnis leer; die Umwandlung
 * in Markdown erfolgt durch die aufrufende Editor-Fassade.
 */
public class CodeBlockDialog extends Dialog<String> {

    private final TextArea codeArea = new TextArea();

    /** Erstellt und konfiguriert den lokalisierten Codeblockdialog. */
    public CodeBlockDialog() {
        setTitle(I18n.text("dialog.code.title"));
        setHeaderText(I18n.text("dialog.code.header"));

        codeArea.setPromptText(I18n.text("dialog.code.prompt"));
        VBox box = new VBox(8, new Label(I18n.text("dialog.code.label")), codeArea);
        VBox.setVgrow(codeArea, Priority.ALWAYS);
        getDialogPane().setContent(box);
        getDialogPane().setPrefSize(500, 300);

        getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL);
        setResultConverter(buttonType ->
                buttonType == DialogButtons.OK ? codeArea.getText() : null);
    }

    /**
     * Liefert das Textfeld für den Codeinhalt.
     *
     * <p>Der Zugriff ermöglicht Aufrufern und UI-Tests, den Dialog vor der
     * Anzeige vorzubelegen beziehungsweise den eingegebenen Wert auszulesen.
     *
     * @return mehrzeiliges Eingabefeld des Dialogs
     */
    public TextArea getCodeArea() {
        return codeArea;
    }
}
