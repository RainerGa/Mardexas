package de.richmarkdown.ui.dialogs;

import de.richmarkdown.i18n.I18n;
import de.richmarkdown.io.ImageAssets;
import de.richmarkdown.markdown.AccessibleImageDecoration;
import com.gluonhq.richtextarea.model.ImageDecoration;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.net.URI;
import java.nio.file.*;

/** Image insertion and editing, with document-relative paths and optional managed imports. */
public class ImageDialog extends Dialog<String> {
    private final TextField urlField = new TextField();
    private final TextField alt = new TextField();
    private final TextField width = new TextField();
    private final TextField height = new TextField();
    private final CheckBox copy = new CheckBox(I18n.text("dialog.workflow.copyAsset"));
    private final CheckBox ratio = new CheckBox(I18n.text("dialog.workflow.ratio"));
    private final Label error = new Label();
    private final Label status = new Label();
    private final ImageView preview = new ImageView();
    private final Path directory;
    private Image image;
    private Task<String> importTask;
    private String imageLink;
    private boolean busy;
    private boolean committing;
    private boolean changing;

    public ImageDialog() { this(null, null); }
    public ImageDialog(Path documentDirectory, ImageDecoration initial) {
        directory = documentDirectory;
        imageLink = initial == null ? null : initial.getLink();
        setTitle(I18n.text("dialog.image.title"));
        setHeaderText(I18n.text("dialog.image.header"));
        setResizable(true);
        urlField.setId("image-path"); alt.setId("image-alt"); width.setId("image-width"); height.setId("image-height");
        urlField.setPromptText(I18n.text("dialog.image.path"));
        width.setPromptText(I18n.text("dialog.workflow.original")); height.setPromptText(width.getPromptText());
        preview.setFitWidth(440); preview.setFitHeight(240); preview.setPreserveRatio(true);
        Button browse = action("dialog.workflow.browse", () -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(getTitle());
            chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("PNG, JPEG, GIF, BMP", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                    new FileChooser.ExtensionFilter("*", "*.*"));
            if (directory != null && Files.isDirectory(directory)) chooser.setInitialDirectory(directory.toFile());
            var file = chooser.showOpenDialog(getDialogPane().getScene().getWindow());
            if (file != null) {
                urlField.setText(file.toURI().toASCIIString());
                loadPreview();
            }
        });
        Button paste = action("dialog.workflow.clipboard", this::paste);
        Button load = action("dialog.workflow.preview", this::loadPreview);
        Button original = action("dialog.workflow.original", () -> { width.clear(); height.clear(); });
        ratio.setSelected(true);
        copy.setDisable(directory == null);
        copy.setTooltip(new Tooltip(I18n.text("dialog.workflow.copyAssetHelp")));
        GridPane fields = new GridPane(8, 8);
        fields.addRow(0, new Label(I18n.text("dialog.image.path")), urlField, browse);
        fields.addRow(1, new Label(I18n.text("dialog.workflow.alt")), alt);
        fields.addRow(2, new Label(I18n.text("dialog.workflow.width")), width);
        fields.addRow(3, new Label(I18n.text("dialog.workflow.height")), height);
        GridPane.setHgrow(urlField, Priority.ALWAYS); GridPane.setHgrow(alt, Priority.ALWAYS);
        error.getStyleClass().add("rme-dialog-error"); error.setWrapText(true); status.setWrapText(true);
        VBox body = new VBox(10, fields, new HBox(8, paste, load, original), ratio, copy, preview, status, error);
        body.setPrefWidth(650);
        getDialogPane().setContent(body);
        DialogButtons.applyDialogStyles(getDialogPane());
        getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL);
        Button ok = (Button) getDialogPane().lookupButton(DialogButtons.OK);
        ok.setText(I18n.text(initial == null ? "dialog.workflow.insert" : "dialog.workflow.apply"));
        urlField.textProperty().addListener((obs, old, value) -> { if (image != null) image.cancel(); preview.setImage(null); image = null; status.setText(""); validate(); });
        alt.textProperty().addListener((obs, old, value) -> validate());
        width.textProperty().addListener((obs, old, value) -> { proportional(true); validate(); });
        height.textProperty().addListener((obs, old, value) -> { proportional(false); validate(); });
        body.setOnDragOver(event -> { if (event.getDragboard().hasFiles()) event.acceptTransferModes(TransferMode.COPY); event.consume(); });
        body.setOnDragDropped(event -> {
            boolean accepted = !event.getDragboard().getFiles().isEmpty();
            if (accepted) { urlField.setText(event.getDragboard().getFiles().getFirst().toURI().toASCIIString()); loadPreview(); }
            event.setDropCompleted(accepted); event.consume();
        });
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (committing) return;
            event.consume();
            try {
                String resolved = ImageAssets.resolve(urlField.getText(), directory);
                if (copy.isSelected()) {
                    if (!resolved.startsWith("file:")) throw new IllegalArgumentException("dialog.workflow.localOnly");
                    setBusy(true);
                    importTask = new Task<>() { @Override protected String call() throws Exception {
                        return ImageAssets.copyInto(Path.of(URI.create(resolved)), directory.resolve("images")).toUri().toASCIIString();
                    }};
                    importTask.setOnSucceeded(e -> { setBusy(false); urlField.setText(importTask.getValue()); copy.setSelected(false); committing = true; ok.fire(); });
                    importTask.setOnFailed(e -> { setBusy(false); error.setText(importTask.getException().getMessage()); });
                    Thread.ofVirtual().name("image-import").start(importTask);
                } else {
                    if (resolved.startsWith("file:") && !Files.isRegularFile(Path.of(URI.create(resolved))))
                        throw new IllegalArgumentException("dialog.workflow.missingFile");
                    urlField.setText(resolved); committing = true; ok.fire();
                }
            } catch (IllegalArgumentException failure) { showError(failure); }
        });
        setResultConverter(button -> button == DialogButtons.OK ? urlField.getText() : null);
        setOnCloseRequest(event -> { if (busy) event.consume(); });
        setOnHidden(event -> { if (image != null) image.cancel(); });
        if (initial != null) {
            urlField.setText(initial.getUrl()); alt.setText(AccessibleImageDecoration.alt(initial));
            changing = true;
            width.setText(initial.getWidth() < 0 ? "" : Integer.toString(initial.getWidth()));
            height.setText(initial.getHeight() < 0 ? "" : Integer.toString(initial.getHeight()));
            changing = false;
        }
        validate();
        setOnShown(event -> { urlField.requestFocus(); if (!urlField.getText().isBlank()) loadPreview(); });
    }
    private Button action(String key, Runnable action) { Button b = new Button(I18n.text(key)); b.setOnAction(e -> action.run()); return b; }
    private void setBusy(boolean busy) {
        this.busy = busy;
        getDialogPane().getContent().setDisable(busy);
        getDialogPane().lookupButton(DialogButtons.CANCEL).setDisable(busy);
        getDialogPane().lookupButton(DialogButtons.OK).setDisable(busy);
        if (!busy) validate();
    }
    private void showError(IllegalArgumentException failure) {
        String message = failure.getMessage();
        error.setText(message != null && message.startsWith("dialog.") ? I18n.text(message) : I18n.text("dialog.workflow.invalidTarget"));
    }
    private static int dimension(TextField field) {
        if (field.getText().isBlank()) return -1;
        int value = Integer.parseInt(field.getText());
        if (value < 1 || value > 100_000) throw new IllegalArgumentException("dialog.validation.integer");
        return value;
    }
    private void validate() {
        try {
            ImageAssets.resolve(urlField.getText(), directory); dimension(width); dimension(height);
            error.setText(""); getDialogPane().lookupButton(DialogButtons.OK).setDisable(false);
        } catch (IllegalArgumentException failure) { showError(failure); getDialogPane().lookupButton(DialogButtons.OK).setDisable(true); }
    }
    private void proportional(boolean fromWidth) {
        if (changing || !ratio.isSelected() || image == null || image.getWidth() <= 0 || image.getHeight() <= 0) return;
        try {
            int value = dimension(fromWidth ? width : height);
            if (value < 0) return;
            changing = true;
            (fromWidth ? height : width).setText(Long.toString(Math.max(1, Math.round(value * (fromWidth ? image.getHeight() / image.getWidth() : image.getWidth() / image.getHeight())))));
        } catch (IllegalArgumentException ignored) { /* Validation shows the invalid field. */ }
        finally { changing = false; }
    }
    private void loadPreview() {
        try {
            if (image != null) image.cancel();
            Image requested = new Image(ImageAssets.resolve(urlField.getText(), directory), true);
            image = requested; status.setText(I18n.text("dialog.writingLibrary.status.processing"));
            Runnable ready = () -> {
                if (image != requested) return;
                if (requested.isError()) { status.setText(I18n.text("dialog.workflow.imageFailed")); return; }
                if (requested.getProgress() >= 1) {
                    preview.setImage(requested); status.setText((int) requested.getWidth() + " × " + (int) requested.getHeight() + " px");
                }
            };
            requested.progressProperty().addListener((obs, old, value) -> ready.run());
            requested.errorProperty().addListener((obs, old, value) -> ready.run()); ready.run();
        } catch (IllegalArgumentException failure) { showError(failure); }
    }
    private void paste() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasFiles() && !clipboard.getFiles().isEmpty()) { urlField.setText(clipboard.getFiles().getFirst().toURI().toASCIIString()); loadPreview(); }
        else if (clipboard.hasImage()) {
            FileChooser chooser = new FileChooser(); chooser.setTitle(I18n.text("dialog.workflow.saveImage")); chooser.setInitialFileName("image.png");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
            if (directory != null && Files.isDirectory(directory)) chooser.setInitialDirectory(directory.toFile());
            var file = chooser.showSaveDialog(getDialogPane().getScene().getWindow());
            if (file == null) return;
            Image captured = clipboard.getImage();
            var reader = captured.getPixelReader(); if (reader == null) return;
            var buffer = new java.awt.image.BufferedImage((int) captured.getWidth(), (int) captured.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < buffer.getHeight(); y++) for (int x = 0; x < buffer.getWidth(); x++) buffer.setRGB(x, y, reader.getArgb(x, y));
            setBusy(true);
            Task<Boolean> task = new Task<>() { @Override protected Boolean call() throws Exception { return javax.imageio.ImageIO.write(buffer, "png", file); }};
            task.setOnSucceeded(e -> { setBusy(false); urlField.setText(file.toURI().toASCIIString()); loadPreview(); });
            task.setOnFailed(e -> { setBusy(false); error.setText(task.getException().getMessage()); });
            Thread.ofVirtual().name("clipboard-image").start(task);
        } else if (clipboard.hasString()) { urlField.setText(clipboard.getString()); loadPreview(); }
    }
    public TextField getUrlField() { return urlField; }
    public TextField getAltField() { return alt; }
    public ImageDecoration getImageDecoration() { return new AccessibleImageDecoration(urlField.getText(), dimension(width), dimension(height), imageLink, alt.getText()); }
}
