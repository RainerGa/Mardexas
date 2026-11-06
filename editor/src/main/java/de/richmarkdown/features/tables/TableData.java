package de.richmarkdown.features.tables;

import com.gluonhq.richtextarea.model.DecorationModel;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import javafx.scene.text.TextAlignment;

/** Immutable rectangular table. Row zero is always the Markdown header. */
public record TableData(List<List<Cell>> rows) {
    public static final int MAX_CELLS = 100_000;
    private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)");
    public record Cell(String text, List<DecorationModel> decorations, TextAlignment alignment) {
        public Cell { Objects.requireNonNull(text); decorations = List.copyOf(decorations); Objects.requireNonNull(alignment); }
        public Cell(String text) { this(text, List.of(), TextAlignment.LEFT); }
    }
    public enum Calculation { SUM, AVERAGE, MINIMUM, MAXIMUM }
    public TableData {
        Objects.requireNonNull(rows);
        if (rows.isEmpty() || rows.getFirst().isEmpty()) throw new IllegalArgumentException("Die Tabelle muss mindestens eine Zelle enthalten.");
        int width = rows.getFirst().size();
        if ((long) rows.size() * width > MAX_CELLS) throw new IllegalArgumentException("Die Tabelle ist zu groß (maximal 100000 Zellen).");
        List<List<Cell>> copy = new ArrayList<>();
        for (List<Cell> row : rows) { if (row.size() != width) throw new IllegalArgumentException("Unterschiedliche Spaltenzahlen."); copy.add(List.copyOf(row)); }
        rows = List.copyOf(copy);
    }
    public static TableData plain(List<List<String>> rows) { return new TableData(rows.stream().map(r -> r.stream().map(Cell::new).toList()).toList()); }
    public int width() { return rows.getFirst().size(); }
    public TableData sort(int column, boolean numeric, boolean descending) { return sort(List.of(column), numeric, descending); }
    public TableData sort(List<Integer> columns, boolean numeric, boolean descending) {
        if (columns.isEmpty() || columns.size() > width()) throw new IllegalArgumentException("Bitte mindestens eine Sortierspalte angeben.");
        Comparator<List<Cell>> comparator = null;
        for (int column : columns) {
            checkColumn(column); Comparator<List<Cell>> next;
            if (numeric) { Map<Cell, BigDecimal> numbers = new IdentityHashMap<>(); for (int i = 1; i < rows.size(); i++) { Cell cell = rows.get(i).get(column); numbers.put(cell, number(cell)); } next = Comparator.comparing(r -> numbers.get(r.get(column))); }
            else next = Comparator.comparing(r -> r.get(column).text(), String.CASE_INSENSITIVE_ORDER);
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        if (descending) comparator = comparator.reversed();
        List<List<Cell>> result = new ArrayList<>(rows); result.subList(1, result.size()).sort(comparator); return new TableData(result);
    }
    public TableData region(int firstRow, int firstColumn, int lastRow, int lastColumn) {
        Objects.checkFromToIndex(firstRow, lastRow + 1, rows.size()); Objects.checkFromToIndex(firstColumn, lastColumn + 1, width());
        return new TableData(rows.subList(firstRow, lastRow + 1).stream().map(row -> row.subList(firstColumn, lastColumn + 1)).toList());
    }
    public TableData paste(int firstRow, int firstColumn, TableData cells) {
        Objects.checkFromIndexSize(firstRow, cells.rows.size(), rows.size()); Objects.checkFromIndexSize(firstColumn, cells.width(), width());
        List<List<Cell>> result = new ArrayList<>(rows);
        for (int r = 0; r < cells.rows.size(); r++) { List<Cell> target = new ArrayList<>(rows.get(firstRow + r)); for (int c = 0; c < cells.width(); c++) { Cell source = cells.rows.get(r).get(c); target.set(firstColumn + c, new Cell(source.text(), source.decorations(), target.get(firstColumn + c).alignment())); } result.set(firstRow + r, target); }
        return new TableData(result);
    }
    public TableData transpose() {
        List<List<Cell>> result = new ArrayList<>();
        for (int c = 0; c < width(); c++) { List<Cell> row = new ArrayList<>(); for (List<Cell> source : rows) row.add(source.get(c)); result.add(row); }
        return new TableData(result);
    }
    public TableData duplicateRow(int row) { Objects.checkIndex(row, rows.size()); List<List<Cell>> result = new ArrayList<>(rows); result.add(row + 1, rows.get(row)); return new TableData(result); }
    public TableData moveRow(int from, int to) { Objects.checkIndex(from, rows.size()); Objects.checkIndex(to, rows.size()); List<List<Cell>> result = new ArrayList<>(rows); result.add(to, result.remove(from)); return new TableData(result); }
    public TableData duplicateColumn(int column) {
        checkColumn(column); List<List<Cell>> result = new ArrayList<>();
        for (List<Cell> source : rows) { List<Cell> row = new ArrayList<>(source); row.add(column + 1, source.get(column)); result.add(row); } return new TableData(result);
    }
    public TableData moveColumn(int from, int to) {
        checkColumn(from); checkColumn(to); List<List<Cell>> result = new ArrayList<>();
        for (List<Cell> source : rows) { List<Cell> row = new ArrayList<>(source); row.add(to, row.remove(from)); result.add(row); } return new TableData(result);
    }
    /** Split before a body row; both resulting tables retain the original header. */
    public List<TableData> split(int beforeRow) {
        if (beforeRow < 1 || beforeRow >= rows.size()) throw new IllegalArgumentException("An einer Datenzeile teilen.");
        List<List<Cell>> second = new ArrayList<>(); second.add(rows.getFirst()); second.addAll(rows.subList(beforeRow, rows.size()));
        return List.of(new TableData(rows.subList(0, beforeRow)), new TableData(second));
    }
    public TableData join(TableData other, boolean omitSecondHeader) {
        if (width() != other.width()) throw new IllegalArgumentException("Tabellen müssen gleich viele Spalten besitzen.");
        List<List<Cell>> result = new ArrayList<>(rows); result.addAll(other.rows.subList(omitSecondHeader ? 1 : 0, other.rows.size())); return new TableData(result);
    }
    public BigDecimal calculate(int column, Calculation calculation) {
        checkColumn(column); List<BigDecimal> values = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) { Cell cell = rows.get(r).get(column); if (!cell.text().isBlank()) values.add(number(cell)); }
        if (values.isEmpty()) throw new IllegalArgumentException("Die Spalte enthält keine Zahlen.");
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return (switch (calculation) { case SUM -> sum; case AVERAGE -> sum.divide(BigDecimal.valueOf(values.size()), MathContext.DECIMAL128); case MINIMUM -> Collections.min(values); case MAXIMUM -> Collections.max(values); }).stripTrailingZeros();
    }
    public TableData replaceCell(int row, int column, String text) { Objects.checkIndex(row, rows.size()); checkColumn(column); List<List<Cell>> result = new ArrayList<>(rows); List<Cell> cells = new ArrayList<>(rows.get(row)); cells.set(column, new Cell(text, List.of(), cells.get(column).alignment())); result.set(row, cells); return new TableData(result); }
    private void checkColumn(int column) { Objects.checkIndex(column, width()); }
    private static BigDecimal number(Cell cell) {
        String text = cell.text().strip();
        if (text.length() > 1000 || !NUMBER.matcher(text).matches()) throw new IllegalArgumentException("Keine gültige Zahl mit Dezimalpunkt: " + text.substring(0, Math.min(80, text.length())));
        return new BigDecimal(text);
    }
}
