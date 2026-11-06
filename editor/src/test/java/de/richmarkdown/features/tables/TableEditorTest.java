package de.richmarkdown.features.tables;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class TableEditorTest {
    private Stage stage;
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @AfterEach void close() { if (stage != null) FxTestUtil.runAndWait(stage::close); }
    private void flush() { for (int i = 0; i < 4; i++) FxTestUtil.flushFxEvents(); }
    private MarkdownEditor editor(String markdown) {
        MarkdownEditor editor = FxTestUtil.callAndWait(() -> { MarkdownEditor created = new MarkdownEditor(); stage = new Stage(); stage.setScene(new Scene(new StackPane(created.getControl()), 700, 500)); stage.show(); created.setMarkdown(markdown); return created; }); flush(); return editor;
    }
    @Test void decoratedSortUndoRedoIsOneCommand() {
        MarkdownEditor editor = editor("Vorher\n\n| Name | Zahl |\n| --- | --- |\n| **Beta** | 2 |\n| *Alpha* | 1 |\n\nNachher");
        String original = FxTestUtil.callAndWait(editor::getMarkdownSource);
        FxTestUtil.runAndWait(() -> { var source = editor.getDocumentSnapshot(); var table = TableDocuments.at(source, source.getText().indexOf("Name")).orElseThrow(); editor.replaceDocument(TableDocuments.replace(source, table, java.util.List.of(table.data().sort(0, false, false)))); }); flush();
        String sorted = FxTestUtil.callAndWait(editor::getMarkdownSource); assertNotEquals(original, sorted); assertTrue(sorted.contains("**Beta**"));
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().undo().execute(new javafx.event.ActionEvent())); flush(); assertEquals(original, FxTestUtil.callAndWait(editor::getMarkdownSource));
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().redo().execute(new javafx.event.ActionEvent())); flush(); assertEquals(sorted, FxTestUtil.callAndWait(editor::getMarkdownSource));
    }
    @Test void clipboardTableInsertionUndoAndReadonlyGuard() {
        MarkdownEditor editor = editor("Text"); String original = FxTestUtil.callAndWait(editor::getMarkdownSource);
        FxTestUtil.runAndWait(() -> TableTools.insert(editor, DelimitedTables.parse("A\tB\n1\t2", '\t'))); flush(); assertTrue(FxTestUtil.callAndWait(editor::getMarkdownSource).contains("| A | B |"));
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().undo().execute(new javafx.event.ActionEvent())); flush(); assertEquals(original, FxTestUtil.callAndWait(editor::getMarkdownSource));
        FxTestUtil.runAndWait(() -> { editor.getControl().setEditable(false); assertThrows(IllegalStateException.class, () -> TableTools.insert(editor, TableData.plain(java.util.List.of(java.util.List.of("A"))))); });
    }
    @Test void menuContainsAllUserOperations() { MarkdownEditor editor = editor(""); FxTestUtil.runAndWait(() -> assertEquals(14, TableTools.createMenu(editor, () -> stage).getItems().stream().filter(item -> !(item instanceof javafx.scene.control.SeparatorMenuItem)).count())); }
    @Test void insertionAfterEmojiUsesExportedDocumentCoordinates() {
        MarkdownEditor editor = editor("😀 Anfang\n\nEnde");
        FxTestUtil.runAndWait(() -> {
            var model = ((com.gluonhq.richtextarea.RichTextAreaSkin) editor.getControl().getSkin()).getViewModel();
            int exported = editor.getDocumentSnapshot().getText().indexOf("Ende");
            model.setCaretPosition(model.getTextBuffer().getInternalPosition(exported));
            TableTools.insert(editor, TableData.plain(java.util.List.of(java.util.List.of("Header"), java.util.List.of("Value"))));
        }); flush();
        String markdown = FxTestUtil.callAndWait(editor::getMarkdownSource);
        assertTrue(markdown.indexOf("Anfang") < markdown.indexOf("Header"), markdown);
        assertTrue(markdown.indexOf("Header") < markdown.indexOf("Ende"), markdown);
        assertTrue(markdown.contains("😀 Anfang"), markdown);
    }
    @Test void completelyEmptySingleCellTableRetainsItsStructureAndUndo() {
        MarkdownEditor editor = editor("");
        String original = FxTestUtil.callAndWait(editor::getMarkdownSource);
        FxTestUtil.runAndWait(() -> TableTools.insert(editor, DelimitedTables.parse("\"\"", ',')));
        flush();
        assertTrue(FxTestUtil.callAndWait(() -> editor.getStatistics().tables()) > 0,
                "An empty table cell still has a table paragraph decoration");
        assertTrue(FxTestUtil.callAndWait(editor::getMarkdownSource).contains("|"));
        String tableMarkdown = FxTestUtil.callAndWait(editor::getMarkdownSource);
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().undo().execute(new javafx.event.ActionEvent()));
        flush();
        assertEquals(original, FxTestUtil.callAndWait(editor::getMarkdownSource));
        assertEquals(0L, FxTestUtil.callAndWait(() -> editor.getStatistics().tables()));
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().redo().execute(new javafx.event.ActionEvent()));
        flush();
        assertEquals(tableMarkdown, FxTestUtil.callAndWait(editor::getMarkdownSource));
        assertEquals(1L, FxTestUtil.callAndWait(() -> editor.getStatistics().tables()));
    }
}
