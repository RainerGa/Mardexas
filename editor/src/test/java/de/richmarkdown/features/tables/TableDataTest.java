package de.richmarkdown.features.tables;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class TableDataTest {
    private TableData sample() { return TableData.plain(List.of(List.of("Name", "Wert"), List.of("Beta", "10"), List.of("Alpha", "2"), List.of("alpha", "3"))); }
    @Test void defensiveCopiesAndValidation() { List<TableData.Cell> row = new ArrayList<>(List.of(new TableData.Cell("A"))); List<List<TableData.Cell>> rows = new ArrayList<>(List.of(row)); TableData table = new TableData(rows); row.clear(); rows.clear(); assertEquals("A", table.rows().getFirst().getFirst().text()); assertThrows(UnsupportedOperationException.class, () -> table.rows().clear()); assertThrows(UnsupportedOperationException.class, () -> table.rows().getFirst().clear()); assertThrows(IllegalArgumentException.class, () -> new TableData(List.of())); assertThrows(IllegalArgumentException.class, () -> TableData.plain(List.of(List.of("a"), List.of("a", "b")))); }
    @Test void stableTextAndNumericSortKeepHeader() { TableData table = sample(); assertEquals(List.of("Name", "Alpha", "alpha", "Beta"), table.sort(0, false, false).rows().stream().map(r -> r.getFirst().text()).toList()); assertEquals(List.of("Wert", "10", "3", "2"), table.sort(1, true, true).rows().stream().map(r -> r.get(1).text()).toList()); assertEquals("Beta", table.rows().get(1).getFirst().text()); assertThrows(IllegalArgumentException.class, () -> table.sort(0, true, false)); }
    @Test void transposeRoundTrip() { assertEquals(sample(), sample().transpose().transpose()); }
    @Test void duplicateAndMoveRowsColumns() { TableData table = sample(); assertEquals(5, table.duplicateRow(1).rows().size()); assertEquals(table.rows().get(1), table.duplicateRow(1).rows().get(2)); assertEquals("Alpha", table.moveRow(1, 3).rows().get(1).getFirst().text()); assertEquals("Beta", table.moveRow(1, 3).rows().get(3).getFirst().text()); assertEquals(3, table.duplicateColumn(0).width()); assertEquals("Wert", table.moveColumn(0, 1).rows().getFirst().getFirst().text()); assertThrows(IndexOutOfBoundsException.class, () -> table.moveColumn(0, 4)); }
    @Test void splitAndJoinRoundTrip() { List<TableData> split = sample().split(2); assertEquals(2, split.getFirst().rows().size()); assertEquals("Name", split.get(1).rows().getFirst().getFirst().text()); assertEquals(sample(), split.getFirst().join(split.get(1), true)); assertEquals(5, split.getFirst().join(split.get(1), false).rows().size()); assertThrows(IllegalArgumentException.class, () -> sample().split(0)); assertThrows(IllegalArgumentException.class, () -> sample().join(TableData.plain(List.of(List.of("x"))), true)); }
    @Test void allCalculationsAndReplacement() { TableData table = sample(); assertEquals(new BigDecimal("15"), table.calculate(1, TableData.Calculation.SUM)); assertEquals(new BigDecimal("5"), table.calculate(1, TableData.Calculation.AVERAGE)); assertEquals(new BigDecimal("2"), table.calculate(1, TableData.Calculation.MINIMUM)); assertEquals(new BigDecimal("1E+1"), table.calculate(1, TableData.Calculation.MAXIMUM)); assertEquals("15", table.replaceCell(1, 1, "15").rows().get(1).get(1).text()); }
    @Test void decimalPrecisionBlankAndBadNumbers() { TableData table = TableData.plain(List.of(List.of("n"), List.of("0.1"), List.of("0.2"), List.of(""))); assertEquals(new BigDecimal("0.3"), table.calculate(0, TableData.Calculation.SUM)); for (String value : List.of("NaN", "Infinity", "1,2", "1e999999999")) assertThrows(IllegalArgumentException.class, () -> TableData.plain(List.of(List.of("n"), List.of(value))).calculate(0, TableData.Calculation.SUM)); assertThrows(IllegalArgumentException.class, () -> TableData.plain(List.of(List.of("n"), List.of(""))).calculate(0, TableData.Calculation.SUM)); }
    @Test void rectangularClipboardCopyAndPaste() { TableData table = sample(); TableData region = table.region(1, 0, 2, 1); assertEquals(2, region.rows().size()); assertEquals("Alpha", region.rows().get(1).getFirst().text()); TableData changed = table.paste(2, 0, region); assertEquals("Beta", changed.rows().get(2).getFirst().text()); assertThrows(IndexOutOfBoundsException.class, () -> table.paste(3, 1, region)); }
    @Test void multiColumnSortBreaksTiesStably() { TableData data = TableData.plain(List.of(List.of("A", "B"), List.of("x", "3"), List.of("x", "1"), List.of("a", "2"))); assertEquals("1", data.sort(List.of(0, 1), false, false).rows().get(2).get(1).text()); }
    @Test void boundedCells() { assertThrows(IllegalArgumentException.class, () -> new TableData(Collections.nCopies(100_001, List.of(new TableData.Cell(""))))); }
    @Test void replacingCalculatedValuesAndPastingKeepTargetColumnAlignment() {
        var aligned = new TableData.Cell("2", List.of(), javafx.scene.text.TextAlignment.RIGHT);
        TableData table = new TableData(List.of(List.of(aligned), List.of(aligned)));
        assertEquals(javafx.scene.text.TextAlignment.RIGHT, table.replaceCell(1, 0, "4").rows().get(1).getFirst().alignment());
        TableData pasted = table.paste(1, 0, DelimitedTables.parse("3", '\t'));
        assertEquals("3", pasted.rows().get(1).getFirst().text());
        assertEquals(javafx.scene.text.TextAlignment.RIGHT, pasted.rows().get(1).getFirst().alignment());
        assertEquals("2", table.rows().get(1).getFirst().text());
    }
}
