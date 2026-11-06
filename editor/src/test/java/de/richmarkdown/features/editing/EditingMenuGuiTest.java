package de.richmarkdown.features.editing;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.Selection;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.animation.AnimationTimer;
import javafx.event.ActionEvent;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Uses an actual Stage and mouse events on rendered text and popup menu items. */
class EditingMenuGuiTest {
    private MarkdownEditor editor;
    private Stage stage;
    private Menu menu;
    private FxRobot robot;
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @BeforeEach void setup() throws Exception {
        robot = new FxRobot();
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor(); stage = new Stage();
            menu = EditingCommands.createMenu(editor, stage);
            var root = new BorderPane(editor.getControl()); root.setTop(new MenuBar(menu));
            stage.setScene(new Scene(root, 760, 480)); stage.show();
        });
        pulses();
    }
    @AfterEach void close() { FxTestUtil.runAndWait(() -> { menu.hide(); stage.close(); }); }
    private void pulses() throws Exception {
        var done = new CountDownLatch(1);
        FxTestUtil.runAndWait(() -> new AnimationTimer() {
            private int count;
            @Override public void handle(long now) { if (++count == 4) { stop(); done.countDown(); } }
        }.start());
        assertTrue(done.await(5, TimeUnit.SECONDS), "Rendered GUI must complete layout pulses");
        FxTestUtil.flushFxEvents();
    }
    private void open(String markdown) throws Exception {
        FxTestUtil.runAndWait(() -> editor.setMarkdown(markdown)); pulses();
    }
    private String text() { return FxTestUtil.callAndWait(() -> editor.getDocumentSnapshot().getText()); }
    private String markdown() { return FxTestUtil.callAndWait(editor::getMarkdownSource); }
    private com.gluonhq.richtextarea.viewmodel.RichTextAreaViewModel model() {
        return ((RichTextAreaSkin) editor.getControl().getSkin()).getViewModel();
    }
    private void clickText(String value) throws Exception {
        Point2D at = FxTestUtil.callAndWait(() -> editor.getControl().lookupAll("Text").stream()
                .filter(Text.class::isInstance).map(Text.class::cast).filter(node -> node.getText().contains(value))
                .map(node -> node.localToScreen(node.getBoundsInLocal().getMinX() + 8,
                        node.getBoundsInLocal().getMinY() + node.getBoundsInLocal().getHeight() / 2))
                .findFirst().orElseThrow());
        robot.clickOn(at.getX(), at.getY()); pulses();
    }
    private void showMenu() throws Exception {
        robot.clickOn(FxTestUtil.callAndWait(() -> {
            var menuButton = stage.getScene().getRoot().lookup(".menu-bar .menu-button");
            assertNotNull(menuButton, "The rendered menu bar must contain its menu button");
            return menuButton;
        })); pulses();
        assertTrue(FxTestUtil.callAndWait(menu::isShowing));
    }
    private void clickItem(int index) throws Exception {
        var node = FxTestUtil.callAndWait(() -> {
            assertTrue(menu.isShowing(), "The actual popup must be open before clicking an item");
            var itemNode = menu.getItems().get(index).getStyleableNode();
            assertNotNull(itemNode);
            assertTrue(itemNode.getScene().getWindow().isShowing());
            return itemNode;
        });
        robot.clickOn(node); pulses();
    }

    @ParameterizedTest
    @ValueSource(strings = {"- Target", "1. Target", "# Target", "> Target", "```java\nTarget\n```", "| Target | Value |\n|---|---|\n| A | B |"})
    void actualClickInStructuralBlockDisablesParagraphCommandsWithoutMovingCaret(String block) throws Exception {
        open(block + "\n\nAfter"); clickText("Target");
        String before = text(); int caret = FxTestUtil.callAndWait(() -> editor.getControl().getCaretPosition());
        int history = FxTestUtil.callAndWait(() -> model().getUndoStackSize());
        showMenu();
        FxTestUtil.runAndWait(() -> {
            assertTrue(menu.getItems().get(6).isDisable());
            assertTrue(menu.getItems().get(7).isDisable());
            assertTrue(menu.getItems().get(8).isDisable());
            assertFalse(menu.getItems().get(0).isDisable(), "Case conversion of text remains meaningful");
        });
        clickItem(7); // Real pointer click on a disabled action must have no effect.
        FxTestUtil.runAndWait(() -> {
            assertThrows(IllegalStateException.class, () -> new EditingCommands(editor).moveParagraph(1));
            assertThrows(IllegalStateException.class, () -> new EditingCommands(editor).duplicateParagraph());
            assertEquals(history, model().getUndoStackSize());
            assertEquals(caret, editor.getControl().getCaretPosition());
        });
        assertEquals(before, text());
    }

    @Test void actualMenuDuplicateFollowsTheCopyAndKeyboardUndoRestoresClickedPosition() throws Exception {
        open("Before\n\nTarget text\n\nAfter"); clickText("Target text");
        String before = text(); int source = before.indexOf("Target text");
        int caret = FxTestUtil.callAndWait(() -> editor.getControl().getCaretPosition());
        assertTrue(caret >= source && caret < source + 11);
        showMenu(); assertFalse(FxTestUtil.callAndWait(() -> menu.getItems().get(8).isDisable()));
        clickItem(8);
        int copy = text().lastIndexOf("Target text"); assertTrue(copy > source);
        assertEquals(copy + caret - source, FxTestUtil.callAndWait(() -> editor.getControl().getCaretPosition()));
        assertTrue(FxTestUtil.callAndWait(() -> editor.getControl().isFocused()));
        robot.push(KeyCode.CONTROL, KeyCode.Z); pulses();
        assertEquals(before, text());
        assertEquals(caret, FxTestUtil.callAndWait(() -> editor.getControl().getCaretPosition()));
    }

    @Test void multipleParagraphSelectionCannotSilentlyMoveOrDuplicateOnlyItsLastParagraph() throws Exception {
        open("First\n\nSecond\n\nLast");
        FxTestUtil.runAndWait(() -> {
            int end = editor.getDocumentSnapshot().getText().indexOf("Last");
            model().setCaretPosition(end); model().setSelection(new Selection(0, end));
        }); pulses();
        String before = text(); showMenu();
        FxTestUtil.runAndWait(() -> {
            assertTrue(menu.getItems().get(6).isDisable()); assertTrue(menu.getItems().get(7).isDisable());
            assertTrue(menu.getItems().get(8).isDisable());
            assertThrows(IllegalStateException.class, () -> new EditingCommands(editor).moveParagraph(-1));
            assertThrows(IllegalStateException.class, () -> new EditingCommands(editor).duplicateParagraph());
        });
        assertEquals(before, text());
    }

    @Test void convertingPartialHeadingSelectionChangesWholeParagraphAndRetainsSelectionAndUndo() throws Exception {
        open("# One *two* three\n\nAfter"); String before = markdown();
        int start = text().indexOf("two");
        FxTestUtil.runAndWait(() -> {
            model().setCaretPosition(start); model().setSelection(new Selection(start, start + 3));
        }); pulses(); showMenu(); clickItem(13);
        assertFalse(markdown().startsWith("#"), "The whole heading must become an ordinary paragraph");
        FxTestUtil.runAndWait(() -> {
            assertEquals(start, editor.getControl().getCaretPosition());
            assertEquals(new Selection(start, start + 3), model().getSelection());
            editor.getControl().getActionFactory().undo().execute(new ActionEvent());
        }); pulses(); assertEquals(before, markdown());
    }

    @Test void emptyClipboardAndReadOnlyStateDisableMutatingMenuActions() throws Exception {
        open("Target text"); clickText("Target text");
        FxTestUtil.runAndWait(() -> { Clipboard.getSystemClipboard().clear(); editor.getControl().setEditable(false); });
        showMenu();
        FxTestUtil.runAndWait(() -> {
            for (int index : new int[] {0, 1, 2, 3, 4, 6, 7, 8, 11, 12, 13, 15, 16, 17})
                assertTrue(menu.getItems().get(index).isDisable(), "Mutating menu item " + index);
            assertFalse(menu.getItems().get(9).isDisable(), "Selecting text remains available");
        });
    }
}
