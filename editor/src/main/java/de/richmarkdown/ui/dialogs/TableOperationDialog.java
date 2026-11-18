package de.richmarkdown.ui.dialogs;

import de.richmarkdown.features.tables.TableData;
import de.richmarkdown.features.tables.DelimitedTables;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import java.util.*;

/** A complete table operation, including all coordinates, validation and a before/after preview. */
public final class TableOperationDialog extends Dialog<List<TableData>> {
    public enum Operation { COPY, PASTE, SORT, DUPLICATE_ROW, DUPLICATE_COLUMN, MOVE_ROW, MOVE_COLUMN, SPLIT, JOIN, CALCULATE }
    private List<TableData> result;
    private final List<Runnable> triggers = new ArrayList<>();
    private final VBox options = new VBox(8);
    private final TableData table;
    private final int row;
    private final int column;
    public TableOperationDialog(Window owner, String title, TableData table, Operation operation, TableData other, int currentRow, int currentColumn) {
        this.table = table; row = Math.clamp(currentRow + 1, 1, table.rows().size()); column = Math.clamp(currentColumn, 0, table.width() - 1);
        initOwner(owner); setTitle(title); setResizable(true);
        Label error = new Label(); error.setWrapText(true); error.getStyleClass().add("rme-dialog-error");
        TextArea before = WorkflowDialogs.output(DelimitedTables.write(table, '\t'));
        TextArea after = WorkflowDialogs.output(""); before.setWrapText(false); after.setWrapText(false);
        after.setId("table-operation-preview");
        SplitPane preview = new SplitPane(new VBox(5, new Label(WorkflowDialogs.text("source")), before), new VBox(5, new Label(WorkflowDialogs.text("result")), after));
        VBox.setVgrow(before, Priority.ALWAYS); VBox.setVgrow(after, Priority.ALWAYS);
        java.util.concurrent.Callable<List<TableData>> compute;
        switch (operation) {
            case COPY -> {
                Spinner<Integer> r1 = number("from", row, 1, table.rows().size()), r2 = number("to", row, 1, table.rows().size());
                ComboBox<Integer> c1 = column("from"), c2 = column("to");
                compute = () -> List.of(table.region(value(r1) - 1, c1.getValue(), value(r2) - 1, c2.getValue()));
            }
            case PASTE -> {
                Spinner<Integer> r = number("line", row, 1, table.rows().size()); ComboBox<Integer> c = column("column");
                options.getChildren().add(new Label(WorkflowDialogs.text("replace")));
                compute = () -> List.of(table.paste(value(r) - 1, c.getValue(), Objects.requireNonNull(other)));
            }
            case MOVE_ROW -> {
                Spinner<Integer> from = number("from", row, 1, table.rows().size()), to = number("to", row, 1, table.rows().size());
                compute = () -> List.of(table.moveRow(value(from) - 1, value(to) - 1));
            }
            case MOVE_COLUMN -> {
                ComboBox<Integer> from = column("from"), to = column("to"); compute = () -> List.of(table.moveColumn(from.getValue(), to.getValue()));
            }
            case DUPLICATE_ROW -> { Spinner<Integer> from = number("line", row, 1, table.rows().size()); compute = () -> List.of(table.duplicateRow(value(from) - 1)); }
            case DUPLICATE_COLUMN -> { ComboBox<Integer> from = column("column"); compute = () -> List.of(table.duplicateColumn(from.getValue())); }
            case SPLIT -> { if (table.rows().size() < 2) throw new IllegalArgumentException(WorkflowDialogs.text("invalidTarget")); Spinner<Integer> from = number("line", Math.max(2, row), 2, table.rows().size()); compute = () -> table.split(value(from) - 1); }
            case JOIN -> {
                CheckBox omit = new CheckBox(WorkflowDialogs.text("omitSecondHeader")); omit.setSelected(true); options.getChildren().add(omit);
                changed(omit.selectedProperty());
                options.getChildren().add(WorkflowDialogs.output(DelimitedTables.write(Objects.requireNonNull(other), '\t')));
                compute = () -> List.of(table.join(other, omit.isSelected()));
            }
            case CALCULATE -> {
                ComboBox<Integer> source = column("source"), target = column("to");
                Spinner<Integer> destination = number("line", row, 1, table.rows().size());
                ComboBox<TableData.Calculation> calculation = new ComboBox<>(); calculation.getItems().setAll(TableData.Calculation.values()); calculation.setValue(TableData.Calculation.SUM);
                calculation.setConverter(new javafx.util.StringConverter<>() {
                    @Override public String toString(TableData.Calculation value) { return value == null ? "" : WorkflowDialogs.text(value.name().toLowerCase(Locale.ROOT)); }
                    @Override public TableData.Calculation fromString(String value) { throw new UnsupportedOperationException(); }
                });
                changed(calculation.valueProperty()); options.getChildren().add(calculation);
                Label result = new Label(); options.getChildren().add(result);
                compute = () -> {
                    String number = table.calculate(source.getValue(), calculation.getValue()).toPlainString();
                    result.setText(WorkflowDialogs.text("result") + ": " + number + " · " + WorkflowDialogs.text("replace") + ": " + table.rows().get(value(destination) - 1).get(target.getValue()).text());
                    return List.of(table.replaceCell(value(destination) - 1, target.getValue(), number));
                };
            }
            case SORT -> {
                List<ComboBox<Integer>> columns = new ArrayList<>(); List<CheckBox> numbers = new ArrayList<>(), desc = new ArrayList<>();
                VBox criteria = new VBox(5); options.getChildren().add(criteria);
                Runnable add = () -> {
                    ComboBox<Integer> c = columnChoice(); columns.add(c);
                    CheckBox numeric = new CheckBox(WorkflowDialogs.text("numeric")), descending = new CheckBox(WorkflowDialogs.text("descending"));
                    numbers.add(numeric); desc.add(descending); changed(c.valueProperty()); changed(numeric.selectedProperty()); changed(descending.selectedProperty());
                    HBox criterion = new HBox(8, new Label(Integer.toString(columns.size())), c, numeric, descending);
                    criteria.getChildren().add(criterion);
                };
                add.run();
                Button addButton = WorkflowDialogs.button("addCriterion", () -> { if (columns.size() < table.width()) { add.run(); triggers.forEach(Runnable::run); } });
                Button remove = WorkflowDialogs.button("removeCriterion", () -> { if (columns.size() > 1) { columns.removeLast(); numbers.removeLast(); desc.removeLast(); criteria.getChildren().removeLast(); triggers.forEach(Runnable::run); } });
                options.getChildren().add(new HBox(8, addButton, remove));
                compute = () -> {
                    List<TableData.SortKey> keys = new ArrayList<>();
                    for (int i = 0; i < columns.size(); i++) keys.add(new TableData.SortKey(columns.get(i).getValue(), numbers.get(i).isSelected(), desc.get(i).isSelected()));
                    return List.of(table.sortBy(keys));
                };
            }
            default -> throw new IllegalArgumentException();
        }
        VBox body = new VBox(10, options, preview, error); body.setPrefSize(950, 650); VBox.setVgrow(preview, Priority.ALWAYS);
        getDialogPane().setContent(body); getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL); DialogButtons.applyDialogStyles(getDialogPane());
        ((Button) getDialogPane().lookupButton(DialogButtons.OK)).setText(WorkflowDialogs.text(operation == Operation.COPY ? "copy" : "apply"));
        Runnable validate = () -> {
            try {
                result = compute.call(); error.setText("");
                after.setText(result.stream().map(t -> DelimitedTables.write(t, '\t')).collect(java.util.stream.Collectors.joining("\n")));
                getDialogPane().lookupButton(DialogButtons.OK).setDisable(false);
            } catch (Exception failure) { result = null; error.setText(Objects.toString(failure.getMessage(), WorkflowDialogs.text("invalidTarget"))); getDialogPane().lookupButton(DialogButtons.OK).setDisable(true); }
        };
        triggers.add(validate); validate.run();
        setResultConverter(button -> button == DialogButtons.OK ? result : null);
    }
    private <T> void changed(javafx.beans.value.ObservableValue<T> observable) { observable.addListener((obs, old, value) -> triggers.forEach(Runnable::run)); }
    private Spinner<Integer> number(String key, int initial, int minimum, int maximum) {
        Spinner<Integer> spinner = new Spinner<>(minimum, maximum, Math.clamp(initial, minimum, maximum)); spinner.setEditable(true);
        changed(spinner.getEditor().textProperty()); options.getChildren().add(new HBox(8, new Label(WorkflowDialogs.text(key)), spinner)); return spinner;
    }
    private static int value(Spinner<Integer> spinner) {
        var factory = (SpinnerValueFactory.IntegerSpinnerValueFactory) spinner.getValueFactory();
        if (!DialogButtons.validInteger(spinner, factory.getMin(), factory.getMax())) throw new IllegalArgumentException(de.richmarkdown.i18n.I18n.text("dialog.validation.integer"));
        return Integer.parseInt(spinner.getEditor().getText());
    }
    private ComboBox<Integer> columnChoice() {
        ComboBox<Integer> combo = new ComboBox<>(); for (int i = 0; i < table.width(); i++) combo.getItems().add(i); combo.setValue(column);
        combo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Integer value) { return value == null ? "" : (value + 1) + ": " + table.rows().getFirst().get(value).text(); }
            @Override public Integer fromString(String value) { throw new UnsupportedOperationException(); }
        }); return combo;
    }
    private ComboBox<Integer> column(String key) { ComboBox<Integer> combo = columnChoice(); changed(combo.valueProperty()); options.getChildren().add(new HBox(8, new Label(WorkflowDialogs.text(key)), combo)); return combo; }
}
