package de.richmarkdown.ui.dialogs;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.features.printing.PrintOptions;
import de.richmarkdown.features.tables.*;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.markdown.*;
import de.richmarkdown.settings.ApplicationSettings;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextAlignment;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class DialogWorkflowTest {
    @TempDir Path directory;
    MarkdownEditor editor;
    Stage stage;
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @BeforeEach void settings() { I18n.setSettingsStore(ApplicationSettings.inMemory()); }
    @AfterEach void cleanup() {
        FxTestUtil.runAndWait(() -> { List.copyOf(Window.getWindows()).forEach(Window::hide); });
        I18n.resetSettingsStore();
    }
    void pulses() { for (int i = 0; i < 4; i++) FxTestUtil.flushFxEvents(); }
    void open(String markdown) {
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor(); stage = new Stage(); stage.setScene(new Scene(new StackPane(editor.getControl()), 700, 500));
            stage.show(); editor.setMarkdown(markdown);
        }); pulses();
    }
    void modal(Runnable open, Consumer<DialogPane> interact) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        FxTestUtil.runAndWait(() -> {
            Platform.runLater(() -> {
                DialogPane pane = Window.getWindows().stream().filter(w -> w.isShowing() && w.getScene().getRoot() instanceof DialogPane)
                        .map(w -> (DialogPane) w.getScene().getRoot()).reduce((a, b) -> b).orElseThrow();
                try { interact.accept(pane); }
                catch (Throwable error) { failure.set(error); }
                finally { if (pane.getScene() != null && pane.getScene().getWindow() != null && pane.getScene().getWindow().isShowing()) ((Button) pane.lookupButton(DialogButtons.CANCEL)).fire(); }
            });
            open.run();
        }); pulses();
        if (failure.get() != null) throw new AssertionError("Dialog interaction", failure.get());
    }
    void undo() { FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().undo().execute(new javafx.event.ActionEvent())); pulses(); }

    @Test void imageInsertionHasMetadataAndOneUndoAndCanBeEdited() throws Exception {
        Path image = directory.resolve("image.png");
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", image.toFile());
        open("Before"); FxTestUtil.runAndWait(() -> editor.goToPosition(6));
        modal(() -> InsertionDialogs.image(editor, stage), pane -> {
            ((TextField) pane.lookup("#image-path")).setText(image.toUri().toASCIIString());
            ((TextField) pane.lookup("#image-alt")).setText("Diagram [A]");
            ((TextField) pane.lookup("#image-width")).setText("200");
            ((Button) pane.lookupButton(DialogButtons.OK)).fire();
        });
        String markdown = FxTestUtil.callAndWait(editor::getMarkdownSource);
        assertTrue(markdown.contains("alt=\"Diagram [A]\""), markdown); assertTrue(markdown.contains("width=\"200px\""), markdown);
        int position = FxTestUtil.callAndWait(() -> editor.getDocumentSnapshot().getDecorations().stream().filter(run -> run.getDecoration() instanceof com.gluonhq.richtextarea.model.ImageDecoration).findFirst().orElseThrow().getStart());
        assertTrue(position >= 0); FxTestUtil.runAndWait(() -> editor.goToPosition(position));
        modal(() -> InsertionDialogs.image(editor, stage), pane -> {
            assertEquals("Diagram [A]", ((TextField) pane.lookup("#image-alt")).getText());
            assertEquals("200", ((TextField) pane.lookup("#image-width")).getText());
            ((TextField) pane.lookup("#image-alt")).setText("Revised"); ((Button) pane.lookupButton(DialogButtons.OK)).fire();
        });
        assertTrue(FxTestUtil.callAndWait(editor::getMarkdownSource).contains("Revised"));
        undo(); assertEquals(markdown, FxTestUtil.callAndWait(editor::getMarkdownSource));
        undo(); assertEquals("Before", FxTestUtil.callAndWait(editor::getMarkdownSource));
    }
    @Test void linkWithoutSelectionCreatesVisibleLabelAndOneUndo() {
        open("Before "); FxTestUtil.runAndWait(() -> editor.goToPosition(editor.getDocumentSnapshot().getText().length()));
        String before = FxTestUtil.callAndWait(editor::getMarkdownSource);
        modal(() -> InsertionDialogs.link(editor, stage), pane -> {
            ((TextField) pane.lookup("#link-text")).setText("Visible link"); ((TextField) pane.lookup("#link-url")).setText("https://example.org");
            ((Button) pane.lookupButton(DialogButtons.OK)).fire();
        });
        assertTrue(FxTestUtil.callAndWait(editor::getMarkdownSource).contains("[Visible link](https://example.org)"));
        undo(); assertEquals(before, FxTestUtil.callAndWait(editor::getMarkdownSource));
    }
    @Test void editLinkIncludesAllFormattedRunsAndRemoveKeepsFormatting() {
        open("[one **two** three](https://example.org)");
        FxTestUtil.runAndWait(() -> editor.goToPosition(11));
        modal(() -> InsertionDialogs.link(editor, stage), pane -> {
            assertEquals("one two three", ((TextField) pane.lookup("#link-text")).getText());
            pane.lookupAll(".button").stream().map(Button.class::cast).filter(b -> b.getText().equals(WorkflowDialogs.text("removeLink"))).findFirst().orElseThrow().fire();
        });
        String markdown = FxTestUtil.callAndWait(editor::getMarkdownSource);
        assertFalse(markdown.contains("https:")); assertTrue(markdown.contains("**two**"), markdown);
        undo(); assertTrue(FxTestUtil.callAndWait(editor::getMarkdownSource).contains("https://example.org"));
    }
    @Test void codeEditingRestoresLanguageAndSingleUndo() {
        open("```java\nreturn 1;\n```"); FxTestUtil.runAndWait(() -> editor.goToPosition(4));
        String before = FxTestUtil.callAndWait(editor::getMarkdownSource);
        modal(() -> InsertionDialogs.code(editor, stage), pane -> {
            var language = (ComboBox<?>) pane.lookup("#code-language"); assertEquals("java", language.getEditor().getText());
            ((TextArea) pane.lookup("#code-content")).setText("return 2;"); ((Button) pane.lookupButton(DialogButtons.OK)).fire();
        });
        assertTrue(FxTestUtil.callAndWait(editor::getMarkdownSource).contains("```java\nreturn 2;"));
        undo(); assertEquals(before, FxTestUtil.callAndWait(editor::getMarkdownSource));
    }
    @Test void cancelledInsertionLeavesDocumentUntouched() {
        open("Original"); String before = FxTestUtil.callAndWait(editor::getMarkdownSource);
        modal(() -> InsertionDialogs.link(editor, stage), pane -> {
            ((TextField) pane.lookup("#link-url")).setText("https://example.org"); ((Button) pane.lookupButton(DialogButtons.CANCEL)).fire();
        });
        assertEquals(before, FxTestUtil.callAndWait(editor::getMarkdownSource));
    }
    @Test void tableHeadersAndAlignmentReachInsertionDataAndDelimitedImportKeepsData() {
        FxTestUtil.runAndWait(() -> {
            TableDialog dialog = new TableDialog(); dialog.getDialogPane().applyCss();
            var content = dialog.getDialogPane().getContent();
            var scroll = (ScrollPane) content.lookup(".scroll-pane");
            ((TextField) scroll.getContent().lookup("#table-heading-0")).setText("Price");
            ((ComboBox<TextAlignment>) scroll.getContent().lookup("#table-alignment-0")).setValue(TextAlignment.RIGHT);
            var table = dialog.getTableData(); assertEquals("Price", table.rows().getFirst().getFirst().text());
            assertEquals(TextAlignment.RIGHT, table.rows().get(1).getFirst().alignment());
            DelimitedTableDialog csv = new DelimitedTableDialog(null, "A;B\n1;2", null);
            assertEquals(2, csv.getResultConverter().call(DialogButtons.OK).width());
            ((CheckBox) csv.getDialogPane().lookup(".check-box")).setSelected(false);
            assertEquals(3, csv.getResultConverter().call(DialogButtons.OK).rows().size());
            assertEquals("A", csv.getResultConverter().call(DialogButtons.OK).rows().get(1).getFirst().text());
            assertNull(csv.getResultConverter().call(DialogButtons.CANCEL));
        });
    }
    @Test void combinedPrintContainsDeviceAndLayoutAndDisablesWithoutPrinter() {
        FxTestUtil.runAndWait(() -> {
            PrintDialog dialog = new PrintDialog(List.of(), null, PrintOptions.defaults(), false, (choice, owner) -> fail("No printer"));
            assertEquals(2, ((TabPane) dialog.getDialogPane().lookup(".tab-pane")).getTabs().size());
            assertTrue(dialog.getDialogPane().lookupButton(DialogButtons.PRINT).isDisabled());
            assertNull(dialog.getResultConverter().call(DialogButtons.CANCEL));
        });
    }
    @Test void issueFiltersUseStableRuleIdsAndContext() {
        FxTestUtil.runAndWait(() -> {
            var issues = List.of(new MarkdownIssue("rule.a", MarkdownIssue.Severity.WARNING, "First", 0, 1, 1),
                    new MarkdownIssue("rule.b", MarkdownIssue.Severity.ERROR, "Second", 2, 1, 3));
            IssueDialog dialog = new IssueDialog(() -> issues, ignored -> { }); dialog.configureContext(() -> "abcdef");
            ((TextField) dialog.getDialogPane().lookup(".text-field")).setText("rule.b");
            assertEquals(List.of(issues.getLast()), dialog.getIssues()); dialog.getListView().getSelectionModel().selectFirst();
            assertEquals("abcdef", ((TextArea) dialog.getDialogPane().lookup(".text-area")).getText());
        });
    }
}
