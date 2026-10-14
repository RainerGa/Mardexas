package de.richmarkdown.editor;

import com.gluonhq.richtextarea.RichTextArea;
import com.gluonhq.richtextarea.model.Document;
import de.richmarkdown.FxTestUtil;
import javafx.event.ActionEvent;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testfx.util.WaitForAsyncUtils.waitForFxEvents;

/**
 * TDD: Caret-Navigation mit der Ende-Taste nach Mausklick.
 *
 * Repro des gemeldeten Fehlers (PROJEKTPLAN Kap. 12): Dokument mit vier Zeilen,
 * Mausklick vor das erste Zeichen der zweiten Zeile, dann Ende-Taste. Erwartet:
 * Caret am Ende der zweiten Zeile (hinter der "2"). Fehler: Caret steht vor der
 * "2", also ein Zeichen vor dem Zeilenende.
 *
 * Der Mausklick wird über den TestFX-Roboter (echter Dispatch-Pfad inklusive
 * Picking) ausgeführt; die Ende-Taste wird als synthetisches KeyEvent direkt
 * auf das Control gespielt – genau so, wie es die Skin per INPUT_MAP tut
 * (Muster aus MarkdownEditorTest).
 */
@ExtendWith(ApplicationExtension.class)
class CaretEndKeyTest {

    private MarkdownEditor editor;
    private RichTextArea rta;

    @Start
    public void start(Stage stage) {
        editor = new MarkdownEditor();
        rta = editor.getControl();
        stage.setScene(new Scene(new StackPane(rta), 600, 400));
        stage.show();
    }

    @Test
    @DisplayName("Ende nach Mausklick vor das erste Zeichen springt an das Zeilenende")
    void endKeyAfterClickBeforeFirstCharMovesToEndOfLine(FxRobot robot) {
        // given: vier Zeilen – Zeile 2 belegt die Dokumentindizes [7, 14)
        openDocument("Zeile1\nZeile2\nZeile3\nZeile4");
        waitForFxEvents();

        // when: Mausklick vor das "Z" der zweiten Zeile
        Point2D clickPoint = clickPointBeforeFirstChar("Zeile2");
        robot.clickOn(clickPoint.getX(), clickPoint.getY());
        waitForFxEvents();

        // then: Caret steht am Anfang der zweiten Zeile (Index 7)
        assertEquals(7, rta.getCaretPosition(),
                "Klick vor das erste Zeichen muss den Caret an den Zeilenanfang setzen");

        // when: Ende-Taste
        pressEnd();

        // then: Caret steht am Ende der zweiten Zeile (Index 13, hinter der "2")
        assertEquals(13, rta.getCaretPosition(),
                "Ende-Taste muss den Caret an das Ende der Zeile setzen");
    }

    // ---------- Helfer ----------

    private void openDocument(String text) {
        FxTestUtil.runAndWait(() -> rta.getActionFactory()
                .open(new Document(text))
                .execute(new ActionEvent()));
        waitForFxEvents();
    }

    private void pressEnd() {
        FxTestUtil.runAndWait(() -> rta.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                KeyCode.END, false, false, false, false)));
        waitForFxEvents();
    }

    /**
     * Bildschirmpunkt knapp rechts des Zeilenanfangs der Zeile mit dem gegebenen
     * Inhalt – entspricht einem Mausklick „vor dem ersten Zeichen".
     *
     * Hinweis: {@code robot.clickOn(x, y)} erwartet Bildschirmkoordinaten; das
     * Monocle-Fenster liegt nicht zwingend am Ursprung, daher localToScreen.
     */
    private Point2D clickPointBeforeFirstChar(String lineText) {
        return FxTestUtil.callAndWait(() -> {
            TextFlow flow = rta.lookupAll(".text-flow").stream()
                    .filter(TextFlow.class::isInstance)
                    .map(TextFlow.class::cast)
                    .filter(f -> textOf(f).equals(lineText))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "keine TextFlow mit Inhalt '" + lineText + "' gefunden"));
            return flow.localToScreen(flow.getPadding().getLeft() + 1, flow.getHeight() / 2);
        });
    }

    private String textOf(TextFlow flow) {
        return flow.getChildren().stream()
                .filter(Text.class::isInstance)
                .map(t -> ((Text) t).getText())
                .collect(Collectors.joining());
    }
}
