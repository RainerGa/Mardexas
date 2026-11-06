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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateless-Prüfer für Markdown-Regelverstöße (AddFeatures #16).
 *
 * <p>Der Prüfer arbeitet auf dem reinen Markdown-Quelltext
 * ({@link MarkdownEditor#getMarkdown()} oder direkt
 * {@link #check(String)}) und liefert eine Liste von
 * {@link MarkdownIssue} mit Position, Zeile und Spalte. Die Positionen können
 * zur Navigation per {@link MarkdownEditor#goToPosition(int)} genutzt werden.
 *
 * <p>Prüfregeln:
 * <ol>
 *   <li>Ungeschlossener Code-Fence (```` ``` ```` ohne Schließung) → ERROR</li>
 *   <li>Leerer Link-Text {@code [](url)} oder leere URL {@code [text]()} → ERROR</li>
 *   <li>Tabelle mit ungleicher Spaltenanzahl pro Zeile → WARNING</li>
 *   <li>Unbalanced emphasis (ungerade Anzahl {@code *} pro Absatz) → WARNING</li>
 *   <li>Bild ohne Alt-Text oder ohne URL → WARNING</li>
 *   <li>Überschriften-Sprung (Ebene um mehr als 1 übersprungen) → HINT</li>
 * </ol>
 */
public final class MarkdownChecker {

    /** Leerer Link oder leere URL: {@code [text](url)}. */
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]*)\\]\\(([^\\)]*)\\)");
    /** Bild: {@code ![alt](url)}. */
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(([^\\)]*)\\)");
    /** Code-Fence am Zeilenanfang: {@code ```} optional gefolgt von Sprache. */
    private static final Pattern FENCE_PATTERN = Pattern.compile("^\\s*```(\\w*)");
    /** Tabellen-Zeile: mit Pipe-Zeichen eingefasste Zellen. */
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("^\\s*\\|.*\\|\\s*$");
    /** Überschrift am Zeilenanfang: 1-6× {@code #} gefolgt von Leerzeichen. */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s");

    private MarkdownChecker() {
    }

    /**
     * Prüft den angegebenen Markdown-Text auf Regelverstöße.
     *
     * @param markdown Markdown-Quelltext (kann {@code null} sein)
     * @return Liste der festgestellten Verstöße, leer bei gültigem Dokument
     */
    public static List<MarkdownIssue> check(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        List<MarkdownIssue> issues = new ArrayList<>();
        String[] lines = de.richmarkdown.features.checking.MarkdownSourceMask.prose(markdown).split("\n", -1);
        int[] lineStarts = lineStarts(markdown, lines);

        // Regel 1: ungeschlossener Code-Fence
        checkUnclosedFence(markdown.split("\n", -1), lineStarts, markdown, issues);

        // Regeln 2 und 5 pro Zeile: leere Links und Bilder
        for (int i = 0; i < lines.length; i++) {
            checkLinks(lines[i], lineStarts[i], issues);
            checkImages(lines[i], lineStarts[i], issues);
        }

        // Regel 3: Tabellen mit ungleicher Spaltenanzahl
        checkTables(lines, lineStarts, markdown, issues);

        // Regel 4: unbalanced emphasis pro Absatz
        checkEmphasis(lines, lineStarts, markdown, issues);

        // Regel 6: Überschriften-Sprung
        checkHeadingJumps(lines, lineStarts, markdown, issues);

        List<MarkdownIssue> positioned = new ArrayList<>();
        for (MarkdownIssue issue : issues) { int found = java.util.Arrays.binarySearch(lineStarts, issue.position()); int line = found >= 0 ? found : -found - 2; positioned.add(new MarkdownIssue(issue.severity(), issue.message(), issue.position(), line + 1, issue.position() - lineStarts[line] + 1)); }
        return List.copyOf(positioned);
    }

    private static void checkUnclosedFence(String[] lines, int[] lineStarts,
                                           String markdown, List<MarkdownIssue> issues) {
        char fence = 0; int length = 0, fenceStart = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i]; int at = 0; while (at < line.length() && at < 4 && line.charAt(at) == ' ') at++;
            if (at > 3 || at == line.length()) continue;
            char marker = line.charAt(at); int end = at; while (end < line.length() && line.charAt(end) == marker) end++;
            if (fence == 0 && (marker == '`' || marker == '~') && end - at >= 3) { fence = marker; length = end - at; fenceStart = i; }
            else if (marker == fence && end - at >= length && line.substring(end).isBlank()) fence = 0;
        }
        if (fence != 0) { int position = lineStarts[fenceStart]; issues.add(new MarkdownIssue(MarkdownIssue.Severity.ERROR, "Ungeschlossener Code-Fence", position, fenceStart + 1, 1)); }
    }

    private static void checkLinks(String line, int lineStart, List<MarkdownIssue> issues) {
        Matcher lm = LINK_PATTERN.matcher(line);
        while (lm.find()) {
            // Ein Bild wird als ![alt](url) geschrieben; der Link-Muster würde
            // andernfalls das [alt](url) darin fälschlich als Link melden.
            if (lm.start() > 0 && line.charAt(lm.start() - 1) == '!') {
                continue;
            }
            String text = lm.group(1);
            String url = lm.group(2);
            if (text.isBlank() || url.isBlank()) {
                int position = lineStart + lm.start();
                int[] lc = toLineColumn(line, position - lineStart);
                issues.add(new MarkdownIssue(MarkdownIssue.Severity.ERROR,
                        "Leerer Link-Text oder leere URL", position, lc[0], lc[1]));
            }
        }
    }

    private static void checkImages(String line, int lineStart, List<MarkdownIssue> issues) {
        Matcher im = IMAGE_PATTERN.matcher(line);
        while (im.find()) {
            String alt = im.group(1);
            String url = im.group(2);
            if (alt.isBlank() || url.isBlank()) {
                int position = lineStart + im.start();
                int[] lc = toLineColumn(line, position - lineStart);
                issues.add(new MarkdownIssue(MarkdownIssue.Severity.WARNING,
                        "Bild ohne Alt-Text oder ohne URL", position, lc[0], lc[1]));
            }
        }
    }

    private static void checkTables(String[] lines, int[] lineStarts,
                                    String markdown, List<MarkdownIssue> issues) {
        int i = 0;
        while (i < lines.length) {
            if (TABLE_ROW_PATTERN.matcher(lines[i]).matches()) {
                int groupStart = i;
                while (i < lines.length && TABLE_ROW_PATTERN.matcher(lines[i]).matches()) {
                    i++;
                }
                int headerCols = -1;
                for (int r = groupStart; r < i; r++) {
                    int cols = countCells(lines[r]);
                    if (headerCols == -1) {
                        headerCols = cols;
                    } else if (cols != headerCols) {
                        int position = lineStarts[r];
                        int[] lc = toLineColumn(markdown, position);
                        issues.add(new MarkdownIssue(MarkdownIssue.Severity.WARNING,
                                "Tabelle mit ungleicher Spaltenanzahl", position, lc[0], lc[1]));
                    }
                }
            } else {
                i++;
            }
        }
    }

    private static void checkEmphasis(String[] lines, int[] lineStarts,
                                      String markdown, List<MarkdownIssue> issues) {
        int paraStart = 0;
        for (int i = 0; i <= lines.length; i++) {
            boolean end = (i == lines.length) || lines[i].isBlank();
            if (end) {
                if (i > paraStart) {
                    StringBuilder paragraph = new StringBuilder();
                    for (int j = paraStart; j < i; j++) {
                        String content = lines[j];
                        if (content.strip().matches("(?:\\*\\s*){3,}")) content = "";
                        else content = content.replaceFirst("^ {0,3}[*+-]\\s+", "");
                        paragraph.append(content).append('\n');
                    }
                    String withoutCode = paragraph.toString().replaceAll("`[^`]*`", "");
                    long starCount = withoutCode.chars().filter(ch -> ch == '*').count();
                    if (starCount % 2 != 0) {
                        int position = lineStarts[paraStart];
                        int[] lc = toLineColumn(markdown, position);
                        issues.add(new MarkdownIssue(MarkdownIssue.Severity.WARNING,
                                "Unbalanced emphasis (ungerade Anzahl '*' im Absatz)",
                                position, lc[0], lc[1]));
                    }
                }
                paraStart = i + 1;
            }
        }
    }

    private static void checkHeadingJumps(String[] lines, int[] lineStarts,
                                          String markdown, List<MarkdownIssue> issues) {
        Integer prevLevel = null;
        for (int i = 0; i < lines.length; i++) {
            Matcher hm = HEADING_PATTERN.matcher(lines[i]);
            if (hm.find()) {
                int level = hm.group(1).length();
                if (prevLevel != null && level - prevLevel > 1) {
                    int position = lineStarts[i];
                    int[] lc = toLineColumn(markdown, position);
                    issues.add(new MarkdownIssue(MarkdownIssue.Severity.HINT,
                            "Überschriften-Sprung von H" + prevLevel + " zu H" + level,
                            position, lc[0], lc[1]));
                }
                prevLevel = level;
            }
        }
    }

    private static int countCells(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) {
            return 0;
        }
        String inner = trimmed.substring(1, trimmed.length() - 1);
        return inner.split("\\|", -1).length;
    }

    /** Mappt eine Zeichenposition auf (Zeile, Spalte), beide 1-basiert. */
    private static int[] toLineColumn(String text, int position) {
        // Final results are normalized once using binary search over lineStarts.
        return new int[]{1, position + 1};
    }

    /** Startposition jeder Zeile im Gesamttext. */
    private static int[] lineStarts(String markdown, String[] lines) {
        int[] starts = new int[lines.length];
        int pos = 0;
        for (int i = 0; i < lines.length; i++) {
            starts[i] = pos;
            pos += lines[i].length() + 1; // Zeile + Zeilenumbruch
        }
        return starts;
    }
}
