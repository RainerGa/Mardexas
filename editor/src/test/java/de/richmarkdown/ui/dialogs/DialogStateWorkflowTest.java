package de.richmarkdown.ui.dialogs;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.io.MarkdownLibraryStore;
import de.richmarkdown.settings.ApplicationSettings;
import de.richmarkdown.ui.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class DialogStateWorkflowTest {
    @TempDir Path directory;
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @BeforeEach void settings() { I18n.setSettingsStore(new ApplicationSettings(directory.resolve("config"))); }
    @AfterEach void cleanup() { FxTestUtil.runAndWait(() -> List.copyOf(Window.getWindows()).forEach(Window::hide)); I18n.resetSettingsStore(); }
    static DialogPane otherPane(DialogPane exclude) {
        return Window.getWindows().stream().filter(Window::isShowing).map(window -> window.getScene().getRoot())
                .filter(root -> root instanceof DialogPane && root != exclude).map(DialogPane.class::cast).findFirst().orElseThrow();
    }
    @Test void displayPreviewRollsBackOnCancelAndDoesNotPersist() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        FxTestUtil.runAndWait(() -> {
            EditorWindow window = new EditorWindow(); window.show();
            try {
                AccessibilityOptions options = new AccessibilityOptions(window);
                String original = window.getEditorRoot().getStyle();
                Platform.runLater(() -> {
                    DialogPane pane = otherPane(null);
                    try {
                        ((Spinner<?>) pane.lookup(".spinner")).getEditor().setText("22");
                        assertTrue(window.getEditorRoot().getStyle().contains("22px"));
                        ((CheckBox) pane.lookup(".check-box")).setSelected(true);
                        assertTrue(window.getEditorRoot().getStyleClass().contains("rme-dark"));
                    } catch (Throwable error) { failure.set(error); }
                    finally { ((Button) pane.lookupButton(DialogButtons.CANCEL)).fire(); }
                });
                options.show();
                assertEquals(original, window.getEditorRoot().getStyle()); assertNull(I18n.preference("access.size", null));
            } finally { window.disposeFeatures(); window.close(); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
    @Test void libraryCloseCanKeepDirtyDraftOrDiscardItWithoutSaving() throws Exception {
        var store = new MarkdownLibraryStore(directory, MarkdownLibraryStore.Kind.SNIPPET);
        store.save(null, "Saved", "saved", "original");
        var dialog = FxTestUtil.callAndWait(() -> {
            var created = new WritingLibraryDialog(null, store, MarkdownLibraryStore.Kind.SNIPPET, ""); created.show(); return created;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> FxTestUtil.callAndWait(() -> !dialog.getDialogPane().getContent().isDisabled()));
        FxTestUtil.runAndWait(() -> {
            ((ListView<?>) dialog.getDialogPane().lookup("#library-list")).getSelectionModel().selectFirst();
            ((TextArea) dialog.getDialogPane().lookup("#library-content")).setText("draft");
            Platform.runLater(() -> ((Button) otherPane(dialog.getDialogPane()).lookupButton(DialogButtons.KEEP)).fire());
            ((Button) dialog.getDialogPane().lookupButton(DialogButtons.CANCEL)).fire();
            assertTrue(dialog.isShowing()); assertEquals("draft", ((TextArea) dialog.getDialogPane().lookup("#library-content")).getText());
            Platform.runLater(() -> ((Button) otherPane(dialog.getDialogPane()).lookupButton(DialogButtons.DISCARD)).fire());
            ((Button) dialog.getDialogPane().lookupButton(DialogButtons.CANCEL)).fire();
            assertFalse(dialog.isShowing());
        });
        assertEquals("original", store.list().getFirst().content());
    }
    @Test void templateLoadingReportsOversizeFilesAndAcceptsMarkdownExtension() throws Exception {
        Files.writeString(directory.resolve("a.MARKDOWN"), "# Custom");
        Files.writeString(directory.resolve("huge.md"), "x".repeat(262_145));
        I18n.setPreference("templates.directory", directory.toString());
        var dialog = FxTestUtil.callAndWait(() -> { var created = new TemplateDialog(null); created.show(); return created; });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> FxTestUtil.callAndWait(dialog::isTemplatesLoaded));
        FxTestUtil.runAndWait(() -> {
            var pane = dialog.getDialogPane();
            var list = (ListView<?>) pane.lookup(".list-view");
            assertEquals(5, list.getItems().size(), () -> list.getItems() + " / " + pane.lookupAll(".label").stream()
                    .map(Label.class::cast).map(Label::getText).toList());
            list.getSelectionModel().selectLast(); assertEquals("# Custom", dialog.getResultConverter().call(DialogButtons.OK));
            assertTrue(pane.lookupAll(".label").stream().map(Label.class::cast).anyMatch(label -> label.getText().contains("huge.md")));
            ((TextField) pane.lookup(".text-field")).setText("no such template"); assertTrue(list.getItems().isEmpty());
            assertTrue(pane.lookupButton(DialogButtons.OK).isDisabled()); dialog.close();
        });
    }
    @Test void renderedMarkdownPreviewActuallyLoadsItsSnapshot() {
        FxTestUtil.runAndWait(() -> {
            var control = WorkflowDialogs.markdownPreview("**Visible** text");
            javafx.stage.Stage stage = new javafx.stage.Stage(); stage.setScene(new javafx.scene.Scene(new javafx.scene.layout.StackPane(control), 400, 250)); stage.show();
            control.applyCss(); control.layout();
            assertEquals("Visible text", control.getDocument().getText()); stage.close();
        });
    }
}
