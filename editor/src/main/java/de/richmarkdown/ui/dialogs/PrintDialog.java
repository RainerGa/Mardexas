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
import javafx.collections.FXCollections;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.PrintColor;
import javafx.print.PrintSides;
import javafx.print.Printer;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Rein JavaFX-nativer Druckdialog ohne AWT/Swing.
 */
public class PrintDialog extends Dialog<PrintDialogResult> {

    private final ComboBox<Printer> printerBox = new ComboBox<>();
    private final Spinner<Integer> copiesSpinner = new Spinner<>();
    private final ChoiceBox<PageOrientation> orientationBox = new ChoiceBox<>();
    private final ChoiceBox<Paper> paperBox = new ChoiceBox<>();
    private final ChoiceBox<PrintSides> sidesBox = new ChoiceBox<>();
    private final ChoiceBox<PrintColor> colorBox = new ChoiceBox<>();

    /**
     * Erstellt den lokalisierten Druckdialog für die gegebenen Drucker.
     *
     * @param printers verfügbare Drucker (bereits auf Hintergrund-Thread
     *                 ermittelt); der erste Eintrag ist vorausgewählt
     * @param defaultPrinter bevorzugt vorausgewählter Drucker; {@code null}
     *                       wählt den ersten Eintrag
     */
    public PrintDialog(List<Printer> printers, Printer defaultPrinter) {
        setTitle(I18n.text("dialog.print.title"));
        setHeaderText(I18n.text("dialog.print.header"));

        List<Printer> sorted = new ArrayList<>(printers);
        sorted.sort(Comparator.comparing(Printer::getName,
                String.CASE_INSENSITIVE_ORDER));
        printerBox.setItems(FXCollections.observableArrayList(sorted));
        printerBox.setConverter(printerConverter(sorted));
        printerBox.setCellFactory(list -> printerCell());
        if (sorted.contains(defaultPrinter)) {
            printerBox.getSelectionModel().select(defaultPrinter);
        } else if (!sorted.isEmpty()) {
            printerBox.getSelectionModel().select(0);
        }

        copiesSpinner.setValueFactory(
                new IntegerSpinnerValueFactory(1, 99, 1));
        copiesSpinner.setEditable(true);

        orientationBox.setItems(FXCollections.observableArrayList(
                PageOrientation.PORTRAIT, PageOrientation.LANDSCAPE));
        orientationBox.setConverter(orientationConverter());
        orientationBox.getSelectionModel().select(PageOrientation.PORTRAIT);

        paperBox.setItems(FXCollections.observableArrayList(
                Paper.A4, Paper.NA_LETTER, Paper.NA_8X10, Paper.LEGAL,
                Paper.A5, Paper.A3, Paper.EXECUTIVE, Paper.TABLOID));
        paperBox.setConverter(paperConverter());
        paperBox.getSelectionModel().select(Paper.A4);

        sidesBox.setItems(FXCollections.observableArrayList(
                PrintSides.ONE_SIDED, PrintSides.DUPLEX, PrintSides.TUMBLE));
        sidesBox.setConverter(sidesConverter());
        sidesBox.getSelectionModel().select(PrintSides.ONE_SIDED);

        colorBox.setItems(FXCollections.observableArrayList(
                PrintColor.COLOR, PrintColor.MONOCHROME));
        colorBox.setConverter(colorConverter());
        colorBox.getSelectionModel().select(PrintColor.COLOR);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label(I18n.text("dialog.print.printer")), printerBox);
        grid.addRow(1, new Label(I18n.text("dialog.print.copies")), copiesSpinner);
        grid.addRow(2, new Label(I18n.text("dialog.print.orientation")), orientationBox);
        grid.addRow(3, new Label(I18n.text("dialog.print.paper")), paperBox);
        grid.addRow(4, new Label(I18n.text("dialog.print.sides")), sidesBox);
        grid.addRow(5, new Label(I18n.text("dialog.print.color")), colorBox);
        getDialogPane().setContent(grid);
        getDialogPane().setPrefWidth(420);

        getDialogPane().getButtonTypes().addAll(
                DialogButtons.PRINT, DialogButtons.CANCEL);
        setResultConverter(buttonType -> buttonType == DialogButtons.PRINT
                ? new PrintDialogResult(
                        printerBox.getSelectionModel().getSelectedItem(),
                        copiesSpinner.getValue(),
                        orientationBox.getValue(),
                        paperBox.getValue(),
                        sidesBox.getValue(),
                        colorBox.getValue())
                : null);
    }

    private static StringConverter<Printer> printerConverter(List<Printer> sorted) {
        return new StringConverter<>() {
            @Override
            public String toString(Printer printer) {
                return printer == null ? "" : printer.getName();
            }

            @Override
            public Printer fromString(String name) {
                return sorted.stream()
                        .filter(p -> p.getName().equals(name))
                        .findFirst()
                        .orElse(null);
            }
        };
    }

    private static ListCell<Printer> printerCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Printer printer, boolean empty) {
                super.updateItem(printer, empty);
                setText(empty || printer == null ? null : printer.getName());
            }
        };
    }

    private static StringConverter<PageOrientation> orientationConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(PageOrientation orientation) {
                if (orientation == PageOrientation.LANDSCAPE) {
                    return I18n.text("dialog.print.landscape");
                }
                return I18n.text("dialog.print.portrait");
            }

            @Override
            public PageOrientation fromString(String text) {
                return null;
            }
        };
    }

    private static StringConverter<Paper> paperConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Paper paper) {
                return paper == null ? "" : paper.getName();
            }

            @Override
            public Paper fromString(String text) {
                return null;
            }
        };
    }

    private static StringConverter<PrintSides> sidesConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(PrintSides sides) {
                if (sides == PrintSides.DUPLEX) {
                    return I18n.text("dialog.print.duplex");
                }
                if (sides == PrintSides.TUMBLE) {
                    return I18n.text("dialog.print.tumble");
                }
                return I18n.text("dialog.print.oneSided");
            }

            @Override
            public PrintSides fromString(String text) {
                return null;
            }
        };
    }

    private static StringConverter<PrintColor> colorConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(PrintColor color) {
                if (color == PrintColor.MONOCHROME) {
                    return I18n.text("dialog.print.monochrome");
                }
                return I18n.text("dialog.print.colorPrint");
            }

            @Override
            public PrintColor fromString(String text) {
                return null;
            }
        };
    }

    /** Drucker-Auswahlbox (für Tests und Vorauswahl). */
    public ComboBox<Printer> getPrinterBox() {
        return printerBox;
    }

    /** Kopien-Spinner 1 bis 99 (für Tests). */
    public Spinner<Integer> getCopiesSpinner() {
        return copiesSpinner;
    }

    /** Orientierungs-Auswahl (für Tests). */
    public ChoiceBox<PageOrientation> getOrientationBox() {
        return orientationBox;
    }

    /** Papier-Auswahl (für Tests). */
    public ChoiceBox<Paper> getPaperBox() {
        return paperBox;
    }

    /** Duplex-Auswahl (für Tests). */
    public ChoiceBox<PrintSides> getSidesBox() {
        return sidesBox;
    }

    /** Farb-Auswahl (für Tests). */
    public ChoiceBox<PrintColor> getColorBox() {
        return colorBox;
    }
}
