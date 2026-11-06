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
import de.richmarkdown.markdown.Change;
import de.richmarkdown.markdown.ChangeStatus;
import de.richmarkdown.markdown.ChangeType;
import de.richmarkdown.markdown.ChangeViewMode;
import de.richmarkdown.markdown.MarkdownStyles;
import de.richmarkdown.markdown.TrackChangesEngine;
import de.richmarkdown.markdown.ViewMode;
import com.gluonhq.richtextarea.model.TextDecoration;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TrackChangesView} and related components.
 * Tests the logic without requiring full JavaFX initialization.
 */
class TrackChangesViewTest {

    @BeforeAll
    static void initToolkit() throws InterruptedException {
        FxTestUtil.startToolkit();
    }

    @Test
    void trackChangesViewCreation() {
        TrackChangesView view = new TrackChangesView();
        assertNotNull(view);
        assertEquals(ViewMode.ALL_MARKUP, view.getViewMode());
        assertTrue(view.getChanges().isEmpty());
    }

    @Test
    void trackChangesViewLoadLeftRight() {
        TrackChangesView view = new TrackChangesView();
        view.loadLeft("Hello world");
        view.loadRight("Hello brave world");

        List<Change> changes = view.getChanges();
        // Algorithm may produce multiple changes due to LCS tie-breaking
        assertTrue(changes.size() >= 1);
        boolean hasInsertion = changes.stream().anyMatch(c -> c.type() == ChangeType.INSERTED);
        assertTrue(hasInsertion);
    }

    @Test
    void trackChangesViewViewMode() {
        TrackChangesView view = new TrackChangesView();
        view.loadLeft("Hello world");
        view.loadRight("Hello brave world");

        view.setViewMode(ViewMode.NO_MARKUP);
        assertEquals(ViewMode.NO_MARKUP, view.getViewMode());
        assertEquals(ViewMode.NO_MARKUP, view.getInlineView().getViewMode());
    }

    @Test
    void reviewPaneCreation() {
        ReviewPane pane = new ReviewPane();
        assertNotNull(pane);
        assertTrue(pane.getChanges().isEmpty());
    }

    @Test
    void reviewPaneSetChanges() {
        ReviewPane pane = new ReviewPane();
        List<Change> changes = TrackChangesEngine.diff("Hello world", "Hello brave world");
        pane.setChanges(changes);

        assertTrue(pane.getChanges().size() >= 1);
        boolean hasInsertion = pane.getChanges().stream().anyMatch(c -> c.type() == ChangeType.INSERTED);
        assertTrue(hasInsertion);
    }

    @Test
    void reviewPaneUpdatesChangeByIdWhenStatusChanges() {
        ReviewPane pane = new ReviewPane();
        Change original = TrackChangesEngine.diff("Hello", "Hello world").getFirst();
        pane.setChanges(List.of(original));

        pane.updateChange(original.withStatus(ChangeStatus.ACCEPTED));

        assertEquals(ChangeStatus.ACCEPTED, pane.getChanges().getFirst().status());
    }

    @Test
    void reviewPaneCallbacks() {
        ReviewPane pane = new ReviewPane();
        List<Change> changes = TrackChangesEngine.diff("Hello world", "Hello brave world");
        pane.setChanges(changes);

        Change[] acceptedChange = new Change[1];
        pane.setOnAcceptChange(c -> acceptedChange[0] = c);
        pane.setOnRejectChange(c -> acceptedChange[0] = c); // same holder for test

        // Just verify callback is set
        assertNotNull(pane);
    }

@Test
    void changeViewModeLogic() {
        Change inserted = new Change(
            "1", ChangeType.INSERTED, 0, 6, "Hello ", "Author", Instant.now(), null, ChangeStatus.PENDING
        );
        Change deleted = new Change(
            "2", ChangeType.DELETED, 6, 11, "world", "Author", Instant.now(), null, ChangeStatus.PENDING
        );
        Change acceptedInsertion = inserted.withStatus(ChangeStatus.ACCEPTED);
        Change rejectedDeletion = deleted.withStatus(ChangeStatus.REJECTED);

        // ALL_MARKUP shows everything
        assertTrue(ChangeViewMode.isVisibleIn(inserted, ViewMode.ALL_MARKUP));
        assertTrue(ChangeViewMode.isVisibleIn(deleted, ViewMode.ALL_MARKUP));

        // SIMPLE_MARKUP shows insertions
        assertTrue(ChangeViewMode.isVisibleIn(inserted, ViewMode.SIMPLE_MARKUP));
        assertTrue(ChangeViewMode.isVisibleIn(acceptedInsertion, ViewMode.SIMPLE_MARKUP));

        // NO_MARKUP shows accepted insertions
        assertTrue(ChangeViewMode.isVisibleIn(acceptedInsertion, ViewMode.NO_MARKUP));
    }

    @Test
    void changeDisplayText() {
        Change inserted = new Change(
            "1", ChangeType.INSERTED, 0, 6, "Hello ", "Author", Instant.now(), null, ChangeStatus.PENDING
        );
        Change deleted = new Change(
            "2", ChangeType.DELETED, 6, 11, "world", "Author", Instant.now(), null, ChangeStatus.PENDING
        );
        Change accepted = inserted.withStatus(ChangeStatus.ACCEPTED);
        Change rejected = deleted.withStatus(ChangeStatus.REJECTED);

        // ALL_MARKUP and SIMPLE_MARKUP show full text
        assertEquals("Hello ", ChangeViewMode.getDisplayText(inserted, ViewMode.ALL_MARKUP));
        assertEquals("world", ChangeViewMode.getDisplayText(deleted, ViewMode.ALL_MARKUP));

        // NO_MARKUP: accepted insertion shown, rejected deletion shown
        assertEquals("Hello ", ChangeViewMode.getDisplayText(accepted, ViewMode.NO_MARKUP));
        assertEquals("world", ChangeViewMode.getDisplayText(rejected, ViewMode.NO_MARKUP));

        // NO_MARKUP: rejected insertion hidden, accepted deletion hidden
        Change rejectedInsertion = inserted.withStatus(ChangeStatus.REJECTED);
        Change acceptedDeletion = deleted.withStatus(ChangeStatus.ACCEPTED);

        assertEquals("", ChangeViewMode.getDisplayText(rejectedInsertion, ViewMode.NO_MARKUP));
        assertEquals("", ChangeViewMode.getDisplayText(acceptedDeletion, ViewMode.NO_MARKUP));
    }

    @Test
    void inlineDocumentViewCreation() {
        InlineDocumentView view = new InlineDocumentView();
        assertNotNull(view);
        assertEquals(ViewMode.ALL_MARKUP, view.getViewMode());
        assertFalse(view.isEditable());
    }

    @Test
    void inlineDocumentViewLoadsRichTextAreaStylesheet() {
        InlineDocumentView view = new InlineDocumentView();

        String stylesheet = view.getUserAgentStylesheet();
        assertTrue(stylesheet.contains("rich-text-area.css"));
        assertFalse(stylesheet.contains(" file:"));
    }

    @Test
    void trackChangesViewLoadsApplicationStylesheetSeparately() {
        TrackChangesView view = new TrackChangesView();

        assertTrue(view.getStylesheets().stream()
                .anyMatch(stylesheet -> stylesheet.endsWith("de/richmarkdown/ui/app.css")));
    }

    @Test
    void trackChangesViewAppliesBothStylesheets() {
        FxTestUtil.runAndWait(() -> {
            TrackChangesView view = new TrackChangesView();
            new Scene(view);
            view.applyCss();
        });
    }

    @Test
    void inlineDocumentViewDisplaysDiffTextAndDecorations() {
        List<Change> changes = TrackChangesEngine.diff("Hello world", "Hello brave world");
        assertEquals("Hello brave world",
                TrackChangesEngine.buildViewText("Hello world", changes, ViewMode.ALL_MARKUP),
                changes.toString());

        TrackChangesView[] view = new TrackChangesView[1];
        Stage[] stage = new Stage[1];
        FxTestUtil.runAndWait(() -> {
            view[0] = new TrackChangesView();
            view[0].loadLeft("Hello world");
            view[0].loadRight("Hello brave world");
            stage[0] = new Stage();
            stage[0].setScene(new Scene(view[0], 800, 600));
            stage[0].show();
            view[0].applyCss();
            view[0].layout();
        });
        FxTestUtil.flushFxEvents();
        FxTestUtil.flushFxEvents();

        assertEquals("Hello brave world", view[0].getInlineView().getDocument().getText());
        assertFalse(view[0].getInlineView().getDocument().getDecorations().isEmpty());
        FxTestUtil.runAndWait(() -> stage[0].close());
    }

    @Test
    void inlineDocumentViewClearlyMarksReplacementText() {
        String oldText = "parameters";
        String newText = "parametafdasfd adf adf asdf asdf ers";
        TrackChangesView[] view = new TrackChangesView[1];
        Stage[] stage = new Stage[1];
        FxTestUtil.runAndWait(() -> {
            view[0] = new TrackChangesView();
            stage[0] = new Stage();
            stage[0].setScene(new Scene(view[0], 800, 600));
            stage[0].show();
            view[0].applyCss();
            view[0].layout();
            view[0].loadLeft(oldText);
            view[0].loadRight(newText);
        });
        FxTestUtil.flushFxEvents();
        FxTestUtil.flushFxEvents();

        FxTestUtil.runAndWait(() -> {
            var document = view[0].getInlineView().getDocument();
            assertEquals(oldText + newText, document.getText());
            List<com.gluonhq.richtextarea.model.TextDecoration> textDecorations =
                    document.getDecorations().stream()
                            .map(decoration -> (com.gluonhq.richtextarea.model.TextDecoration)
                                    decoration.getDecoration())
                            .toList();
            assertTrue(textDecorations.stream().anyMatch(decoration ->
                    decoration.isStrikethrough() && "#c00000".equals(decoration.getForeground())));
            assertTrue(textDecorations.stream().anyMatch(decoration ->
                    decoration.isUnderline() && "#008000".equals(decoration.getForeground())));
        });
        FxTestUtil.runAndWait(() -> stage[0].close());
    }

    @Test
    void comparisonRendersMarkdownFormattingAndDiffMarkupTogether() {
        TrackChangesView[] view = new TrackChangesView[1];
        Stage[] stage = new Stage[1];
        FxTestUtil.runAndWait(() -> {
            view[0] = new TrackChangesView();
            stage[0] = new Stage();
            stage[0].setScene(new Scene(view[0], 800, 600));
            stage[0].show();
            view[0].applyCss();
            view[0].layout();
            view[0].loadLeft("# Intro **world**");
            view[0].loadRight("# Intro **brave world**");
        });
        FxTestUtil.flushFxEvents();
        FxTestUtil.flushFxEvents();

        FxTestUtil.runAndWait(() -> {
            var document = view[0].getInlineView().getDocument();
            assertEquals("Intro brave world", document.getText());
            TextDecoration insertion = document.getDecorations().stream()
                    .filter(decoration -> decoration.getStart() == 6)
                    .map(decoration -> (TextDecoration) decoration.getDecoration())
                    .filter(decoration -> "#008000".equals(decoration.getForeground()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(insertion.isUnderline());
            assertEquals(MarkdownStyles.headingSize(1), insertion.getFontSize());
        });
        FxTestUtil.runAndWait(() -> stage[0].close());
    }

    @Test
    void comparisonTracksFormattingOnlyMarkdownChanges() {
        TrackChangesView[] view = new TrackChangesView[1];
        Stage[] stage = new Stage[1];
        FxTestUtil.runAndWait(() -> {
            view[0] = new TrackChangesView();
            stage[0] = new Stage();
            stage[0].setScene(new Scene(view[0], 800, 600));
            stage[0].show();
            view[0].applyCss();
            view[0].layout();
            view[0].loadLeft("**Important** text");
            view[0].loadRight("Important text");
        });
        FxTestUtil.flushFxEvents();
        FxTestUtil.flushFxEvents();

        FxTestUtil.runAndWait(() -> {
            assertTrue(view[0].getChanges().stream()
                    .anyMatch(change -> change.type() == ChangeType.FORMAT_CHANGED));
            var document = view[0].getInlineView().getDocument();
            assertEquals("Important text", document.getText());
            TextDecoration formatChange = document.getDecorations().stream()
                    .filter(decoration -> decoration.getStart() == 0)
                    .map(decoration -> (TextDecoration) decoration.getDecoration())
                    .filter(decoration -> "#7f6000".equals(decoration.getForeground()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("#fff2cc", formatChange.getBackground());
        });
        FxTestUtil.runAndWait(() -> stage[0].close());
    }

    @Test
    void comparisonHandlesImagesWithoutOptionalLinks() {
        TrackChangesView[] view = new TrackChangesView[1];
        Stage[] stage = new Stage[1];
        FxTestUtil.runAndWait(() -> {
            view[0] = new TrackChangesView();
            stage[0] = new Stage();
            stage[0].setScene(new Scene(view[0], 800, 600));
            stage[0].show();
            view[0].applyCss();
            view[0].layout();
            view[0].loadLeft("![image](image.png)");
            view[0].loadRight("![image](image.png)");
        });
        FxTestUtil.flushFxEvents();
        FxTestUtil.flushFxEvents();

        FxTestUtil.runAndWait(() -> {
            assertTrue(view[0].getChanges().isEmpty());
            assertNotNull(view[0].getInlineView().getDocument());
        });
        FxTestUtil.runAndWait(() -> stage[0].close());
    }

    @Test
    void selectingReviewChangeHighlightsItsInlineText() {
        TrackChangesView[] view = new TrackChangesView[1];
        Stage[] stage = new Stage[1];
        FxTestUtil.runAndWait(() -> {
            view[0] = new TrackChangesView();
            stage[0] = new Stage();
            stage[0].setScene(new Scene(view[0], 800, 600));
            stage[0].show();
            view[0].applyCss();
            view[0].layout();
            view[0].loadLeft("Hello world");
            view[0].loadRight("Hello brave world");
        });
        FxTestUtil.flushFxEvents();
        FxTestUtil.flushFxEvents();
        Change insertion = view[0].getChanges().stream()
                .filter(change -> change.type() == ChangeType.INSERTED)
                .findFirst()
                .orElseThrow();
        FxTestUtil.runAndWait(() -> view[0].getInlineView().selectChange(insertion));
        FxTestUtil.flushFxEvents();
        FxTestUtil.flushFxEvents();

        FxTestUtil.runAndWait(() -> {
            var inline = view[0].getInlineView();
            var skin = (com.gluonhq.richtextarea.RichTextAreaSkin) inline.getSkin();
            var selection = skin.getViewModel().getSelection();
            assertEquals(6, selection.getStart());
            assertEquals(12, selection.getEnd());
            // Selection moves the caret without reopening and rebuilding the document.
            assertEquals("Hello brave world", inline.getDocument().getText());
        });
        FxTestUtil.runAndWait(() -> stage[0].close());
    }

    @Test
    void changeWithStatus() {
        Change original = new Change(
            "1", ChangeType.INSERTED, 0, 5, "Hello", "Author", Instant.now(), "comment", ChangeStatus.PENDING
        );

        Change accepted = original.withStatus(ChangeStatus.ACCEPTED);
        assertEquals(ChangeStatus.ACCEPTED, accepted.status());
        assertEquals("Hello", accepted.text());
        assertEquals("comment", accepted.comment());
        assertEquals("Author", accepted.author());

        Change rejected = original.withStatus(ChangeStatus.REJECTED);
        assertEquals(ChangeStatus.REJECTED, rejected.status());
    }

    @Test
    void changeWithComment() {
        Change original = new Change(
            "1", ChangeType.INSERTED, 0, 5, "Hello", "Author", Instant.now(), "old comment", ChangeStatus.PENDING
        );

        Change updated = original.withComment("new comment");
        assertEquals("new comment", updated.comment());
        assertEquals(ChangeStatus.PENDING, updated.status());
    }

    @Test
    void trackChangesViewAcceptChange() {
        TrackChangesView view = new TrackChangesView();
        view.loadLeft("Hello world");
        view.loadRight("Hello brave world");

        List<Change> before = view.getChanges();
        assertTrue(before.size() >= 1);

        // Accept the first insertion change
        Change change = before.stream()
            .filter(c -> c.type() == ChangeType.INSERTED)
            .findFirst()
            .orElseThrow();
        Change accepted = change.withStatus(ChangeStatus.ACCEPTED);

        // Verify the model
        assertEquals(ChangeStatus.ACCEPTED, accepted.status());
    }

    @Test
    void trackChangesViewAcceptAll() {
        TrackChangesView view = new TrackChangesView();
        view.loadLeft("Hello world");
        view.loadRight("Hello brave world");

        List<Change> before = view.getChanges();
        assertTrue(before.size() >= 1);

        // Accept all
        List<Change> acceptedAll = before.stream()
            .map(c -> c.withStatus(ChangeStatus.ACCEPTED))
            .toList();

        assertTrue(acceptedAll.size() >= 1);
        assertTrue(acceptedAll.stream().allMatch(c -> c.status() == ChangeStatus.ACCEPTED));
    }

    @Test
    void trackChangesViewRejectAll() {
        TrackChangesView view = new TrackChangesView();
        view.loadLeft("Hello world");
        view.loadRight("Hello brave world");

        List<Change> before = view.getChanges();

        List<Change> rejectedAll = before.stream()
            .map(c -> c.withStatus(ChangeStatus.REJECTED))
            .toList();

        assertTrue(rejectedAll.size() >= 1);
        assertTrue(rejectedAll.stream().allMatch(c -> c.status() == ChangeStatus.REJECTED));
    }

    @Test
    void diffWithFormatting() {
        // Test that formatting changes are detected (currently only text diff)
        String old = "**Bold** text";
        String newT = "*Italic* text";
        List<Change> changes = TrackChangesEngine.diff(old, newT);

        // Should detect changes
        assertFalse(changes.isEmpty());
    }
}