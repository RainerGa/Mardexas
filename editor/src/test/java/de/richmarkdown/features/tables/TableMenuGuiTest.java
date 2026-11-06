package de.richmarkdown.features.tables;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.GuiTestSupport;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/** Rendered Stage, real Glass mouse/keyboard input, actual menu handlers and undo history. */
class TableMenuGuiTest {
    private MarkdownEditor editor;
    private Stage stage;
    private Menu menu;
    private MenuBar bar;

    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @AfterEach void close() { if (stage != null) FxTestUtil.runAndWait(stage::close); }

    private void open(String markdown) throws Exception {
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor();
            stage = new Stage();
            menu = TableTools.createMenu(editor, () -> stage);
            bar = new MenuBar(menu);
            VBox pane = new VBox(bar, editor.getControl());
            VBox.setVgrow(editor.getControl(), Priority.ALWAYS);
            stage.setScene(new Scene(pane, 780, 440));
            stage.show();
            editor.setMarkdown(markdown);
        });
        GuiTestSupport.pulses();
    }

    private void showMenu() throws Exception {
        Node node = FxTestUtil.callAndWait(() -> bar.lookup(".menu"));
        assertNotNull(node);
        GuiTestSupport.click(node, 20, node.getBoundsInLocal().getHeight() / 2);
        assertTrue(FxTestUtil.callAndWait(menu::isShowing));
    }

    private void clickItem(String text) throws Exception {
        Node label = FxTestUtil.callAndWait(() -> Window.getWindows().stream()
                .filter(Window::isShowing).filter(window -> window.getScene() != null)
                .flatMap(window -> window.getScene().getRoot().lookupAll(".label").stream())
                .filter(Label.class::isInstance).map(Label.class::cast)
                .filter(node -> text.equals(node.getText())).findFirst().orElseThrow());
        GuiTestSupport.click(label, 5, label.getBoundsInLocal().getHeight() / 2);
    }

    private String markdown() { return FxTestUtil.callAndWait(editor::getMarkdownSource); }
    private int caret() { return FxTestUtil.callAndWait(() -> editor.getControl().getCaretPosition()); }
    private double scroll() { return FxTestUtil.callAndWait(() -> ((ScrollBar) editor.getControl().lookup(".scroll-bar:vertical")).getValue()); }

    private void go(int exported) throws Exception {
        FxTestUtil.runAndWait(() -> {
            var model = ((RichTextAreaSkin) editor.getControl().getSkin()).getViewModel();
            editor.goToPosition(model.getTextBuffer().getInternalPosition(exported));
        });
        GuiTestSupport.pulses();
    }

    @Test void transposingThroughVisibleMenuKeepsCaretAndViewportAndIsUndoable() throws Exception {
        open("Absatz vor der Tabelle.\n\n".repeat(90)
                + "| Name | Zahl |\n| --- | --- |\n| **Beta** | 2 |\n| *Alpha* | 1 |\n\n"
                + "Nach der Tabelle.\n\n".repeat(30));
        String original = markdown();
        int position = FxTestUtil.callAndWait(() -> editor.getDocumentSnapshot().getText().indexOf("Beta") + 2);
        go(position);
        double before = scroll();
        showMenu();
        clickItem("Transponieren");
        assertEquals(position, caret(), "The menu command must retain the active position in the table");
        assertEquals(before, scroll(), 0.04, "Editing a visible table must not jump to the document start");
        String changed = markdown();
        assertNotEquals(original, changed);
        assertTrue(changed.contains("**Beta**"));
        assertTrue(changed.contains("*Alpha*"));
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().undo().execute(new javafx.event.ActionEvent()));
        GuiTestSupport.pulses();
        assertEquals(original, markdown());
        assertEquals(position, caret());
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().redo().execute(new javafx.event.ActionEvent()));
        GuiTestSupport.pulses();
        assertEquals(changed, markdown());
        assertEquals(position, caret());
    }

    @Test void clipboardMenuInsertsOrdinaryMarkdownAndUndoRestoresCaret() throws Exception {
        open("😀 Anfang");
        go(FxTestUtil.callAndWait(() -> editor.getDocumentSnapshot().getText().length()));
        int previous = caret();
        String original = markdown();
        FxTestUtil.runAndWait(() -> {
            ClipboardContent content = new ClipboardContent();
            content.putString("Name\tZahl\nAlpha\t2");
            Clipboard.getSystemClipboard().setContent(content);
        });
        showMenu();
        clickItem("Tabelle aus Zwischenablage (TSV)…");
        assertTrue(markdown().contains("| Name | Zahl |"));
        assertEquals(previous + 1, caret(), "Caret belongs in the newly inserted table, after its paragraph boundary");
        assertTrue(FxTestUtil.callAndWait(() -> editor.getControl().isFocused()));
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().undo().execute(new javafx.event.ActionEvent()));
        GuiTestSupport.pulses();
        assertEquals(original, markdown());
        assertEquals(previous, caret());
    }

    @Test void menuDisablesTableOperationsOutsideTablesAndMutationsInReadonlyMode() throws Exception {
        open("Text\n\n| Name |\n| --- |\n| Wert |");
        go(1);
        showMenu();
        FxTestUtil.runAndWait(() -> {
            assertFalse(menu.getItems().getFirst().isDisable());
            for (int index = 2; index < menu.getItems().size(); index++)
                if (!(menu.getItems().get(index) instanceof javafx.scene.control.SeparatorMenuItem))
                    assertTrue(menu.getItems().get(index).isDisable(), menu.getItems().get(index).getText());
        });
        GuiTestSupport.key(KeyCode.ESCAPE);
        go(FxTestUtil.callAndWait(() -> editor.getDocumentSnapshot().getText().indexOf("Name")));
        FxTestUtil.runAndWait(() -> editor.getControl().setEditable(false));
        showMenu();
        FxTestUtil.runAndWait(() -> {
            assertFalse(menu.getItems().get(2).isDisable(), "Export remains available for a readonly table");
            assertFalse(menu.getItems().get(3).isDisable(), "Copy remains available for a readonly table");
            assertTrue(menu.getItems().get(7).isDisable(), "Transpose is a mutation");
            assertTrue(menu.getItems().getFirst().isDisable());
        });
        GuiTestSupport.key(KeyCode.ESCAPE);
    }
}
