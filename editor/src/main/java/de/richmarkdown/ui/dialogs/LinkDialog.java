package de.richmarkdown.ui.dialogs;

import de.richmarkdown.i18n.I18n;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.nio.file.Path;
import java.util.List;

/** Complete link editor. The historic String result remains the destination. */
public class LinkDialog extends Dialog<String> {
    private final TextField urlField = new TextField();
    private final TextField textField = new TextField();
    public LinkDialog() { this("", "", List.of(), null); }
    public LinkDialog(String label, String target, List<String> anchors, Path directory) {
        setTitle(I18n.text("dialog.link.title")); setHeaderText(I18n.text("dialog.link.header")); setResizable(true);
        urlField.setId("link-url"); textField.setId("link-text");
        urlField.setText(target == null ? "" : target); textField.setText(label == null ? "" : label);
        urlField.setAccessibleText(I18n.text("dialog.link.url")); textField.setAccessibleText(WorkflowDialogs.text("linkText"));
        GridPane fields = new GridPane(8, 8);
        Button browse = WorkflowDialogs.button("browse", () -> {
            FileChooser chooser = new FileChooser(); if (directory != null && java.nio.file.Files.isDirectory(directory)) chooser.setInitialDirectory(directory.toFile());
            var file = chooser.showOpenDialog(getDialogPane().getScene().getWindow());
            if (file != null) urlField.setText(file.toURI().toASCIIString());
        });
        ComboBox<String> headings = new ComboBox<>(); headings.getItems().setAll(anchors);
        headings.setPromptText(WorkflowDialogs.text("heading"));
        headings.setOnAction(event -> { if (headings.getValue() != null) urlField.setText(headings.getValue()); });
        Button remove = WorkflowDialogs.button("removeLink", () -> { setResult(""); close(); }); remove.setDisable(target == null || target.isEmpty());
        fields.addRow(0, new Label(WorkflowDialogs.text("linkText")), textField);
        fields.addRow(1, new Label(I18n.text("dialog.link.url")), urlField, browse);
        fields.addRow(2, new Label(WorkflowDialogs.text("heading")), headings);
        Label error = new Label(); error.getStyleClass().add("rme-dialog-error"); error.setWrapText(true);
        getDialogPane().setContent(new VBox(10, fields, remove, error));
        DialogButtons.applyDialogStyles(getDialogPane()); getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL);
        Button ok = (Button) getDialogPane().lookupButton(DialogButtons.OK); ok.setText(WorkflowDialogs.text("apply"));
        Runnable validate = () -> {
            boolean valid = validTarget(urlField.getText()); ok.setDisable(!valid);
            error.setText(valid ? "" : I18n.text(urlField.getText().isBlank() ? "dialog.validation.required" : "dialog.workflow.invalidTarget"));
        };
        urlField.textProperty().addListener((obs, old, value) -> validate.run()); validate.run();
        setResultConverter(button -> button == DialogButtons.OK ? urlField.getText().strip() : null);
        setOnShown(event -> urlField.requestFocus());
    }
    public static boolean validTarget(String value) {
        if (value == null || value.isBlank() || value.chars().anyMatch(Character::isISOControl)) return false;
        try {
            var uri = java.net.URI.create(value.strip());
            String scheme = uri.getScheme();
            if (scheme != null && !java.util.Set.of("http", "https", "mailto", "file").contains(scheme.toLowerCase(java.util.Locale.ROOT))) return false;
            return scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || uri.getHost() != null;
        } catch (IllegalArgumentException failure) { return false; }
    }
    public TextField getUrlField() { return urlField; }
    public TextField getTextField() { return textField; }
}
