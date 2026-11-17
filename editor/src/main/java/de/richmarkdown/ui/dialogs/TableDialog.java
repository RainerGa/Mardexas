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
import de.richmarkdown.ui.TableSize;
import javafx.scene.control.Button;
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
 * beim Speichern als GFM-Kopfzeile serialisiert. Die zuletzt gewählte Größe
 * wird dauerhaft im Anwendungsprofil gespeichert (D7).
 */
public class TableDialog extends Dialog<TableSize> {

    private static final int DEFAULT_ROWS = 3;
    private static final int DEFAULT_COLUMNS = 3;
    private static final int MAX_ROWS = 20;
    private static final int MAX_COLUMNS = 12;

    private static final String LAST_ROWS_KEY = "table.lastRows";
    private static final String LAST_COLUMNS_KEY = "table.lastColumns";

    private final Spinner<Integer> rowsSpinner = new Spinner<>();
    private final Spinner<Integer> columnsSpinner = new Spinner<>();

    /** Erstellt den lokalisierten Tabellendialog mit Standardgröße 3 × 3. */
    public TableDialog() {
        setTitle(I18n.text("dialog.table.title"));
        setHeaderText(I18n.text("dialog.table.header"));

        int rows = readSaved(LAST_ROWS_KEY, DEFAULT_ROWS, MAX_ROWS);
        int columns = readSaved(LAST_COLUMNS_KEY, DEFAULT_COLUMNS, MAX_COLUMNS);

        configureSpinner(rowsSpinner, rows, MAX_ROWS);
        configureSpinner(columnsSpinner, columns, MAX_COLUMNS);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label(I18n.text("dialog.table.rows")), rowsSpinner);
        grid.addRow(1, new Label(I18n.text("dialog.table.columns")), columnsSpinner);

        getDialogPane().setContent(grid);
        DialogButtons.applyDialogStyles(getDialogPane());
        getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL);

        Button ok = (Button) getDialogPane().lookupButton(DialogButtons.OK);
        ok.setDefaultButton(true);

        Label error = new Label();
        error.getStyleClass().add("rme-dialog-error");
        grid.add(error, 0, 2, 2, 1);
        Runnable validate = () -> {
            boolean valid = DialogButtons.validInteger(rowsSpinner, 1, MAX_ROWS)
                    && DialogButtons.validInteger(columnsSpinner, 1, MAX_COLUMNS);
            ok.setDisable(!valid);
            error.setText(valid ? "" : I18n.text("dialog.validation.integer"));
        };
        rowsSpinner.getEditor().textProperty().addListener((obs, old, value) -> validate.run());
        columnsSpinner.getEditor().textProperty().addListener((obs, old, value) -> validate.run());
        validate.run();
        setResultConverter(buttonType -> {
            if (buttonType == DialogButtons.OK) {
                rowsSpinner.commitValue();
                columnsSpinner.commitValue();
                I18n.setPreference(LAST_ROWS_KEY, Integer.toString(rowsSpinner.getValue()));
                I18n.setPreference(LAST_COLUMNS_KEY, Integer.toString(columnsSpinner.getValue()));
                return new TableSize(rowsSpinner.getValue(), columnsSpinner.getValue());
            }
            return null;
        });

        setOnShown(event -> rowsSpinner.requestFocus());
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

    private static int readSaved(String key, int fallback, int maxValue) {
        String value = I18n.preference(key, null);
        if (value != null) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed >= 1 && parsed <= maxValue) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }
}
