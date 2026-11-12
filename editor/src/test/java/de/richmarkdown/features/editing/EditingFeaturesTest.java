package de.richmarkdown.features.editing;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.Selection;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.ui.DocumentStructureView;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class EditingFeaturesTest {
    private MarkdownEditor editor;
    private DocumentStructureView structure;
    private Stage stage;
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @BeforeEach void setup() {
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor(); structure = new DocumentStructureView(editor);
            stage = new Stage(); stage.setScene(new Scene(new HBox(structure, editor.getControl()), 900, 500)); stage.show();
        }); flush();
    }
    @AfterEach void close() { FxTestUtil.runAndWait(() -> { structure.close(); stage.close(); }); }
    private void flush() { for (int i = 0; i < 4; i++) FxTestUtil.flushFxEvents(); }
    private void open(String text) { FxTestUtil.runAndWait(() -> editor.setMarkdown(text)); flush(); }
    private String text() { return FxTestUtil.callAndWait(() -> editor.getDocumentSnapshot().getText()); }
    private void undo() { FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().undo().execute(new ActionEvent())); flush(); }
    @Test void modelFindsDescendantsAndMovesWholeChapter() {
        open("# Alpha\n\none two\n\n## Child\n\nthree\n\n# Beta\n\nfour");
        FxTestUtil.runAndWait(() -> {
            var source = editor.getDocumentSnapshot(); var chapters = ChapterModel.chapters(source);
            assertEquals(3, chapters.size());
            assertEquals("Alpha", chapters.getFirst().title());
            assertEquals(chapters.get(2).start(), chapters.getFirst().end());
            assertEquals(1, ChapterModel.filter(chapters, "ALP", 1).size());
            assertEquals(5, chapters.getFirst().words());
            var moved = ChapterModel.moveSibling(source, chapters.getFirst().start(), 1);
            assertTrue(moved.getText().indexOf("Beta") < moved.getText().indexOf("Alpha"));
            assertTrue(moved.getText().indexOf("Child") > moved.getText().indexOf("Alpha"));
            assertSame(source, ChapterModel.moveSibling(source, chapters.get(1).start(), -1));
        });
    }
    @Test void structureFiltersAndMovesUndoably() {
        open("# Alpha\n\nbody\n\n## Child\n\n# Beta\n\ntail");
        String original = text();
        FxTestUtil.runAndWait(() -> {
            structure.getSearchField().setText("BETA");
            assertEquals(1, structure.getListView().getItems().size());
            structure.getSearchField().clear(); structure.getMaximumLevel().setValue(1);
            assertEquals(2, structure.getListView().getItems().size());
            structure.getListView().getSelectionModel().select(0);
            assertTrue(structure.getChapterStatistics().matches("[0-9]+ .+"));
            structure.moveSelectedChapter(1);
        }); flush();
        assertTrue(text().indexOf("Beta") < text().indexOf("Alpha"));
        undo(); assertEquals(original, text());
    }
    @Test void foldingNeverChangesSavedContentAndNavigationUnfolds() {
        open("# Alpha\n\n😀 body\n\n## Child\n\ninside\n\n# Beta\n\ntail");
        String original = text();
        FxTestUtil.runAndWait(() -> {
            try (var folding = new ChapterFolding(editor)) {
                var chapter = ChapterModel.chapters(editor.getDocumentSnapshot()).getFirst();
                var skin = (RichTextAreaSkin) editor.getControl().getSkin();
                int before = skin.getVisibleParagraphStarts().size();
                folding.fold(chapter);
                assertTrue(folding.isFolded());
                assertTrue(skin.getVisibleParagraphStarts().size() < before);
                assertEquals(original, editor.getDocumentSnapshot().getText());
                int target = skin.getViewModel().getTextBuffer().getInternalSelection(new Selection(chapter.bodyStart(), chapter.end())).getStart();
                editor.goToPosition(target);
                assertFalse(folding.isFolded());
                assertEquals(before, skin.getVisibleParagraphStarts().size());
                assertFalse(editor.isModified());
            }
        });
    }
    @Test void foldingUnfoldsOnSelectionAndEdit() {
        open("# Alpha\n\nbody\n\n# Beta\n\ntail");
        FxTestUtil.runAndWait(() -> {
            try (var folding = new ChapterFolding(editor)) {
                var chapter = ChapterModel.chapters(editor.getDocumentSnapshot()).getFirst();
                folding.fold(chapter);
                ((RichTextAreaSkin) editor.getControl().getSkin()).getViewModel().setSelection(new Selection(0, chapter.end()));
                assertFalse(folding.isFolded());
                folding.fold(chapter);
                new EditingCommands(editor).paste("inserted", false);
                assertFalse(folding.isFolded());
            }
        });
    }
    @Test void searchRevealExpandsHiddenTargetWithoutMovingCaret() {
        open("# Alpha\n\nbody\n\n## Child\n\ninside\n\n# Beta\n\ntail");
        FxTestUtil.runAndWait(() -> {
            try (var folding = new ChapterFolding(editor)) {
                var chapter = ChapterModel.chapters(editor.getDocumentSnapshot()).getFirst();
                var skin = (RichTextAreaSkin) editor.getControl().getSkin();
                int before = skin.getVisibleParagraphStarts().size();
                folding.fold(chapter); int caret = editor.getInternalCaretPosition();
                int target = skin.getViewModel().getTextBuffer().getInternalSelection(new Selection(chapter.bodyStart(), chapter.end())).getStart();
                editor.revealPosition(target);
                assertEquals(before, skin.getVisibleParagraphStarts().size());
                assertEquals(caret, editor.getInternalCaretPosition());
                assertFalse(editor.isModified());
            }
        });
    }
    @Test void plainAndInterpretedPasteAreUndoableAndDiffer() {
        open("start");
        FxTestUtil.runAndWait(() -> new EditingCommands(editor).paste("**bold**", false)); flush();
        assertTrue(text().contains("**bold**")); undo(); assertEquals("start", text());
        FxTestUtil.runAndWait(() -> new EditingCommands(editor).paste("**bold**", true)); flush();
        assertTrue(text().contains("bold")); assertFalse(text().contains("**"));
        undo(); assertEquals("start", text());
    }
    @Test void literalMarkdownSurvivesSaveAndReparse() {
        open("");
        String literal = "**bold** [link](https://example.org) `code` _emphasis_";
        FxTestUtil.runAndWait(() -> new EditingCommands(editor).paste(literal, false)); flush();
        FxTestUtil.runAndWait(() -> {
            var reparsed = new de.richmarkdown.markdown.MarkdownParser().parseInto(editor.getMarkdownSource());
            assertEquals(literal, reparsed.getText());
            assertTrue(reparsed.getDecorations().stream().filter(d -> d.getDecoration() instanceof com.gluonhq.richtextarea.model.TextDecoration)
                    .allMatch(d -> ((com.gluonhq.richtextarea.model.TextDecoration) d.getDecoration()).getFontWeight() != javafx.scene.text.FontWeight.BOLD));
        });
    }
    @Test void transformationsUseExportedSelectionAfterEmoji() {
        open("😀 Straße tail");
        FxTestUtil.runAndWait(() -> {
            var skin = (RichTextAreaSkin) editor.getControl().getSkin();
            String raw = editor.getDocumentSnapshot().getText(); int start = raw.indexOf("Straße");
            skin.getViewModel().setSelection(skin.getViewModel().getTextBuffer().getInternalSelection(new Selection(start, start + 6)));
            new EditingCommands(editor).transform(DocumentEdits.Transform.UPPER);
        }); flush();
        assertEquals("😀 STRASSE tail", text()); undo(); assertEquals("😀 Straße tail", text());
    }
    @Test void paragraphCommandsAndClearFormattingHaveSingleUndo() {
        open("**first**\n\nsecond"); String original = text();
        FxTestUtil.runAndWait(() -> {
            editor.goToPosition(0);
            new EditingCommands(editor).duplicateParagraph();
        }); flush(); assertTrue(text().startsWith("first\nfirst"));
        undo(); assertEquals(original, text());
        FxTestUtil.runAndWait(() -> {
            new EditingCommands(editor).selectParagraph();
            assertTrue(editor.getControl().getSelection().isDefined());
            new EditingCommands(editor).clearFormatting(false, false);
        }); flush();
        String markdown = FxTestUtil.callAndWait(editor::getMarkdownSource); assertFalse(markdown.contains("**first**"));
        undo(); assertTrue(FxTestUtil.callAndWait(editor::getMarkdownSource).contains("**first**"));
    }
    @Test void readOnlyRejectsMutationAndEmptyPasteIsNoop() {
        open("original");
        FxTestUtil.runAndWait(() -> {
            var commands = new EditingCommands(editor); commands.paste(null, false); commands.paste("", true);
            assertFalse(editor.isModified());
            editor.getControl().setEditable(false);
            assertThrows(IllegalStateException.class, () -> commands.paste("changed", false));
            assertEquals("original", editor.getDocumentSnapshot().getText());
        });
    }
    @Test void everyEditingMenuActionIsAvailable() {
        FxTestUtil.runAndWait(() -> assertEquals(18, EditingCommands.createMenu(editor, stage).getItems().size()));
    }
    @Test void writingModesAreDisplayOnlyAndRestoreWidth() {
        open("# Heading\n\nbody"); String original = text();
        FxTestUtil.runAndWait(() -> {
            var controller = new WritingViewController(editor);
            double width = editor.getControl().getMaxWidth();
            controller.focusParagraphProperty().set(true); controller.typewriterProperty().set(true);
            controller.readingWidthProperty().set(true);
            var skin = (RichTextAreaSkin) editor.getControl().getSkin();
            assertTrue(skin.isFocusCurrentParagraph()); assertTrue(skin.isTypewriterMode());
            assertEquals(820, editor.getControl().getMaxWidth());
            controller.readingWidthProperty().set(false); assertEquals(width, editor.getControl().getMaxWidth());
            controller.focusParagraphProperty().set(false); controller.typewriterProperty().set(false);
            assertFalse(skin.isFocusCurrentParagraph()); assertFalse(skin.isTypewriterMode());
            assertEquals(4, controller.createMenu().getItems().size());
            assertEquals(original, editor.getDocumentSnapshot().getText()); assertFalse(editor.isModified());
        });
    }
    @Test void caretCommandsUseExportedOffsetsAfterMultipleEmoji() {
        open("😀😀😀😀😀😀 first\n\nsecond");
        String original = text();
        FxTestUtil.runAndWait(() -> {
            var skin = (RichTextAreaSkin) editor.getControl().getSkin();
            int start = editor.getDocumentSnapshot().getText().indexOf("second");
            int internal = skin.getViewModel().getTextBuffer().getInternalSelection(new Selection(start + 2, start + 3)).getStart();
            editor.goToPosition(internal);
            var commands = new EditingCommands(editor);
            assertEquals(start, commands.selectionOrParagraph().start());
            commands.paste("X", false);
        }); flush(); assertTrue(text().endsWith("seXcond")); undo(); assertEquals(original, text());
        FxTestUtil.runAndWait(() -> {
            var skin = (RichTextAreaSkin) editor.getControl().getSkin();
            int start = editor.getDocumentSnapshot().getText().indexOf("second");
            int internal = skin.getViewModel().getTextBuffer().getInternalSelection(new Selection(start, start + 1)).getStart();
            editor.goToPosition(internal); new EditingCommands(editor).duplicateParagraph();
        }); flush(); assertTrue(text().endsWith("second\nsecond")); undo(); assertEquals(original, text());
    }

}
