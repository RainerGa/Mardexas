package de.richmarkdown.ui.dialogs;

import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.features.checking.*;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import java.util.*;

/** Structured inventory with explicit, cancellable rechecks and document navigation. */
public final class LinkReviewDialog extends Dialog<Void> {
    public enum Mode { INVENTORY, LOCAL, WEB }
    private record Row(LinkManagement.Link link, String status) { }
    public LinkReviewDialog(Window owner, MarkdownEditor editor, Mode mode) {
        initOwner(owner); setTitle(de.richmarkdown.i18n.I18n.text("dialog.link.title")); setResizable(true);
        String source = editor.getMarkdownSource(); var snapshot = editor.getDocumentSnapshot();
        var directory = CheckingTools.directory(editor);
        ListView<Row> list = new ListView<>(); TextField filter = new TextField(); filter.setPromptText(WorkflowDialogs.text("search"));
        var rows = javafx.collections.FXCollections.<Row>observableArrayList();
        var filtered = new javafx.collections.transformation.FilteredList<>(rows); list.setItems(filtered);
        filter.textProperty().addListener((obs, old, value) -> filtered.setPredicate(row -> (row.link().label() + " " + row.link().target() + " " + row.status()).toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT))));
        list.setCellFactory(view -> new ListCell<>() { @Override protected void updateItem(Row row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) { setText(null); return; }
            int line = 1; for (int i = 0; i < row.link().start(); i++) if (source.charAt(i) == '\n') line++;
            setText(WorkflowDialogs.text("line") + " " + line + " · " + row.link().label() + " → " + row.link().target() + "\n" + row.status());
        }});
        TextArea context = WorkflowDialogs.output(""); context.setPrefRowCount(5);
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> {
            if (row == null) { context.clear(); return; }
            int from = Math.max(0, row.link().start() - 150), to = Math.min(source.length(), row.link().end() + 150);
            context.setText(source.substring(from, to)); context.selectRange(row.link().start() - from, row.link().end() - from);
        });
        Label status = new Label(); ProgressIndicator progress = new ProgressIndicator(); progress.setMaxSize(22, 22); progress.setVisible(false);
        Task<?>[] pending = {null};
        Button recheck = WorkflowDialogs.button("reload", () -> { });
        Runnable check = () -> {
            if (pending[0] != null) pending[0].cancel(true);
            recheck.setDisable(true); progress.setVisible(true); status.setText("");
            Task<List<Row>> task = new Task<>() { @Override protected List<Row> call() {
                Map<LinkManagement.Link, String> problems = new HashMap<>();
                if (mode == Mode.LOCAL) LinkManagement.localProblems(source, directory).forEach(problem -> problems.put(problem.link(), problem.message()));
                if (mode == Mode.WEB) LinkManagement.webProblems(source, LinkManagement::probe).forEach(problem -> problems.put(problem.link(), problem.message()));
                List<Row> result = new ArrayList<>();
                for (var link : LinkManagement.links(source)) {
                    if (isCancelled()) return List.of();
                    boolean relevant = mode == Mode.INVENTORY || mode == Mode.WEB && link.target().matches("(?i)^https?://.*")
                        || mode == Mode.LOCAL && !link.target().matches("(?i)^(https?|mailto|tel):.*") && !link.target().startsWith("#");
                    if (relevant) result.add(new Row(link, problems.getOrDefault(link, mode == Mode.INVENTORY ? "" : "✓")));
                }
                return List.copyOf(result);
            }};
            pending[0] = task;
            task.setOnSucceeded(event -> { if (!isShowing()) return; rows.setAll(task.getValue()); list.getSelectionModel().selectFirst(); status.setText(WorkflowDialogs.text("result") + ": " + rows.size()); recheck.setDisable(false); progress.setVisible(false); });
            task.setOnFailed(event -> { if (!isShowing()) return; status.setText(task.getException().getMessage()); recheck.setDisable(false); progress.setVisible(false); });
            Thread.ofVirtual().name("link-review").start(task);
        };
        recheck.setOnAction(event -> check.run());
        Button jump = WorkflowDialogs.button("context", () -> {
            Row row = list.getSelectionModel().getSelectedItem(); if (row == null) return;
            if (!sameDocument(editor, snapshot)) { status.setText(WorkflowDialogs.text("documentChanged")); return; }
            int exported = IssueNavigation.exportedParagraph(source, row.link().start(), directory);
            if (editor.getControl().getSkin() instanceof com.gluonhq.richtextarea.RichTextAreaSkin skin) {
                skin.getViewModel().setCaretPosition(skin.getViewModel().getTextBuffer().getInternalPosition(exported));
            }
        });
        Button open = WorkflowDialogs.button("open", () -> {
            Row row = list.getSelectionModel().getSelectedItem(); if (row == null) return;
            editor.openReviewedLink(row.link().target());
        });
        Button edit = WorkflowDialogs.button("apply", () -> {
            Row row = list.getSelectionModel().getSelectedItem(); if (row == null) return;
            if (!sameDocument(editor, snapshot)) { status.setText(WorkflowDialogs.text("documentChanged")); return; }
            close(); CheckingTools.editDestination(editor, owner, row.link().target());
        });
        jump.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        open.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(() -> {
            Row row = list.getSelectionModel().getSelectedItem();
            return row == null || !row.link().target().matches("(?i)^(https?|mailto):.*");
        }, list.getSelectionModel().selectedItemProperty()));
        edit.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull().or(editor.getControl().editableProperty().not()));
        VBox body = new VBox(8, filter, list, context, new HBox(8, jump, open, edit, recheck, progress), status); body.setPrefSize(850, 600); VBox.setVgrow(list, Priority.ALWAYS);
        getDialogPane().setContent(body); getDialogPane().getButtonTypes().add(DialogButtons.CLOSE); DialogButtons.applyDialogStyles(getDialogPane());
        setOnShown(event -> check.run()); setOnHidden(event -> { if (pending[0] != null) pending[0].cancel(true); });
    }
    private static boolean sameDocument(MarkdownEditor editor, com.gluonhq.richtextarea.model.Document original) {
        var current = editor.getDocumentSnapshot(); return current.getText().equals(original.getText()) && current.getDecorations().equals(original.getDecorations());
    }
}
