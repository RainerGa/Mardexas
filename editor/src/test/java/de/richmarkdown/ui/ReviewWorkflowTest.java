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
import com.gluonhq.richtextarea.model.Document;
import com.gluonhq.richtextarea.model.TextDecoration;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.markdown.*;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReviewWorkflowTest {
    private final List<Stage> stages = new ArrayList<>();

    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @AfterEach void closeStages() { FxTestUtil.runAndWait(() -> stages.forEach(Stage::close)); }

    private TrackChangesView comparison(String original, String revised) {
        TrackChangesView view = FxTestUtil.callAndWait(() -> {
            TrackChangesView created = new TrackChangesView();
            Stage stage = new Stage();
            stage.setScene(new Scene(created, 1000, 700));
            stage.show();
            stages.add(stage);
            created.loadComparison(TrackChangesView.prepare(original, null, revised, null));
            return created;
        });
        flush();
        return view;
    }

    private void flush() { FxTestUtil.flushFxEvents(); FxTestUtil.flushFxEvents(); FxTestUtil.flushFxEvents(); }
    private TextDecoration style(Document document) { return (TextDecoration) document.getDecorations().getFirst().getDecoration(); }

    @Test void preparedComparisonOwnsAnImmutableChangeList() {
        List<Change> supplied = new ArrayList<>(TrackChangesEngine.diff("before", "after"));
        List<Change> expected = List.copyOf(supplied);
        var comparison = new TrackChangesView.Comparison(new Document("before"), new Document("after"), supplied);
        supplied.clear();
        assertEquals(expected, comparison.changes());
        assertThrows(UnsupportedOperationException.class, () -> comparison.changes().clear());
    }

    @Test void individualDecisionCanBeUndoneRedoneAndReset() {
        TrackChangesView view = comparison("Hello world", "Hello brave world");
        FxTestUtil.runAndWait(() -> {
            Change insertion = view.getChanges().getFirst();
            view.decide(insertion, ChangeStatus.REJECTED);
            assertEquals("Hello world", view.getResultDocument().getText());
            assertTrue(view.canUndo());
            view.undo();
            assertEquals(ChangeStatus.PENDING, view.getChanges().getFirst().status());
            assertEquals("Hello brave world", view.getResultDocument().getText());
            view.redo();
            assertEquals(ChangeStatus.REJECTED, view.getChanges().getFirst().status());
            view.decide(insertion, ChangeStatus.PENDING);
            assertEquals(ChangeStatus.PENDING, view.getChanges().getFirst().status());
            assertFalse(view.canRedo());
        });
    }

    @Test void bulkDecisionsPreservePriorDecisionsAndUndoInOneStep() {
        TrackChangesView view = comparison("A B C", "A X B Y C");
        FxTestUtil.runAndWait(() -> {
            Change first = view.getChanges().getFirst();
            view.decide(first, ChangeStatus.REJECTED);
            List<Change> before = view.getChanges();
            view.acceptAll();
            assertEquals(ChangeStatus.REJECTED, view.getChanges().getFirst().status());
            assertTrue(view.getChanges().stream().skip(1).allMatch(c -> c.status() == ChangeStatus.ACCEPTED));
            view.undo();
            assertEquals(before, view.getChanges());
            view.redo();
            assertTrue(view.getChanges().stream().noneMatch(c -> c.status() == ChangeStatus.PENDING));
        });
    }

    @Test void commentsShareHistoryAndSurviveOtherDecisions() {
        TrackChangesView view = comparison("Hello world", "Hello brave world");
        FxTestUtil.runAndWait(() -> {
            Change change = view.getChanges().getFirst();
            view.setComment(change, "Please verify");
            assertEquals("Please verify", view.getChanges().getFirst().comment());
            assertEquals(view.getChanges(), view.getReviewPane().getChanges());
            view.acceptAll();
            assertEquals("Please verify", view.getChanges().getFirst().comment());
            view.undo();
            assertEquals("Please verify", view.getChanges().getFirst().comment());
            view.undo();
            assertNull(view.getChanges().getFirst().comment());
            view.redo();
            assertEquals("Please verify", view.getChanges().getFirst().comment());
            view.setComment(change, " ");
            assertNull(view.getChanges().getFirst().comment());
            assertFalse(view.canRedo());
        });
    }

    @Test void navigationContinuesAfterCurrentDecision() {
        TrackChangesView view = comparison("A B C D", "A X B Y C Z D");
        FxTestUtil.runAndWait(() -> {
            assertEquals(3, view.getChanges().size());
            Change middle = view.getChanges().get(1);
            view.getReviewPane().selectChange(middle);
            view.decide(middle, ChangeStatus.ACCEPTED);
            assertEquals(view.getChanges().get(2).id(), view.getReviewPane().getSelectedChange().id());
            view.undo();
            assertEquals(middle.id(), view.getReviewPane().getSelectedChange().id());
        });
    }

    @Test void pendingAndRejectedFormattingAreConsistentAcrossModes() {
        TrackChangesView view = comparison("**Important**", "Important");
        FxTestUtil.runAndWait(() -> {
            assertEquals(FontWeight.NORMAL, style(view.getResultDocument()).getFontWeight());
            view.decide(view.getChanges().getFirst(), ChangeStatus.REJECTED);
            assertEquals(FontWeight.BOLD, style(view.getResultDocument()).getFontWeight());
        });
        flush();
        FxTestUtil.runAndWait(() -> {
            assertEquals(FontWeight.BOLD, style(view.getInlineView().getDocument()).getFontWeight());
            view.undo();
            view.setViewMode(ViewMode.NO_MARKUP);
        });
        flush();
        FxTestUtil.runAndWait(() -> {
            assertEquals(FontWeight.NORMAL, style(view.getInlineView().getDocument()).getFontWeight());
            view.setViewMode(ViewMode.ORIGINAL);
        });
        flush();
        FxTestUtil.runAndWait(() -> assertEquals(FontWeight.BOLD, style(view.getInlineView().getDocument()).getFontWeight()));
    }

    @Test void cleanModesAndExportNeverContainReviewFormatting() {
        TrackChangesView view = comparison("Hello world", "Hello brave world");
        FxTestUtil.runAndWait(() -> view.setViewMode(ViewMode.NO_MARKUP));
        flush();
        FxTestUtil.runAndWait(() -> {
            view.getInlineView().selectChange(view.getChanges().getFirst());
            Document displayed = view.getInlineView().getDocument();
            Document exported = view.getResultDocument();
            assertEquals("Hello brave world", exported.getText());
            assertEquals(displayed.getDecorations(), exported.getDecorations());
            assertTrue(exported.getDecorations().stream().map(d -> (TextDecoration) d.getDecoration())
                    .noneMatch(d -> d.isUnderline() || d.isStrikethrough() || "#008000".equals(d.getForeground())));
        });
    }

    @Test void simpleModeHidesDeletionsAndHasMarginMarkers() {
        TrackChangesView view = comparison("Hello old world", "Hello new world");
        FxTestUtil.runAndWait(() -> view.setViewMode(ViewMode.SIMPLE_MARKUP));
        flush();
        FxTestUtil.runAndWait(() -> {
            assertEquals("Hello new world", view.getInlineView().getDocument().getText());
            assertEquals(2, view.getChanges().size());
            assertFalse(view.getInlineView().getDocument().getDecorations().stream()
                    .map(d -> (TextDecoration) d.getDecoration()).anyMatch(TextDecoration::isStrikethrough));
        });
    }

    @Test void reviewButtonsHaveVectorGraphicsTooltipsAndEnoughSpace() {
        TrackChangesView view = comparison("old text", "new text");
        FxTestUtil.runAndWait(() -> {
            view.applyCss();
            view.layout();
            var buttons = view.lookupAll(".button").stream().filter(node -> node.getId() != null
                    && node.getId().startsWith("review-")).map(node -> (Button) node).toList();
            assertTrue(buttons.size() >= 9);
            for (Button button : buttons) {
                assertNotNull(button.getGraphic());
                assertTrue(button.getText() == null || button.getText().isEmpty());
                assertNotNull(button.getTooltip());
                assertFalse(button.getTooltip().getText().isBlank());
                assertEquals(button.getTooltip().getText(), button.getAccessibleText());
                assertTrue(button.getMinWidth() >= 30);
            }
        });
    }

    @Test void selectionKeepsDocumentAndHandlesEmojiOffsets() {
        TrackChangesView view = comparison("😀 Hello world", "😀 Hello brave world");
        FxTestUtil.runAndWait(() -> {
            Document before = view.getInlineView().getDocument();
            view.getInlineView().selectChange(view.getChanges().getFirst());
            assertSame(before, view.getInlineView().getDocument());
            var model = ((RichTextAreaSkin) view.getInlineView().getSkin()).getViewModel();
            assertEquals("brave ", model.getTextBuffer().getText(model.getSelection().getStart(), model.getSelection().getEnd()));
        });
    }

    @Test void newComparisonResetsHistoryAndComments() {
        TrackChangesView view = comparison("a", "b");
        FxTestUtil.runAndWait(() -> {
            view.acceptAll();
            assertTrue(view.hasReviewChanges());
            view.markReviewSaved();
            assertFalse(view.hasReviewChanges());
            view.undo();
            assertTrue(view.hasReviewChanges());
            view.loadComparison(TrackChangesView.prepare("same", null, "same", null));
            assertTrue(view.getChanges().isEmpty());
            assertFalse(view.canUndo());
            assertFalse(view.canRedo());
            assertFalse(view.hasReviewChanges());
        });
    }
    @Test void reportExportsCommentsAndDecisionsWithoutMarkingThemAsSavedInResult() {
        TrackChangesView view = comparison("old", "new");
        FxTestUtil.runAndWait(() -> {
            view.setComment(view.getChanges().getFirst(), "Check ```example``` carefully");
            view.rejectAll();
            view.markResultSaved();
            assertTrue(view.hasReviewChanges(), "Comments have not been exported with the result");
            String report = view.getReviewReport("original.md", "revised.md");
            assertTrue(report.contains("Check ```example``` carefully"));
            assertTrue(report.contains(ReviewPane.statusText(ChangeStatus.REJECTED)));
            assertTrue(report.contains("original.md"));
            view.markReviewSaved();
            assertFalse(view.hasReviewChanges());
        });
    }

    @Test void exportedMissingImageKeepsItsRevisedSourceDirectory(@org.junit.jupiter.api.io.TempDir Path directory) {
        FxTestUtil.runAndWait(() -> {
            TrackChangesView view = new TrackChangesView();
            Path original = directory.resolve("original");
            Path revised = directory.resolve("revised");
            view.loadComparison(TrackChangesView.prepare("", original, "![image](missing.png)", revised));
            String markdown = view.getResultMarkdown(original);
            assertTrue(markdown.contains(revised.resolve("missing.png").toUri().toString()), markdown);
        });
    }

    @Test void imageReplacementCanBeRejectedAndRestored(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/pE8AAAAASUVORK5CYII=");
        java.nio.file.Files.createDirectories(directory.resolve("old"));
        java.nio.file.Files.createDirectories(directory.resolve("new"));
        java.nio.file.Files.write(directory.resolve("old/image.png"), png);
        java.nio.file.Files.write(directory.resolve("new/image.png"), png);
        FxTestUtil.runAndWait(() -> {
            TrackChangesView view = new TrackChangesView();
            view.loadComparison(TrackChangesView.prepare("![image](image.png)", directory.resolve("old"),
                    "![image](image.png)", directory.resolve("new")));
            assertEquals(1, view.getChanges().size());
            assertEquals(ChangeType.FORMAT_CHANGED, view.getChanges().getFirst().type());
            assertTrue(view.getResultMarkdown(null).contains(directory.resolve("new/image.png").toUri().toString()));
            view.rejectAll();
            assertTrue(view.getResultMarkdown(null).contains(directory.resolve("old/image.png").toUri().toString()));
            view.undo();
            assertTrue(view.getResultMarkdown(null).contains(directory.resolve("new/image.png").toUri().toString()));
        });
    }

    @Test void invalidImageAddressesAreVisiblePlaceholdersInsteadOfRenderingErrors() {
        FxTestUtil.runAndWait(() -> {
            for (String address : List.of("", "unknown:bad.png", "file:relative.png", "http://bad host/image.png")) {
                TrackChangesView view = new TrackChangesView();
                view.loadComparison(TrackChangesView.prepare("", null, "![image](" + address + ")", null));
                assertTrue(view.getResultDocument().getDecorations().stream()
                        .noneMatch(run -> run.getDecoration() instanceof com.gluonhq.richtextarea.model.ImageDecoration));
                assertTrue(view.getResultDocument().getText().contains("Bild nicht gefunden"));
            }
        });
    }

    @Test void statusFilterRemainsActiveAfterDecisionsAndSupportsUndo() {
        TrackChangesView view = comparison("A B C", "A X B Y C");
        FxTestUtil.runAndWait(() -> {
            var filter = (javafx.scene.control.ComboBox<?>) view.getReviewPane().lookup(".combo-box");
            filter.getSelectionModel().select(1);
            view.decide(view.getChanges().getFirst(), ChangeStatus.ACCEPTED);
            assertEquals(1, filter.getSelectionModel().getSelectedIndex());
            assertEquals(ChangeStatus.PENDING, view.getReviewPane().getSelectedChange().status());
            view.undo();
            assertEquals(1, filter.getSelectionModel().getSelectedIndex());
            assertEquals(view.getChanges().getFirst().id(), view.getReviewPane().getSelectedChange().id());
        });
    }

    @Test void imagesInsideQuotesResolveAgainstTheirSourceDirectory(@org.junit.jupiter.api.io.TempDir Path directory) {
        FxTestUtil.runAndWait(() -> {
            TrackChangesView view = new TrackChangesView();
            view.loadComparison(TrackChangesView.prepare("", null, "> ![image](missing.png)", directory));
            assertTrue(view.getResultDocument().getDecorations().stream()
                    .noneMatch(run -> run.getDecoration() instanceof com.gluonhq.richtextarea.model.ImageDecoration));
            assertTrue(view.getResultMarkdown(null).contains(directory.resolve("missing.png").toUri().toString()), view.getResultMarkdown(null));
        });
    }

}
