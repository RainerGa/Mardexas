package de.richmarkdown.ui.dialogs;

import de.richmarkdown.features.tables.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import java.util.*;

/** Preview and validate delimiter/header interpretation before importing or exporting. */
public final class DelimitedTableDialog extends Dialog<TableData> {
    private TableData result;
    private final ComboBox<String> separator = new ComboBox<>();
    public DelimitedTableDialog(Window owner, String source, TableData exporting) {
        initOwner(owner); setTitle(WorkflowDialogs.text(exporting == null ? "preview" : "export")); setResizable(true);
        separator.getItems().setAll(",", ";", "Tab"); separator.setValue(source != null && source.contains("\t") ? "Tab" : source != null && source.contains(";") ? ";" : ",");
        CheckBox header = new CheckBox(WorkflowDialogs.text("headerRow")); header.setSelected(true); header.setDisable(exporting != null);
        TextArea preview = WorkflowDialogs.output(""); preview.setWrapText(false); Label error = new Label(); error.getStyleClass().add("rme-dialog-error");
        VBox body = new VBox(8, new HBox(8, new Label(WorkflowDialogs.text("separator")), separator), header, preview, error); body.setPrefSize(850, 550); VBox.setVgrow(preview, Priority.ALWAYS);
        getDialogPane().setContent(body); getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL); DialogButtons.applyDialogStyles(getDialogPane());
        Runnable validate = () -> {
            try {
                result = exporting == null ? DelimitedTables.parse(source, separator()) : exporting;
                if (exporting == null && !header.isSelected()) {
                    List<List<TableData.Cell>> rows = new ArrayList<>(); List<TableData.Cell> headings = new ArrayList<>();
                    for (int i = 0; i < result.width(); i++) headings.add(new TableData.Cell(WorkflowDialogs.text("column") + " " + (i + 1)));
                    rows.add(headings); rows.addAll(result.rows()); result = new TableData(rows);
                }
                preview.setText(exporting == null ? DelimitedTables.write(result, '\t') : DelimitedTables.write(result, separator()));
                error.setText(""); getDialogPane().lookupButton(DialogButtons.OK).setDisable(false);
            } catch (IllegalArgumentException failure) { result = null; error.setText(failure.getMessage()); getDialogPane().lookupButton(DialogButtons.OK).setDisable(true); }
        };
        separator.valueProperty().addListener((obs, old, value) -> validate.run()); header.selectedProperty().addListener((obs, old, value) -> validate.run()); validate.run();
        setResultConverter(button -> button == DialogButtons.OK ? result : null);
    }
    public char separator() { return "Tab".equals(separator.getValue()) ? '\t' : separator.getValue().charAt(0); }
}
