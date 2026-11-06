/*
 * Copyright (C) 2026 Rainer Garbotz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.richmarkdown.ui;

import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.i18n.I18n;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.function.Consumer;

/** Modal file comparison with cancellable preparation and undoable editor import. */
public final class DiffDialog extends Dialog<ButtonType> {
    private final TrackChangesView view = new TrackChangesView();
    private final Label status = new Label(I18n.text("trackChanges.loading"));
    private final BorderPane content = new BorderPane();
    private final ButtonType applyType = new ButtonType(I18n.text("trackChanges.apply"), ButtonBar.ButtonData.OTHER);
    private final ButtonType saveType = new ButtonType(I18n.text("trackChanges.save"), ButtonBar.ButtonData.OTHER);
    private final ButtonType reportType = new ButtonType(I18n.text("trackChanges.report.save"), ButtonBar.ButtonData.OTHER);
    private final String originalName;
    private final String revisedName;
    private final ButtonType closeType = new ButtonType(I18n.text("dialog.close"), ButtonBar.ButtonData.CANCEL_CLOSE);
    // The dialog must update the live editor; a defensive copy would disconnect the result.
    private final MarkdownEditor editor;
    private final Consumer<String> onError;
    private Task<?> activeTask;
    private boolean ready;
    private boolean saving;
    private boolean closingAfterApply;

    public DiffDialog(Window owner, MarkdownEditor editor, File original, File revised,
                      String originalContents, boolean readOnly, Consumer<String> onError) {
        this(owner, editor, original, revised,
                new de.richmarkdown.markdown.MarkdownParser().parseInto(originalContents, directory(original)),
                readOnly, onError);
    }

    public DiffDialog(Window owner, MarkdownEditor editor, File original, File revised,
                      com.gluonhq.richtextarea.model.Document originalContents,
                      boolean readOnly, Consumer<String> onError) {
        this.editor = editor;
        originalName = original.getAbsolutePath();
        revisedName = revised.getAbsolutePath();
        this.onError = onError;
        initOwner(owner);
        setTitle(I18n.text("dialog.diff.title"));
        setResizable(true);
        getDialogPane().setPrefSize(1100, 750);
        getDialogPane().getButtonTypes().addAll(applyType, saveType, reportType, closeType);
        status.setWrapText(true);
        VBox loading = new VBox(12, new ProgressIndicator(), new Label(I18n.text("trackChanges.loading")));
        loading.setAlignment(javafx.geometry.Pos.CENTER);
        content.setCenter(loading);
        content.setBottom(status);
        getDialogPane().setContent(content);
        getDialogPane().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (ready && !saving) view.handleReviewKey(event);
        });
        Button apply = button(applyType, "apply.tooltip");
        Button save = button(saveType, "save.tooltip");
        Button report = button(reportType, "report.tooltip");
        report.setDisable(true);
        button(closeType, "close.tooltip");
        apply.setDisable(true);
        save.setDisable(true);
        apply.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (!confirmApply()) return;
            try {
                editor.replaceDocument(view.getResultDocument());
                view.markResultSaved();
                if (onError != null) onError.accept(null);
                closingAfterApply = true;
                close();
            } catch (RuntimeException failure) {
                closingAfterApply = false;
                fail(failure);
            }
        });
        save.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18n.text("trackChanges.save"));
            chooser.setInitialFileName("comparison-result.md");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.text("file.filter"), "*.md"));
            File target = chooser.showSaveDialog(getDialogPane().getScene().getWindow());
            if (target != null) saveResult(target, readOnly, false);
        });
        report.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18n.text("trackChanges.report.save"));
            chooser.setInitialFileName("comparison-review.md");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.text("file.filter"), "*.md"));
            File target = chooser.showSaveDialog(getDialogPane().getScene().getWindow());
            if (target != null) saveResult(target, readOnly, true);
        });
        setOnCloseRequest(event -> {
            if (saving) { event.consume(); return; }
            if (!closingAfterApply && ready && view.hasReviewChanges() && !confirmClose()) event.consume();
        });
        setOnHidden(event -> { if (activeTask != null) activeTask.cancel(true); });
        Task<TrackChangesView.Comparison> load = new Task<>() {
            @Override protected TrackChangesView.Comparison call() throws IOException {
                try {
                    String revisedContents = editor.getFileHandler().read(revised);
                    return TrackChangesView.prepare(originalContents, directory(original), revisedContents, directory(revised));
                } catch (IOException failure) {
                    throw new IOException(MessageFormat.format(I18n.text("error.read"), revised.getName(), failure.getMessage()), failure);
                }
            }
        };
        load.setOnSucceeded(event -> {
            if (!isShowing()) return;
            try {
                view.setSourceLabels(original.getAbsolutePath(), revised.getAbsolutePath());
                view.loadComparison(load.getValue());
                content.setCenter(view);
                ready = true;
                if (onError != null) onError.accept(null);
                apply.setDisable(readOnly);
                save.setDisable(false);
                report.setDisable(false);
                status.setText(I18n.text("trackChanges.ready"));
            } catch (RuntimeException failure) { fail(failure); }
        });
        load.setOnFailed(event -> {
            if (!isShowing()) return;
            content.setCenter(new Label(I18n.text("trackChanges.failed")));
            fail(load.getException());
        });
        setOnShown(event -> start(load));
    }

    private static Path directory(File file) {
        return file.toPath().toAbsolutePath().normalize().getParent();
    }

    private Button button(ButtonType type, String tooltip) {
        Button button = (Button) getDialogPane().lookupButton(type);
        button.setTooltip(new Tooltip(I18n.text("trackChanges." + tooltip)));
        button.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        return button;
    }

    private void start(Task<?> task) {
        activeTask = task;
        Thread worker = new Thread(task, "markdown-comparison");
        worker.setDaemon(true);
        worker.start();
    }

    private void saveResult(File target, boolean readOnly, boolean report) {
        try {
            File current = editor.getCurrentFile().orElse(null);
            boolean replacesCurrentFile = current != null && sameFile(current.toPath(), target.toPath());
            if (replacesCurrentFile && (readOnly || report)) {
                throw new IOException(I18n.text("trackChanges.export.otherFile"));
            }
            var resultDocument = view.getResultDocument();
            String reportMarkdown = report ? view.getReviewReport(originalName, revisedName) : null;
            saving = true;
            view.setDisable(true);
            getDialogPane().lookupButton(applyType).setDisable(true);
            getDialogPane().lookupButton(saveType).setDisable(true);
            getDialogPane().lookupButton(reportType).setDisable(true);
            getDialogPane().lookupButton(closeType).setDisable(true);
            status.setText(I18n.text("trackChanges.saving"));
            Task<Void> write = new Task<>() {
                @Override protected Void call() throws IOException {
                    try {
                        String markdown = report ? reportMarkdown
                                : new de.richmarkdown.markdown.MarkdownWriter().write(resultDocument, directory(target));
                        writeAtomically(target.toPath(), markdown);
                        return null;
                    } catch (IOException failure) {
                        throw new IOException(MessageFormat.format(I18n.text("error.save"), target.getName(), failure.getMessage()), failure);
                    }
                }
            };
            write.setOnSucceeded(event -> {
                finishSave(readOnly);
                if (replacesCurrentFile) {
                    try { editor.replaceWithSavedDocument(resultDocument); }
                    catch (RuntimeException failure) { fail(failure); return; }
                }
                if (report) view.markReviewSaved(); else view.markResultSaved();
                if (onError != null) onError.accept(null);
                status.setText(MessageFormat.format(I18n.text("trackChanges.saved"), target.getAbsolutePath()));
            });
            write.setOnFailed(event -> { finishSave(readOnly); fail(write.getException()); });
            start(write);
        } catch (IOException | RuntimeException failure) { fail(failure); }
    }

    private static boolean sameFile(Path first, Path second) throws IOException {
        return first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize())
                || (Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second));
    }

    private void finishSave(boolean readOnly) {
        saving = false;
        view.setDisable(false);
        getDialogPane().lookupButton(applyType).setDisable(readOnly);
        getDialogPane().lookupButton(saveType).setDisable(false);
        getDialogPane().lookupButton(reportType).setDisable(false);
        getDialogPane().lookupButton(closeType).setDisable(false);
    }

    /** Same-directory temporary file prevents truncation if writing fails. */
    static void writeAtomically(Path target, String markdown) throws IOException {
        Path absolute = Files.isSymbolicLink(target) ? target.toRealPath() : target.toAbsolutePath();
        if (Files.isDirectory(absolute)) throw new IOException("Is a directory: " + absolute);
        Path parent = absolute.getParent();
        if (parent == null) throw new IOException("Target has no parent directory: " + absolute);
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".markdown-review-", ".tmp");
        try {
            Files.writeString(temporary, markdown, StandardCharsets.UTF_8);
            if (Files.exists(absolute) && Files.getFileStore(absolute).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(absolute));
            }
            try { Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    private void fail(Throwable failure) {
        String detail = failure == null || failure.getMessage() == null
                ? I18n.text("trackChanges.failed") : failure.getMessage();
        String message = MessageFormat.format(I18n.text("trackChanges.error"), detail);
        status.setText(message);
        if (onError != null) onError.accept(message);
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.initOwner(getDialogPane().getScene().getWindow());
        alert.setHeaderText(null);
        alert.setTitle(I18n.text("dialog.diff.title"));
        ((Button) alert.getDialogPane().lookupButton(ButtonType.OK)).setTooltip(new Tooltip(I18n.text("dialog.ok")));
        alert.show();
    }

    private boolean confirmApply() {
        String message = I18n.text("trackChanges.apply.confirm") + "\n\n"
                + I18n.text("trackChanges.apply.confirm.hint");
        if (view.getChanges().stream().anyMatch(change -> change.comment() != null)) {
            message += "\n\n" + I18n.text("trackChanges.apply.confirm.comments");
        }
        ButtonType applyAndClose = new ButtonType(I18n.text("trackChanges.apply.confirm.button"),
                ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(I18n.text("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, applyAndClose, cancel);
        alert.initOwner(getDialogPane().getScene().getWindow());
        alert.setTitle(I18n.text("dialog.diff.title"));
        alert.setHeaderText(null);
        alert.getDialogPane().getButtonTypes().forEach(type ->
                ((Button) alert.getDialogPane().lookupButton(type)).setTooltip(new Tooltip(type.getText())));
        return alert.showAndWait().orElse(cancel) == applyAndClose;
    }

    private boolean confirmClose() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, I18n.text("trackChanges.discard"),
                de.richmarkdown.ui.dialogs.DialogButtons.DISCARD, de.richmarkdown.ui.dialogs.DialogButtons.KEEP);
        alert.initOwner(getDialogPane().getScene().getWindow());
        alert.setHeaderText(null);
        alert.getDialogPane().getButtonTypes().forEach(type ->
                ((Button) alert.getDialogPane().lookupButton(type)).setTooltip(new Tooltip(type.getText())));
        return alert.showAndWait().orElse(de.richmarkdown.ui.dialogs.DialogButtons.KEEP)
                == de.richmarkdown.ui.dialogs.DialogButtons.DISCARD;
    }

    /** Returns the live JavaFX comparison component; access it on the FX application thread. */
    public TrackChangesView getComparisonView() { return view; }
    public boolean isReady() { return ready; }
}
