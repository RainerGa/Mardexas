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
