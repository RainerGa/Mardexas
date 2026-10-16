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
 * Ein Eintrag der Dokumentstruktur: Überschriften-Ebene, Text und
 * Caret-Position im Dokument (PROJEKTPLAN 7.1, Nachtrag).
 *
 * @param level    Überschriften-Ebene 1..6
 * @param text     Überschriftentext
 * @param position Caret-Position des Absatzanfangs
 */
public record DocumentHeading(int level, String text, int position) {

    public DocumentHeading {
        if (level < 1 || level > 6) {
            throw new IllegalArgumentException("Überschriften-Ebene muss 1..6 sein, war: " + level);
        }
        if (text == null) {
            throw new NullPointerException("Überschriftentext darf nicht null sein");
        }
        if (position < 0) {
            throw new IllegalArgumentException("Position darf nicht negativ sein, war: " + position);
        }
    }
}
