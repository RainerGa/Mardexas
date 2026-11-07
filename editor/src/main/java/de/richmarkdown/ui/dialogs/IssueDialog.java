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
import de.richmarkdown.markdown.MarkdownIssue;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Dialog zur Anzeige von Formatierungsverstößen (AddFeatures #16), nach dem
 * Vorbild des Statistik-Dialogs.
 *
 * <p>Der Dialog zeigt die vom {@linkplain de.richmarkdown.markdown.MarkdownChecker}
 * ermittelten Issues in einer Liste an, farblich nach Schweregrad
 * (ERROR=rot, WARNING=orange, HINT=grau). Ein Klick auf einen Eintrag springt
 * über den übergebenen Handler zur Issue-Position (in der Regel
 * {@link de.richmarkdown.ui.EditorWindow#jumpTo(int, String,
 * de.richmarkdown.ui.NavigationHistory.JumpType)} mit
 * {@code JumpType.ISSUE}).
 */
public class IssueDialog extends Dialog<ButtonType> {

    private final Supplier<List<MarkdownIssue>> issuesSupplier;
    private final ListView<MarkdownIssue> listView = new ListView<>();
    private final Button checkButton = new Button(I18n.text("dialog.issues.check"));

    /**
     * Erstellt den lokalisierten Issues-Dialog mit aktuellen Prüf-Ergebnissen.
     *
     * @param issuesSupplier liefert die aktuelle Issue-Liste; wird beim Öffnen
     *                       und bei "Alle prüfen" abgefragt
     * @param onJump         wird mit der Issue-Position aufgerufen, wenn der
     *                       Anwender einen Eintrag anklickt
     */
    public IssueDialog(Supplier<List<MarkdownIssue>> issuesSupplier, IntConsumer onJump) {
        this.issuesSupplier = issuesSupplier;
        setTitle(I18n.text("dialog.issues.title"));
        setHeaderText(I18n.text("dialog.issues.header"));

        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, nv) -> {
            if (nv != null && onJump != null) {
                onJump.accept(nv.position());
            }
        });

        checkButton.getStyleClass().add("rme-issues-check");
        checkButton.setDefaultButton(false);
        checkButton.setTooltip(new javafx.scene.control.Tooltip("Das aktuelle Dokument erneut auf Markdown-Probleme prüfen."));
        checkButton.setOnAction(event -> refresh());

        HBox buttonRow = new HBox(checkButton);
        buttonRow.setPadding(new Insets(8, 0, 0, 0));

        VBox content = new VBox(12, listView, buttonRow);
        content.setPadding(new Insets(4));
        VBox.setVgrow(listView, Priority.ALWAYS);
        getDialogPane().setContent(content);
        getDialogPane().setPrefSize(520, 400);
        getDialogPane().getButtonTypes().add(DialogButtons.CLOSE);

        refresh();
    }

    /** Optional editor actions; ignored rules remain outside the Markdown document. */
    public void configureActions(java.util.function.Consumer<MarkdownIssue> fix,
                                 java.util.function.Consumer<MarkdownIssue> ignore) {
        HBox row = (HBox) ((VBox) getDialogPane().getContent()).getChildren().get(1);
        if (fix != null) {
            Button button = new Button("Korrekturvorschlag übernehmen");
            button.setTooltip(new javafx.scene.control.Tooltip("Einen verfügbaren Korrekturvorschlag als rückgängig machbare Textänderung übernehmen."));
            button.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(() -> { MarkdownIssue selected = listView.getSelectionModel().getSelectedItem(); return selected == null || !selected.message().equals("Ungeschlossener Code-Fence"); }, listView.getSelectionModel().selectedItemProperty()));
            button.setOnAction(event -> { MarkdownIssue selected = listView.getSelectionModel().getSelectedItem(); if (selected != null) { fix.accept(selected); refresh(); } }); row.getChildren().add(button);
        }
        if (ignore != null) {
            Button button = new Button("Prüfung ignorieren"); button.setTooltip(new javafx.scene.control.Tooltip("Gleiche Hinweise für diesen Prüfdialog ausblenden; das Dokument wird nicht verändert."));
            button.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull()); button.setOnAction(event -> { MarkdownIssue selected = listView.getSelectionModel().getSelectedItem(); if (selected != null) { ignore.accept(selected); refresh(); } }); row.getChildren().add(button);
        }
    }

    /**
     * Liest die Issues neu ein und aktualisiert die Liste.
     *
     * <p>Entspricht der "Alle prüfen"-Schaltfläche.
     */
    public void refresh() {
        listView.setItems(FXCollections.observableArrayList(issuesSupplier.get()));
    }

    /**
     * Liefert die Issue-Liste (für UI-Tests).
     *
     * @return aktuell angezeigte Issue-Liste
     */
    public List<MarkdownIssue> getIssues() {
        return List.copyOf(listView.getItems());
    }

    /**
     * Liefert die Issue-Liste (für UI-Tests und Selektions-Zugriff).
     *
     * @return JavaFX-Liste der aktuellen Issues
     */
    public ListView<MarkdownIssue> getListView() {
        return listView;
    }

    /**
     * Liefert die "Alle prüfen"-Schaltfläche (für UI-Tests).
     *
     * @return Schaltfläche „Alle prüfen"
     */
    public Button getCheckButton() {
        return checkButton;
    }

    /** Zelle farblich nach Schweregrad. */
    static final class IssueCell extends ListCell<MarkdownIssue> {

        private final Label label = new Label();

        IssueCell() {
            label.setMaxWidth(Double.MAX_VALUE);
            label.setWrapText(true);
        }

        @Override
        protected void updateItem(MarkdownIssue issue, boolean empty) {
            super.updateItem(issue, empty);
            if (empty || issue == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            String severityTag = switch (issue.severity()) {
                case ERROR -> "[E] ";
                case WARNING -> "[W] ";
                case HINT -> "[H] ";
            };
            label.setText(severityTag + issue.message() + " (Zeile " + issue.line()
                    + ", Spalte " + issue.column() + ")");
            Color color = switch (issue.severity()) {
                case ERROR -> Color.web("#c0392b");
                case WARNING -> Color.web("#d68910");
                case HINT -> Color.web("#7f8c8d");
            };
            label.setTextFill(color);
            setGraphic(label);
            setPadding(new Insets(2, 4, 2, 4));
        }
    }
}
