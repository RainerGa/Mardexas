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
import com.gluonhq.richtextarea.model.ImageDecoration;
import com.gluonhq.richtextarea.model.TableDecoration;
import com.gluonhq.richtextarea.model.Document;
import com.gluonhq.richtextarea.model.ParagraphDecoration;
import com.gluonhq.richtextarea.model.TextDecoration;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wandelt Markdown-Quelltext in das interne Blockmodell oder in ein
 * {@link Document} der Rich-Text-Area-Bibliothek um.
 *
 * <p>Die Implementierung unterstützt die vom Editor verwendete CommonMark-
 * und GFM-Teilmenge: Absätze, Überschriften H1–H6, verschachtelte Listen und
 * Zitate, Code-Fences, horizontale Trennlinien, GFM-Tabellen, Bildverweise,
 * Inline-Formatierungen, Markdown- und HTML-Textlinks, verlinkte HTML-Bilder
 * und Escape-Sequenzen. Nicht abgebildete Markdown-Erweiterungen werden nicht
 * als eigenständige Modelltypen erhalten.
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
    private static final Pattern CODE_FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})[^\\r\\n]*$");
    private static final Pattern IMAGE_LINE =
            Pattern.compile("^\\s*!\\[[^\\]]*\\]\\(([^)]*)\\)\\s*$");
    private static final Pattern HTML_LINKED_IMAGE_LINE = Pattern.compile(
            "^\\s*<a\\b((?:[^>\"']|\"[^\"]*\"|'[^']*')*)>\\s*"
                    + "<img\\b((?:[^>\"']|\"[^\"]*\"|'[^']*')*)/?>\\s*</a>\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_IMAGE_LINE = Pattern.compile(
            "^\\s*<img\\b((?:[^>\"']|\"[^\"]*\"|'[^']*')*)/?>\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_ANCHOR_START = Pattern.compile(
            "<a\\b((?:[^>\"']|\"[^\"]*\"|'[^']*')*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern URI_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");
    private static final Pattern IMAGE_EXTENSION = Pattern.compile(
            "(?i).*\\.(?:avif|bmp|gif|ico|jpe?g|png|svg|tiff?|webp)$");

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

    /**
     * Parst Markdown in ein Rich-Text-Document und löst lokale Bildpfade
     * relativ zum Verzeichnis des Quelldokuments auf.
     *
     * <p>Ein führender einzelner Slash wird dabei als dokumentwurzel-relativer
     * Pfad behandelt. Absolute URLs und protocol-relative URLs bleiben
     * unverändert. Nicht vorhandene lokale Bilder werden als sichtbare
     * Platzhalter dargestellt und beim Schreiben als ursprünglicher
     * Bildverweis erhalten.
     *
     * @param markdown Markdown-Quelltext
     * @param imageBaseDirectory Verzeichnis, relativ zu dem Bildpfade gesucht
     *                           werden
     * @return Rich-Text-Document mit aufgelösten Bildadressen und Platzhaltern
     */
    public Document parseInto(String markdown, Path imageBaseDirectory) {
        return parseInto(markdown, imageBaseDirectory, true);
    }

    /** Comparison documents retain absolute addresses even for missing images. */
    public Document parseForComparison(String markdown, Path imageBaseDirectory) {
        return parseInto(markdown, imageBaseDirectory, false);
    }

    /** Resolves image metadata in an editor snapshot without re-parsing its text. */
    public Document normalizeComparisonImages(Document document, Path imageBaseDirectory) {
        Path baseDirectory = imageBaseDirectory.toAbsolutePath().normalize();
        String source = document.getText();
        StringBuilder text = new StringBuilder();
        List<DecorationModel> runs = new ArrayList<>();
        int cursor = 0;
        for (DecorationModel run : document.getDecorations()) {
            int start = Math.max(cursor, run.getStart());
            int end = Math.min(source.length(), run.getStart() + run.getLength());
            if (end <= start) continue;
            if (start > cursor) {
                runs.add(new DecorationModel(text.length(), start - cursor,
                        TextDecoration.builder().presets().build(), run.getParagraphDecoration()));
                text.append(source, cursor, start);
            }
            String segment = source.substring(start, end);
            com.gluonhq.richtextarea.model.Decoration style = run.getDecoration();
            if (style instanceof TextDecoration td) {
                ImagePlaceholder placeholder = ImagePlaceholder.fromMarker(td.getBackground()).orElse(null);
                if (placeholder != null && (segment.equals(placeholder.displayText())
                        || segment.equals(placeholder.displayText() + "\n"))) {
                    ImagePlaceholder resolved = new ImagePlaceholder(resolveImageUrl(placeholder.url(), baseDirectory),
                            placeholder.link(), placeholder.width(), placeholder.height());
                    segment = resolved.displayText() + segment.substring(placeholder.displayText().length());
                    style = TextDecoration.builder().fromDecoration(td).background(resolved.marker()).build();
                }
            } else if (style instanceof ImageDecoration image) {
                String url = resolveImageUrl(image.getUrl(), baseDirectory);
                if (url.startsWith("//")) url = "https:" + url;
                if (isUnsupportedComparisonImage(url) || isMissingLocalImage(url)) {
                    ImagePlaceholder placeholder = new ImagePlaceholder(url, image.getLink(), image.getWidth(), image.getHeight());
                    segment = placeholder.displayText() + segment.substring(Math.min(1, segment.length()));
                    style = textDecorationFor(Inline.builder(placeholder.displayText()).url(placeholder.marker()).build(), null);
                } else {
                    style = new ImageDecoration(url, image.getWidth(), image.getHeight(), image.getLink());
                }
            }
            runs.add(new DecorationModel(text.length(), segment.length(), style, run.getParagraphDecoration()));
            text.append(segment);
            cursor = end;
        }
        if (cursor < source.length()) {
            runs.add(new DecorationModel(text.length(), source.length() - cursor,
                    TextDecoration.builder().presets().build(), ParagraphDecoration.builder().presets().build()));
            text.append(source.substring(cursor));
        }
        return runs.isEmpty() ? new Document(text.toString()) : new Document(text.toString(), runs, 0);
    }

    private Document parseInto(String markdown, Path imageBaseDirectory, boolean preserveImagePaths) {
        if (imageBaseDirectory == null) {
            return parseInto(markdown);
        }
        Path baseDirectory = imageBaseDirectory.toAbsolutePath().normalize();
        List<Block> blocks = new ArrayList<>();
        for (Block block : parse(markdown).getBlocks()) {
            blocks.add(resolveBlockImages(block, baseDirectory, preserveImagePaths));
        }
        return buildDocument(MarkdownModel.of(blocks));
    }

    private Block resolveBlockImages(Block block, Path baseDirectory, boolean preserveImagePaths) {
        if (block instanceof QuoteBlock quote) {
            return new QuoteBlock(quote.getChildren().stream()
                    .map(child -> resolveBlockImages(child, baseDirectory, preserveImagePaths)).toList());
        }
        if (!(block instanceof ImageBlock image)) return block;
        String resolvedUrl = resolveImageUrl(image.getUrl(), baseDirectory);
        if (!preserveImagePaths && resolvedUrl.startsWith("//")) resolvedUrl = "https:" + resolvedUrl;
        if ((!preserveImagePaths && isUnsupportedComparisonImage(resolvedUrl)) || isMissingLocalImage(resolvedUrl)) {
            ImagePlaceholder placeholder = new ImagePlaceholder(preserveImagePaths ? image.getUrl() : resolvedUrl,
                    image.getLink(), image.getWidth(), image.getHeight());
            return new ParagraphBlock(List.of(Inline.builder(placeholder.displayText()).url(placeholder.marker()).build()));
        }
        return new ImageBlock(resolvedUrl, image.getWidth(), image.getHeight(), image.getLink());
    }

    private String resolveImageUrl(String url, Path baseDirectory) {
        if (url == null || url.isBlank() || url.startsWith("//")
                || URI_SCHEME.matcher(url).find()) {
            return url;
        }
        String path = url.startsWith("/") ? url.substring(1) : url;
        return baseDirectory.resolve(path).normalize().toUri().toString();
    }

    private boolean isUnsupportedComparisonImage(String url) {
        if (url == null || url.isBlank()) return true;
        try {
            java.net.URI uri = java.net.URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null) return true;
            return switch (scheme.toLowerCase(java.util.Locale.ROOT)) {
                case "http", "https" -> uri.getHost() == null;
                case "file" -> { Paths.get(uri); yield false; }
                case "jar" -> { uri.toURL(); yield false; }
                case "data" -> !url.startsWith("data:image/") || !url.contains(",");
                default -> true;
            };
        } catch (IllegalArgumentException | java.net.MalformedURLException invalid) {
            return true;
        }
    }

    private boolean isMissingLocalImage(String url) {
        if (url == null || url.isBlank()) return true;
        if (!url.startsWith("file:")) {
            return false;
        }
        return !Files.isRegularFile(Paths.get(java.net.URI.create(url)));
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
        if (cells < 1 || cells == 1 && line.indexOf('|') < 0) {
            // A single column requires an explicit pipe to distinguish thematic rules / Setext headings.
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
            } else if (isHtmlLinkedImageLine(line)) {
                blocks.add(parseHtmlLinkedImage(line));
                i++;
            } else if (isHtmlImageLine(line)) {
                blocks.add(parseHtmlImage(line));
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
            String candidate = line.stripTrailing();
            int indent = 0;
            while (indent < candidate.length() && candidate.charAt(indent) == ' ') indent++;
            int run = indent;
            while (run < candidate.length() && candidate.charAt(run) == fence.charAt(0)) run++;
            if (indent <= 3 && run - indent >= fence.length() && run == candidate.length()) {
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
                String marker = matcher.group(2);
                boolean numbered = Character.isDigit(marker.charAt(0));
                int startNumber = 1;
                if (numbered) {
                    // Extract start number from marker (e.g., "3." or "3)")
                    String numStr = marker.replaceAll("[.)]", "");
                    try {
                        startNumber = Integer.parseInt(numStr);
                    } catch (NumberFormatException ignored) {
                        startNumber = 1;
                    }
                }
                String text = matcher.group(3) == null ? "" : matcher.group(3).trim();
                ListItemBlock item = new ListItemBlock(level, numbered, startNumber, parseInlines(text));
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
                            new ListItemBlock(previous.getLevel(), previous.isNumbered(), previous.getStartNumber(), merged));
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
        String[] separators = splitTableRow(lines.get(start + 1));
        List<javafx.scene.text.TextAlignment> alignments = new ArrayList<>();
        for (int c = 0; c < columnCount; c++) {
            String separator = c < separators.length ? separators[c].strip() : "";
            alignments.add(separator.endsWith(":") ? (separator.startsWith(":") ? javafx.scene.text.TextAlignment.CENTER : javafx.scene.text.TextAlignment.RIGHT) : javafx.scene.text.TextAlignment.LEFT);
        }
        blocks.add(new TableBlock(rows.size(), columnCount, cells, alignments));
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
        String url = matcher.group(1).trim();
        if (url.isEmpty() || isImageUrl(url)) {
            return new ImageBlock(url);
        }
        int imageStart = line.indexOf("![");
        return new ParagraphBlock(parseInlines(line.substring(imageStart + 1)));
    }

    private boolean isImageUrl(String url) {
        if (url.regionMatches(true, 0, "data:image/", 0, "data:image/".length())) {
            return true;
        }
        int queryStart = url.indexOf('?');
        int fragmentStart = url.indexOf('#');
        int suffixStart = queryStart < 0 ? fragmentStart
                : fragmentStart < 0 ? queryStart : Math.min(queryStart, fragmentStart);
        String path = suffixStart < 0 ? url : url.substring(0, suffixStart);
        return IMAGE_EXTENSION.matcher(path).matches();
    }

    private Block parseHtmlLinkedImage(String line) {
        Matcher matcher = HTML_LINKED_IMAGE_LINE.matcher(line);
        matcher.matches();
        String link = decodeHtmlEntities(htmlAttribute(matcher.group(1), "href"));
        String url = decodeHtmlEntities(htmlAttribute(matcher.group(2), "src"));
        int width = parseHtmlDimension(htmlAttribute(matcher.group(2), "width"));
        int height = parseHtmlDimension(htmlAttribute(matcher.group(2), "height"));
        return new ImageBlock(url, width, height, link);
    }

    private Block parseHtmlImage(String line) {
        Matcher matcher = HTML_IMAGE_LINE.matcher(line);
        matcher.matches();
        String url = decodeHtmlEntities(htmlAttribute(matcher.group(1), "src"));
        int width = parseHtmlDimension(htmlAttribute(matcher.group(1), "width"));
        int height = parseHtmlDimension(htmlAttribute(matcher.group(1), "height"));
        return new ImageBlock(url, width, height, null);
    }

    private boolean isHtmlImageLine(String line) {
        Matcher matcher = HTML_IMAGE_LINE.matcher(line);
        if (!matcher.matches()) {
            return false;
        }
        String src = htmlAttribute(matcher.group(1), "src");
        return src != null && !src.isBlank();
    }

    private boolean isHtmlLinkedImageLine(String line) {
        Matcher matcher = HTML_LINKED_IMAGE_LINE.matcher(line);
        if (!matcher.matches()) {
            return false;
        }
        String href = htmlAttribute(matcher.group(1), "href");
        String src = htmlAttribute(matcher.group(2), "src");
        return href != null && !href.isBlank() && src != null && !src.isBlank();
    }

    private String htmlAttribute(String attributes, String name) {
        Pattern attribute = Pattern.compile("(?:^|\\s)" + Pattern.quote(name)
                + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = attribute.matcher(attributes);
        if (!matcher.find()) {
            return null;
        }
        for (int group = 1; group <= 3; group++) {
            if (matcher.group(group) != null) {
                return matcher.group(group);
            }
        }
        return null;
    }

    private int parseHtmlDimension(String value) {
        if (value == null) {
            return -1;
        }
        int length = 0;
        while (length < value.length() && Character.isDigit(value.charAt(length))) {
            length++;
        }
        if (length == 0) {
            return -1;
        }
        try {
            return Integer.parseInt(value.substring(0, length));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String decodeHtmlEntities(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }

    private int indexOfIgnoreCase(String text, String value, int fromIndex) {
        int lastStart = text.length() - value.length();
        for (int i = fromIndex; i <= lastStart; i++) {
            if (text.regionMatches(true, i, value, 0, value.length())) {
                return i;
            }
        }
        return -1;
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
                String content = contents.get(i).trim();
                if (CODE_FENCE.matcher(content).matches()) {
                    int end = i + 1;
                    while (end < depths.size() && depths.get(end) == depth) end++;
                    int consumed = collectCodeBlock(contents.subList(i, end), 0, children);
                    i += consumed;
                } else {
                    children.add(IMAGE_LINE.matcher(content).matches()
                            ? parseImageLine(content) : new ParagraphBlock(parseInlines(content)));
                    i++;
                }
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
        List<String> paragraphLines = new ArrayList<>();
        while (i < lines.size()) {
            String line = lines.get(i);
            int setextLevel = setextHeadingLevel(line);
            if (setextLevel > 0 && !paragraphLines.isEmpty()) {
                blocks.add(new HeadingBlock(setextLevel, parseInlines(String.join(" ", paragraphLines))));
                return i + 1;
            }
            if (line.isBlank() || isBlockStart(line)) {
                break;
            }
            paragraphLines.add(line.trim());
            i++;
        }
        for (String line : paragraphLines) {
            // Das Editor-Dokument kennt keinen weichen Zeilenumbruch innerhalb
            // eines Absatzes; jede Markdown-Textzeile muss daher als eigener
            // Absatz in den Editor, damit der Zeilenumbruch sichtbar bleibt.
            blocks.add(new ParagraphBlock(parseInlines(line)));
        }
        return i;
    }

    /** CommonMark Setext underline: up to three spaces, contiguous '=' or '-', then whitespace. */
    private static int setextHeadingLevel(String line) {
        int start = 0;
        while (start < line.length() && line.charAt(start) == ' ') start++;
        if (start > 3 || start == line.length()) return 0;
        char marker = line.charAt(start);
        if (marker != '=' && marker != '-') return 0;
        int end = start;
        while (end < line.length() && line.charAt(end) == marker) end++;
        for (int i = end; i < line.length(); i++) {
            if (line.charAt(i) != ' ' && line.charAt(i) != '\t') return 0;
        }
        return marker == '=' ? 1 : 2;
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

            // CommonMark code spans use equal-length backtick runs.
            if (c == '`') {
                int runEnd = i;
                while (runEnd < text.length() && text.charAt(runEnd) == '`') runEnd++;
                int length = runEnd - i;
                int candidate = runEnd;
                int close = -1;
                while (candidate < text.length()) {
                    int next = text.indexOf('`', candidate);
                    if (next < 0) break;
                    int nextEnd = next;
                    while (nextEnd < text.length() && text.charAt(nextEnd) == '`') nextEnd++;
                    if (nextEnd - next == length) { close = next; break; }
                    candidate = nextEnd;
                }
                if (close > runEnd) {
                    String code = text.substring(runEnd, close).replace('\n', ' ');
                    if (code.startsWith(" ") && code.endsWith(" ") && !code.isBlank()) code = code.substring(1, code.length() - 1);
                    flush(plain, result, base);
                    result.add(Inline.builder(code).code(true).build());
                    i = close + length;
                    continue;
                }
            }

            // HTML-Anker mit Textinhalt
            if (c == '<') {
                Matcher anchor = HTML_ANCHOR_START.matcher(text);
                anchor.region(i, text.length());
                if (anchor.lookingAt()) {
                    String href = htmlAttribute(anchor.group(1), "href");
                    int close = indexOfIgnoreCase(text, "</a>", anchor.end());
                    if (href != null && close >= anchor.end()) {
                        flush(plain, result, base);
                        String inner = decodeHtmlEntities(text.substring(anchor.end(), close));
                        result.addAll(scan(inner, base.withUrl(decodeHtmlEntities(href))));
                        i = close + 4;
                        continue;
                    }
                }
            }

            // Link
            if (c == '[') {
                int closeBracket = findUnescaped(text, "]", i + 1);
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

            // Autolink in spitzen Klammern: <https://...> oder <http://...>
            if (c == '<' && (text.startsWith("<http://", i) || text.startsWith("<https://", i))) {
                int close = text.indexOf('>', i + 1);
                if (close > 0) {
                    String url = text.substring(i + 1, close);
                    if (!url.isEmpty() && !containsAny(url, WHITESPACE_DELIMS)
                            && (url.startsWith("http://") || url.startsWith("https://"))) {
                        flush(plain, result, base);
                        result.add(Inline.builder(url).url(url).build());
                        i = close + 1;
                        continue;
                    }
                }
            }

            // Autolink: bare http(s)-URL
            int urlLen = matchUrl(text, i);
            if (urlLen > 0) {
                flush(plain, result, base);
                String urlText = text.substring(i, i + urlLen);
                result.add(Inline.builder(urlText).url(urlText).build());
                i += urlLen;
                continue;
            }

            // Inline-Image-Syntax ![...](url) für Webseiten: führendes '!'
            // überspringen, sodass der Link-Handler das [text](url) verarbeitet
            // ohne den '!' als Plain-Text zu hinterlassen.
            if (c == '!' && i + 1 < text.length() && text.charAt(i + 1) == '[') {
                i++;
                continue;
            }

            plain.append(c);
            i++;
        }
        flush(plain, result, base);
        return result;
    }

    private static final char[] WHITESPACE_DELIMS = {' ', '\t', '\n', '\r', '<', '>', ')', ']'};

    private static boolean containsAny(String s, char[] chars) {
        for (int k = 0; k < s.length(); k++) {
            for (char ch : chars) {
                if (s.charAt(k) == ch) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int matchUrl(String text, int i) {
        if (!text.regionMatches(i, "http://", 0, 7) && !text.regionMatches(i, "https://", 0, 8)) {
            return -1;
        }
        if (i > 0 && Character.isLetterOrDigit(text.charAt(i - 1))) {
            return -1;
        }
        int end = i;
        int n = text.length();
        while (end < n) {
            char ch = text.charAt(end);
            if (Character.isWhitespace(ch)) {
                break;
            }
            if (ch == '<' || ch == '>' || ch == ')' || ch == ']' || ch == ','
                    || ch == ';' || ch == '!' || ch == '?') {
                break;
            }
            end++;
        }
        while (end > i && isTrailingPunctuation(text.charAt(end - 1))) {
            end--;
        }
        return end > i ? end - i : -1;
    }

    private static boolean isTrailingPunctuation(char ch) {
        return ch == ',' || ch == ';' || ch == ':' || ch == '.' || ch == '!' || ch == '?';
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
        int index = findUnescaped(text, marker, from);
        while (index >= 0 && marker.equals("*") && index + 1 < text.length() && text.charAt(index + 1) == '*') {
            index = findUnescaped(text, marker, index + 2);
        }
        return index;
    }

    private static int findUnescaped(String text, String marker, int from) {
        int candidate = text.indexOf(marker, from);
        while (candidate >= 0) {
            int slashes = 0;
            for (int i = candidate - 1; i >= 0 && text.charAt(i) == '\\'; i--) slashes++;
            if (slashes % 2 == 0) return candidate;
            candidate = text.indexOf(marker, candidate + marker.length());
        }
        return -1;
    }

    private boolean isEscapable(char c) {
        return "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~".indexOf(c) >= 0;
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
                        paragraphDecoration(quoteDepth), codeBlockTextDecoration(codeBlock.getLanguage())));
            }
        } else if (block instanceof HorizontalRuleBlock) {
            specs.add(new ParagraphSpec(List.of(Inline.text(HR_TEXT)),
                    paragraphDecoration(quoteDepth), strikethroughTextDecoration()));
        } else if (block instanceof TableBlock table) {
            specs.add(tableSpec(table));
        } else if (block instanceof ImageBlock image && image.getUrl().isBlank()) {
            ImagePlaceholder placeholder = new ImagePlaceholder(image.getUrl(), image.getLink(), image.getWidth(), image.getHeight());
            specs.add(new ParagraphSpec(List.of(Inline.builder(placeholder.displayText()).url(placeholder.marker()).build()),
                    paragraphDecoration(quoteDepth), null));
        } else if (block instanceof ImageBlock image) {
            specs.add(new ParagraphSpec(List.of(Inline.text(IMAGE_PLACEHOLDER)),
                    paragraphDecoration(quoteDepth), new ImageDecoration(image.getUrl(),
                            image.getWidth(), image.getHeight(), image.getLink())));
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
        javafx.scene.text.TextAlignment[][] alignments = new javafx.scene.text.TextAlignment[table.getRows()][table.getColumns()];
        for (int r = 0; r < table.getRows(); r++) for (int c = 0; c < table.getColumns(); c++) alignments[r][c] = table.getColumnAlignment(c);
        ParagraphDecoration pd = ParagraphDecoration.builder().presets()
                .tableDecoration(new TableDecoration(table.getRows(), table.getColumns(), alignments))
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
                .startNumber(item.getStartNumber())
                .build();
    }

    private TextDecoration headingTextDecoration(int level) {
        return TextDecoration.builder().presets()
                .fontSize(MarkdownStyles.headingSize(level))
                .fontWeight(FontWeight.BOLD)
                .build();
    }

    private TextDecoration codeBlockTextDecoration(String language) {
        return TextDecoration.builder().presets()
                .fontFamily(MarkdownStyles.MONOSPACED)
                .background(MarkdownStyles.CODE_BLOCK_BACKGROUND)
                .codeLanguage(language)
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
            if (ImagePlaceholder.fromMarker(inline.getUrl()).isPresent()) {
                builder.background(inline.getUrl());
                builder.foreground("#b00020");
                builder.fontPosture(FontPosture.ITALIC);
            } else {
                builder.url(inline.getUrl());
                builder.foreground("blue");
                builder.underline(true);
            }
        }
        return builder.build();
    }

    /** Ein Absatz des Zieldokuments: Inline-Läufe, Absatz- und Basis-Dekoration. */
    private record ParagraphSpec(List<Inline> inlines, ParagraphDecoration paragraphDecoration,
                                 com.gluonhq.richtextarea.model.Decoration baseTextDecoration) {
    }
}
