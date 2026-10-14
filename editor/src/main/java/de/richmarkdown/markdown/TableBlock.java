package de.richmarkdown.markdown;

import java.util.List;

/**
 * Tabelle im GitHub-Flavored-Markdown-Format (GFM) mit formatierten Zellinhalten.
 *
 * <p>Jede Zelle enthält eine Inline-Liste. Die interne flache Zellfolge ist in
 * Zeilen-Hauptordnung gespeichert und umfasst genau
 * {@code rows * columns} Einträge. Die erste Zeile dient im GFM-Markdown als
 * Kopfzeile; die Trennzeile wird vom Parser beziehungsweise Writer als
 * Syntaxelement behandelt, nicht als separate Modellzeile.
 */
public class TableBlock extends Block {

    private final int rows;
    private final int columns;
    private final List<List<Inline>> cells;

    /**
     * Erstellt eine Tabelle aus einer zeilenweise abgelegten Zellfolge.
     *
     * @param rows Anzahl der Tabellenzeilen einschließlich Kopfzeile
     * @param columns Anzahl der Tabellenspalten
     * @param cells Zellinhalte in Zeilen-Hauptordnung
     * @throws IllegalArgumentException wenn die Zellanzahl nicht
     *                                  {@code rows * columns} entspricht
     */
    public TableBlock(int rows, int columns, List<List<Inline>> cells) {
        if (cells.size() != rows * columns) {
            throw new IllegalArgumentException(
                    "Expected " + (rows * columns) + " cells but got " + cells.size());
        }
        this.rows = rows;
        this.columns = columns;
        this.cells = List.copyOf(cells);
    }

    /**
     * Liefert die Anzahl der Tabellenzeilen.
     *
     * @return Anzahl der Datenzeilen einschließlich Kopfzeile
     */
    public int getRows() {
        return rows;
    }

    /**
     * Liefert die Anzahl der Tabellenspalten.
     *
     * @return Anzahl der Tabellenspalten
     */
    public int getColumns() {
        return columns;
    }

    /**
     * Liefert den Inline-Inhalt einer Tabellenzelle.
     *
     * @param row nullbasierter Zeilenindex
     * @param column nullbasierter Spaltenindex
     * @return formatierte Inline-Folge der Zelle
     * @throws IndexOutOfBoundsException wenn der aus den Indizes berechnete
     *                                   Zellindex außerhalb der Zellfolge liegt
     */
    public List<Inline> getCell(int row, int column) {
        return cells.get(row * columns + column);
    }

    /**
     * Verkettet den Text aller Inlines einer Zelle ohne Formatierungsmarker.
     *
     * @param row nullbasierter Zeilenindex
     * @param column nullbasierter Spaltenindex
     * @return reiner Textinhalt der Zelle
     */
    public String getCellText(int row, int column) {
        StringBuilder sb = new StringBuilder();
        for (Inline inline : getCell(row, column)) {
            sb.append(inline.getText());
        }
        return sb.toString();
    }

    /**
     * Liefert alle Zellen als unveränderliche flache Folge in Zeilen-Hauptordnung.
     *
     * @return Zellinhalte mit exakt {@code getRows() * getColumns()} Elementen
     */
    public List<List<Inline>> getCells() {
        return cells;
    }
}
