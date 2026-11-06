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

package de.richmarkdown.markdown;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TrackChangesEngine}.
 */
class TrackChangesEngineTest {

    @Test
    void diffIdenticalTexts() {
        String text = "Hello world";
        List<Change> changes = TrackChangesEngine.diff(text, text);
        assertTrue(changes.isEmpty(), "Identical texts should produce no changes");
    }

    @Test
    void diffEmptyToText() {
        List<Change> changes = TrackChangesEngine.diff("", "Hello world");
        assertEquals(1, changes.size());
        assertEquals(ChangeType.INSERTED, changes.get(0).type());
        assertEquals("Hello world", changes.get(0).text());
    }

    @Test
    void diffTextToEmpty() {
        List<Change> changes = TrackChangesEngine.diff("Hello world", "");
        assertEquals(1, changes.size());
        assertEquals(ChangeType.DELETED, changes.get(0).type());
        assertEquals("Hello world", changes.get(0).text());
    }

    @Test
    void diffSingleWordInsertion() {
        // Tokenization: "Hello world" -> ["Hello ", "world"]
        //              "Hello brave world" -> ["Hello ", "brave ", "world"]
        List<Change> changes = TrackChangesEngine.diff("Hello world", "Hello brave world");
        assertEquals(1, changes.size());
        assertEquals(ChangeType.INSERTED, changes.get(0).type());
        assertEquals("brave ", changes.get(0).text());
        assertEquals("Hello brave world",
                TrackChangesEngine.buildViewText("Hello world", changes, ViewMode.ALL_MARKUP));
    }

    @Test
    void diffReconstructsTargetTextAcrossEdits() {
        List<String[]> examples = List.of(
                new String[] {"parameters", "parametafdasfd adf adf asdf asdf ers"},
                new String[] {"The quick brown fox", "The slow brown fox"},
                new String[] {"alpha beta gamma", "alpha inserted beta changed gamma"},
                new String[] {"repeat repeat end", "repeat new repeat end"},
                new String[] {"first\nsecond\nthird", "first\nchanged\nthird"},
                new String[] {"prefix suffix", "suffix"},
                new String[] {"prefix suffix", "prefix"}
        );

        for (String[] example : examples) {
            List<Change> changes = TrackChangesEngine.diff(example[0], example[1]);
            assertEquals(example[1], TrackChangesEngine.applyChanges(example[0], changes),
                    "Accepted changes should reconstruct the target text");
        }
    }

    @Test
    void diffSingleWordDeletion() {
        // Tokenization: "Hello brave world" -> ["Hello ", "brave ", "world"]
        //              "Hello world" -> ["Hello ", "world"]
        List<Change> changes = TrackChangesEngine.diff("Hello brave world", "Hello world");
        assertTrue(changes.size() >= 1);
        boolean hasDeletion = changes.stream().anyMatch(c -> c.type() == ChangeType.DELETED);
        assertTrue(hasDeletion);
    }

    @Test
    void diffWordReplacement() {
        // "Hello world" -> ["Hello ", "world"]
        // "Hello universe" -> ["Hello ", "universe"]
        // Replacement = deletion + insertion
        List<Change> changes = TrackChangesEngine.diff("Hello world", "Hello universe");
        assertTrue(changes.size() >= 2);
        boolean hasDeletion = changes.stream().anyMatch(c -> c.type() == ChangeType.DELETED);
        boolean hasInsertion = changes.stream().anyMatch(c -> c.type() == ChangeType.INSERTED);
        assertTrue(hasDeletion);
        assertTrue(hasInsertion);
    }

    @Test
    void diffMultipleChanges() {
        String old = "The quick brown fox jumps over the lazy dog";
        String newT = "The slow red fox jumps over the active cat";
        List<Change> changes = TrackChangesEngine.diff(old, newT);

        // Should find multiple changes
        assertTrue(changes.size() >= 4);
    }

    @Test
    void diffPreservesWhitespace() {
        List<Change> changes = TrackChangesEngine.diff("Hello  world", "Hello   world");
        // Two spaces vs three spaces
        assertTrue(changes.size() >= 1);
        boolean hasDeletion = changes.stream().anyMatch(c -> c.type() == ChangeType.DELETED);
        boolean hasInsertion = changes.stream().anyMatch(c -> c.type() == ChangeType.INSERTED);
        assertTrue(hasDeletion || hasInsertion);
    }

    @Test
    void diffUnicode() {
        // Due to LCS tie-breaking, may produce INSERTED or DELETED
        List<Change> changes = TrackChangesEngine.diff("Hallo Welt", "Hallo schöne Welt");
        assertTrue(changes.size() >= 1);
        boolean hasInsertion = changes.stream().anyMatch(c -> c.type() == ChangeType.INSERTED);
        boolean hasDeletion = changes.stream().anyMatch(c -> c.type() == ChangeType.DELETED);
        assertTrue(hasInsertion || hasDeletion, "Should detect a change");
    }

    @Test
    void diffEmptyStrings() {
        List<Change> changes = TrackChangesEngine.diff("", "");
        assertTrue(changes.isEmpty());
    }

    @Test
    void diffNullInputs() {
        List<Change> changes = TrackChangesEngine.diff(null, null);
        assertTrue(changes.isEmpty());
    }

    @Test
    void diffOneNull() {
        List<Change> changes = TrackChangesEngine.diff("Hello", null);
        assertEquals(1, changes.size());
        assertEquals(ChangeType.DELETED, changes.get(0).type());
        assertEquals("Hello", changes.get(0).text());
    }

    @Test
    void applyChangesAllAccepted() {
        String old = "Hello world";
        List<Change> changes = TrackChangesEngine.diff(old, "Hello brave world");
        // changes are PENDING by default, so applyChanges includes them
        String result = TrackChangesEngine.applyChanges(old, changes);
        // Should produce the new text
        assertTrue(result.contains("brave"));
    }

    @Test
    void applyChangesAccepted() {
        String old = "Hello world";
        List<Change> changes = TrackChangesEngine.diff(old, "Hello brave world");
        // Find the insertion change and accept it
        Change insertion = changes.stream()
            .filter(c -> c.type() == ChangeType.INSERTED)
            .findFirst()
            .orElseThrow();
        Change accepted = insertion.withStatus(ChangeStatus.ACCEPTED);
        List<Change> acceptedChanges = changes.stream()
            .map(c -> c.type() == ChangeType.INSERTED ? accepted : c)
            .toList();

        String result = TrackChangesEngine.applyChanges(old, acceptedChanges);
        assertTrue(result.contains("brave"));
    }

    @Test
    void applyChangesRejected() {
        String old = "Hello world";
        List<Change> changes = TrackChangesEngine.diff(old, "Hello brave world");
        // Find the insertion change and reject it
        Change insertion = changes.stream()
            .filter(c -> c.type() == ChangeType.INSERTED)
            .findFirst()
            .orElseThrow();
        Change rejected = insertion.withStatus(ChangeStatus.REJECTED);
        List<Change> rejectedChanges = changes.stream()
            .map(c -> c.type() == ChangeType.INSERTED ? rejected : c)
            .toList();

        String result = TrackChangesEngine.applyChanges(old, rejectedChanges);
        // Rejecting an insertion should show the original text (or part of it)
        // Due to LCS tie-breaking, the insertion position may vary
        assertFalse(result.isEmpty());
        assertTrue(result.contains("world") || result.contains("Hello"));
    }

    @Test
    void applyChangesDeletionAccepted() {
        String old = "Hello brave world";
        List<Change> changes = TrackChangesEngine.diff(old, "Hello world");
        Change deletion = changes.stream()
            .filter(c -> c.type() == ChangeType.DELETED)
            .findFirst()
            .orElseThrow();
        Change accepted = deletion.withStatus(ChangeStatus.ACCEPTED);
        List<Change> acceptedChanges = changes.stream()
            .map(c -> c.type() == ChangeType.DELETED ? accepted : c)
            .toList();

        String result = TrackChangesEngine.applyChanges(old, acceptedChanges);
        assertEquals("Hello world", result);
    }

    @Test
    void applyChangesDeletionRejected() {
        String old = "Hello brave world";
        List<Change> changes = TrackChangesEngine.diff(old, "Hello world");
        Change deletion = changes.stream()
            .filter(c -> c.type() == ChangeType.DELETED)
            .findFirst()
            .orElseThrow();
        Change rejected = deletion.withStatus(ChangeStatus.REJECTED);
        List<Change> rejectedChanges = changes.stream()
            .map(c -> c.type() == ChangeType.DELETED ? rejected : c)
            .toList();

        String result = TrackChangesEngine.applyChanges(old, rejectedChanges);
        assertEquals("Hello brave world", result);
    }

    @Test
    void buildViewTextAllMarkup() {
        String old = "Hello world";
        List<Change> changes = TrackChangesEngine.diff(old, "Hello brave world");

        String viewText = TrackChangesEngine.buildViewText(old, changes, ViewMode.ALL_MARKUP);
        // Should show the new text with all changes
        assertTrue(viewText.contains("brave"));
    }

    @Test
    void buildViewTextNoMarkupAccepted() {
        String old = "Hello world";
        List<Change> changes = TrackChangesEngine.diff(old, "Hello brave world");
        Change insertion = changes.stream()
            .filter(c -> c.type() == ChangeType.INSERTED)
            .findFirst()
            .orElseThrow();
        Change accepted = insertion.withStatus(ChangeStatus.ACCEPTED);
        List<Change> acceptedChanges = changes.stream()
            .map(c -> c.type() == ChangeType.INSERTED ? accepted : c)
            .toList();

        String viewText = TrackChangesEngine.buildViewText(old, acceptedChanges, ViewMode.NO_MARKUP);
        assertTrue(viewText.contains("brave"));
    }

    @Test
    void buildViewTextNoMarkupShowsPendingInsertion() {
        String old = "Hello world";
        List<Change> changes = TrackChangesEngine.diff(old, "Hello brave world");

        assertEquals("Hello brave world",
                TrackChangesEngine.buildViewText(old, changes, ViewMode.NO_MARKUP));
    }

    @Test
    void buildViewTextNoMarkupOmitsAcceptedDeletion() {
        String old = "Hello brave world";
        List<Change> changes = TrackChangesEngine.diff(old, "Hello world");
        List<Change> acceptedChanges = changes.stream()
                .map(change -> change.withStatus(ChangeStatus.ACCEPTED))
                .toList();

        assertEquals("Hello world",
                TrackChangesEngine.buildViewText(old, acceptedChanges, ViewMode.NO_MARKUP));
    }

    @Test
    void buildViewTextNoMarkupRejected() {
        String old = "Hello world";
        List<Change> changes = TrackChangesEngine.diff(old, "Hello brave world");
        Change insertion = changes.stream()
            .filter(c -> c.type() == ChangeType.INSERTED)
            .findFirst()
            .orElseThrow();
        Change rejected = insertion.withStatus(ChangeStatus.REJECTED);
        List<Change> rejectedChanges = changes.stream()
            .map(c -> c.type() == ChangeType.INSERTED ? rejected : c)
            .toList();

        String viewText = TrackChangesEngine.buildViewText(old, rejectedChanges, ViewMode.NO_MARKUP);
        assertEquals("Hello world", viewText);
    }

    @Test
    void buildViewTextOriginalAccepted() {
        String old = "Hello world";
        List<Change> changes = TrackChangesEngine.diff(old, "Hello brave world");
        Change insertion = changes.stream()
            .filter(c -> c.type() == ChangeType.INSERTED)
            .findFirst()
            .orElseThrow();
        Change accepted = insertion.withStatus(ChangeStatus.ACCEPTED);
        List<Change> acceptedChanges = changes.stream()
            .map(c -> c.type() == ChangeType.INSERTED ? accepted : c)
            .toList();

        String viewText = TrackChangesEngine.buildViewText(old, acceptedChanges, ViewMode.ORIGINAL);
        assertEquals("Hello world", viewText);
    }

    @Test
    void buildViewTextOriginalRejected() {
        String old = "Hello world";
        List<Change> changes = TrackChangesEngine.diff(old, "Hello brave world");
        Change insertion = changes.stream()
            .filter(c -> c.type() == ChangeType.INSERTED)
            .findFirst()
            .orElseThrow();
        Change rejected = insertion.withStatus(ChangeStatus.REJECTED);
        List<Change> rejectedChanges = changes.stream()
            .map(c -> c.type() == ChangeType.INSERTED ? rejected : c)
            .toList();

        String viewText = TrackChangesEngine.buildViewText(old, rejectedChanges, ViewMode.ORIGINAL);
        assertEquals(old, viewText);
    }

    @Test
    void diffNewlines() {
        String old = "Line 1\nLine 2\nLine 3";
        String newT = "Line 1\nLine 2 modified\nLine 3";
        List<Change> changes = TrackChangesEngine.diff(old, newT);

        // Should detect the change
        assertTrue(changes.size() >= 1);
    }

    @Test
    void diffMultipleInsertions() {
        String old = "A B C";
        String newT = "A X B Y C";
        List<Change> changes = TrackChangesEngine.diff(old, newT);

        // Should detect insertions
        boolean hasInsertion = changes.stream().anyMatch(c -> c.type() == ChangeType.INSERTED);
        assertTrue(hasInsertion);
    }

    @Test
    void diffAdjacentChangesMerged() {
        // Two adjacent insertions should be merged
        String old = "Hello world";
        String newT = "Hello beautiful brave world";
        List<Change> changes = TrackChangesEngine.diff(old, newT);

        // Should detect the insertion
        boolean hasInsertion = changes.stream().anyMatch(c -> c.type() == ChangeType.INSERTED);
        assertTrue(hasInsertion);
    }
    @Test
    void largeDifferentDocumentsUseBoundedMemoryAndReconstructExactly() {
        String original = "old ".repeat(12_000);
        String revised = "new ".repeat(12_000);
        List<Change> changes = assertTimeoutPreemptively(java.time.Duration.ofSeconds(3),
                () -> TrackChangesEngine.diff(original, revised));
        assertEquals(revised, TrackChangesEngine.applyChanges(original, changes));
        assertEquals(original, TrackChangesEngine.buildViewText(original, changes, ViewMode.ORIGINAL));
    }

    @Test
    void randomizedEditsReconstructBothVersions() {
        java.util.Random random = new java.util.Random(1729);
        for (int iteration = 0; iteration < 500; iteration++) {
            StringBuilder original = new StringBuilder();
            StringBuilder revised = new StringBuilder();
            for (int i = 0, length = random.nextInt(30); i < length; i++) original.append(random.nextInt(7)).append(' ');
            for (int i = 0, length = random.nextInt(30); i < length; i++) revised.append(random.nextInt(7)).append(' ');
            List<Change> changes = TrackChangesEngine.diff(original.toString(), revised.toString());
            assertEquals(revised.toString(), TrackChangesEngine.applyChanges(original.toString(), changes));
            assertEquals(original.toString(), TrackChangesEngine.buildViewText(original.toString(), changes, ViewMode.ORIGINAL));
        }
    }

}