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
