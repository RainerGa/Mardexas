package de.richmarkdown.features.editing;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.Selection;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.GuiTestSupport;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real replacement command, its undo history and the rendered viewport. */
class EditingPositionTest {
    private MarkdownEditor editor;
    private Stage stage;
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @BeforeEach void setup() {
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor(); stage = new Stage();
            stage.setScene(new Scene(new StackPane(editor.getControl()), 750, 400)); stage.show();
        });
        flush();
    }
    @AfterEach void close() { FxTestUtil.runAndWait(stage::close); }
    private void flush() {
        try {
            GuiTestSupport.pulses();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("The shown editor must finish rendering before viewport assertions", interrupted);
        }
    }
    private void open(String markdown) { FxTestUtil.runAndWait(() -> editor.setMarkdown(markdown)); flush(); }
    private String text() { return FxTestUtil.callAndWait(() -> editor.getDocumentSnapshot().getText()); }
    private com.gluonhq.richtextarea.viewmodel.RichTextAreaViewModel model() {
        return ((RichTextAreaSkin) editor.getControl().getSkin()).getViewModel();
    }
    private void select(int start, int end, boolean backwards) {
        FxTestUtil.runAndWait(() -> {
            editor.goToPosition(model().getTextBuffer().getInternalPosition(backwards ? start : end));
            model().setSelection(model().getTextBuffer().getInternalSelection(new Selection(start, end)));
        }); flush();
    }
    private void assertContext(int caret, int start, int end) {
        FxTestUtil.runAndWait(() -> {
            assertEquals(caret, editor.getControl().getCaretPosition(), "Exported caret");
            var selection = model().getSelection();
            if (start == end) assertFalse(selection.isDefined());
            else {
                assertTrue(selection.isDefined());
                assertEquals(start, model().getTextBuffer().getText(0, selection.getStart()).length());
                assertEquals(end, model().getTextBuffer().getText(0, selection.getEnd()).length());
            }
        });
    }
    private void undo() { FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().undo().execute(new ActionEvent())); flush(); }
    private void redo() { FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().redo().execute(new ActionEvent())); flush(); }
    private double scroll() { return FxTestUtil.callAndWait(() -> ((ScrollBar) editor.getControl().lookup(".scroll-bar:vertical")).getValue()); }

    private void renderPulses() throws Exception {
        var completed = new java.util.concurrent.CountDownLatch(1);
        FxTestUtil.runAndWait(() -> new javafx.animation.AnimationTimer() {
            private int frames;
            @Override public void handle(long now) {
                if (++frames == 4) { stop(); completed.countDown(); }
            }
        }.start());
        assertTrue(completed.await(FxTestUtil.TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS));
        flush();
    }

    @ParameterizedTest @ValueSource(ints = {-1, 1})
    void paragraphMoveInLongDocumentKeepsCaretInViewportAfterLayout(int direction) throws Exception {
        FxTestUtil.runAndWait(() -> {
            stage.close();
            de.richmarkdown.i18n.I18n.setSettingsStore(de.richmarkdown.settings.ApplicationSettings.inMemory());
            var window = new de.richmarkdown.ui.EditorWindow();
            window.show(); stage = window; editor = window.getEditor();
        });
        flush();
        open("# Anfang\n\n" + "Ein Absatz vor dem Ziel.\n\n".repeat(150)
                + "Zielabsatz mit Schreibposition\n\n" + "Ein Absatz danach.\n\n".repeat(90));
        int start = text().indexOf("Zielabsatz");
        select(start + 7, start + 7, false); renderPulses();
        double before = scroll(); assertTrue(before > .1);
        var largestDeletion = new java.util.concurrent.atomic.AtomicInteger();
        FxTestUtil.runAndWait(() -> model().addChangeListener(event -> {
            if (event instanceof com.gluonhq.richtextarea.model.TextBuffer.DeleteEvent deleted)
                largestDeletion.accumulateAndGet(deleted.getLength(), Math::max);
        }));
        var menus = FxTestUtil.callAndWait(() -> (javafx.scene.control.MenuBar) stage.getScene().lookup(".menu-bar"));
        var edit = FxTestUtil.callAndWait(() -> menus.getMenus().get(1));
        var paragraphs = FxTestUtil.callAndWait(() -> (javafx.scene.control.Menu) edit.getItems().stream()
                .filter(javafx.scene.control.Menu.class::isInstance).findFirst().orElseThrow());
        FxTestUtil.runAndWait(() -> { edit.show(); paragraphs.show(); menus.requestFocus(); });
        renderPulses();
        FxTestUtil.runAndWait(() -> {
            paragraphs.getItems().get(direction < 0 ? 6 : 7).fire();
            paragraphs.hide(); edit.hide();
        });
        renderPulses();
        int destination = text().indexOf("Zielabsatz");
        assertContext(destination + 7, 0, 0);
        assertTrue(largestDeletion.get() < 100, "Only the neighboring paragraphs may be replaced, not the whole document");
        assertTrue(scroll() > .1, "Moving a paragraph must not scroll to the beginning");
        assertTrue(Math.abs(scroll() - before) < .05, "The viewport must follow the moved paragraph locally");
        assertCaretInViewport();
        undo(); renderPulses(); assertContext(start + 7, 0, 0); assertCaretInViewport();
        redo(); renderPulses(); assertContext(destination + 7, 0, 0); assertCaretInViewport();
    }

    @ParameterizedTest
    @CsvSource({"-1,false", "1,false", "-1,true", "1,true"})
    void localMovePreservesRichContentAndTrailingParagraph(int direction, boolean trailingNewline) throws Exception {
        String image = EditingPositionTest.class.getResource("/pic.png").toExternalForm();
        open("# Überschrift\n\n![Bild](" + image + ")\n\n**Ziel😀text**\n\n[Danach](https://example.org)"
                + (trailingNewline ? "\n\n" : ""));
        String originalMarkdown = FxTestUtil.callAndWait(editor::getMarkdownSource);
        int start = text().indexOf("Ziel"); select(start + 6, start + 6, false);
        var source = FxTestUtil.callAndWait(editor::getDocumentSnapshot);
        var expected = DocumentEdits.moveParagraph(source, start + 6, direction);
        FxTestUtil.runAndWait(() -> new EditingCommands(editor).moveParagraph(direction)); renderPulses();
        assertEquals(expected.getText(), text());
        assertEquals(new de.richmarkdown.markdown.MarkdownWriter().write(expected), FxTestUtil.callAndWait(editor::getMarkdownSource));
        int destination = text().indexOf("Ziel"); assertContext(destination + 6, 0, 0);
        undo(); renderPulses(); assertEquals(originalMarkdown, FxTestUtil.callAndWait(editor::getMarkdownSource));
        assertContext(start + 6, 0, 0);
        redo(); renderPulses(); assertContext(destination + 6, 0, 0);
    }

    private void assertCaretInViewport() {
        FxTestUtil.runAndWait(() -> {
            var viewport = editor.getControl().lookup(".virtual-flow");
            var bounds = viewport.localToScene(viewport.getBoundsInLocal());
            var rendered = editor.getControl().lookupAll(".caret").stream()
                    .filter(javafx.scene.shape.Path.class::isInstance).map(javafx.scene.shape.Path.class::cast)
                    .filter(path -> !path.getElements().isEmpty()).toList();
            assertEquals(1, rendered.size(), "Exactly the active caret must be rendered");
            assertTrue(rendered.stream().allMatch(node -> {
                var caret = node.localToScene(node.getBoundsInLocal());
                return caret.getHeight() > 0 && caret.getMinY() >= bounds.getMinY()
                        && caret.getMaxY() <= bounds.getMaxY();
            }), "The rendered caret must remain within the viewport");
            assertTrue(editor.getControl().isFocused(), "Typing focus must return to the editor");
        });
    }

    @Test void returningFocusRevealsExistingCaretAfterCellsWereRebuilt() throws Exception {
        open("Absatz vor dem Ziel.\n\n".repeat(90) + "Zielabsatz\n\n" + "Danach.\n\n".repeat(30));
        int position = text().indexOf("Zielabsatz") + 4;
        select(position, position, false); renderPulses();
        FxTestUtil.runAndWait(() -> {
            stage.getScene().getRoot().setFocusTraversable(true);
            stage.getScene().getRoot().requestFocus();
            ((ScrollBar) editor.getControl().lookup(".scroll-bar:vertical")).setValue(0);
        });
        renderPulses(); assertEquals(0, scroll(), .000001);
        int history = FxTestUtil.callAndWait(() -> model().getUndoStackSize());
        FxTestUtil.runAndWait(() -> editor.getControl().requestFocus());
        renderPulses();
        assertContext(position, 0, 0); assertCaretInViewport();
        assertTrue(scroll() > .1);
        assertEquals(history, FxTestUtil.callAndWait(() -> model().getUndoStackSize()));
    }

    @Test void selectedParagraphCanMoveToDocumentEndWithoutTrailingNewline() throws Exception {
        open("Erster\n\nZiel\n\nLetzter");
        // Use consecutive rendered paragraphs to exercise the document boundary.
        FxTestUtil.runAndWait(() -> editor.replaceDocument(new com.gluonhq.richtextarea.model.Document("Erster\nZiel\nLetzter", 9)));
        flush();
        FxTestUtil.runAndWait(() -> new EditingCommands(editor).selectParagraph()); flush();
        FxTestUtil.runAndWait(() -> new EditingCommands(editor).moveParagraph(1)); renderPulses();
        assertEquals("Erster\nLetzter\nZiel", text());
        assertContext(text().length(), text().indexOf("Ziel"), text().length());
        undo(); renderPulses(); assertEquals("Erster\nZiel\nLetzter", text()); assertContext(12, 7, 12);
        redo(); renderPulses(); assertContext(text().length(), text().indexOf("Ziel"), text().length());
    }

    @Test void rangeReplacementRejectsInvalidPositionsWithoutChangingHistory() {
        open("Vorher 😀 danach");
        FxTestUtil.runAndWait(() -> {
            int history = model().getUndoStackSize();
            var replacement = new com.gluonhq.richtextarea.model.Document("neu", 4);
            assertThrows(IllegalArgumentException.class, () -> editor.replaceRange(-1, 2, replacement, Selection.UNDEFINED));
            assertThrows(IllegalArgumentException.class, () -> editor.replaceRange(3, 2, replacement, Selection.UNDEFINED));
            assertThrows(IllegalArgumentException.class, () -> editor.replaceRange(0, 99, replacement, Selection.UNDEFINED));
            assertThrows(IllegalArgumentException.class, () -> editor.replaceRange(0, 2, replacement, new Selection(0, 99)));
            editor.getControl().setEditable(false);
            assertThrows(IllegalStateException.class, () -> editor.replaceRange(0, 2, replacement, Selection.UNDEFINED));
            editor.getControl().setEditable(true);
            assertEquals(history, model().getUndoStackSize());
            assertEquals("Vorher 😀 danach", editor.getDocumentSnapshot().getText());
        });
    }

    @ParameterizedTest
    @CsvSource({"UPPER,STRASSE HALLO", "LOWER,straße hallo", "SENTENCE,Straße hallo"})
    void caseConversionRetainsSelectionAndViewportThroughUndoRedo(DocumentEdits.Transform operation, String expected) {
        open("Vorspann 😀\n\n" + "Eine weitere Zeile.\n\n".repeat(90) + "Straße HALLO\n\n" + "Nachspann\n\n".repeat(50));
        String original = text(); int start = original.indexOf("Straße HALLO"), end = start + "Straße HALLO".length();
        select(start, end, false); assertTrue(scroll() > .1);
        int paragraph = FxTestUtil.callAndWait(() -> model().getParagraphList().indexOf(
                model().getParagraphWithCaret().orElseThrow()));
        double before = paragraphY(paragraph);
        FxTestUtil.runAndWait(() -> new EditingCommands(editor).transform(operation)); flush();
        assertEquals(expected, text().substring(start, start + expected.length()));
        assertContext(start + expected.length(), start, start + expected.length());
        assertEquals(before, paragraphY(paragraph), 1d, "Formatting must retain the viewport within one pixel");
        undo(); assertEquals(original, text()); assertContext(end, start, end);
        assertEquals(before, paragraphY(paragraph), 1d, "Undo must retain the viewport within one pixel");
        redo(); assertContext(start + expected.length(), start, start + expected.length());
        assertEquals(before, paragraphY(paragraph), 1d, "Redo must retain the viewport within one pixel");
    }

    private double paragraphY(int paragraph) {
        // VirtualFlow refines its estimated total height as cells are rendered.
        // Check the resulting displacement in pixels, rather than requiring an
        // identical normalized ratio despite platform-dependent font metrics.
        return FxTestUtil.callAndWait(() -> editor.getControl().lookupAll(".list-cell").stream()
                .filter(javafx.scene.control.ListCell.class::isInstance)
                .map(javafx.scene.control.ListCell.class::cast)
                .filter(cell -> cell.getIndex() == paragraph)
                .mapToDouble(cell -> cell.localToScene(cell.getBoundsInLocal()).getMinY())
                .findFirst().orElseThrow(() -> new AssertionError("The edited paragraph must stay visible")));
    }

    @Test void backwardsSelectionKeepsItsActiveEndWhenUnicodeExpands() {
        open("😀 vorher\n\nStraße\n\nnachher"); int start = text().indexOf("Straße");
        select(start, start + 6, true);
        FxTestUtil.runAndWait(() -> new EditingCommands(editor).transform(DocumentEdits.Transform.UPPER)); flush();
        assertContext(start, start, start + 7);
        undo(); assertContext(start, start, start + 6);
        redo(); assertContext(start, start, start + 7);
    }

    @Test void paragraphCaseConversionMapsCaretAfterSharpSWithoutSelectingParagraph() {
        open("😀 vorher\n\nStraße weiter\n\nnachher"); int start = text().indexOf("Straße");
        select(start + 5, start + 5, false);
        FxTestUtil.runAndWait(() -> new EditingCommands(editor).transform(DocumentEdits.Transform.UPPER)); flush();
        assertContext(start + 6, 0, 0);
        undo(); assertContext(start + 5, 0, 0);
        redo(); assertContext(start + 6, 0, 0);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void clearingStylesAndLinksRetainsBackwardsSelection(int kind) {
        open("😀 vorher\n\n# [**Zieltext**](https://example.org)\n\nnachher");
        String original = FxTestUtil.callAndWait(editor::getMarkdownSource); int start = text().indexOf("Zieltext");
        select(start, start + 8, true);
        FxTestUtil.runAndWait(() -> new EditingCommands(editor).clearFormatting(kind == 1, kind == 2)); flush();
        assertContext(start, start, start + 8);
        undo(); assertEquals(original, FxTestUtil.callAndWait(editor::getMarkdownSource)); assertContext(start, start, start + 8);
        redo(); assertContext(start, start, start + 8);
    }

    @ParameterizedTest @CsvSource({"SORT_LINES,a|b|b|z", "UNIQUE_LINES,z|b|a"})
    void lineOperationsRetainWholeSelectionWithAdjustedLength(DocumentEdits.Transform operation, String encoded) {
        open("😀 vorher\n\n```\nz\nb\nb\na\n```\n\nnachher");
        int start = text().indexOf("z\n"), end = start + 7; String expected = encoded.replace('|', '\n');
        select(start, end, false);
        FxTestUtil.runAndWait(() -> new EditingCommands(editor).transform(operation)); flush();
        assertEquals(expected, text().substring(start, start + expected.length()));
        assertContext(start + expected.length(), start, start + expected.length());
        undo(); assertContext(end, start, end); redo(); assertContext(start + expected.length(), start, start + expected.length());
    }

    @ParameterizedTest @ValueSource(ints = {-1, 1})
    void paragraphMoveFollowsItsTextAndColumn(int direction) {
        open("😀 first\n\nmiddle\n\nlast"); int start = text().indexOf("middle");
        select(start + 3, start + 3, false);
        FxTestUtil.runAndWait(() -> new EditingCommands(editor).moveParagraph(direction)); flush();
        int destination = text().indexOf("middle"); assertNotEquals(start, destination);
        assertContext(destination + 3, 0, 0); undo(); assertContext(start + 3, 0, 0); redo(); assertContext(destination + 3, 0, 0);
    }

    @Test void duplicateMovesCaretToCopyAndSelectParagraphDoesNotLoseFocus() {
        open("😀 first\n\nmiddle"); int start = text().indexOf("middle"); select(start + 2, start + 2, false);
        FxTestUtil.runAndWait(() -> new EditingCommands(editor).duplicateParagraph()); flush();
        int copy = text().lastIndexOf("middle"); assertTrue(copy > start); assertContext(copy + 2, 0, 0);
        FxTestUtil.runAndWait(() -> new EditingCommands(editor).selectParagraph()); flush();
        assertContext(copy + 6, copy, copy + 6);
        assertTrue(FxTestUtil.callAndWait(() -> editor.getControl().isFocused()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void pasteReplacesSelectionAndPlacesCaretAfterInsertedText(boolean markdown) {
        open("😀 before\n\nreplace me\n\nafter"); int start = text().indexOf("replace me");
        select(start, start + 10, true);
        FxTestUtil.runAndWait(() -> new EditingCommands(editor).paste(markdown ? "**Neu😀**" : "Neu😀", markdown)); flush();
        assertContext(start + 5, 0, 0);
        undo(); assertContext(start, start, start + 10);
        redo(); assertContext(start + 5, 0, 0);
    }

    @ParameterizedTest
    @CsvSource({"'- Target',false", "'# Target',false", "'```java\nTarget\n```',false",
            "'| Target | Value |\n|---|---|\n| A | B |',false", "'- Target',true"})
    void inlinePasteKeepsSurroundingBlockAndUndo(String source, boolean markdown) {
        open(source);
        String original = FxTestUtil.callAndWait(editor::getMarkdownSource);
        int position = text().indexOf("Target") + 3;
        select(position, position, false);
        FxTestUtil.runAndWait(() -> new EditingCommands(editor).paste("X", markdown)); flush();
        assertEquals(original.replace("Target", "TarXget"), FxTestUtil.callAndWait(editor::getMarkdownSource));
        assertContext(position + 1, 0, 0);
        undo(); assertEquals(original, FxTestUtil.callAndWait(editor::getMarkdownSource)); assertContext(position, 0, 0);
    }

    @Test void unsafeTablePasteIsRejectedBeforeChangingTextOrHistory() {
        open("| Target | Value |\n|---|---|\n| A | B |");
        int position = text().indexOf("Target") + 2; select(position, position, false);
        FxTestUtil.runAndWait(() -> {
            var original = editor.getDocumentSnapshot(); int history = model().getUndoStackSize();
            assertThrows(IllegalStateException.class, () -> new EditingCommands(editor).paste("one\ntwo", false));
            assertThrows(IllegalStateException.class, () -> new EditingCommands(editor).paste("# Heading", true));
            assertEquals(original.getText(), editor.getDocumentSnapshot().getText());
            assertEquals(original.getDecorations(), editor.getDocumentSnapshot().getDecorations());
            assertEquals(history, model().getUndoStackSize());
        }); assertContext(position, 0, 0);
    }

    @Test void unchangedTextAndBoundaryMoveDoNotCreateUndoEntries() {
        open("UPPER"); select(2, 2, false);
        FxTestUtil.runAndWait(() -> {
            int count = model().getUndoStackSize();
            var commands = new EditingCommands(editor);
            commands.transform(DocumentEdits.Transform.UPPER); commands.moveParagraph(-1);
            assertEquals(count, model().getUndoStackSize());
        }); assertContext(2, 0, 0);
    }

    @Test void defaultReplacementHonorsExportedCaretAndValidatesSelectionBeforeEditing() {
        open("before 😀 text"); select(10, 14, true);
        FxTestUtil.runAndWait(() -> {
            var result = new com.gluonhq.richtextarea.model.Document("😀 result", 5);
            int count = model().getUndoStackSize();
            assertThrows(IllegalArgumentException.class, () -> editor.replaceDocument(result, new Selection(0, 99)));
            assertEquals(count, model().getUndoStackSize());
            assertEquals("before 😀 text", editor.getDocumentSnapshot().getText());
            editor.replaceDocument(result);
        }); flush();
        assertEquals("😀 result", text()); assertContext(5, 0, 0);
        undo(); assertEquals("before 😀 text", text()); assertContext(10, 10, 14);
        redo(); assertContext(5, 0, 0);
    }

    @Test void paragraphSelectionIncludingNewlineDoesNotDuplicateTheFollowingParagraph() {
        open("first\n\nmiddle\n\nlast"); int start = text().indexOf("middle");
        select(start + 2, start + 2, false);
        FxTestUtil.runAndWait(() -> new EditingCommands(editor).selectParagraph()); flush();
        assertContext(start + 7, start, start + 7);
        FxTestUtil.runAndWait(() -> new EditingCommands(editor).duplicateParagraph()); flush();
        assertTrue(text().contains("middle\nmiddle\n"));
        assertContext(start + 14, start + 7, start + 14);
        undo(); assertContext(start + 7, start, start + 7);
    }
}
