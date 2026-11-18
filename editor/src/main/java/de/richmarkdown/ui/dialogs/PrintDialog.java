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
import javafx.scene.control.Button;
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

    private static final String LAST_PRINTER = "print.lastPrinter";
    private static final String LAST_COPIES = "print.lastCopies";
    private static final String LAST_ORIENTATION = "print.lastOrientation";
    private static final String LAST_PAPER = "print.lastPaper";
    private static final String LAST_SIDES = "print.lastSides";
    private static final String LAST_COLOR = "print.lastColor";

    private de.richmarkdown.features.printing.PrintOptionsDialog optionsPanel;
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
        Printer selectedPrinter = restorePrinter(sorted, defaultPrinter);
        if (selectedPrinter != null) {
            printerBox.getSelectionModel().select(selectedPrinter);
        } else if (!sorted.isEmpty()) {
            printerBox.getSelectionModel().select(0);
        }

        int savedCopies = readInt(LAST_COPIES, 1, 1, 99);
        copiesSpinner.setValueFactory(
                new IntegerSpinnerValueFactory(1, 99, savedCopies));
        copiesSpinner.setEditable(true);

        orientationBox.setItems(FXCollections.observableArrayList(
                PageOrientation.PORTRAIT, PageOrientation.LANDSCAPE));
        orientationBox.setConverter(orientationConverter());
        selectEnum(orientationBox, readEnum(LAST_ORIENTATION, PageOrientation.class,
                PageOrientation.PORTRAIT));

        paperBox.setItems(FXCollections.observableArrayList(
                Paper.A4, Paper.NA_LETTER, Paper.NA_8X10, Paper.LEGAL,
                Paper.A5, Paper.A3, Paper.EXECUTIVE, Paper.TABLOID));
        paperBox.setConverter(paperConverter());
        paperBox.setValue(paperBox.getItems().stream()
                .filter(p -> p.getName().equals(I18n.preference(LAST_PAPER, "")))
                .findFirst().orElse(Paper.A4));

        sidesBox.setItems(FXCollections.observableArrayList(
                PrintSides.ONE_SIDED, PrintSides.DUPLEX, PrintSides.TUMBLE));
        sidesBox.setConverter(sidesConverter());
        selectEnum(sidesBox, readEnum(LAST_SIDES, PrintSides.class, PrintSides.ONE_SIDED));

        colorBox.setItems(FXCollections.observableArrayList(
                PrintColor.COLOR, PrintColor.MONOCHROME));
        colorBox.setConverter(colorConverter());
        selectEnum(colorBox, readEnum(LAST_COLOR, PrintColor.class, PrintColor.COLOR));

        Runnable capabilities = () -> {
            Printer printer = printerBox.getValue(); if (printer == null) return;
            var attributes = printer.getPrinterAttributes();
            supported(orientationBox, attributes.getSupportedPageOrientations(), attributes.getDefaultPageOrientation());
            supported(paperBox, attributes.getSupportedPapers(), attributes.getDefaultPaper());
            supported(sidesBox, attributes.getSupportedPrintSides(), attributes.getDefaultPrintSides());
            supported(colorBox, attributes.getSupportedPrintColors(), attributes.getDefaultPrintColor());
            int maximum = Math.min(99, attributes.getMaxCopies());
            copiesSpinner.setValueFactory(new IntegerSpinnerValueFactory(1, Math.max(1, maximum), Math.min(copiesSpinner.getValue(), Math.max(1, maximum))));
        };
        capabilities.run();
        printerBox.valueProperty().addListener((obs, old, value) -> capabilities.run());
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

        DialogButtons.applyDialogStyles(getDialogPane());
        getDialogPane().getButtonTypes().addAll(
                DialogButtons.PRINT, DialogButtons.CANCEL);
        Button print = (Button) getDialogPane().lookupButton(DialogButtons.PRINT);
        print.setDefaultButton(true);

        setResultConverter(buttonType -> {
            if (buttonType != DialogButtons.PRINT) return null;
            copiesSpinner.commitValue();
            savePreferences();
            return new PrintDialogResult(printerBox.getValue(), copiesSpinner.getValue(),
                    orientationBox.getValue(), paperBox.getValue(), sidesBox.getValue(), colorBox.getValue(), optionsPanel == null ? null : optionsPanel.commitOptions());
        });
        Label error = new Label();
        error.getStyleClass().add("rme-dialog-error");
        grid.add(error, 0, 6, 2, 1);
        Runnable validate = () -> {
            boolean valid = DialogButtons.validInteger(copiesSpinner, 1, printerBox.getValue() == null ? 99 : Math.min(99, printerBox.getValue().getPrinterAttributes().getMaxCopies()));
            print.setDisable(!valid || printerBox.getValue() == null || optionsPanel != null && !optionsPanel.validProperty().get());
            error.setText(valid ? "" : I18n.text("dialog.validation.integer"));
        };
        copiesSpinner.getEditor().textProperty().addListener((obs, old, value) -> validate.run());
        printerBox.valueProperty().addListener((obs, old, value) -> validate.run());
        validate.run();
        setOnShown(event -> printerBox.requestFocus());
    }

    /** Combined device, layout and snapshot-preview workflow. The owner stays unset for X11/GTK. */
    public PrintDialog(List<Printer> printers, Printer defaultPrinter, de.richmarkdown.features.printing.PrintOptions initial,
                       boolean hasSelection, java.util.function.BiConsumer<PrintDialogResult, javafx.stage.Window> preview) {
        this(printers, defaultPrinter);
        optionsPanel = new de.richmarkdown.features.printing.PrintOptionsDialog(null, initial, hasSelection);
        javafx.scene.Node layout = optionsPanel.getDialogPane().getContent(); optionsPanel.getDialogPane().setContent(null);
        javafx.scene.control.Tab device = new javafx.scene.control.Tab(I18n.text("dialog.print.title"), getDialogPane().getContent()); device.setClosable(false);
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(layout); scroll.setFitToWidth(true);
        javafx.scene.control.Tab options = new javafx.scene.control.Tab(I18n.text("dialog.printOptions.title"), scroll); options.setClosable(false);
        Button showPreview = WorkflowDialogs.button("preview", () -> {
            try {
                PrintDialogResult choice = new PrintDialogResult(printerBox.getValue(), copiesSpinner.getValue(), orientationBox.getValue(), paperBox.getValue(), sidesBox.getValue(), colorBox.getValue(), optionsPanel.currentOptions());
                preview.accept(choice, getDialogPane().getScene().getWindow());
            } catch (RuntimeException failure) { de.richmarkdown.ui.FeatureUi.error(getDialogPane().getScene().getWindow(), failure); }
        });
        showPreview.disableProperty().bind(optionsPanel.validProperty().not().or(printerBox.valueProperty().isNull()));
        getDialogPane().setContent(new javafx.scene.layout.VBox(10, new javafx.scene.control.TabPane(device, options), showPreview));
        getDialogPane().setPrefSize(620, 670); setResizable(true);
        Button print = (Button) getDialogPane().lookupButton(DialogButtons.PRINT);
        Runnable validate = () -> print.setDisable(!optionsPanel.validProperty().get() || printerBox.getValue() == null || !DialogButtons.validInteger(copiesSpinner, 1, printerBox.getValue() == null ? 99 : Math.min(99, printerBox.getValue().getPrinterAttributes().getMaxCopies())));
        optionsPanel.validProperty().addListener((obs, old, value) -> validate.run()); validate.run();
    }

    private static <T> void supported(ChoiceBox<T> box, java.util.Set<T> supported, T fallback) {
        T selected = box.getValue();
        box.getItems().setAll(supported.stream().sorted(Comparator.comparing(Object::toString)).toList());
        box.setValue(supported.contains(selected) ? selected : fallback);
    }

    private Printer restorePrinter(List<Printer> sorted, Printer defaultPrinter) {
        String saved = I18n.preference(LAST_PRINTER, null);
        if (saved != null) {
            Printer byName = sorted.stream().filter(p -> p.getName().equals(saved)).findFirst().orElse(null);
            if (byName != null) {
                return byName;
            }
        }
        return defaultPrinter;
    }

    private static <T extends Enum<T>> T readEnum(String key, Class<T> type, T fallback) {
        try {
            return Enum.valueOf(type, I18n.preference(key, fallback.name()));
        } catch (IllegalArgumentException invalid) {
            return fallback;
        }
    }

    private static int readInt(String key, int fallback, int min, int max) {
        String value = I18n.preference(key, null);
        if (value != null) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed >= min && parsed <= max) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static <T> void selectEnum(javafx.scene.control.ChoiceBox<T> box, T value) {
        box.getSelectionModel().select(value);
    }

    private void savePreferences() {
        Printer printer = printerBox.getSelectionModel().getSelectedItem();
        if (printer != null) {
            I18n.setPreference(LAST_PRINTER, printer.getName());
        }
        I18n.setPreference(LAST_COPIES, Integer.toString(copiesSpinner.getValue()));
        I18n.setPreference(LAST_ORIENTATION, orientationBox.getValue().name());
        I18n.setPreference(LAST_PAPER, paperBox.getValue().getName());
        I18n.setPreference(LAST_SIDES, sidesBox.getValue().name());
        I18n.setPreference(LAST_COLOR, colorBox.getValue().name());
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
