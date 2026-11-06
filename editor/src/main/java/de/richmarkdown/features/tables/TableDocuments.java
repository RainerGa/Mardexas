package de.richmarkdown.features.tables;

import com.gluonhq.richtextarea.model.*;
import javafx.scene.text.TextAlignment;
import java.util.*;

/** Table edits operate on exported document coordinates, retaining surrounding styles and images. */
public final class TableDocuments {
    private TableDocuments() {}
    public record Located(int start, int end, TableData data) {}
    public static Optional<Located> at(Document source, int caret) {
        String text = source.getText();
        if (caret < 0 || caret > text.length()) throw new IllegalArgumentException("Ungültige Schreibposition.");
        int start = caret == 0 ? 0 : text.lastIndexOf('\n', caret - 1) + 1;
        int end = text.indexOf('\n', start); if (end < 0) end = text.length();
        for (DecorationModel dm : source.getDecorations()) {
            if (dm.getStart() <= start && (dm.getStart() + dm.getLength() > start || dm.getLength() == 0 && dm.getStart() == start) && dm.getParagraphDecoration() != null && dm.getParagraphDecoration().hasTableDecoration()) {
                TableDecoration td = dm.getParagraphDecoration().getTableDecoration();
                String[] cells = text.substring(start, end).split("\u200b", -1);
                if ((long) td.getRows() * td.getColumns() != cells.length) throw new IllegalArgumentException("Die Tabellenstruktur ist beschädigt.");
                List<List<TableData.Cell>> rows = new ArrayList<>(); int pos = start, index = 0;
                List<DecorationModel> decorations = source.getDecorations(); int decorationIndex = 0;
                for (int r = 0; r < td.getRows(); r++) { List<TableData.Cell> row = new ArrayList<>(); for (int c = 0; c < td.getColumns(); c++) { String cell = cells[index++]; List<DecorationModel> runs = new ArrayList<>();
                    while (decorationIndex < decorations.size() && decorations.get(decorationIndex).getStart() + decorations.get(decorationIndex).getLength() <= pos) decorationIndex++;
                    for (int d = decorationIndex; d < decorations.size(); d++) { DecorationModel run = decorations.get(d); if (run.getStart() >= pos + cell.length()) break; int a = Math.max(pos, run.getStart()), b = Math.min(pos + cell.length(), run.getStart() + run.getLength()); if (b > a) runs.add(new DecorationModel(a - pos, b - a, run.getDecoration(), run.getParagraphDecoration())); }
                    row.add(new TableData.Cell(cell, runs, td.getCellAlignment()[r][c])); pos += cell.length() + 1; } rows.add(row); }
                return Optional.of(new Located(start, end, new TableData(rows)));
            }
        }
        return Optional.empty();
    }
    public static Document replace(Document source, Located table, List<TableData> replacements) { return splice(source, table.start(), table.end(), tables(replacements)); }
    /** Inserts complete paragraphs; never concatenates table structure into an existing paragraph. */
    public static Document insert(Document source, int caret, TableData data) {
        Document insertion = insertion(source, caret, data);
        Document result = splice(source, caret, caret, insertion);
        return new Document(result.getText(), result.getDecorations(), insertion.getCaretPosition(),
                source.getPageFormat(), source.getHeadersFooters());
    }
    /** The insertion fragment uses an absolute result caret, matching the editor's range command. */
    public static Document insertion(Document source, int caret, TableData data) {
        if (caret < 0 || caret > source.getText().length()) throw new IllegalArgumentException("Ungültige Schreibposition.");
        if (at(source, caret).isPresent()) throw new IllegalArgumentException("Zum Einfügen außerhalb der vorhandenen Tabelle klicken.");
        Document table = tables(List.of(data)); String prefix = caret > 0 && source.getText().charAt(caret - 1) != '\n' ? "\n" : "";
        String suffix = caret < source.getText().length() && source.getText().charAt(caret) != '\n' ? "\n" : "";
        List<DecorationModel> spans = new ArrayList<>();
        if (!prefix.isEmpty()) spans.add(DecorationModel.createDefaultDecorationModel(1));
        for (DecorationModel dm : table.getDecorations()) spans.add(new DecorationModel(dm.getStart() + prefix.length(), dm.getLength(), dm.getDecoration(), dm.getParagraphDecoration()));
        if (!suffix.isEmpty()) spans.add(new DecorationModel(prefix.length() + table.getText().length(), 1, TextDecoration.builder().presets().build(), ParagraphDecoration.builder().presets().build()));
        return new Document(prefix + table.getText() + suffix, spans, caret + prefix.length());
    }
    public static Document tables(List<TableData> tables) {
        if (tables.isEmpty()) throw new IllegalArgumentException("Keine Tabelle.");
        StringBuilder text = new StringBuilder(); List<DecorationModel> spans = new ArrayList<>();
        for (TableData table : tables) {
            if (!text.isEmpty()) { int offset = text.length(); text.append("\n\n"); spans.add(new DecorationModel(offset, 2, TextDecoration.builder().presets().build(), ParagraphDecoration.builder().presets().build())); }
            TextAlignment[][] alignment = new TextAlignment[table.rows().size()][table.width()];
            for (int r = 0; r < table.rows().size(); r++) for (int c = 0; c < table.width(); c++) alignment[r][c] = table.rows().get(r).get(c).alignment();
            ParagraphDecoration pd = ParagraphDecoration.builder().presets().tableDecoration(new TableDecoration(table.rows().size(), table.width(), alignment)).build();
            int count = 0;
            for (List<TableData.Cell> row : table.rows()) for (TableData.Cell cell : row) {
                if (count++ > 0) { int offset = text.length(); text.append('\u200b'); spans.add(new DecorationModel(offset, 1, TextDecoration.builder().presets().build(), pd)); }
                if (cell.text().indexOf('\n') >= 0 || cell.text().indexOf('\r') >= 0 || cell.text().indexOf('\u200b') >= 0) throw new IllegalArgumentException("Markdown-Tabellenzellen unterstützen keine Zeilenumbrüche oder Tabellentrennzeichen. Bitte die Daten vor dem Import bereinigen.");
                int offset = text.length(); text.append(cell.text());
                if (cell.decorations().isEmpty()) spans.add(new DecorationModel(offset, cell.text().length(), TextDecoration.builder().presets().build(), pd));
                else for (DecorationModel dm : cell.decorations()) spans.add(new DecorationModel(offset + dm.getStart(), dm.getLength(), dm.getDecoration(), pd));
            }
        }
        return new Document(text.toString(), List.copyOf(spans), 0);
    }
    public static Document splice(Document source, int start, int end, Document insertion) {
        Objects.checkFromToIndex(start, end, source.getText().length());
        List<DecorationModel> spans = new ArrayList<>(slice(source, 0, start, 0, null));
        for (DecorationModel dm : insertion.getDecorations()) spans.add(new DecorationModel(start + dm.getStart(), dm.getLength(), dm.getDecoration(), dm.getParagraphDecoration()));
        spans.addAll(slice(source, end, source.getText().length(), start + insertion.getText().length(), null));
        return new Document(source.getText().substring(0, start) + insertion.getText() + source.getText().substring(end), List.copyOf(spans), start, source.getPageFormat(), source.getHeadersFooters());
    }
    private static List<DecorationModel> slice(Document source, int start, int end, int target, ParagraphDecoration override) {
        List<DecorationModel> result = new ArrayList<>();
        for (DecorationModel dm : source.getDecorations()) { int a = Math.max(start, dm.getStart()), b = Math.min(end, dm.getStart() + dm.getLength()); if (b > a) result.add(new DecorationModel(target + a - start, b - a, dm.getDecoration(), override == null ? dm.getParagraphDecoration() : override)); }
        return result;
    }
}
