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

import static de.richmarkdown.ui.FeatureUi.text;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.Selection;
import com.gluonhq.richtextarea.model.Document;
import de.richmarkdown.editor.DocumentHeading;
import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.i18n.I18n;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import de.richmarkdown.features.editing.ChapterModel;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Dokumentstruktur-Anzeige (Word-Navigationsbereich / LibreOffice-Navigator):
 * listet alle Überschriften hierarchisch auf und navigiert per Klick
 * (PROJEKTPLAN 7.1, Nachtrag).
 *
 * <p>Die Anzeige aktualisiert sich bei Dokumentänderungen (Laden) und bei
 * Texteingaben (neue/entfernte Überschriften). Der aktuell am Caret stehende
 * Eintrag wird markiert. Die Navigation setzt den Caret über
 * {@link MarkdownEditor#goToPosition(int)}; Änderungen an der Auswahl wirken
 * nicht auf den Dokumentinhalt.
 */
public class DocumentStructureView extends VBox {

    private static final int INDENT_PER_LEVEL = 12;

    private final MarkdownEditor editor;
    private final ListView<DocumentHeading> listView = new ListView<>();
    private final Label headerLabel = new Label(I18n.text("structure.title"));
    private final TextField searchField = new TextField();
    private final ComboBox<Integer> maximumLevel = new ComboBox<>();
    private final Label chapterStatistics = new Label();
    private java.util.List<DocumentHeading> allHeadings = java.util.List.of();
    private java.util.List<ChapterModel.Chapter> chapters = java.util.List.of();
    private final de.richmarkdown.features.editing.ChapterFolding folding;

    /** Guard gegen Selektions-/Navigations-Rückkopplung. */
    private boolean updatingSelection;

    /** Eigener Sprungpfad (z. B. für die Zurück-/Vorwärts-Navigation). */
    private java.util.function.IntConsumer jumpHandler;

    /**
     * Erstellt eine mit dem Editor verbundene Dokumentstrukturansicht.
     *
     * @param editor Editor-Fassade als Quelle der Überschriften, Caretposition
     *               und Navigationsaktionen
     */
    public DocumentStructureView(MarkdownEditor editor) {
        this.editor = editor;
        this.folding = new de.richmarkdown.features.editing.ChapterFolding(editor);
        getStyleClass().add("rme-structure-view");

        listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        listView.setCellFactory(lv -> new HeadingCell());
        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, nv) -> {
            updateStatistics(nv);
            if (nv != null && !updatingSelection) {
                updatingSelection = true;
                try {
                    performJump(nv.position());
                } finally {
                    updatingSelection = false;
                }
            }
        });

        // Caret-Verfolgung: der aktuelle Eintrag wird markiert
        editor.getControl().caretPositionProperty().addListener((obs, old, nv) -> selectEntryAtCaret());

        // Aktualisierung bei Dokument- und Textänderungen
        editor.getControl().documentProperty().addListener((obs, old, nv) -> refresh());
        editor.getControl().textLengthProperty().addListener((obs, old, nv) -> refresh());
        // Dekorationsänderungen (Überschriften setzen/entfernen) meldet die Fassade
        editor.addChangeListener(this::refresh);

        headerLabel.getStyleClass().add("structure-header");
        headerLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        headerLabel.setMaxWidth(Double.MAX_VALUE);

        searchField.setPromptText(text("Überschriften suchen", "Search headings"));
        searchField.setTooltip(new Tooltip(text("Dokumentstruktur nach Überschriftentext filtern", "Filter the outline by heading text")));
        searchField.textProperty().addListener((obs, old, value) -> applyFilter());
        maximumLevel.getItems().setAll(1, 2, 3, 4, 5, 6);
        maximumLevel.setValue(6);
        maximumLevel.setTooltip(new Tooltip(text("Überschriften bis zu dieser Ebene anzeigen", "Show headings up to this level")));
        maximumLevel.valueProperty().addListener((obs, old, value) -> applyFilter());
        maximumLevel.setMaxWidth(Double.MAX_VALUE);
        chapterStatistics.setWrapText(true);
        var context = new ContextMenu();
        MenuItem up = new MenuItem(text("Kapitel nach oben verschieben", "Move chapter up"));
        MenuItem down = new MenuItem(text("Kapitel nach unten verschieben", "Move chapter down"));
        up.setId("structure.moveUp"); up.setGraphic(CommandIcons.create(up.getId(), 18));
        down.setId("structure.moveDown"); down.setGraphic(CommandIcons.create(down.getId(), 18));
        up.setOnAction(event -> moveSelectedChapter(-1));
        down.setOnAction(event -> moveSelectedChapter(1));
        context.getItems().addAll(up, down);
        MenuItem fold = new MenuItem(text("Kapitel einklappen", "Collapse chapter"));
        MenuItem expand = new MenuItem(text("Kapitel ausklappen", "Expand chapters"));
        fold.setId("structure.fold"); fold.setGraphic(CommandIcons.create(fold.getId(), 18));
        expand.setId("structure.unfold"); expand.setGraphic(CommandIcons.create(expand.getId(), 18));
        fold.setOnAction(event -> foldSelectedChapter());
        expand.setOnAction(event -> folding.expandAll());
        context.getItems().addAll(fold, expand);
        context.setOnShowing(event -> {
            int index = allHeadings.indexOf(listView.getSelectionModel().getSelectedItem());
            ChapterModel.Chapter selected = index >= 0 && index < chapters.size() ? chapters.get(index) : null;
            boolean editable = editor.getControl().isEditable();
            up.setDisable(!editable || selected == null || !ChapterModel.canMoveSibling(chapters, selected.start(), -1));
            down.setDisable(!editable || selected == null || !ChapterModel.canMoveSibling(chapters, selected.start(), 1));
            fold.setDisable(selected == null || selected.bodyStart() >= selected.end());
            expand.setDisable(!folding.isFolded());
        });
        listView.setContextMenu(context);

        VBox.setVgrow(listView, Priority.ALWAYS);
        getChildren().addAll(headerLabel, searchField, maximumLevel, listView, chapterStatistics);
        setPrefWidth(220);
        setMinWidth(180);

        refresh();
    }

    /**
     * Liest die Überschriften neu ein und aktualisiert die aktuelle Markierung.
     *
     * <p>Die Methode ist öffentlich, damit Änderungen, die nicht über die
     * Editor-Fassade signalisiert werden, explizit synchronisiert werden können.
     */
    public void refresh() {
        allHeadings = editor.getHeadings();
        try { chapters = ChapterModel.chapters(editor.getDocumentSnapshot()); }
        catch (IllegalStateException notReady) { chapters = java.util.List.of(); }
        applyFilter();
    }

    private void applyFilter() {
        String query = searchField.getText().toLowerCase(java.util.Locale.ROOT);
        int level = maximumLevel.getValue() == null ? 6 : maximumLevel.getValue();
        updatingSelection = true;
        try {
            listView.getItems().setAll(allHeadings.stream().filter(h -> h.level() <= level
                    && h.text().toLowerCase(java.util.Locale.ROOT).contains(query)).toList());
        } finally {
            updatingSelection = false;
        }
        selectEntryAtCaret();
    }

    public TextField getSearchField() { return searchField; }
    public ComboBox<Integer> getMaximumLevel() { return maximumLevel; }
    public String getChapterStatistics() { return chapterStatistics.getText(); }
    public void foldSelectedChapter() {
        int index = allHeadings.indexOf(listView.getSelectionModel().getSelectedItem());
        if (index >= 0 && index < chapters.size()) folding.fold(chapters.get(index));
    }
    public void expandChapters() { folding.expandAll(); }

    private void updateStatistics(DocumentHeading heading) {
        if (heading == null) { chapterStatistics.setText(""); return; }
        int index = allHeadings.indexOf(heading);
        chapterStatistics.setText(index >= 0 && index < chapters.size()
                ? chapters.get(index).words() + text(" Wörter im Kapitel einschließlich Unterkapitel", " words in chapter including subchapters") : "");
    }

    /** Moves selected section with all descendants, preserving decorations and Undo. */
    public void moveSelectedChapter(int direction) {
        var heading = listView.getSelectionModel().getSelectedItem();
        int index = allHeadings.indexOf(heading);
        if (index < 0 || index >= chapters.size() || direction == 0 || !editor.getControl().isEditable()) return;
        var source = editor.getDocumentSnapshot();
        var selected = chapters.get(index);
        var result = ChapterModel.moveSibling(source, selected.start(), direction);
        if (!source.equals(result)) {
            int destination = result.getCaretPosition();
            int caret = editor.getControl().getCaretPosition();
            if (caret >= selected.start() && caret < selected.end())
                caret = Math.min(result.getText().length(), destination + caret - selected.start());
            else caret = destination;
            Selection selection = Selection.UNDEFINED;
            Selection internal = editor.getControl().getSelection();
            if (internal.isDefined() && editor.getControl().getSkin() instanceof RichTextAreaSkin skin) {
                var buffer = skin.getViewModel().getTextBuffer();
                int start = buffer.getText(0, internal.getStart()).length();
                int end = buffer.getText(0, internal.getEnd()).length();
                if (start >= selected.start() && end <= selected.end()) {
                    start = Math.min(result.getText().length(), destination + start - selected.start());
                    end = Math.min(result.getText().length(), destination + end - selected.start());
                    if (end > start) selection = new Selection(start, end);
                }
            }
            editor.replaceDocument(new Document(result.getText(), result.getDecorations(), caret,
                    source.getPageFormat(), source.getHeadersFooters()), selection);
            javafx.application.Platform.runLater(editor.getControl()::requestFocus);
        }
        refresh();
    }

    /**
     * Setzt einen eigenen Sprungpfad für Klicks in die Strukturliste.
     *
     * <p>Standardmäßig springt die Ansicht direkt über
     * {@link MarkdownEditor#goToPosition(int)}. {@link EditorWindow} setzt
     * hier einen Handler, der die Sprungquelle für die Zurück-/Vorwärts-
     * Navigation (AddFeatures #4) vermerkt, bevor gesprungen wird.
     *
     * @param handler erhält die Zielposition; {@code null} stellt das
     *                Standardverhalten wieder her
     */
    public void setJumpHandler(java.util.function.IntConsumer handler) {
        this.jumpHandler = handler;
    }

    /** Führt einen Sprung über den konfigurierten Handler bzw. den Editor aus. */
    private void performJump(int position) {
        if (jumpHandler != null) {
            jumpHandler.accept(position);
        } else {
            editor.goToPosition(position);
        }
    }

    /**
     * Liefert die Liste, in der Überschriften angezeigt und ausgewählt werden.
     *
     * @return JavaFX-Liste der aktuellen Dokumentüberschriften
     */
    public ListView<DocumentHeading> getListView() {
        return listView;
    }

    /**
     * Liefert die Kopfzeile der Strukturansicht.
     *
     * @return lokalisiertes Titel-Label
     */
    public Label getHeaderLabel() {
        return headerLabel;
    }

    /** Markiert den Eintrag, der zur aktuellen Caret-Position gehört. */
    private void selectEntryAtCaret() {
        if (updatingSelection) {
            return;
        }
        int caret = editor.getInternalCaretPosition();
        int index = -1;
        var items = listView.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).position() <= caret) {
                index = i;
            } else {
                break;
            }
        }
        if (listView.getSelectionModel().getSelectedIndex() != index) {
            updatingSelection = true;
            try {
                if (index >= 0) {
                    listView.getSelectionModel().select(index);
                    listView.scrollTo(index);
                } else {
                    listView.getSelectionModel().clearSelection();
                }
            } finally {
                updatingSelection = false;
            }
        }
    }

    /** Zelle mit Einrückung nach Überschriften-Ebene. */
    static final class HeadingCell extends ListCell<DocumentHeading> {

        private final Label label = new Label();

        HeadingCell() {
            label.setMaxWidth(Double.MAX_VALUE);
            label.setWrapText(true);
        }

        @Override
        protected void updateItem(DocumentHeading heading, boolean empty) {
            super.updateItem(heading, empty);
            if (empty || heading == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            label.setText(heading.text());
            double size = switch (heading.level()) {
                case 1 -> 14;
                case 2 -> 13;
                default -> 12;
            };
            String weight = heading.level() == 1 ? "bold" : "normal";
            label.setStyle("-fx-font-size: " + size + "px; -fx-font-weight: " + weight + ";");
            setGraphic(label);
            setPadding(new Insets(2, 4, 2, indentFor(heading.level())));
        }

        /** Linker Innenabstand einer Zelle je nach Überschriften-Ebene. */
        static int indentFor(int level) {
            return 4 + (Math.max(1, level) - 1) * INDENT_PER_LEVEL;
        }
    }
}
