package de.richmarkdown.features.tables;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TableSortCriteriaTest {
    @Test void independentTypeDirectionAndStableTiesPreserveHeaderAndCells() {
        var table = TableData.plain(List.of(List.of("Group", "Score", "Id"), List.of("B", "9", "1"), List.of("A", "2", "2"),
                List.of("A", "10", "3"), List.of("a", "10", "4")));
        var sorted = table.sortBy(List.of(new TableData.SortKey(0, false, false), new TableData.SortKey(1, true, true)));
        assertSame(table.rows().getFirst().getFirst(), sorted.rows().getFirst().getFirst());
        assertEquals(List.of("Id", "3", "4", "2", "1"), sorted.rows().stream().map(row -> row.get(2).text()).toList());
        assertEquals("1", table.rows().get(1).get(2).text());
    }
    @Test void invalidAndDuplicateKeysAndNonnumericCellsFailBeforeAnyMutation() {
        var table = TableData.plain(List.of(List.of("H"), List.of("abc")));
        assertThrows(IllegalArgumentException.class, () -> table.sortBy(List.of()));
        assertThrows(IllegalArgumentException.class, () -> table.sortBy(List.of(new TableData.SortKey(0, true, false))));
        assertThrows(IllegalArgumentException.class, () -> table.sortBy(List.of(new TableData.SortKey(0, false, false), new TableData.SortKey(0, false, true))));
        assertEquals("abc", table.rows().get(1).getFirst().text());
    }
}
