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

import de.richmarkdown.editor.DocumentStatistics;
import de.richmarkdown.i18n.I18n;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Statistik-Dialog nach dem Vorbild LibreOffice Writer „Extras → Statistik“
 * (AddFeatures #3).
 *
 * <p>Wie in LO Writer zeigt der Dialog eine Liste beschrifteter Zählfelder
 * (Wörter, Zeichen mit/ohne Leerzeichen, Leerzeichen, Sätze, Absätze,
 * Zeilen, Tabellen, Bilder) und bietet eine Schaltfläche „Aktualisieren“,
 * die die Zahlen neu aus dem Dokument berechnet. Die Werte werden beim
 * Öffnen des Dialogs berechnet; die Berechnung selbst liegt außerhalb des
 * Dialogs (LO-Vorgehen: der Dialog zeigt nur die Auswertung).
 *
 * <p>Optisch folgt der Dialog der LO-Anordnung: links die Beschriftungen,
 * rechts die rechtsbündigen Zahlen, darunter die Aktualisieren-Schaltfläche.
 */
public class StatisticsDialog extends Dialog<ButtonType> {

    private final Supplier<DocumentStatistics> statisticsSupplier;
    private final Map<String, Label> valueLabels = new LinkedHashMap<>();
    private final Button updateButton = new Button(I18n.text("dialog.statistics.update"));

    /**
     * Erstellt den lokalisierten Statistikdialog mit aktuellen Werten.
     *
     * @param statisticsSupplier liefert die aktuelle Dokumentstatistik;
     *                           wird beim Öffnen und bei „Aktualisieren“ abgefragt
     */
    public StatisticsDialog(Supplier<DocumentStatistics> statisticsSupplier) {
        this.statisticsSupplier = statisticsSupplier;
        setTitle(I18n.text("dialog.statistics.title"));
        setHeaderText(I18n.text("dialog.statistics.header"));

        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(4);
        ColumnConstraints labels = new ColumnConstraints();
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        values.setFillWidth(true);
        grid.getColumnConstraints().addAll(labels, values);

        addRow(grid, "dialog.statistics.words");
        addRow(grid, "dialog.statistics.charactersWithSpaces");
        addRow(grid, "dialog.statistics.charactersWithoutSpaces");
        addRow(grid, "dialog.statistics.spaces");
        addRow(grid, "dialog.statistics.sentences");
        addRow(grid, "dialog.statistics.paragraphs");
        addRow(grid, "dialog.statistics.lines");
        addRow(grid, "dialog.statistics.tables");
        addRow(grid, "dialog.statistics.images");

        updateButton.getStyleClass().add("rme-statistics-update");
        updateButton.setDefaultButton(false);
        updateButton.setOnAction(event -> refresh());

        HBox buttonRow = new HBox(updateButton);
        buttonRow.setPadding(new Insets(8, 0, 0, 0));

        VBox content = new VBox(12, grid, buttonRow);
        content.setPadding(new Insets(4));
        getDialogPane().setContent(content);
        getDialogPane().setPrefSize(380, 360);
        getDialogPane().getButtonTypes().add(DialogButtons.CLOSE);

        refresh();
    }

    /**
     * Fügt eine Beschriftungs-/Wert-Zeile hinzu und registriert das Wert-Label.
     *
     * @param grid Zielpanel
     * @param labelKey I18n-Schlüssel der Beschriftung
     */
    private void addRow(GridPane grid, String labelKey) {
        int row = grid.getRowCount();
        Label caption = new Label(I18n.text(labelKey));
        caption.getStyleClass().add("rme-statistics-label");
        Label value = new Label("0");
        value.getStyleClass().add("rme-statistics-value");
        value.setMinWidth(80);
        grid.add(caption, 0, row);
        grid.add(value, 1, row);
        valueLabels.put(labelKey, value);
    }

    /**
     * Berechnet die Statistik neu und aktualisiert alle Zählfelder.
     *
     * <p>Entspricht der „Aktualisieren“-Schaltfläche des LO-Dialogs.
     */
    public void refresh() {
        DocumentStatistics stats = statisticsSupplier.get();
        setValue("dialog.statistics.words", stats.words());
        setValue("dialog.statistics.charactersWithSpaces", stats.charactersWithSpaces());
        setValue("dialog.statistics.charactersWithoutSpaces", stats.charactersWithoutSpaces());
        setValue("dialog.statistics.spaces", stats.spaces());
        setValue("dialog.statistics.sentences", stats.sentences());
        setValue("dialog.statistics.paragraphs", stats.paragraphs());
        setValue("dialog.statistics.lines", stats.lines());
        setValue("dialog.statistics.tables", stats.tables());
        setValue("dialog.statistics.images", stats.images());
    }

    private void setValue(String labelKey, long value) {
        valueLabels.get(labelKey).setText(Long.toString(value));
    }

    /**
     * Liefert das Wert-Label eines Zählfelds (für UI-Tests und individuelle
     * Nachgestaltungen).
     *
     * @param labelKey I18n-Schlüssel der Zeile
     * @return rechtsbündiges Wert-Label der Zeile
     */
    public Label getValueLabel(String labelKey) {
        return valueLabels.get(labelKey);
    }

    /**
     * Liefert die Aktualisieren-Schaltfläche (für UI-Tests).
     *
     * @return Schaltfläche „Aktualisieren“
     */
    public Button getUpdateButton() {
        return updateButton;
    }
}
