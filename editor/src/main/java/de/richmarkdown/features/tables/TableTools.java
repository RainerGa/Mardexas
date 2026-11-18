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
                var result = operation(editor, owner.get(), table, de.richmarkdown.ui.dialogs.TableOperationDialog.Operation.COPY, null);
                if (result != null) { ClipboardContent content = new ClipboardContent(); content.putString(DelimitedTables.write(result.getFirst(), '\t')); Clipboard.getSystemClipboard().setContent(content); }
            }, owner),
            item("table.pasteRegion", "Zellen aus Zwischenablage einsetzen (TSV)…", () -> edit(editor, table -> operation(editor, owner.get(), table,
                    de.richmarkdown.ui.dialogs.TableOperationDialog.Operation.PASTE, DelimitedTables.parse(Clipboard.getSystemClipboard().getString(), '\t'))), owner),
            new SeparatorMenuItem(),
            item("table.sortColumns", "Nach Spalten sortieren…", () -> edit(editor, table -> operation(editor, owner.get(), table, de.richmarkdown.ui.dialogs.TableOperationDialog.Operation.SORT, null)), owner),
            item("table.transpose", "Transponieren", () -> edit(editor, table -> List.of(table.transpose())), owner),
            item("table.duplicateRow", "Zeile duplizieren…", () -> edit(editor, table -> operation(editor, owner.get(), table, de.richmarkdown.ui.dialogs.TableOperationDialog.Operation.DUPLICATE_ROW, null)), owner),
            item("table.duplicateColumn", "Spalte duplizieren…", () -> edit(editor, table -> operation(editor, owner.get(), table, de.richmarkdown.ui.dialogs.TableOperationDialog.Operation.DUPLICATE_COLUMN, null)), owner),
            item("table.moveRow", "Zeile verschieben…", () -> edit(editor, table -> operation(editor, owner.get(), table, de.richmarkdown.ui.dialogs.TableOperationDialog.Operation.MOVE_ROW, null)), owner),
            item("table.moveColumn", "Spalte verschieben…", () -> edit(editor, table -> operation(editor, owner.get(), table, de.richmarkdown.ui.dialogs.TableOperationDialog.Operation.MOVE_COLUMN, null)), owner),
            item("table.split", "Tabelle teilen…", () -> edit(editor, table -> operation(editor, owner.get(), table, de.richmarkdown.ui.dialogs.TableOperationDialog.Operation.SPLIT, null)), owner),
            item("table.joinNext", "Mit nächster Tabelle verbinden…", () -> join(editor, owner.get()), owner),
            item("table.calculateColumn", "Spalte berechnen…", () -> edit(editor, table -> operation(editor, owner.get(), table, de.richmarkdown.ui.dialogs.TableOperationDialog.Operation.CALCULATE, null)), owner)

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
        MenuItem item = new MenuItem(text); item.setId(id); item.setOnAction(event -> { try { action.run(); } catch (RuntimeException failure) { error(owner.get(), failure); } }); return item;
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
    private static List<TableData> operation(MarkdownEditor editor, Window owner, TableData table,
            de.richmarkdown.ui.dialogs.TableOperationDialog.Operation operation, TableData other) {
        var located = current(editor, editor.getDocumentSnapshot());
        String prefix = editor.getDocumentSnapshot().getText().substring(located.start(), Math.clamp(exportedCaret(editor), located.start(), located.end()));
        int cell = (int) prefix.chars().filter(c -> c == '\u200b').count();
        return new de.richmarkdown.ui.dialogs.TableOperationDialog(owner, de.richmarkdown.ui.dialogs.WorkflowDialogs.text(operation == de.richmarkdown.ui.dialogs.TableOperationDialog.Operation.SORT ? "sort" : "apply"),
                table, operation, other, cell / table.width(), cell % table.width()).showAndWait().orElse(null);
    }
    private static void join(MarkdownEditor editor, Window owner) {
        editable(editor);
        Document source = editor.getDocumentSnapshot(); TableDocuments.Located first = current(editor, source); int next = first.end(); String text = source.getText(); while (next < text.length() && (text.charAt(next) == '\n' || text.charAt(next) == '\r')) next++;
        if (next >= text.length()) throw new IllegalArgumentException("Keine unmittelbar folgende Tabelle vorhanden.");
        TableDocuments.Located second = TableDocuments.at(source, next).orElseThrow(() -> new IllegalArgumentException("Keine unmittelbar folgende Tabelle vorhanden."));
        List<TableData> result = operation(editor, owner, first.data(), de.richmarkdown.ui.dialogs.TableOperationDialog.Operation.JOIN, second.data());
        if (result != null) apply(editor, source, new TableDocuments.Located(first.start(), second.end(), first.data()), result);
    }

    private static FileChooser chooser() { FileChooser chooser = new FileChooser(); chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("CSV", "*.csv"), new FileChooser.ExtensionFilter("TSV", "*.tsv")); return chooser; }
    private static void importFile(MarkdownEditor editor, Window owner) {
        editable(editor);
        File file = chooser().showOpenDialog(owner); if (file == null) return;
        Document before = editor.getDocumentSnapshot(); int caret = exportedCaret(editor);
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                int byteLimit = DelimitedTables.MAX_CHARACTERS * 4;
                try (var input = Files.newInputStream(file.toPath())) {
                    byte[] bytes = input.readNBytes(byteLimit + 1);
                    if (bytes.length > byteLimit) throw new IllegalArgumentException("Die Datei ist zu groß.");
                    return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                }
            }
        };
        task.setOnSucceeded(event -> { try {
            var dialog = new de.richmarkdown.ui.dialogs.DelimitedTableDialog(owner, task.getValue(), null);
            var chosen = dialog.showAndWait(); if (chosen.isEmpty()) return;
            editable(editor); Document now = editor.getDocumentSnapshot(); if (!now.getText().equals(before.getText()) || !now.getDecorations().equals(before.getDecorations())) throw new IllegalStateException("Das Dokument wurde während des Imports geändert. Bitte den Import erneut starten."); editor.replaceRange(caret, caret, TableDocuments.insertion(before, caret, chosen.get()), Selection.UNDEFINED); javafx.application.Platform.runLater(editor.getControl()::requestFocus); } catch (RuntimeException failure) { error(owner, failure); } });
        task.setOnFailed(event -> error(owner, task.getException())); start(task);
    }
    private static void exportFile(MarkdownEditor editor, Window owner) {
        TableData table = current(editor, editor.getDocumentSnapshot()).data();
        var dialog = new de.richmarkdown.ui.dialogs.DelimitedTableDialog(owner, null, table);
        if (dialog.showAndWait().isEmpty()) return;
        FileChooser chooser = chooser(); chooser.setInitialFileName(dialog.separator() == '\t' ? "table.tsv" : "table.csv");
        File file = chooser.showSaveDialog(owner); if (file == null) return; char separator = dialog.separator();
        Task<Void> task = new Task<>() { @Override protected Void call() throws Exception { DelimitedTables.writeFile(file.toPath(), table, separator); return null; } };
        task.setOnFailed(event -> error(owner, task.getException())); start(task);
    }
    private static void start(Task<?> task) { Thread thread = new Thread(task, "mardexas-table-io"); thread.setDaemon(true); thread.start(); }
}
