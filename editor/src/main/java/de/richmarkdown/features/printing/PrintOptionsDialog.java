package de.richmarkdown.features.printing;

import javafx.scene.control.*;
import static de.richmarkdown.ui.FeatureUi.text;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

/** Print-only preferences and scope; no document metadata is added. */
public final class PrintOptionsDialog extends Dialog<PrintOptions> {
    public PrintOptionsDialog(Window owner, PrintOptions initial, boolean hasSelection) {
        initOwner(owner); setTitle(text("Druckbereich und Druckgestaltung", "Print scope and layout"));
        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(8);
        ComboBox<PrintOptions.Scope> scope = new ComboBox<>(); scope.getItems().setAll(PrintOptions.Scope.values()); scope.setValue(initial.scope()); scope.setCellFactory(view -> scopeCell()); scope.setButtonCell(scopeCell()); scope.setTooltip(new Tooltip(text("Dokument, markierte Auswahl oder Kapitel an der Schreibposition drucken.", "Print the document, selected text, or chapter at the caret.")));
        TextField ranges = field(initial.pageRanges(), text("Leer: alle Seiten. Beispiel: 1-3,5. Nummerierung innerhalb des gewählten Druckbereichs.", "Empty: all pages. Example: 1-3,5. Pages within the chosen print scope."));
        TextField top = field(Double.toString(initial.top()), text("Oberer Seitenrand in Millimetern.", "Top margin in millimetres.")); TextField right = field(Double.toString(initial.right()), text("Rechter Seitenrand in Millimetern.", "Right margin in millimetres.")); TextField bottom = field(Double.toString(initial.bottom()), text("Unterer Seitenrand in Millimetern.", "Bottom margin in millimetres.")); TextField left = field(Double.toString(initial.left()), text("Linker Seitenrand in Millimetern.", "Left margin in millimetres."));
        TextField font = field(initial.fontFamily(), text("Schriftart ausschließlich für den Ausdruck. Code bleibt in Festbreitenschrift.", "Print-only font. Code retains its monospace font.")); TextField size = field(Double.toString(initial.fontSize()), text("Grundschriftgröße von 6 bis 48. Überschriftengrößen werden proportional angepasst.", "Base font size from 6 to 48. Heading sizes scale proportionally."));
        TextField header = field(initial.header(), text("Einzeiliger Kopftext, ausschließlich im Ausdruck.", "Single-line print-only header.")); TextField footer = field(initial.footer(), text("Einzeiliger Fußtext, ausschließlich im Ausdruck.", "Single-line print-only footer."));
        CheckBox numbers = new CheckBox(text("Seitenzahlen drucken", "Print page numbers")); numbers.setSelected(initial.pageNumbers()); numbers.setTooltip(new Tooltip(text("Seite und Gesamtseitenzahl in der Fußzeile anzeigen.", "Show page number and total in the footer.")));
        int row = 0; grid.addRow(row++, new Label(text("Druckbereich", "Print scope")), scope); grid.addRow(row++, new Label(text("Seiten", "Pages")), ranges); grid.addRow(row++, new Label(text("Rand oben (mm)", "Top margin (mm)")), top); grid.addRow(row++, new Label(text("Rand rechts (mm)", "Right margin (mm)")), right); grid.addRow(row++, new Label(text("Rand unten (mm)", "Bottom margin (mm)")), bottom); grid.addRow(row++, new Label(text("Rand links (mm)", "Left margin (mm)")), left); grid.addRow(row++, new Label(text("Druckschrift", "Print font")), font); grid.addRow(row++, new Label(text("Grundschriftgröße", "Base font size")), size); grid.addRow(row++, new Label(text("Kopfzeile", "Header")), header); grid.addRow(row++, new Label(text("Fußzeile", "Footer")), footer); grid.addRow(row++, new Label(""), numbers);
        Label error = new Label(); error.setWrapText(true); error.setMaxWidth(420); grid.add(error, 0, row, 2, 1); getDialogPane().setContent(grid); getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().lookupButton(ButtonType.OK).setUserData(text("Druckoptionen bestätigen", "Confirm print options"));
        ((Button) getDialogPane().lookupButton(ButtonType.OK)).setTooltip(new Tooltip(text("Druckbereich und Druckgestaltung für den nächsten Druckauftrag übernehmen.", "Use these scope and layout settings for this print job.")));
        ((Button) getDialogPane().lookupButton(ButtonType.CANCEL)).setTooltip(new Tooltip(text("Druckauftrag abbrechen.", "Cancel printing.")));
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, event -> { try { if (scope.getValue() == PrintOptions.Scope.SELECTION && !hasSelection) throw new IllegalArgumentException(text("Bitte zuerst Text markieren.", "Select text first.")); options(top, right, bottom, left, font, size, header, footer, numbers, scope, ranges); error.setText(""); } catch (IllegalArgumentException failure) { error.setText(failure.getMessage()); event.consume(); } });
        setResultConverter(button -> button == ButtonType.OK ? options(top, right, bottom, left, font, size, header, footer, numbers, scope, ranges) : null);
    }
    private static PrintOptions options(TextField top, TextField right, TextField bottom, TextField left, TextField font, TextField size, TextField header, TextField footer, CheckBox numbers, ComboBox<PrintOptions.Scope> scope, TextField ranges) {
        try { return new PrintOptions(number(top), number(right), number(bottom), number(left), font.getText(), number(size), header.getText(), footer.getText(), numbers.isSelected(), scope.getValue(), ranges.getText()); }
        catch (NumberFormatException failure) { throw new IllegalArgumentException(text("Bitte für Ränder und Schriftgröße gültige Zahlen eingeben.", "Enter valid margin and font-size numbers.")); }
    }
    private static double number(TextField field) { return Double.parseDouble(field.getText().strip().replace(',', '.')); }
    private static TextField field(String text, String tooltip) { TextField field = new TextField(text); field.setTooltip(new Tooltip(tooltip)); return field; }
    private static ListCell<PrintOptions.Scope> scopeCell() { return new ListCell<>() { @Override protected void updateItem(PrintOptions.Scope item, boolean empty) { super.updateItem(item, empty); setText(empty || item == null ? null : switch (item) { case DOCUMENT -> text("Gesamtes Dokument", "Entire document"); case SELECTION -> text("Markierte Auswahl", "Selected text"); case CHAPTER -> text("Aktuelles Kapitel einschließlich Unterkapitel", "Current chapter including subchapters"); }); } }; }
}
