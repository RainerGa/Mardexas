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

package de.richmarkdown.findreplace;

import com.gluonhq.richtextarea.RichTextArea;
import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.model.Paragraph;
import com.gluonhq.richtextarea.model.ParagraphDecoration;
import com.gluonhq.richtextarea.model.TableDecoration;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressionstest fuer das Ersetzen von Text innerhalb einer Tabelle.
 *
 * <p>Fehlerbild: Wird ein Suchbegriff mehrfach in derselben Tabellenzelle ersetzt, verschwand
 * eine komplette Spalte der Tabelle. Ursache war die Umrechnung der Textpositionen in die
 * internen Positionen der Bibliothek: nach der ersten Ersetzung deckt ein Textstueck nur noch
 * einen Teil einer Texteinheit ab, die Umrechnung lieferte dann eine falsche Position und das
 * Ersetzen loeschte den Trenner zwischen zwei Zellen.
 *
 * <p>Der Test prueft nach jedem Ersetzen, dass
 * <ul>
 *   <li>die {@link TableDecoration} weiterhin dieselbe Zeilen- und Spaltenzahl hat,</li>
 *   <li>alle Zellen vorhanden sind und ihre Texte behalten haben,</li>
 *   <li>die gerenderte Tabelle dieselbe Anzahl Zellen hat,</li>
 *   <li>der geschriebene Markdown weiterhin dieselbe Anzahl Spalten je Zeile hat.</li>
 * </ul>
 */
class TableReplaceTest {

    /** Zeilentrenner der Tabellenzellen (ZERO_WIDTH_TABLE_SEPARATOR). */
    private static final char CELL_SEPARATOR = '\u200B';

    private static final int ROWS = 5;
    private static final int COLUMNS = 3;

    private static final String TABLE_MARKDOWN = """
            | Feature | Supported | Notes |
            |---------|-----------|-------|
            | Bold | Yes | Via toolbar |
            | Italic | Yes | Via toolbar |
            | Headings | Yes | H1 through H6 |
            | Tables | Yes | Via pipe syntax |
            """;

    private MarkdownEditor editor;
    private RichTextArea rta;
    private Stage stage;
    private FindReplaceEngine engine;

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    @BeforeEach
    void setUp() {
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor();
            rta = editor.getControl();
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(rta), 900, 600));
            stage.show();
        });
        FxTestUtil.flushFxEvents();
    }

    private void setText(String text) {
        FxTestUtil.runAndWait(() -> editor.setMarkdown(text));
        FxTestUtil.flushFxEvents();
        FxTestUtil.flushFxEvents();
        engine = FxTestUtil.callAndWait(() ->
                new FindReplaceEngine(rta, ((RichTextAreaSkin) rta.getSkin()).getViewModel()));
    }

    private FindReplaceEngine.FindOptions opts() {
        return new FindReplaceEngine.FindOptions();
    }

    /**
     * Kernfall: Der Suchbegriff kommt zweimal in derselben Zelle vor ("Via pipe syntax").
     * Die zweite Ersetzung trifft auf ein Textstueck, das nur einen Teil der Texteinheit
     * abdeckt - genau hier wurde hier previously der Zellentrenner geloescht.
     */
    @Test
    void replaceAllTwiceInTheSameCellKeepsAllColumns() {
        setText(TABLE_MARKDOWN);
        List<String> before = cells();
        assertEquals(ROWS * COLUMNS, before.size(), "Ausgangstabelle: 5 Zeilen x 3 Spalten");

        int count = FxTestUtil.callAndWait(() -> engine.replaceAll(" ", "_", opts()));
        FxTestUtil.flushFxEvents();

        assertEquals(6, count, "Alle sechs Leerzeichen muessen ersetzt werden");
        assertTableIntact(before, " ", "_");
    }

    @Test
    void replaceAllInTableKeepsAllColumns() {
        setText(TABLE_MARKDOWN);
        List<String> before = cells();

        int count = FxTestUtil.callAndWait(() -> engine.replaceAll("Yes", "Ja", opts()));
        FxTestUtil.flushFxEvents();

        assertEquals(4, count, "Alle vier Vorkommen von 'Yes' muessen ersetzt werden");
        assertTableIntact(before, "Yes", "Ja");
    }

    @Test
    void replaceAllInFirstCellKeepsAllColumns() {
        setText(TABLE_MARKDOWN);
        List<String> before = cells();

        int count = FxTestUtil.callAndWait(() -> engine.replaceAll("Bold", "Fett", opts()));
        FxTestUtil.flushFxEvents();

        assertEquals(1, count);
        assertTableIntact(before, "Bold", "Fett");
    }

    @Test
    void replaceAllWithLongerTextKeepsAllColumns() {
        setText(TABLE_MARKDOWN);
        List<String> before = cells();

        int count = FxTestUtil.callAndWait(() -> engine.replaceAll("Yes", "Ja, wirklich", opts()));
        FxTestUtil.flushFxEvents();

        assertEquals(4, count);
        assertTableIntact(before, "Yes", "Ja, wirklich");
    }

    @Test
    void replaceSelectionInTableKeepsAllColumns() {
        setText(TABLE_MARKDOWN);
        List<String> before = cells();

        FxTestUtil.callAndWait(() -> {
            List<FindReplaceEngine.Match> matches = engine.findAll("Headings", opts());
            assertEquals(1, matches.size(), "Genau ein Treffer erwartet");
            engine.selectMatch(matches.get(0));
            return null;
        });
        FxTestUtil.flushFxEvents();

        FindReplaceEngine.SearchResult result = FxTestUtil.callAndWait(() ->
                engine.replaceSelection("Headings", "Überschriften", opts()));
        FxTestUtil.flushFxEvents();

        assertNotNull(result, "Der Treffer muss ersetzt werden");
        assertTableIntact(before, "Headings", "Überschriften");
    }

    @Test
    void replacingTwiceInTheSameCellKeepsAllColumns() {
        setText(TABLE_MARKDOWN);
        List<String> before = cells();

        // Erst "Via" ersetzen, danach "toolbar" in derselben Zelle.
        FxTestUtil.callAndWait(() -> engine.replaceAll("Via", "Durch", opts()));
        FxTestUtil.flushFxEvents();
        FxTestUtil.callAndWait(() -> engine.replaceAll("toolbar", "Werkzeugleiste", opts()));
        FxTestUtil.flushFxEvents();

        assertTableIntact(before, "Via", "Durch", "toolbar", "Werkzeugleiste");
    }

    /**
     * Prueft, dass die Tabelle nach dem Ersetzen unveraendert aufgebaut ist: gleiche
     * Zellanzahl, gleiche Zeilen-/Spaltenzahl, erwartete Texte in den Zellen,
     * gleiche Anzahl Zellen im Rendering und im geschriebenen Markdown.
     *
     * @param before die Zelltexte vor dem Ersetzen
     * @param replacements die Ersetzungen in der Reihenfolge, in der sie ausgefuehrt wurden
     *                    (Suchbegriff, Ersetzungstext, Suchbegriff, Ersetzungstext, ...)
     */
    private void assertTableIntact(List<String> before, String... replacements) {
        List<String> after = cells();
        assertEquals(before.size(), after.size(),
                "Die Anzahl der Tabellenzellen hat sich geaendert: " + before + " -> " + after);

        TableDecoration decoration = tableDecoration();
        assertNotNull(decoration, "Die Tabelle hat ihre TableDecoration verloren");
        assertEquals(ROWS, decoration.getRows(), "Die Zeilenzahl hat sich geaendert");
        assertEquals(COLUMNS, decoration.getColumns(), "Die Spaltenzahl hat sich geaendert");

        for (int i = 0; i < before.size(); i++) {
            String expected = before.get(i);
            for (int r = 0; r + 1 < replacements.length; r += 2) {
                expected = expected.replace(replacements[r], replacements[r + 1]);
            }
            assertEquals(expected, after.get(i),
                    "Die Zelle " + i + " hat einen unerwarteten Text: '" + after.get(i)
                            + "' (erwartet '" + expected + "')");
        }

        // Gerenderte Tabelle: alle Zellen muessen vorhanden sein.
        List<String> rendered = renderedCells();
        assertEquals(ROWS * COLUMNS + 1, rendered.size(),
                "Die gerenderte Tabelle hat zu wenige Zellen: " + rendered);
        for (int i = 0; i < ROWS * COLUMNS; i++) {
            assertEquals(after.get(i), rendered.get(i),
                    "Die gerenderte Zelle " + i + " stimmt nicht mit dem Dokument ueberein");
        }

        // Geschriebener Markdown: jede Zeile muss gleich viele Spalten haben.
        List<String> rows = FxTestUtil.callAndWait(editor::getMarkdown).lines()
                .filter(line -> line.startsWith("|"))
                .toList();
        assertEquals(ROWS + 1, rows.size(), "Markdown-Tabelle hat eine falsche Zeilenzahl: " + rows);
        for (String row : rows) {
            assertEquals(COLUMNS, row.split("\\|", -1).length - 2,
                    "Markdown-Zeile hat eine falsche Spaltenzahl: " + row);
        }
    }

    /** Die Texte aller Zellen der Tabelle in Dokumentreihenfolge. */
    private List<String> cells() {
        return FxTestUtil.callAndWait(() -> {
            List<String> cells = new ArrayList<>();
            Paragraph table = tableParagraph();
            if (table == null) {
                return cells;
            }
            String text = ((RichTextAreaSkin) rta.getSkin()).getViewModel().getTextBuffer()
                    .getText(table.getStart(), table.getEnd());
            for (String cell : text.split(String.valueOf(CELL_SEPARATOR), -1)) {
                cells.add(cell.replace("\n", ""));
            }
            return cells;
        });
    }

    private String cell(int index) {
        return cells().get(index);
    }

    /** Die TableDecoration des Tabellenabsatzes. */
    private TableDecoration tableDecoration() {
        return FxTestUtil.callAndWait(() -> {
            Paragraph table = tableParagraph();
            if (table == null) {
                return null;
            }
            ParagraphDecoration decoration = table.getDecoration();
            return decoration != null && decoration.hasTableDecoration()
                    ? decoration.getTableDecoration()
                    : null;
        });
    }

    /** Der Absatz mit der Tabellendekoration. */
    private Paragraph tableParagraph() {
        RichTextAreaSkin skin = (RichTextAreaSkin) rta.getSkin();
        for (Paragraph paragraph : skin.getViewModel().getParagraphList()) {
            ParagraphDecoration decoration = paragraph.getDecoration();
            if (decoration != null && decoration.hasTableDecoration()) {
                return paragraph;
            }
        }
        return null;
    }

    /** Die Texte der gerenderten Tabellenzellen. */
    private List<String> renderedCells() {
        return FxTestUtil.callAndWait(() -> {
            FxTestUtil.runAndWait(() -> {
                rta.applyCss();
                rta.layout();
            });
            List<String> rendered = new ArrayList<>();
            GridPane grid = (GridPane) rta.lookup(".table");
            if (grid == null) {
                return rendered;
            }
            for (Node node : grid.getChildren()) {
                StringBuilder cellText = new StringBuilder();
                node.lookupAll(".text-flow").forEach(tf -> {
                    if (tf instanceof javafx.scene.text.TextFlow flow) {
                        flow.getChildren().stream().filter(Text.class::isInstance).map(Text.class::cast)
                                .forEach(t -> cellText.append(t.getText()));
                    }
                });
                rendered.add(cellText.toString().replace(String.valueOf(CELL_SEPARATOR), ""));
            }
            return rendered;
        });
    }
}
