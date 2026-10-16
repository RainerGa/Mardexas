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

import com.gluonhq.richtextarea.model.DecorationModel;
import com.gluonhq.richtextarea.model.TableDecoration;
import com.gluonhq.richtextarea.model.Document;
import com.gluonhq.richtextarea.model.ParagraphDecoration;
import com.gluonhq.richtextarea.model.TextDecoration;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;

/**
 * Serialisiert ein Rich-Text-Document oder {@link MarkdownModel} in kanonischen
 * Markdown-Quelltext.
 *
 * <p>Die Blockerkennung im Rich-Text-Document beruht auf den zentralen
 * Konventionen aus {@link MarkdownStyles} und den Decoration-Merkmalen der
 * Rich-Text-Area-Bibliothek. Das erzeugte Markdown ist auf einen stabilen
 * Parse-/Write-Round-Trip mit {@link MarkdownParser} ausgelegt; nicht im
 * Modell verfügbare Informationen (beispielsweise Bild-Alt-Texte) können
 * naturgemäß nicht rekonstruiert werden.
 *
 * <p>Kanonische Erkennungsregeln:
 * <ul>
 *   <li>Überschrift: alignment LEFT, fontSize == Heading-Größe, bold</li>
 *   <li>Code-Block: Hintergrund-Marker (MarkdownStyles)</li>
 *   <li>Liste: graphicType + indentationLevel (Ebene)</li>
 *   <li>Zitat: indentationLevel &gt; 0, graphicType NONE</li>
 *   <li>HR: Absatz nur aus Leerzeichen, vollständig durchgestrichen</li>
 *   <li>Inline-Code: monospaced ohne Hintergrund-Marker</li>
 * </ul>
 */
public class MarkdownWriter {

    private static final String ZERO_WIDTH = "\u200b";
    private static final String ZERO_WIDTH_NO_BREAK = "\ufeff";

    /** Erstellt einen zustandslosen Markdown-Serializer. */
    public MarkdownWriter() {
    }

    /**
     * Konvertiert ein Rich-Text-Document in kanonischen Markdown-Quelltext.
     *
     * @param document Quelldokument einschließlich Text- und Absatzdekorationen
     * @return serialisierter Markdown-Text
     */
    public String write(Document document) {
        return write(toModel(document));
    }

    /**
     * Schreibt ein MarkdownModel als Markdown-Text.
     *
     * <p>Absatztrennung: direkt aufeinanderfolgende normale Absätze und
     * Listeneinträge werden durch einen Zeilenumbruch getrennt. Andere
     * Blockgrenzen sowie explizite leere Absatz-Blöcke verwenden eine
     * Leerzeile. Leere Absatz-Blöcke werden nicht selbst gerendert.
     *
     * @param model geordnete Modellblöcke
     * @return kanonischer Markdown-Text ohne zusätzliches abschließendes
     *         Newline, sofern dies nicht zur Repräsentation eines leeren
     *         Schlussabsatzes erforderlich ist
     */
    public String write(MarkdownModel model) {
        StringBuilder sb = new StringBuilder();
        int[] counters = new int[10];
        Block previousBlock = null;
        boolean forceBlankLine = false;

        for (Block block : model.getBlocks()) {
            if (isEmptyParagraphBlock(block)) {
                // Nur der erste leere Absatz nach einem nicht-leeren Block setzt forceBlankLine
                // Aufeinanderfolgende leere Absätze werden ignoriert, um doppelte Leerzeilen zu vermeiden
                if (previousBlock != null && !isEmptyParagraphBlock(previousBlock)) {
                    forceBlankLine = true;
                }
                continue;
            }
            if (sb.length() > 0) {
                boolean lineLike = (previousBlock instanceof ParagraphBlock && block instanceof ParagraphBlock)
                        || (previousBlock instanceof ListItemBlock && block instanceof ListItemBlock)
                        || (previousBlock instanceof QuoteBlock && block instanceof QuoteBlock);
                sb.append(lineLike && !forceBlankLine ? "\n" : "\n\n");
            }
            if (block instanceof ListItemBlock item) {
                renderListItem(item, sb, counters);
            } else {
                java.util.Arrays.fill(counters, 0);
                renderBlock(block, sb);
            }
            previousBlock = block;
            forceBlankLine = false;
        }
        // Ein einzelner trailing leerer Absatz wird als abschließendes Newline
        // erhalten (konventioneller Dateiabschluss); weitere werden normalisiert
        if (forceBlankLine && sb.length() > 0) {
            sb.append('\n');
        }
        return sb.toString();
    }

    // ---------- Document → MarkdownModel ----------

    /**
     * Überführt Text und Dekorationen eines Rich-Text-Documents in das interne
     * Blockmodell.
     *
     * @param document Rich-Text-Document als Quelle
     * @return geordnetes Markdown-Modell
     */
    public MarkdownModel toModel(Document document) {
        String text = document.getText();
        List<Block> blocks = new ArrayList<>();

        List<int[]> paragraphs = splitParagraphs(text);
        for (int[] paragraph : paragraphs) {
            int start = paragraph[0];
            int end = paragraph[1];
            String paragraphText = text.substring(start, end);

            ParagraphDecoration pd = paragraphDecorationAt(document, start);
            com.gluonhq.richtextarea.model.Decoration decoration = decorationAt(document, start);

            Block block = classify(paragraphText, pd, decoration, document, start, end);
            if (block != null) {
                if (block instanceof CodeBlockBlock codeBlock
                        && !blocks.isEmpty()
                        && blocks.get(blocks.size() - 1) instanceof CodeBlockBlock previous) {
                    // Zusammenhängende Code-Block-Absätze zu einem Block verschmelzen
                    List<String> merged = new ArrayList<>(previous.getLines());
                    merged.addAll(codeBlock.getLines());
                    blocks.set(blocks.size() - 1, new CodeBlockBlock(merged));
                } else {
                    blocks.add(block);
                }
            }
        }

        // Hinweis: führende/trailing leere Absätze bleiben im Modell – write()
        // entscheidet über deren Wirkung (Leerzeile bzw. abschließendes Newline)
        return MarkdownModel.of(blocks);
    }

    private Block classify(String paragraphText, ParagraphDecoration pd,
                           com.gluonhq.richtextarea.model.Decoration decoration,
                           Document document, int start, int end) {
        // Bild: ImageDecoration (kein TextDecoration!)
        if (decoration instanceof com.gluonhq.richtextarea.model.ImageDecoration imageDecoration) {
            return new ImageBlock(imageDecoration.getUrl());
        }
        TextDecoration td = decoration instanceof TextDecoration textDecoration ? textDecoration : null;

        // Tabelle: TableDecoration in der ParagraphDecoration, und zwar genau
        // dann, wenn der Absatz selbst die Tabellenzellen trägt (die
        // Dekoration MUSS bei diesem Absatz beginnen und er enthält exakt
        // rows*columns-1 Zellseparatoren). Sonst fällt ein leerer Folgeabsatz
        // über den Decoration-Fallback ebenfalls in die Tabelle.
        if (pd != null && pd.hasTableDecoration()
                && isTableParagraph(document, start, paragraphText, pd.getTableDecoration())) {
            return tableBlock(document, start, end, pd.getTableDecoration());
        }

        // HR: nur Leerzeichen, vollständig durchgestrichen
        if (isHorizontalRule(paragraphText, document, start, end)) {
            return new HorizontalRuleBlock();
        }

        // Überschrift: kanonische Größe + bold
        if (td != null && td.getFontWeight() == FontWeight.BOLD) {
            int level = MarkdownStyles.headingLevel(td.getFontSize());
            if (level > 0) {
                return new HeadingBlock(level, inlineRuns(document, start, end));
            }
        }

        // Code-Block: Hintergrund-Marker
        if (td != null && MarkdownStyles.isCodeBlockBackground(td.getBackground())) {
            List<String> lines = new ArrayList<>();
            for (String line : paragraphText.split("\n", -1)) {
                lines.add(stripZeroWidth(line));
            }
            return new CodeBlockBlock(lines);
        }

        // Liste
        if (pd != null && pd.getGraphicType() != ParagraphDecoration.GraphicType.NONE) {
            boolean numbered = pd.getGraphicType() == ParagraphDecoration.GraphicType.NUMBERED_LIST;
            return new ListItemBlock(Math.max(1, pd.getIndentationLevel()), numbered,
                    inlineRuns(document, start, end));
        }

        // Zitat (Verschachtelungstiefe = Einrückungs-Ebene)
        if (pd != null && pd.getIndentationLevel() > 0) {
            return nestedQuote(pd.getIndentationLevel(),
                    new ParagraphBlock(inlineRuns(document, start, end)));
        }

        // Normaler Absatz
        return new ParagraphBlock(inlineRuns(document, start, end));
    }

    /**
     * Prüft, ob der Absatz selbst die Tabelle trägt: Die Dekoration muss bei
     * diesem Absatz beginnen, und der Text muss exakt
     * {@code rows*columns-1} Zellseparatoren enthalten (so legt die Bibliothek
     * native Tabellen an: ein Absatz, Zellen durch {@code \u200b} getrennt).
     */
    private boolean isTableParagraph(Document document, int start, String paragraphText,
                                     TableDecoration tableDecoration) {
        int rows = tableDecoration.getRows();
        int columns = tableDecoration.getColumns();
        if (rows < 1 || columns < 1) {
            return false;
        }
        DecorationModel own = decorationModelAt(document, start);
        if (own == null || own.getStart() != start) {
            return false;
        }
        long separators = paragraphText.chars().filter(ch -> ch == '\u200b').count();
        return separators == (long) rows * columns - 1;
    }

    /**
     * Extrahiert die Zellen einer Tabelle. Die Bibliothek trennt Zellen durch
     * {@code \u200b} (ZERO_WIDTH_TABLE_SEPARATOR) in Zeilen-Hauptordnung; der
     * Absatz endet mit dem Zeilenende. Pro Zelle werden die Inline-Läufe aus
     * den Dekorationen extrahiert (Formatierung in Zellen bleibt erhalten).
     */
    private Block tableBlock(Document document, int start, int end, TableDecoration tableDecoration) {
        int rows = tableDecoration.getRows();
        int columns = tableDecoration.getColumns();
        String paragraphText = document.getText().substring(start, end);
        List<List<Inline>> cells = new ArrayList<>();
        int pos = start;
        for (int i = 0; i < rows * columns; i++) {
            int separator = paragraphText.indexOf('\u200b', pos - start);
            int cellEnd = separator < 0 ? end : start + separator;
            cells.add(inlineRuns(document, pos, cellEnd));
            pos = cellEnd + 1;
        }
        return new TableBlock(rows, columns, cells);
    }

    private Block nestedQuote(int depth, Block child) {
        Block current = child;
        for (int i = 0; i < depth; i++) {
            current = new QuoteBlock(List.of(current));
        }
        return current;
    }

    private List<Inline> withoutBold(List<Inline> inlines) {
        List<Inline> result = new ArrayList<>();
        for (Inline inline : inlines) {
            result.add(Inline.builder(inline.getText())
                    .bold(false)
                    .italic(inline.isItalic())
                    .underline(inline.isUnderline())
                    .strikethrough(inline.isStrikethrough())
                    .code(inline.isCode())
                    .url(inline.getUrl())
                    .build());
        }
        return result;
    }

    private boolean isHorizontalRule(String paragraphText, Document document, int start, int end) {
        if (paragraphText.isEmpty() || !paragraphText.trim().isEmpty()) {
            return false;
        }
        // Alle Zeichen müssen durchgestrichen sein
        for (int i = start; i < end; i++) {
            TextDecoration td = textDecorationAt(document, i);
            if (td == null || !Boolean.TRUE.equals(td.isStrikethrough())) {
                return false;
            }
        }
        return true;
    }

    private boolean isEmptyParagraphBlock(Block block) {
        if (block instanceof ParagraphBlock paragraph) {
            return paragraph.getInlines().isEmpty()
                    || paragraph.getInlines().stream().allMatch(i -> i.getText().isEmpty());
        }
        return false;
    }

    // ---------- Inline-Extraktion ----------

    private List<Inline> inlineRuns(Document document, int start, int end) {
        List<Inline> inlines = new ArrayList<>();
        int pos = start;
        while (pos < end) {
            DecorationModel dm = decorationModelAt(document, pos);
            int runEnd = end;
            if (dm != null) {
                runEnd = Math.min(dm.getStart() + dm.getLength(), end);
            }
            if (runEnd <= pos) {
                runEnd = pos + 1;
            }
            String runText = stripZeroWidth(document.getText().substring(pos, runEnd));
            if (!runText.isEmpty()) {
                inlines.add(toInline(runText, dm != null ? dm.getDecoration() : null));
            }
            pos = runEnd;
        }
        return mergeInlines(inlines);
    }

    private Inline toInline(String text, com.gluonhq.richtextarea.model.Decoration decoration) {
        Inline.Builder builder = Inline.builder(text);
        if (decoration instanceof TextDecoration td) {
            builder.bold(td.getFontWeight() == FontWeight.BOLD)
                    .italic(td.getFontPosture() == javafx.scene.text.FontPosture.ITALIC)
                    .underline(Boolean.TRUE.equals(td.isUnderline()))
                    .strikethrough(Boolean.TRUE.equals(td.isStrikethrough()))
                    .code(MarkdownStyles.MONOSPACED.equals(td.getFontFamily())
                            && !MarkdownStyles.isCodeBlockBackground(td.getBackground()));
            if (td.getURL() != null && !td.getURL().isEmpty()) {
                builder.url(td.getURL());
            }
        }
        return builder.build();
    }

    private List<Inline> mergeInlines(List<Inline> inlines) {
        List<Inline> merged = new ArrayList<>();
        for (Inline inline : inlines) {
            if (!merged.isEmpty()) {
                Inline last = merged.get(merged.size() - 1);
                if (sameFormat(last, inline)) {
                    merged.set(merged.size() - 1,
                            Inline.builder(last.getText() + inline.getText())
                                    .bold(last.isBold())
                                    .italic(last.isItalic())
                                    .underline(last.isUnderline())
                                    .strikethrough(last.isStrikethrough())
                                    .code(last.isCode())
                                    .url(last.getUrl())
                                    .build());
                    continue;
                }
            }
            merged.add(inline);
        }
        return merged;
    }

    private boolean sameFormat(Inline a, Inline b) {
        return a.isBold() == b.isBold()
                && a.isItalic() == b.isItalic()
                && a.isUnderline() == b.isUnderline()
                && a.isStrikethrough() == b.isStrikethrough()
                && a.isCode() == b.isCode()
                && java.util.Objects.equals(a.getUrl(), b.getUrl());
    }

    // ---------- Document-Zugriffshilfen ----------

    private List<int[]> splitParagraphs(String text) {
        List<int[]> paragraphs = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                paragraphs.add(new int[]{start, i});
                start = i + 1;
            }
        }
        paragraphs.add(new int[]{start, text.length()});
        return paragraphs;
    }

    private DecorationModel decorationModelAt(Document document, int position) {
        DecorationModel fallback = null;
        for (DecorationModel dm : document.getDecorations()) {
            if (position >= dm.getStart() && position < dm.getStart() + dm.getLength()) {
                return dm;
            }
            if (dm.getStart() == position) {
                fallback = dm;
            } else if (dm.getStart() < position) {
                if (fallback == null || fallback.getStart() < dm.getStart()) {
                    fallback = dm;
                }
            }
        }
        return fallback;
    }

    private com.gluonhq.richtextarea.model.Decoration decorationAt(Document document, int position) {
        DecorationModel dm = decorationModelAt(document, position);
        return dm != null ? dm.getDecoration() : null;
    }

    private TextDecoration textDecorationAt(Document document, int position) {
        DecorationModel dm = decorationModelAt(document, position);
        return dm != null && dm.getDecoration() instanceof TextDecoration td ? td : null;
    }

    private ParagraphDecoration paragraphDecorationAt(Document document, int position) {
        DecorationModel dm = decorationModelAt(document, position);
        return dm != null ? dm.getParagraphDecoration() : null;
    }

    private String stripZeroWidth(String text) {
        return text.replace(ZERO_WIDTH, "").replace(ZERO_WIDTH_NO_BREAK, "");
    }

    // ---------- Rendering ----------

    private void renderBlock(Block block, StringBuilder sb) {
        if (block instanceof ParagraphBlock paragraph) {
            renderInlines(paragraph.getInlines(), sb, true);
        } else if (block instanceof HeadingBlock heading) {
            sb.append("#".repeat(heading.getLevel())).append(' ');
            // Überschriften sind implizit fett – das bold-Flag wird nicht erneut ausgegeben
            renderInlines(withoutBold(heading.getInlines()), sb, false);
        } else if (block instanceof QuoteBlock quote) {
            renderQuote(quote, sb, 1);
        } else if (block instanceof CodeBlockBlock codeBlock) {
            sb.append("```").append(codeBlock.getLanguage());
            for (String line : codeBlock.getLines()) {
                sb.append('\n').append(line);
            }
            sb.append('\n').append("```");
        } else if (block instanceof HorizontalRuleBlock) {
            sb.append("---");
        } else if (block instanceof TableBlock table) {
            renderTable(table, sb);
        } else if (block instanceof ImageBlock image) {
            sb.append("![](").append(image.getUrl()).append(")");
        } else {
            throw new IllegalArgumentException("Unsupported block: " + block.getClass());
        }
    }

    /** Rendert eine GFM-Tabelle: Kopfzeile, Trennzeile, Datenzeilen. */
    private void renderTable(TableBlock table, StringBuilder sb) {
        for (int row = 0; row < table.getRows(); row++) {
            if (row > 0) {
                sb.append('\n');
            }
            sb.append("| ");
            for (int column = 0; column < table.getColumns(); column++) {
                if (column > 0) {
                    sb.append(" | ");
                }
                renderInlines(table.getCell(row, column), sb, false);
            }
            sb.append(" |");
            if (row == 0) {
                sb.append('\n').append("| ");
                for (int column = 0; column < table.getColumns(); column++) {
                    if (column > 0) {
                        sb.append(" | ");
                    }
                    sb.append("---");
                }
                sb.append(" |");
            }
        }
    }

    private void renderListItem(ListItemBlock item, StringBuilder sb, int[] counters) {
        int indent = item.isNumbered() ? (item.getLevel() - 1) * 3 : (item.getLevel() - 1) * 2;
        sb.append(" ".repeat(Math.max(0, indent)));

        if (item.isNumbered()) {
            int level = item.getLevel();
            if (counters[level] == 0) {
                counters[level] = 1;
            } else {
                counters[level]++;
            }
            for (int i = level + 1; i < counters.length; i++) {
                counters[i] = 0;
            }
            sb.append(counters[level]).append(". ");
        } else {
            for (int i = item.getLevel(); i < counters.length; i++) {
                counters[i] = 0;
            }
            sb.append("- ");
        }
        renderInlines(item.getInlines(), sb, false);
    }

    private void renderQuote(QuoteBlock quote, StringBuilder sb, int depth) {
        String prefix = "> ".repeat(depth);
        List<Block> children = quote.getChildren();
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            StringBuilder child = new StringBuilder();
            Block childBlock = children.get(i);
            if (childBlock instanceof ParagraphBlock paragraph) {
                // Im Zitat ist der Zeilenanfang durch "> " bereits eindeutig –
                // kein Line-Start-Escaping nötig
                renderInlines(paragraph.getInlines(), child, false);
            } else {
                renderBlock(childBlock, child);
            }
            for (String line : child.toString().split("\n", -1)) {
                sb.append(prefix).append(line).append('\n');
            }
        }
        // letztes Newline entfernen (der Block-Joiner übernimmt die Trennung)
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.deleteCharAt(sb.length() - 1);
        }
    }

    /**
     * Rendert Inline-Läufe mit zustandsbehafteten Markern: Betonungs-Marker
     * bleiben über aufeinanderfolgende Läufe hinweg geöffnet, sodass
     * verschachtelte Formate (z. B. Fett mit kursivem Einschub) korrekt
     * round-trippen ({@code **a *b* c**} statt {@code **a ****b**** c**}).
     */
    private void renderInlines(List<Inline> inlines, StringBuilder sb, boolean lineStart) {
        MarkerState state = new MarkerState();
        boolean first = true;

        for (Inline inline : inlines) {
            String text = escape(inline.getText(), lineStart && first);
            if (text.isEmpty()) {
                continue;
            }
            first = false;

            if (inline.isCode()) {
                // Code-Span: Marker bleiben geöffnet (**a `code` b** ist valide)
                sb.append('`').append(text).append('`');
                continue;
            }

            if (inline.getUrl() != null) {
                // Link: Marker-Zustand sichern, Linkinhalt rendern, innerhalb
                // des Links geöffnete Marker schließen, äußeren Zustand wiederherstellen
                MarkerState saved = state.copy();
                sb.append('[');
                state.adjust(sb, inline, text);
                state.closeTo(sb, saved);
                sb.append("](").append(inline.getUrl()).append(")");
                state.restore(saved);
                continue;
            }

            state.adjust(sb, inline, text);
        }

        state.closeAll(sb);
    }

    /** Zustand der offenen Betonungs-Marker. */
    private static final class MarkerState {
        private boolean bold;
        private boolean italic;
        private boolean strike;
        private boolean underline;

        private void adjust(StringBuilder sb, Inline inline, String text) {
            // 1. Marker schließen, die für diesen Lauf nicht gelten
            //    (in umgekehrter Öffnungsreihenfolge)
            if (!inline.isUnderline() && underline) {
                sb.append("</u>");
                underline = false;
            }
            if (!inline.isStrikethrough() && strike) {
                sb.append("~~");
                strike = false;
            }
            if (!inline.isItalic() && italic) {
                sb.append('*');
                italic = false;
            }
            if (!inline.isBold() && bold) {
                sb.append("**");
                bold = false;
            }
            // 2. Marker öffnen, die für diesen Lauf gelten
            if (inline.isBold() && !bold) {
                sb.append("**");
                bold = true;
            }
            if (inline.isItalic() && !italic) {
                sb.append('*');
                italic = true;
            }
            if (inline.isStrikethrough() && !strike) {
                sb.append("~~");
                strike = true;
            }
            if (inline.isUnderline() && !underline) {
                sb.append("<u>");
                underline = true;
            }
            // 3. Text
            sb.append(text);
        }

        private void closeAll(StringBuilder sb) {
            if (underline) {
                sb.append("</u>");
                underline = false;
            }
            if (strike) {
                sb.append("~~");
                strike = false;
            }
            if (italic) {
                sb.append('*');
                italic = false;
            }
            if (bold) {
                sb.append("**");
                bold = false;
            }
        }

        /** Schließt Marker, die seit dem übergebenen Zustand geöffnet wurden. */
        private void closeTo(StringBuilder sb, MarkerState target) {
            if (underline && !target.underline) {
                sb.append("</u>");
                underline = false;
            }
            if (strike && !target.strike) {
                sb.append("~~");
                strike = false;
            }
            if (italic && !target.italic) {
                sb.append('*');
                italic = false;
            }
            if (bold && !target.bold) {
                sb.append("**");
                bold = false;
            }
        }

        private MarkerState copy() {
            MarkerState copy = new MarkerState();
            copy.bold = this.bold;
            copy.italic = this.italic;
            copy.strike = this.strike;
            copy.underline = this.underline;
            return copy;
        }

        private void restore(MarkerState saved) {
            this.bold = saved.bold;
            this.italic = saved.italic;
            this.strike = saved.strike;
            this.underline = saved.underline;
        }
    }

    // ---------- Escaping ----------

    /**
     * Escaping nur am Zeilenanfang: Dort würden Marker die Blockstruktur
     * ändern (Überschrift, Zitat, Liste). Inline-Zeichen werden nicht
     * maskiert – ungematchte Marker round-trippen identisch, gematchte
     * werden als Formatierung korrekt wiederholt.
     */
    private String escape(String text, boolean lineStart) {
        if (lineStart && !text.isEmpty()) {
            return escapeLineStart(text);
        }
        return text;
    }

    private String escapeLineStart(String text) {
        char first = text.charAt(0);
        // HR-ähnliche Zeilenfolgen und Block-Marker am Zeilenanfang
        if (first == '#' || first == '>' || first == '+' || first == '-') {
            return "\\" + text;
        }
        // "1. " oder "1) " am Zeilenanfang
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("^(\\d+)([.)])").matcher(text);
        if (matcher.find()) {
            return matcher.group(1) + "\\" + matcher.group(2) + text.substring(matcher.end());
        }
        return text;
    }
}
