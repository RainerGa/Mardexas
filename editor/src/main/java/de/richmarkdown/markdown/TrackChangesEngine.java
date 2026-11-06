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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Word-level diff engine using LCS (Longest Common Subsequence) algorithm
 * with robust character position tracking.
 * Produces a list of {@link Change} objects with word-level granularity.
 */
public final class TrackChangesEngine {

    // Split text into words (sequences of non-whitespace + optional trailing whitespace)
    // or standalone whitespace
    private static final Pattern WORD_PATTERN = Pattern.compile("\\S+\\s*|\\s+");
    private static final String DEFAULT_AUTHOR = "Author";

    private TrackChangesEngine() {
    }

    /**
     * Computes the word-level diff between two texts and returns a list of changes.
     *
     * @param oldText the original text
     * @param newText the revised text
     * @return list of changes (never null)
     */
    public static List<Change> diff(String oldText, String newText) {
        String oldT = oldText == null ? "" : oldText;
        String newT = newText == null ? "" : newText;

        if (oldT.equals(newT)) {
            return List.of();
        }

        if (oldT.isEmpty()) {
            if (newT.isEmpty()) {
                return List.of();
            }
            return List.of(new Change(
                null, ChangeType.INSERTED, 0, 0, newT, DEFAULT_AUTHOR, Instant.now(), null, ChangeStatus.PENDING
            ));
        }

        if (newT.isEmpty()) {
            return List.of(new Change(
                null, ChangeType.DELETED, 0, oldT.length(), oldT, DEFAULT_AUTHOR, Instant.now(), null, ChangeStatus.PENDING
            ));
        }

        List<String> oldWords = splitWords(oldT);
        List<String> newWords = splitWords(newT);

        // Compute LCS and derive edit script
        List<EditOp> editScript = computeEditScript(oldWords, newWords);
        
        // Convert edit script to Changes with correct character positions
        return editScriptToChanges(editScript, oldWords, newWords, oldT);
    }

    /**
     * Splits text into words (sequences of non-whitespace + optional trailing whitespace)
     * or standalone whitespace.
     */
    private static List<String> splitWords(String text) {
        List<String> words = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(text);
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
    }

    /**
     * Represents an edit operation in the diff.
     */
    private enum EditType {
        EQUAL, INSERT, DELETE
    }

    private record EditOp(EditType type, int oldIndex, int newIndex, int length) {
    }

    /**
     * Computes the edit script using LCS (Longest Common Subsequence).
     * Returns a list of operations: EQUAL (matching), INSERT (in new only), DELETE (in old only).
     */
    private static List<EditOp> computeEditScript(List<String> oldWords, List<String> newWords) {
        int n = oldWords.size();
        int m = newWords.size();

        List<EditOp> result = new ArrayList<>();
        buildScript(oldWords, 0, n, newWords, 0, m, result);
        return result;
    }

    // Hirschberg: linear memory. Very large unmatched regions use an exact,
    // coarser replacement rather than an unbounded quadratic computation.
    private static void buildScript(List<String> oldWords, int from, int to,
                                    List<String> newWords, int start, int end, List<EditOp> result) {
        if (Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException();
        }
        while (from < to && start < end && oldWords.get(from).equals(newWords.get(start))) {
            result.add(new EditOp(EditType.EQUAL, from++, start++, 1));
        }
        int suffix = 0;
        while (from < to - suffix && start < end - suffix
                && oldWords.get(to - suffix - 1).equals(newWords.get(end - suffix - 1))) {
            suffix++;
        }
        int oldEnd = to - suffix;
        int newEnd = end - suffix;
        int n = oldEnd - from;
        int m = newEnd - start;
        if (n == 0 || m == 0 || (long) n * m > 20_000_000L) {
            if (n > 0) result.add(new EditOp(EditType.DELETE, from, start, n));
            if (m > 0) result.add(new EditOp(EditType.INSERT, oldEnd, start, m));
        } else if (n == 1) {
            int match = -1;
            for (int j = start; j < newEnd; j++) {
                if (oldWords.get(from).equals(newWords.get(j))) { match = j; break; }
            }
            if (match < 0) {
                result.add(new EditOp(EditType.DELETE, from, start, 1));
                result.add(new EditOp(EditType.INSERT, oldEnd, start, m));
            } else {
                if (match > start) result.add(new EditOp(EditType.INSERT, from, start, match - start));
                result.add(new EditOp(EditType.EQUAL, from, match, 1));
                if (match + 1 < newEnd) result.add(new EditOp(EditType.INSERT, oldEnd, match + 1, newEnd - match - 1));
            }
        } else {
            int middle = from + n / 2;
            int split = findSplit(oldWords, from, middle, oldEnd, newWords, start, newEnd);
            buildScript(oldWords, from, middle, newWords, start, split, result);
            buildScript(oldWords, middle, oldEnd, newWords, split, newEnd, result);
        }
        if (suffix > 0) result.add(new EditOp(EditType.EQUAL, oldEnd, newEnd, suffix));
    }

    private static int findSplit(List<String> oldWords, int from, int middle, int to,
                                 List<String> newWords, int start, int end) {
        int[] forward = lcsRow(oldWords, from, middle, newWords, start, end, false);
        int[] backward = lcsRow(oldWords, middle, to, newWords, start, end, true);
        int m = end - start;
        int split = 0;
        int best = -1;
        for (int j = 0; j <= m; j++) {
            int length = forward[j] + backward[m - j];
            if (length > best) { best = length; split = j; }
        }
        return start + split;
    }

    private static int[] lcsRow(List<String> oldWords, int from, int to,
                                List<String> newWords, int start, int end, boolean reverse) {
        int[] row = new int[end - start + 1];
        for (int i = 0; i < to - from; i++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException();
            }
            int diagonal = 0;
            String word = oldWords.get(reverse ? to - i - 1 : from + i);
            for (int j = 1; j < row.length; j++) {
                int previous = row[j];
                String other = newWords.get(reverse ? end - j : start + j - 1);
                row[j] = word.equals(other) ? diagonal + 1 : Math.max(row[j], row[j - 1]);
                diagonal = previous;
            }
        }
        return row;
    }

    /**
     * Converts edit script to Changes with correct character positions.
     */
    private static List<Change> editScriptToChanges(List<EditOp> editScript,
            List<String> oldWords, List<String> newWords, String oldText) {
        List<Change> changes = new ArrayList<>();
        String author = DEFAULT_AUTHOR;
        Instant now = Instant.now();

        int oldWordIdx = 0;
        int newWordIdx = 0;
        int oldCharPos = 0;

        for (EditOp op : editScript) {
            switch (op.type()) {
                case EQUAL -> {
                    // Matching words: advance both indices and character position
                    for (int k = 0; k < op.length(); k++) {
                        if (oldWordIdx < oldWords.size()) {
                            oldCharPos += oldWords.get(oldWordIdx).length();
                        }
                        oldWordIdx++;
                        newWordIdx++;
                    }
                }
                case DELETE -> {
                    // Deleted words in old
                    int delStart = oldCharPos;
                    StringBuilder deletedText = new StringBuilder();
                    for (int k = 0; k < op.length(); k++) {
                        if (oldWordIdx < oldWords.size()) {
                            deletedText.append(oldWords.get(oldWordIdx));
                            oldCharPos += oldWords.get(oldWordIdx).length();
                            oldWordIdx++;
                        }
                    }
                    int delEnd = oldCharPos;
                    if (delStart != delEnd) {
                        changes.add(new Change(
                            null, ChangeType.DELETED, delStart, delEnd, deletedText.toString(),
                            author, now, null, ChangeStatus.PENDING
                        ));
                    }
                }
                case INSERT -> {
                    // Inserted words in new
                    int insPos = oldCharPos;
                    StringBuilder insertedText = new StringBuilder();
                    for (int k = 0; k < op.length(); k++) {
                        if (newWordIdx < newWords.size()) {
                            insertedText.append(newWords.get(newWordIdx));
                            newWordIdx++;
                        }
                    }
                    if (!insertedText.isEmpty()) {
                        changes.add(new Change(
                            null, ChangeType.INSERTED, insPos, insPos, insertedText.toString(),
                            author, now, null, ChangeStatus.PENDING
                        ));
                    }
                }
            }
        }

        return mergeAdjacentChanges(changes);
    }

    /**
     * Merges adjacent changes of the same type at the same position.
     */
    private static List<Change> mergeAdjacentChanges(List<Change> changes) {
        if (changes.size() <= 1) {
            return changes;
        }
        List<Change> merged = new ArrayList<>();
        Change current = changes.get(0);
        for (int i = 1; i < changes.size(); i++) {
            Change next = changes.get(i);
            boolean canMerge = false;

            if (current.type() == next.type() && current.author().equals(next.author())) {
                if (current.type() == ChangeType.INSERTED) {
                    canMerge = current.endOffset() == next.startOffset();
                } else if (current.type() == ChangeType.DELETED) {
                    canMerge = current.endOffset() == next.startOffset();
                }
            }

            if (canMerge) {
                current = new Change(
                    current.id(),
                    current.type(),
                    current.startOffset(),
                    next.endOffset(),
                    current.text() + next.text(),
                    current.author(),
                    current.timestamp(),
                    current.comment(),
                    current.status()
                );
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    /**
     * Reconstructs the merged document text from old text and changes.
     */
    public static String applyChanges(String oldText, List<Change> changes) {
        StringBuilder result = new StringBuilder();
        String oldT = oldText == null ? "" : oldText;
        int oldIdx = 0;

        List<Change> sortedChanges = new ArrayList<>(changes);
        sortedChanges.sort((a, b) -> Integer.compare(a.startOffset(), b.startOffset()));

        for (Change change : sortedChanges) {
            if (change.startOffset() > oldIdx) {
                result.append(oldT, oldIdx, change.startOffset());
                oldIdx = change.startOffset();
            }

            switch (change.type()) {
                case INSERTED -> {
                    if (change.status() == ChangeStatus.ACCEPTED || change.status() == ChangeStatus.PENDING) {
                        result.append(change.text());
                    }
                }
                case DELETED -> {
                    if (change.status() == ChangeStatus.REJECTED) {
                        result.append(change.text());
                    }
                    oldIdx = change.endOffset();
                }
                case FORMAT_CHANGED -> {
                    result.append(change.text());
                    oldIdx = change.endOffset();
                }
            }
        }

        if (oldIdx < oldT.length()) {
            result.append(oldT.substring(oldIdx));
        }

        return result.toString();
    }

    /**
     * Builds the text for a specific view mode.
     */
    public static String buildViewText(String oldText, List<Change> changes, ViewMode mode) {
        StringBuilder result = new StringBuilder();
        String oldT = oldText == null ? "" : oldText;
        int oldIdx = 0;

        List<Change> sortedChanges = new ArrayList<>(changes);
        sortedChanges.sort((a, b) -> Integer.compare(a.startOffset(), b.startOffset()));

        for (Change change : sortedChanges) {
            int changeStart = Math.max(oldIdx, Math.min(oldT.length(), change.startOffset()));
            if (changeStart > oldIdx) {
                result.append(oldT, oldIdx, changeStart);
                oldIdx = changeStart;
            }

            String displayText = ChangeViewMode.getDisplayText(change, mode);
            if (!displayText.isEmpty()) {
                result.append(displayText);
            }

            if (change.type() == ChangeType.DELETED || change.type() == ChangeType.FORMAT_CHANGED) {
                oldIdx = Math.max(oldIdx, Math.min(oldT.length(), change.endOffset()));
            }
        }

        if (oldIdx < oldT.length()) {
            result.append(oldT.substring(oldIdx));
        }

        return result.toString();
    }
}