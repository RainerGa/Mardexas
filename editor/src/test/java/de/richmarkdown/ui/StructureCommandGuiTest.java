package de.richmarkdown.ui;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.GuiTestSupport;
import de.richmarkdown.editor.DocumentHeading;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/** Actual rendered outline, popup actions, keyboard filtering and cursor/navigation checks. */
class StructureCommandGuiTest {
    private MarkdownEditor editor;
    private DocumentStructureView structure;
    private Stage stage;

    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @AfterEach void close() { if (stage != null) FxTestUtil.runAndWait(stage::close); }
    private void open(String markdown) throws Exception {
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor();
            structure = new DocumentStructureView(editor);
            StackPane content = new StackPane(editor.getControl());
            HBox pane = new HBox(structure, content);
            HBox.setHgrow(content, Priority.ALWAYS);
            stage = new Stage();
            stage.setScene(new Scene(pane, 940, 460));
            stage.show();
            editor.setMarkdown(markdown);
        });
        GuiTestSupport.pulses();
    }
    private String markdown() { return FxTestUtil.callAndWait(editor::getMarkdownSource); }
    private int caret() { return FxTestUtil.callAndWait(() -> editor.getControl().getCaretPosition()); }
    private void go(int exported) throws Exception {
        FxTestUtil.runAndWait(() -> editor.goToPosition(((RichTextAreaSkin) editor.getControl().getSkin())
                .getViewModel().getTextBuffer().getInternalPosition(exported)));
        GuiTestSupport.pulses();
    }
    private void showContext() throws Exception {
        FxTestUtil.runAndWait(() -> {
            var list = structure.getListView();
            var point = list.localToScreen(20, 30);
            list.getContextMenu().show(list, point.getX(), point.getY());
        });
        GuiTestSupport.pulses();
    }
    private void clickItem(int index) throws Exception {
        String title = FxTestUtil.callAndWait(() -> structure.getListView().getContextMenu().getItems().get(index).getText());
        Node label = FxTestUtil.callAndWait(() -> Window.getWindows().stream()
                .filter(Window::isShowing).filter(window -> window.getScene() != null)
                .flatMap(window -> window.getScene().getRoot().lookupAll(".label").stream())
                .filter(Label.class::isInstance).map(Label.class::cast)
                .filter(node -> title.equals(node.getText())).findFirst().orElseThrow());
        GuiTestSupport.click(label, 5, label.getBoundsInLocal().getHeight() / 2);
    }
    private void clickHeading(String title) throws Exception {
        Node cell = FxTestUtil.callAndWait(() -> structure.getListView().lookupAll(".list-cell").stream()
                .filter(ListCell.class::isInstance).map(ListCell.class::cast)
                .filter(node -> node.getItem() instanceof DocumentHeading heading && heading.text().equals(title))
                .findFirst().orElseThrow());
        GuiTestSupport.click(cell, 40, cell.getBoundsInLocal().getHeight() / 2);
    }
    @Test void movingChapterThroughPopupRetainsBodyCaretAndUndoRestoresIt() throws Exception {
        open("# Alpha\n\n😀 body text\n\n## Child\n\nchild text\n\n# Beta\n\ntail");
        String original = markdown();
        int originalCaret = FxTestUtil.callAndWait(() -> editor.getDocumentSnapshot().getText().indexOf("body") + 3);
        go(originalCaret);
        showContext();
        clickItem(1);
        String moved = markdown();
        assertTrue(moved.indexOf("Beta") < moved.indexOf("Alpha"), moved);
        assertTrue(moved.indexOf("Alpha") < moved.indexOf("Child"), moved);
        assertEquals(FxTestUtil.callAndWait(() -> editor.getDocumentSnapshot().getText().indexOf("body") + 3), caret());
        assertTrue(FxTestUtil.callAndWait(() -> editor.getControl().isFocused()));
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().undo().execute(new javafx.event.ActionEvent()));
        GuiTestSupport.pulses();
        assertEquals(original, markdown());
        assertEquals(originalCaret, caret());
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().redo().execute(new javafx.event.ActionEvent()));
        GuiTestSupport.pulses();
        assertEquals(moved, markdown());
        assertEquals(FxTestUtil.callAndWait(() -> editor.getDocumentSnapshot().getText().indexOf("body") + 3), caret());
    }
    @Test void collapseAndMouseNavigationRevealHiddenChapterWithoutChangingMarkdown() throws Exception {
        open("# Alpha\n\n😀 body\n\n## Child\n\ninside\n\n# Beta\n\ntail");
        String original = markdown();
        clickHeading("Alpha");
        int before = FxTestUtil.callAndWait(() -> ((RichTextAreaSkin) editor.getControl().getSkin()).getVisibleParagraphStarts().size());
        showContext();
        clickItem(2);
        assertTrue(FxTestUtil.callAndWait(() -> ((RichTextAreaSkin) editor.getControl().getSkin()).getVisibleParagraphStarts().size()) < before);
        clickHeading("Child");
        assertEquals(before, FxTestUtil.callAndWait(() -> ((RichTextAreaSkin) editor.getControl().getSkin()).getVisibleParagraphStarts().size()));
        assertEquals(FxTestUtil.callAndWait(() -> editor.getDocumentSnapshot().getText().indexOf("Child")), caret());
        assertEquals(original, markdown());
        assertFalse(FxTestUtil.callAndWait(editor::isModified));
    }
    @Test void keyboardFilteringDoesNotNavigateAndContextActionsRespectParentAndReadonly() throws Exception {
        open("# Alpha\n\n## Child\n\ninside\n\n# Beta\n\ntail");
        clickHeading("Child");
        int originalCaret = caret();
        showContext();
        FxTestUtil.runAndWait(() -> {
            var items = structure.getListView().getContextMenu().getItems();
            assertTrue(items.get(0).isDisable());
            assertTrue(items.get(1).isDisable(), "Moving a child across its parent boundary is invalid");
        });
        GuiTestSupport.key(KeyCode.ESCAPE);
        var search = structure.getSearchField();
        GuiTestSupport.click(search, 20, search.getHeight() / 2);
        GuiTestSupport.key(KeyCode.B);
        assertEquals(originalCaret, caret(), "Filtering must not navigate away from the text");
        FxTestUtil.runAndWait(() -> {
            assertEquals(1, structure.getListView().getItems().size());
            assertEquals("Beta", structure.getListView().getItems().getFirst().text());
            search.clear();
            editor.getControl().setEditable(false);
        });
        clickHeading("Alpha");
        showContext();
        FxTestUtil.runAndWait(() -> assertTrue(structure.getListView().getContextMenu().getItems().get(1).isDisable()));
        GuiTestSupport.key(KeyCode.ESCAPE);
    }
}
