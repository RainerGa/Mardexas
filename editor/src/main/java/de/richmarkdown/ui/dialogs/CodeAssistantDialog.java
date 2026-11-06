package de.richmarkdown.ui.dialogs;

import de.richmarkdown.markdown.CodeEditing;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
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

/** Code editor with language picker and optional lightweight syntax preview. */
public final class CodeAssistantDialog extends Dialog<String> {
    private final TextArea code = new TextArea();
    private final ComboBox<String> language = new ComboBox<>();
    private final Spinner<Integer> width = new Spinner<>(1, 16, 4);
    private final Label status = new Label();
    private final TextFlow preview = new TextFlow();

    public CodeAssistantDialog(Window owner, String initialCode) {
        initOwner(owner);
        setTitle("Codeblock bearbeiten / einfügen");
        setHeaderText("Sprache und Inhalt werden als gewöhnlicher Markdown-Codeblock gespeichert.");
        setResizable(true);
        language.getItems().addAll("", "java", "javascript", "typescript", "python", "json", "xml", "html", "css", "sql", "bash", "c", "cpp", "rust", "go", "yaml", "markdown", "text");
        language.setEditable(true);
        language.setValue("");
        language.setId("code-language");
        language.setTooltip(new Tooltip("Codesprache / Standard-Fence-Info"));
        width.setTooltip(new Tooltip("Anzahl Leerzeichen pro Einrückung / Tabulator"));
        code.setText(initialCode == null ? "" : initialCode);
        code.setStyle("-fx-font-family:monospace;");
        code.setId("code-content");
        code.setPrefRowCount(14);
        Button indent = button("Einrücken", "Alle Codezeilen einrücken", () -> transform(CodeEditing.indent(code.getText(), width.getValue(), false)));
        Button dedent = button("Ausrücken", "Ein Einrückungsniveau entfernen", () -> transform(CodeEditing.indent(code.getText(), width.getValue(), true)));
        Button tabs = button("Tabs → Leerzeichen", "Tabulatoren anhand ihrer Spaltenposition auflösen", () -> transform(CodeEditing.expandTabs(code.getText(), width.getValue())));
        Button spaces = button("Leerzeichen → Tabs", "Nur führende Leerzeichen in Tabulatoren umwandeln", () -> transform(CodeEditing.leadingSpacesToTabs(code.getText(), width.getValue())));
        Button copy = button("Kopieren", "Code ohne Markdown-Fences kopieren", () -> {
            ClipboardContent contents = new ClipboardContent(); contents.putString(code.getText());
            Clipboard.getSystemClipboard().setContent(contents);
        });
        Button colours = button("Syntaxvorschau", "Syntaxfarben anzeigen (bis 32 KiB); Farben werden nicht gespeichert", this::highlight);
        HBox format = new HBox(8, new Label("Sprache:"), language, new Label("Einrückung:"), width, copy);
        HBox actions = new HBox(8, indent, dedent, tabs, spaces, colours);
        ScrollPane previewPane = new ScrollPane(preview);
        previewPane.setFitToWidth(true);
        previewPane.setPrefViewportHeight(140);
        VBox body = new VBox(10, format, actions, code, previewPane, status);
        body.setPrefSize(900, 650);
        VBox.setVgrow(code, Priority.ALWAYS);
        getDialogPane().setContent(body);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) getDialogPane().lookupButton(ButtonType.OK)).setTooltip(new Tooltip("Codeblock als Markdown übernehmen"));
        ((Button) getDialogPane().lookupButton(ButtonType.CANCEL)).setTooltip(new Tooltip("Codebearbeitung abbrechen"));
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try { result(); } catch (IllegalArgumentException error) { status.setText(error.getMessage()); event.consume(); }
        });
        setResultConverter(button -> button == ButtonType.OK ? result() : null);
    }

    private String result() { return CodeEditing.fenced(code.getText(), language.getEditor().getText()); }
    private void transform(String text) { code.replaceText(0, code.getLength(), text); }
    private void highlight() {
        preview.getChildren().clear();
        for (CodeEditing.Span span : CodeEditing.highlight(code.getText(), language.getEditor().getText())) {
            Text text = new Text(span.text());
            text.setFont(Font.font("monospaced", 13));
            text.setFill(switch (span.kind()) {
                case "keyword" -> Color.DARKVIOLET;
                case "string" -> Color.DARKGREEN;
                case "comment" -> Color.GRAY;
                case "number" -> Color.DARKBLUE;
                default -> Color.BLACK;
            });
            preview.getChildren().add(text);
        }
        status.setText(code.getLength() > CodeEditing.MAX_HIGHLIGHT_CHARS
                ? "Für lange Blöcke bleibt die Vorschau ohne Syntaxfarben." : "Syntaxfarben betreffen ausschließlich diese Vorschau.");
    }
    private Button button(String text, String help, Runnable operation) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(help));
        button.setOnAction(event -> {
            try { operation.run(); status.setText(""); }
            catch (IllegalArgumentException error) { status.setText(error.getMessage()); }
        });
        return button;
    }
}
