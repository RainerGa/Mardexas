package de.richmarkdown.features.tables;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class DelimitedTablesTest {
    @Test void csvQuotesBomCrLfTrailingEmptyAndRaggedRows() { TableData data = DelimitedTables.parse("\ufeffName,Wert,Notiz\r\n\"a,b\",\"a\"\"b\",\r\nx\r\n", ','); assertEquals("a,b", data.rows().get(1).getFirst().text()); assertEquals("a\"b", data.rows().get(1).get(1).text()); assertEquals("", data.rows().get(2).get(2).text()); assertEquals(3, data.rows().size()); }
    @Test void roundTripsAllDelimitersAndMultilineQuotedFields() { TableData data = TableData.plain(List.of(List.of("a,b", "a\tb", "a;b"), List.of("\"x\"", "a\nb", ""))); for (char separator : new char[]{',', ';', '\t'}) assertEquals(data, DelimitedTables.parse(DelimitedTables.write(data, separator), separator)); }
    @Test void preservesEmptyCellsAndFinalRecord() { assertEquals(3, DelimitedTables.parse("a\tb\t", '\t').width()); assertEquals("", DelimitedTables.parse("\"\"", ',').rows().getFirst().getFirst().text()); assertEquals(1, DelimitedTables.parse("a\n", ',').rows().size()); }
    @Test void rejectsBrokenInputAndBounds() { for (String source : new String[]{"", "\"abc", "a\"b", "\"a\"b"}) assertThrows(IllegalArgumentException.class, () -> DelimitedTables.parse(source, ',')); assertThrows(IllegalArgumentException.class, () -> DelimitedTables.parse(null, ',')); assertThrows(IllegalArgumentException.class, () -> DelimitedTables.parse("a", '|')); assertThrows(IllegalArgumentException.class, () -> DelimitedTables.parse("a".repeat(DelimitedTables.MAX_CHARACTERS + 1), ',')); assertThrows(IllegalArgumentException.class, () -> DelimitedTables.parse(",".repeat(100_001), ',')); }
    @Test void fileExportReplacesOnlyCompleteContentAndCleansTemporaryFiles(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var target = directory.resolve("table.csv");
        java.nio.file.Files.writeString(target, "old content");
        TableData table = TableData.plain(List.of(List.of("Größe", "a,b"), List.of("1", "2")));
        DelimitedTables.writeFile(target, table, ',');
        assertEquals(table, DelimitedTables.parse(java.nio.file.Files.readString(target), ','));
        var invalidTarget = directory.resolve("existing-directory");
        java.nio.file.Files.createDirectory(invalidTarget);
        java.nio.file.Files.writeString(invalidTarget.resolve("keep.txt"), "keep");
        assertThrows(java.io.IOException.class, () -> DelimitedTables.writeFile(invalidTarget, table, ','));
        assertEquals("keep", java.nio.file.Files.readString(invalidTarget.resolve("keep.txt")));
        try (var files = java.nio.file.Files.list(directory)) { assertEquals(2L, files.count(), "No temporary export file may be left after failure"); }
    }
}
