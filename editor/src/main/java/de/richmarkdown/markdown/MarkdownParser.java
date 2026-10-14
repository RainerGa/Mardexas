package de.richmarkdown.markdown;

import com.gluonhq.richtextarea.model.DecorationModel;
import com.gluonhq.richtextarea.model.ImageDecoration;
import com.gluonhq.richtextarea.model.TableDecoration;
import com.gluonhq.richtextarea.model.Document;
import com.gluonhq.richtextarea.model.ParagraphDecoration;
import com.gluonhq.richtextarea.model.TextDecoration;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wandelt Markdown-Quelltext in das interne Blockmodell oder in ein
 * {@link Document} der Rich-Text-Area-Bibliothek um.
 *
 * <p>Die Implementierung unterstützt die vom Editor verwendete CommonMark-
 * und GFM-Teilmenge: Absätze, Überschriften H1–H6, verschachtelte Listen und
 * Zitate, Code-Fences, horizontale Trennlinien, GFM-Tabellen, Bildverweise,
 * Inline-Formatierungen, Links und Escape-Sequenzen. Nicht abgebildete
 * Markdown-Erweiterungen werden nicht als eigenständige Modelltypen erhalten.
 *
 * <p>Einzelne Quellzeilenumbrüche in normalen Textabsätzen bleiben als
 * getrennte Absätze sichtbar; eine leere Quellzeile bleibt eine Absatzgrenze.
 * Dadurch wird der Editorinhalt nicht wie in der HTML-Interpretation
 * herkömmlicher Markdown-Renderer zu einem einzigen Textfluss zusammengezogen.
 *
 * <p>Die Erkennung zeilenorientierter Konstrukte ist linear ausgelegt, damit
 * lange Markerfolgen nicht zu übermäßigem Backtracking der Regex-Engine führen.
 *
 * @see MarkdownModel
 * @see MarkdownWriter
 */
public class MarkdownParser {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})(?:\\s+(.*))?$");
    private static final Pattern LIST_ITEM = Pattern.compile("^(\\s*)([-*+]|\\d+[.)])(?:\\s+(.*))?$");
    private static final Pattern CODE_FENCE = Pattern.compile("^\\s*(```|~~~)\\s*.*$");
    private static final Pattern IMAGE_LINE =
            Pattern.compile("^\\s*!\\[[^\\]]*\\]\\(([^)]*)\\)\\s*$");

    /*
     * HORIZONTAL_RULE, QUOTE_LINE und TABLE_SEPARATOR_LINE wurden bewusst NICHT
     * als Regex umgesetzt (RME-003): Die JDK-Regex-Engine matcht Loop-,
     * Backref- und verschachtelte Quantifier-Konstrukte rekursiv. Bereits
     * ~2.000 identische Marker-Zeichen in einer Zeile (z. B. "*".repeat(2000)
     * oder ">".repeat(2000)) führten daher zu einem StackOverflowError beim
     * Öffnen eines Dokuments (CWE-674/CWE-1333). Die folgenden linearen Scans
     * sind zu den ursprünglichen Mustern äquivalent (per Differenztest
     * abgesichert, siehe ParserHardeningTest):
     *
     *   HORIZONTAL_RULE      ^\s*([-*_])(?:\s*\1){2,}\s*$
     *   QUOTE_LINE           ^(\s*)((?:>\s?)+)(.*)$
     *   TABLE_SEPARATOR_LINE ^\s*\|?(\s*:?-+:?\s*\|)+\s*:?-+:?\s*\|?\s*$
     */

    /** Leerzeichenlauf, der eine Horizontal Rule im Document abbildet. */
    private static final String HR_TEXT = "          ";

    /** Zero-Width-Table-Separator der Bibliothek (Zelltrenner). */
    private static final String TABLE_SEPARATOR = "\u200b";

    /** Zero-Width-No-Break-Space der Bibliothek (Bild-Platzhalter). */
    private static final String IMAGE_PLACEHOLDER = "\ufeff";

    /** Ergebnis von {@link #matchQuoteLine(String)} (Ersatz für QUOTE_LINE). */
    record QuoteLine(String markers, String content) {
    }

    /** Erstellt einen zustandslosen Parser. */
    public MarkdownParser() {
    }

    /**
     * Parst Markdown-Quelltext in das interne Blockmodell.
     *
     * @param markdown Quelltext mit normalisierten oder gemischten Zeilenenden;
     *                 {@code null} und leerer Text ergeben ein leeres Modell
     * @return Modell mit den erkannten Blöcken in Quelltextreihenfolge
     */
    public MarkdownModel parse(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return MarkdownModel.of(List.of());
        }
        List<String> lines = java.util.Arrays.asList(markdown.split("\n", -1));
        List<Block> blocks = parseBlocks(lines);
        return MarkdownModel.of(blocks);
    }

    /**
     * Parst Markdown-Quelltext in ein Rich-Text-Document.
     *
     * <p>Diese Methode ist der Adapter für die Editoroberfläche; für
     * formatunabhängige Verarbeitung sollte {@link #parse(String)} verwendet
     * werden.
     *
     * @param markdown Markdown-Quelltext
     * @return für die Rich-Text-Area-Bibliothek aufgebautes Dokument
     */
    public Document parseInto(String markdown) {
        return buildDocument(parse(markdown));
    }

    // ---------- Lineare Zeilenklassifikation (Ersatz für Regex, RME-003) ----------

    /** Whitespace-Klasse der JDK-Regex-Engine ({@code \s}). */
    private static boolean isRegexWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r';
    }

    /** Zeilentrenner im Sinne des Regex-Punkts ({@code .} matcht sie nicht). */
    private static boolean isLineTerminator(char c) {
        return c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029';
    }

    /** Index des ersten Zeichens ab {@code from}, das kein Regex-Whitespace ist. */
    private static int skipWhitespace(String s, int from) {
        int i = from;
        while (i < s.length() && isRegexWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    /**
     * Horizontal Rule: {@code ^\s*([-*_])(?:\s*\1){2,}\s*$} – mindestens drei
     * gleiche Marker ({@code -}, {@code *} oder {@code _}), nur durch
     * Whitespace getrennt, sonst nichts in der Zeile.
     */
    static boolean isHorizontalRule(String line) {
        int n = line.length();
        int i = skipWhitespace(line, 0);
        if (i >= n) {
            return false;
        }
        char marker = line.charAt(i);
        if (marker != '-' && marker != '*' && marker != '_') {
            return false;
        }
        int count = 1;
        i++;
        while (true) {
            int j = skipWhitespace(line, i);
            if (j < n && line.charAt(j) == marker) {
                count++;
                i = j + 1;
            } else {
                i = j;
                break;
            }
        }
        return count >= 3 && skipWhitespace(line, i) == n;
    }

    /**
     * Zitat-Zeile: {@code ^(\s*)((?:>\s?)+)(.*)$} – führender Whitespace, dann
     * ein oder mehrere '{@code >}' (jeder mit höchstens einem folgendem
     * Whitespace-Zeichen), dann der Rest als Inhalt. {@code null}, wenn die
     * Zeile keine Zitat-Zeile ist.
     */
    static QuoteLine matchQuoteLine(String line) {
        int n = line.length();
        int i = skipWhitespace(line, 0);
        int markersStart = i;
        while (i < n && line.charAt(i) == '>') {
            i++;
            if (i < n && isRegexWhitespace(line.charAt(i))) {
                i++;
            }
        }
        if (i == markersStart) {
            return null;
        }
        // (.*)$ des Originalmusters: der Rest darf keine Zeilentrenner enthalten
        // (. matcht keine Zeilentrenner, matches() verlangt vollständigen Verbrauch).
        for (int k = i; k < n; k++) {
            if (isLineTerminator(line.charAt(k))) {
                return null;
            }
        }
        return new QuoteLine(line.substring(markersStart, i), line.substring(i));
    }

    /**
     * Tabellen-Trennzeile: {@code ^\s*\|?(\s*:?-+:?\s*\|)+\s*:?-+:?\s*\|?\s*$}
     * – optionale äußere Pipes, dazwischen mindestens zwei Zellen aus
     * Bindestrichen mit optionalen Doppelpunkt-Alignment-Markern.
     */
    static boolean isTableSeparatorLine(String line) {
        int n = line.length();
        int i = skipWhitespace(line, 0);
        if (i < n && line.charAt(i) == '|') {
            i++; // optionale führende Pipe
        }
        int cells = 0;
        while (i < n) {
            // Zelle: \s*:?-+:?\s*
            int j = skipWhitespace(line, i);
            if (j < n && line.charAt(j) == ':') {
                j++;
            }
            int dashes = 0;
            while (j < n && line.charAt(j) == '-') {
                j++;
                dashes++;
            }
            if (dashes == 0) {
                break; // keine weitere Zelle
            }
            if (j < n && line.charAt(j) == ':') {
                j++;
            }
            i = skipWhitespace(line, j);
            cells++;
            if (i < n && line.charAt(i) == '|') {
                i++; // nächste Zelle folgt
            } else {
                break; // Zellenende erreicht
            }
        }
        if (cells < 2) {
            // Das Originalmuster verlangt eine Gruppe (mindestens eine Zelle
            // mit Pipe) plus eine abschließende Zelle.
            return false;
        }
        if (i < n && line.charAt(i) == '|') {
            i++; // optionale abschließende Pipe
        }
        return skipWhitespace(line, i) == n;
    }

    // ---------- Block-Parsing ----------

    private List<Block> parseBlocks(List<String> lines) {
        List<Block> blocks = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.isBlank()) {
                blocks.add(emptyParagraph());
                i++;
            } else if (CODE_FENCE.matcher(line).find()) {
                i = collectCodeBlock(lines, i, blocks);
            } else if (isHorizontalRule(line)) {
                blocks.add(new HorizontalRuleBlock());
                i++;
            } else if (HEADING.matcher(line).matches()) {
                blocks.add(parseHeading(line));
                i++;
            } else if (LIST_ITEM.matcher(line).matches()) {
                i = collectListItems(lines, i, blocks);
            } else if (i + 1 < lines.size() && line.contains("|")
                    && isTableSeparatorLine(lines.get(i + 1))) {
                i = collectTable(lines, i, blocks);
            } else if (IMAGE_LINE.matcher(line).matches()) {
                blocks.add(parseImageLine(line));
                i++;
            } else if (matchQuoteLine(line) != null) {
                i = collectQuote(lines, i, blocks);
            } else {
                i = collectParagraph(lines, i, blocks);
            }
        }
        return blocks;
    }

    private Block emptyParagraph() {
        return new ParagraphBlock(List.of());
    }

    private Block parseHeading(String line) {
        Matcher matcher = HEADING.matcher(line);
        matcher.matches();
        int level = matcher.group(1).length();
        String text = matcher.group(2) == null ? "" : matcher.group(2).trim();
        return new HeadingBlock(level, parseInlines(text));
    }

    private int collectCodeBlock(List<String> lines, int start, List<Block> blocks) {
        Matcher fenceMatcher = CODE_FENCE.matcher(lines.get(start));
        fenceMatcher.matches();
        String fence = fenceMatcher.group(1);
        String language = lines.get(start).trim().substring(fence.length()).trim();
        List<String> codeLines = new ArrayList<>();
        int i = start + 1;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.trim().startsWith(fence) && line.trim().length() >= fence.length()) {
                i++;
                break;
            }
            codeLines.add(line);
            i++;
        }
        blocks.add(new CodeBlockBlock(language, codeLines));
        return i;
    }

    private int collectListItems(List<String> lines, int start, List<Block> blocks) {
        int i = start;
        ListItemBlock previous = null;
        while (i < lines.size()) {
            String line = lines.get(i);
            Matcher matcher = LIST_ITEM.matcher(line);
            if (matcher.matches()) {
                int indent = matcher.group(1).length();
                int level = indent / 2 + 1;
                boolean numbered = Character.isDigit(matcher.group(2).charAt(0));
                String text = matcher.group(3) == null ? "" : matcher.group(3).trim();
                ListItemBlock item = new ListItemBlock(level, numbered, parseInlines(text));
                blocks.add(item);
                previous = item;
                i++;
            } else if (!line.isBlank() && previous != null && line.startsWith(" ")) {
                // Fortsetzungszeile eines Listeneintrags (weicher Umbruch)
                String continuation = line.trim();
                if (!continuation.isEmpty()) {
                    List<Inline> merged = new ArrayList<>(previous.getInlines());
                    merged.addAll(parseInlines(continuation));
                    blocks.set(blocks.size() - 1,
                            new ListItemBlock(previous.getLevel(), previous.isNumbered(), merged));
                }
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    private int collectTable(List<String> lines, int start, List<Block> blocks) {
        List<String[]> rows = new ArrayList<>();
        rows.add(splitTableRow(lines.get(start)));
        int i = start + 2; // Kopfzeile + Trennzeile überspringen
        while (i < lines.size() && lines.get(i).contains("|") && !lines.get(i).isBlank()) {
            rows.add(splitTableRow(lines.get(i)));
            i++;
        }
        int columnCount = rows.stream().mapToInt(r -> r.length).max().orElse(0);
        List<List<Inline>> cells = new ArrayList<>();
        for (String[] row : rows) {
            for (int c = 0; c < columnCount; c++) {
                String cell = c < row.length ? row[c] : "";
                cells.add(parseInlines(cell));
            }
        }
        blocks.add(new TableBlock(rows.size(), columnCount, cells));
        return i;
    }

    /** Teilt eine Tabellenzeile in Zellen; führende/trailing Pipe wird entfernt. */
    private String[] splitTableRow(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String[] parts = trimmed.split("\\|", -1);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }

    private Block parseImageLine(String line) {
        Matcher matcher = IMAGE_LINE.matcher(line);
        matcher.matches();
        return new ImageBlock(matcher.group(1).trim());
    }

    private int collectQuote(List<String> lines, int start, List<Block> blocks) {
        List<Integer> depths = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        int i = start;
        while (i < lines.size()) {
            String line = lines.get(i);
            QuoteLine quoteLine = matchQuoteLine(line);
            if (quoteLine != null) {
                depths.add((int) quoteLine.markers().chars().filter(c -> c == '>').count());
                contents.add(quoteLine.content());
                i++;
            } else if (!line.isBlank() && !contents.isEmpty() && !isBlockStart(line)) {
                // Lazy Continuation: Textzeile ohne '>' setzt das Zitat fort
                depths.add(depths.get(depths.size() - 1));
                contents.add(line);
                i++;
            } else {
                break;
            }
        }
        Block tree = buildQuoteTree(depths, contents, 0, depths.get(0));
        // Verschachtelungstiefe der ersten Zeile abbilden
        for (int d = 1; d < depths.get(0); d++) {
            tree = new QuoteBlock(List.of(tree));
        }
        blocks.add(tree);
        return i;
    }

    /**
     * Baut den Zitat-Baum. Jede Zitat-Zeile wird ein eigener Absatz (das
     * Document-Modell kann weder weiche Zeilenumbrüche noch Blockstrukturen
     * innerhalb von Zitaten abbilden – siehe Projektdokumentation).
     */
    private Block buildQuoteTree(List<Integer> depths, List<String> contents, int from, int depth) {
        List<Block> children = new ArrayList<>();
        int i = from;
        while (i < depths.size() && depths.get(i) >= depth) {
            if (depths.get(i) > depth) {
                int j = i;
                while (j < depths.size() && depths.get(j) > depth) {
                    j++;
                }
                children.add(buildQuoteTree(depths, contents, i, depths.get(i)));
                i = j;
            } else {
                children.add(new ParagraphBlock(parseInlines(contents.get(i).trim())));
                i++;
            }
        }
        return new QuoteBlock(children);
    }

    private boolean isBlockStart(String line) {
        return HEADING.matcher(line).matches()
                || isHorizontalRule(line)
                || LIST_ITEM.matcher(line).matches()
                || matchQuoteLine(line) != null
                || CODE_FENCE.matcher(line).find();
    }

    private int collectParagraph(List<String> lines, int start, List<Block> blocks) {
        int i = start;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.isBlank() || isBlockStart(line)) {
                break;
            }
            // Das Editor-Dokument kennt keinen weichen Zeilenumbruch innerhalb
            // eines Absatzes; jede Markdown-Textzeile muss daher als eigener
            // Absatz in den Editor, damit der Zeilenumbruch sichtbar bleibt.
            blocks.add(new ParagraphBlock(parseInlines(line.trim())));
            i++;
        }
        return i;
    }

    // ---------- Inline-Parsing ----------

    private List<Inline> parseInlines(String text) {
        List<Inline> inlines = scan(text, Format.PLAIN);
        return mergeInlines(inlines);
    }

    private List<Inline> scan(String text, Format base) {
        List<Inline> result = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);

            // Escaping
            if (c == '\\' && i + 1 < text.length() && isEscapable(text.charAt(i + 1))) {
                plain.append(text.charAt(i + 1));
                i += 2;
                continue;
            }

            // Inline-Code (kein weiteres Parsing innerhalb)
            if (c == '`') {
                int close = text.indexOf('`', i + 1);
                if (close > i + 1) {
                    String code = text.substring(i + 1, close);
                    flush(plain, result, base);
                    result.add(Inline.builder(code).code(true).build());
                    i = close + 1;
                    continue;
                }
            }

            // Link
            if (c == '[') {
                int closeBracket = text.indexOf(']', i + 1);
                if (closeBracket > 0 && closeBracket + 1 < text.length()
                        && text.charAt(closeBracket + 1) == '(') {
                    int closeParen = text.indexOf(')', closeBracket + 2);
                    if (closeParen > 0) {
                        String inner = text.substring(i + 1, closeBracket);
                        String url = text.substring(closeBracket + 2, closeParen);
                        flush(plain, result, base);
                        result.addAll(scan(inner, base.withUrl(url)));
                        i = closeParen + 1;
                        continue;
                    }
                }
            }

            // Unterstrichen
            if (text.startsWith("<u>", i)) {
                int close = text.indexOf("</u>", i + 3);
                if (close > 0) {
                    String inner = text.substring(i + 3, close);
                    flush(plain, result, base);
                    result.addAll(scan(inner, base.withUnderline(true)));
                    i = close + 4;
                    continue;
                }
            }

            // Betonung: längste Marker zuerst
            String marker = matchEmphasis(text, i);
            if (marker != null) {
                int close = findClosingEmphasis(text, i + marker.length(), marker);
                if (close > 0) {
                    String inner = text.substring(i + marker.length(), close);
                    if (!inner.isEmpty()) {
                        flush(plain, result, base);
                        result.addAll(scan(inner, base.withEmphasis(marker)));
                        i = close + marker.length();
                        continue;
                    }
                }
            }

            plain.append(c);
            i++;
        }
        flush(plain, result, base);
        return result;
    }

    private String matchEmphasis(String text, int i) {
        for (String marker : new String[]{"***", "**", "~~", "*"}) {
            if (text.startsWith(marker, i)) {
                return marker;
            }
        }
        return null;
    }

    private int findClosingEmphasis(String text, int from, String marker) {
        int index = text.indexOf(marker, from);
        if (index < 0) {
            return -1;
        }
        // Bei "*" nicht innerhalb eines "**"-Markers schließen
        if (marker.equals("*") && index + 1 < text.length() && text.charAt(index + 1) == '*') {
            return findClosingEmphasis(text, index + 2, marker);
        }
        return index;
    }

    private boolean isEscapable(char c) {
        return "\\`*_[]~#>-+!".indexOf(c) >= 0;
    }

    private void flush(StringBuilder plain, List<Inline> result, Format base) {
        if (plain.length() > 0) {
            result.add(base.toInline(plain.toString()));
            plain.setLength(0);
        }
    }

    private List<Inline> mergeInlines(List<Inline> inlines) {
        List<Inline> merged = new ArrayList<>();
        for (Inline inline : inlines) {
            if (!merged.isEmpty() && sameFormat(merged.get(merged.size() - 1), inline)) {
                Inline last = merged.get(merged.size() - 1);
                merged.set(merged.size() - 1, Inline.builder(last.getText() + inline.getText())
                        .bold(last.isBold())
                        .italic(last.isItalic())
                        .underline(last.isUnderline())
                        .strikethrough(last.isStrikethrough())
                        .code(last.isCode())
                        .url(last.getUrl())
                        .build());
            } else {
                merged.add(inline);
            }
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

    /** Formatierungszustand beim Inline-Scan. */
    private static final class Format {
        private static final Format PLAIN = new Format(false, false, false, false, false, null);

        private final boolean bold;
        private final boolean italic;
        private final boolean underline;
        private final boolean strikethrough;
        private final boolean code;
        private final String url;

        private Format(boolean bold, boolean italic, boolean underline,
                       boolean strikethrough, boolean code, String url) {
            this.bold = bold;
            this.italic = italic;
            this.underline = underline;
            this.strikethrough = strikethrough;
            this.code = code;
            this.url = url;
        }

        private Format withEmphasis(String marker) {
            return switch (marker) {
                case "***" -> new Format(true, true, underline, strikethrough, code, url);
                case "**" -> new Format(true, italic, underline, strikethrough, code, url);
                case "~~" -> new Format(bold, italic, underline, true, code, url);
                default -> new Format(bold, true, underline, strikethrough, code, url);
            };
        }

        private Format withUnderline(boolean value) {
            return new Format(bold, italic, value, strikethrough, code, url);
        }

        private Format withUrl(String value) {
            return new Format(bold, italic, underline, strikethrough, code, value);
        }

        private Inline toInline(String text) {
            return Inline.builder(text)
                    .bold(bold)
                    .italic(italic)
                    .underline(underline)
                    .strikethrough(strikethrough)
                    .code(code)
                    .url(url)
                    .build();
        }
    }

    // ---------- Document-Aufbau ----------

    private Document buildDocument(MarkdownModel model) {
        List<ParagraphSpec> specs = new ArrayList<>();
        for (Block block : model.getBlocks()) {
            collectParagraphs(block, specs, 0);
        }
        return buildDocumentFromSpecs(specs);
    }

    private void collectParagraphs(Block block, List<ParagraphSpec> specs, int quoteDepth) {
        if (block instanceof ParagraphBlock paragraph) {
            specs.add(new ParagraphSpec(paragraph.getInlines(), paragraphDecoration(quoteDepth), null));
        } else if (block instanceof HeadingBlock heading) {
            specs.add(new ParagraphSpec(heading.getInlines(), paragraphDecoration(quoteDepth),
                    headingTextDecoration(heading.getLevel())));
        } else if (block instanceof ListItemBlock item) {
            specs.add(new ParagraphSpec(item.getInlines(), listDecoration(item), null));
        } else if (block instanceof CodeBlockBlock codeBlock) {
            for (String line : codeBlock.getLines()) {
                specs.add(new ParagraphSpec(List.of(Inline.text(line)),
                        paragraphDecoration(quoteDepth), codeBlockTextDecoration()));
            }
        } else if (block instanceof HorizontalRuleBlock) {
            specs.add(new ParagraphSpec(List.of(Inline.text(HR_TEXT)),
                    paragraphDecoration(quoteDepth), strikethroughTextDecoration()));
        } else if (block instanceof TableBlock table) {
            specs.add(tableSpec(table));
        } else if (block instanceof ImageBlock image) {
            specs.add(new ParagraphSpec(List.of(Inline.text(IMAGE_PLACEHOLDER)),
                    paragraphDecoration(quoteDepth), new ImageDecoration(image.getUrl())));
        } else if (block instanceof QuoteBlock quote) {
            for (Block child : quote.getChildren()) {
                collectParagraphs(child, specs, quoteDepth + 1);
            }
        }
    }

    /**
     * Document-Spec einer Tabelle: Zellinhalte, getrennt durch den
     * Zero-Width-Table-Separator (so stellt die Bibliothek Tabellen dar).
     */
    private ParagraphSpec tableSpec(TableBlock table) {
        List<Inline> inlines = new ArrayList<>();
        for (int row = 0; row < table.getRows(); row++) {
            for (int column = 0; column < table.getColumns(); column++) {
                if (row * table.getColumns() + column > 0) {
                    inlines.add(Inline.text(TABLE_SEPARATOR));
                }
                inlines.addAll(table.getCell(row, column));
            }
        }
        ParagraphDecoration pd = ParagraphDecoration.builder().presets()
                .tableDecoration(new TableDecoration(table.getRows(), table.getColumns()))
                .build();
        return new ParagraphSpec(inlines, pd, null);
    }

    private ParagraphDecoration paragraphDecoration(int quoteDepth) {
        if (quoteDepth > 0) {
            return ParagraphDecoration.builder().presets().indentationLevel(quoteDepth).build();
        }
        return ParagraphDecoration.builder().presets().build();
    }

    private ParagraphDecoration listDecoration(ListItemBlock item) {
        return ParagraphDecoration.builder().presets()
                .graphicType(item.isNumbered()
                        ? ParagraphDecoration.GraphicType.NUMBERED_LIST
                        : ParagraphDecoration.GraphicType.BULLETED_LIST)
                .indentationLevel(item.getLevel())
                .build();
    }

    private TextDecoration headingTextDecoration(int level) {
        return TextDecoration.builder().presets()
                .fontSize(MarkdownStyles.headingSize(level))
                .fontWeight(FontWeight.BOLD)
                .build();
    }

    private TextDecoration codeBlockTextDecoration() {
        return TextDecoration.builder().presets()
                .fontFamily(MarkdownStyles.MONOSPACED)
                .background(MarkdownStyles.CODE_BLOCK_BACKGROUND)
                .build();
    }

    private TextDecoration strikethroughTextDecoration() {
        return TextDecoration.builder().presets().strikethrough(true).build();
    }

    private Document buildDocumentFromSpecs(List<ParagraphSpec> specs) {
        StringBuilder text = new StringBuilder();
        List<DecorationModel> decorations = new ArrayList<>();

        for (int p = 0; p < specs.size(); p++) {
            ParagraphSpec spec = specs.get(p);
            boolean last = p == specs.size() - 1;
            int paragraphStart = text.length();

            List<Inline> inlines = spec.inlines();
            if (inlines.isEmpty()) {
                // Leerer Absatz: Newline bekommt eine eigene Decoration
                if (!last) {
                    decorations.add(new DecorationModel(paragraphStart, 1,
                            TextDecoration.builder().presets().build(), spec.paragraphDecoration()));
                    text.append('\n');
                }
                continue;
            }

            for (int r = 0; r < inlines.size(); r++) {
                Inline inline = inlines.get(r);
                boolean lastRun = r == inlines.size() - 1;
                int length = inline.getText().length() + (!last && lastRun ? 1 : 0);
                decorations.add(new DecorationModel(text.length(), length,
                        textDecorationFor(inline, spec.baseTextDecoration()),
                        spec.paragraphDecoration()));
                text.append(inline.getText());
            }
            if (!last) {
                text.append('\n');
            }
        }
        // Leeres Dokument: die Bibliothek erwartet eine Default-Decoration
        // (sonst NPE in ParagraphTile beim Rendern)
        if (decorations.isEmpty()) {
            return new Document(text.toString());
        }
        return new Document(text.toString(), decorations, 0);
    }

    private com.gluonhq.richtextarea.model.Decoration textDecorationFor(Inline inline,
            com.gluonhq.richtextarea.model.Decoration base) {
        if (base instanceof ImageDecoration imageDecoration) {
            // Bild-Platzhalzer: die ImageDecoration ist die Dekoration des Laufs
            return imageDecoration;
        }
        TextDecoration.Builder builder = TextDecoration.builder();
        if (base instanceof TextDecoration textBase) {
            builder = builder.fromDecoration(textBase);
        } else {
            builder = builder.presets();
        }
        if (inline.isBold()) {
            builder.fontWeight(FontWeight.BOLD);
        }
        if (inline.isItalic()) {
            builder.fontPosture(FontPosture.ITALIC);
        }
        if (inline.isUnderline()) {
            builder.underline(true);
        }
        if (inline.isStrikethrough()) {
            builder.strikethrough(true);
        }
        if (inline.isCode()) {
            builder.fontFamily(MarkdownStyles.MONOSPACED);
        }
        if (inline.getUrl() != null) {
            builder.url(inline.getUrl());
        }
        return builder.build();
    }

    /** Ein Absatz des Zieldokuments: Inline-Läufe, Absatz- und Basis-Dekoration. */
    private record ParagraphSpec(List<Inline> inlines, ParagraphDecoration paragraphDecoration,
                                 com.gluonhq.richtextarea.model.Decoration baseTextDecoration) {
    }
}
