package de.richmarkdown.features.editing;

import static de.richmarkdown.ui.FeatureUi.text;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.Selection;
import com.gluonhq.richtextarea.model.Document;
import com.gluonhq.richtextarea.model.DecorationModel;
import com.gluonhq.richtextarea.model.ImageDecoration;
import com.gluonhq.richtextarea.model.ParagraphDecoration;
import com.gluonhq.richtextarea.model.TextDecoration;
import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.markdown.MarkdownParser;
import de.richmarkdown.markdown.MarkdownStyles;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.stage.Window;
import java.util.Objects;

/** UI entry point for format-preserving editing commands. All mutations use one undoable replacement. */
public final class EditingCommands {
    private final MarkdownEditor editor;
    public EditingCommands(MarkdownEditor editor) { this.editor = Objects.requireNonNull(editor); }

    public DocumentEdits.Range selectionOrParagraph() {
        return selectionOrParagraph(editor.getDocumentSnapshot(), context());
    }
    private static DocumentEdits.Range selectionOrParagraph(Document document, Context context) {
        return context.selection().isDefined()
                ? new DocumentEdits.Range(context.selection().getStart(), context.selection().getEnd())
                : DocumentEdits.paragraph(document.getText(), context.caret());
    }
    public void transform(DocumentEdits.Transform operation) {
        requireEditable();
        var source = editor.getDocumentSnapshot();
        var context = context();
        var range = selectionOrParagraph(source, context);
        if ((operation == DocumentEdits.Transform.SORT_LINES || operation == DocumentEdits.Transform.UNIQUE_LINES)
                && !EditingContext.reorderLines(source, range))
            throw new IllegalStateException(text("Bitte vollständige normale Absätze oder Zeilen innerhalb eines Codeblocks auswählen.",
                    "Please select complete ordinary paragraphs or lines within one code block."));
        var result = DocumentEdits.transform(source, range.start(), range.end(), operation);
        java.util.function.IntUnaryOperator map = operation == DocumentEdits.Transform.SORT_LINES
                || operation == DocumentEdits.Transform.UNIQUE_LINES
                ? position -> DocumentEdits.linePosition(source, result, range, operation, position)
                : position -> {
                    if (position <= range.start()) return position;
                    if (position >= range.end()) return position + result.getText().length() - source.getText().length();
                    return DocumentEdits.convertedPosition(source, range, operation, position);
                };
        apply(result, map.applyAsInt(context.caret()), mappedSelection(context.selection(), map));
    }
    public void clearFormatting(boolean linksOnly, boolean paragraphToo) {
        requireEditable();
        var context = context();
        var source = editor.getDocumentSnapshot();
        var range = selectionOrParagraph(source, context);
        if (paragraphToo) range = wholeParagraphs(source, range);
        apply(DocumentEdits.clear(source, range.start(), range.end(), linksOnly, paragraphToo),
                context.caret(), context.selection());
    }
    public void moveParagraph(int direction) {
        requireEditable();
        var source = editor.getDocumentSnapshot();
        var context = context();
        int anchor = paragraphAnchor(source, context);
        var range = DocumentEdits.paragraph(source.getText(), anchor);
        requireOrdinaryParagraph(source, context, range);
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Direction must be -1 or 1");
        if (!hasNeighbor(source, range, direction)) return;
        if (!EditingContext.ordinaryParagraph(source, DocumentEdits.paragraph(source.getText(), direction < 0 ? range.start() - 1 : range.end())))
            throw new IllegalStateException(text("Absätze können hier nicht mit einem Listenpunkt, einer Überschrift, Tabelle, einem Bild, Zitat oder Codeblock vertauscht werden.",
                    "Paragraphs cannot be swapped with list items, headings, tables, images, quotes or code blocks."));
        var result = DocumentEdits.moveParagraph(source, anchor, direction);
        if (result == source) return;
        var neighbor = DocumentEdits.paragraph(source.getText(), direction < 0 ? range.start() - 1 : range.end());
        var changedRange = new DocumentEdits.Range(Math.min(range.start(), neighbor.start()), Math.max(range.end(), neighbor.end()));
        applyParagraph(result, context, range, result.getCaretPosition(), changedRange);
    }
    public void duplicateParagraph() {
        requireEditable();
        var source = editor.getDocumentSnapshot();
        var context = context();
        int anchor = paragraphAnchor(source, context);
        var range = DocumentEdits.paragraph(source.getText(), anchor);
        requireOrdinaryParagraph(source, context, range);
        int destination = range.end() + (source.getText().substring(range.start(), range.end()).endsWith("\n") ? 0 : 1);
        applyParagraph(DocumentEdits.duplicateParagraph(source, anchor), context, range, destination);
    }
    public void selectParagraph() {
        var range = DocumentEdits.paragraph(editor.getDocumentSnapshot().getText(), exportedCaret());
        model().setSelection(model().getTextBuffer().getInternalSelection(new Selection(range.start(), range.end())));
        model().setCaretPosition(model().getTextBuffer().getInternalPosition(range.end()));
        focus();
    }
    public void paste(String text, boolean parseMarkdown) {
        if (text == null || text.isEmpty()) return;
        requireEditable();
        var source = editor.getDocumentSnapshot();
        Selection selected = editor.getControl().getSelection();
        int start = exportedCaret(), end = start;
        if (selected != null && selected.isDefined()) {
            var range = selectionOrParagraph(); start = range.start(); end = range.end();
        }
        Document insertion = parseMarkdown ? new MarkdownParser().parseInto(text,
                editor.getCurrentFile().map(file -> file.toPath().toAbsolutePath().getParent()).orElse(null))
                : new Document(text);
        insertion = insertionInContext(source, start, end, insertion, parseMarkdown);
        var result = DocumentEdits.replace(source, start, end, insertion);
        apply(result, result.getCaretPosition(), Selection.UNDEFINED);
    }
    private int exportedCaret() {
        return model().getTextBuffer().getText(0, Math.max(0, editor.getInternalCaretPosition())).length();
    }
    private record Context(int caret, Selection selection) { }
    private Context context() {
        var selected = model().getSelection();
        var buffer = model().getTextBuffer();
        return new Context(exportedCaret(), selected.isDefined()
                ? new Selection(buffer.getText(0, selected.getStart()).length(), buffer.getText(0, selected.getEnd()).length())
                : Selection.UNDEFINED);
    }
    private static Selection mappedSelection(Selection selection, java.util.function.IntUnaryOperator map) {
        return selection.isDefined() ? new Selection(map.applyAsInt(selection.getStart()), map.applyAsInt(selection.getEnd())) : selection;
    }
    private static int paragraphAnchor(Document source, Context context) {
        int caret = context.caret();
        // A selected paragraph includes its newline; the active end then lies at
        // the next paragraph's start, but commands must still act on the selection.
        if (context.selection().isDefined() && caret == context.selection().getEnd()
                && caret > context.selection().getStart() && source.getText().charAt(caret - 1) == '\n') return caret - 1;
        return caret;
    }
    private static DocumentEdits.Range wholeParagraphs(Document source, DocumentEdits.Range range) {
        return new DocumentEdits.Range(DocumentEdits.paragraph(source.getText(), range.start()).start(),
                DocumentEdits.paragraph(source.getText(), Math.max(range.start(), range.end() - 1)).end());
    }
    private static boolean containedSelection(Context context, DocumentEdits.Range range) {
        return !context.selection().isDefined()
                || context.selection().getStart() >= range.start() && context.selection().getEnd() <= range.end();
    }
    private static void requireOrdinaryParagraph(Document source, Context context, DocumentEdits.Range range) {
        if (!containedSelection(context, range) || !EditingContext.ordinaryParagraph(source, range))
            throw new IllegalStateException(text("Dieser Befehl benötigt genau einen normalen Absatz. Für Überschriften bitte die Kapitelbefehle verwenden.",
                    "This command requires a single ordinary paragraph. Please use chapter commands for headings."));
    }
    private static boolean hasNeighbor(Document source, DocumentEdits.Range range, int direction) {
        return direction < 0 ? range.start() > 0 : range.end() < source.getText().length()
                || range.start() < range.end() && source.getText().endsWith("\n");
    }
    private static DecorationModel decorationAt(Document source, int position) {
        for (var run : source.getDecorations())
            if (run.getStart() <= position && run.getStart() + run.getLength() > position) return run;
        return source.getDecorations().isEmpty() ? DecorationModel.createDefaultDecorationModel(0) : source.getDecorations().getLast();
    }
    private static Document insertionInContext(Document source, int start, int end, Document insertion, boolean markdown) {
        var paragraph = DocumentEdits.paragraph(source.getText(), start);
        int contentEnd = paragraph.end() > paragraph.start() && source.getText().charAt(paragraph.end() - 1) == '\n'
                ? paragraph.end() - 1 : paragraph.end();
        // Replacing a complete block deliberately changes its type. An inline
        // insertion must retain the surrounding Markdown block's semantics.
        if (start == paragraph.start() && end >= contentEnd && end > start) return insertion;
        var current = decorationAt(source, start);
        var currentParagraph = current.getParagraphDecoration();
        if (current.getDecoration() instanceof ImageDecoration)
            throw new IllegalStateException(text("Bitte vor oder nach dem Bild einfügen.", "Please paste before or after the image."));
        var style = current.getDecoration() instanceof TextDecoration value ? value : TextDecoration.builder().presets().build();
        if (markdown && MarkdownStyles.isCodeBlockBackground(style.getBackground()))
            throw new IllegalStateException(text("Innerhalb eines Codeblocks bitte als Text oder wörtlichen Quelltext einfügen.",
                    "Inside a code block, please paste plain text or literal source."));
        boolean table = currentParagraph != null && currentParagraph.hasTableDecoration();
        if (table && (insertion.getText().contains("\n") || insertion.getText().indexOf('\u200b') >= 0
                || source.getText().substring(start, end).indexOf('\u200b') >= 0
                || !EditingContext.ordinaryParagraph(insertion, new DocumentEdits.Range(0, insertion.getText().length()))))
            throw new IllegalStateException(text("Bitte nur Text innerhalb einer einzelnen Tabellenzelle einfügen. Für Tabelleninhalte die Tabellenbefehle verwenden.",
                    "Please paste text within a single table cell. Use table commands for table contents."));
        var models = insertion.getDecorations().stream().map(run -> {
            var runParagraph = run.getParagraphDecoration();
            // Preserve explicit Markdown blocks, but inherit the surrounding
            // list, quote or table for inline text and plain-text insertions.
            boolean inline = !markdown || runParagraph == null || !runParagraph.hasTableDecoration()
                    && runParagraph.getGraphicType() == ParagraphDecoration.GraphicType.NONE && runParagraph.getIndentationLevel() == 0;
            var decoration = run.getDecoration();
            if (decoration instanceof TextDecoration text && inline) {
                var builder = TextDecoration.builder().fromDecoration(text);
                if (MarkdownStyles.isHeadingSize(style.getFontSize())) builder.fontSize(style.getFontSize()).fontWeight(javafx.scene.text.FontWeight.BOLD);
                if (MarkdownStyles.isCodeBlockBackground(style.getBackground()))
                    builder.background(style.getBackground()).fontFamily(style.getFontFamily()).codeLanguage(style.getCodeLanguage());
                decoration = builder.build();
            }
            return new DecorationModel(run.getStart(), run.getLength(), decoration,
                    inline && currentParagraph != null ? currentParagraph : runParagraph);
        }).toList();
        return new Document(insertion.getText(), models, insertion.getCaretPosition());
    }
    private void applyParagraph(Document result, Context context, DocumentEdits.Range range, int destination) {
        applyParagraph(result, context, range, destination, null);
    }
    private void applyParagraph(Document result, Context context, DocumentEdits.Range range, int destination,
                                DocumentEdits.Range changedRange) {
        java.util.function.IntUnaryOperator map = position -> Math.min(result.getText().length(),
                destination + Math.max(0, Math.min(position, range.end()) - range.start()));
        Selection selection = context.selection();
        if (selection.isDefined() && (selection.getStart() < range.start() || selection.getEnd() > range.end())) selection = Selection.UNDEFINED;
        apply(result, map.applyAsInt(context.caret()), mappedSelection(selection, map), changedRange);
    }
    private void apply(Document document, int caret, Selection selection) {
        apply(document, caret, selection, null);
    }
    private void apply(Document document, int caret, Selection selection, DocumentEdits.Range changedRange) {
        var current = editor.getDocumentSnapshot();
        if (document.getText().equals(current.getText()) && document.getDecorations().equals(current.getDecorations())) return;
        if (changedRange == null) {
            var positioned = new Document(document.getText(), document.getDecorations(), caret,
                    document.getPageFormat(), document.getHeadersFooters());
            editor.replaceDocument(positioned, selection);
        } else {
            var part = DocumentEdits.slice(document, changedRange.start(), changedRange.end());
            editor.replaceRange(changedRange.start(), changedRange.end(),
                    new Document(part.getText(), part.getDecorations(), caret), selection);
        }
        focus();
    }
    private void focus() {
        javafx.application.Platform.runLater(() -> {
            if (editor.getControl().getScene() != null) editor.getControl().requestFocus();
        });
    }
    private void requireEditable() {
        if (!editor.getControl().isEditable()) throw new IllegalStateException(text("Das Dokument ist schreibgeschützt.", "The document is read-only."));
    }
    private com.gluonhq.richtextarea.viewmodel.RichTextAreaViewModel model() {
        if (!(editor.getControl().getSkin() instanceof RichTextAreaSkin skin)) throw new IllegalStateException("Editor is not ready");
        return skin.getViewModel();
    }
    public static Menu createMenu(MarkdownEditor editor, Window owner) {
        var commands = new EditingCommands(editor);
        Menu menu = new Menu(text("Text und Absätze", "Text and paragraphs"));
        menu.setId("menu.textParagraphs");
        add(menu, "edit.uppercase", text("GROSSBUCHSTABEN", "UPPERCASE"), () -> commands.transform(DocumentEdits.Transform.UPPER), owner);
        add(menu, "edit.lowercase", text("kleinbuchstaben", "lowercase"), () -> commands.transform(DocumentEdits.Transform.LOWER), owner);
        add(menu, "edit.capitalizeSentences", text("Satzanfänge großschreiben", "Capitalize sentences"), () -> commands.transform(DocumentEdits.Transform.SENTENCE), owner);
        add(menu, "edit.sortLines", text("Zeilen sortieren", "Sort lines"), () -> commands.transform(DocumentEdits.Transform.SORT_LINES), owner);
        add(menu, "edit.uniqueLines", text("Doppelte Zeilen entfernen", "Remove duplicate lines"), () -> commands.transform(DocumentEdits.Transform.UNIQUE_LINES), owner);
        menu.getItems().add(new SeparatorMenuItem());
        add(menu, "edit.moveParagraphUp", text("Absatz nach oben verschieben", "Move paragraph up"), () -> commands.moveParagraph(-1), owner);
        add(menu, "edit.moveParagraphDown", text("Absatz nach unten verschieben", "Move paragraph down"), () -> commands.moveParagraph(1), owner);
        add(menu, "edit.duplicateParagraph", text("Absatz duplizieren", "Duplicate paragraph"), commands::duplicateParagraph, owner);
        add(menu, "edit.selectParagraph", text("Absatz auswählen", "Select paragraph"), commands::selectParagraph, owner);
        menu.getItems().add(new SeparatorMenuItem());
        add(menu, "edit.clearTextFormatting", text("Textauszeichnung entfernen", "Clear text formatting"), () -> commands.clearFormatting(false, false), owner);
        add(menu, "edit.removeLinks", text("Links entfernen, Text behalten", "Remove links, keep text"), () -> commands.clearFormatting(true, false), owner);
        add(menu, "edit.ordinaryParagraph", text("In normalen Absatz umwandeln", "Convert to ordinary paragraph"), () -> commands.clearFormatting(false, true), owner);
        menu.getItems().add(new SeparatorMenuItem());
        add(menu, "edit.pastePlainText", text("Nur Text einfügen", "Paste plain text"), () -> commands.paste(Clipboard.getSystemClipboard().getString(), false), owner);
        add(menu, "edit.pasteMarkdown", text("Als Markdown einfügen", "Paste as Markdown"), () -> commands.paste(Clipboard.getSystemClipboard().getString(), true), owner);
        add(menu, "edit.pasteLiteralMarkdown", text("Markdown-Quelltext wörtlich einfügen", "Paste Markdown source literally"), () -> commands.paste(Clipboard.getSystemClipboard().getString(), false), owner);
        // Calculate a single snapshot when the menu opens. Do not scan the entire
        // document once per item on every keystroke in a large document.
        Runnable refresh = () -> {
            if (!(editor.getControl().getSkin() instanceof RichTextAreaSkin)) {
                menu.getItems().forEach(item -> item.setDisable(true));
                return;
            }
            var source = de.richmarkdown.ui.CommandDocumentSnapshot.get(editor);
            var context = commands.context();
            var range = selectionOrParagraph(source, context);
            var paragraph = DocumentEdits.paragraph(source.getText(), paragraphAnchor(source, context));
            boolean editable = editor.getControl().isEditable();
            boolean ordinary = containedSelection(context, paragraph) && EditingContext.ordinaryParagraph(source, paragraph);
            boolean words = EditingContext.containsText(source, range);
            boolean reorder = EditingContext.reorderLines(source, range);
            for (int index = 0; index < menu.getItems().size(); index++) {
                var item = menu.getItems().get(index);
                if (item instanceof SeparatorMenuItem) continue;
                boolean available = switch (index) {
                    case 0, 1, 2 -> words;
                    case 3, 4 -> reorder;
                    case 6, 7 -> ordinary && hasNeighbor(source, paragraph, index == 6 ? -1 : 1)
                            && EditingContext.ordinaryParagraph(source, DocumentEdits.paragraph(source.getText(),
                                    index == 6 ? paragraph.start() - 1 : paragraph.end()));
                    case 8 -> ordinary && !source.getText().isEmpty();
                    case 9 -> range.start() < range.end();
                    case 13 -> range.start() < range.end() && !EditingContext.hasTable(source, wholeParagraphs(source, range));
                    case 15, 16, 17 -> Clipboard.getSystemClipboard().hasString() && !Clipboard.getSystemClipboard().getString().isEmpty();
                    default -> range.start() < range.end();
                };
                item.setDisable(!available || index != 9 && !editable);
            }
        };
        menu.getProperties().put(de.richmarkdown.ui.CommandIdentity.REFRESH, refresh);
        menu.setOnShowing(event -> refresh.run());
        javafx.beans.InvalidationListener updateWhileOpen = ignored -> { if (menu.isShowing()) refresh.run(); };
        editor.getControl().caretPositionProperty().addListener(updateWhileOpen);
        editor.getControl().selectionProperty().addListener(updateWhileOpen);
        editor.getControl().editableProperty().addListener(updateWhileOpen);
        editor.getControl().documentProperty().addListener(updateWhileOpen);
        return menu;
    }
    private static void add(Menu menu, String id, String label, Runnable action, Window owner) {
        var item = new MenuItem(label);
        item.setId(id);
        item.setOnAction(event -> {
            if (item.isDisable()) return;
            try { action.run(); }
            catch (RuntimeException failure) {
                var alert = new Alert(Alert.AlertType.ERROR, text("Die Bearbeitung konnte nicht ausgeführt werden: ", "The edit could not be completed: ") + failure.getMessage(), ButtonType.OK);
                alert.initOwner(owner); alert.showAndWait();
            }
        });
        menu.getItems().add(item);
    }
}
