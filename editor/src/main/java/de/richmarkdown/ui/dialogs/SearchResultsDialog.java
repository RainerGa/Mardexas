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
import de.richmarkdown.findreplace.FindReplaceEngine;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import java.text.MessageFormat;
import java.util.List;

/** Virtualized result selection; stores only bounded text excerpts in cells. */
public final class SearchResultsDialog extends Dialog<List<FindReplaceEngine.ReplacementPreview>> {
    private record Row(FindReplaceEngine.ReplacementPreview preview, SimpleBooleanProperty selected) { }

    public SearchResultsDialog(Window owner, List<FindReplaceEngine.ReplacementPreview> previews,
                               boolean replacements, java.util.function.Consumer<FindReplaceEngine.Match> reveal) {
        this(owner, previews, replacements, reveal, null);
    }
    public SearchResultsDialog(Window owner, List<FindReplaceEngine.ReplacementPreview> previews,
                               boolean replacements, java.util.function.Consumer<FindReplaceEngine.Match> reveal, String source) {
        initOwner(owner);
        setTitle(replacements ? I18n.text("dialog.searchResults.title.replacements")
                : I18n.text("dialog.searchResults.title.matches"));
        setHeaderText(I18n.text("dialog.searchResults.header"));
        setResizable(true);
        List<Row> rows = previews.stream().map(preview -> new Row(preview, new SimpleBooleanProperty(replacements))).toList();
        int[] lineStarts = source == null ? new int[]{0} : java.util.stream.IntStream.concat(java.util.stream.IntStream.of(0),
                java.util.stream.IntStream.range(0, source.length()).filter(i -> source.charAt(i) == '\n').map(i -> i + 1)).toArray();
        ListView<Row> list = new ListView<>();
        list.getItems().setAll(rows);
        list.setId("search-preview-list");
        list.setCellFactory(ignored -> new ListCell<>() {
            private final CheckBox check = new CheckBox();
            private Row bound;
            @Override
            protected void updateItem(Row row, boolean empty) {
                super.updateItem(row, empty);
                if (bound != null) {
                    check.selectedProperty().unbindBidirectional(bound.selected());
                }
                bound = row;
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                String before = excerpt(row.preview().text());
                String after = excerpt(row.preview().replacement());
                int offset = row.preview().match().getStart();
                String location = Integer.toString(offset);
                if (source != null && offset <= source.length()) {
                    int found = java.util.Arrays.binarySearch(lineStarts, offset);
                    int line = found >= 0 ? found : -found - 2;
                    int column = offset - lineStarts[line] + 1;
                    location = WorkflowDialogs.text("line") + " " + (line + 1) + ", " + WorkflowDialogs.text("column") + " " + column;
                }
                check.setText(location + ": " + before + (replacements ? " → " + after : ""));
                check.setTooltip(new Tooltip(I18n.text("dialog.searchResults.tooltip.select")));
                check.selectedProperty().bindBidirectional(row.selected());
                check.setMouseTransparent(!replacements);
                setGraphic(check);
            }
        });
        list.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null) {
                reveal.accept(list.getSelectionModel().getSelectedItem().preview().match());
            }
        });
        javafx.scene.control.TextArea context = WorkflowDialogs.output(""); context.setPrefRowCount(4);
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> {
            if (row == null) { context.clear(); return; }
            int start = row.preview().match().getStart(), end = row.preview().match().getEnd();
            if (source != null) {
                int from = Math.max(0, start - 180), to = Math.min(source.length(), end + 180);
                context.setText(source.substring(from, to)); context.selectRange(start - from, end - from);
            } else context.setText(row.preview().text());
        });
        list.setOnKeyPressed(event -> {
            Row row = list.getSelectionModel().getSelectedItem(); if (row == null) return;
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) { reveal.accept(row.preview().match()); event.consume(); }
            if (event.getCode() == javafx.scene.input.KeyCode.SPACE && replacements) { row.selected().set(!row.selected().get()); event.consume(); }
        });
        var totalSelected = new javafx.beans.property.SimpleIntegerProperty(replacements ? rows.size() : 0);
        Label selectedCount = new Label(); selectedCount.textProperty().bind(totalSelected.asString(WorkflowDialogs.text("selected") + ": %d / " + rows.size()));
        rows.forEach(row -> row.selected().addListener((obs, old, value) -> totalSelected.set(totalSelected.get() + (value ? 1 : -1))));
        Button all = new Button(I18n.text("dialog.searchResults.button.selectAll"));
        all.setTooltip(new Tooltip(I18n.text("dialog.searchResults.tooltip.selectAll")));
        all.setOnAction(event -> rows.forEach(row -> row.selected().set(true)));
        Button none = new Button(I18n.text("dialog.searchResults.button.selectNone"));
        none.setTooltip(new Tooltip(I18n.text("dialog.searchResults.tooltip.selectNone")));
        none.setOnAction(event -> rows.forEach(row -> row.selected().set(false)));
        VBox body = new VBox(8, new Label(MessageFormat.format(I18n.text("dialog.searchResults.count"), previews.size())), selectedCount, list, context);
        if (replacements) {
            body.getChildren().add(new javafx.scene.layout.HBox(8, all, none));
        }
        body.setPrefSize(750, 480);
        VBox.setVgrow(list, Priority.ALWAYS);
        getDialogPane().setContent(body);

        DialogButtons.applyDialogStyles(getDialogPane());
        if (replacements) {
            getDialogPane().getButtonTypes().addAll(DialogButtons.OK, DialogButtons.CANCEL);
            Button ok = (Button) getDialogPane().lookupButton(DialogButtons.OK);
            ok.setDefaultButton(true); ok.disableProperty().bind(totalSelected.isEqualTo(0));
            ok.setTooltip(new Tooltip(I18n.text("dialog.searchResults.button.ok")));
        } else {
            getDialogPane().getButtonTypes().addAll(DialogButtons.CLOSE, DialogButtons.CANCEL);
        }
        for (ButtonType type : getDialogPane().getButtonTypes()) {
            ((Button) getDialogPane().lookupButton(type)).setTooltip(new Tooltip(
                    type == DialogButtons.OK ? I18n.text("dialog.searchResults.button.ok")
                            : I18n.text("dialog.searchResults.button.close")));
        }
        setResultConverter(button -> button == DialogButtons.OK
                ? rows.stream().filter(row -> row.selected().get()).map(Row::preview).toList() : null);

        setOnShown(event -> list.requestFocus());
    }

    private static String excerpt(String text) {
        return text.substring(0, Math.min(text.length(), 160)).replace('\n', '↵').replace('\r', ' ')
                + (text.length() > 160 ? "…" : "");
    }
}
