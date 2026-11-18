package de.richmarkdown.ui.dialogs;

import com.gluonhq.richtextarea.Selection;
import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.model.*;
import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.features.editing.DocumentEdits;
import de.richmarkdown.markdown.*;
import javafx.stage.Window;
import java.util.*;

/** Document-aware insertion and editing. Replacements are single undoable edits. */
public final class InsertionDialogs {
    private InsertionDialogs() { }
    public static int[] range(MarkdownEditor editor) {
        int caret = Math.clamp(editor.getControl().getCaretPosition(), 0, editor.getDocumentSnapshot().getText().length());
        Selection selection = editor.getControl().getSelection();
        if (selection != null && selection.isDefined() && editor.getControl().getSkin() instanceof RichTextAreaSkin skin) {
            var buffer = skin.getViewModel().getTextBuffer();
            return new int[]{buffer.getText(0, selection.getStart()).length(), buffer.getText(0, selection.getEnd()).length()};
        }
        return new int[]{caret, caret};
    }
    private static java.nio.file.Path directory(MarkdownEditor editor) { return editor.getCurrentFile().map(file -> file.toPath().toAbsolutePath().getParent()).orElse(null); }
    private static boolean unchanged(MarkdownEditor editor, Document original) {
        Document now = editor.getDocumentSnapshot();
        if (!editor.getControl().isEditable() || !now.getText().equals(original.getText()) || !now.getDecorations().equals(original.getDecorations()))
            { WorkflowDialogs.showText(null, WorkflowDialogs.text("documentChanged"), WorkflowDialogs.text("documentChanged")); return false; }
        return true;
    }
    public static void image(MarkdownEditor editor, Window owner) {
        if (!editor.getControl().isEditable()) return;
        Document source = editor.getDocumentSnapshot(); int[] range = range(editor);
        ImageDecoration initial = null;
        for (var run : source.getDecorations()) {
            if (run.getDecoration() instanceof ImageDecoration image && run.getStart() <= range[0]
                    && range[0] < run.getStart() + run.getLength()) {
                initial = image; range[0] = run.getStart(); range[1] = run.getStart() + run.getLength(); break;
            }
            if (run.getDecoration() instanceof TextDecoration text && run.getStart() <= range[0] && range[0] < run.getStart() + run.getLength()) {
                var placeholder = ImagePlaceholder.fromMarker(text.getBackground()).orElse(null);
                if (placeholder != null) {
                    initial = new AccessibleImageDecoration(placeholder.url(), placeholder.width(), placeholder.height(), placeholder.link(), placeholder.altText());
                    range[0] = run.getStart(); range[1] = run.getStart() + run.getLength(); break;
                }
            }
        }
        ImageDialog dialog = new ImageDialog(directory(editor), initial); if (owner != null) dialog.initOwner(owner);
        boolean updating = initial != null;
        dialog.showAndWait().ifPresent(value -> {
            if (!unchanged(editor, source)) return;
            var image = dialog.getImageDecoration();
            String prefix = !updating && range[0] > 0 && source.getText().charAt(range[0] - 1) != '\n' ? "\n" : "";
            String suffix = updating && !source.getText().substring(range[0], range[1]).endsWith("\n") ? "" : "\n";
            String text = prefix + "\ufeff" + suffix;
            List<DecorationModel> runs = new ArrayList<>();
            var paragraph = ParagraphDecoration.builder().presets().build();
            if (!prefix.isEmpty()) runs.add(new DecorationModel(0, 1, TextDecoration.builder().presets().build(), paragraph));
            runs.add(new DecorationModel(prefix.length(), 1, image, paragraph));
            if (!suffix.isEmpty()) runs.add(new DecorationModel(prefix.length() + 1, 1, TextDecoration.builder().presets().build(), paragraph));
            editor.replaceRange(range[0], range[1], new Document(text, runs, text.length()), Selection.UNDEFINED);
        });
    }
    public static void link(MarkdownEditor editor, Window owner) {
        if (!editor.getControl().isEditable()) return;
        Document source = editor.getDocumentSnapshot(); int[] range = range(editor); String target = "";
        if (range[0] == range[1]) {
            int position = range[0];
            for (var run : source.getDecorations()) {
                if (run.getDecoration() instanceof TextDecoration td && td.getURL() != null && !td.getURL().isEmpty()
                        && run.getStart() <= position && position < run.getStart() + run.getLength()) {
                    target = td.getURL(); range[0] = run.getStart(); range[1] = run.getStart() + run.getLength(); break;
                }
            }
            // Expand through adjacent text runs belonging to the same link.
            if (!target.isEmpty()) { int start; int end; do { start = range[0]; end = range[1]; for (var run : source.getDecorations()) {
                if (run.getDecoration() instanceof TextDecoration td && target.equals(td.getURL())) {
                    if (run.getStart() + run.getLength() == range[0]) range[0] = run.getStart();
                    if (run.getStart() == range[1]) range[1] += run.getLength();
                }
            } } while (start != range[0] || end != range[1]); }
        } else for (var run : source.getDecorations()) if (run.getStart() <= range[0] && run.getStart() + run.getLength() > range[0]
                && run.getDecoration() instanceof TextDecoration td) { target = Objects.toString(td.getURL(), ""); break; }
        List<String> anchors = new ArrayList<>();
        var headings = java.util.regex.Pattern.compile("(?m)^#{1,6}\\s+(.+)$").matcher(editor.getMarkdownSource());
        Map<String, Integer> occurrences = new HashMap<>();
        while (headings.find()) {
            String slug = headings.group(1).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}_ -]", "").replace(' ', '-');
            int count = occurrences.merge(slug, 1, Integer::sum); anchors.add("#" + slug + (count > 1 ? "-" + (count - 1) : ""));
        }
        String originalLabel = source.getText().substring(range[0], range[1]);
        LinkDialog dialog = new LinkDialog(originalLabel, target, anchors, directory(editor)); if (owner != null) dialog.initOwner(owner);
        dialog.showAndWait().ifPresent(value -> {
            if (!unchanged(editor, source)) return;
            String label = dialog.getTextField().getText(); if (label.isEmpty()) label = value;
            Document fragment;
            if (label.equals(originalLabel) && range[1] > range[0]) fragment = DocumentEdits.slice(source, range[0], range[1]);
            else fragment = new Document(label);
            List<DecorationModel> decorated = new ArrayList<>();
            for (var run : fragment.getDecorations()) {
                if (run.getDecoration() instanceof TextDecoration td) decorated.add(new DecorationModel(run.getStart(), run.getLength(),
                        TextDecoration.builder().fromDecoration(td).url(value.isEmpty() ? null : value).build(), run.getParagraphDecoration()));
                else decorated.add(run);
            }
            editor.replaceRange(range[0], range[1], new Document(fragment.getText(), decorated, fragment.getText().length()), Selection.UNDEFINED);
        });
    }
    public static void code(MarkdownEditor editor, Window owner) {
        if (!editor.getControl().isEditable()) return;
        Document source = editor.getDocumentSnapshot(); int[] range = range(editor); String language = "";
        int caret = range[0];
        for (var run : source.getDecorations()) if (run.getDecoration() instanceof TextDecoration td
                && MarkdownStyles.isCodeBlockBackground(td.getBackground()) && run.getStart() <= caret && caret < run.getStart() + run.getLength()) {
            language = Objects.toString(td.getCodeLanguage(), "");
            range[0] = run.getStart(); range[1] = run.getStart() + run.getLength();
            int start; int end;
            do {
                start = range[0]; end = range[1];
                for (var next : source.getDecorations()) if (next.getDecoration() instanceof TextDecoration nt
                        && MarkdownStyles.isCodeBlockBackground(nt.getBackground()) && Objects.equals(td.getCodeLanguage(), nt.getCodeLanguage())) {
                    if (next.getStart() == range[1]) range[1] += next.getLength();
                    if (next.getStart() + next.getLength() == range[0]) range[0] = next.getStart();
                }
            } while (start != range[0] || end != range[1]);
            break;
        }
        String initial = source.getText().substring(range[0], range[1]);
        CodeAssistantDialog dialog = new CodeAssistantDialog(owner, initial, language);
        dialog.showAndWait().ifPresent(markdown -> {
            if (!unchanged(editor, source)) return;
            Document insertion = new MarkdownParser().parseInto(markdown, directory(editor));
            if (range[0] > 0 && source.getText().charAt(range[0] - 1) != '\n') insertion = DocumentEdits.join(insertion, List.of(new Document("\n"), insertion), 0);
            editor.replaceRange(range[0], range[1], insertion, Selection.UNDEFINED);
        });
    }
}
