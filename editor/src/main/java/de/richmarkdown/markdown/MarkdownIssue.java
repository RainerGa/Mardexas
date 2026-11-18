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

/**
 * Ein vom Formatierungsprüfer festgestellter Markdown-Regelverstoß
 * (AddFeatures #16) mit Schweregrad, Meldung und Fundort.
 *
 * @param severity  Schweregrad des Verstoßes
 * @param message   lesbare Beschreibung des Verstoßes
 * @param position  Zeichenposition des Verstoßes im Dokument
 * @param line      Zeile des Verstoßes (1-basiert)
 * @param column    Spalte des Verstoßes (1-basiert)
 */
public record MarkdownIssue(Severity severity, String message, int position, int line, int column, String ruleId) {

    /** Existing callers keep their constructor; new checkers should supply a stable rule id. */
    public MarkdownIssue(Severity severity, String message, int position, int line, int column) {
        this(severity, message, position, line, column, legacyRule(message));
    }
    public MarkdownIssue(String ruleId, Severity severity, String message, int position, int line, int column) {
        this(severity, message, position, line, column, ruleId);
    }
    private static String legacyRule(String message) {
        if (message == null) return "custom";
        if (message.equals("Ungeschlossener Code-Fence")) return "fence.unclosed";
        if (message.startsWith("Leerer Link-Text")) return "link.empty";
        if (message.startsWith("Bild ohne Alt-Text")) return "image.description";
        if (message.startsWith("Tabelle mit ungleicher")) return "table.columns";
        if (message.startsWith("Unbalanced emphasis")) return "emphasis.unbalanced";
        if (message.startsWith("Überschriften-Sprung")) return "heading.jump";
        return "custom:" + message;
    }

    /** Schweregrad eines formatierungsbezogenen Verstoßes. */
    public enum Severity {
        /** Fehler, der die Dokumentstruktur beeinträchtigt (z. B. ungeschlossener Code-Block). */
        ERROR,
        /** Warnung für stilistische oder formale Probleme (z. B. leerer Link). */
        WARNING,
        /** Hinweis für unkritische Auffälligkeiten (z. B. Überschriften-Sprung). */
        HINT
    }

    public MarkdownIssue {
        if (ruleId == null || ruleId.isBlank()) throw new IllegalArgumentException("Rule id required");
        if (severity == null) {
            throw new NullPointerException("Schweregrad darf nicht null sein");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Meldung darf nicht null oder leer sein");
        }
        if (position < 0) {
            throw new IllegalArgumentException("Position darf nicht negativ sein, war: " + position);
        }
        if (line < 1) {
            throw new IllegalArgumentException("Zeile muss >= 1 sein, war: " + line);
        }
        if (column < 1) {
            throw new IllegalArgumentException("Spalte muss >= 1 sein, war: " + column);
        }
    }
}
