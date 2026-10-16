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

package de.richmarkdown.ui;

import com.gluonhq.richtextarea.RichTextArea;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * TDD: Formatvorlagen-Anzeige (Dropdown der Format-Toolbar) folgt dem Caret –
 * wie in Word/LibreOffice Writer (PROJEKTPLAN 5.2, 7.1).
 *
 * <p>Prüft alle Stil-Übergänge per Mausklick: Überschriften, Absatz, Zitat und
 * Listen. Die Listen-Toggles müssen ebenfalls dem Absatz am Caret folgen.
 */
class FormatStyleFollowTest {

    private MarkdownEditor editor;
    private RichTextArea rta;
    private FormatToolBar toolBar;
    private Stage stage;

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    @BeforeEach
    void setUp() {
        WaitForAsyncUtils.clearExceptions();
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor();
            rta = editor.getControl();
            toolBar = new FormatToolBar(editor);
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(toolBar, rta), 800, 400));
            stage.show();
        });
        flush();
    }

    @AfterAll
    static void tearDown() {
        // kein hide(): blockt den FX-Thread in Monocle headless
    }

    private void flush() {
        FxTestUtil.flushFxEvents();
        FxTestUtil.flushFxEvents();
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void clickLine(FxRobot robot, int index) {
        Point2D point = FxTestUtil.callAndWait(() -> {
            for (Node n : rta.lookupAll(".list-cell")) {
                if (n instanceof ListCell<?> c && c.getIndex() == index) {
                    return c.localToScreen(c.getWidth() / 2, c.getHeight() / 2);
                }
            }
            throw new AssertionError("Editor-Zeile " + index + " nicht gefunden");
        });
        robot.clickOn(point.getX(), point.getY());
        flush();
    }

    private FormatToolBar.ParagraphStyle shownStyle() {
        return FxTestUtil.callAndWait(() -> toolBar.getStyleChoice().getValue());
    }

    // ---------- Überschungen ↔ Absatz ----------

    @Test
    @DisplayName("Klick in die Überschrift zeigt Überschrift 1")
    void clickHeadingShowsHeading() {
        editor.setMarkdown("# Überschrift\n\nAbsatz");
        flush();
        clickLine(new FxRobot(), 0);
        assertEquals(FormatToolBar.ParagraphStyle.H1, shownStyle());
    }

    @Test
    @DisplayName("Klick in den Absatz zeigt Absatz")
    void clickBodyShowsBody() {
        editor.setMarkdown("# Überschrift\n\nAbsatz");
        flush();
        FxRobot robot = new FxRobot();
        clickLine(robot, 0);
        clickLine(robot, 2);
        assertEquals(FormatToolBar.ParagraphStyle.BODY, shownStyle());
    }

    // ---------- Zitat ----------

    @Test
    @DisplayName("Klick in das Zitat zeigt Zitat")
    void clickQuoteShowsQuote() {
        editor.setMarkdown("# Überschrift\n\nAbsatz\n\n> Zitat");
        flush();
        FxRobot robot = new FxRobot();
        clickLine(robot, 0);
        clickLine(robot, 4);
        assertEquals(FormatToolBar.ParagraphStyle.QUOTE, shownStyle(),
                "nach Klick in die Zitat-Zeile muss Zitat angezeigt werden");
    }

    @Test
    @DisplayName("Klick aus dem Zitat in den Absatz zeigt Absatz")
    void clickBodyAfterQuoteShowsBody() {
        editor.setMarkdown("> Zitat\n\nAbsatz");
        flush();
        FxRobot robot = new FxRobot();
        clickLine(robot, 0);
        clickLine(robot, 2);
        assertEquals(FormatToolBar.ParagraphStyle.BODY, shownStyle(),
                "nach Klick in die Absatz-Zeile muss Absatz angezeigt werden");
    }

    // ---------- Listen ----------

    @Test
    @DisplayName("Klick in die Aufzählungsliste aktiviert den Listen-Toggle")
    void clickBulletListActivatesToggle() {
        editor.setMarkdown("# Überschrift\n\n- Eins\n- Zwei");
        flush();
        FxRobot robot = new FxRobot();
        clickLine(robot, 0);
        clickLine(robot, 2);
        assertEquals(true, FxTestUtil.callAndWait(() -> toolBar.getBulletListToggle().isSelected()),
                "in der Aufzählungsliste muss der Listen-Toggle aktiv sein");
    }

    @Test
    @DisplayName("Klick aus der Liste in den Absatz deaktiviert den Listen-Toggle")
    void clickBodyAfterListDeactivatesToggle() {
        editor.setMarkdown("- Eins\n- Zwei\n\nAbsatz");
        flush();
        FxRobot robot = new FxRobot();
        clickLine(robot, 0);
        clickLine(robot, 3);
        assertEquals(false, FxTestUtil.callAndWait(() -> toolBar.getBulletListToggle().isSelected()),
                "im Absatz muss der Listen-Toggle inaktiv sein");
    }

    // ---------- Dokument-Integrität ----------

    @Test
    @DisplayName("Klick in eine formatierte Zeile verändert das Dokument nicht")
    void clickDoesNotModifyDocument() {
        String markdown = "# Überschrift\n\nAbsatz\n\n> Zitat\n\n- Eins\n- Zwei";
        editor.setMarkdown(markdown);
        flush();
        FxRobot robot = new FxRobot();
        // durch alle formatierten Zeilen klicken
        for (int line : new int[]{0, 2, 4, 6, 7}) {
            clickLine(robot, line);
        }
        assertEquals(markdown, FxTestUtil.callAndWait(editor::getMarkdown),
                "reines Klicken darf das Dokument nicht verändern");
    }

    // ---------- Statusleiste ----------

    @Test
    @DisplayName("die Statusleiste zeigt denselben Stil wie das Dropdown")
    void statusBarMatchesDropdown() {
        editor.setMarkdown("# Überschrift\n\nAbsatz\n\n> Zitat");
        flush();
        FxRobot robot = new FxRobot();
        clickLine(robot, 0);
        clickLine(robot, 4);
        String status = FxTestUtil.callAndWait(() -> toolBar.getStyleChoice().getValue().getLabel());
        assertFalse(status.isEmpty());
        assertEquals(FormatToolBar.ParagraphStyle.QUOTE.getLabel(), status,
                "Statusleiste und Dropdown müssen übereinstimmen");
    }
}
