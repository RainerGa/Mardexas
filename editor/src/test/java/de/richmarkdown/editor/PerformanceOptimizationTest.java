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

package de.richmarkdown.editor;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.model.*;
import de.richmarkdown.FxTestUtil;
import java.nio.file.*;
import java.util.concurrent.*;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceOptimizationTest {
    @TempDir Path directory;
    MarkdownEditor editor;
    Stage stage;
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @BeforeEach void setup() {
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor(); stage = new Stage();
            stage.setScene(new Scene(new StackPane(editor.getControl()), 800, 600)); stage.show();
        });
        FxTestUtil.flushFxEvents();
    }
    @AfterEach void close() { FxTestUtil.runAndWait(() -> { editor.cancelPendingIo(); stage.close(); editor.getControl().getSkin().dispose(); }); }
    private void text(String value) { FxTestUtil.runAndWait(() -> editor.setMarkdown(value)); FxTestUtil.flushFxEvents(); }
    private void await(CompletableFuture<Void> operation) throws Exception { operation.get(20, TimeUnit.SECONDS); FxTestUtil.flushFxEvents(); }
    private void insert(String value) {
        var model = ((RichTextAreaSkin) editor.getControl().getSkin()).getViewModel();
        model.setCaretPosition(0);
        new com.gluonhq.richtextarea.viewmodel.ActionCmdFactory().insertText(value).apply(model);
    }
    @Test void snapshotIsSharedImmutableAndInvalidatedBySameLengthStylesAndUndo() {
        text("# title\n\nbody");
        FxTestUtil.runAndWait(() -> {
            Document first = editor.getDocumentSnapshot();
            assertSame(first, editor.getDocumentSnapshot());
            assertThrows(UnsupportedOperationException.class, () -> first.getDecorations().clear());
            var model = ((RichTextAreaSkin) editor.getControl().getSkin()).getViewModel();
            model.getTextBuffer().decorate(0, 5, TextDecoration.builder().presets().fontSize(40).build());
            Document changed = editor.getDocumentSnapshot();
            assertNotSame(first, changed); assertEquals(first.getText(), changed.getText());
            model.getTextBuffer().undo();
            assertNotSame(changed, editor.getDocumentSnapshot());
            assertEquals(first.getDecorations(), editor.getDocumentSnapshot().getDecorations());
        });
    }
    @Test void failedSavePreservesDirtyAndFileAssociation() throws Exception {
        text("body");
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().insertText("edit").execute(new ActionEvent()));
        FxTestUtil.flushFxEvents();
        var operation = FxTestUtil.callAndWait(() -> editor.saveAsync(directory.toFile()));
        assertThrows(ExecutionException.class, () -> operation.get(20, TimeUnit.SECONDS));
        FxTestUtil.runAndWait(() -> { assertTrue(editor.isModified()); assertTrue(editor.getCurrentFile().isEmpty()); });
    }
    @Test void editDuringSaveStaysDirtyAndWritesCapturedRevision() throws Exception {
        text("original");
        var operation = FxTestUtil.callAndWait(() -> {
            var future = editor.saveAsync(directory.resolve("saved.md").toFile());
            insert("new ");
            return future;
        });
        await(operation);
        assertEquals("original", Files.readString(directory.resolve("saved.md")));
        assertEquals(de.richmarkdown.io.MarkdownFileHandler.fingerprint(Files.readAllBytes(directory.resolve("saved.md"))),
                FxTestUtil.callAndWait(() -> editor.getDiskFingerprint().orElseThrow()));
        assertTrue(FxTestUtil.callAndWait(editor::isModified));
        assertTrue(FxTestUtil.callAndWait(() -> editor.getDocumentSnapshot().getText().contains("new ")));
    }
    @Test void twoSavesAreOrderedAndSuccessfulSaveClearsDirty() throws Exception {
        text("first");
        Path target = directory.resolve("saved.md");
        var both = FxTestUtil.callAndWait(() -> {
            var first = editor.saveAsync(target.toFile());
            insert("second ");
            var second = editor.saveAsync(target.toFile());
            return CompletableFuture.allOf(first, second);
        });
        await(both);
        assertEquals("second first", Files.readString(target));
        assertFalse(FxTestUtil.callAndWait(editor::isModified));
    }
    @Test void asyncLoadCompletesAfterInstallationAndDisposedLoadIsIgnored() throws Exception {
        Path file = directory.resolve("load.md"); Files.writeString(file, "# Loaded\n\nbody");
        await(FxTestUtil.callAndWait(() -> editor.openAsync(file.toFile())));
        FxTestUtil.runAndWait(() -> { assertEquals("Loaded", editor.getHeadings().getFirst().text()); assertFalse(editor.isModified()); });
        var stale = FxTestUtil.callAndWait(() -> { var future = editor.openAsync(file.toFile()); editor.cancelPendingIo(); return future; });
        assertThrows(CancellationException.class, () -> stale.get(20, TimeUnit.SECONDS));
    }
    @Test void heightsChangeOnStyleAndReturnOnUndoAndRespectWidth() {
        text("a paragraph with enough words to wrap at a narrow width\n\nlast paragraph");
        FxTestUtil.runAndWait(() -> {
            var skin = (RichTextAreaSkin) editor.getControl().getSkin();
            var paragraph = skin.getParagraphs().getFirst();
            double before = skin.measureParagraphHeight(paragraph, 500);
            assertEquals(before, skin.measureParagraphHeight(paragraph, 500));
            assertTrue(skin.measureParagraphHeight(paragraph, 80) > before);
            skin.getViewModel().getTextBuffer().decorate(0, 10, TextDecoration.builder().presets().fontSize(60).build());
            double styled = skin.measureParagraphHeight(skin.getParagraphs().getFirst(), 500);
            assertTrue(styled > before);
            skin.getViewModel().getTextBuffer().undo();
            assertEquals(before, skin.measureParagraphHeight(skin.getParagraphs().getFirst(), 500), 0.01);
        });
    }
}
