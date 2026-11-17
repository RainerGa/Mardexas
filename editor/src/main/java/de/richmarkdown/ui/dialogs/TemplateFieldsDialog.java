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

import de.richmarkdown.io.TemplateFields;
import de.richmarkdown.i18n.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Values are substituted before a template enters the document. */
public final class TemplateFieldsDialog extends Dialog<String> {
    public TemplateFieldsDialog(Window owner, String template) {
        initOwner(owner);
        setTitle(I18n.text("dialog.templateFields.title"));
        setHeaderText(I18n.text("dialog.templateFields.header"));

        GridPane grid = new GridPane(10, 8);
        Map<String, TextField> fields = new LinkedHashMap<>();
        int row = 0;
        for (String name : TemplateFields.names(template)) {
            TextField input = new TextField();
            input.setTooltip(new Tooltip(I18n.text("dialog.templateFields.tooltip")));
            input.setAccessibleText(name);
            input.setId("template-field-" + row);
            fields.put(name, input);
            grid.addRow(row++, new Label(name), input);
        }
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(500);
        scroll.setPrefViewportHeight(Math.min(500, Math.max(80, row * 40)));
        getDialogPane().setContent(scroll);

        DialogButtons.applyDialogStyles(getDialogPane());
        getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL);

        Button ok = (Button) getDialogPane().lookupButton(DialogButtons.OK);
        ok.setDefaultButton(true);
        ok.setTooltip(new Tooltip(I18n.text("dialog.templateFields.button.ok")));
        ((Button) getDialogPane().lookupButton(DialogButtons.CANCEL))
                .setTooltip(new Tooltip(I18n.text("dialog.templateFields.button.cancel")));

        Label error = new Label(I18n.text("dialog.validation.required"));
        error.getStyleClass().add("rme-dialog-error");
        grid.add(error, 0, row, 2, 1);
        Runnable validate = () -> {
            boolean invalid = fields.values().stream().anyMatch(field -> field.getText().isBlank());
            ok.setDisable(invalid);
            error.setVisible(invalid);
            error.setManaged(invalid);
        };
        fields.values().forEach(field -> field.textProperty().addListener((obs, old, value) -> validate.run()));
        validate.run();
        setResizable(true);
        setResultConverter(button -> {
            if (button != DialogButtons.OK) return null;
            Map<String, String> values = new LinkedHashMap<>();
            fields.forEach((name, field) -> values.put(name, field.getText()));
            return TemplateFields.resolve(template, values);
        });

        setOnShown(event -> {
            var first = fields.values().stream().findFirst();
            first.ifPresent(TextField::requestFocus);
        });
    }

    public static Optional<String> resolve(Window owner, String template) {
        return TemplateFields.names(template).isEmpty() ? Optional.of(template)
                : new TemplateFieldsDialog(owner, template).showAndWait();
    }
}
