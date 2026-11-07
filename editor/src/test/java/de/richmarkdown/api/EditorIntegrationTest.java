package de.richmarkdown.api;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.Selection;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.GuiTestSupport;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.settings.ApplicationSettings;
import de.richmarkdown.ui.EditorWindow;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Shown-stage tests exercise the public API, actual command routing, selection and ownership. */
class EditorIntegrationTest {
    private Stage host;
    private final List<EditorPane> panes = new ArrayList<>();
    private EditorMenus menus;
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @BeforeEach void setup() {
        FxTestUtil.runAndWait(() -> {
            I18n.setSettingsStore(ApplicationSettings.inMemory()); I18n.setCurrentLanguage("en");
            host = new Stage(); host.setScene(new Scene(new BorderPane(), 1100, 750)); host.show();
        });
    }
    @AfterEach void cleanup() {
        FxTestUtil.runAndWait(() -> {
            if (menus != null) menus.close();
            for (Window window : List.copyOf(Window.getWindows())) if (window != host) window.hide();
            panes.forEach(EditorPane::close); host.hide();
        });
    }
    private EditorPane pane(EditorPane.Presentation mode) {
        EditorPane pane = new EditorPane(mode); panes.add(pane); return pane;
    }
    private EditorPane mount(EditorPane.Presentation mode, String markdown) throws Exception {
        EditorPane pane = FxTestUtil.callAndWait(() -> {
            EditorPane result = pane(mode); host.getScene().setRoot(result);
            result.getEditor().setMarkdown(markdown); return result;
        });
        GuiTestSupport.pulses(); return pane;
    }
    private void select(EditorPane pane, int start, int end) throws Exception {
        FxTestUtil.runAndWait(() -> {
            var model = ((RichTextAreaSkin) pane.getEditor().getControl().getSkin()).getViewModel();
            model.setCaretPosition(end); model.setSelection(new Selection(start, end));
            pane.getEditor().getControl().requestFocus();
        }); GuiTestSupport.pulses();
    }
    private static MenuItem item(MenuBar bar, String id) {
        return bar.getMenus().stream().map(menu -> find(menu, id)).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
    }
    private static MenuItem find(MenuItem item, String id) {
        if (id.equals(item.getId())) return item;
        if (item instanceof Menu menu) for (MenuItem child : menu.getItems()) { MenuItem found = find(child, id); if (found != null) return found; }
        return null;
    }

    @ParameterizedTest @EnumSource(EditorPane.Presentation.class)
    void everyPresentationRendersAndPreservesDocumentHistoryAcrossSwitches(EditorPane.Presentation mode) throws Exception {
        EditorPane pane = mount(mode, "alpha beta"); select(pane, 0, 5);
        FxTestUtil.runAndWait(() -> assertTrue(pane.getCommands().execute("menu.bold")));
        GuiTestSupport.pulses();
        String bold = FxTestUtil.callAndWait(() -> pane.getEditor().getMarkdownSource()); assertTrue(bold.contains("**alpha**"));
        for (var presentation : EditorPane.Presentation.values()) {
            FxTestUtil.runAndWait(() -> pane.setPresentation(presentation)); GuiTestSupport.pulses();
            FxTestUtil.runAndWait(() -> {
                assertEquals(presentation, pane.getPresentation());
                if (presentation != EditorPane.Presentation.EDITOR_ONLY)
                    assertNotNull(pane.lookup("#command-favorites"), "Quick access survives presentation changes");
                assertEquals(bold, pane.getEditor().getMarkdownSource());
                assertEquals(new Selection(0, 5), pane.getEditor().getControl().getSelection());
                assertTrue(pane.getEditor().getControl().getWidth() > 100);
                assertTrue(pane.snapshot(null, null).getWidth() > 100);
            });
        }
        FxTestUtil.runAndWait(() -> assertTrue(pane.getCommands().execute("menu.undo"))); GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> assertEquals("alpha beta", pane.getEditor().getMarkdownSource().strip()));
    }
    @Test void hostMenusFollowActiveDocumentAndPreserveHostItems() throws Exception {
        EditorPane left = paneOnFx(), right = paneOnFx();
        MenuBar bar = new MenuBar();
        FxTestUtil.runAndWait(() -> {
            left.getEditor().setMarkdown("left"); right.getEditor().setMarkdown("right");
            bar.getMenus().add(new Menu("Host"));
            host.getScene().setRoot(new BorderPane(new HBox(left, right), bar, null, null, null));
            menus = new EditorMenus(bar); menus.setActiveEditor(left);
            assertNotSame(item(left.menuSource(), "menu.bold"), item(bar, "menu.bold"));
            assertNotSame(item(left.menuSource(), "menu.bold").getGraphic(), item(bar, "menu.bold").getGraphic());
        }); GuiTestSupport.pulses(); select(left, 0, 4);
        FxTestUtil.runAndWait(() -> item(bar, "menu.bold").fire()); GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> {
            assertTrue(left.getEditor().getMarkdownSource().contains("**left**"));
            assertEquals("right", right.getEditor().getMarkdownSource().strip());
            menus.setActiveEditor(right);
        }); select(right, 0, 5);
        FxTestUtil.runAndWait(() -> item(bar, "menu.italic").fire()); GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> {
            assertTrue(right.getEditor().getMarkdownSource().contains("*right*"));
            assertTrue(left.getEditor().getMarkdownSource().contains("**left**"));
            menus.setActiveEditor(null); assertEquals(1, bar.getMenus().size());
            menus.close(); menus.close();
            assertThrows(IllegalStateException.class, () -> menus.setActiveEditor(left));
        });
    }
    private EditorPane paneOnFx() { return FxTestUtil.callAndWait(() -> pane(EditorPane.Presentation.TOOLBAR)); }

    @Test void embeddedModesAreIndependentAndWindowCommandsRequireHostPolicy() throws Exception {
        EditorPane left = mount(EditorPane.Presentation.CLASSIC, "hello"), right = paneOnFx();
        FxTestUtil.runAndWait(() -> {
            left.setPresentation(EditorPane.Presentation.RIBBON);
            assertEquals(EditorPane.Presentation.TOOLBAR, right.getPresentation());
            assertFalse(left.getCommands().execute("menu.quit")); assertTrue(host.isShowing());
            AtomicInteger calls = new AtomicInteger();
            left.getCommands().setHandler("menu.quit", calls::incrementAndGet);
            left.getCommands().setAllowed("menu.quit", true);
            assertTrue(left.getCommands().execute("menu.quit")); assertEquals(1, calls.get());
            left.getCommands().setAllowed("menu.quit", false);
            assertFalse(left.getCommands().execute("menu.quit")); assertEquals(1, calls.get());
            assertThrows(IllegalArgumentException.class, () -> left.getCommands().setHandler("unknown", () -> { }));
            assertThrows(IllegalArgumentException.class, () -> left.getCommands().execute("unknown"));
        });
    }
    @Test void hostCallbacksReportErrorsAndToolbarUsesTheSameDispatcher() throws Exception {
        EditorPane pane = mount(EditorPane.Presentation.TOOLBAR, "hello");
        FxTestUtil.runAndWait(() -> {
            AtomicInteger calls = new AtomicInteger(); var commands = pane.getCommands();
            commands.setHandler("menu.save", calls::incrementAndGet);
            assertTrue(commands.execute("menu.save")); assertEquals(1, calls.get());
            var save = pane.getToolBar().getItems().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getTooltip() != null && button.getTooltip().getText().equals(I18n.text("menu.save"))).findFirst().orElseThrow();
            save.fire(); assertEquals(2, calls.get(), "Toolbar and menus use the host save handler");
            AtomicReference<RuntimeException> failure = new AtomicReference<>(); commands.setErrorHandler(failure::set);
            commands.setHandler("menu.save", () -> { throw new IllegalStateException("disk unavailable"); });
            assertFalse(commands.execute("menu.save")); assertEquals("disk unavailable", failure.get().getMessage());
            commands.setHandler("menu.save", null); commands.setAllowed("menu.save", false);
            assertFalse(commands.execute("menu.save"));
        });
    }
    @Test void readOnlyDisablesHostFormattingAndOwnedFindDialogUsesHostWindow() throws Exception {
        EditorPane pane = mount(EditorPane.Presentation.CLASSIC, "hello"); select(pane, 0, 5);
        FxTestUtil.runAndWait(() -> {
            pane.setOutlineVisible(false); pane.setLineNumbersVisible(false); pane.setStatusVisible(false);
            pane.setReadOnly(true); assertTrue(pane.isReadOnly());
            assertFalse(pane.getCommands().execute("menu.bold"));
            assertTrue(pane.getCommands().execute("menu.findReplace"));
            assertTrue(Window.getWindows().stream().anyMatch(window -> window != host && window instanceof Stage stage && stage.getOwner() == host));
        });
    }
    @Test void lifecycleIsIdempotentDoesNotCloseHostAndRejectsUseAfterDisposal() throws Exception {
        EditorPane pane = mount(EditorPane.Presentation.EDITOR_ONLY, "hello");
        FxTestUtil.runAndWait(() -> {
            pane.setConfirmationHandler((title, message) -> false);
            // Force a genuine user edit; loading a document itself is not a dirty change.
            pane.getEditor().getControl().getActionFactory().insertText("!").execute(new javafx.event.ActionEvent());
        }); GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> {
            assertFalse(pane.mayClose()); pane.setConfirmationHandler((title, message) -> true); assertTrue(pane.mayClose());
            var commands = pane.getCommands(); pane.close(); pane.close();
            assertTrue(pane.isClosed()); assertTrue(host.isShowing()); assertFalse(commands.execute("menu.bold"));
            assertThrows(IllegalStateException.class, pane::getEditor);
            assertThrows(IllegalStateException.class, () -> pane.setPresentation(EditorPane.Presentation.CLASSIC));
        });
        assertThrows(IllegalStateException.class, pane::getEditor);
        assertThrows(IllegalStateException.class, pane::close);
        assertThrows(IllegalStateException.class, EditorPane::new);
    }
    @Test void cleanupContinuesAfterFailureAndIsNotRepeated() {
        FxTestUtil.runAndWait(() -> {
            EditorWindow window = new EditorWindow(); AtomicInteger calls = new AtomicInteger();
            window.addFeatureDisposer(() -> { throw new IllegalStateException("first"); });
            window.addFeatureDisposer(calls::incrementAndGet);
            window.addFeatureDisposer(() -> { throw new IllegalArgumentException("second"); });
            var failure = assertThrows(IllegalStateException.class, window::disposeFeatures);
            assertEquals(1, calls.get()); assertEquals(1, failure.getSuppressed().length);
            assertDoesNotThrow(window::disposeFeatures);
        });
    }

    @Test void defaultPresentationAndToolbarActionsUseHostHandlersAndReportCommandFailures() throws Exception {
        FxTestUtil.runAndWait(() -> {
            EditorPane pane = new EditorPane(); panes.add(pane); host.getScene().setRoot(pane);
            assertEquals(EditorPane.Presentation.CLASSIC, pane.getPresentation());
        }); GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> {
            EditorPane pane = panes.getFirst(); var commands = pane.getCommands();
            assertTrue(commands.execute("ui.mode.ribbon")); assertEquals(EditorPane.Presentation.RIBBON, pane.getPresentation());
            assertTrue(commands.execute("ui.mode.classic")); assertEquals(EditorPane.Presentation.CLASSIC, pane.getPresentation());
            for (String id : List.of("menu.new", "menu.open")) {
                AtomicInteger calls = new AtomicInteger(); commands.setHandler(id, calls::incrementAndGet);
                var button = pane.getToolBar().getItems().stream().filter(Button.class::isInstance)
                        .map(Button.class::cast).filter(value -> value.getTooltip() != null &&
                                value.getTooltip().getText().equals(I18n.text(id))).findFirst().orElseThrow();
                button.fire(); assertEquals(1, calls.get(), id);
            }
            AtomicReference<String> error = new AtomicReference<>(); pane.setErrorHandler(error::set);
            commands.setHandler("menu.save", () -> { throw new IllegalStateException("host failure"); });
            assertFalse(commands.execute("menu.save")); assertEquals("host failure", error.get());
            pane.setErrorHandler(null);
        });
    }

    @Test void largeDocumentSelectionAndViewportStayAtTheEditedParagraph() throws Exception {
        EditorPane pane = mount(EditorPane.Presentation.CLASSIC,
                java.util.stream.IntStream.range(0, 220).mapToObj(index -> "Paragraph " + index + " with ordinary text.\n\n").collect(java.util.stream.Collectors.joining()));
        int position = FxTestUtil.callAndWait(() -> pane.getEditor().getDocumentSnapshot().getText().indexOf("Paragraph 175"));
        select(pane, position, position + 13);
        FxTestUtil.runAndWait(() -> pane.getEditor().revealPosition(position)); GuiTestSupport.pulses();
        double before = FxTestUtil.callAndWait(() -> {
            var flow = (javafx.scene.control.skin.VirtualFlow<?>) pane.getEditor().getControl().lookup(".virtual-flow");
            return flow.getFirstVisibleCell().getIndex();
        });
        assertTrue(before > 100, "The test must actually edit far below the document start");
        FxTestUtil.runAndWait(() -> assertTrue(pane.getCommands().execute("edit.uppercase"))); GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> {
            assertTrue(pane.getEditor().getDocumentSnapshot().getText().contains("PARAGRAPH 175"));
            var flow = (javafx.scene.control.skin.VirtualFlow<?>) pane.getEditor().getControl().lookup(".virtual-flow");
            assertEquals(before, flow.getFirstVisibleCell().getIndex(), 1);
            assertEquals(new Selection(position, position + 13), pane.getEditor().getControl().getSelection());
        });
    }

    @Test void invalidPresentationAndShellErrorsAreReportedToTheHost() throws Exception {
        EditorPane pane = mount(EditorPane.Presentation.EDITOR_ONLY, "hello");
        FxTestUtil.runAndWait(() -> {
            assertThrows(NullPointerException.class, () -> pane.setPresentation(null));
            AtomicReference<String> error = new AtomicReference<>(); pane.setErrorHandler(error::set);
            // No associated file: this must report the validation error without opening a FileChooser.
            pane.getCommands().execute("menu.file.compareWith");
            assertNotNull(error.get()); assertFalse(error.get().isBlank());
            pane.setErrorHandler(null);
            MenuBar bar = new MenuBar(); menus = new EditorMenus(bar); pane.close();
            assertThrows(IllegalStateException.class, () -> menus.setActiveEditor(pane));
        });
    }
}
