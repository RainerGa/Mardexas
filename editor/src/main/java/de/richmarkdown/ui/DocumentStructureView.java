package de.richmarkdown.ui;

import de.richmarkdown.editor.DocumentHeading;
import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.i18n.I18n;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
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

    /** Guard gegen Selektions-/Navigations-Rückkopplung. */
    private boolean updatingSelection;

    /**
     * Erstellt eine mit dem Editor verbundene Dokumentstrukturansicht.
     *
     * @param editor Editor-Fassade als Quelle der Überschriften, Caretposition
     *               und Navigationsaktionen
     */
    public DocumentStructureView(MarkdownEditor editor) {
        this.editor = editor;
        getStyleClass().add("rme-structure-view");

        listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        listView.setCellFactory(lv -> new HeadingCell());
        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, nv) -> {
            if (nv != null && !updatingSelection) {
                updatingSelection = true;
                try {
                    editor.goToPosition(nv.position());
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

        VBox.setVgrow(listView, Priority.ALWAYS);
        getChildren().addAll(headerLabel, listView);
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
        var headings = editor.getHeadings();
        updatingSelection = true;
        try {
            listView.getItems().setAll(headings);
        } finally {
            updatingSelection = false;
        }
        selectEntryAtCaret();
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
        int caret = editor.getControl().getCaretPosition();
        int index = -1;
        var items = listView.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).position() <= caret) {
                index = i;
            } else {
                break;
            }
        }
        if (index >= 0 && listView.getSelectionModel().getSelectedIndex() != index) {
            updatingSelection = true;
            try {
                listView.getSelectionModel().select(index);
                listView.scrollTo(index);
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
