package de.richmarkdown.ui;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.Selection;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.GuiTestSupport;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.settings.ApplicationSettings;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.robot.Robot;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real stages guard the command adapter, including actual native shortcut dispatch. */
class WindowCommandRegistryTest {
    private EditorWindow window;
    private final List<WindowCommandRegistry> registries = new ArrayList<>();

    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @BeforeEach void show() throws Exception {
        FxTestUtil.runAndWait(() -> {
            I18n.setSettingsStore(ApplicationSettings.inMemory()); I18n.setCurrentLanguage("de");
            UiModeService.reset(); window = new EditorWindow();
            window.setWidth(1000); window.setHeight(650); window.show();
            window.getEditor().setMarkdown("alpha beta"); window.getEditorControl().requestFocus();
        });
        GuiTestSupport.pulses();
    }
    @AfterEach void close() {
        FxTestUtil.runAndWait(() -> {
            registries.forEach(WindowCommandRegistry::close);
            for (Window dialog : List.copyOf(Window.getWindows())) {
                if (dialog.getScene() != null && dialog.getScene().lookup(".dialog-pane") instanceof DialogPane) dialog.hide();
            }
            window.hide(); window.disposeFeatures();
        });
    }
    private com.gluonhq.richtextarea.viewmodel.RichTextAreaViewModel model() {
        return ((RichTextAreaSkin) window.getEditorControl().getSkin()).getViewModel();
    }
    private void select(int start, int end) throws Exception {
        FxTestUtil.runAndWait(() -> {
            model().setCaretPosition(start); model().setSelection(new Selection(start, end));
            window.getEditorControl().requestFocus();
        }); GuiTestSupport.pulses();
    }
    private String text() { return FxTestUtil.callAndWait(() -> window.getEditor().getDocumentSnapshot().getText()); }
    private String markdown() { return FxTestUtil.callAndWait(() -> window.getEditor().getMarkdownSource()); }
    private void execute(String id) throws Exception {
        FxTestUtil.runAndWait(() -> assertTrue(window.getCommandRegistry().execute(id), "Available action: " + id));
        GuiTestSupport.pulses();
    }
    private void shortcut(KeyCode key, boolean shift) throws Exception {
        FxTestUtil.runAndWait(() -> {
            Robot robot = new Robot(); robot.keyPress(KeyCode.CONTROL);
            if (shift) robot.keyPress(KeyCode.SHIFT);
            robot.keyPress(key); robot.keyRelease(key);
            if (shift) robot.keyRelease(KeyCode.SHIFT);
            robot.keyRelease(KeyCode.CONTROL);
        }); GuiTestSupport.pulses();
    }
    private WindowCommandRegistry custom(Menu menu) {
        return FxTestUtil.callAndWait(() -> {
            WindowCommandRegistry registry = new WindowCommandRegistry(window, new MenuBar(menu));
            registries.add(registry); return registry;
        });
    }

    @Test void menuClipboardActionsExecuteTheirActionsAndSupportUndoRedo() throws Exception {
        select(0, 5); execute("menu.copy");
        assertEquals("alpha", FxTestUtil.callAndWait(() -> Clipboard.getSystemClipboard().getString()));
        execute("menu.cut"); assertEquals(" beta", text());
        execute("menu.undo"); assertEquals("alpha beta", text());
        select(6, 10);
        FxTestUtil.runAndWait(() -> {
            ClipboardContent clipboard = new ClipboardContent(); clipboard.putString("gamma");
            Clipboard.getSystemClipboard().setContent(clipboard);
        });
        execute("menu.paste"); assertEquals("alpha gamma", text());
        execute("menu.undo"); assertEquals("alpha beta", text());
        execute("menu.redo"); assertEquals("alpha gamma", text());
        execute("menu.selectAll");
        assertEquals(new Selection(0, 11), FxTestUtil.callAndWait(() -> model().getSelection()));
    }

    @ParameterizedTest
    @CsvSource({"menu.bold,B", "menu.italic,I", "menu.underline,U"})
    void formatShortcutExecutesOnceAndNativeUndoRedoExecuteOnce(String id, KeyCode key) throws Exception {
        select(0, 5); String original = markdown();
        int history = FxTestUtil.callAndWait(() -> model().getUndoStackSize());
        shortcut(key, false);
        String changed = markdown(); assertNotEquals(original, changed);
        FxTestUtil.runAndWait(() -> {
            assertEquals(history + 1, model().getUndoStackSize(), "One shortcut must create exactly one undo step");
            assertTrue(window.getCommandRegistry().get(id).isSelected(), "Ribbon toggle state must reflect the text decoration");
        });
        shortcut(KeyCode.Z, false); assertEquals(original, markdown());
        assertEquals(history, FxTestUtil.callAndWait(() -> model().getUndoStackSize()));
        shortcut(KeyCode.Z, true); assertEquals(changed, markdown());
        assertEquals(history + 1, FxTestUtil.callAndWait(() -> model().getUndoStackSize()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"menu.bold", "menu.italic", "menu.underline", "menu.strikethrough", "menu.inlineCode",
            "menu.bulletList", "menu.numberedList"})
    void selectedFormattingTracksWholeSelectionAndTogglingOff(String id) throws Exception {
        select(0, 5); execute(id);
        assertTrue(FxTestUtil.callAndWait(() -> window.getCommandRegistry().get(id).isSelected()), id);
        execute(id);
        assertFalse(FxTestUtil.callAndWait(() -> window.getCommandRegistry().get(id).isSelected()), id);
    }

    @Test void mixedFormattingIsNotPresentedAsUniformlySelected() throws Exception {
        FxTestUtil.runAndWait(() -> window.getEditor().setMarkdown("**alpha** beta"));
        GuiTestSupport.pulses(); select(0, 5);
        assertTrue(FxTestUtil.callAndWait(() -> window.getCommandRegistry().get("menu.bold").isSelected()));
        select(0, 10);
        assertFalse(FxTestUtil.callAndWait(() -> window.getCommandRegistry().get("menu.bold").isSelected()));
    }

    @Test void togglingMixedSelectionPreservesOtherFragmentStylesAndIsOneUndoStep() throws Exception {
        FxTestUtil.runAndWait(() -> window.getEditor().setMarkdown("**alpha** *beta*"));
        GuiTestSupport.pulses(); select(0, 10);
        String original = markdown();
        int history = FxTestUtil.callAndWait(() -> model().getUndoStackSize());
        execute("menu.bold");
        FxTestUtil.runAndWait(() -> {
            assertEquals(history + 1, model().getUndoStackSize());
            assertTrue(window.getCommandRegistry().get("menu.bold").isSelected());
            var runs = window.getEditor().getDocumentSnapshot().getDecorations();
            assertTrue(runs.stream().filter(run -> run.getStart() >= 6)
                    .allMatch(run -> run.getDecoration() instanceof com.gluonhq.richtextarea.model.TextDecoration style
                            && style.getFontPosture() == javafx.scene.text.FontPosture.ITALIC));
        });
        execute("menu.bold");
        assertFalse(FxTestUtil.callAndWait(() -> window.getCommandRegistry().get("menu.bold").isSelected()));
        execute("menu.undo"); execute("menu.undo");
        assertEquals(original, markdown());
    }

    @Test void editingAnAuxiliaryTextFieldDoesNotDispatchDocumentFormattingOrClipboardCommands() throws Exception {
        select(0, 5); String original = markdown();
        javafx.scene.control.TextField field = FxTestUtil.callAndWait(() -> {
            var input = new javafx.scene.control.TextField("field text");
            window.getChrome().getChildren().add(input); input.requestFocus(); return input;
        });
        GuiTestSupport.pulses(); shortcut(KeyCode.A, false);
        assertEquals("field text", FxTestUtil.callAndWait(field::getSelectedText));
        shortcut(KeyCode.X, false); assertEquals("", FxTestUtil.callAndWait(field::getText));
        assertEquals(original, markdown());
        shortcut(KeyCode.Z, false); assertEquals("field text", FxTestUtil.callAndWait(field::getText));
        assertEquals(original, markdown());
        shortcut(KeyCode.B, false); assertEquals(original, markdown());
    }

    @Test void nativeClipboardShortcutsTargetTheSelectionExactlyOnce() throws Exception {
        select(0, 5); shortcut(KeyCode.C, false);
        assertEquals("alpha", FxTestUtil.callAndWait(() -> Clipboard.getSystemClipboard().getString()));
        shortcut(KeyCode.X, false); assertEquals(" beta", text());
        shortcut(KeyCode.Z, false); assertEquals("alpha beta", text());
        shortcut(KeyCode.A, false);
        assertEquals(new Selection(0, 10), FxTestUtil.callAndWait(() -> model().getSelection()));
        FxTestUtil.runAndWait(() -> {
            ClipboardContent clipboard = new ClipboardContent(); clipboard.putString("replacement");
            Clipboard.getSystemClipboard().setContent(clipboard);
        });
        shortcut(KeyCode.V, false); assertEquals("replacement", text());
    }

    @ParameterizedTest
    @ValueSource(strings = {"menu.bold", "menu.italic", "menu.underline", "menu.strikethrough", "menu.inlineCode",
            "menu.bulletList", "menu.numberedList", "menu.heading1", "menu.body", "menu.quote",
            "menu.horizontalRule", "menu.link", "menu.image", "menu.codeBlock", "menu.insertTable"})
    void readOnlyPreventsEveryBasicFormattingAndInsertionCommand(String id) throws Exception {
        select(0, 5); String original = markdown();
        FxTestUtil.runAndWait(() -> window.getEditorControl().setEditable(false)); GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> {
            assertFalse(window.getCommandRegistry().get(id).isEnabled(), id);
            assertFalse(window.getCommandRegistry().execute(id));
        });
        assertEquals(original, markdown());
    }

    @Test void closedRegistryIsInertAndUnknownIdentityIsRejected() {
        FxTestUtil.runAndWait(() -> {
            var registry = window.getCommandRegistry(); registry.close(); registry.close();
            assertFalse(registry.execute("menu.bold"));
            assertThrows(IllegalArgumentException.class, () -> registry.get("unknown.command"));
            assertThrows(IllegalArgumentException.class, () -> registry.execute("unknown.command"));
        }); assertEquals("alpha beta", text());
    }

    @Test void ancestorAvailabilityIsRecheckedImmediatelyBeforeDispatch() {
        AtomicInteger calls = new AtomicInteger();
        Menu menu = FxTestUtil.callAndWait(() -> {
            Menu result = new Menu("Bearbeiten"); result.setId("menu.edit");
            MenuItem action = new MenuItem("Kopieren"); action.setId("menu.copy");
            action.setOnAction(event -> calls.incrementAndGet()); result.getItems().add(action); return result;
        });
        var registry = custom(menu);
        FxTestUtil.runAndWait(() -> {
            menu.setDisable(true); assertFalse(registry.execute("menu.copy"));
            menu.setDisable(false); menu.setVisible(false); assertFalse(registry.execute("menu.copy"));
            assertEquals(0, calls.get());
            menu.setVisible(true); assertTrue(registry.execute("menu.copy")); assertEquals(1, calls.get());
        });
    }

    @Test void failedCallbackShowsUnderstandableErrorAndKeepsDispatcherUsable() throws Exception {
        Menu menu = FxTestUtil.callAndWait(() -> {
            Menu result = new Menu("Bearbeiten"); result.setId("menu.edit");
            MenuItem action = new MenuItem("Kopieren"); action.setId("menu.copy");
            action.setOnAction(event -> { throw new IllegalStateException("Deliberate command failure"); });
            result.getItems().add(action); return result;
        });
        var registry = custom(menu);
        FxTestUtil.runAndWait(() -> assertDoesNotThrow(() -> registry.execute("menu.copy")));
        GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> {
            DialogPane alert = Window.getWindows().stream().filter(Window::isShowing)
                    .filter(stage -> stage.getScene() != null).map(stage -> stage.getScene().lookup(".dialog-pane"))
                    .filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst().orElseThrow();
            assertEquals("Deliberate command failure", alert.getContentText());
            assertTrue(registry.get("menu.copy").isEnabled());
        }); assertEquals("alpha beta", text());
    }

    @Test void synchronousContextChangesAreCoalescedIntoOneRefresh() throws Exception {
        AtomicInteger refreshes = new AtomicInteger();
        Menu menu = FxTestUtil.callAndWait(() -> {
            Menu result = new Menu("Bearbeiten"); result.setId("menu.edit");
            MenuItem action = new MenuItem("Kopieren"); action.setId("menu.copy"); result.getItems().add(action);
            result.getProperties().put(CommandIdentity.REFRESH, (Runnable) refreshes::incrementAndGet); return result;
        });
        custom(menu); GuiTestSupport.pulses(); int before = refreshes.get();
        FxTestUtil.runAndWait(() -> {
            for (int i = 0; i < 10; i++) model().setCaretPosition(i);
        }); GuiTestSupport.pulses();
        assertEquals(before + 1, refreshes.get(), "Caret events in one FX task must schedule one context refresh");
    }
}
