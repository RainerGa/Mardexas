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
import com.gluonhq.richtextarea.model.TextDecoration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AP-02: Oeffentliche Datei-API auf {@link MarkdownEditor}.
 */
class MarkdownEditorFileApiTest {

    @TempDir
    Path tempDir;

    private MarkdownEditor activeEditor;
    private Stage activeStage;

    @AfterEach
    void closeEditorBeforeDeletingTemporaryImages() throws Exception {
        try {
            // Image loading runs independently of the FX event queue. In particular,
            // Windows cannot delete an image while its loader still holds the file.
            if (activeEditor != null) {
                org.testfx.util.WaitForAsyncUtils.waitFor(FxTestUtil.TIMEOUT_SECONDS,
                        java.util.concurrent.TimeUnit.SECONDS, () -> FxTestUtil.callAndWait(() ->
                                activeEditor.getControl().lookupAll("ImageView").stream()
                                        .map(node -> ((javafx.scene.image.ImageView) node).getImage())
                                        .filter(java.util.Objects::nonNull)
                                        .allMatch(image -> image.getProgress() >= 1 || image.isError())));
            }
        } finally {
            FxTestUtil.runAndWait(() -> {
                if (activeEditor != null) activeEditor.getControl().setSkin(null);
                if (activeStage != null) activeStage.close();
            });
        }
    }

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    private MarkdownEditor newEditorInScene() {
        MarkdownEditor[] holder = new MarkdownEditor[1];
        FxTestUtil.runAndWait(() -> {
            MarkdownEditor created = new MarkdownEditor();
            holder[0] = created;
            activeEditor = created;
            Stage stage = new Stage();
            activeStage = stage;
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
    void openResolvesHtmlImageAgainstMarkdownDirectoryAndKeepsRelativePath() throws IOException {
        Path imagePath = tempDir.resolve("src/Gui/Icons/freecad.png");
        Files.createDirectories(imagePath.getParent());
        Files.write(imagePath, java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/pE8AAAAASUVORK5CYII="));
        File file = tempDir.resolve("README.md").toFile();
        Files.writeString(file.toPath(),
                "<a href=\"https://freecad.org\"><img src=\"/src/Gui/Icons/freecad.png\"></a>",
                StandardCharsets.UTF_8);
        MarkdownEditor editor = newEditorInScene();

        FxTestUtil.runAndWait(() -> {
            try {
                editor.open(file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        flush();

        assertEquals("<a href=\"https://freecad.org\"><img "
                + "src=\"src/Gui/Icons/freecad.png\"></a>",
                FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void openShowsPlaceholderForMissingLocalImageWithoutLosingMarkup() throws IOException {
        File file = tempDir.resolve("README.md").toFile();
        String html = "<a href=\"https://freecad.org\"><img "
                + "src=\"/src/Gui/Icons/freecad.svg\"></a>";
        Files.writeString(file.toPath(), html, StandardCharsets.UTF_8);
        MarkdownEditor editor = newEditorInScene();

        FxTestUtil.runAndWait(() -> {
            try {
                editor.open(file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        flush();

        assertTrue(FxTestUtil.callAndWait(() -> editor.getControl().getDocument().getText()
                .contains("[Bild nicht gefunden: /src/Gui/Icons/freecad.svg]")));
        assertEquals(html, FxTestUtil.callAndWait(editor::getMarkdown));
    }

    @Test
    void openRendersFreecadBadgeImageSyntaxAsLabeledHyperlinks() throws IOException {
        File file = tempDir.resolve("README.md").toFile();
        Files.writeString(file.toPath(),
                "![Release](https://github.com/freecad/freecad/releases/latest)\n"
                        + "![Crowdin](https://crowdin.com/project/freecad)",
                StandardCharsets.UTF_8);
        MarkdownEditor editor = newEditorInScene();

        FxTestUtil.runAndWait(() -> {
            try {
                editor.open(file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        flush();

        FxTestUtil.runAndWait(() -> {
            assertEquals("Release\nCrowdin", editor.getControl().getDocument().getText());
            assertEquals("https://github.com/freecad/freecad/releases/latest",
                    ((TextDecoration) editor.getControl().getDocument().getDecorations().get(0)
                            .getDecoration()).getURL());
            assertEquals("https://crowdin.com/project/freecad",
                    ((TextDecoration) editor.getControl().getDocument().getDecorations().get(1)
                            .getDecoration()).getURL());
        });
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
    @Test
    void replacingWithReviewedDocumentIsDirtyUndoableAndPreservesFile() throws IOException {
        File file = tempDir.resolve("reviewed.md").toFile();
        Files.writeString(file.toPath(), "original");
        MarkdownEditor editor = newEditorInScene();
        FxTestUtil.runAndWait(() -> {
            try { editor.open(file); } catch (IOException failure) { throw new RuntimeException(failure); }
        });
        flush();
        FxTestUtil.runAndWait(() -> editor.replaceDocument(
                new de.richmarkdown.markdown.MarkdownParser().parseInto("**reviewed**")));
        flush();
        assertEquals("**reviewed**", FxTestUtil.callAndWait(editor::getMarkdownSource));
        assertTrue(FxTestUtil.callAndWait(editor::isModified));
        assertEquals(file, FxTestUtil.callAndWait(() -> editor.getCurrentFile().orElseThrow()));
        assertEquals("original", Files.readString(file.toPath()));
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().undo().execute(new javafx.event.ActionEvent()));
        flush();
        assertEquals("original", FxTestUtil.callAndWait(editor::getMarkdownSource));
        assertFalse(FxTestUtil.callAndWait(editor::isModified));
    }

    @Test
    void readOnlySnapshotIncludesUnpublishedEditsWithoutClearingDirtyFlag() {
        MarkdownEditor editor = newEditorInScene();
        FxTestUtil.runAndWait(() -> editor.setMarkdown("original"));
        flush();
        FxTestUtil.runAndWait(() -> {
            editor.goToPosition("original".length());
            editor.getControl().getActionFactory().insertText("new ").execute(new javafx.event.ActionEvent());
        });
        flush();
        assertTrue(FxTestUtil.callAndWait(editor::isModified));
        assertEquals("originalnew ", FxTestUtil.callAndWait(editor::getMarkdownSource));
        assertTrue(FxTestUtil.callAndWait(editor::isModified));
    }

    @Test
    void reviewedReplacementPreservesParagraphsImagesAndRedo() throws IOException {
        Path image = tempDir.resolve("pixel.png");
        Files.write(image, java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/pE8AAAAASUVORK5CYII="));
        MarkdownEditor editor = newEditorInScene();
        FxTestUtil.runAndWait(() -> editor.setMarkdown("before"));
        flush();
        String markdown = "# Heading\n\n- one\n- **two**\n\n| A | B |\n| --- | --- |\n| x | y |\n\n![image](" + image.toUri() + ")";
        var parsed = new de.richmarkdown.markdown.MarkdownParser().parseInto(markdown);
        String canonical = new de.richmarkdown.markdown.MarkdownWriter().write(parsed);
        FxTestUtil.runAndWait(() -> editor.replaceDocument(parsed));
        flush();
        assertEquals(canonical, FxTestUtil.callAndWait(editor::getMarkdownSource));
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().undo().execute(new javafx.event.ActionEvent()));
        flush();
        assertEquals("before", FxTestUtil.callAndWait(editor::getMarkdownSource));
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().redo().execute(new javafx.event.ActionEvent()));
        flush();
        assertEquals(canonical, FxTestUtil.callAndWait(editor::getMarkdownSource));
    }

}
