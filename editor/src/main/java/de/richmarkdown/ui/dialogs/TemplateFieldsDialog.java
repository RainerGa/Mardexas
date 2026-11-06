package de.richmarkdown.ui.dialogs;

import de.richmarkdown.io.TemplateFields;
import javafx.scene.control.ButtonType;
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

/** Values are substituted before a template enters the editor. */
public final class TemplateFieldsDialog extends Dialog<String> {
    public TemplateFieldsDialog(Window owner, String template) {
        initOwner(owner);
        setTitle("Vorlagenfelder ausfüllen");
        setHeaderText("Diese Werte werden einmalig als normaler Text eingesetzt.");
        GridPane grid = new GridPane(10, 8);
        Map<String, TextField> fields = new LinkedHashMap<>();
        int row = 0;
        for (String name : TemplateFields.names(template)) {
            TextField input = new TextField();
            input.setTooltip(new Tooltip("Wert für " + name));
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
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((javafx.scene.control.Button) getDialogPane().lookupButton(ButtonType.OK)).setTooltip(new Tooltip("Werte einsetzen und Vorlage übernehmen"));
        ((javafx.scene.control.Button) getDialogPane().lookupButton(ButtonType.CANCEL)).setTooltip(new Tooltip("Einfügen abbrechen"));
        setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            Map<String, String> values = new LinkedHashMap<>();
            fields.forEach((name, field) -> values.put(name, field.getText()));
            return TemplateFields.resolve(template, values);
        });
    }

    public static Optional<String> resolve(Window owner, String template) {
        return TemplateFields.names(template).isEmpty() ? Optional.of(template)
                : new TemplateFieldsDialog(owner, template).showAndWait();
    }
}
