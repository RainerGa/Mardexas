package de.richmarkdown.ui;

import de.richmarkdown.i18n.I18n;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/** Display settings only. Text decorations and serialized Markdown remain unchanged. */
public final class AccessibilityOptions {
    private final EditorWindow window;
    public AccessibilityOptions(EditorWindow window) { this.window = window; applySaved(); }
    public void apply(boolean dark, boolean contrast, int size) {
        if (size < 10 || size > 32) throw new IllegalArgumentException("UI size must be between 10 and 32");
        String style = "-fx-font-size: " + size + "px;";
        if (contrast) style += "-fx-base: black; -fx-background: black; -fx-control-inner-background: black; -fx-text-base-color: white; -fx-text-inner-color: white; -fx-accent: yellow; -fx-focus-color: yellow;";
        else if (dark) style += "-fx-base: #303238; -fx-background: #25272b; -fx-control-inner-background: #25272b; -fx-text-base-color: #eeeeee; -fx-text-inner-color: #eeeeee; -fx-accent: #6ea5ed;";
        window.getEditorRoot().getStyleClass().removeAll("rme-dark", "rme-high-contrast");
        if (contrast) window.getEditorRoot().getStyleClass().add("rme-high-contrast");
        else if (dark) window.getEditorRoot().getStyleClass().add("rme-dark");
        window.getEditorRoot().setStyle(style);
    }
    static int savedSize() {
        int size;
        try { size = Integer.parseInt(I18n.preference("access.size", "13")); } catch (NumberFormatException invalid) { size = 13; }
        return Math.max(10, Math.min(32, size));
    }
    private void applySaved() {
        apply(Boolean.parseBoolean(I18n.preference("access.dark", "false")), Boolean.parseBoolean(I18n.preference("access.contrast", "false")), savedSize());
    }
    public void show() {
        CheckBox dark = new CheckBox(FeatureUi.text("Dunkle Oberfläche", "Dark interface")); dark.setSelected(Boolean.parseBoolean(I18n.preference("access.dark", "false")));
        CheckBox contrast = new CheckBox(FeatureUi.text("Hoher Kontrast", "High contrast")); contrast.setSelected(Boolean.parseBoolean(I18n.preference("access.contrast", "false")));
        Spinner<Integer> size = new Spinner<>(10, 32, savedSize()); size.setEditable(true);
        size.setTooltip(new Tooltip(FeatureUi.text("Schriftgröße der Bedienoberfläche", "Interface text size")));
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.initOwner(window.getDialogOwner());
        dialog.setTitle(FeatureUi.text("Lesbarkeit und Bedienung", "Readability and accessibility"));
        dialog.getDialogPane().setContent(new VBox(10, dark, contrast, new Label(FeatureUi.text("Oberflächenschriftgröße", "Interface text size")), size));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.showAndWait().filter(result -> result == ButtonType.OK).ifPresent(result -> {
            try {
                size.commitValue();
                I18n.setPreference("access.dark", Boolean.toString(dark.isSelected()));
                I18n.setPreference("access.contrast", Boolean.toString(contrast.isSelected()));
                I18n.setPreference("access.size", size.getValue().toString());
                apply(dark.isSelected(), contrast.isSelected(), size.getValue());
            } catch (RuntimeException failure) { FeatureUi.error(window.getDialogOwner(), failure); }
        });
    }
}
