package de.richmarkdown.features.editing;

import com.gluonhq.richtextarea.model.*;
import de.richmarkdown.markdown.MarkdownStyles;
import javafx.scene.text.FontWeight;
import java.util.*;
import java.util.function.UnaryOperator;

/** Pure edits on exported document coordinates. Never serializes through Markdown. */
public final class DocumentEdits {
    private DocumentEdits() { }
    public record Range(int start, int end) {
        public Range { if (start < 0 || end < start) throw new IllegalArgumentException("Invalid range"); }
    }
    public enum Transform { UPPER, LOWER, SENTENCE, SORT_LINES, UNIQUE_LINES }

    public static Range paragraph(String text, int caret) {
        Objects.checkIndex(caret, text.length() + 1);
        int start = text.lastIndexOf('\n', caret - 1) + 1;
        int end = text.indexOf('\n', caret);
        return new Range(start, end < 0 ? text.length() : end + 1);
    }

    public static Document replace(Document source, int start, int end, Document insertion) {
        check(source, start, end);
        return join(source, List.of(slice(source, 0, start), insertion,
                slice(source, end, source.getText().length())), start + insertion.getText().length());
    }

    public static Document slice(Document source, int start, int end) {
        check(source, start, end);
        var models = new ArrayList<DecorationModel>();
        var sourceModels = source.getDecorations();
        int low = 0, high = sourceModels.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            var model = sourceModels.get(middle);
            if (model.getStart() + model.getLength() <= start) low = middle + 1;
            else high = middle;
        }
        for (int i = low; i < sourceModels.size(); i++) {
            var model = sourceModels.get(i);
            if (model.getStart() >= end) break;
            int a = Math.max(start, model.getStart());
            int b = Math.min(end, model.getStart() + model.getLength());
            if (a < b) models.add(new DecorationModel(a - start, b - a,
                    model.getDecoration(), model.getParagraphDecoration()));
        }
        return new Document(source.getText().substring(start, end), List.copyOf(models), 0,
                source.getPageFormat(), source.getHeadersFooters());
    }

    public static Document join(Document original, List<Document> parts, int caret) {
        var text = new StringBuilder();
        var models = new ArrayList<DecorationModel>();
        for (Document part : parts) {
            int offset = text.length();
            text.append(part.getText());
            for (var model : part.getDecorations()) {
                if (model.getLength() > 0) models.add(new DecorationModel(offset + model.getStart(),
                        model.getLength(), model.getDecoration(), model.getParagraphDecoration()));
            }
        }
        if (models.isEmpty()) models.add(DecorationModel.createDefaultDecorationModel(text.length()));
        return new Document(text.toString(), List.copyOf(models), Math.min(caret, text.length()),
                original.getPageFormat(), original.getHeadersFooters());
    }

    public static Document transform(Document source, int start, int end, Transform operation) {
        check(source, start, end);
        if (start == end) return source;
        if (operation == Transform.SORT_LINES || operation == Transform.UNIQUE_LINES) {
            if (!EditingContext.reorderLines(source, new Range(start, end)))
                throw new IllegalArgumentException("Please select complete ordinary paragraphs or lines within one code block.");
            var lines = lines(slice(source, start, end));
            if (operation == Transform.SORT_LINES) lines.sort(Comparator.comparing(Document::getText));
            else {
                var seen = new HashSet<String>();
                lines.removeIf(line -> !seen.add(line.getText()));
            }
            return replace(source, start, end, combineLines(source, lines,
                    source.getText().charAt(end - 1) == '\n'));
        }
        UnaryOperator<String> fn = caseFunction(operation);
        var parts = new ArrayList<Document>();
        // Process decoration runs, keeping memory proportional to runs rather than text length.
        for (var d : slice(source, start, end).getDecorations()) {
            String original = source.getText().substring(start + d.getStart(), start + d.getStart() + d.getLength());
            String text = convertedText(original, d.getDecoration(), operation, fn);
            parts.add(new Document(text, List.of(new DecorationModel(0, text.length(), d.getDecoration(), d.getParagraphDecoration())), 0));
        }
        return replace(source, start, end, join(source, parts, 0));
    }

    private static UnaryOperator<String> caseFunction(Transform operation) {
        final boolean[] sentence = {true};
        return switch (operation) {
            case UPPER -> value -> value.toUpperCase(Locale.ROOT);
            case LOWER -> value -> value.toLowerCase(Locale.ROOT);
            default -> value -> {
                if (value.codePoints().anyMatch(Character::isLetter)) {
                    String result = sentence[0] ? value.toUpperCase(Locale.ROOT) : value.toLowerCase(Locale.ROOT);
                    sentence[0] = false;
                    return result;
                }
                if (value.equals(".") || value.equals("!") || value.equals("?")) sentence[0] = true;
                return value;
            };
        };
    }

    private static String convertedText(String original, Decoration decoration, Transform operation, UnaryOperator<String> fn) {
        if (decoration instanceof ImageDecoration) return original;
        if (operation != Transform.SENTENCE) return fn.apply(original);
        var builder = new StringBuilder();
        original.codePoints().forEach(cp -> builder.append(fn.apply(new String(Character.toChars(cp)))));
        return builder.toString();
    }

    /** Maps a position through a case conversion without copying the surrounding document. */
    static int convertedPosition(Document source, Range range, Transform operation, int position) {
        int limit = Math.max(range.start(), Math.min(position, range.end()));
        var fn = caseFunction(operation);
        int length = range.start();
        for (var run : source.getDecorations()) {
            if (run.getStart() >= limit) break;
            int a = Math.max(range.start(), run.getStart());
            int b = Math.min(limit, run.getStart() + run.getLength());
            if (a < b) length += convertedText(source.getText().substring(a, b), run.getDecoration(), operation, fn).length();
        }
        return position < range.start() ? position : length + Math.max(0, position - range.end());
    }

    /** Keep a caret on its original line after sorting or removing duplicate lines. */
    static int linePosition(Document source, Document result, Range range, Transform operation, int position) {
        int delta = result.getText().length() - source.getText().length();
        if (position <= range.start()) return position;
        if (position >= range.end()) return position + delta;
        String selected = source.getText().substring(range.start(), range.end());
        String[] lines = selected.split("\n", -1);
        int count = lines.length - (selected.endsWith("\n") ? 1 : 0);
        var order = new ArrayList<Integer>();
        var seen = new HashSet<String>();
        int offset = range.start(), chosen = 0, column = 0;
        for (int i = 0; i < count; i++) {
            if (offset <= position) { chosen = i; column = position - offset; }
            offset += lines[i].length() + 1;
            if (operation == Transform.SORT_LINES || seen.add(lines[i])) order.add(i);
        }
        if (operation == Transform.SORT_LINES) order.sort(Comparator.comparing(i -> lines[i]));
        offset = range.start();
        for (int index : order) {
            if (index == chosen || operation == Transform.UNIQUE_LINES && lines[index].equals(lines[chosen]))
                return offset + Math.min(column, lines[index].length());
            offset += lines[index].length() + 1;
        }
        return Math.min(position, range.end() + delta);
    }

    public static Document clear(Document source, int start, int end, boolean linksOnly, boolean paragraphToo) {
        Document selected = slice(source, start, end);
        if (paragraphToo && EditingContext.hasTable(source, new Range(start, end)))
            throw new IllegalArgumentException("Tabellen können nicht als Absatz umformatiert werden. Bitte zuerst die Tabelle in Text umwandeln.");
        var models = selected.getDecorations().stream().map(d -> {
            Decoration decoration = d.getDecoration();
            if (decoration instanceof TextDecoration text) decoration = linksOnly
                    ? TextDecoration.builder().fromDecoration(text).url("").build()
                    : clearedTextStyle(text, paragraphToo);
            return new DecorationModel(d.getStart(), d.getLength(), decoration,
                    paragraphToo ? ParagraphDecoration.builder().presets().build() : d.getParagraphDecoration());
        }).toList();
        return replace(source, start, end, new Document(selected.getText(), models, 0));
    }

    public static Document duplicateParagraph(Document source, int caret) {
        Range range = paragraph(source.getText(), caret);
        if (!EditingContext.ordinaryParagraph(source, range))
            throw new IllegalArgumentException("Only ordinary paragraphs can be duplicated with this command.");
        if (source.getText().isEmpty()) return source;
        Document part = slice(source, range.start(), range.end());
        if (!part.getText().endsWith("\n")) part = join(source, List.of(new Document("\n"), part), 0);
        return replace(source, range.end(), range.end(), part);
    }

    public static Document moveParagraph(Document source, int caret, int direction) {
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Direction must be -1 or 1");
        Range selected = paragraph(source.getText(), caret);
        if (!EditingContext.ordinaryParagraph(source, selected))
            throw new IllegalArgumentException("Only ordinary paragraphs can be moved with this command.");
        if (direction < 0 && selected.start() == 0 || direction > 0 && (selected.start() == source.getText().length()
                || selected.end() == source.getText().length() && !source.getText().endsWith("\n"))) return source;
        Range adjacent = paragraph(source.getText(), direction < 0 ? selected.start() - 1 : selected.end());
        if (!EditingContext.ordinaryParagraph(source, adjacent))
            throw new IllegalArgumentException("Paragraphs cannot be moved into lists, tables, headings, images, quotes or code blocks.");
        int pairStart = Math.min(selected.start(), adjacent.start()), pairEnd = Math.max(selected.end(), adjacent.end());
        Range firstRange = direction < 0 ? adjacent : selected, secondRange = direction < 0 ? selected : adjacent;
        Document first = slice(source, firstRange.start(), firstRange.end());
        Document second = slice(source, secondRange.start(), secondRange.end());
        Document secondLine = lineWithSeparator(source, second);
        Document firstBody = lineBody(first);
        var parts = new ArrayList<Document>();
        parts.add(secondLine); parts.add(firstBody);
        if (second.getText().endsWith("\n")) parts.add(lineSeparator(first));
        int destination = direction < 0 ? pairStart : pairStart + secondLine.getText().length();
        // Only construct the two affected paragraphs; do not split a large
        // document into thousands of temporary Document objects for one move.
        Document result = replace(source, pairStart, pairEnd, join(source, parts, 0));
        return new Document(result.getText(), result.getDecorations(), destination,
                source.getPageFormat(), source.getHeadersFooters());
    }

    private static Document lineBody(Document document) {
        return document.getText().endsWith("\n") ? slice(document, 0, document.getText().length() - 1) : document;
    }
    private static Document lineWithSeparator(Document source, Document document) {
        return document.getText().endsWith("\n") ? document : join(source, List.of(document, lineSeparator(document)), 0);
    }
    private static Document lineSeparator(Document document) {
        if (document.getText().endsWith("\n")) return slice(document, document.getText().length() - 1, document.getText().length());
        var run = document.getDecorations().isEmpty() ? DecorationModel.createDefaultDecorationModel(1) : document.getDecorations().getLast();
        return new Document("\n", List.of(new DecorationModel(0, 1, run.getDecoration(), run.getParagraphDecoration())), 0);
    }

    private static TextDecoration clearedTextStyle(TextDecoration original, boolean paragraphToo) {
        var builder = TextDecoration.builder().presets();
        if (!paragraphToo) {
            // These styles encode Markdown block types, rather than inline formatting.
            if (MarkdownStyles.isHeadingSize(original.getFontSize()))
                builder.fontSize(original.getFontSize()).fontWeight(FontWeight.BOLD);
            if (MarkdownStyles.isCodeBlockBackground(original.getBackground()))
                builder.background(original.getBackground()).fontFamily(original.getFontFamily()).codeLanguage(original.getCodeLanguage());
        }
        return builder.build();
    }

    /** Move an entire chapter range before another chapter boundary. */
    public static Document moveRange(Document source, int start, int end, int destination) {
        check(source, start, end);
        check(source, destination, destination);
        if (destination >= start && destination <= end) return source;
        // Separators belong between blocks, including when the last block has no trailing newline.
        Document result;
        if (destination < start) result = join(source, List.of(slice(source, 0, destination),
                separated(source, slice(source, start, end)), slice(source, destination, start),
                slice(source, end, source.getText().length())), destination);
        else {
            Document beforeMoved = separated(source, slice(source, end, destination));
            result = join(source, List.of(slice(source, 0, start), beforeMoved, slice(source, start, end),
                    slice(source, destination, source.getText().length())), start + beforeMoved.getText().length());
        }
        if (!source.getText().endsWith("\n") && result.getText().endsWith("\n")) {
            Document trimmed = slice(result, 0, result.getText().length() - 1);
            return new Document(trimmed.getText(), trimmed.getDecorations(), result.getCaretPosition(), source.getPageFormat(), source.getHeadersFooters());
        }
        return result;
    }

    private static Document separated(Document source, Document block) {
        if (block.getText().isEmpty() || block.getText().endsWith("\n")) return block;
        return join(source, List.of(block, new Document("\n")), 0);
    }
    private static ArrayList<Document> lines(Document source) {
        var result = new ArrayList<Document>();
        int start = 0;
        for (int i = 0; i < source.getText().length(); i++) if (source.getText().charAt(i) == '\n') {
            result.add(slice(source, start, i)); start = i + 1;
        }
        if (start < source.getText().length() || result.isEmpty()) result.add(slice(source, start, source.getText().length()));
        return result;
    }
    private static Document combineLines(Document source, List<Document> lines, boolean trailingNewline) {
        var parts = new ArrayList<Document>();
        for (int i = 0; i < lines.size(); i++) {
            Document line = lines.get(i); parts.add(line);
            if (i + 1 < lines.size() || trailingNewline) {
                var d = line.getDecorations().isEmpty() ? DecorationModel.createDefaultDecorationModel(1) : line.getDecorations().getLast();
                parts.add(new Document("\n", List.of(new DecorationModel(0, 1, d.getDecoration(), d.getParagraphDecoration())), 0));
            }
        }
        return join(source, parts, 0);
    }
    private static void check(Document source, int start, int end) {
        Objects.requireNonNull(source);
        if (start < 0 || end < start || end > source.getText().length()) throw new IndexOutOfBoundsException();
        if (start > 0 && start < source.getText().length() && Character.isLowSurrogate(source.getText().charAt(start))) throw new IllegalArgumentException("Split Unicode character");
        if (end > 0 && end < source.getText().length() && Character.isLowSurrogate(source.getText().charAt(end))) throw new IllegalArgumentException("Split Unicode character");
    }
}
