package de.richmarkdown.features.tables;

import java.util.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Bounded RFC4180-style parser; quoting also works for TSV from spreadsheet clipboards. */
public final class DelimitedTables {
    public static final int MAX_CHARACTERS = 8_000_000;
    private DelimitedTables() {}
    public static TableData parse(String source, char separator) {
        if (separator != ',' && separator != ';' && separator != '\t') throw new IllegalArgumentException("Unbekanntes Trennzeichen.");
        if (source == null || source.isEmpty()) throw new IllegalArgumentException("Keine Tabellendaten vorhanden.");
        if (source.length() > MAX_CHARACTERS) throw new IllegalArgumentException("Die Datei ist zu groß.");
        if (source.charAt(0) == '\ufeff') source = source.substring(1);
        List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>(); StringBuilder cell = new StringBuilder();
        boolean quoted = false, closed = false, started = false; int cells = 0;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (quoted) { if (ch == '"') { if (i + 1 < source.length() && source.charAt(i + 1) == '"') { cell.append('"'); i++; } else { quoted = false; closed = true; } } else cell.append(ch); continue; }
            if (ch == '"') { if (started || closed) throw new IllegalArgumentException("Anführungszeichen innerhalb eines ungeschützten Feldes."); quoted = true; started = true; continue; }
            if (ch == separator || ch == '\r' || ch == '\n') {
                row.add(cell.toString()); if (++cells > TableData.MAX_CELLS) throw new IllegalArgumentException("Zu viele Tabellenzellen."); cell.setLength(0); started = false; closed = false;
                if (ch != separator) { rows.add(row); row = new ArrayList<>(); if (ch == '\r' && i + 1 < source.length() && source.charAt(i + 1) == '\n') i++; }
            } else { if (closed) throw new IllegalArgumentException("Unerwarteter Text nach schließendem Anführungszeichen."); cell.append(ch); started = true; }
        }
        if (quoted) throw new IllegalArgumentException("Nicht geschlossenes Anführungszeichen.");
        if (started || closed || !row.isEmpty()) { row.add(cell.toString()); rows.add(row); }
        if (rows.isEmpty()) throw new IllegalArgumentException("Keine Tabellendaten vorhanden.");
        int width = rows.stream().mapToInt(List::size).max().orElseThrow();
        if ((long) rows.size() * width > TableData.MAX_CELLS) throw new IllegalArgumentException("Zu viele Tabellenzellen nach Ergänzen leerer Spalten.");
        for (List<String> r : rows) while (r.size() < width) r.add("");
        return TableData.plain(rows);
    }
    public static String write(TableData table, char separator) {
        if (separator != ',' && separator != ';' && separator != '\t') throw new IllegalArgumentException("Unbekanntes Trennzeichen.");
        StringBuilder result = new StringBuilder();
        for (List<TableData.Cell> row : table.rows()) { for (int c = 0; c < row.size(); c++) { if (c > 0) result.append(separator); String s = row.get(c).text(); if (s.indexOf(separator) >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) result.append('"').append(s.replace("\"", "\"\"")).append('"'); else result.append(s); } result.append('\n'); }
        return result.toString();
    }
    /** Finish the export beside its destination before replacing an existing file. */
    public static void writeFile(Path target, TableData table, char separator) throws IOException {
        String content = write(table, separator);
        Path absolute = target.toAbsolutePath();
        Path directory = absolute.getParent();
        if (directory == null) throw new IOException("Kein gültiges Exportverzeichnis vorhanden.");
        Path temporary = Files.createTempFile(directory, ".mardexas-table-", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try { Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unavailable) { Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
