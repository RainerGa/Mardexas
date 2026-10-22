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

package de.richmarkdown.editor;

/**
 * Dokumentstatistik nach dem Vorbild des LibreOffice-Writer-Dialogs
 * „Extras → Statistik“ (AddFeatures #3).
 *
 * <p>Die Zählungen orientieren sich an den Feldern des LO-Statistikdialogs:
 * Wörter, Zeichen (mit und ohne Leerzeichen), Leerzeichen, Sätze, Absätze,
 * Zeilen, Tabellen und Bilder. Alle Werte leiten sich ausschließlich aus dem
 * sichtbaren Dokumentinhalt ab; es werden keine Zusatzinformationen in der
 * Markdown-Datei gespeichert.
 *
 * <p>Zählregeln (dokumentierte Heuristiken, angelehnt an LO Writer):
 * <ul>
 *   <li><b>Wörter:</b> zusammenhängende Zeichenfolgen ohne Whitespace –
 *       Whitespace (Leerzeichen, Tabs, Zeilenumbrüche) trennt Wörter.</li>
 *   <li><b>Zeichen (mit Leerzeichen):</b> alle sichtbaren Zeichen inklusive
 *       Leerzeichen und Tabs, ohne Absatzmarken ({@code \n}) und ohne
 *       nicht sichtbare Steuerzeichen (Bild-Platzhalter, Tabellen-Trenner).</li>
 *   <li><b>Zeichen (ohne Leerzeichen):</b> Teilmenge ohne Whitespace.</li>
 *   <li><b>Leerzeichen:</b> alle Whitespace-Zeichen außer Absatzmarken.</li>
 *   <li><b>Sätze:</b> Zeichenfolgen, die mit {@code .}, {@code !} oder
 *       {@code ?} enden; ein Text ohne solches Zeichen zählt als ein Satz.</li>
 *   <li><b>Absätze bzw. Zeilen:</b> Zeilen des Dokuments (der Editor bildet
 *       eine Zeile als Absatz ab); ein leeres Dokument hat 0 Absätze.</li>
 *   <li><b>Tabellen bzw. Bilder:</b> Anzahl der Tabellen-Dekorationen bzw.
 *       Bild-Dekorationen im Dokumentmodell.</li>
 * </ul>
 *
 * @param words Anzahl der Wörter
 * @param charactersWithSpaces Zeichen inklusive Leerzeichen
 * @param charactersWithoutSpaces Zeichen ohne Leerzeichen
 * @param spaces Anzahl der Leerzeichen (Whitespace ohne Absatzmarken)
 * @param sentences Anzahl der Sätze
 * @param paragraphs Anzahl der Absätze (Dokumentzeilen)
 * @param lines Anzahl der Zeilen (entspricht den Absätzen im Editor)
 * @param tables Anzahl der Tabellen
 * @param images Anzahl der Bilder
 */
public record DocumentStatistics(long words, long charactersWithSpaces,
                                 long charactersWithoutSpaces, long spaces,
                                 long sentences, long paragraphs, long lines,
                                 long tables, long images) {

    /**
     * Liefert eine leere Statistik (alle Zähler auf null) – etwa solange kein
     * Dokument geladen ist.
     *
     * @return Statistik ohne gezählte Inhalte
     */
    public static DocumentStatistics empty() {
        return new DocumentStatistics(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Ermittelt die Statistik für den angegebenen Dokumenttext.
     *
     * @param text sichtbarer Dokumenttext; Absätze sind durch {@code \n}
     *             getrennt; {@code null} wird wie leer behandelt
     * @param tables Anzahl der Tabellen im Dokument
     * @param images Anzahl der Bilder im Dokument
     * @return berechnete Dokumentstatistik
     */
    public static DocumentStatistics of(String text, int tables, int images) {
        String visible = stripNonVisible(text);
        if (visible.isEmpty()) {
            return new DocumentStatistics(0, 0, 0, 0, 0, 0, 0, tables, images);
        }
        long charsWithSpaces = 0;
        long charsWithoutSpaces = 0;
        long spaces = 0;
        long words = 0;
        boolean inWord = false;
        for (int i = 0; i < visible.length(); i++) {
            char c = visible.charAt(i);
            if (c == '\n') {
                // Absatzmarke: zählt nicht als Zeichen, trennt aber Wörter
                inWord = false;
                continue;
            }
            charsWithSpaces++;
            if (Character.isWhitespace(c)) {
                spaces++;
                inWord = false;
            } else {
                charsWithoutSpaces++;
                if (!inWord) {
                    words++;
                    inWord = true;
                }
            }
        }
        long lines = countLines(visible);
        return new DocumentStatistics(words, charsWithSpaces, charsWithoutSpaces,
                spaces, countSentences(visible), lines, lines, tables, images);
    }

    /** Entfernt nicht sichtbare Steuerzeichen (Bild-Platzhalter, Tabellen-Trenner). */
    private static String stripNonVisible(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\ufffc", "")   // Bild-Platzhalter
                .replace("\u200b", "")   // Tabellenzell-Trenner
                .replace("\ufeff", "");       // Zero-Width-No-Break-Space
    }

    /** Anzahl der Dokumentzeilen; ein leeres Dokument hat 0 Zeilen. */
    private static long countLines(String visible) {
        if (visible.isEmpty()) {
            return 0;
        }
        return visible.split("\n", -1).length;
    }

    /**
     * Anzahl der Sätze: Zeichenfolgen, die mit Satzendezeichen enden,
     * zuzüglich eines Restsatzes, falls Text ohne Satzendezeichen vorhanden
     * ist (LO-Writer-Heuristik).
     */
    private static long countSentences(String visible) {
        long sentences = 0;
        for (int i = 0; i < visible.length(); i++) {
            char c = visible.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                sentences++;
            }
        }
        boolean hasTextWithoutSentenceEnd = false;
        for (int i = visible.length() - 1; i >= 0; i--) {
            char c = visible.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            hasTextWithoutSentenceEnd = c != '.' && c != '!' && c != '?';
            break;
        }
        return sentences + (hasTextWithoutSentenceEnd ? 1 : 0);
    }

    /**
     * Validiert die Tabellen- und Bildanzahl beim Bau der Statistik.
     *
     * @throws IllegalArgumentException wenn Tabellen oder Bilder negativ sind
     */
    public DocumentStatistics {
        if (tables < 0 || images < 0) {
            throw new IllegalArgumentException(
                    "tables/images must not be negative: " + tables + "/" + images);
        }
    }
}
