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
