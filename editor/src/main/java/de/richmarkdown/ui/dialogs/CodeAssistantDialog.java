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

package de.richmarkdown.ui.dialogs;

import de.richmarkdown.i18n.I18n;
import de.richmarkdown.markdown.CodeEditing;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Window;

import java.util.List;

/** Code editor with language picker and optional lightweight syntax preview. */
public final class CodeAssistantDialog extends Dialog<String> {
    private static final List<String> LANGUAGES = List.of(
            "", "java", "javascript", "typescript", "python", "json", "xml", "html",
            "css", "sql", "bash", "c", "cpp", "rust", "go", "yaml", "markdown", "text");

    private final TextArea code = new TextArea();
    private final ComboBox<String> language = new ComboBox<>();
    private final Spinner<Integer> width = new Spinner<>(1, 16, 4);
    private final Label status = new Label();
    private final TextFlow preview = new TextFlow();

    public CodeAssistantDialog(Window owner, String initialCode) { this(owner, initialCode, ""); }
    public CodeAssistantDialog(Window owner, String initialCode, String initialLanguage) {
        initOwner(owner);
        setTitle(I18n.text("dialog.codeAssistant.title"));
        setHeaderText(I18n.text("dialog.codeAssistant.header"));
        setResizable(true);
        language.getItems().addAll(LANGUAGES);
        language.setEditable(true);
        language.setValue(initialLanguage); language.getEditor().setText(initialLanguage);
        language.setId("code-language");
        language.setAccessibleText(I18n.text("dialog.codeAssistant.language"));
        language.setTooltip(new javafx.scene.control.Tooltip(I18n.text("dialog.codeAssistant.language")));
        width.setTooltip(new javafx.scene.control.Tooltip(I18n.text("dialog.codeAssistant.indent")));
        code.setText(initialCode == null ? "" : initialCode);
        code.setStyle("-fx-font-family:monospace;");
        code.setId("code-content");
        code.setAccessibleText(I18n.text("dialog.code.label"));
        code.setPrefRowCount(14);

        Button indent = button(I18n.text("dialog.codeAssistant.button.indent"),
                I18n.text("dialog.codeAssistant.button.indent"),
                () -> transform(CodeEditing.indent(code.getText(), width.getValue(), false)));
        Button dedent = button(I18n.text("dialog.codeAssistant.button.dedent"),
                I18n.text("dialog.codeAssistant.button.dedent"),
                () -> transform(CodeEditing.indent(code.getText(), width.getValue(), true)));
        Button tabs = button(I18n.text("dialog.codeAssistant.button.tabs"),
                I18n.text("dialog.codeAssistant.button.tabs"),
                () -> transform(CodeEditing.expandTabs(code.getText(), width.getValue())));
        Button spaces = button(I18n.text("dialog.codeAssistant.button.spaces"),
                I18n.text("dialog.codeAssistant.button.spaces"),
                () -> transform(CodeEditing.leadingSpacesToTabs(code.getText(), width.getValue())));
        Button copy = button(I18n.text("dialog.codeAssistant.button.copy"),
                I18n.text("dialog.codeAssistant.button.copy"),
                () -> {
                    ClipboardContent contents = new ClipboardContent();
                    contents.putString(code.getText());
                    Clipboard.getSystemClipboard().setContent(contents);
                });
        Button colours = button(I18n.text("dialog.codeAssistant.button.preview"),
                I18n.text("dialog.codeAssistant.button.preview"), this::highlight);

        HBox format = new HBox(8, new Label(I18n.text("dialog.codeAssistant.language")), language,
                new Label(I18n.text("dialog.codeAssistant.indent")), width, copy);
        HBox actions = new HBox(8, indent, dedent, tabs, spaces, colours);
        ScrollPane previewPane = new ScrollPane(preview);
        previewPane.setFitToWidth(true);
        previewPane.setPrefViewportHeight(140);
        status.getStyleClass().add("rme-dialog-error");
        status.setWrapText(true);
        VBox body = new VBox(10, format, actions, code, previewPane, status);
        body.setPrefSize(900, 650);
        VBox.setVgrow(code, Priority.ALWAYS);
        getDialogPane().setContent(body);

        DialogButtons.applyDialogStyles(getDialogPane());
        getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL);

        Button ok = (Button) getDialogPane().lookupButton(DialogButtons.OK);
        ok.setDefaultButton(true);
        ok.setTooltip(new Tooltip(I18n.text("dialog.codeAssistant.button.ok.tooltip")));
        ((Button) getDialogPane().lookupButton(DialogButtons.CANCEL))
                .setTooltip(new Tooltip(I18n.text("dialog.codeAssistant.button.cancel.tooltip")));

        ((Button) getDialogPane().lookupButton(DialogButtons.OK)).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                result();
            } catch (IllegalArgumentException error) {
                status.setText(error.getMessage());
                event.consume();
            }
        });
        setResultConverter(button -> button != null && button.getButtonData() == ButtonBar.ButtonData.OK_DONE
                ? result() : null);

        Runnable validate = () -> {
            try { result(); ok.setDisable(false); status.setText(""); }
            catch (IllegalArgumentException invalid) { ok.setDisable(true); status.setText(invalid.getMessage()); }
        };
        code.textProperty().addListener((obs, old, value) -> validate.run());
        language.getEditor().textProperty().addListener((obs, old, value) -> validate.run());
        validate.run();
        WorkflowDialogs.multilineKeys(this, ok);
        setOnShown(event -> code.requestFocus());
    }

    private String result() {
        if (code.getText().isBlank()) throw new IllegalArgumentException(I18n.text("dialog.validation.required"));
        return CodeEditing.fenced(code.getText(), language.getEditor().getText());
    }

    private void transform(String text) {
        code.replaceText(0, code.getLength(), text);
    }

    private void highlight() {
        preview.getChildren().clear();
        for (CodeEditing.Span span : CodeEditing.highlight(code.getText(), language.getEditor().getText())) {
            Text text = new Text(span.text());
            text.setFont(Font.font("monospaced", 13));
            boolean dark = Boolean.parseBoolean(I18n.preference("access.dark", "false"));
            boolean contrast = Boolean.parseBoolean(I18n.preference("access.contrast", "false"));
            text.setFill(contrast ? Color.YELLOW : dark ? switch (span.kind()) {
                case "keyword" -> Color.PLUM;
                case "string" -> Color.LIGHTGREEN;
                case "comment" -> Color.LIGHTGRAY;
                case "number" -> Color.LIGHTBLUE;
                default -> Color.WHITE;
            } : switch (span.kind()) {
                case "keyword" -> Color.DARKVIOLET;
                case "string" -> Color.DARKGREEN;
                case "comment" -> Color.GRAY;
                case "number" -> Color.DARKBLUE;
                default -> Color.BLACK;
            });
            preview.getChildren().add(text);
        }
        status.setText(code.getLength() > CodeEditing.MAX_HIGHLIGHT_CHARS
                ? I18n.text("dialog.codeAssistant.status.long")
                : I18n.text("dialog.codeAssistant.status.short"));
    }

    private Button button(String text, String help, Runnable operation) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(help));
        button.setOnAction(event -> {
            try {
                operation.run();
                status.setText("");
            } catch (IllegalArgumentException error) {
                status.setText(error.getMessage());
            }
        });
        return button;
    }
}
