package de.richmarkdown.features.editing;

import com.gluonhq.richtextarea.model.DecorationModel;
import com.gluonhq.richtextarea.model.Document;
import com.gluonhq.richtextarea.model.ImageDecoration;
import com.gluonhq.richtextarea.model.ParagraphDecoration;
import com.gluonhq.richtextarea.model.TextDecoration;
import de.richmarkdown.markdown.MarkdownStyles;

/** Semantic limits for commands that rearrange rendered paragraphs or lines. */
final class EditingContext {
    private EditingContext() { }

    static boolean ordinaryParagraph(Document document, DocumentEdits.Range range) {
        return document.getDecorations().stream().filter(run -> overlaps(run, range))
                .allMatch(run -> ordinary(run, false));
    }

    static boolean reorderLines(Document document, DocumentEdits.Range range) {
        String text = document.getText();
        if (range.start() == range.end() || !lineStart(text, range.start()) || !lineEnd(text, range.end())) return false;
        String selected = text.substring(range.start(), range.end());
        int firstBreak = selected.indexOf('\n');
        if (firstBreak < 0 || firstBreak == selected.length() - 1) return false;
        // Code lines may be sorted, but never across code/prose boundaries or languages.
        Boolean code = null;
        String language = null;
        for (var run : document.getDecorations()) {
            if (!overlaps(run, range)) continue;
            if (!ordinary(run, true)) return false;
            var style = (TextDecoration) run.getDecoration();
            boolean inCode = MarkdownStyles.isCodeBlockBackground(style.getBackground());
            if (code != null && code != inCode) return false;
            if (inCode && language != null && !language.equals(style.getCodeLanguage())) return false;
            code = inCode;
            if (inCode) language = style.getCodeLanguage();
        }
        return true;
    }

    static boolean containsText(Document document, DocumentEdits.Range range) {
        return document.getDecorations().stream().anyMatch(run -> overlaps(run, range)
                && run.getDecoration() instanceof TextDecoration
                && document.getText().substring(Math.max(range.start(), run.getStart()),
                        Math.min(range.end(), run.getStart() + run.getLength())).codePoints().anyMatch(Character::isLetter));
    }

    static boolean hasTable(Document document, DocumentEdits.Range range) {
        return document.getDecorations().stream().anyMatch(run -> overlaps(run, range)
                && run.getParagraphDecoration() != null && run.getParagraphDecoration().hasTableDecoration());
    }

    private static boolean ordinary(DecorationModel run, boolean allowCode) {
        var paragraph = run.getParagraphDecoration();
        if (paragraph != null && (paragraph.hasTableDecoration()
                || paragraph.getGraphicType() != ParagraphDecoration.GraphicType.NONE || paragraph.getIndentationLevel() > 0)) return false;
        if (run.getDecoration() instanceof ImageDecoration || !(run.getDecoration() instanceof TextDecoration style)) return false;
        if (MarkdownStyles.isHeadingSize(style.getFontSize())) return false;
        return allowCode || !MarkdownStyles.isCodeBlockBackground(style.getBackground());
    }

    private static boolean overlaps(DecorationModel run, DocumentEdits.Range range) {
        return run.getStart() < range.end() && run.getStart() + run.getLength() > range.start();
    }
    private static boolean lineStart(String text, int position) { return position == 0 || text.charAt(position - 1) == '\n'; }
    private static boolean lineEnd(String text, int position) {
        return position == text.length() || text.charAt(position) == '\n' || position > 0 && text.charAt(position - 1) == '\n';
    }
}
