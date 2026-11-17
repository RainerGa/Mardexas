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
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;

/**
 * Zentral definierte, lokalisierte JavaFX-Buttontypen für die Dialoge der
 * Anwendung.
 *
 * <p>Die gemeinsamen Instanzen sorgen dafür, dass Beschriftung und
 * {@link ButtonBar.ButtonData} zwischen Dialogen konsistent sind. Die Labels
 * werden beim Laden dieser Klasse anhand der zu diesem Zeitpunkt aktiven
 * Sprache aufgelöst.
 */
public final class DialogButtons {

    /** Bestätigungsaktion mit OK-Semantik. */
    public static final ButtonType OK =
            new ButtonType(I18n.text("dialog.ok"), ButtonBar.ButtonData.OK_DONE);
    /** Druckaktion mit OK-Semantik für den Druckdialog. */
    public static final ButtonType PRINT =
            new ButtonType(I18n.text("dialog.print.print"), ButtonBar.ButtonData.OK_DONE);
    /** Abbruchaktion mit Cancel-Semantik. */
    public static final ButtonType CANCEL =
            new ButtonType(I18n.text("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
    /** Schließaktion ohne bestätigende Semantik. */
    public static final ButtonType CLOSE =
            new ButtonType(I18n.text("dialog.close"), ButtonBar.ButtonData.CANCEL_CLOSE);
    /** Zustimmung zum Verwerfen nicht gespeicherter Änderungen. */
    public static final ButtonType DISCARD =
            new ButtonType(I18n.text("confirm.yes"), ButtonBar.ButtonData.YES);
    /** Ablehnung des Verwerfens nicht gespeicherter Änderungen. */
    public static final ButtonType KEEP =
            new ButtonType(I18n.text("confirm.no"), ButtonBar.ButtonData.NO);

    /**
     * Bindet das anwendungseigene Dialog-Stylesheet an den angegebenen
     * {@link DialogPane}. Das Laden ist defensiv: fehlt die Ressource im
     * Klassenpfad/JAR, wird ohne Stil fortgefahren (kein NPE).
     *
     * @param pane Dialog-Pane, der das Stylesheet hinzugefügt werden soll
     */
    public static void applyDialogStyles(DialogPane pane) {
        applyPanelStyles(pane, null);
        pane.contentProperty().addListener((obs, old, content) -> decorate(content));
        decorate(pane.getContent());
        pane.getButtonTypes().addListener((javafx.collections.ListChangeListener<ButtonType>) change -> styleButtons(pane));
        styleButtons(pane);
    }

    /** Shared styling and saved accessibility theme for modeless panels as well. */
    public static void applyPanelStyles(javafx.scene.Parent root, javafx.scene.control.Button primary) {
        var resource = DialogButtons.class.getResource("/de/richmarkdown/ui/dialogs.css");
        if (resource != null && !root.getStylesheets().contains(resource.toExternalForm())) {
            root.getStylesheets().add(resource.toExternalForm());
        }
        root.getStyleClass().add("rme-dialog");
        boolean contrast = Boolean.parseBoolean(stylePreference("access.contrast", "false"));
        boolean dark = Boolean.parseBoolean(stylePreference("access.dark", "false"));
        root.getStyleClass().removeAll("rme-dark", "rme-high-contrast");
        if (contrast) root.getStyleClass().add("rme-high-contrast");
        else if (dark) root.getStyleClass().add("rme-dark");
        String size = stylePreference("access.size", "13");
        try {
            root.setStyle(root.getStyle() + "-fx-font-size: "
                    + Math.max(10, Math.min(32, Integer.parseInt(size))) + "px;");
        } catch (NumberFormatException ignored) {
            // Keep the stylesheet's font size for damaged settings.
        }
        decorate(root);
        if (primary != null) {
            primary.getStyleClass().add("rme-dialog-primary");
            primary.setDefaultButton(true);
            primary.setAccessibleText(primary.getText());
            primary.setMnemonicParsing(true);
            if (!primary.getText().contains("_")) primary.setText("_" + primary.getText());
        }
    }

    private static String stylePreference(String key, String fallback) {
        try {
            return I18n.preference(key, fallback);
        } catch (java.io.UncheckedIOException unavailable) {
            return fallback;
        }
    }

    private static void styleButtons(DialogPane pane) {
        for (ButtonType type : pane.getButtonTypes()) {
            if (!(pane.lookupButton(type) instanceof javafx.scene.control.Button button)) continue;
            String key = type == OK || type == ButtonType.OK ? "dialog.ok"
                    : type == CANCEL || type == ButtonType.CANCEL ? "dialog.cancel"
                    : type == CLOSE || type == ButtonType.CLOSE ? "dialog.close"
                    : type == PRINT ? "dialog.print.print" : null;
            if (key != null) button.setText(I18n.text(key));
            button.setAccessibleText(button.getText());
            button.setMnemonicParsing(true);
            if (button.getTooltip() == null) button.setTooltip(new javafx.scene.control.Tooltip(button.getText()));
            if (type.getButtonData().isDefaultButton()) {
                if (!button.getStyleClass().contains("rme-dialog-primary")) button.getStyleClass().add("rme-dialog-primary");
                button.setDefaultButton(true);
                if (!button.getText().contains("_")) button.setText("_" + button.getText());
            }
        }
    }

    private static void decorate(javafx.scene.Node node) {
        if (node == null) return;
        if (node instanceof javafx.scene.control.TextInputControl field) {
            if (!field.getStyleClass().contains("rme-dialog-field")) field.getStyleClass().add("rme-dialog-field");
        }
        if (node instanceof javafx.scene.layout.GridPane grid) {
            grid.setHgap(12);
            grid.setVgap(10);
            if (!grid.getStyleClass().contains("rme-dialog-section")) grid.getStyleClass().add("rme-dialog-section");
            for (javafx.scene.Node child : grid.getChildren()) {
                if (child instanceof javafx.scene.control.Label label && label.getLabelFor() == null) {
                    int row = java.util.Objects.requireNonNullElse(javafx.scene.layout.GridPane.getRowIndex(label), 0);
                    grid.getChildren().stream().filter(field -> field != label
                            && java.util.Objects.requireNonNullElse(javafx.scene.layout.GridPane.getRowIndex(field), 0) == row
                            && field instanceof javafx.scene.control.Control).findFirst().ifPresent(field -> {
                                label.setLabelFor(field);
                                if (field.getAccessibleText() == null) field.setAccessibleText(label.getText());
                                if (field instanceof javafx.scene.control.Control control && control.getTooltip() == null) {
                                    control.setTooltip(new javafx.scene.control.Tooltip(label.getText()));
                                }
                            });
                }
            }
        }
        if (node instanceof javafx.scene.layout.VBox box) box.setSpacing(12);
        if (node instanceof javafx.scene.control.ScrollPane scroll) decorate(scroll.getContent());
        else if (node instanceof javafx.scene.control.TitledPane titled) decorate(titled.getContent());
        else if (node instanceof javafx.scene.control.SplitPane split) split.getItems().forEach(DialogButtons::decorate);
        else if (node instanceof javafx.scene.control.TabPane tabs) tabs.getTabs().forEach(tab -> decorate(tab.getContent()));
        else if (node instanceof javafx.scene.Parent parent) parent.getChildrenUnmodifiable().forEach(DialogButtons::decorate);
    }

    /** Checks the visible spinner editor without committing or silently clamping input. */
    public static boolean validInteger(javafx.scene.control.Spinner<Integer> spinner, int min, int max) {
        try {
            int value = Integer.parseInt(spinner.getEditor().getText().strip());
            return value >= min && value <= max;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    /** A descriptive confirmation action with the shared affirmative semantics. */
    public static ButtonType confirmation(String key) {
        return new ButtonType(I18n.text(key), ButtonBar.ButtonData.OK_DONE);
    }

    private DialogButtons() {
    }
}
