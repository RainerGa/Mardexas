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

package de.richmarkdown.ui;

/**
 * Zeilen- und Spaltenanzahl einer im Einfügedialog konfigurierten Tabelle.
 *
 * <p>Der Datensatz transportiert das Dialogergebnis an
 * {@link de.richmarkdown.editor.MarkdownEditor#insertTable(int, int)}.
 * Tabellenzeilen umfassen die GFM-Kopfzeile.
 *
 * @param rows Anzahl der Zeilen einschließlich Kopfzeile
 * @param columns Anzahl der Spalten
 */
public record TableSize(int rows, int columns) {
}
