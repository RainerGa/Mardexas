package de.richmarkdown.features.checking;

import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.markdown.*;
import de.richmarkdown.ui.dialogs.*;
import com.gluonhq.richtextarea.model.*;
import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.Selection;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.stage.Window;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;

/** Explicit link inventory/checking actions. Network checks are never automatic. */
public final class CheckingTools {
    private CheckingTools() {}
    public static Path directory(MarkdownEditor editor) { return editor.getCurrentFile().map(file -> file.toPath().toAbsolutePath().getParent()).orElse(null); }
    public static Menu createMenu(MarkdownEditor editor, Supplier<Window> owner) {
        Menu menu = new Menu("Links verwalten");
        menu.setId("menu.links");
        menu.getItems().addAll(item("links.inventory", "Alle Links anzeigen…", () -> new LinkReviewDialog(owner.get(), editor, LinkReviewDialog.Mode.INVENTORY).showAndWait(), owner),
            item("links.checkLocal", "Lokale Linkziele prüfen…", () -> new LinkReviewDialog(owner.get(), editor, LinkReviewDialog.Mode.LOCAL).showAndWait(), owner),
            item("links.checkWeb", "Weblinks prüfen (Netzwerk)…", () -> new LinkReviewDialog(owner.get(), editor, LinkReviewDialog.Mode.WEB).showAndWait(), owner),
            item("links.replaceDestination", "Linkziel gesammelt ersetzen…", () -> replace(editor, owner.get()), owner));
        menu.getItems().getLast().disableProperty().bind(editor.getControl().editableProperty().not());
        return menu;
    }
    private static MenuItem item(String id, String label, Runnable action, Supplier<Window> owner) { MenuItem item = new MenuItem(label); item.setId(id); item.setOnAction(event -> { try { action.run(); } catch (RuntimeException failure) { error(owner.get(), failure); } }); return item; }
    public static Document replaceDestinations(Document source, String oldTarget, String newTarget) {
        if (newTarget.isBlank() || newTarget.indexOf('\n') >= 0 || newTarget.indexOf('\r') >= 0) throw new IllegalArgumentException("Ungültiges Linkziel.");
        List<DecorationModel> spans = new ArrayList<>();
        for (DecorationModel dm : source.getDecorations()) {
            Decoration decoration = dm.getDecoration();
            if (decoration instanceof TextDecoration td && Objects.equals(oldTarget, td.getURL())) decoration = TextDecoration.builder().fromDecoration(td).url(newTarget).build();
            else if (decoration instanceof ImageDecoration image) {
                String url = Objects.equals(oldTarget, image.getUrl()) ? newTarget : image.getUrl(); String link = Objects.equals(oldTarget, image.getLink()) ? newTarget : image.getLink();
                if (!url.equals(image.getUrl()) || !Objects.equals(link, image.getLink())) decoration = new AccessibleImageDecoration(url, image.getWidth(), image.getHeight(), link, AccessibleImageDecoration.alt(image));
            }
            spans.add(new DecorationModel(dm.getStart(), dm.getLength(), decoration, dm.getParagraphDecoration()));
        }
        return new Document(source.getText(), List.copyOf(spans), source.getCaretPosition(), source.getPageFormat(), source.getHeadersFooters());
    }
    private static void replace(MarkdownEditor editor, Window owner) { editDestination(editor, owner, null); }
    public static void editDestination(MarkdownEditor editor, Window owner, String selected) {
        if (!editor.getControl().isEditable()) return;
        Document snapshot = editor.getDocumentSnapshot(); String markdown = editor.getMarkdownSource();
        List<LinkManagement.Link> links = LinkManagement.links(markdown);
        List<String> targets = links.stream().map(LinkManagement.Link::target).distinct().toList(); if (targets.isEmpty()) return;
        Dialog<String> dialog = new Dialog<>(); dialog.initOwner(owner); dialog.setTitle(I18nText()); dialog.setResizable(true);
        ComboBox<String> from = new ComboBox<>(); from.getItems().setAll(targets); from.setValue(selected == null ? targets.getFirst() : selected);
        TextField to = new TextField(from.getValue()); Label count = new Label(); TextArea preview = WorkflowDialogs.output("");
        dialog.getDialogPane().setContent(new javafx.scene.layout.VBox(8, new Label(WorkflowDialogs.text("from")), from, new Label(WorkflowDialogs.text("to")), to, count, preview));
        dialog.getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL); DialogButtons.applyDialogStyles(dialog.getDialogPane());
        Runnable update = () -> {
            dialog.getDialogPane().lookupButton(DialogButtons.OK).setDisable(!LinkDialog.validTarget(to.getText()));
            var matches = links.stream().filter(link -> link.target().equals(from.getValue())).toList(); count.setText(WorkflowDialogs.text("selected") + ": " + matches.size());
            preview.setText(matches.stream().map(link -> link.label() + ": " + link.target() + " → " + to.getText()).collect(java.util.stream.Collectors.joining("\n")));
        };
        from.valueProperty().addListener((obs, old, value) -> update.run()); to.textProperty().addListener((obs, old, value) -> update.run()); update.run();
        dialog.setResultConverter(type -> type == DialogButtons.OK ? to.getText().strip() : null);
        dialog.showAndWait().ifPresent(target -> {
            try {
                var current = editor.getDocumentSnapshot();
                if (!editor.getControl().isEditable() || !current.getText().equals(snapshot.getText()) || !current.getDecorations().equals(snapshot.getDecorations())) throw new IllegalStateException(WorkflowDialogs.text("documentChanged"));
                String original = from.getValue(); Path directory = directory(editor);
                Document result = replaceDestinations(snapshot, original, target);
                if (directory != null && !original.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*") && !original.startsWith("#")) result = replaceDestinations(result, directory.resolve(original).normalize().toUri().toString(), target);
                // Image URLs must be resolved for rendering, while ordinary link destinations retain user spelling.
                List<DecorationModel> resolved = new ArrayList<>();
                for (var run : result.getDecorations()) {
                    Decoration decoration = run.getDecoration();
                    if (decoration instanceof ImageDecoration image && image.getUrl().equals(target)) decoration = new AccessibleImageDecoration(de.richmarkdown.io.ImageAssets.resolve(target, directory), image.getWidth(), image.getHeight(), image.getLink(), AccessibleImageDecoration.alt(image));
                    resolved.add(new DecorationModel(run.getStart(), run.getLength(), decoration, run.getParagraphDecoration()));
                }
                replaceKeepingPosition(editor, new Document(result.getText(), resolved, result.getCaretPosition(), result.getPageFormat(), result.getHeadersFooters()));
            } catch (RuntimeException failure) { error(owner, failure); }
        });
    }
    private static String I18nText() { return de.richmarkdown.i18n.I18n.text("dialog.link.title"); }
    /** Apply a decoration-only edit without losing the visible writing position or selection. */
    public static void replaceKeepingPosition(MarkdownEditor editor, Document replacement) {
        if (!editor.getControl().isEditable()) throw new IllegalStateException("Das Dokument ist schreibgeschützt.");
        int length = replacement.getText().length();
        int caret = Math.min(length, Math.max(0, editor.getControl().getCaretPosition()));
        Selection selection = editor.getControl().getSelection();
        Selection resultSelection = Selection.UNDEFINED;
        if (selection != null && selection.isDefined()) {
            var buffer = ((RichTextAreaSkin) editor.getControl().getSkin()).getViewModel().getTextBuffer();
            int start = Math.min(length, buffer.getText(0, selection.getStart()).length());
            int end = Math.min(length, buffer.getText(0, selection.getEnd()).length());
            if (start != end) resultSelection = new Selection(start, end);
        }
        editor.replaceDocument(new Document(replacement.getText(), replacement.getDecorations(), caret,
                replacement.getPageFormat(), replacement.getHeadersFooters()), resultSelection);
    }
    public static void showIssues(MarkdownEditor editor, Window owner, IntConsumer onJump) {
        Set<String> ignored = new HashSet<>();
        IssueDialog dialog = new IssueDialog(() -> AdvancedMarkdownCheck.check(editor.getMarkdownSource(), directory(editor)).stream().filter(issue -> !ignored.contains(issue.ruleId())).toList(), onJump);
        dialog.initOwner(owner); dialog.configureActions(editor.getControl().isEditable() ? issue -> {
            try {
                if (!editor.getControl().isEditable()) throw new IllegalStateException("Das Dokument ist schreibgeschützt.");
                AdvancedMarkdownCheck.quickFix(editor.getMarkdownSource(), issue).ifPresent(fixed -> replaceKeepingPosition(editor, new MarkdownParser().parseInto(fixed, directory(editor))));
            } catch (RuntimeException failure) { error(owner, failure); }
        } : null, issue -> ignored.add(issue.ruleId())); dialog.configureContext(editor::getMarkdownSource);
        dialog.configureReset(ignored::clear); dialog.showAndWait();
    }
    private static void error(Window owner, Throwable failure) { Alert alert = new Alert(Alert.AlertType.ERROR, Objects.toString(failure.getMessage(), failure.getClass().getSimpleName()), ButtonType.OK); alert.initOwner(owner); alert.setHeaderText("Linkprüfung oder Bearbeitung fehlgeschlagen."); alert.showAndWait(); }
}
