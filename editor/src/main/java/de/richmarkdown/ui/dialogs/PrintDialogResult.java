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

package de.richmarkdown.ui.dialogs;

import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.PrintColor;
import javafx.print.PrintSides;
import javafx.print.Printer;

/**
 * Ergebnis des JavaFX-nativen Druckdialogs: vom Anwender gewählter Drucker
 * und Druckeinstellungen. Wird vom Aufrufer in {@code JobSettings} und
 * {@code PageLayout} übernommen; kein AWT/Swing beteiligt.
 *
 * @param printer gewählter Drucker (nie {@code null} bei Bestätigung)
 * @param copies Kopienzahl (mindestens 1)
 * @param orientation Hoch- oder Querformat
 * @param paper Papierformat
 * @param sides einseitig, Duplex oder Tumble
 * @param color Farbe oder Schwarz-Weiß
 */
public record PrintDialogResult(
        Printer printer,
        int copies,
        PageOrientation orientation,
        Paper paper,
        PrintSides sides,
        PrintColor color) {
}
