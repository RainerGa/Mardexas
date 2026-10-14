package de.richmarkdown.ui;

import com.gluonhq.richtextarea.RichTextArea;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import java.text.MessageFormat;

/**
 * Statusleiste des Editors mit Änderungsstatus, Cursorposition, Absatzstil und
 * optionalen Bedienelementen wie der Zoomsteuerung.
 *
 * <p>Die Werte werden an die Properties der {@link MarkdownEditor}-Fassade
 * gebunden, sodass Änderungen im Dokument ohne Polling angezeigt werden.
 */
public class StatusBar extends HBox {

    private final Label statusLabel = new Label();
    private final Label positionLabel = new Label();
    private final Label styleLabel = new Label();

    /**
     * Erstellt eine Statusleiste, die sich an die Zustände des Editors bindet.
     *
     * @param editor Editor-Fassade, deren Änderungs-, Caret-, Text- und
     *               Formatierungszustände dargestellt werden
     */
    public StatusBar(MarkdownEditor editor) {
        RichTextArea control = editor.getControl();
        getStyleClass().add("rme-status-bar");
        setSpacing(12);
        setPadding(new Insets(4, 8, 4, 8));

        // '*' marker für ungespeicherte Änderungen (PROJEKTPLAN 7.1)
        statusLabel.textProperty().bind(
                Bindings.when(editor.modifiedProperty()).then("*").otherwise(""));
        positionLabel.textProperty().bind(Bindings.createStringBinding(
                () -> MessageFormat.format(I18n.text("status.position"),
                        editor.caretPositionProperty().get(), editor.textLengthProperty().get()),
                editor.caretPositionProperty(), editor.textLengthProperty()));
        // Stil hängt von beiden Dekorations-Properties ab (Caret und Absatz)
        styleLabel.textProperty().bind(Bindings.createStringBinding(
                () -> MessageFormat.format(I18n.text("status.style"),
                        CaretStyle.detect(control).getLabel()),
                control.decorationAtCaretProperty(),
                control.decorationAtParagraphProperty()));

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(statusLabel, spacer, styleLabel, positionLabel);
    }

    /**
     * Liefert das Label, das bei ungespeicherten Änderungen markiert wird.
     *
     * @return Label für den Indikator ungespeicherter Änderungen
     */
    public Label getStatusLabel() {
        return statusLabel;
    }

    /**
     * Liefert das Label mit Caret-Position und Dokumentlänge.
     *
     * @return Label mit aktueller Cursor- und Dokumentposition
     */
    public Label getPositionLabel() {
        return positionLabel;
    }

    /**
     * Liefert das Label des am Caret erkannten Absatzformats.
     *
     * @return Label des Absatzformats an der Caret-Position
     */
    public Label getStyleLabel() {
        return styleLabel;
    }

    /**
     * Fügt die angegebenen Steuerelemente am Ende der Statusleiste ein.
     *
     * @param nodes Steuerelemente, beispielsweise Zoomregler und Prozentanzeige
     */
    public void addZoomControls(javafx.scene.Node... nodes) {
        getChildren().addAll(nodes);
    }
}
