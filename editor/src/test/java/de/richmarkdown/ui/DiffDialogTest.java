/*
 * Copyright (C) 2026 Rainer Garbotz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.richmarkdown.ui;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.i18n.I18n;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DiffDialogTest {
    @TempDir Path directory;
    private MarkdownEditor createdEditor;
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @AfterEach void closeWindows() {
        FxTestUtil.runAndWait(() -> List.copyOf(Window.getWindows()).forEach(Window::hide));
    }

    private static class FileWindow extends EditorWindow {
        File selected;
        int selections;
        @Override protected File chooseFileToOpen() { selections++; return selected; }
    }

    @Test void noOpenFileShowsUsefulErrorWithoutOpeningChooser() {
        FileWindow window = FxTestUtil.callAndWait(() -> { FileWindow w = new FileWindow(); w.show(); return w; });
        FxTestUtil.runAndWait(window::showDiffDialog);
        assertEquals(I18n.text("dialog.diff.noFile"), window.getLastError());
        assertEquals(0, window.selections);
    }

    @Test void cancellingChooserDoesNotModifyDocument() throws Exception {
        Path source = directory.resolve("original.md");
        Files.writeString(source, "original");
        FileWindow window = FxTestUtil.callAndWait(() -> { FileWindow w = new FileWindow(); w.show(); w.openFile(source.toFile()); return w; });
        FxTestUtil.flushFxEvents(); FxTestUtil.flushFxEvents();
        FxTestUtil.runAndWait(window::showDiffDialog);
        assertEquals(1, window.selections);
        assertNull(window.getLastError());
        assertEquals("original", FxTestUtil.callAndWait(window.getEditor()::getMarkdownSource));
    }

    @Test void sameFileCanCompareUnsavedEditorContentsAgainstSavedContents() throws Exception {
        Path source = directory.resolve("original.md");
        Files.writeString(source, "original");
        DiffDialog dialog = createDialog(source, source, "original unsaved", false, new AtomicReference<>());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> FxTestUtil.callAndWait(dialog::isReady));
        FxTestUtil.runAndWait(() -> {
            assertFalse(dialog.getComparisonView().getChanges().isEmpty());
            assertEquals("original", dialog.getComparisonView().getResultDocument().getText());
        });
    }

    @Test void dialogUsesMemorySnapshotAndAllDialogButtonsHaveTooltips() throws Exception {
        Path original = directory.resolve("original.md");
        Path revised = directory.resolve("revised.md");
        Files.writeString(original, "disk contents");
        Files.writeString(revised, "memory new contents");
        DiffDialog dialog = createDialog(original, revised, "memory contents", false, new AtomicReference<>());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> FxTestUtil.callAndWait(dialog::isReady));
        FxTestUtil.flushFxEvents(); FxTestUtil.flushFxEvents();
        FxTestUtil.runAndWait(() -> {
            var changes = dialog.getComparisonView().getChanges();
            assertEquals(1, changes.size());
            assertEquals("new ", changes.getFirst().text());
            assertEquals("memory new contents", dialog.getComparisonView().getResultDocument().getText());
            for (var type : dialog.getDialogPane().getButtonTypes()) {
                assertNotNull(((Button) dialog.getDialogPane().lookupButton(type)).getTooltip());
            }
            dialog.getDialogPane().applyCss();
            dialog.getDialogPane().layout();
            var image = dialog.getDialogPane().snapshot(null, null);
            var buffered = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < buffered.getHeight(); y++) for (int x = 0; x < buffered.getWidth(); x++) {
                buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
            try { javax.imageio.ImageIO.write(buffered, "png", Path.of("target/diff-dialog-preview.png").toFile()); }
            catch (java.io.IOException failure) { throw new RuntimeException(failure); }
        });
    }

    @Test void missingComparisonFileReportsFailureAndKeepsResultActionsDisabled() throws Exception {
        Path original = directory.resolve("original.md");
        Files.writeString(original, "original");
        AtomicReference<String> error = new AtomicReference<>();
        DiffDialog dialog = createDialog(original, directory.resolve("missing.md"), "original", false, error);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> error.get() != null);
        assertTrue(error.get().contains("missing.md"));
        FxTestUtil.runAndWait(() -> {
            assertFalse(dialog.isReady());
            assertTrue(dialog.getDialogPane().getButtonTypes().stream()
                    .filter(type -> type.getButtonData() == javafx.scene.control.ButtonBar.ButtonData.OTHER)
                    .allMatch(type -> dialog.getDialogPane().lookupButton(type).isDisable()));
        });
    }

    @Test void readOnlyComparisonAllowsExportButDisablesApply() throws Exception {
        Path original = directory.resolve("original.md");
        Path revised = directory.resolve("revised.md");
        Files.writeString(original, "a"); Files.writeString(revised, "b");
        DiffDialog dialog = createDialog(original, revised, "a", true, new AtomicReference<>());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> FxTestUtil.callAndWait(dialog::isReady));
        FxTestUtil.runAndWait(() -> {
            var types = dialog.getDialogPane().getButtonTypes();
            assertTrue(dialog.getDialogPane().lookupButton(types.get(0)).isDisable());
            assertFalse(dialog.getDialogPane().lookupButton(types.get(1)).isDisable());
            assertFalse(dialog.getDialogPane().lookupButton(types.get(2)).isDisable());
        });
    }

    @Test void atomicExportWritesUtf8AndRetainsOldFileOnFailure() throws Exception {
        Path target = directory.resolve("result.md");
        Files.writeString(target, "old");
        DiffDialog.writeAtomically(target, "Änderung 😀");
        assertEquals("Änderung 😀", Files.readString(target));
        Path invalid = directory.resolve("folder");
        Files.createDirectory(invalid);
        Files.writeString(invalid.resolve("keep.md"), "keep");
        assertThrows(java.io.IOException.class, () -> DiffDialog.writeAtomically(invalid, "new"));
        assertEquals("keep", Files.readString(invalid.resolve("keep.md")));
        try (var entries = Files.list(directory)) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString().startsWith(".markdown-review-")));
        }
    }

    @Test void exportingToFilesystemRootReportsIOException() {
        Path root = directory.toAbsolutePath().getRoot();
        assertThrows(java.io.IOException.class, () -> DiffDialog.writeAtomically(root, "content"));
    }

    @Test void closingAnUnexportedReviewCanBeCancelled() throws Exception {
        Path original = directory.resolve("original.md");
        Path revised = directory.resolve("revised.md");
        Files.writeString(original, "a"); Files.writeString(revised, "b");
        DiffDialog dialog = createDialog(original, revised, "a", false, new AtomicReference<>());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> FxTestUtil.callAndWait(dialog::isReady));
        FxTestUtil.runAndWait(() -> {
            dialog.getComparisonView().rejectAll();
            javafx.application.Platform.runLater(() -> answerCloseConfirmation(false));
            dialog.close();
            assertTrue(dialog.isShowing());
            javafx.application.Platform.runLater(() -> answerCloseConfirmation(true));
            dialog.close();
            assertFalse(dialog.isShowing());
        });
    }

    private void answerCloseConfirmation(boolean discard) {
        for (Window window : List.copyOf(Window.getWindows())) {
            if (window.getScene().getRoot() instanceof javafx.scene.control.DialogPane pane) {
                var type = discard ? de.richmarkdown.ui.dialogs.DialogButtons.DISCARD
                        : de.richmarkdown.ui.dialogs.DialogButtons.KEEP;
                if (pane.lookupButton(type) instanceof Button answer) { answer.fire(); return; }
            }
        }
        throw new AssertionError("Missing close confirmation");
    }

    @Test void cancellingApplyPreservesEditorAndReview() throws Exception {
        Path original = directory.resolve("original.md");
        Path revised = directory.resolve("revised.md");
        Files.writeString(original, "original");
        Files.writeString(revised, "updated");
        DiffDialog dialog = createDialog(original, revised, "original", false, new AtomicReference<>());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> FxTestUtil.callAndWait(dialog::isReady));
        FxTestUtil.runAndWait(() -> {
            dialog.getComparisonView().rejectAll();
            var before = dialog.getComparisonView().getChanges();
            javafx.application.Platform.runLater(() -> answerApplyConfirmation(false, false));
            ((Button) dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().getFirst())).fire();
            assertTrue(dialog.isShowing());
            assertEquals(before, dialog.getComparisonView().getChanges());
            assertEquals("original", createdEditor.getMarkdownSource());
            assertFalse(createdEditor.isModified());
            dialog.getComparisonView().markReviewSaved();
        });
    }

    @Test void confirmingApplyClosesOnceAndReplacementRemainsUndoable() throws Exception {
        Path original = directory.resolve("original.md");
        Path revised = directory.resolve("revised.md");
        Files.writeString(original, "original");
        Files.writeString(revised, "updated");
        DiffDialog dialog = createDialog(original, revised, "original", false, new AtomicReference<>());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> FxTestUtil.callAndWait(dialog::isReady));
        FxTestUtil.runAndWait(() -> {
            var view = dialog.getComparisonView();
            view.setComment(view.getChanges().getFirst(), "Review note");
            javafx.application.Platform.runLater(() -> answerApplyConfirmation(true, true));
            ((Button) dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().getFirst())).fire();
            assertFalse(dialog.isShowing());
            assertEquals("updated", createdEditor.getMarkdownSource());
            assertTrue(createdEditor.isModified());
            createdEditor.getControl().getActionFactory().undo().execute(new javafx.event.ActionEvent());
        });
        FxTestUtil.flushFxEvents(); FxTestUtil.flushFxEvents();
        assertEquals("original", FxTestUtil.callAndWait(createdEditor::getMarkdownSource));
        assertEquals("original", Files.readString(original));
    }

    @Test void failedApplyKeepsDialogAndReviewOpen() throws Exception {
        Path original = directory.resolve("original.md");
        Path revised = directory.resolve("revised.md");
        Files.writeString(original, "original");
        Files.writeString(revised, "updated");
        AtomicReference<String> error = new AtomicReference<>();
        DiffDialog dialog = createDialog(original, revised, "original", false, error);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> FxTestUtil.callAndWait(dialog::isReady));
        FxTestUtil.runAndWait(() -> {
            var before = dialog.getComparisonView().getChanges();
            createdEditor.getControl().setEditable(false);
            javafx.application.Platform.runLater(() -> answerApplyConfirmation(true, false));
            ((Button) dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().getFirst())).fire();
            assertTrue(dialog.isShowing());
            assertNotNull(error.get());
            assertEquals(before, dialog.getComparisonView().getChanges());
            assertEquals("original", createdEditor.getMarkdownSource());
        });
    }

    private void answerApplyConfirmation(boolean apply, boolean hasComments) {
        for (Window window : List.copyOf(Window.getWindows())) {
            if (window.getScene().getRoot() instanceof javafx.scene.control.DialogPane pane) {
                var confirm = pane.getButtonTypes().stream()
                        .filter(type -> type.getText().equals(I18n.text("trackChanges.apply.confirm.button")))
                        .findFirst().orElse(null);
                if (confirm == null) continue;
                assertTrue(pane.getContentText().contains(I18n.text("trackChanges.apply.confirm")));
                assertEquals(hasComments, pane.getContentText().contains(I18n.text("trackChanges.apply.confirm.comments")));
                pane.getButtonTypes().forEach(type -> assertNotNull(((Button) pane.lookupButton(type)).getTooltip()));
                var answer = apply ? confirm : pane.getButtonTypes().stream()
                        .filter(type -> type.getButtonData() == javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE)
                        .findFirst().orElseThrow();
                ((Button) pane.lookupButton(answer)).fire();
                return;
            }
        }
        throw new AssertionError("Missing apply confirmation");
    }

    private DiffDialog createDialog(Path original, Path revised, String contents, boolean readOnly,
                                    AtomicReference<String> error) {
        return FxTestUtil.callAndWait(() -> {
            MarkdownEditor editor = new MarkdownEditor();
            createdEditor = editor;
            Stage owner = new Stage();
            owner.setScene(new Scene(new StackPane(editor.getControl()), 600, 400));
            owner.show();
            try { editor.open(original.toFile()); }
            catch (java.io.IOException failure) { throw new RuntimeException(failure); }
            DiffDialog dialog = new DiffDialog(owner, editor, original.toFile(), revised.toFile(), contents, readOnly, error::set);
            dialog.show();
            return dialog;
        });
    }
}
