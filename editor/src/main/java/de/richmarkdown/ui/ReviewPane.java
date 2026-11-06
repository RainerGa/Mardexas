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

package de.richmarkdown.ui;

import de.richmarkdown.i18n.I18n;
import de.richmarkdown.markdown.Change;
import de.richmarkdown.markdown.ChangeStatus;
import de.richmarkdown.markdown.ChangeType;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.text.MessageFormat;
import java.util.List;
import java.util.function.Consumer;

/** Review list with status filtering, accessible actions and editable comments. */
public class ReviewPane extends VBox {
    private final ObservableList<Change> changes = FXCollections.observableArrayList();
    private final FilteredList<Change> filtered = new FilteredList<>(changes);
    private final ListView<Change> listView = new ListView<>(filtered);
    private final Label summary = new Label();
    private final ComboBox<String> filter = new ComboBox<>();
    private Consumer<Change> onAcceptChange;
    private Consumer<Change> onRejectChange;
    private Consumer<Change> onSelectChange;
    private Consumer<Change> onCommentChange;
    private Consumer<Change> onResetChange;
    private Runnable onAcceptAll;
    private Runnable onRejectAll;
    private Runnable onPrevious;
    private Runnable onNext;
    private final Button previous = ReviewIcons.button("previous", () -> { if (onPrevious != null) onPrevious.run(); });
    private final Button next = ReviewIcons.button("next", () -> { if (onNext != null) onNext.run(); });
    private final Button accept = ReviewIcons.button("accept", () -> acceptSelected());
    private final Button reject = ReviewIcons.button("reject", () -> rejectSelected());
    private final Button reset = ReviewIcons.button("reset", () -> resetSelected());
    private final Button acceptAll = ReviewIcons.button("acceptAll", () -> { if (onAcceptAll != null) onAcceptAll.run(); });
    private final Button rejectAll = ReviewIcons.button("rejectAll", () -> { if (onRejectAll != null) onRejectAll.run(); });

    public ReviewPane() {
        getStyleClass().add("rme-review-pane");
        setPadding(new Insets(8));
        setSpacing(8);
        setPrefWidth(360);
        setMinWidth(280);
        FlowPane toolbar = new FlowPane(4, 4, previous, next, accept, reject, reset, acceptAll, rejectAll);
        toolbar.getStyleClass().add("rme-review-toolbar");
        filter.getItems().add(I18n.text("trackChanges.filter.all"));
        for (ChangeStatus status : ChangeStatus.values()) filter.getItems().add(statusText(status));
        filter.getSelectionModel().selectFirst();
        filter.setTooltip(new Tooltip(I18n.text("trackChanges.filter.tooltip")));
        filter.getSelectionModel().selectedIndexProperty().addListener((obs, before, after) -> {
            int index = after.intValue();
            filtered.setPredicate(change -> index <= 0 || change.status() == ChangeStatus.values()[index - 1]);
            listView.setPlaceholder(new Label(I18n.text(index <= 0
                    ? "trackChanges.noChanges" : "trackChanges.filter.empty")));
            if (listView.getSelectionModel().isEmpty() && !filtered.isEmpty()) listView.getSelectionModel().selectFirst();
            updateActions();
        });
        listView.setCellFactory(view -> new ChangeListCell());
        listView.setPlaceholder(new Label(I18n.text("trackChanges.noChanges")));
        listView.getSelectionModel().selectedItemProperty().addListener((obs, before, selected) -> {
            updateActions();
            if (selected != null && onSelectChange != null) onSelectChange.accept(selected);
        });
        VBox.setVgrow(listView, Priority.ALWAYS);
        summary.setWrapText(true);
        getChildren().addAll(toolbar, summary, filter, listView);
        updateActions();
    }

    private void acceptSelected() {
        Change selected = getSelectedChange();
        if (selected != null && onAcceptChange != null) onAcceptChange.accept(selected);
    }

    private void rejectSelected() {
        Change selected = getSelectedChange();
        if (selected != null && onRejectChange != null) onRejectChange.accept(selected);
    }

    private void resetSelected() {
        Change selected = getSelectedChange();
        if (selected != null && onResetChange != null) onResetChange.accept(selected);
    }

    private void updateActions() {
        Change selected = getSelectedChange();
        int index = listView.getSelectionModel().getSelectedIndex();
        previous.setDisable(filtered.isEmpty() || index <= 0);
        next.setDisable(filtered.isEmpty() || index >= filtered.size() - 1);
        accept.setDisable(selected == null || selected.status() != ChangeStatus.PENDING);
        reject.setDisable(selected == null || selected.status() != ChangeStatus.PENDING);
        reset.setDisable(selected == null || selected.status() == ChangeStatus.PENDING);
        long pending = changes.stream().filter(change -> change.status() == ChangeStatus.PENDING).count();
        acceptAll.setDisable(pending == 0);
        rejectAll.setDisable(pending == 0);
        summary.setText(MessageFormat.format(I18n.text("trackChanges.summary"), changes.size(), pending,
                changes.stream().filter(change -> change.status() == ChangeStatus.ACCEPTED).count(),
                changes.stream().filter(change -> change.status() == ChangeStatus.REJECTED).count()));
    }

    public void setChanges(List<Change> newChanges) {
        Change selected = getSelectedChange();
        changes.setAll(newChanges == null ? List.of() : newChanges);
        if (selected != null) selectChange(selected, false);
        if (listView.getSelectionModel().isEmpty() && !filtered.isEmpty()) listView.getSelectionModel().selectFirst();
        updateActions();
    }

    public List<Change> getChanges() { return List.copyOf(changes); }
    public Change getSelectedChange() { return listView.getSelectionModel().getSelectedItem(); }

    public void updateChange(Change updated) {
        for (int i = 0; i < changes.size(); i++) {
            if (changes.get(i).id().equals(updated.id())) { changes.set(i, updated); break; }
        }
        updateActions();
    }

    public void removeChange(Change change) {
        changes.removeIf(item -> item.id().equals(change.id()));
        updateActions();
    }

    public void selectNext() { navigate(1); }
    public void selectPrevious() { navigate(-1); }
    private void navigate(int direction) {
        if (filtered.isEmpty()) return;
        int index = Math.max(0, Math.min(filtered.size() - 1,
                listView.getSelectionModel().getSelectedIndex() + direction));
        listView.getSelectionModel().select(index);
        listView.scrollTo(index);
    }

    public void selectChange(Change change) { selectChange(change, true); }

    private void selectChange(Change change, boolean reveal) {
        if (reveal && filtered.stream().noneMatch(item -> item.id().equals(change.id()))
                && changes.stream().anyMatch(item -> item.id().equals(change.id()))) {
            filter.getSelectionModel().selectFirst();
        }
        for (int i = 0; i < filtered.size(); i++) {
            if (filtered.get(i).id().equals(change.id())) {
                listView.getSelectionModel().select(i);
                listView.scrollTo(i);
                return;
            }
        }
    }

    public void setOnAcceptChange(Consumer<Change> handler) { onAcceptChange = handler; }
    public void setOnRejectChange(Consumer<Change> handler) { onRejectChange = handler; }
    public void setOnSelectChange(Consumer<Change> handler) { onSelectChange = handler; }
    public void setOnCommentChange(Consumer<Change> handler) { onCommentChange = handler; }
    public void setOnResetChange(Consumer<Change> handler) { onResetChange = handler; }
    public void setOnAcceptAll(Runnable handler) { onAcceptAll = handler; }
    public void setOnRejectAll(Runnable handler) { onRejectAll = handler; }
    public void setOnPrevious(Runnable handler) { onPrevious = handler; }
    public void setOnNext(Runnable handler) { onNext = handler; }

    static String statusText(ChangeStatus status) {
        return I18n.text("trackChanges.status." + status.name().toLowerCase(java.util.Locale.ROOT));
    }

    static String typeText(ChangeType type) {
        return I18n.text("trackChanges.type." + type.name().toLowerCase(java.util.Locale.ROOT));
    }

    private class ChangeListCell extends ListCell<Change> {
        @Override protected void updateItem(Change change, boolean empty) {
            super.updateItem(change, empty);
            setText(null);
            setGraphic(null);
            setTooltip(null);
            if (empty || change == null) return;
            Circle indicator = new Circle(5, Color.web(switch (change.type()) {
                case INSERTED -> "#27ae60";
                case DELETED -> "#e74c3c";
                case FORMAT_CHANGED -> "#f39c12";
            }));
            String text = change.text().replace("\ufeff", I18n.text("trackChanges.image"));
            Label preview = new Label(text.length() > 80 ? text.substring(0, 77) + "…" : text);
            preview.setWrapText(true);
            preview.setMaxWidth(220);
            preview.setMinWidth(0);
            Label meta = new Label(typeText(change.type()) + " · " + statusText(change.status()));
            meta.getStyleClass().add("rme-change-meta");
            VBox description = new VBox(3, preview, meta);
            HBox.setHgrow(description, Priority.ALWAYS);
            description.setMinWidth(0);
            if (change.comment() != null) {
                Label comment = new Label(I18n.text("trackChanges.comment.label") + " " + change.comment());
                comment.setWrapText(true);
                comment.getStyleClass().add("rme-change-meta");
                description.getChildren().add(comment);
            }
            Button yes = ReviewIcons.button("accept", () -> { if (onAcceptChange != null) onAcceptChange.accept(getItem()); });
            Button no = ReviewIcons.button("reject", () -> { if (onRejectChange != null) onRejectChange.accept(getItem()); });
            Button resetButton = ReviewIcons.button("reset", () -> { if (onResetChange != null) onResetChange.accept(getItem()); });
            yes.setDisable(change.status() != ChangeStatus.PENDING);
            no.setDisable(change.status() != ChangeStatus.PENDING);
            resetButton.setDisable(change.status() == ChangeStatus.PENDING);
            FlowPane actions = new FlowPane(2, 2, yes, no, resetButton, ReviewIcons.button("comment", this::showCommentDialog));
            actions.setPrefWrapLength(66);
            actions.setMinWidth(66);
            actions.setMaxWidth(66);
            HBox row = new HBox(6, indicator, description, actions);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6));
            setTooltip(new Tooltip(typeText(change.type()) + ": " + text));
            setGraphic(row);
        }

        private void showCommentDialog() {
            Change change = getItem();
            if (change == null) return;
            TextInputDialog dialog = new TextInputDialog(change.comment() == null ? "" : change.comment());
            if (getScene() != null) dialog.initOwner(getScene().getWindow());
            dialog.setTitle(I18n.text("trackChanges.comment.title"));
            dialog.setHeaderText(I18n.text("trackChanges.comment.header"));
            dialog.setContentText(I18n.text("trackChanges.comment.content"));
            dialog.getDialogPane().getButtonTypes().forEach(type -> {
                if (dialog.getDialogPane().lookupButton(type) instanceof Button button) {
                    button.setTooltip(new Tooltip(type.getText()));
                }
            });
            dialog.showAndWait().ifPresent(comment -> {
                Change updated = change.withComment(comment.isBlank() ? null : comment);
                if (onCommentChange != null) onCommentChange.accept(updated);
                else updateChange(updated);
            });
        }
    }
}
