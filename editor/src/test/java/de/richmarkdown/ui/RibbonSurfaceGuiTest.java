package de.richmarkdown.ui;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.Selection;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.GuiTestSupport;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.settings.ApplicationSettings;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.shape.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** Integration checks use shown windows, rendered caret geometry and native Glass input. */
class RibbonSurfaceGuiTest {
    private EditorWindow window;
    private final List<EditorWindow> additionalWindows = new ArrayList<>();

    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }

    @BeforeEach void openWindow() throws Exception {
        FxTestUtil.runAndWait(() -> {
            I18n.setSettingsStore(ApplicationSettings.inMemory());
            I18n.setCurrentLanguage("de");
            UiModeService.reset();
            I18n.setDocumentStructureEnabled(false);
            window = new EditorWindow();
            window.setX(5); window.setY(5); window.setWidth(1000); window.setHeight(720);
            window.show();
            window.getCommandSurface().setMode(UiMode.CLASSIC);
        });
        GuiTestSupport.pulses();
    }

    @AfterEach void closeWindows() {
        FxTestUtil.runAndWait(() -> {
            window.getCommandSurface().setMode(UiMode.CLASSIC);
            additionalWindows.forEach(EditorWindow::hide);
            window.hide();
        });
    }

    private void open(String markdown) throws Exception {
        FxTestUtil.runAndWait(() -> window.getEditor().setMarkdown(markdown));
        GuiTestSupport.pulses();
    }

    private String text() { return FxTestUtil.callAndWait(() -> window.getEditor().getDocumentSnapshot().getText()); }
    private String markdown() { return FxTestUtil.callAndWait(() -> window.getEditor().getMarkdownSource()); }
    private com.gluonhq.richtextarea.viewmodel.RichTextAreaViewModel model() {
        return ((RichTextAreaSkin) window.getEditorControl().getSkin()).getViewModel();
    }
    private String command(String label) {
        return FxTestUtil.callAndWait(() -> window.getCommandRegistry().commands().stream()
                .filter(value -> label.equals(value.label())).findFirst().orElseThrow().id());
    }
    private void go(int position) throws Exception {
        FxTestUtil.runAndWait(() -> {
            window.getEditor().goToPosition(model().getTextBuffer().getInternalPosition(position));
            window.getEditorControl().requestFocus();
        });
        GuiTestSupport.pulses();
    }
    private ButtonBase showCommand(String id) throws Exception {
        FxTestUtil.runAndWait(() -> {
            window.getCommandSurface().setMode(UiMode.RIBBON);
            window.getCommandSurface().getRibbon().selectCommand(id);
            window.getCommandRegistry().refresh();
        });
        GuiTestSupport.pulses();
        return FxTestUtil.callAndWait(() -> {
            Node control = window.getCommandSurface().getRibbon().findControl(id);
            assertInstanceOf(ButtonBase.class, control, "The command must have a visible actionable control");
            return (ButtonBase) control;
        });
    }
    private void click(ButtonBase button) throws Exception {
        Bounds bounds = FxTestUtil.callAndWait(button::getBoundsInLocal);
        GuiTestSupport.click(button, bounds.getCenterX(), bounds.getCenterY());
    }
    private void shortcut(KeyCode key, boolean shift) throws Exception {
        GuiTestSupport.shortcut(key, shift);
    }
    private Bounds caretBounds() {
        var paths = window.getEditorControl().lookupAll(".caret").stream()
                .filter(Path.class::isInstance).map(Path.class::cast)
                .filter(path -> !path.getElements().isEmpty()).toList();
        assertEquals(1, paths.size(), "Exactly one real caret must be rendered");
        return paths.getFirst().localToScene(paths.getFirst().getBoundsInLocal());
    }

    @Test void everyRegisteredCommandIsReachableInBothSurfaces() throws Exception {
        open("A paragraph");
        FxTestUtil.runAndWait(() -> {
            assertTrue(window.getCommandRegistry().ids().size() > 100, "All existing feature menus must be covered");
            assertEquals(window.getCommandRegistry().ids(), window.getCommandSurface().getRibbon().commandIds());
            assertEquals(9, window.getCommandSurface().getMenuBar().getMenus().size());
            window.getCommandSurface().getMenuBar().getMenus().forEach(this::assertIcons);
        });
    }
    private void assertIcons(Menu menu) {
        assertNotNull(menu.getGraphic(), "Menu icon: " + menu.getText());
        for (MenuItem item : menu.getItems()) {
            if (item instanceof SeparatorMenuItem) continue;
            assertNotNull(item.getGraphic(), "Action icon: " + item.getText());
            if (item instanceof Menu nested) assertIcons(nested);
        }
    }

    @ParameterizedTest
    @EnumSource(UiMode.class)
    void removedIndexStaysAbsentWithLegacyPreferenceAndStructureStillToggles(UiMode mode,
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path profile) throws Exception {
        FxTestUtil.runAndWait(() -> {
            window.hide();
            ApplicationSettings settings = new ApplicationSettings(profile);
            settings.saveBoolean("indexView", true);
            I18n.setSettingsStore(settings);
            I18n.setDocumentStructureEnabled(true);
            window = new EditorWindow();
            window.setWidth(1000); window.setHeight(720); window.show();
            window.getCommandSurface().setMode(mode);
        });
        open("# Heading\n\nText to keep in place");
        go(text().indexOf("Text") + 3);
        String before = markdown();
        int caret = FxTestUtil.callAndWait(() -> window.getEditorControl().getCaretPosition());
        double narrow = FxTestUtil.callAndWait(() -> window.getEditorRoot().getCenter().getLayoutBounds().getWidth());
        FxTestUtil.runAndWait(() -> {
            assertFalse(window.getCommandRegistry().ids().contains("menu.view.index"));
            assertThrows(IllegalArgumentException.class, () -> window.getCommandRegistry().execute("menu.view.index"));
            assertFalse(window.getCommandSurface().getRibbon().commandIds().contains("menu.view.index"));
            assertFalse(CommandIcons.supports("menu.view.index"));
            assertNull(window.getScene().lookup(".rme-index-view"));
            assertSame(window.getStructureView(), window.getEditorRoot().getLeft());
            assertTrue(window.getStructureView().isVisible());
        });
        if (mode == UiMode.RIBBON) click(showCommand("menu.documentStructure"));
        else clickClassicCommand("menu.documentStructure");
        FxTestUtil.runAndWait(() -> {
            assertFalse(window.getStructureView().isVisible());
            assertFalse(window.getStructureView().isManaged());
            assertTrue(window.getEditorRoot().getCenter().getLayoutBounds().getWidth() > narrow + 100);
        });
        if (mode == UiMode.RIBBON) click(showCommand("menu.documentStructure"));
        else clickClassicCommand("menu.documentStructure");
        FxTestUtil.runAndWait(() -> {
            assertTrue(window.getStructureView().isVisible());
            assertTrue(window.getStructureView().getWidth() >= 180);
            assertEquals(caret, window.getEditorControl().getCaretPosition());
        });
        assertEquals(before, markdown());
    }

    @ParameterizedTest
    @ValueSource(strings = {"- Target", "1. Target", "# Target", "> Target", "```java\nTarget\n```", "| Target | Value |\n|---|---|\n| A | B |"})
    void structuralContextIsDisabledWithoutOpeningClassicMenu(String block) throws Exception {
        open(block + "\n\nAfter"); go(text().indexOf("Target") + 2);
        String before = markdown();
        ButtonBase move = showCommand(command("Absatz nach unten verschieben"));
        FxTestUtil.runAndWait(() -> assertTrue(move.isDisabled()));
        int history = FxTestUtil.callAndWait(() -> model().getUndoStackSize());
        click(move);
        assertEquals(before, markdown());
        assertEquals(history, FxTestUtil.callAndWait(() -> model().getUndoStackSize()));
    }

    @ParameterizedTest
    @EnumSource(UiMode.class)
    void sameCaseCommandThroughEitherSurfaceHasIdenticalResultAndUndo(UiMode mode) throws Exception {
        open("Before\n\nselected text\n\nAfter");
        go(text().indexOf("selected") + 3);
        String before = markdown();
        if (mode == UiMode.RIBBON) {
            click(showCommand(command("GROSSBUCHSTABEN")));
        } else {
            clickClassicCommand(command("GROSSBUCHSTABEN"));
        }
        assertTrue(text().contains("SELECTED TEXT"));
        assertTrue(FxTestUtil.callAndWait(() -> window.getEditorControl().isFocused()));
        String after = markdown();
        shortcut(KeyCode.Z, false); assertEquals(before, markdown());
        shortcut(KeyCode.Z, true); assertEquals(after, markdown());
    }

    @ParameterizedTest
    @EnumSource(UiMode.class)
    void licenseAndThanksAreUsableFromBothSurfacesWithoutChangingTheDocument(UiMode mode) throws Exception {
        open("# Keep this document\n\nSelected text");
        go(text().indexOf("Selected") + 4);
        String before = markdown();
        int caret = FxTestUtil.callAndWait(() -> window.getEditorControl().getCaretPosition());
        for (String id : List.of("menu.licensing", "menu.thanks")) {
            if (mode == UiMode.RIBBON) click(showCommand(id));
            else clickClassicCommand(id);
            GuiTestSupport.pulses();
            String dialogId = id.equals("menu.thanks") ? "#thanks-dialog" : "#license-dialog";
            FxTestUtil.runAndWait(() -> assertTrue(javafx.stage.Window.getWindows().stream().anyMatch(shown ->
                    shown.getScene().lookup(dialogId) != null)));
            GuiTestSupport.key(KeyCode.ESCAPE);
            FxTestUtil.runAndWait(() -> assertFalse(javafx.stage.Window.getWindows().stream().anyMatch(shown ->
                    shown.getScene().lookup(dialogId) != null)));
            assertEquals(before, markdown());
            assertEquals(caret, FxTestUtil.callAndWait(() -> window.getEditorControl().getCaretPosition()));
        }
    }

    private void clickClassicCommand(String id) throws Exception {
        List<MenuItem> path = FxTestUtil.callAndWait(() -> {
            for (Menu root : window.getCommandSurface().getMenuBar().getMenus()) {
                List<MenuItem> found = classicPath(root, id);
                if (found != null) return found;
            }
            throw new AssertionError("Missing classic command " + id);
        });
        for (MenuItem item : path) {
            Node row = FxTestUtil.callAndWait(() -> {
                Node node = item.getStyleableNode();
                if (item == path.getFirst()) {
                    // MenuBar headers are skin-owned MenuButtons; the Menu model itself
                    // has no styleable node until it becomes a popup submenu.
                    node = window.getCommandSurface().getMenuBar().lookupAll(".menu-button").stream()
                            .filter(javafx.scene.control.MenuButton.class::isInstance)
                            .map(javafx.scene.control.MenuButton.class::cast)
                            .filter(button -> item.getText().equals(button.getText())).findFirst().orElseThrow();
                }
                assertNotNull(node, "The actual menu row must have been rendered: " + item.getId());
                assertTrue(node.isVisible());
                assertTrue(node.getScene().getWindow().isShowing());
                return node;
            });
            Bounds bounds = FxTestUtil.callAndWait(row::getBoundsInLocal);
            GuiTestSupport.click(row, bounds.getCenterX(), bounds.getCenterY());
            if (item instanceof Menu submenu)
                assertTrue(FxTestUtil.callAndWait(submenu::isShowing), "The actual parent menu must be open: " + item.getId());
        }
    }
    private List<MenuItem> classicPath(MenuItem candidate, String id) {
        if (id.equals(candidate.getId())) return List.of(candidate);
        if (candidate instanceof Menu parent) {
            for (MenuItem child : parent.getItems()) {
                List<MenuItem> path = classicPath(child, id);
                if (path != null) {
                    List<MenuItem> complete = new ArrayList<>(); complete.add(parent); complete.addAll(path); return complete;
                }
            }
        }
        return null;
    }

    @Test void ribbonUppercaseKeepsSelectionCaretAndTypingLocationAndSupportsUndoRedo() throws Exception {
        open("Before\n\n😀 target text\n\nAfter");
        int start = text().indexOf("target"); go(start);
        FxTestUtil.runAndWait(() -> model().setSelection(new Selection(
                model().getTextBuffer().getInternalPosition(start), model().getTextBuffer().getInternalPosition(start + 6))));
        String before = markdown();
        click(showCommand(command("GROSSBUCHSTABEN")));
        assertTrue(text().contains("TARGET text"));
        FxTestUtil.runAndWait(() -> {
            assertTrue(window.getEditorControl().isFocused());
            Bounds caret = caretBounds();
            Bounds viewport = window.getEditorControl().localToScene(window.getEditorControl().getBoundsInLocal());
            assertTrue(caret.getMinY() >= viewport.getMinY() && caret.getMaxY() <= viewport.getMaxY());
        });
        String after = markdown(); shortcut(KeyCode.Z, false); assertEquals(before, markdown());
        shortcut(KeyCode.Z, true); assertEquals(after, markdown());
        GuiTestSupport.key(KeyCode.X);
        assertTrue(text().contains("x text"), "Typing must replace the retained selection at the action location");
    }

    @Test void repeatedSwitchesPreserveDocumentCaretSelectionAndUndoHistory() throws Exception {
        open(IntStream.range(0, 150).mapToObj(i -> "Paragraph " + i + " with text.")
                .collect(java.util.stream.Collectors.joining("\n\n")));
        int start = text().indexOf("Paragraph 115"); go(start + 8);
        GuiTestSupport.key(KeyCode.X);
        String before = markdown();
        Selection selection = new Selection(modelPosition(start + 12), modelPosition(start + 5));
        FxTestUtil.runAndWait(() -> { model().setSelection(selection);
            model().setCaretPosition(selection.getStart()); });
        int caret = FxTestUtil.callAndWait(() -> model().getCaretPosition());
        int history = FxTestUtil.callAndWait(() -> model().getUndoStackSize());
        boolean modified = FxTestUtil.callAndWait(() -> window.getEditor().isModified());
        for (int count = 0; count < 4; count++) {
            FxTestUtil.runAndWait(() -> window.getCommandSurface().setMode(UiMode.RIBBON));
            GuiTestSupport.pulses();
            FxTestUtil.runAndWait(() -> window.getCommandSurface().setMode(UiMode.CLASSIC));
            GuiTestSupport.pulses();
        }
        assertEquals(before, markdown());
        FxTestUtil.runAndWait(() -> {
            assertEquals(caret, model().getCaretPosition()); assertEquals(selection, model().getSelection());
            assertEquals(history, model().getUndoStackSize()); assertEquals(modified, window.getEditor().isModified());
            Bounds rendered = caretBounds();
            Bounds viewport = window.getEditorControl().localToScene(window.getEditorControl().getBoundsInLocal());
            assertTrue(rendered.getMinY() >= viewport.getMinY() && rendered.getMaxY() <= viewport.getMaxY());
        });
        shortcut(KeyCode.Z, false);
        assertFalse(text().contains("Paragrapxh 115"));
        assertEquals(history - 1, FxTestUtil.callAndWait(() -> model().getUndoStackSize()), "One key chord must undo once after repeated switches");
    }
    private int modelPosition(int position) { return FxTestUtil.callAndWait(() -> model().getTextBuffer().getInternalPosition(position)); }

    @Test void rapidModeSwitchRestoresActualVisibleParagraphAndPixelOffset() throws Exception {
        open(IntStream.range(0, 250).mapToObj(i -> "Visible paragraph " + i)
                .collect(java.util.stream.Collectors.joining("\n\n")));
        FxTestUtil.runAndWait(() -> {
            window.getEditorRoot().setFocusTraversable(true); window.getEditorRoot().requestFocus();
            ((javafx.scene.control.ScrollBar) window.getEditorControl().lookup(".scroll-bar:vertical")).setValue(.55);
        });
        GuiTestSupport.pulses();
        double[] before = FxTestUtil.callAndWait(this::visibleAnchor);
        FxTestUtil.runAndWait(() -> {
            window.getCommandSurface().setMode(UiMode.RIBBON);
            window.getCommandSurface().setMode(UiMode.CLASSIC);
            window.getCommandSurface().setMode(UiMode.RIBBON);
        });
        GuiTestSupport.pulses();
        double[] after = FxTestUtil.callAndWait(this::visibleAnchor);
        assertEquals(before[0], after[0], "The actual first visible paragraph must be preserved");
        assertEquals(before[1], after[1], 1.5, "The paragraph must retain its pixel offset within the viewport");
    }
    private double[] visibleAnchor() {
        var flow = (javafx.scene.control.skin.VirtualFlow<?>) window.getEditorControl().lookup(".virtual-flow");
        var first = flow.getFirstVisibleCell();
        return new double[] {first.getIndex(), first.localToScene(first.getBoundsInLocal()).getMinY()
                - flow.localToScene(flow.getBoundsInLocal()).getMinY()};
    }

    @Test void ribbonControlsHaveDescriptionsIconsAndAccessibleNames() throws Exception {
        open("Target text"); go(3);
        ButtonBase upper = showCommand(command("GROSSBUCHSTABEN"));
        FxTestUtil.runAndWait(() -> {
            assertNotNull(upper.getGraphic()); assertNotNull(upper.getTooltip());
            assertFalse(upper.getTooltip().getText().isBlank());
            assertNotNull(upper.getAccessibleText()); assertFalse(upper.getAccessibleText().isBlank());
        });
    }

    @Test void narrowRibbonRetainsEveryCommandAndUsableControls() throws Exception {
        open("Target text"); go(3);
        String id = command("GROSSBUCHSTABEN"); showCommand(id);
        FxTestUtil.runAndWait(() -> window.setWidth(500)); GuiTestSupport.pulses();
        assertEquals(FxTestUtil.callAndWait(() -> window.getCommandRegistry().ids()),
                FxTestUtil.callAndWait(() -> window.getCommandSurface().getRibbon().commandIds()));
        click(showCommand(id)); assertTrue(text().contains("TARGET TEXT"));
    }

    @Test void toggleMouseActivationChangesSharedStateExactlyOnceAndCreatesNoUndoStep() throws Exception {
        open("Target text"); go(4);
        int history = FxTestUtil.callAndWait(() -> model().getUndoStackSize());
        ButtonBase control = showCommand("view.typewriter");
        assertInstanceOf(javafx.scene.control.ToggleButton.class, control);
        boolean original = FxTestUtil.callAndWait(() -> window.getCommandRegistry().get("view.typewriter").isSelected());
        click(control);
        FxTestUtil.runAndWait(() -> {
            assertEquals(!original, window.getCommandRegistry().get("view.typewriter").isSelected());
            assertEquals(!original, ((javafx.scene.control.ToggleButton) control).isSelected());
            assertEquals(history, model().getUndoStackSize());
        });
        click(control);
        FxTestUtil.runAndWait(() -> assertEquals(original, window.getCommandRegistry().get("view.typewriter").isSelected()));
        assertEquals("Target text", text());
    }

    @Test void nativeF10EntersRibbonAndEscapeReturnsToWritingPosition() throws Exception {
        open("Target text"); go(4); showCommand(command("GROSSBUCHSTABEN"));
        FxTestUtil.runAndWait(() -> window.getEditorControl().requestFocus());
        GuiTestSupport.key(KeyCode.F10);
        FxTestUtil.runAndWait(() -> {
            Node focus = window.getScene().getFocusOwner();
            while (focus != null && focus != window.getCommandSurface().getRibbon()) focus = focus.getParent();
            assertNotNull(focus, "F10 must provide keyboard access to the Ribbon");
        });
        GuiTestSupport.key(KeyCode.ESCAPE);
        assertTrue(FxTestUtil.callAndWait(() -> window.getEditorControl().isFocused()));
        GuiTestSupport.key(KeyCode.X); assertEquals("Targxet text", text());
    }

    @Test void closingControllerUnsubscribesAndRemovesKeyboardFilter() throws Exception {
        open("Target text"); go(4);
        FxTestUtil.runAndWait(() -> window.getCommandSurface().setMode(UiMode.RIBBON));
        GuiTestSupport.pulses();
        int before = FxTestUtil.callAndWait(UiModeService::subscriberCount);
        FxTestUtil.runAndWait(() -> {
            window.getCommandSurface().close(); window.getCommandSurface().close();
            window.getEditorControl().requestFocus();
        });
        assertEquals(before - 1, FxTestUtil.callAndWait(UiModeService::subscriberCount));
        GuiTestSupport.key(KeyCode.F10);
        assertTrue(FxTestUtil.callAndWait(() -> window.getEditorControl().isFocused()), "A disposed controller must no longer handle keyboard events");
    }

    @Test void modeChoiceAndRadioStateStaySynchronizedAcrossWindows() throws Exception {
        EditorWindow second = FxTestUtil.callAndWait(() -> {
            EditorWindow created = new EditorWindow(); additionalWindows.add(created); created.show(); return created;
        });
        FxTestUtil.runAndWait(() -> window.getCommandSurface().setMode(UiMode.RIBBON));
        GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> {
            for (EditorWindow editor : List.of(window, second)) {
                assertEquals(UiMode.RIBBON, editor.getCommandSurface().getMode());
                assertTrue(editor.getCommandRegistry().get("ui.mode.ribbon").isSelected());
                assertFalse(editor.getCommandRegistry().get("ui.mode.classic").isSelected());
            }
            second.getCommandSurface().setMode(UiMode.CLASSIC);
            assertEquals(UiMode.CLASSIC, window.getCommandSurface().getMode());
            assertTrue(window.getCommandRegistry().get("ui.mode.classic").isSelected());
            assertFalse(window.getCommandRegistry().get("ui.mode.ribbon").isSelected());
        });
    }

    @Test void readonlyStateUpdatesAndActionsStayBoundToTheirEditor() throws Exception {
        open("first document"); go(3);
        ButtonBase firstAction = showCommand(command("GROSSBUCHSTABEN"));
        EditorWindow second = FxTestUtil.callAndWait(() -> {
            EditorWindow created = new EditorWindow(); additionalWindows.add(created);
            created.getEditor().setMarkdown("second document"); created.show(); return created;
        });
        GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> { window.getEditorControl().setEditable(false); window.getCommandRegistry().refresh(); });
        GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> assertTrue(firstAction.isDisabled()));
        assertEquals("first document", text());
        assertEquals("second document", FxTestUtil.callAndWait(() -> second.getEditor().getDocumentSnapshot().getText()));
        FxTestUtil.runAndWait(() -> { window.getEditorControl().setEditable(true); window.getCommandRegistry().refresh(); });
        GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> assertFalse(firstAction.isDisabled()));
    }
}
