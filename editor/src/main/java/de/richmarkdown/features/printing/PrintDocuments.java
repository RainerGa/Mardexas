package de.richmarkdown.features.printing;

import com.gluonhq.richtextarea.model.*;
import de.richmarkdown.features.tables.TableDocuments;
import de.richmarkdown.markdown.MarkdownStyles;
import java.util.*;

/** Pure print-only snapshot transformations. Exported offsets, never internal editor offsets. */
public final class PrintDocuments {
    private PrintDocuments() {}
    public static Document prepare(Document source, PrintOptions options, int caret, int selectionStart, int selectionEnd) {
        Document selected = switch (options.scope()) {
            case DOCUMENT -> source;
            case SELECTION -> selection(source, selectionStart, selectionEnd);
            case CHAPTER -> chapter(source, caret);
        };
        List<DecorationModel> spans = new ArrayList<>();
        for (DecorationModel dm : selected.getDecorations()) {
            Decoration decoration = dm.getDecoration();
            if (decoration instanceof TextDecoration td) {
                boolean code = MarkdownStyles.MONOSPACED.equals(td.getFontFamily()) || MarkdownStyles.isCodeBlockBackground(td.getBackground());
                double factor = options.fontSize() / 14d;
                decoration = TextDecoration.builder().fromDecoration(td).fontFamily(code ? td.getFontFamily() : options.fontFamily()).fontSize(td.getFontSize() * factor).build();
            }
            spans.add(new DecorationModel(dm.getStart(), dm.getLength(), decoration, dm.getParagraphDecoration()));
        }
        return new Document(selected.getText(), List.copyOf(spans), 0, selected.getPageFormat(), selected.getHeadersFooters());
    }
    public static Document selection(Document source, int start, int end) {
        Objects.checkFromToIndex(start, end, source.getText().length());
        if (start == end) throw new IllegalArgumentException("Bitte zuerst einen Druckbereich markieren.");
        // A partial structural table cannot stand on its own: retain the full table.
        var first = TableDocuments.at(source, start); if (first.isPresent()) start = first.get().start();
        var last = TableDocuments.at(source, end - 1); if (last.isPresent()) end = last.get().end();
        return cut(source, start, end);
    }
    public static Document chapter(Document source, int caret) {
        if (caret < 0 || caret > source.getText().length()) throw new IllegalArgumentException("Ungültige Schreibposition.");
        List<int[]> headings = headings(source); int chosen = -1;
        for (int i = 0; i < headings.size(); i++) { if (headings.get(i)[0] <= caret) chosen = i; else break; }
        if (chosen < 0) throw new IllegalArgumentException("Die Schreibposition liegt vor der ersten Überschrift.");
        int start = headings.get(chosen)[0], level = headings.get(chosen)[1], end = source.getText().length();
        for (int i = chosen + 1; i < headings.size(); i++) if (headings.get(i)[1] <= level) { end = headings.get(i)[0]; break; }
        return cut(source, start, end);
    }
    private static List<int[]> headings(Document source) {
        List<int[]> headings = new ArrayList<>(); int start = 0, cursor = 0; List<DecorationModel> spans = source.getDecorations();
        while (start < source.getText().length()) {
            int end = source.getText().indexOf('\n', start); if (end < 0) end = source.getText().length();
            while (cursor < spans.size() && spans.get(cursor).getStart() + spans.get(cursor).getLength() <= start) cursor++;
            if (cursor < spans.size()) { DecorationModel dm = spans.get(cursor); if (dm.getStart() <= start && dm.getDecoration() instanceof TextDecoration td && (dm.getParagraphDecoration() == null || !dm.getParagraphDecoration().hasTableDecoration()) && !MarkdownStyles.isCodeBlockBackground(td.getBackground())) { int level = MarkdownStyles.headingLevel(td.getFontSize()); if (level > 0) headings.add(new int[]{start, level}); } }
            start = end + 1;
        } return headings;
    }
    private static Document cut(Document source, int start, int end) {
        List<DecorationModel> spans = new ArrayList<>();
        for (DecorationModel dm : source.getDecorations()) { int a = Math.max(start, dm.getStart()), b = Math.min(end, dm.getStart() + dm.getLength()); if (b > a) spans.add(new DecorationModel(a - start, b - a, dm.getDecoration(), dm.getParagraphDecoration())); }
        return new Document(source.getText().substring(start, end), List.copyOf(spans), 0, source.getPageFormat(), source.getHeadersFooters());
    }
}
