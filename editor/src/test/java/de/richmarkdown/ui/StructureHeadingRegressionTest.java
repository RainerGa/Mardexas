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

import com.gluonhq.richtextarea.RichTextAreaSkin;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructureHeadingRegressionTest {
    private static final List<String> TITLES = List.of("Your own 3D Parametric Modeler", "Overview", "Installing",
            "Compiling", "Reporting Issues", "Usage & Getting Help");
    @TempDir Path directory;
    private Stage stage;
    private MarkdownEditor editor;
    private DocumentStructureView structure;

    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @AfterEach void close() { if (stage != null) FxTestUtil.runAndWait(stage::close); }

    private void open(String markdown) throws Exception {
        Path file = directory.resolve("README.md");
        Files.writeString(file, markdown);
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor();
            structure = new DocumentStructureView(editor);
            stage = new Stage();
            stage.setScene(new Scene(new HBox(structure, editor.getControl()), 900, 500));
            stage.show();
            try { editor.open(file.toFile()); }
            catch (java.io.IOException failure) { throw new RuntimeException(failure); }
        });
        flush();
    }

    private void flush() { for (int i = 0; i < 4; i++) FxTestUtil.flushFxEvents(); }

    private String readme() throws Exception {
        Files.write(directory.resolve("logo.png"), java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/pE8AAAAASUVORK5CYII="));
        return """
                ![logo](logo.png)

                ### Your own 3D Parametric Modeler

                [Git repository](https://example.org/repository) •
                [Blog](https://example.org/blog)

                😀 A regular paragraph before the next heading.

                Overview
                --------

                **Body text** is not a heading.

                Installing
                ----------

                Install packages.

                Compiling
                ---------

                Build instructions.

                Reporting Issues
                ---------

                Issue instructions.

                Usage & Getting Help
                --------------------

                Documentation.
                """;
    }

    @Test void openingReadmeFindsAllHeadingsAndNoLinks() throws Exception {
        open(readme());
        FxTestUtil.runAndWait(() -> {
            var headings = structure.getListView().getItems();
            assertEquals(TITLES, headings.stream().map(heading -> heading.text()).toList());
            assertEquals(List.of(3, 2, 2, 2, 2, 2), headings.stream().map(heading -> heading.level()).toList());
            assertEquals(TITLES, editor.getHeadings().stream().map(heading -> heading.text()).toList());
            assertFalse(editor.isModified());
        });
    }

    @Test void structureNavigationUsesInternalPositionsAfterImagesAndEmoji() throws Exception {
        open(readme());
        FxTestUtil.runAndWait(() -> {
            var model = ((RichTextAreaSkin) editor.getControl().getSkin()).getViewModel();
            for (int index = 0; index < TITLES.size(); index++) {
                var heading = structure.getListView().getItems().get(index);
                structure.getListView().getSelectionModel().select(index);
                assertEquals(heading.position(), editor.getInternalCaretPosition());
                var paragraph = model.getParagraphList().stream()
                        .filter(item -> item.getStart() == heading.position()).findFirst().orElseThrow();
                assertEquals(TITLES.get(index), model.getTextBuffer().getText(paragraph.getStart(), paragraph.getEnd()).trim());
            }
            assertFalse(editor.isModified());
        });
    }

    @Test void atxHeadingsAfterEmojiDoNotTurnBodyTextIntoHeadings() throws Exception {
        open("😀 Intro\n\n# 😀 First\n\n[Link](https://example.org)\n\n## Second\n\nText");
        FxTestUtil.runAndWait(() -> assertEquals(List.of("😀 First", "Second"),
                editor.getHeadings().stream().map(heading -> heading.text()).toList()));
    }
    @Test void selectedHeadingFollowsInternalCaretPositionAfterImages() throws Exception {
        open(readme());
        FxTestUtil.runAndWait(() -> {
            int next = structure.getListView().getItems().get(1).position();
            editor.goToPosition(next - 1);
            assertEquals(0, structure.getListView().getSelectionModel().getSelectedIndex());
            editor.goToPosition(next);
            assertEquals(1, structure.getListView().getSelectionModel().getSelectedIndex());
            editor.goToPosition(0);
            assertEquals(-1, structure.getListView().getSelectionModel().getSelectedIndex());
        });
    }

    @Test void headingJumpsPreserveBackAndForwardPositionsAfterImagesAndEmoji() throws Exception {
        Path file = directory.resolve("README.md");
        Files.writeString(file, readme());
        EditorWindow window = FxTestUtil.callAndWait(() -> {
            EditorWindow created = new EditorWindow();
            stage = created;
            created.setStructureViewVisible(true);
            created.show();
            created.openFile(file.toFile());
            return created;
        });
        window.pendingFileOperation().get(20, java.util.concurrent.TimeUnit.SECONDS);
        flush();
        FxTestUtil.runAndWait(() -> {
            var editor = window.getEditor();
            var headings = editor.getHeadings();
            int source = headings.get(1).position() + 1;
            int target = headings.getLast().position();
            editor.goToPosition(source);
            window.getStructureView().getListView().getSelectionModel().select(headings.size() - 1);
            assertEquals(target, editor.getInternalCaretPosition());
            assertTrue(window.navigateBack());
            assertEquals(source, editor.getInternalCaretPosition());
            assertTrue(window.navigateForward());
            assertEquals(target, editor.getInternalCaretPosition());
            assertFalse(editor.isModified());
        });
    }

}
