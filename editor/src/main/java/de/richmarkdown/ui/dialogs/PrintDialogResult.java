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
