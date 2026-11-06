package de.richmarkdown.features.editing;

import com.gluonhq.richtextarea.model.Document;
import com.gluonhq.richtextarea.model.TextDecoration;
import de.richmarkdown.markdown.MarkdownStyles;
import javafx.scene.text.FontWeight;
import java.util.*;

/** Chapter boundaries in exported document coordinates; descendants belong to their parent. */
public final class ChapterModel {
    private ChapterModel() { }
    public record Chapter(int level, String title, int start, int bodyStart, int end, int words) { }
    public static List<Chapter> chapters(Document document) {
        String text = document.getText();
        record Heading(int level, String title, int start, int bodyStart) { }
        var headings = new ArrayList<Heading>();
        int index = 0, start = 0;
        var decorations = document.getDecorations();
        while (start < text.length()) {
            int end = text.indexOf('\n', start);
            if (end < 0) end = text.length();
            while (index < decorations.size() && decorations.get(index).getStart() + decorations.get(index).getLength() <= start) index++;
            if (index < decorations.size() && end > start) {
                var d = decorations.get(index);
                if (d.getStart() <= start && d.getDecoration() instanceof TextDecoration td
                        && td.getFontWeight() == FontWeight.BOLD && MarkdownStyles.headingLevel(td.getFontSize()) > 0) {
                    headings.add(new Heading(MarkdownStyles.headingLevel(td.getFontSize()),
                            text.substring(start, end).trim(), start, Math.min(end + 1, text.length())));
                }
            }
            start = end + 1;
        }
        // Monotonic stack finds section ends in O(number of headings).
        int[] ends = new int[headings.size()]; Arrays.fill(ends, text.length());
        var stack = new ArrayDeque<Integer>();
        for (int i = 0; i < headings.size(); i++) {
            while (!stack.isEmpty() && headings.get(stack.peek()).level() >= headings.get(i).level()) ends[stack.pop()] = headings.get(i).start();
            stack.push(i);
        }
        // Prefix word counts avoid rescanning every nested chapter.
        int[] starts = headings.stream().mapToInt(Heading::start).toArray();
        int[] counts = new int[headings.size()];
        int count = 0, nextHeading = 0; boolean inWord = false;
        for (int i = 0; i < text.length();) {
            if (nextHeading < starts.length && i == starts[nextHeading]) counts[nextHeading++] = count;
            int codePoint = text.codePointAt(i);
            boolean word = Character.isLetterOrDigit(codePoint);
            if (word && !inWord) count++;
            inWord = word;
            i += Character.charCount(codePoint);
        }
        var result = new ArrayList<Chapter>();
        for (int i = 0; i < headings.size(); i++) {
            var h = headings.get(i);
            int endIndex = Arrays.binarySearch(starts, ends[i]);
            int endCount = endIndex < 0 ? count : counts[endIndex];
            result.add(new Chapter(h.level(), h.title(), h.start(), h.bodyStart(), ends[i], endCount - counts[i]));
        }
        return List.copyOf(result);
    }
    public static List<Chapter> filter(List<Chapter> chapters, String query, int maximumLevel) {
        String needle = Objects.requireNonNullElse(query, "").toLowerCase(Locale.ROOT);
        return chapters.stream().filter(c -> c.level() <= maximumLevel
                && c.title().toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
    public static Document moveSibling(Document document, int chapterStart, int direction) {
        if (direction == 0) return document;
        var chapters = chapters(document);
        int index = -1;
        for (int i = 0; i < chapters.size(); i++) if (chapters.get(i).start() == chapterStart) { index = i; break; }
        if (index < 0) return document;
        var selected = chapters.get(index);
        for (int i = index + Integer.signum(direction); i >= 0 && i < chapters.size(); i += Integer.signum(direction)) {
            var candidate = chapters.get(i);
            if (candidate.level() < selected.level()) break;
            if (candidate.level() == selected.level()) return DocumentEdits.moveRange(document, selected.start(), selected.end(),
                    direction < 0 ? candidate.start() : candidate.end());
        }
        return document;
    }
    /** Eligibility uses the same parent boundary as the actual move, without copying document text. */
    public static boolean canMoveSibling(List<Chapter> chapters, int chapterStart, int direction) {
        if (direction == 0) return false;
        int index = -1;
        for (int i = 0; i < chapters.size(); i++) if (chapters.get(i).start() == chapterStart) { index = i; break; }
        if (index < 0) return false;
        int level = chapters.get(index).level();
        for (int i = index + Integer.signum(direction); i >= 0 && i < chapters.size(); i += Integer.signum(direction)) {
            if (chapters.get(i).level() < level) break;
            if (chapters.get(i).level() == level) return true;
        }
        return false;
    }
}
