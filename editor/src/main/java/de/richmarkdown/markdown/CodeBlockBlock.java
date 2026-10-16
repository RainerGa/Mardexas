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

import java.util.List;

/**
 * Mehrzeiliger Markdown-Codeblock mit optionaler Fence-Info-Zeichenkette.
 *
 * <p>Der Inhalt wird zeilenweise und ohne Syntax-Highlighting-Metadaten
 * gespeichert. Eine nicht angegebene Sprache wird als leere Zeichenkette
 * repräsentiert.
 */
public class CodeBlockBlock extends Block {

    private final String language;
    private final List<String> lines;

    /**
     * Erstellt einen Codeblock ohne Info-String.
     *
     * @param lines Codezeilen in unveränderlicher Ausgabereihenfolge
     */
    public CodeBlockBlock(List<String> lines) {
        this("", lines);
    }

    /**
     * Erstellt einen Codeblock mit optionalem Info-String.
     *
     * @param language Sprache oder anderer Fence-Info-Text; {@code null} wird
     *                 als leerer Info-String normalisiert
     * @param lines Codezeilen in Ausgabereihenfolge
     */
    public CodeBlockBlock(String language, List<String> lines) {
        this.language = language == null ? "" : language;
        this.lines = List.copyOf(lines);
    }

    /**
     * Liefert den Info-String der Code-Fence (z. B. {@code java}).
     *
     * @return Info-String oder leere Zeichenkette, falls keiner angegeben war
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Liefert die unveränderliche Folge der Codezeilen.
     *
     * @return Codezeilen in ihrer ursprünglichen Reihenfolge
     */
    public List<String> getLines() {
        return lines;
    }
}
