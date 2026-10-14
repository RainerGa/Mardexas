package de.richmarkdown.ui.dialogs;

import de.richmarkdown.i18n.I18n;
import de.richmarkdown.ui.TableSize;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.layout.GridPane;

/**
 * Erfasst Zeilen- und Spaltenanzahl für eine neue Tabelle.
 *
 * <p>Ergebnis ist ein {@link TableSize}; beim Abbruch bleibt {@code showAndWait()}
 * leer. Standardmäßig sind drei Zeilen und drei Spalten ausgewählt; Zeilen
 * können bis 20 und Spalten bis 12 eingestellt werden. Die erste Zeile wird
 * beim Speichern als GFM-Kopfzeile serialisiert.
 */
public class TableDialog extends Dialog<TableSize> {

    private static final int DEFAULT_ROWS = 3;
    private static final int DEFAULT_COLUMNS = 3;
    private static final int MAX_ROWS = 20;
    private static final int MAX_COLUMNS = 12;

    private final Spinner<Integer> rowsSpinner = new Spinner<>();
    private final Spinner<Integer> columnsSpinner = new Spinner<>();

    /** Erstellt den lokalisierten Tabellendialog mit Standardgröße 3 × 3. */
    public TableDialog() {
        setTitle(I18n.text("dialog.table.title"));
        setHeaderText(I18n.text("dialog.table.header"));

        configureSpinner(rowsSpinner, DEFAULT_ROWS, MAX_ROWS);
        configureSpinner(columnsSpinner, DEFAULT_COLUMNS, MAX_COLUMNS);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label(I18n.text("dialog.table.rows")), rowsSpinner);
        grid.addRow(1, new Label(I18n.text("dialog.table.columns")), columnsSpinner);
        getDialogPane().setContent(grid);
        getDialogPane().setPrefSize(260, 160);

        getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL);
        setResultConverter(buttonType -> buttonType == DialogButtons.OK
                ? new TableSize(rowsSpinner.getValue(), columnsSpinner.getValue())
                : null);
    }

    /**
     * Liefert den Spinner für die Tabellenzeilen.
     *
     * @return editierbarer Spinner mit einem Wertebereich von 1 bis 20
     */
    public Spinner<Integer> getRowsSpinner() {
        return rowsSpinner;
    }

    /**
     * Liefert den Spinner für die Tabellenspalten.
     *
     * @return editierbarer Spinner mit einem Wertebereich von 1 bis 12
     */
    public Spinner<Integer> getColumnsSpinner() {
        return columnsSpinner;
    }

    private void configureSpinner(Spinner<Integer> spinner, int initialValue, int maxValue) {
        IntegerSpinnerValueFactory factory = new IntegerSpinnerValueFactory(1, maxValue, initialValue);
        spinner.setValueFactory(factory);
        spinner.setEditable(true);
    }
}
