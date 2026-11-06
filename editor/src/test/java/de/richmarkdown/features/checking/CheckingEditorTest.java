package de.richmarkdown.features.checking;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import com.gluonhq.richtextarea.model.*;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CheckingEditorTest {
    private Stage stage;
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @AfterEach void close() { if (stage != null) FxTestUtil.runAndWait(stage::close); }
    private void flush() { for (int i = 0; i < 4; i++) FxTestUtil.flushFxEvents(); }
    private MarkdownEditor editor(String markdown) {
        MarkdownEditor editor = FxTestUtil.callAndWait(() -> {
            MarkdownEditor created = new MarkdownEditor(); stage = new Stage(); stage.setScene(new Scene(new StackPane(created.getControl()), 700, 500)); stage.show(); created.setMarkdown(markdown); return created;
        }); flush(); return editor;
    }
    @Test void bulkDestinationReplacementPreservesFormattingAndSupportsUndoRedo() {
        MarkdownEditor editor = editor("[**Fett**](old.md) und [Andere](other.md)");
        String original = FxTestUtil.callAndWait(editor::getMarkdownSource);
        FxTestUtil.runAndWait(() -> editor.replaceDocument(CheckingTools.replaceDestinations(editor.getDocumentSnapshot(), "old.md", "new.md"))); flush();
        String changed = FxTestUtil.callAndWait(editor::getMarkdownSource);
        assertTrue(changed.contains("**Fett**")); assertTrue(changed.contains("new.md")); assertTrue(changed.contains("other.md"));
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().undo().execute(new javafx.event.ActionEvent())); flush(); assertEquals(original, FxTestUtil.callAndWait(editor::getMarkdownSource));
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().redo().execute(new javafx.event.ActionEvent())); flush(); assertEquals(changed, FxTestUtil.callAndWait(editor::getMarkdownSource));
    }
    @Test void readonlyDisablesReplacementButKeepsInventoryAndChecksAvailable() {
        MarkdownEditor editor = editor("[Text](old.md)");
        FxTestUtil.runAndWait(() -> {
            var menu = CheckingTools.createMenu(editor, () -> stage);
            editor.getControl().setEditable(false);
            assertTrue(menu.getItems().getLast().isDisable()); assertFalse(menu.getItems().getFirst().isDisable());
            editor.getControl().setEditable(true); assertFalse(menu.getItems().getLast().isDisable());
        });
    }
    @Test void imageAndTextDestinationsAreChangedWithoutMutatingTheOriginal() {
        TextDecoration link = TextDecoration.builder().presets().url("old.md").build();
        ImageDecoration image = new ImageDecoration("old.md", 120, 80, "old.md");
        ParagraphDecoration paragraph = ParagraphDecoration.builder().presets().build();
        Document original = new Document("x\ufeff", List.of(new DecorationModel(0, 1, link, paragraph), new DecorationModel(1, 1, image, paragraph)), 0);
        Document changed = CheckingTools.replaceDestinations(original, "old.md", "new.md");
        ImageDecoration replacement = (ImageDecoration) changed.getDecorations().get(1).getDecoration();
        assertEquals("new.md", replacement.getUrl()); assertEquals("new.md", replacement.getLink()); assertEquals(120, replacement.getWidth());
        assertEquals("old.md", image.getUrl()); assertEquals("old.md", link.getURL());
        assertThrows(IllegalArgumentException.class, () -> CheckingTools.replaceDestinations(original, "old.md", "\n"));
    }
    @Test void bulkLinkEditKeepsBackwardSelectionAfterEmojiAndRestoresContextOnUndoRedo() {
        MarkdownEditor editor = editor("😀 prefix [**link**](old.md) suffix");
        final int[] context = new int[2];
        FxTestUtil.runAndWait(() -> {
            var model = ((com.gluonhq.richtextarea.RichTextAreaSkin) editor.getControl().getSkin()).getViewModel();
            String text = editor.getDocumentSnapshot().getText();
            context[0] = text.indexOf("link"); context[1] = context[0] + 4;
            model.setCaretPosition(model.getTextBuffer().getInternalPosition(context[0]));
            model.setSelection(model.getTextBuffer().getInternalSelection(new com.gluonhq.richtextarea.Selection(context[0], context[1])));
            CheckingTools.replaceKeepingPosition(editor, CheckingTools.replaceDestinations(editor.getDocumentSnapshot(), "old.md", "new.md"));
            assertEquals(context[0], editor.getControl().getCaretPosition());
            assertEquals(context[0], model.getTextBuffer().getText(0, model.getSelection().getStart()).length());
            assertEquals(context[1], model.getTextBuffer().getText(0, model.getSelection().getEnd()).length());
        }); flush();
        assertTrue(FxTestUtil.callAndWait(editor::getMarkdownSource).contains("new.md"));
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().undo().execute(new javafx.event.ActionEvent())); flush();
        assertEquals(context[0], FxTestUtil.callAndWait(editor.getControl()::getCaretPosition));
        assertTrue(FxTestUtil.callAndWait(editor::getMarkdownSource).contains("old.md"));
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().redo().execute(new javafx.event.ActionEvent())); flush();
        assertEquals(context[0], FxTestUtil.callAndWait(editor.getControl()::getCaretPosition));
        FxTestUtil.runAndWait(() -> {
            editor.getControl().setEditable(false);
            assertThrows(IllegalStateException.class, () -> CheckingTools.replaceKeepingPosition(editor, editor.getDocumentSnapshot()));
        });
    }

}
