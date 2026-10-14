package de.richmarkdown.ui;

import com.gluonhq.richtextarea.RichTextArea;
import com.gluonhq.richtextarea.model.Document;
import com.gluonhq.richtextarea.model.ParagraphDecoration;
import com.gluonhq.richtextarea.model.TextDecoration;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.markdown.MarkdownStyles;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD Phase 1: FormatToolBar – Toggles mit Caret-Zustand, Styles-Dropdown,
 * Listen-Buttons, Einzug (PROJEKTPLAN 7.1).
 */
class FormatToolBarTest {

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
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor();
            rta = editor.getControl();
            toolBar = new FormatToolBar(editor);
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(toolBar, rta), 800, 400));
            stage.show();
        });
    }

    private void flush() {
        FxTestUtil.flushFxEvents();
    }

    private void type(String text) {
        FxTestUtil.runAndWait(() -> {
            for (char c : text.toCharArray()) {
                rta.fireEvent(new KeyEvent(KeyEvent.KEY_TYPED, String.valueOf(c), "",
                        KeyCode.UNDEFINED, false, false, false, false));
            }
        });
    }

    private void selectAll() {
        FxTestUtil.runAndWait(() -> rta.getActionFactory().selectAll().execute(new ActionEvent()));
        flush();
    }

    private Document document() {
        FxTestUtil.runAndWait(() -> rta.getActionFactory().save().execute(new ActionEvent()));
        flush();
        return rta.getDocument();
    }

    private TextDecoration textDecorationAt(int index) {
        Document doc = document();
        for (var dm : doc.getDecorations()) {
            if (index >= dm.getStart() && index < dm.getStart() + dm.getLength()) {
                return (TextDecoration) dm.getDecoration();
            }
        }
        for (var dm : doc.getDecorations()) {
            if (dm.getStart() == index) {
                return (TextDecoration) dm.getDecoration();
            }
        }
        throw new AssertionError("no decoration at index " + index + " in " + doc);
    }

    private ParagraphDecoration paragraphDecorationAt(int index) {
        Document doc = document();
        for (var dm : doc.getDecorations()) {
            if (index >= dm.getStart() && index < dm.getStart() + dm.getLength()) {
                return dm.getParagraphDecoration();
            }
        }
        for (var dm : doc.getDecorations()) {
            if (dm.getStart() == index) {
                return dm.getParagraphDecoration();
            }
        }
        throw new AssertionError("no paragraph decoration at index " + index + " in " + doc);
    }

    // ---------- Grundstruktur ----------

    @Test
    void toolbarExposesAllFormatControls() {
        assertNotNull(toolBar.getBoldToggle());
        assertNotNull(toolBar.getItalicToggle());
        assertNotNull(toolBar.getUnderlineToggle());
        assertNotNull(toolBar.getStrikethroughToggle());
        assertNotNull(toolBar.getBulletListToggle());
        assertNotNull(toolBar.getNumberedListToggle());
        assertNotNull(toolBar.getStyleChoice());
        assertNotNull(toolBar.getIndentButton());
        assertNotNull(toolBar.getOutdentButton());
    }

    // ---------- Toggles wenden Formatierung an ----------

    @Test
    void boldToggle_appliesBoldToSelection() {
        type("hello");
        selectAll();
        FxTestUtil.runAndWait(() -> toolBar.getBoldToggle().setSelected(true));
        flush();

        assertEquals(javafx.scene.text.FontWeight.BOLD, textDecorationAt(0).getFontWeight());
    }

    @Test
    void italicToggle_appliesItalicToSelection() {
        type("hello");
        selectAll();
        FxTestUtil.runAndWait(() -> toolBar.getItalicToggle().setSelected(true));
        flush();

        assertEquals(javafx.scene.text.FontPosture.ITALIC, textDecorationAt(0).getFontPosture());
    }

    @Test
    void underlineToggle_appliesUnderlineToSelection() {
        type("hello");
        selectAll();
        FxTestUtil.runAndWait(() -> toolBar.getUnderlineToggle().setSelected(true));
        flush();

        assertTrue(textDecorationAt(0).isUnderline());
    }

    @Test
    void strikethroughToggle_appliesStrikethroughToSelection() {
        type("hello");
        selectAll();
        FxTestUtil.runAndWait(() -> toolBar.getStrikethroughToggle().setSelected(true));
        flush();

        assertTrue(textDecorationAt(0).isStrikethrough());
    }

    // ---------- Toggles spiegeln Caret-Zustand ----------

    @Test
    void boldToggle_reflectsCaretInBoldText() {
        type("bold");
        selectAll();
        FxTestUtil.runAndWait(() -> toolBar.getBoldToggle().setSelected(true));
        flush();
        FxTestUtil.runAndWait(() -> rta.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                KeyCode.HOME, false, false, false, false)));
        flush();

        assertTrue(FxTestUtil.callAndWait(() -> toolBar.getBoldToggle().isSelected()));
    }

    @Test
    void boldToggle_notSelectedInNormalText() {
        type("plain");
        FxTestUtil.runAndWait(() -> rta.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                KeyCode.HOME, false, false, false, false)));
        flush();

        assertFalse(FxTestUtil.callAndWait(() -> toolBar.getBoldToggle().isSelected()));
    }

    // ---------- Inline-Code (Soll 7.2) ----------

    @Test
    void toolbarExposesInlineCodeToggle() {
        assertNotNull(toolBar.getInlineCodeToggle());
    }

    @Test
    void inlineCodeToggle_appliesMonospacedToSelection() {
        type("hello");
        selectAll();
        FxTestUtil.runAndWait(() -> toolBar.getInlineCodeToggle().setSelected(true));
        flush();

        assertEquals("`hello`", FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void inlineCodeToggle_reflectsCaretInCode() {
        type("code");
        selectAll();
        FxTestUtil.runAndWait(() -> toolBar.getInlineCodeToggle().setSelected(true));
        flush();
        FxTestUtil.runAndWait(() -> rta.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                KeyCode.HOME, false, false, false, false)));
        flush();

        assertTrue(FxTestUtil.callAndWait(() -> toolBar.getInlineCodeToggle().isSelected()));
    }

    // ---------- Styles-Dropdown ----------

    @Test
    void styleChoice_appliesHeading1() {
        type("Title");
        selectAll();
        FxTestUtil.runAndWait(() -> toolBar.getStyleChoice().setValue(FormatToolBar.ParagraphStyle.H1));
        flush();

        TextDecoration td = textDecorationAt(0);
        assertEquals(MarkdownStyles.headingSize(1), td.getFontSize());
        assertEquals(javafx.scene.text.FontWeight.BOLD, td.getFontWeight());
    }

    @Test
    void styleChoice_appliesQuote() {
        type("quote");
        selectAll();
        FxTestUtil.runAndWait(() -> toolBar.getStyleChoice().setValue(FormatToolBar.ParagraphStyle.QUOTE));
        flush();

        ParagraphDecoration pd = paragraphDecorationAt(0);
        assertEquals(ParagraphDecoration.GraphicType.NONE, pd.getGraphicType());
        assertEquals(1, pd.getIndentationLevel());
    }

    @Test
    void styleChoice_appliesBodyStyle() {
        type("Title");
        selectAll();
        FxTestUtil.runAndWait(() -> toolBar.getStyleChoice().setValue(FormatToolBar.ParagraphStyle.H1));
        flush();
        FxTestUtil.runAndWait(() -> toolBar.getStyleChoice().setValue(FormatToolBar.ParagraphStyle.BODY));
        flush();

        TextDecoration td = textDecorationAt(0);
        assertEquals(MarkdownStyles.DEFAULT_FONT_SIZE, td.getFontSize());
        assertEquals(javafx.scene.text.FontWeight.NORMAL, td.getFontWeight());
    }

    @Test
    void styleChoice_reflectsHeadingAtCaret() {
        type("Title");
        selectAll();
        FxTestUtil.runAndWait(() -> toolBar.getStyleChoice().setValue(FormatToolBar.ParagraphStyle.H2));
        flush();
        FxTestUtil.runAndWait(() -> rta.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                KeyCode.HOME, false, false, false, false)));
        flush();

        assertEquals(FormatToolBar.ParagraphStyle.H2,
                FxTestUtil.callAndWait(() -> toolBar.getStyleChoice().getValue()));
    }

    // ---------- Listen-Buttons ----------

    @Test
    void bulletListToggle_appliesBulletedList() {
        type("item");
        selectAll();
        FxTestUtil.runAndWait(() -> toolBar.getBulletListToggle().setSelected(true));
        flush();

        ParagraphDecoration pd = paragraphDecorationAt(0);
        assertEquals(ParagraphDecoration.GraphicType.BULLETED_LIST, pd.getGraphicType());
        assertEquals(1, pd.getIndentationLevel());
    }

    @Test
    void numberedListToggle_appliesNumberedList() {
        type("item");
        selectAll();
        FxTestUtil.runAndWait(() -> toolBar.getNumberedListToggle().setSelected(true));
        flush();

        ParagraphDecoration pd = paragraphDecorationAt(0);
        assertEquals(ParagraphDecoration.GraphicType.NUMBERED_LIST, pd.getGraphicType());
        assertEquals(1, pd.getIndentationLevel());
    }

    @Test
    void bulletListToggle_reflectsCaretInList() {
        type("item");
        selectAll();
        FxTestUtil.runAndWait(() -> toolBar.getBulletListToggle().setSelected(true));
        flush();
        FxTestUtil.runAndWait(() -> rta.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                KeyCode.HOME, false, false, false, false)));
        flush();

        assertTrue(FxTestUtil.callAndWait(() -> toolBar.getBulletListToggle().isSelected()));
    }

    // ---------- Einzug ----------

    @Test
    void indentButton_increasesLevel() {
        type("item");
        selectAll();
        FxTestUtil.runAndWait(() -> toolBar.getBulletListToggle().setSelected(true));
        flush();
        FxTestUtil.runAndWait(() -> toolBar.getIndentButton().fire());
        flush();

        assertEquals(2, paragraphDecorationAt(0).getIndentationLevel());
    }

    @Test
    void outdentButton_decreasesLevel() {
        type("item");
        selectAll();
        FxTestUtil.runAndWait(() -> toolBar.getBulletListToggle().setSelected(true));
        flush();
        FxTestUtil.runAndWait(() -> toolBar.getIndentButton().fire());
        flush();
        FxTestUtil.runAndWait(() -> toolBar.getOutdentButton().fire());
        flush();

        assertEquals(1, paragraphDecorationAt(0).getIndentationLevel());
    }

    // ---------- Undo über Toolbar ----------

    @Test
    void boldToggle_isUndoable() {
        type("hello");
        selectAll();
        FxTestUtil.runAndWait(() -> toolBar.getBoldToggle().setSelected(true));
        flush();
        FxTestUtil.runAndWait(() -> rta.getActionFactory().undo().execute(new ActionEvent()));
        flush();

        assertEquals(javafx.scene.text.FontWeight.NORMAL, textDecorationAt(0).getFontWeight());
    }
}
