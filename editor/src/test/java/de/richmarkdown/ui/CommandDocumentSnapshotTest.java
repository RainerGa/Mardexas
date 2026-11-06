package de.richmarkdown.ui;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.model.Document;
import com.gluonhq.richtextarea.model.TextDecoration;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommandDocumentSnapshotTest {
    private static final class CountingEditor extends MarkdownEditor {
        private int exports;
        @Override public Document getDocumentSnapshot() { exports++; return super.getDocumentSnapshot(); }
    }
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @Test void cacheTracksBufferChangesButNotCaretOrSelection() {
        var editor = new CountingEditor[1]; var stage = new Stage[1];
        FxTestUtil.runAndWait(() -> {
            editor[0] = new CountingEditor(); stage[0] = new Stage();
            stage[0].setScene(new Scene(new StackPane(editor[0].getControl()), 700, 440)); stage[0].show();
            editor[0].setMarkdown("First paragraph\n\nSecond paragraph");
        });
        FxTestUtil.flushFxEvents();
        try {
            FxTestUtil.runAndWait(() -> {
                var control = editor[0].getControl(); var model = ((RichTextAreaSkin) control.getSkin()).getViewModel();
                CommandDocumentSnapshot.clear(editor[0]); int before = editor[0].exports;
                Document first = CommandDocumentSnapshot.get(editor[0]);
                model.setCaretPosition(5); model.setSelection(new com.gluonhq.richtextarea.Selection(1, 4));
                Document second = CommandDocumentSnapshot.get(editor[0]);
                assertEquals(before + 1, editor[0].exports, "Moving the caret must reuse the document export");
                assertNotSame(first, second); assertEquals(first.getText(), second.getText());
                assertThrows(UnsupportedOperationException.class, () -> first.getDecorations().clear());
                var buffer = model.getTextBuffer();
                buffer.decorate(0, 5, TextDecoration.builder().presets().underline(true).build());
                Document decorated = CommandDocumentSnapshot.get(editor[0]);
                assertEquals(before + 2, editor[0].exports);
                assertNotEquals(first.getDecorations(), decorated.getDecorations());
                buffer.undo(); assertEquals(first.getDecorations(), CommandDocumentSnapshot.get(editor[0]).getDecorations());
                assertEquals(before + 3, editor[0].exports);
                buffer.append("!"); assertTrue(CommandDocumentSnapshot.get(editor[0]).getText().endsWith("!"));
                assertEquals(before + 4, editor[0].exports);
                CommandDocumentSnapshot.clear(editor[0]);
                CommandDocumentSnapshot.get(editor[0]); assertEquals(before + 5, editor[0].exports);
            });
            FxTestUtil.runAndWait(() -> editor[0].setMarkdown("Replacement"));
            FxTestUtil.flushFxEvents();
            FxTestUtil.runAndWait(() -> assertTrue(CommandDocumentSnapshot.get(editor[0]).getText().startsWith("Replacement")));
        } finally { FxTestUtil.runAndWait(() -> { CommandDocumentSnapshot.clear(editor[0]); stage[0].close(); }); }
    }
}
