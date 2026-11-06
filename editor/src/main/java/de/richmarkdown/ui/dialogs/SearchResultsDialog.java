package de.richmarkdown.ui.dialogs;

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
import java.util.List;
import static de.richmarkdown.ui.FeatureUi.text;

/** Virtualized result selection; stores only bounded text excerpts in cells. */
public final class SearchResultsDialog extends Dialog<List<FindReplaceEngine.ReplacementPreview>> {
    private record Row(FindReplaceEngine.ReplacementPreview preview, SimpleBooleanProperty selected) { }
    public SearchResultsDialog(Window owner, List<FindReplaceEngine.ReplacementPreview> previews,
                               boolean replacements, java.util.function.Consumer<FindReplaceEngine.Match> reveal) {
        initOwner(owner);
        setTitle(replacements ? text("Ersetzungsvorschau", "Replacement preview") : text("Suchtreffer", "Search results"));
        setHeaderText(text("Treffer auswählen; Doppelklick springt zur Textstelle.", "Select matches; double-click to jump to the text."));
        setResizable(true);
        List<Row> rows = previews.stream().map(preview -> new Row(preview, new SimpleBooleanProperty(replacements))).toList();
        ListView<Row> list = new ListView<>();
        list.getItems().setAll(rows);
        list.setId("search-preview-list");
        list.setCellFactory(ignored -> new ListCell<>() {
            private final CheckBox check = new CheckBox();
            private Row bound;
            @Override protected void updateItem(Row row, boolean empty) {
                super.updateItem(row, empty);
                if (bound != null) check.selectedProperty().unbindBidirectional(bound.selected());
                bound = row;
                if (empty || row == null) { setGraphic(null); return; }
                String before = excerpt(row.preview().text());
                String after = excerpt(row.preview().replacement());
                check.setText(row.preview().match().getStart() + ": " + before + (replacements ? " → " + after : ""));
                check.setTooltip(new Tooltip(text("Diesen Treffer auswählen", "Select this match")));
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
        Button all = new Button(text("Alle auswählen", "Select all"));
        all.setTooltip(new Tooltip(text("Alle Treffer zur Ersetzung auswählen", "Select all matches for replacement")));
        all.setOnAction(event -> rows.forEach(row -> row.selected().set(true)));
        Button none = new Button(text("Keine auswählen", "Select none"));
        none.setTooltip(new Tooltip(text("Alle Treffer von der Ersetzung ausschließen", "Exclude all matches from replacement")));
        none.setOnAction(event -> rows.forEach(row -> row.selected().set(false)));
        VBox body = new VBox(8, new Label(previews.size() + text(" Treffer", " matches")), list);
        if (replacements) body.getChildren().add(new javafx.scene.layout.HBox(8, all, none));
        body.setPrefSize(750, 480);
        VBox.setVgrow(list, Priority.ALWAYS);
        getDialogPane().setContent(body);
        getDialogPane().getButtonTypes().addAll(replacements ? ButtonType.OK : ButtonType.CLOSE, ButtonType.CANCEL);
        for (ButtonType type : getDialogPane().getButtonTypes()) {
            ((Button) getDialogPane().lookupButton(type)).setTooltip(new Tooltip(type == ButtonType.OK
                    ? text("Nur ausgewählte Treffer ersetzen", "Replace selected matches only") : text("Dialog schließen", "Close dialog")));
        }
        setResultConverter(button -> button == ButtonType.OK
                ? rows.stream().filter(row -> row.selected().get()).map(Row::preview).toList() : null);
    }
    private static String excerpt(String text) {
        return text.substring(0, Math.min(text.length(), 160)).replace('\n', '↵').replace('\r', ' ')
                + (text.length() > 160 ? "…" : "");
    }
}
