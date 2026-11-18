package de.richmarkdown.ui.dialogs;

import de.richmarkdown.i18n.I18n;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.Window;
import java.util.Optional;

/** Shared behavior for work dialogs; all validation happens before the dialog closes. */
public final class WorkflowDialogs {
    private WorkflowDialogs() { }
    public static String text(String key) { return I18n.text("dialog.workflow." + key); }
    public static Button button(String key, Runnable action) {
        Button button = new Button(text(key)); button.setTooltip(new Tooltip(button.getText())); button.setAccessibleText(button.getText()); button.setOnAction(event -> action.run()); return button;
    }
    public static void copy(String value) {
        ClipboardContent content = new ClipboardContent(); content.putString(value); Clipboard.getSystemClipboard().setContent(content);
    }
    public static TextArea output(String value) {
        TextArea area = new TextArea(value); area.setEditable(false); area.setWrapText(true); area.setPrefRowCount(12); return area;
    }
    public static void showText(Window owner, String title, String value) {
        Dialog<Void> dialog = new Dialog<>(); dialog.initOwner(owner); dialog.setTitle(title); dialog.setResizable(true);
        TextArea area = output(value); VBox body = new VBox(8, area, button("copy", () -> copy(area.getText())));
        VBox.setVgrow(area, Priority.ALWAYS); body.setPrefSize(700, 450); dialog.getDialogPane().setContent(body);
        dialog.getDialogPane().getButtonTypes().add(DialogButtons.CLOSE); DialogButtons.applyDialogStyles(dialog.getDialogPane()); dialog.show();
    }
    public static boolean discard(Window owner) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text("unsaved"), DialogButtons.DISCARD, DialogButtons.KEEP);
        alert.initOwner(owner); alert.setHeaderText(null); DialogButtons.applyDialogStyles(alert.getDialogPane());
        return alert.showAndWait().orElse(DialogButtons.KEEP) == DialogButtons.DISCARD;
    }
    public static Optional<Integer> integer(Window owner, String title, int initial, int minimum, int maximum) {
        Dialog<Integer> dialog = new Dialog<>(); dialog.initOwner(owner); dialog.setTitle(title);
        Spinner<Integer> spinner = new Spinner<>(minimum, maximum, Math.clamp(initial, minimum, maximum)); spinner.setEditable(true);
        Label error = new Label(); error.getStyleClass().add("rme-dialog-error");
        dialog.getDialogPane().setContent(new VBox(8, new Label(title + " (" + minimum + "–" + maximum + ")"), spinner, error));
        dialog.getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL);
        DialogButtons.applyDialogStyles(dialog.getDialogPane());
        Runnable validate = () -> {
            boolean valid = DialogButtons.validInteger(spinner, minimum, maximum);
            dialog.getDialogPane().lookupButton(DialogButtons.OK).setDisable(!valid);
            error.setText(valid ? "" : I18n.text("dialog.validation.integer"));
        };
        spinner.getEditor().textProperty().addListener((obs, old, value) -> validate.run()); validate.run();
        dialog.setResultConverter(button -> { if (button != DialogButtons.OK) return null; spinner.commitValue(); return spinner.getValue(); });
        return dialog.showAndWait();
    }
    /** Rich preview uses the same parser and control as the editor, and never opens external links. */
    public static com.gluonhq.richtextarea.RichTextArea markdownPreview(String markdown) {
        var control = new com.gluonhq.richtextarea.RichTextArea(); control.setEditable(false); control.setPrefHeight(200);
        var document = new de.richmarkdown.markdown.MarkdownParser().parseInto(markdown);
        control.skinProperty().addListener((obs, old, skin) -> {
            if (skin instanceof com.gluonhq.richtextarea.RichTextAreaSkin richSkin) {
                richSkin.getViewModel().openImmediately(document);
            }
        });
        return control;
    }
    public static void multilineKeys(Dialog<?> dialog, Button primary) {
        dialog.getDialogPane().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER && event.isShortcutDown()) { primary.fire(); event.consume(); }
        });
    }
}
