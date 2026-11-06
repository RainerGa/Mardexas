package de.richmarkdown.features.tables;

import de.richmarkdown.editor.MarkdownEditor;
import com.gluonhq.richtextarea.Selection;
import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.model.Document;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Supplier;

/** Optional table menu, using one undoable document command per operation. */
public final class TableTools {
    private TableTools() {}
    public static Menu createMenu(MarkdownEditor editor, Supplier<Window> owner) {
        Menu menu = new Menu("Erweiterte Tabellenfunktionen");
        menu.setId("menu.advancedTables");
        menu.getItems().addAll(
            item("table.pasteTsv", "Tabelle aus Zwischenablage (TSV)…", () -> insert(editor, DelimitedTables.parse(Clipboard.getSystemClipboard().getString(), '\t')) , owner),
            item("table.importDelimited", "CSV-/TSV-Tabelle importieren…", () -> importFile(editor, owner.get()), owner),
            item("table.exportDelimited", "Tabelle als CSV/TSV exportieren…", () -> exportFile(editor, owner.get()), owner),
            item("table.copyRegion", "Zellbereich kopieren (TSV)…", () -> {
                TableData table = current(editor, editor.getDocumentSnapshot()).data();
                int firstRow = index(owner.get(), "Erste Zeile", table.rows().size()), firstColumn = index(owner.get(), "Erste Spalte", table.width());
                int lastRow = index(owner.get(), "Letzte Zeile", table.rows().size()), lastColumn = index(owner.get(), "Letzte Spalte", table.width());
                ClipboardContent content = new ClipboardContent(); content.putString(DelimitedTables.write(table.region(firstRow, firstColumn, lastRow, lastColumn), '\t')); Clipboard.getSystemClipboard().setContent(content);
            }, owner),
            item("table.pasteRegion", "Zellen aus Zwischenablage einsetzen (TSV)…", () -> edit(editor, table -> {
                TableData cells = DelimitedTables.parse(Clipboard.getSystemClipboard().getString(), '\t');
                int row = index(owner.get(), "Erste Zielzeile (vorhandene Inhalte werden ersetzt)", table.rows().size()), column = index(owner.get(), "Erste Zielspalte", table.width());
                return List.of(table.paste(row, column, cells));
            }), owner),
            new SeparatorMenuItem(),
            item("table.sortColumns", "Nach Spalten sortieren…", () -> edit(editor, table -> {
                TextInputDialog columnsDialog = new TextInputDialog("1"); columnsDialog.initOwner(owner.get()); columnsDialog.setTitle("Sortierspalten"); columnsDialog.setHeaderText("Spaltennummern nach Priorität, beispielsweise 1,3");
                String input = columnsDialog.showAndWait().orElseThrow(Cancelled::new); List<Integer> columns;
                try { columns = Arrays.stream(input.split(",", -1)).map(String::strip).map(Integer::parseInt).map(n -> n - 1).toList(); } catch (NumberFormatException failure) { throw new IllegalArgumentException("Bitte gültige Spaltennummern eingeben."); }
                ChoiceDialog<String> mode = new ChoiceDialog<>("Text aufsteigend", "Text aufsteigend", "Text absteigend", "Zahlen aufsteigend", "Zahlen absteigend"); mode.setTitle("Sortierung (Kopfzeile bleibt erhalten)"); mode.initOwner(owner.get());
                return mode.showAndWait().map(value -> table.sort(columns, value.startsWith("Zahlen"), value.endsWith("absteigend"))).map(List::of).orElse(null);
            }), owner),
            item("table.transpose", "Transponieren", () -> edit(editor, table -> List.of(table.transpose())), owner),
            item("table.duplicateRow", "Zeile duplizieren…", () -> edit(editor, table -> List.of(table.duplicateRow(index(owner.get(), "Zeile duplizieren (1 = Kopfzeile)", table.rows().size())))), owner),
            item("table.duplicateColumn", "Spalte duplizieren…", () -> edit(editor, table -> List.of(table.duplicateColumn(index(owner.get(), "Spalte duplizieren", table.width())))), owner),
            item("table.moveRow", "Zeile verschieben…", () -> edit(editor, table -> List.of(table.moveRow(index(owner.get(), "Ausgangszeile", table.rows().size()), index(owner.get(), "Zielzeile", table.rows().size())))), owner),
            item("table.moveColumn", "Spalte verschieben…", () -> edit(editor, table -> List.of(table.moveColumn(index(owner.get(), "Ausgangsspalte", table.width()), index(owner.get(), "Zielspalte", table.width())))), owner),
            item("table.split", "Tabelle teilen…", () -> edit(editor, table -> table.split(index(owner.get(), "Vor welcher Zeile teilen? (ab 2)", table.rows().size()))), owner),
            item("table.joinNext", "Mit nächster Tabelle verbinden…", () -> join(editor, owner.get()), owner),
            item("table.calculateColumn", "Spalte berechnen…", () -> calculate(editor, owner.get()), owner)
        );
        menu.getProperties().put(de.richmarkdown.ui.CommandIdentity.REFRESH, (Runnable) () -> updateAvailability(menu, editor));
        menu.setOnShowing(event -> updateAvailability(menu, editor));
        updateAvailability(menu, editor);
        return menu;
    }
    private static void updateAvailability(Menu menu, MarkdownEditor editor) {
        boolean editable = editor.getControl().isEditable();
        var paragraph = editor.getControl().getDecorationAtParagraph();
        var table = paragraph != null && paragraph.hasTableDecoration() ? paragraph.getTableDecoration() : null;
        for (int i = 0; i < menu.getItems().size(); i++) {
            MenuItem item = menu.getItems().get(i);
            if (item instanceof SeparatorMenuItem) continue;
            if (i < 2) item.setDisable(!editable || table != null);
            else item.setDisable(table == null || i > 3 && !editable);
        }
        if (table != null) {
            menu.getItems().get(12).setDisable(!editable || table.getRows() < 2);
            menu.getItems().get(14).setDisable(!editable || table.getRows() < 2);
        }
    }
    private static MenuItem item(String id, String text, Runnable action, Supplier<Window> owner) {
        MenuItem item = new MenuItem(text); item.setId(id); item.setOnAction(event -> { try { action.run(); } catch (Cancelled ignored) { /* explicit cancellation */ } catch (RuntimeException failure) { error(owner.get(), failure); } }); return item;
    }
    private static void error(Window owner, Throwable failure) { Alert alert = new Alert(Alert.AlertType.ERROR); alert.initOwner(owner); alert.setTitle("Tabellenbearbeitung"); alert.setHeaderText("Die Tabellenaktion konnte nicht ausgeführt werden."); alert.setContentText(failure.getMessage()); alert.showAndWait(); }
    private static int exportedCaret(MarkdownEditor editor) {
        // RichTextAreaSkin already exports its internal caret through the control property.
        // Converting that property a second time shifts positions after an emoji or image.
        return Math.max(0, editor.getControl().getCaretPosition());
    }
    private static void editable(MarkdownEditor editor) { if (!editor.getControl().isEditable()) throw new IllegalStateException("Der Editor ist schreibgeschützt."); }
    private static TableDocuments.Located current(MarkdownEditor editor, Document snapshot) { return TableDocuments.at(snapshot, exportedCaret(editor)).orElseThrow(() -> new IllegalArgumentException("Bitte zuerst in eine Tabelle klicken.")); }
    private static void edit(MarkdownEditor editor, java.util.function.Function<TableData, List<TableData>> operation) {
        editable(editor);
        Document snapshot = editor.getDocumentSnapshot();
        TableDocuments.Located located = current(editor, snapshot);
        List<TableData> result = operation.apply(located.data());
        if (result != null && !result.equals(List.of(located.data()))) apply(editor, snapshot, located, result);
    }
    /** Only replace the affected table; keep the user's text position and selection inside it. */
    private static void apply(MarkdownEditor editor, Document before, TableDocuments.Located located, List<TableData> result) {
        editable(editor);
        Document now = editor.getDocumentSnapshot();
        if (!now.getText().equals(before.getText()) || !now.getDecorations().equals(before.getDecorations()))
            throw new IllegalStateException("Das Dokument wurde während der Tabellenaktion geändert. Bitte die Aktion erneut starten.");
        Document insertion = TableDocuments.tables(result);
        int length = insertion.getText().length();
        int caret = located.start() + Math.clamp(exportedCaret(editor) - located.start(), 0, length);
        Selection selected = editor.getControl().getSelection();
        Selection selection = Selection.UNDEFINED;
        if (selected.isDefined() && editor.getControl().getSkin() instanceof RichTextAreaSkin skin) {
            var buffer = skin.getViewModel().getTextBuffer();
            int start = buffer.getText(0, selected.getStart()).length();
            int end = buffer.getText(0, selected.getEnd()).length();
            if (start >= located.start() && end <= located.end()) {
                start = located.start() + Math.min(start - located.start(), length);
                end = located.start() + Math.min(end - located.start(), length);
                if (end > start) selection = new Selection(start, end);
            }
        }
        editor.replaceRange(located.start(), located.end(),
                new Document(insertion.getText(), insertion.getDecorations(), caret), selection);
        javafx.application.Platform.runLater(editor.getControl()::requestFocus);
    }
    public static void insert(MarkdownEditor editor, TableData table) {
        editable(editor);
        Document source = editor.getDocumentSnapshot();
        int caret = exportedCaret(editor);
        editor.replaceRange(caret, caret, TableDocuments.insertion(source, caret, table), Selection.UNDEFINED);
        javafx.application.Platform.runLater(editor.getControl()::requestFocus);
    }
    private static int index(Window owner, String prompt, int maximum) { TextInputDialog dialog = new TextInputDialog("1"); dialog.initOwner(owner); dialog.setTitle("Tabellenposition"); dialog.setHeaderText(prompt); dialog.setContentText("Nummer (1–" + maximum + "):"); String result = dialog.showAndWait().orElseThrow(Cancelled::new); try { int index = Integer.parseInt(result.strip()) - 1; Objects.checkIndex(index, maximum); return index; } catch (NumberFormatException | IndexOutOfBoundsException failure) { throw new IllegalArgumentException("Bitte eine Nummer zwischen 1 und " + maximum + " eingeben."); } }
    private static void calculate(MarkdownEditor editor, Window owner) {
        edit(editor, table -> {
            int column = index(owner, "Spalte berechnen (ohne Kopfzeile, leere Zellen werden ignoriert)", table.width());
            ChoiceDialog<String> choice = new ChoiceDialog<>("Summe", "Summe", "Mittelwert", "Minimum", "Maximum"); choice.initOwner(owner); choice.setTitle("Berechnung"); choice.setHeaderText("Zahlenformat: Dezimalpunkt, keine Tausendertrennzeichen.");
            String mode = choice.showAndWait().orElseThrow(Cancelled::new);
            TableData.Calculation calculation = switch (mode) { case "Mittelwert" -> TableData.Calculation.AVERAGE; case "Minimum" -> TableData.Calculation.MINIMUM; case "Maximum" -> TableData.Calculation.MAXIMUM; default -> TableData.Calculation.SUM; };
            String result = table.calculate(column, calculation).toPlainString();
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Ergebnis: " + result + "\nAls normalen Text in eine Zielzelle einsetzen? Deren bisheriger Inhalt wird ersetzt.", ButtonType.OK, ButtonType.CANCEL); alert.initOwner(owner); alert.setHeaderText(mode);
            if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return null;
            int row = index(owner, "Zielzeile für " + result, table.rows().size()); int target = index(owner, "Zielspalte für " + result, table.width());
            return List.of(table.replaceCell(row, target, result));
        });
    }
    private static void join(MarkdownEditor editor, Window owner) {
        editable(editor);
        Document source = editor.getDocumentSnapshot(); TableDocuments.Located first = current(editor, source); int next = first.end(); String text = source.getText(); while (next < text.length() && (text.charAt(next) == '\n' || text.charAt(next) == '\r')) next++;
        if (next >= text.length()) throw new IllegalArgumentException("Keine unmittelbar folgende Tabelle vorhanden.");
        TableDocuments.Located second = TableDocuments.at(source, next).orElseThrow(() -> new IllegalArgumentException("Keine unmittelbar folgende Tabelle vorhanden."));
        ChoiceDialog<String> choice = new ChoiceDialog<>("Zweite Kopfzeile weglassen", "Zweite Kopfzeile weglassen", "Zweite Kopfzeile als Daten behalten"); choice.initOwner(owner); choice.setTitle("Tabellen verbinden"); String selection = choice.showAndWait().orElseThrow(Cancelled::new);
        TableData joined = first.data().join(second.data(), selection.equals("Zweite Kopfzeile weglassen"));
        apply(editor, source, new TableDocuments.Located(first.start(), second.end(), first.data()), List.of(joined));
    }
    private static FileChooser chooser() { FileChooser chooser = new FileChooser(); chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("CSV", "*.csv"), new FileChooser.ExtensionFilter("TSV", "*.tsv")); return chooser; }
    private static char separator(File file, Window owner) {
        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".tsv")) return '\t';
        ChoiceDialog<String> choice = new ChoiceDialog<>("Komma", "Komma", "Semikolon", "Tabulator"); choice.initOwner(owner); choice.setTitle("Trennzeichen"); choice.setHeaderText("Die erste Zeile wird als Tabellenkopf übernommen.");
        return switch (choice.showAndWait().orElseThrow(Cancelled::new)) { case "Semikolon" -> ';'; case "Tabulator" -> '\t'; default -> ','; };
    }
    private static void importFile(MarkdownEditor editor, Window owner) {
        editable(editor);
        File file = chooser().showOpenDialog(owner); if (file == null) return; char separator = separator(file, owner);
        Document before = editor.getDocumentSnapshot(); int caret = exportedCaret(editor);
        Task<TableData> task = new Task<>() { @Override protected TableData call() throws Exception { if (Files.size(file.toPath()) > DelimitedTables.MAX_CHARACTERS * 4L) throw new IllegalArgumentException("Die Datei ist zu groß."); return DelimitedTables.parse(Files.readString(file.toPath(), StandardCharsets.UTF_8), separator); } };
        task.setOnSucceeded(event -> { try { editable(editor); Document now = editor.getDocumentSnapshot(); if (!now.getText().equals(before.getText()) || !now.getDecorations().equals(before.getDecorations())) throw new IllegalStateException("Das Dokument wurde während des Imports geändert. Bitte den Import erneut starten."); editor.replaceRange(caret, caret, TableDocuments.insertion(before, caret, task.getValue()), Selection.UNDEFINED); javafx.application.Platform.runLater(editor.getControl()::requestFocus); } catch (RuntimeException failure) { error(owner, failure); } });
        task.setOnFailed(event -> error(owner, task.getException())); start(task);
    }
    private static void exportFile(MarkdownEditor editor, Window owner) {
        TableData table = current(editor, editor.getDocumentSnapshot()).data(); File file = chooser().showSaveDialog(owner); if (file == null) return; char separator = separator(file, owner);
        Task<Void> task = new Task<>() { @Override protected Void call() throws Exception { DelimitedTables.writeFile(file.toPath(), table, separator); return null; } }; task.setOnFailed(event -> error(owner, task.getException())); start(task);
    }
    private static void start(Task<?> task) { Thread thread = new Thread(task, "mardexas-table-io"); thread.setDaemon(true); thread.start(); }
    private static final class Cancelled extends RuntimeException { private static final long serialVersionUID = 1L; }
}
