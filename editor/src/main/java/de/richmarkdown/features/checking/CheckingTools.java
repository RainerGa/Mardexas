package de.richmarkdown.features.checking;

import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.markdown.*;
import de.richmarkdown.ui.dialogs.IssueDialog;
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
        menu.getItems().addAll(item("links.inventory", "Alle Links anzeigen…", () -> showList(owner.get(), LinkManagement.links(editor.getMarkdownSource()).stream().map(link -> (link.image() ? "Bild: " : "Link: ") + link.label() + " → " + link.target()).toList()), owner),
            item("links.checkLocal", "Lokale Linkziele prüfen…", () -> showList(owner.get(), LinkManagement.localProblems(editor.getMarkdownSource(), directory(editor)).stream().map(LinkManagement.Problem::message).toList()), owner),
            item("links.checkWeb", "Weblinks prüfen (Netzwerk)…", () -> checkWeb(editor, owner.get()), owner),
            item("links.replaceDestination", "Linkziel gesammelt ersetzen…", () -> replace(editor, owner.get()), owner));
        menu.getItems().getLast().disableProperty().bind(editor.getControl().editableProperty().not());
        return menu;
    }
    private static MenuItem item(String id, String label, Runnable action, Supplier<Window> owner) { MenuItem item = new MenuItem(label); item.setId(id); item.setOnAction(event -> { try { action.run(); } catch (RuntimeException failure) { error(owner.get(), failure); } }); return item; }
    private static void showList(Window owner, List<String> rows) { Dialog<ButtonType> dialog = new Dialog<>(); dialog.initOwner(owner); dialog.setTitle("Links"); ListView<String> list = new ListView<>(); list.getItems().setAll(rows.isEmpty() ? List.of("Keine Einträge oder Probleme gefunden.") : rows); list.setPrefSize(700,400); dialog.getDialogPane().setContent(list); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); ((Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setTooltip(new Tooltip("Linkübersicht schließen.")); dialog.showAndWait(); }
    private static void checkWeb(MarkdownEditor editor, Window owner) {
        String source = editor.getMarkdownSource(); Task<List<LinkManagement.Problem>> task = new Task<>() { @Override protected List<LinkManagement.Problem> call() { return LinkManagement.webProblems(source, LinkManagement::probe); } };
        Dialog<ButtonType> progress = new Dialog<>(); progress.initOwner(owner); progress.setTitle("Weblinks prüfen"); progress.getDialogPane().setContent(new Label("HTTP-/HTTPS-Ziele werden geprüft. Das kann einige Sekunden dauern.")); progress.getDialogPane().getButtonTypes().add(ButtonType.CANCEL); ((Button) progress.getDialogPane().lookupButton(ButtonType.CANCEL)).setTooltip(new Tooltip("Die laufende Linkprüfung abbrechen.")); progress.setOnHidden(event -> { if (task.isRunning()) task.cancel(true); });
        task.setOnSucceeded(event -> { progress.close(); showList(owner, task.getValue().stream().map(LinkManagement.Problem::message).toList()); }); task.setOnFailed(event -> { progress.close(); error(owner, task.getException()); });
        Thread thread = new Thread(task, "mardexas-web-links"); thread.setDaemon(true); thread.start(); progress.showAndWait();
    }
    public static Document replaceDestinations(Document source, String oldTarget, String newTarget) {
        if (newTarget.isBlank() || newTarget.indexOf('\n') >= 0 || newTarget.indexOf('\r') >= 0) throw new IllegalArgumentException("Ungültiges Linkziel.");
        List<DecorationModel> spans = new ArrayList<>();
        for (DecorationModel dm : source.getDecorations()) {
            Decoration decoration = dm.getDecoration();
            if (decoration instanceof TextDecoration td && Objects.equals(oldTarget, td.getURL())) decoration = TextDecoration.builder().fromDecoration(td).url(newTarget).build();
            else if (decoration instanceof ImageDecoration image) {
                String url = Objects.equals(oldTarget, image.getUrl()) ? newTarget : image.getUrl(); String link = Objects.equals(oldTarget, image.getLink()) ? newTarget : image.getLink();
                if (!url.equals(image.getUrl()) || !Objects.equals(link, image.getLink())) decoration = new ImageDecoration(url, image.getWidth(), image.getHeight(), link);
            }
            spans.add(new DecorationModel(dm.getStart(), dm.getLength(), decoration, dm.getParagraphDecoration()));
        }
        return new Document(source.getText(), List.copyOf(spans), source.getCaretPosition(), source.getPageFormat(), source.getHeadersFooters());
    }
    private static void replace(MarkdownEditor editor, Window owner) {
        if (!editor.getControl().isEditable()) throw new IllegalStateException("Das Dokument ist schreibgeschützt.");
        List<String> targets = LinkManagement.links(editor.getMarkdownSource()).stream().map(LinkManagement.Link::target).distinct().toList(); if (targets.isEmpty()) throw new IllegalArgumentException("Das Dokument enthält keine Links.");
        ChoiceDialog<String> old = new ChoiceDialog<>(targets.getFirst(), targets); old.initOwner(owner); old.setTitle("Linkziel ersetzen"); Optional<String> from = old.showAndWait(); if (from.isEmpty()) return;
        TextInputDialog replacement = new TextInputDialog(from.get()); replacement.initOwner(owner); replacement.setTitle("Neues Linkziel"); Optional<String> to = replacement.showAndWait(); if (to.isEmpty()) return;
        String original = from.get(); Path directory = directory(editor);
        // Image references in the document buffer are absolute; the Markdown inventory uses relative paths.
        if (directory != null && !original.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*") && !original.startsWith("#")) {
            String absolute = directory.resolve(original).normalize().toUri().toString(); Document first = replaceDestinations(editor.getDocumentSnapshot(), original, to.get()); replaceKeepingPosition(editor, replaceDestinations(first, absolute, to.get()));
        } else replaceKeepingPosition(editor, replaceDestinations(editor.getDocumentSnapshot(), original, to.get()));
    }
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
        IssueDialog dialog = new IssueDialog(() -> AdvancedMarkdownCheck.check(editor.getMarkdownSource(), directory(editor)).stream().filter(issue -> !ignored.contains(issue.message())).toList(), onJump);
        dialog.initOwner(owner); dialog.configureActions(editor.getControl().isEditable() ? issue -> {
            try {
                if (!editor.getControl().isEditable()) throw new IllegalStateException("Das Dokument ist schreibgeschützt.");
                AdvancedMarkdownCheck.quickFix(editor.getMarkdownSource(), issue).ifPresent(fixed -> replaceKeepingPosition(editor, new MarkdownParser().parseInto(fixed, directory(editor))));
            } catch (RuntimeException failure) { error(owner, failure); }
        } : null, issue -> ignored.add(issue.message())); dialog.showAndWait();
    }
    private static void error(Window owner, Throwable failure) { Alert alert = new Alert(Alert.AlertType.ERROR, Objects.toString(failure.getMessage(), failure.getClass().getSimpleName()), ButtonType.OK); alert.initOwner(owner); alert.setHeaderText("Linkprüfung oder Bearbeitung fehlgeschlagen."); alert.showAndWait(); }
}
