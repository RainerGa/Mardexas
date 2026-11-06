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

import com.gluonhq.richtextarea.Selection;
import com.gluonhq.richtextarea.model.TextDecoration;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.markdown.ChangeType;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Regressions for false differences caused by serializing and reparsing the editor. */
class DiffSnapshotTest {
    private static final String ORIGINAL = """
            <a href="https://example.org"><img src="/icon.svg" width="100px"></a>

            ### Parametric Modeler

            [Website](https://example.org) •
            [Blog](https://example.org/blog)


            [![Release](https://example.org/badge.svg)](https://example.org/releases)

            Overview
            --------

            * **Modeling** This is a parametric 3D
            modeler using parameters in its history.

            Installing
            ----------

            Install the package.
            """;
    private static final String REVISED = ORIGINAL.replace("parameters", "parametadfasdf adf adf asdf asdf ers");
    @TempDir Path directory;
    private final List<Stage> stages = new ArrayList<>();

    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @AfterEach void closeStages() { FxTestUtil.runAndWait(() -> stages.forEach(Stage::close)); }

    private MarkdownEditor open(String source) throws Exception {
        Path file = directory.resolve("README.md");
        Files.writeString(file, source);
        MarkdownEditor editor = FxTestUtil.callAndWait(() -> {
            MarkdownEditor created = new MarkdownEditor();
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(created.getControl()), 700, 500));
            stage.show();
            stages.add(stage);
            try { created.open(file.toFile()); }
            catch (java.io.IOException failure) { throw new RuntimeException(failure); }
            return created;
        });
        flush();
        return editor;
    }

    private void flush() { for (int i = 0; i < 4; i++) FxTestUtil.flushFxEvents(); }

    @Test void comparingLoadedSourceToItselfHasNoFalseDifferences() throws Exception {
        MarkdownEditor editor = open(ORIGINAL);
        var snapshot = FxTestUtil.callAndWait(editor::getDocumentSnapshot);
        var comparison = TrackChangesView.prepare(snapshot, directory, ORIGINAL, directory);
        assertTrue(comparison.changes().isEmpty(), comparison.changes().toString());
        assertFalse(FxTestUtil.callAndWait(editor::isModified));
    }

    @Test void actualDialogShowsOnlyTheChangedWordDespiteMarkdownNormalization() throws Exception {
        MarkdownEditor editor = open(ORIGINAL);
        Path revised = directory.resolve("README2.md");
        Files.writeString(revised, REVISED);
        DiffDialog dialog = FxTestUtil.callAndWait(() -> {
            DiffDialog created = new DiffDialog(stages.getFirst(), editor, directory.resolve("README.md").toFile(),
                    revised.toFile(), editor.getDocumentSnapshot(), false, error -> assertNull(error));
            created.show();
            return created;
        });
        try {
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> FxTestUtil.callAndWait(dialog::isReady));
            FxTestUtil.runAndWait(() -> {
                var changes = dialog.getComparisonView().getChanges();
                assertEquals(2, changes.size(), changes.toString());
                assertEquals(ChangeType.DELETED, changes.get(0).type());
                assertEquals("parameters ", changes.get(0).text());
                assertEquals(ChangeType.INSERTED, changes.get(1).type());
                assertEquals("parametadfasdf adf adf asdf asdf ers ", changes.get(1).text());
            });
        } finally { FxTestUtil.runAndWait(dialog::close); }
        assertFalse(FxTestUtil.callAndWait(editor::isModified));
    }

    @Test void unsavedTextIsComparedWithoutMarkingEditorSaved() throws Exception {
        MarkdownEditor editor = open("parameters");
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory()
                .selectAndInsertText(new Selection(0, 10), "tolerances").execute(new ActionEvent()));
        flush();
        var snapshot = FxTestUtil.callAndWait(editor::getDocumentSnapshot);
        var comparison = TrackChangesView.prepare(snapshot, directory, "parameters", directory);
        assertEquals(List.of("tolerances", "parameters"), comparison.changes().stream().map(change -> change.text()).toList());
        assertTrue(FxTestUtil.callAndWait(editor::isModified));
    }

    @Test void unsavedFormattingIsStillARealDifference() throws Exception {
        MarkdownEditor editor = open("Important");
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().selectAndDecorate(new Selection(0, 9),
                TextDecoration.builder().presets().fontWeight(FontWeight.BOLD).build()).execute(new ActionEvent()));
        flush();
        var snapshot = FxTestUtil.callAndWait(editor::getDocumentSnapshot);
        var comparison = TrackChangesView.prepare(snapshot, directory, "Important", directory);
        assertEquals(1, comparison.changes().size());
        assertEquals(ChangeType.FORMAT_CHANGED, comparison.changes().getFirst().type());
        assertTrue(FxTestUtil.callAndWait(editor::isModified));
    }
}
