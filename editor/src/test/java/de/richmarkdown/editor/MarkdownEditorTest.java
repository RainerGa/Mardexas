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
import com.gluonhq.richtextarea.model.DecorationModel;
import com.gluonhq.richtextarea.model.Document;
import com.gluonhq.richtextarea.model.ParagraphDecoration;
import com.gluonhq.richtextarea.model.TextDecoration;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.markdown.MarkdownStyles;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD Phase 1: Word-ähnliches Editieren (PROJEKTPLAN 7.1, 4.5).
 *
 * Die Tests steuern das {@link RichTextArea} über synthetische KeyEvents exakt so,
 * wie es die Skin per INPUT_MAP tut (Selektion per Shift+Arrows, Tippen per
 * KEY_TYPED, Enter per KEY_PRESSED). Damit ist das Verhalten der Fassade
 * einschließlich des onAction-Hooks (4.5) faithful abgesichert.
 */
class MarkdownEditorTest {

    private MarkdownEditor editor;
    private RichTextArea rta;
    private Stage stage;

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    @BeforeEach
    void setUp() {
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor();
            rta = editor.getControl();
        flush();
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(rta), 600, 400));
            stage.show();
        });
    }

    /** Wählt den gesamten Dokumenttext aus (selectAll über die ActionFactory). */
    private void selectAll() {
        FxTestUtil.runAndWait(() -> rta.getActionFactory().selectAll().execute(new ActionEvent()));
        flush();
    }

    // Hinweis: kein stage.hide() im tearDown – in Monocle headless blockt hide()
    // den FX-Thread dauerhaft (alle nachfolgenden Tests würden timeouten).
    // Die Stages leben nur für die Dauer der Test-JVM.

    // ---------- Test-Helfer ----------

    private void type(String text) {
        FxTestUtil.runAndWait(() -> {
            for (char c : text.toCharArray()) {
                rta.fireEvent(new KeyEvent(KeyEvent.KEY_TYPED, String.valueOf(c), "",
                        KeyCode.UNDEFINED, false, false, false, false));
            }
        });
    }

    private void press(KeyCode code, boolean shiftDown, boolean controlDown) {
        FxTestUtil.runAndWait(() -> rta.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                code, shiftDown, controlDown, false, false)));
    }

    private void enter() {
        press(KeyCode.ENTER, false, false);
    }

    private void home() {
        press(KeyCode.HOME, false, false);
    }

    /** Aktuellen Dokumentzustand (Text + Dekorationen) auslesen. */
    private Document document() {
        FxTestUtil.runAndWait(() -> rta.getActionFactory().save().execute(new ActionEvent()));
        flush();
        return rta.getDocument();
    }

    /** Wartet die asynchrone Ausführung gequeuerter Actions ab. */
    private void flush() {
        FxTestUtil.flushFxEvents();
    }

    private TextDecoration textDecorationAt(int index) {
        Document doc = document();
        for (DecorationModel dm : doc.getDecorations()) {
            if (index >= dm.getStart() && index < dm.getStart() + dm.getLength()) {
                return (TextDecoration) dm.getDecoration();
            }
        }
        // Null-Längen-Fragmente (z. B. leerer Absatz am Dokumentende) einschließen
        for (DecorationModel dm : doc.getDecorations()) {
            if (dm.getStart() == index) {
                return (TextDecoration) dm.getDecoration();
            }
        }
        throw new AssertionError("no decoration at index " + index + " in " + doc);
    }

    private ParagraphDecoration paragraphDecorationAt(int index) {
        Document doc = document();
        for (DecorationModel dm : doc.getDecorations()) {
            if (index >= dm.getStart() && index < dm.getStart() + dm.getLength()) {
                return dm.getParagraphDecoration();
            }
        }
        for (DecorationModel dm : doc.getDecorations()) {
            if (dm.getStart() == index) {
                return dm.getParagraphDecoration();
            }
        }
        throw new AssertionError("no paragraph decoration at index " + index + " in " + doc);
    }

    private TextDecoration decorationAtCaret() {
        return FxTestUtil.callAndWait(() -> (TextDecoration) rta.getDecorationAtCaret());
    }

    private int caretPosition() {
        return FxTestUtil.callAndWait(rta::getCaretPosition);
    }

    private int paragraphCount() {
        return document().getText().split("\n", -1).length;
    }

    // ---------- Fett / Kursiv / Unterstrichen / Durchgestrichen ----------

    @Test
    void toggleBold_appliesBoldToSelection() {
        type("hello");
        selectAll();
        editor.toggleBold();
        flush();

        assertEquals("hello", document().getText());
        assertEquals(javafx.scene.text.FontWeight.BOLD, textDecorationAt(0).getFontWeight());
    }

    @Test
    void toggleBold_togglesOffAgain() {
        type("hello");
        selectAll();
        editor.toggleBold();
        flush();
        editor.toggleBold();
        flush();

        assertEquals(javafx.scene.text.FontWeight.NORMAL, textDecorationAt(0).getFontWeight());
    }

    @Test
    void toggleItalic_appliesItalicToSelection() {
        type("hello");
        selectAll();
        editor.toggleItalic();
        flush();

        assertEquals(javafx.scene.text.FontPosture.ITALIC, textDecorationAt(0).getFontPosture());
    }

    @Test
    void toggleUnderline_appliesUnderlineToSelection() {
        type("hello");
        selectAll();
        editor.toggleUnderline();
        flush();

        assertTrue(textDecorationAt(0).isUnderline());
    }

    @Test
    void toggleStrikethrough_appliesStrikethroughToSelection() {
        type("hello");
        selectAll();
        editor.toggleStrikethrough();
        flush();

        assertTrue(textDecorationAt(0).isStrikethrough());
    }

    @Test
    void toggleBold_withoutSelection_setsDecorationAtCaretForTyping() {
        // Word-Verhalten: Klick ohne Selektion schaltet den Eingabemodus
        editor.toggleBold();
        flush();
        assertEquals(javafx.scene.text.FontWeight.BOLD, decorationAtCaret().getFontWeight());

        type("x");
        assertEquals(javafx.scene.text.FontWeight.BOLD, textDecorationAt(0).getFontWeight());
    }

    @Test
    void decorationAtCaret_reflectsBoldEnvironment() {
        type("bold");
        selectAll();
        editor.toggleBold();
        flush();
        home();

        assertEquals(javafx.scene.text.FontWeight.BOLD, decorationAtCaret().getFontWeight());
    }

    // ---------- Strg+U (eigener Accelerator, in 1.2.6 nicht builtin) ----------

    @Test
    void ctrlU_togglesUnderlineOnSelection() {
        type("hello");
        selectAll();
        press(KeyCode.U, false, true);

        assertTrue(textDecorationAt(0).isUnderline());

        press(KeyCode.U, false, true);
        assertFalse(textDecorationAt(0).isUnderline());
    }

    // ---------- Überschriften ----------

    @Test
    void applyHeading1_setsHeadingDecoration() {
        type("Title");
        selectAll();
        editor.applyHeading(1);
        flush();

        TextDecoration td = textDecorationAt(0);
        assertEquals(MarkdownStyles.headingSize(1), td.getFontSize());
        assertEquals(javafx.scene.text.FontWeight.BOLD, td.getFontWeight());
        assertEquals(javafx.scene.text.TextAlignment.LEFT, paragraphDecorationAt(0).getAlignment());
    }

    @Test
    void applyHeading6_setsHeadingDecoration() {
        type("Title");
        selectAll();
        editor.applyHeading(6);
        flush();

        TextDecoration td = textDecorationAt(0);
        assertEquals(MarkdownStyles.headingSize(6), td.getFontSize());
        assertEquals(javafx.scene.text.FontWeight.BOLD, td.getFontWeight());
    }

    @Test
    void applyHeading_onMultiParagraphSelection_appliesToAllParagraphs() {
        type("one");
        enter();
        type("two");
        selectAll();
        editor.applyHeading(2);
        flush();

        assertEquals(MarkdownStyles.headingSize(2), textDecorationAt(0).getFontSize());
        assertEquals(MarkdownStyles.headingSize(2), textDecorationAt(4).getFontSize());
    }

    @Test
    void applyParagraphStyle_resetsHeadingToBody() {
        type("Title");
        selectAll();
        editor.applyHeading(1);
        flush();
        editor.applyParagraphStyle();
        flush();

        TextDecoration td = textDecorationAt(0);
        assertEquals(MarkdownStyles.DEFAULT_FONT_SIZE, td.getFontSize());
        assertEquals(javafx.scene.text.FontWeight.NORMAL, td.getFontWeight());
    }

    // ---------- Listen ----------

    @Test
    void toggleBulletList_setsBulletedListAtLevel1() {
        type("item");
        selectAll();
        editor.toggleBulletList();
        flush();

        ParagraphDecoration pd = paragraphDecorationAt(0);
        assertEquals(ParagraphDecoration.GraphicType.BULLETED_LIST, pd.getGraphicType());
        assertEquals(1, pd.getIndentationLevel());
    }

    @Test
    void toggleBulletList_togglesOff() {
        type("item");
        selectAll();
        editor.toggleBulletList();
        flush();
        editor.toggleBulletList();
        flush();

        ParagraphDecoration pd = paragraphDecorationAt(0);
        assertEquals(ParagraphDecoration.GraphicType.NONE, pd.getGraphicType());
    }

    @Test
    void toggleNumberedList_setsNumberedListAtLevel1() {
        type("item");
        selectAll();
        editor.toggleNumberedList();
        flush();

        ParagraphDecoration pd = paragraphDecorationAt(0);
        assertEquals(ParagraphDecoration.GraphicType.NUMBERED_LIST, pd.getGraphicType());
        assertEquals(1, pd.getIndentationLevel());
    }

    @Test
    void indent_increasesIndentationLevel() {
        type("item");
        selectAll();
        editor.toggleBulletList();
        flush();
        editor.indent();
        flush();

        assertEquals(2, paragraphDecorationAt(0).getIndentationLevel());
    }

    @Test
    void outdent_decreasesIndentationLevel() {
        type("item");
        selectAll();
        editor.toggleBulletList();
        flush();
        editor.indent();
        flush();
        editor.outdent();
        flush();

        assertEquals(1, paragraphDecorationAt(0).getIndentationLevel());
    }

    // ---------- Zitat ----------

    @Test
    void applyQuote_setsIndentationLevel1WithoutGraphic() {
        type("quote");
        selectAll();
        editor.applyQuote();
        flush();

        ParagraphDecoration pd = paragraphDecorationAt(0);
        assertEquals(ParagraphDecoration.GraphicType.NONE, pd.getGraphicType());
        assertEquals(1, pd.getIndentationLevel());
    }

    // ---------- Enter-Regelwerk (PROJEKTPLAN 4.5) ----------

    @Test
    void enterOnEmptyHeading_resetsToParagraphWithoutNewParagraph() {
        // Leere Überschrift: Enter setzt Stil auf "Absatz" zurück, KEIN neuer Absatz.
        // Geprüft wird auch der Tipp-Modus: danach getippter Text ist normal.
        editor.applyHeading(1);
        flush();
        enter();
        type("x");

        assertEquals("x", document().getText());
        assertEquals(1, paragraphCount());
        TextDecoration td = textDecorationAt(0);
        assertEquals(MarkdownStyles.DEFAULT_FONT_SIZE, td.getFontSize());
        assertEquals(javafx.scene.text.FontWeight.NORMAL, td.getFontWeight());
    }

    @Test
    void enterOnNonEmptyHeading_createsNormalParagraph() {
        // Nicht leere Überschrift: Enter erzeugt neuen Absatz mit Stil "Absatz".
        // Geprüft wird der benutzersichtbare Effekt: der in den neuen Absatz
        // getippte Text trägt die Absatz-Default-Dekoration.
        type("Title");
        editor.applyHeading(1);
        flush();
        enter();
        type("body");

        assertEquals("Title\nbody", document().getText());
        assertEquals(2, paragraphCount());
        TextDecoration newParagraph = textDecorationAt(6);
        assertEquals(MarkdownStyles.DEFAULT_FONT_SIZE, newParagraph.getFontSize());
        assertEquals(javafx.scene.text.FontWeight.NORMAL, newParagraph.getFontWeight());
    }

    @Test
    void enterOnEmptyQuote_endsQuoteWithoutNewParagraph() {
        // Leerer Zitat-Absatz: Enter beendet das Zitat, KEIN neuer Absatz.
        // Geprüft wird auch der Tipp-Modus: danach getippter Text ist Ebene 0.
        editor.applyQuote();
        flush();
        enter();
        type("x");

        assertEquals("x", document().getText());
        assertEquals(1, paragraphCount());
        assertEquals(0, paragraphDecorationAt(0).getIndentationLevel());
    }

    @Test
    void enterInList_continuesList() {
        // Builtin: Dekorations-Vererbung setzt die Liste fort
        type("first");
        editor.toggleBulletList();
        flush();
        enter();
        type("second");

        assertEquals("first\nsecond", document().getText());
        ParagraphDecoration pd = paragraphDecorationAt(7);
        assertEquals(ParagraphDecoration.GraphicType.BULLETED_LIST, pd.getGraphicType());
        assertEquals(1, pd.getIndentationLevel());
    }

    @Test
    void enterOnEmptyTopLevelListItem_endsList() {
        // Erstes Enter: Liste wird fortgesetzt (neuer leerer Eintrag).
        // Zweites Enter auf dem LEEREN Eintrag (Ebene 1): Dedent beendet die
        // Liste – danach getippter Text ist ein normaler Absatz.
        type("first");
        editor.toggleBulletList();
        flush();
        enter();
        enter();
        type("second");

        assertEquals("first\nsecond", document().getText());
        ParagraphDecoration pd = paragraphDecorationAt(6);
        assertEquals(ParagraphDecoration.GraphicType.NONE, pd.getGraphicType());
        assertEquals(0, pd.getIndentationLevel());
    }

    @Test
    void enterInNormalParagraph_createsNewParagraph() {
        type("line");
        enter();
        type("next");

        assertEquals("line\nnext", document().getText());
        assertEquals(2, paragraphCount());
    }

    @Test
    void enterInMiddleOfHeadingText_createsNormalParagraph() {
        // Enter mitten im Überschriftentext (nicht am Ende)
        type("Title");
        editor.applyHeading(1);
        flush();
        home();
        press(KeyCode.RIGHT, false, false);
        press(KeyCode.RIGHT, false, false); // Caret hinter "Ti"
        enter();

        assertEquals("Ti\ntle", document().getText());
        TextDecoration newParagraph = textDecorationAt(3);
        assertEquals(MarkdownStyles.DEFAULT_FONT_SIZE, newParagraph.getFontSize());
        assertEquals(javafx.scene.text.FontWeight.NORMAL, newParagraph.getFontWeight());
    }

    // ---------- Undo/Redo ----------

    @Test
    void undo_revertsBold() {
        type("hello");
        selectAll();
        editor.toggleBold();
        flush();
        assertEquals(javafx.scene.text.FontWeight.BOLD, textDecorationAt(0).getFontWeight());

        FxTestUtil.runAndWait(() -> rta.getActionFactory().undo().execute(new ActionEvent()));
        flush();

        assertEquals(javafx.scene.text.FontWeight.NORMAL, textDecorationAt(0).getFontWeight());
    }

    @Test
    void redo_reappliesBold() {
        type("hello");
        selectAll();
        editor.toggleBold();
        flush();
        FxTestUtil.runAndWait(() -> rta.getActionFactory().undo().execute(new ActionEvent()));
        flush();
        FxTestUtil.runAndWait(() -> rta.getActionFactory().redo().execute(new ActionEvent()));
        flush();

        assertEquals(javafx.scene.text.FontWeight.BOLD, textDecorationAt(0).getFontWeight());
    }

    // ---------- getMarkdown (Phase 2: Writer-Anbindung) ----------

    @Test
    void getMarkdown_serializesPlainText() {
        type("Hello");
        assertEquals("Hello", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void getMarkdown_serializesHeading() {
        type("Title");
        editor.applyHeading(1);
        flush();
        assertEquals("# Title", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void getMarkdown_serializesBoldSelection() {
        type("bold");
        selectAll();
        editor.toggleBold();
        flush();
        assertEquals("**bold**", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void getMarkdown_serializesBulletList() {
        type("item");
        editor.toggleBulletList();
        flush();
        assertEquals("- item", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void getMarkdown_serializesQuote() {
        type("quoted");
        editor.applyQuote();
        flush();
        assertEquals("> quoted", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void getMarkdown_serializesMultipleParagraphs() {
        type("first");
        enter();
        type("second");
        assertEquals("first\nsecond", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    // ---------- setMarkdown (Phase 3: Parser-Anbindung) ----------

    @Test
    void setMarkdown_loadsPlainText() {
        editor.setMarkdown("Hello");
        flush();
        assertEquals("Hello", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void setMarkdown_loadsHeading() {
        editor.setMarkdown("# Title");
        flush();
        assertEquals("# Title", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void setMarkdown_loadsInlineFormatting() {
        editor.setMarkdown("a **b** c");
        flush();
        assertEquals("a **b** c", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void setMarkdown_loadsList() {
        editor.setMarkdown("- a\n- b");
        flush();
        assertEquals("- a\n- b", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void setMarkdown_loadsQuote() {
        editor.setMarkdown("> quoted");
        flush();
        assertEquals("> quoted", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void setMarkdown_loadsCodeBlock() {
        editor.setMarkdown("```\ncode\n```");
        flush();
        assertEquals("```\ncode\n```", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void setMarkdown_replacesExistingContent() {
        type("old content");
        editor.setMarkdown("new");
        flush();
        assertEquals("new", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void setMarkdown_emptyStringClearsDocument() {
        type("something");
        editor.setMarkdown("");
        flush();
        assertEquals("", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void setMarkdown_loadsMixedDocument() {
        String md = "# Title\n\npara text\n\n- a\n- b\n\n> quote";
        editor.setMarkdown(md);
        flush();
        assertEquals(md, FxTestUtil.callAndWait(editor::getMarkdown));
    }

    // ---------- Horizontal Rule einfügen (MVP 7.1) ----------

    @Test
    void insertHorizontalRule_createsStruckThroughSpaces() {
        type("before");
        editor.insertHorizontalRule();
        flush();

        String markdown = FxTestUtil.callAndWait(editor::getMarkdown);
        assertTrue(markdown.contains("---"), "HR expected in: " + markdown);
    }

    @Test
    void insertHorizontalRule_roundTrips() {
        type("before");
        editor.insertHorizontalRule();
        flush();
        type("after");

        String markdown = FxTestUtil.callAndWait(editor::getMarkdown);
        // HR steht als eigene Zeile zwischen den Absätzen
        assertEquals("before\n\n---\n\nafter", markdown);
    }

    @Test
    void insertHorizontalRule_isUndoable() {
        type("text");
        editor.insertHorizontalRule();
        flush();
        undoUntil("text");

        assertEquals("text", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    // ---------- Bild einfügen (Soll 7.2) ----------

    @Test
    void insertImage_createsImageBlock() {
        editor.insertImage("images/pic.png");
        flush();

        String markdown = FxTestUtil.callAndWait(editor::getMarkdown);
        assertEquals("![](images/pic.png)", markdown);
    }

    @Test
    void insertImage_afterText() {
        type("text");
        editor.insertImage("pic.png");
        flush();

        String markdown = FxTestUtil.callAndWait(editor::getMarkdown);
        assertTrue(markdown.contains("![](pic.png)"), "image expected in: " + markdown);
    }

    @Test
    void insertImage_isUndoable() {
        editor.insertImage("pic.png");
        flush();
        undoUntil("");

        assertEquals("", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    // ---------- Code-Block einfügen (Soll 7.2) ----------

    @Test
    void insertCodeBlock_createsFencedBlock() {
        editor.insertCodeBlock("int x = 1;");
        flush();

        String markdown = FxTestUtil.callAndWait(editor::getMarkdown);
        assertEquals("```\nint x = 1;\n```\n", markdown);
    }

    @Test
    void insertCodeBlock_multiLine() {
        editor.insertCodeBlock("line1\nline2");
        flush();

        String markdown = FxTestUtil.callAndWait(editor::getMarkdown);
        assertEquals("```\nline1\nline2\n```\n", markdown);
    }

    @Test
    void insertCodeBlock_afterText() {
        type("text");
        editor.insertCodeBlock("code");
        flush();

        String markdown = FxTestUtil.callAndWait(editor::getMarkdown);
        assertEquals("text\n\n```\ncode\n```\n", markdown);
    }

    @Test
    void insertCodeBlock_isUndoable() {
        editor.insertCodeBlock("code");
        flush();
        undoUntil("");

        assertEquals("", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    // ---------- Tabelle einfügen (native Tabellen-Darstellung, PROJEKTPLAN 7.1) ----------

    /**
     * Die Bibliothek stellt Tabellen nativ dar: EIN Absatz mit
     * {@link com.gluonhq.richtextarea.model.TableDecoration} in der
     * ParagraphDecoration, Zellen getrennt durch
     * {@code \u200b} (ZERO_WIDTH_TABLE_SEPARATOR). Genau diese Darstellung
     * erzeugt der Einfüge-Dialog (Zeilen/Spalten wie in Word) – kein
     * Markdown-Syntax-Rohtext.
     */
    @Test
    void insertTable_createsNativeTableDecoration() {
        editor.insertTable(2, 3);
        flush();

        Document doc = document();
        ParagraphDecoration table = null;
        for (DecorationModel dm : doc.getDecorations()) {
            if (dm.getParagraphDecoration() != null
                    && dm.getParagraphDecoration().hasTableDecoration()) {
                table = dm.getParagraphDecoration();
                break;
            }
        }
        assertNotNull(table, "TableDecoration expected in: " + doc.getDecorations());
        assertEquals(2, table.getTableDecoration().getRows());
        assertEquals(3, table.getTableDecoration().getColumns());
    }

    @Test
    void insertTable_separatesCellsWithZeroWidthSeparators() {
        editor.insertTable(2, 2);
        flush();

        // rows * columns - 1 Separatoren in Zeilen-Hauptordnung
        long separators = document().getText().chars().filter(ch -> ch == '\u200b').count();
        assertEquals(3, separators);
    }

    @Test
    void insertTable_writesGfmTableOnSave() {
        editor.insertTable(2, 2);
        flush();

        // Die native Tabelle wird beim Speichern als GFM-Markdown geschrieben
        assertEquals("|  |  |\n| --- | --- |\n|  |  |\n",
                FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void insertTable_isOwnParagraphAfterText() {
        type("before");
        editor.insertTable(2, 2);
        flush();

        assertEquals("before\n\n|  |  |\n| --- | --- |\n|  |  |\n",
                FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void insertTable_typingLandsInFirstCell() {
        editor.insertTable(2, 2);
        flush();
        type("a");
        flush();

        assertEquals("| a |  |\n| --- | --- |\n|  |  |\n",
                FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void insertTable_canReloadedFromMarkdown() {
        editor.insertTable(2, 2);
        flush();
        String saved = FxTestUtil.callAndWait(editor::getMarkdown);
        editor.setMarkdown(saved);
        flush();

        assertEquals(saved, FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void insertTable_isUndoable() {
        editor.insertTable(2, 2);
        flush();
        undoUntil("");

        assertEquals("", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    /** Macht Undo-Schritte rückgängig, bis der erwartete Markdown-Zustand erreicht ist. */
    private void undoUntil(String expected) {
        for (int i = 0; i < 10; i++) {
            if (expected.equals(FxTestUtil.callAndWait(editor::getMarkdown))) {
                return;
            }
            FxTestUtil.runAndWait(() -> rta.getActionFactory().undo().execute(new ActionEvent()));
            flush();
        }
    }

    // ---------- Inline-Code (Soll 7.2) ----------

    @Test
    void toggleInlineCode_appliesMonospacedToSelection() {
        type("hello");
        selectAll();
        editor.toggleInlineCode();
        flush();

        String markdown = FxTestUtil.callAndWait(editor::getMarkdown);
        assertEquals("`hello`", markdown);
    }

    @Test
    void toggleInlineCode_togglesOff() {
        type("hello");
        selectAll();
        editor.toggleInlineCode();
        flush();
        editor.toggleInlineCode();
        flush();

        String markdown = FxTestUtil.callAndWait(editor::getMarkdown);
        assertEquals("hello", markdown);
    }

    @Test
    void toggleInlineCode_withoutSelection_setsDecorationAtCaret() {
        editor.toggleInlineCode();
        flush();
        type("x");

        String markdown = FxTestUtil.callAndWait(editor::getMarkdown);
        assertEquals("`x`", markdown);
    }

    // ---------- Tab/Shift+Tab Einzug (builtin, MVP 7.1) ----------

    @Test
    void tabKey_indentsListItem() {
        type("item");
        editor.toggleBulletList();
        flush();
        press(KeyCode.TAB, false, false);
        flush();

        assertEquals(2, paragraphDecorationAt(0).getIndentationLevel());
    }

    @Test
    void shiftTabKey_outdentsListItem() {
        type("item");
        editor.toggleBulletList();
        flush();
        press(KeyCode.TAB, false, false);
        flush();
        press(KeyCode.TAB, true, false);
        flush();

        assertEquals(1, paragraphDecorationAt(0).getIndentationLevel());
    }

    // ---------- Dokumentstruktur (Navigation) ----------

    @Test
    void getHeadings_returnsAllHeadingsWithLevelAndPosition() {
        editor.setMarkdown("# One\n\ntext\n\n## Two\n\n### Three");
        flush();

        var headings = FxTestUtil.callAndWait(editor::getHeadings);
        assertEquals(3, headings.size());
        assertEquals(1, headings.get(0).level());
        assertEquals("One", headings.get(0).text());
        assertEquals(2, headings.get(1).level());
        assertEquals("Two", headings.get(1).text());
        assertEquals(3, headings.get(2).level());
        assertEquals("Three", headings.get(2).text());
    }

    @Test
    void getHeadings_positionsPointAtHeadingStart() {
        editor.setMarkdown("# One\n\ntext\n\n## Two");
        flush();

        var headings = FxTestUtil.callAndWait(editor::getHeadings);
        // Caret an die gemeldete Position setzen und prüfen, dass dort die Überschrift steht
        FxTestUtil.runAndWait(() -> editor.goToPosition(headings.get(1).position()));
        flush();
        assertEquals(2, FxTestUtil.callAndWait(rta::getCaretPosition) - headings.get(1).position() + 2);
    }

    @Test
    void getHeadings_emptyForDocumentWithoutHeadings() {
        editor.setMarkdown("just text\n\nmore text");
        flush();

        assertTrue(FxTestUtil.callAndWait(editor::getHeadings).isEmpty());
    }

    @Test
    void getHeadings_emptyForEmptyDocument() {
        assertTrue(FxTestUtil.callAndWait(editor::getHeadings).isEmpty());
    }

    @Test
    void getHeadings_reflectsTypedHeadings() {
        type("Title");
        editor.applyHeading(1);
        flush();

        var headings = FxTestUtil.callAndWait(editor::getHeadings);
        assertEquals(1, headings.size());
        assertEquals("Title", headings.get(0).text());
    }

    @Test
    void goToPosition_movesCaret() {
        editor.setMarkdown("first\n\nsecond\n\nthird");
        flush();

        FxTestUtil.runAndWait(() -> editor.goToPosition(14));
        flush();

        assertEquals(14, FxTestUtil.callAndWait(rta::getCaretPosition));
    }

    @Test
    void goToPosition_clampsToDocumentLength() {
        editor.setMarkdown("short");
        flush();

        FxTestUtil.runAndWait(() -> editor.goToPosition(9999));
        flush();

        assertTrue(FxTestUtil.callAndWait(rta::getCaretPosition) <= 5);
    }

    @Test
    void goToPosition_scrollsTargetParagraphIntoView() {
        FxTestUtil.runAndWait(() -> editor.setMarkdown(longDocument(60)));
        flush();
        flush();

        var headings = FxTestUtil.callAndWait(editor::getHeadings);
        assertTrue(headings.size() >= 4, "langes Dokument mit Überschriften");
        DocumentHeading target = headings.get(headings.size() - 1);

        FxTestUtil.runAndWait(() -> paragraphListView().scrollTo(0));
        flush();
        flush();
        int targetIndex = paragraphIndexOfPosition(target.position());
        assertFalse(isParagraphVisible(targetIndex),
                "Ziel liegt vor der Navigation unterhalb des Sichtbereichs");

        FxTestUtil.runAndWait(() -> editor.goToPosition(target.position()));
        flush();
        flush();

        assertEquals(target.position(), FxTestUtil.callAndWait(rta::getCaretPosition));
        assertTrue(isParagraphVisible(targetIndex),
                "Zielabsatz " + targetIndex + " muss nach der Navigation sichtbar sein");
    }

    @Test
    void goToPosition_returnsFocusToEditor() {
        editor.setMarkdown(longDocument(60));
        flush();
        flush();

        // Fokus weg vom Editor – wie nach einem Klick in die Dokumentstruktur
        FxTestUtil.runAndWait(() -> {
            javafx.scene.control.ListView<com.gluonhq.richtextarea.model.Paragraph> list = paragraphListView();
            if (list != null) {
                list.requestFocus();
            }
        });
        flush();
        assertFalse(FxTestUtil.callAndWait(rta::isFocused));

        FxTestUtil.runAndWait(() -> editor.goToPosition(
                FxTestUtil.callAndWait(editor::getHeadings).get(3).position()));
        flush();

        assertTrue(FxTestUtil.callAndWait(rta::isFocused),
                "Der Fokus muss wie in Word zurück ins Dokument wandern, sonst ist der Caret unsichtbar");
    }

    /** Langes Dokument: alle 10 Absätze eine Überschrift. */
    private static String longDocument(int paragraphs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= paragraphs; i++) {
            sb.append(i % 10 == 1 ? "# Heading " + i : "Zeile " + i).append("\n\n");
        }
        return sb.toString();
    }

    /** Absatz-Liste des Editor-Skins (Style-Class der Bibliothek). */
    @SuppressWarnings("unchecked")
    private javafx.scene.control.ListView<com.gluonhq.richtextarea.model.Paragraph> paragraphListView() {
        return (javafx.scene.control.ListView<com.gluonhq.richtextarea.model.Paragraph>)
                rta.lookup(".paragraph-list-view");
    }

    /** Index des Absatzes, der die Position enthält (letzter mit start &le; position). */
    private int paragraphIndexOfPosition(int position) {
        javafx.scene.control.ListView<com.gluonhq.richtextarea.model.Paragraph> list = paragraphListView();
        int index = -1;
        var items = list.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getStart() <= position) {
                index = i;
            } else {
                break;
            }
        }
        return index;
    }

    /** Steht der Absatz mit dem Index im sichtbaren Bereich des Editors? */
    private boolean isParagraphVisible(int paragraphIndex) {
        return FxTestUtil.callAndWait(() -> {
            javafx.geometry.Bounds editor = rta.localToScreen(rta.getBoundsInLocal());
            return paragraphListView().lookupAll(".list-cell").stream()
                    .filter(ListCell.class::isInstance)
                    .map(c -> (ListCell<?>) c)
                    .filter(c -> c.getIndex() == paragraphIndex)
                    .anyMatch(c -> {
                        javafx.geometry.Bounds b = c.localToScreen(c.getBoundsInLocal());
                        return b != null
                                && b.getMaxY() >= editor.getMinY() - 1
                                && b.getMinY() <= editor.getMaxY() + 1;
                    });
        });
    }

    // ---------- Fassaden-Grundlagen ----------

    @Test
    void controlIsExposedAndHasSkin() {
        assertNotNull(editor.getControl());
        flush();
        assertNotNull(FxTestUtil.callAndWait(rta::getSkin));
    }

    @Test
    void modifiedProperty_reflectsChanges() {
        FxTestUtil.runAndWait(() -> rta.getActionFactory().save().execute(new ActionEvent()));
        flush();
        assertFalse(FxTestUtil.callAndWait(rta::isModified));

        type("x");
        assertTrue(FxTestUtil.callAndWait(rta::isModified));
    }

    @Test
    void caretPositionProperty_tracksCaret() {
        type("abc");
        assertEquals(3, caretPosition());
    }

    @Test
    void decorationsCoverFullDocument() {
        type("hello");
        Document doc = document();
        List<DecorationModel> decorations = doc.getDecorations();
        assertFalse(decorations.isEmpty());
        int covered = decorations.stream().mapToInt(DecorationModel::getLength).sum();
        assertEquals(doc.getText().length(), covered);
    }

    // ---------- Drucken ----------

    /**
     * Die Druckfassung ist ein eigenständiges Control mit dem vollständigen
     * Dokument – das angezeigte Dokument bleibt unberührt (wie Word: der
     * Ausdruck verändert nie die Bildschirmansicht).
     */
    @Test
    void createPrintCopyContainsWholeDocument() {
        FxTestUtil.runAndWait(() -> editor.setMarkdown("# Titel\n\nEin Absatz mit Text"));
        flush();

        RichTextArea copy = FxTestUtil.callAndWait(editor::createPrintCopy);
        assertNotSame(rta, copy);
        // Skin in einer eigenen Szene, dann laden wie im Druckablauf
        FxTestUtil.runAndWait(() -> {
            Stage copyStage = new Stage();
            copyStage.setScene(new Scene(new StackPane(copy), 500, 400));
            copyStage.show();
        });
        flush();
        FxTestUtil.runAndWait(() -> editor.loadPrintCopy(copy));
        flush();
        flush();

        String copyText = FxTestUtil.callAndWait(() -> copy.getDocument().getText());
        assertTrue(copyText.contains("Titel"), copyText);
        assertTrue(copyText.contains("Ein Absatz mit Text"), copyText);
    }

    @Test
    void createPrintCopy_leavesScreenDocumentUnchanged() {
        FxTestUtil.runAndWait(() -> editor.setMarkdown("# Titel\n\nEin Absatz mit Text"));
        flush();
        String before = FxTestUtil.callAndWait(editor::getMarkdown);

        FxTestUtil.callAndWait(editor::createPrintCopy);
        flush();

        assertEquals(before, FxTestUtil.callAndWait(editor::getMarkdown));
    }
}
