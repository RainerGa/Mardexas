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

package de.richmarkdown.features.printing;

import de.richmarkdown.i18n.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.Label;
import de.richmarkdown.ui.dialogs.DialogButtons;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

/** Print-only preferences and scope; no document metadata is added. */
public final class PrintOptionsDialog extends Dialog<PrintOptions> {

    private static final String P_SCOPE = "printOptions.scope";
    private static final String P_RANGES = "printOptions.pageRanges";
    private static final String P_TOP = "printOptions.top";
    private static final String P_RIGHT = "printOptions.right";
    private static final String P_BOTTOM = "printOptions.bottom";
    private static final String P_LEFT = "printOptions.left";
    private static final String P_FONT = "printOptions.font";
    private static final String P_SIZE = "printOptions.fontSize";
    private static final String P_HEADER = "printOptions.header";
    private static final String P_FOOTER = "printOptions.footer";
    private static final String P_NUMBERS = "printOptions.pageNumbers";

    public PrintOptionsDialog(Window owner, PrintOptions initial, boolean hasSelection) {
        initOwner(owner);
        setTitle(I18n.text("dialog.printOptions.title"));

        PrintOptions restored = restore(initial);

        ComboBox<PrintOptions.Scope> scope = new ComboBox<>();
        scope.getItems().setAll(PrintOptions.Scope.values());
        scope.setValue(restored.scope());
        scope.setCellFactory(view -> scopeCell());
        scope.setButtonCell(scopeCell());
        scope.setTooltip(new Tooltip(I18n.text("dialog.printOptions.tooltip.scope")));

        TextField ranges = field(restored.pageRanges(), I18n.text("dialog.printOptions.tooltip.pages"));
        TextField top = field(Double.toString(restored.top()), I18n.text("dialog.printOptions.tooltip.topMargin"));
        TextField right = field(Double.toString(restored.right()), I18n.text("dialog.printOptions.tooltip.rightMargin"));
        TextField bottom = field(Double.toString(restored.bottom()), I18n.text("dialog.printOptions.tooltip.bottomMargin"));
        TextField left = field(Double.toString(restored.left()), I18n.text("dialog.printOptions.tooltip.leftMargin"));
        TextField font = field(restored.fontFamily(), I18n.text("dialog.printOptions.tooltip.font"));
        TextField size = field(Double.toString(restored.fontSize()), I18n.text("dialog.printOptions.tooltip.fontSize"));
        TextField header = field(restored.header(), I18n.text("dialog.printOptions.tooltip.header"));
        TextField footer = field(restored.footer(), I18n.text("dialog.printOptions.tooltip.footer"));
        CheckBox numbers = new CheckBox(I18n.text("dialog.printOptions.label.pageNumbers"));
        numbers.setSelected(restored.pageNumbers());
        numbers.setTooltip(new Tooltip(I18n.text("dialog.printOptions.tooltip.pageNumbers")));

        GridPane grid = grid();
        int row = 0;
        gridAdd(grid, new Label(I18n.text("dialog.printOptions.label.scope")), scope, row++);
        gridAdd(grid, new Label(I18n.text("dialog.printOptions.label.pages")), ranges, row++);
        gridAdd(grid, new Label(I18n.text("dialog.printOptions.label.topMargin")), top, row++);
        gridAdd(grid, new Label(I18n.text("dialog.printOptions.label.rightMargin")), right, row++);
        gridAdd(grid, new Label(I18n.text("dialog.printOptions.label.bottomMargin")), bottom, row++);
        gridAdd(grid, new Label(I18n.text("dialog.printOptions.label.leftMargin")), left, row++);
        gridAdd(grid, new Label(I18n.text("dialog.printOptions.label.font")), font, row++);
        gridAdd(grid, new Label(I18n.text("dialog.printOptions.label.fontSize")), size, row++);
        gridAdd(grid, new Label(I18n.text("dialog.printOptions.label.header")), header, row++);
        gridAdd(grid, new Label(I18n.text("dialog.printOptions.label.footer")), footer, row++);
        gridAdd(grid, new Label(""), numbers, row++);

        Label error = new Label();
        error.getStyleClass().add("rme-dialog-error");
        error.setWrapText(true);
        error.setMaxWidth(420);
        grid.add(error, 0, row, 2, 1);
        getDialogPane().setContent(grid);

        DialogButtons.applyDialogStyles(getDialogPane());
        getDialogPane().getButtonTypes().setAll(DialogButtons.OK, DialogButtons.CANCEL);
        Button ok = (Button) getDialogPane().lookupButton(DialogButtons.OK);
        ok.setDefaultButton(true);
        ok.setTooltip(new Tooltip(I18n.text("dialog.printOptions.button.ok")));
        ((Button) getDialogPane().lookupButton(DialogButtons.CANCEL))
                .setTooltip(new Tooltip(I18n.text("dialog.printOptions.button.cancel")));

        Runnable validate = () -> {
            try {
                if (scope.getValue() == PrintOptions.Scope.SELECTION && !hasSelection) {
                    throw new IllegalArgumentException(I18n.text("dialog.printOptions.error.selectText"));
                }
                options(top, right, bottom, left, font, size, header, footer, numbers, scope, ranges);
                error.setText("");
                ok.setDisable(false);
            } catch (IllegalArgumentException failure) {
                error.setText(failure.getMessage());
                ok.setDisable(true);
            }
        };
        for (TextField input : java.util.List.of(ranges, top, right, bottom, left, font, size, header, footer)) {
            input.textProperty().addListener((obs, old, value) -> validate.run());
        }
        scope.valueProperty().addListener((obs, old, value) -> validate.run());
        validate.run();
        setHeaderText(I18n.text("dialog.printOptions.title"));
        setResizable(true);
        setOnShown(event -> scope.requestFocus());
        setResultConverter(button -> {
            if (button != DialogButtons.OK) return null;
            PrintOptions result = options(top, right, bottom, left, font, size, header, footer, numbers, scope, ranges);
            savePreferences(scope, ranges, top, right, bottom, left, font, size, header, footer, numbers);
            return result;
        });
    }

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        return grid;
    }

    private static void gridAdd(GridPane grid, Label label, javafx.scene.Node field, int row) {
        grid.addRow(row, label, field);
    }

    private PrintOptions restore(PrintOptions initial) {
        PrintOptions.Scope scope = enumPreference(P_SCOPE, PrintOptions.Scope.class, initial.scope());
        String ranges = I18n.preference(P_RANGES, initial.pageRanges());
        double top = doublePreference(P_TOP, initial.top());
        double right = doublePreference(P_RIGHT, initial.right());
        double bottom = doublePreference(P_BOTTOM, initial.bottom());
        double left = doublePreference(P_LEFT, initial.left());
        String font = I18n.preference(P_FONT, initial.fontFamily());
        double size = doublePreference(P_SIZE, initial.fontSize());
        String header = I18n.preference(P_HEADER, initial.header());
        String footer = I18n.preference(P_FOOTER, initial.footer());
        boolean numbers = I18n.preference(P_NUMBERS, null) != null
                ? Boolean.parseBoolean(I18n.preference(P_NUMBERS, "" + initial.pageNumbers()))
                : initial.pageNumbers();
        try {
            return new PrintOptions(top, right, bottom, left, font, size, header, footer, numbers, scope, ranges);
        } catch (IllegalArgumentException ignored) {
            return initial;
        }
    }

    private static <T extends Enum<T>> T enumPreference(String key, Class<T> type, T fallback) {
        String value = I18n.preference(key, null);
        if (value != null) {
            try {
                return Enum.valueOf(type, value);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return fallback;
    }

    private static double doublePreference(String key, double fallback) {
        String value = I18n.preference(key, null);
        if (value != null) {
            try {
                return Double.parseDouble(value.strip().replace(',', '.'));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static PrintOptions options(TextField top, TextField right, TextField bottom, TextField left,
                                        TextField font, TextField size, TextField header, TextField footer,
                                        CheckBox numbers, ComboBox<PrintOptions.Scope> scope, TextField ranges) {
        try {
            return new PrintOptions(number(top), number(right), number(bottom), number(left), font.getText(),
                    number(size), header.getText(), footer.getText(), numbers.isSelected(), scope.getValue(),
                    ranges.getText());
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(I18n.text("dialog.printOptions.error.invalidNumbers"));
        }
    }

    private static double number(TextField field) {
        return Double.parseDouble(field.getText().strip().replace(',', '.'));
    }

    private static TextField field(String text, String tooltip) {
        TextField field = new TextField(text);
        field.setTooltip(new Tooltip(tooltip));
        return field;
    }

    private static ListCell<PrintOptions.Scope> scopeCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(PrintOptions.Scope item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : switch (item) {
                    case DOCUMENT -> I18n.text("dialog.printOptions.scope.document");
                    case SELECTION -> I18n.text("dialog.printOptions.scope.selection");
                    case CHAPTER -> I18n.text("dialog.printOptions.scope.chapter");
                });
            }
        };
    }

    private void savePreferences(ComboBox<PrintOptions.Scope> scope, TextField ranges, TextField top,
                                 TextField right, TextField bottom, TextField left, TextField font,
                                 TextField size, TextField header, TextField footer, CheckBox numbers) {
        I18n.setPreference(P_SCOPE, scope.getValue().name());
        I18n.setPreference(P_RANGES, ranges.getText());
        I18n.setPreference(P_TOP, top.getText());
        I18n.setPreference(P_RIGHT, right.getText());
        I18n.setPreference(P_BOTTOM, bottom.getText());
        I18n.setPreference(P_LEFT, left.getText());
        I18n.setPreference(P_FONT, font.getText());
        I18n.setPreference(P_SIZE, size.getText());
        I18n.setPreference(P_HEADER, header.getText());
        I18n.setPreference(P_FOOTER, footer.getText());
        I18n.setPreference(P_NUMBERS, Boolean.toString(numbers.isSelected()));

    }

}
