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

import de.richmarkdown.FxTestUtil;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AP-02: Oeffentliche Datei-API auf {@link MarkdownEditor}.
 */
class MarkdownEditorFileApiTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    private MarkdownEditor newEditorInScene() {
        MarkdownEditor[] holder = new MarkdownEditor[1];
        FxTestUtil.runAndWait(() -> {
            MarkdownEditor created = new MarkdownEditor();
            holder[0] = created;
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(created.getControl()), 600, 400));
            stage.show();
        });
        FxTestUtil.flushFxEvents();
        return holder[0];
    }

    private void flush() {
        FxTestUtil.flushFxEvents();
        FxTestUtil.flushFxEvents();
        FxTestUtil.flushFxEvents();
    }
    @Test
    void openSetsContentAndCurrentFile() throws IOException {
        File file = tempDir.resolve("open.md").toFile();
        Files.writeString(file.toPath(), "# Title\r\n\r\ntext", StandardCharsets.UTF_8);
        MarkdownEditor editor = newEditorInScene();
        FxTestUtil.runAndWait(() -> {
            try { editor.open(file); } catch (IOException e) { throw new RuntimeException(e); }
        });
        flush();
        assertEquals("# Title\n\ntext", FxTestUtil.callAndWait(editor::getMarkdown));
        assertEquals(file, FxTestUtil.callAndWait(() -> editor.getCurrentFile().orElseThrow()));
        assertFalse(FxTestUtil.callAndWait(editor::isModified));
    }

    @Test
    void saveWritesNormalizedUtf8AndSetsCurrentFile() throws IOException {
        File file = tempDir.resolve("save.md").toFile();
        MarkdownEditor editor = newEditorInScene();
        FxTestUtil.runAndWait(() -> editor.setMarkdown("# Title\n\n- a\n- b"));
        flush();
        FxTestUtil.runAndWait(() -> {
            try { editor.save(file); } catch (IOException e) { throw new RuntimeException(e); }
        });
        flush();
        assertEquals("# Title\n\n- a\n- b", Files.readString(file.toPath(), StandardCharsets.UTF_8));
        assertEquals(file, FxTestUtil.callAndWait(() -> editor.getCurrentFile().orElseThrow()));
        assertFalse(FxTestUtil.callAndWait(editor::isModified));
    }

    @Test
    void saveWithoutFileThrowsIllegalStateException() {
        MarkdownEditor editor = newEditorInScene();
        assertTrue(FxTestUtil.callAndWait(() -> editor.getCurrentFile().isEmpty()));
        FxTestUtil.runAndWait(() -> {
            try {
                editor.save();
                throw new AssertionError("expected IllegalStateException");
            } catch (IllegalStateException expected) {
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
    @Test
    void saveUsesAssociatedFile() throws IOException {
        File file = tempDir.resolve("current.md").toFile();
        Files.writeString(file.toPath(), "original", StandardCharsets.UTF_8);
        MarkdownEditor editor = newEditorInScene();
        FxTestUtil.runAndWait(() -> {
            try { editor.open(file); } catch (IOException e) { throw new RuntimeException(e); }
        });
        flush();
        FxTestUtil.runAndWait(() -> editor.setMarkdown("original changed"));
        flush();
        FxTestUtil.runAndWait(() -> {
            try { editor.save(); } catch (IOException e) { throw new RuntimeException(e); }
        });
        flush();
        assertEquals("original changed", Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    void directoryAsTargetPropagatesIOException() {
        MarkdownEditor editor = newEditorInScene();
        File directory = tempDir.toFile();
        FxTestUtil.runAndWait(() -> editor.setMarkdown("x"));
        flush();
        FxTestUtil.runAndWait(() -> {
            try {
                editor.save(directory);
                throw new AssertionError("expected IOException");
            } catch (IOException expected) {
            }
        });
    }

    @Test
    void clearEmptiesDocumentAndResetsFile() throws IOException {
        File file = tempDir.resolve("clear.md").toFile();
        Files.writeString(file.toPath(), "content", StandardCharsets.UTF_8);
        MarkdownEditor editor = newEditorInScene();
        FxTestUtil.runAndWait(() -> {
            try { editor.open(file); } catch (IOException e) { throw new RuntimeException(e); }
        });
        flush();
        FxTestUtil.runAndWait(editor::clear);
        flush();
        assertEquals("", FxTestUtil.callAndWait(editor::getMarkdown));
        assertTrue(FxTestUtil.callAndWait(() -> editor.getCurrentFile().isEmpty()));
    }
}
