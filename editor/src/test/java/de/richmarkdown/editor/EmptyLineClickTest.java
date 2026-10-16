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

package de.richmarkdown.editor;

import com.gluonhq.richtextarea.RichTextArea;
import com.gluonhq.richtextarea.model.Document;
import de.richmarkdown.FxTestUtil;
import javafx.event.ActionEvent;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testfx.util.WaitForAsyncUtils.waitForFxEvents;

/**
 * TDD: Ein Klick in eine leere Zeile setzt den Caret an Position 1 der Zeile –
 * wie in Word/LibreOffice (PROJEKTPLAN Kap. 4.4, 12).
 *
 * Doppelklicks müssen auf leeren Zeilen wie ein einfacher Klick wirken:
 * {@code selectCurrentWord()} der Bibliothek würde sonst den Caret an das Wort
 * der Nachbarzeile ziehen und dieses selektieren.
 *
 * Hinweis Monocle: Der Klickzähler des Roboters zählt schnelle Klicks hintereinander
 * hoch (Doppelklick). Vor jedem Einzelklick wird deshalb eine Pause eingelegt, die
 * über dem Multi-Click-Schwellwert liegt, und Doppelklicks werden mit kurzem Abstand
 * (40&nbsp;ms) erzeugt.
 */
@ExtendWith(ApplicationExtension.class)
class EmptyLineClickTest {

    private static final int CLICK_PAUSE_MS = 40;
    private static final int CLICK_RESET_MS = 700;

    private MarkdownEditor editor;
    private RichTextArea rta;

    @Start
    public void start(Stage stage) {
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor();
            rta = editor.getControl();
            rta.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> clickCounts.add(e.getClickCount()));
            stage.setScene(new Scene(new StackPane(rta), 600, 400));
            stage.show();
        });
    }

    private final List<Integer> clickCounts = new ArrayList<>();

    @Test
    @DisplayName("Klick in eine leere Zeile setzt den Caret an den Zeilenanfang")
    void clickOnEmptyLinePlacesCaretAtLineStart(FxRobot robot) throws Exception {
        openDocument("Zeile1\n\nZeile3");

        clickSingle(robot, emptyLineFlow(robot, 0), 0.5);

        assertEquals(7, caret(), "Der Caret muss am Anfang der leeren Zeile (Index 7) stehen");
        assertFalse(hasSelection(), "Ein Klick in eine leere Zeile darf keine Selektion hinterlassen");
        assertEquals("Zeile1\n\nZeile3", text(), "Der Klick darf den Text nicht verändern");
        assertEquals(List.of(1), clickCounts(), "Der Klick muss ein Einzelklick sein");
    }

    @Test
    @DisplayName("Klick am linken Rand einer leeren Zeile setzt den Caret an den Zeilenanfang")
    void clickOnEmptyLineAtLeftEdgePlacesCaretAtLineStart(FxRobot robot) throws Exception {
        openDocument("Zeile1\n\nZeile3");

        TextFlow empty = emptyLineFlow(robot, 0);
        Point2D at = FxTestUtil.callAndWait(() ->
                empty.localToScreen(empty.getPadding().getLeft() + 1, empty.getHeight() * 0.5));
        resetClickCount();
        robot.clickOn(at.getX(), at.getY());
        waitForFxEvents();

        assertEquals(7, caret());
    }

    @Test
    @DisplayName("Klick am rechten Ende einer leeren Zeile setzt den Caret an den Zeilenanfang")
    void clickOnEmptyLineAtRightEndPlacesCaretAtLineStart(FxRobot robot) throws Exception {
        openDocument("Zeile1\n\nZeile3");

        TextFlow empty = emptyLineFlow(robot, 0);
        Point2D at = FxTestUtil.callAndWait(() ->
                empty.localToScreen(empty.getWidth() - 2, empty.getHeight() * 0.5));
        resetClickCount();
        robot.clickOn(at.getX(), at.getY());
        waitForFxEvents();

        assertEquals(7, caret());
    }

    @Test
    @DisplayName("Doppelklick in eine leere Zeile verhält sich wie ein Einzelklick")
    void doubleClickOnEmptyLineBehavesLikePlainClick(FxRobot robot) throws Exception {
        openDocument("Zeile1\n\nZeile3");

        clickTwice(robot, emptyLineFlow(robot, 0), 0.5);

        assertEquals(7, caret(), "Doppelklick: der Caret muss am Zeilenanfang bleiben");
        assertFalse(hasSelection(), "Doppelklick auf leere Zeile darf kein Wort der Nachbarzeile wählen");
        assertEquals("Zeile1\n\nZeile3", text());
        assertTrue(clickCounts().stream().anyMatch(c -> c >= 2),
                "Der Doppelklick muss als solcher (clickCount >= 2) ankommen, sonst ist der Test wirkungslos");
    }

    @Test
    @DisplayName("Schneller Klick auf eine andere Zeile, dann in die leere Zeile: Caret bleibt am Zeilenanfang")
    void rapidClickOnOtherLineThenEmptyLineKeepsCaretAtLineStart(FxRobot robot) throws Exception {
        openDocument("Zeile1\n\nZeile3");

        TextFlow first = nonEmptyLineFlow(robot, 0);
        clickSingle(robot, first, 0.1);
        int firstCaret = caret();
        assertTrue(firstCaret >= 0 && firstCaret < 7,
                "Der Klick auf Zeile1 setzt den Caret in diese Zeile, war: " + firstCaret);
        Thread.sleep(CLICK_PAUSE_MS);
        Point2D at = centerOf(emptyLineFlow(robot, 0), 0.5);
        robot.clickOn(at.getX(), at.getY());
        waitForFxEvents();

        assertEquals(7, caret(), "Der schnelle Folgeklick darf den Caret nicht auf die vorige Zeile ziehen");
        assertFalse(hasSelection());
        assertEquals("Zeile1\n\nZeile3", text());
    }

    @Test
    @DisplayName("Dreifachklick in eine leere Zeile lässt den Caret am Zeilenanfang")
    void tripleClickOnEmptyLineKeepsCaretAtLineStart(FxRobot robot) throws Exception {
        openDocument("Zeile1\n\nZeile3");

        resetClickCount();
        TextFlow empty = emptyLineFlow(robot, 0);
        for (int i = 0; i < 3; i++) {
            Point2D at = centerOf(empty, 0.5);
            robot.clickOn(at.getX(), at.getY());
            if (i < 2) {
                Thread.sleep(CLICK_PAUSE_MS);
            }
        }
        waitForFxEvents();

        assertEquals(7, caret());
        assertEquals("Zeile1\n\nZeile3", text());
        assertTrue(clickCounts().contains(3), "Der Dreifachklick muss als solcher ankommen");
    }

    @Test
    @DisplayName("Klick in die leere erste Zeile setzt den Caret an den Dokumentanfang")
    void clickOnEmptyFirstLinePlacesCaretAtParagraphStart(FxRobot robot) throws Exception {
        openDocument("\nZeile2");

        clickSingle(robot, emptyLineFlow(robot, 0), 0.5);

        assertEquals(0, caret());
        assertFalse(hasSelection());
        assertEquals("\nZeile2", text());
    }

    @Test
    @DisplayName("Klick in die leere letzte Zeile setzt den Caret an deren Anfang")
    void clickOnEmptyLastLinePlacesCaretAtLineStart(FxRobot robot) throws Exception {
        openDocument("Zeile1\n");

        clickSingle(robot, emptyLineFlow(robot, 0), 0.5);

        assertEquals(7, caret());
        assertFalse(hasSelection());
    }

    @Test
    @DisplayName("Klick auf aufeinanderfolgende leere Zeilen trifft jeweils den eigenen Zeilenanfang")
    void clicksOnConsecutiveEmptyLinesUseEachLineStart(FxRobot robot) throws Exception {
        openDocument("A\n\n\nB");

        clickSingle(robot, emptyLineFlow(robot, 0), 0.5);
        assertEquals(2, caret());

        clickSingle(robot, emptyLineFlow(robot, 1), 0.5);
        assertEquals(3, caret());

        assertEquals("A\n\n\nB", text());
    }

    @Test
    @DisplayName("Tippen nach Klick in die leere Zeile landet auf dieser Zeile")
    void typingAfterClickOnEmptyLineStaysOnThatLine(FxRobot robot) throws Exception {
        openDocument("Zeile1\n\nZeile3");

        clickSingle(robot, emptyLineFlow(robot, 0), 0.5);
        insert("X");

        assertEquals("Zeile1\nX\nZeile3", text(),
                "Der Text muss auf der leeren Zeile eingefügt werden");
    }

    @Test
    @DisplayName("Tippen nach Doppelklick in die leere Zeile landet auf dieser Zeile")
    void typingAfterDoubleClickOnEmptyLineStaysOnThatLine(FxRobot robot) throws Exception {
        openDocument("Zeile1\n\nZeile3");

        clickTwice(robot, emptyLineFlow(robot, 0), 0.5);
        insert("X");

        assertEquals("Zeile1\nX\nZeile3", text(),
                "Nach dem Doppelklick muss der Text auf der leeren Zeile landen, nicht auf der Vorigen");
    }

    @Test
    @DisplayName("Klick in die leere Zeile hebt eine bestehende Selektion auf")
    void clickOnEmptyLineClearsExistingSelection(FxRobot robot) throws Exception {
        openDocument("Zeile1\n\nZeile3");

        clickTwice(robot, nonEmptyLineFlow(robot, 0), 0.2);
        assertTrue(hasSelection(), "Der Doppelklick auf Zeile1 wählt das Wort");

        clickSingle(robot, emptyLineFlow(robot, 0), 0.5);

        assertFalse(hasSelection());
        assertEquals(7, caret());
        assertEquals("Zeile1\n\nZeile3", text());
    }

    @Test
    @DisplayName("Klick in eine leere Zeile nach setMarkdown (Dateipfad) nutzt den Zeilenanfang")
    void clickOnEmptyLineAfterSetMarkdownUsesLineStart(FxRobot robot) throws Exception {
        FxTestUtil.runAndWait(() -> editor.setMarkdown("Zeile1\n\nZeile3"));
        waitForFxEvents();

        clickSingle(robot, emptyLineFlow(robot, 0), 0.5);

        assertEquals(7, caret());
        assertFalse(hasSelection());
    }

    // ---------- Helfer ----------

    private void openDocument(String text) {
        FxTestUtil.runAndWait(() -> rta.getActionFactory()
                .open(new Document(text))
                .execute(new ActionEvent()));
        waitForFxEvents();
    }

    private void insert(String text) {
        FxTestUtil.runAndWait(() -> rta.getActionFactory()
                .insertText(text)
                .execute(new ActionEvent()));
        waitForFxEvents();
    }

    private String text() {
        FxTestUtil.runAndWait(() -> rta.getActionFactory().save().execute(new ActionEvent()));
        return FxTestUtil.callAndWait(() -> rta.getDocument().getText());
    }

    private int caret() {
        return FxTestUtil.callAndWait(rta::getCaretPosition);
    }

    private boolean hasSelection() {
        return FxTestUtil.callAndWait(() -> rta.getSelection().isDefined());
    }

    private List<Integer> clickCounts() {
        return FxTestUtil.callAndWait(() -> new ArrayList<>(clickCounts));
    }

    private void resetClickCount() {
        FxTestUtil.runAndWait(clickCounts::clear);
    }

    /** Wartet den Multi-Click-Schwellwert ab, damit der nächste Klick ein Einzelklick ist. */
    private void awaitClickCountReset() throws InterruptedException {
        Thread.sleep(CLICK_RESET_MS);
    }

    private void clickSingle(FxRobot robot, TextFlow flow, double fx) throws Exception {
        awaitClickCountReset();
        Point2D at = centerOf(flow, fx);
        robot.clickOn(at.getX(), at.getY());
        waitForFxEvents();
    }

    /** Zwei schnelle Klicks: die Plattform meldet clickCount 1 und 2. */
    private void clickTwice(FxRobot robot, TextFlow flow, double fx) throws Exception {
        awaitClickCountReset();
        Point2D at = centerOf(flow, fx);
        robot.clickOn(at.getX(), at.getY());
        Thread.sleep(CLICK_PAUSE_MS);
        robot.clickOn(at.getX(), at.getY());
        waitForFxEvents();
    }

    private Point2D centerOf(TextFlow flow, double fx) {
        return FxTestUtil.callAndWait(() ->
                flow.localToScreen(flow.getWidth() * fx, flow.getHeight() * 0.5));
    }

    /** Leere Zeilen-TextFlows (Inhalt nur Zero-Width-Zeichen), sortiert nach Bildschirm-Y. */
    private TextFlow emptyLineFlow(FxRobot robot, int index) {
        List<TextFlow> empties = FxTestUtil.callAndWait(() -> flows(robot).stream()
                .filter(EmptyLineClickTest::hasNoVisibleText)
                .toList());
        if (index >= empties.size()) {
            throw new AssertionError("keine leere Zeile #" + index + " gefunden ("
                    + empties.size() + " leere Zeilen)");
        }
        return empties.get(index);
    }

    /** TextFlows mit sichtbarem Text, sortiert nach Bildschirm-Y. */
    private TextFlow nonEmptyLineFlow(FxRobot robot, int index) {
        return flows(robot).stream()
                .filter(f -> !hasNoVisibleText(f))
                .toList()
                .get(index);
    }

    /** Alle sichtbaren TextFlows, sortiert nach Bildschirm-Y (oben = Dokumentanfang). */
    private List<TextFlow> flows(FxRobot robot) {
        return FxTestUtil.callAndWait(() -> rta.lookupAll(".text-flow").stream()
                .filter(TextFlow.class::isInstance)
                .map(TextFlow.class::cast)
                .filter(f -> f.localToScreen(f.getBoundsInLocal()) != null)
                .sorted(java.util.Comparator.comparingDouble(
                        f -> f.localToScreen(f.getBoundsInLocal()).getMinY()))
                .toList());
    }

    private static boolean hasNoVisibleText(TextFlow flow) {
        return flow.getChildren().stream()
                .filter(Text.class::isInstance)
                .map(t -> ((Text) t).getText())
                .collect(Collectors.joining(""))
                .replace("\u200b", "")
                .isEmpty();
    }
}
