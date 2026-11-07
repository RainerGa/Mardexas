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

import de.richmarkdown.findreplace.FindReplaceEngine;
import de.richmarkdown.i18n.I18n;
import com.gluonhq.richtextarea.Selection;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.text.MessageFormat;
import java.util.List;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TextInputDialog;
import javafx.util.StringConverter;
import de.richmarkdown.io.SearchPresetStore;
import de.richmarkdown.ui.FeatureUi;
import static de.richmarkdown.ui.FeatureUi.text;

/**
 * Suchen-und-Ersetzen-Dialog nach LibreOffice Writer.
 */
public class FindReplaceDialog extends Stage {
    static final int HISTORY_LIMIT = 20;
    final FindReplaceEngine engine;
    final java.util.function.Function<Boolean, Boolean> wrapConfirmed;
    final ComboBox<String> findBox = new ComboBox<>();
    final ComboBox<String> replaceBox = new ComboBox<>();
    final CheckBox matchCaseBox = new CheckBox();
    final CheckBox wholeWordsBox = new CheckBox();
    final CheckBox regexBox = new CheckBox();
    final CheckBox wildcardsBox = new CheckBox();
    final CheckBox backwardsBox = new CheckBox();
    final CheckBox selectionOnlyBox = new CheckBox();
    final CheckBox strikethroughOnlyBox = new CheckBox();
    final Label statusLabel = new Label();
    final Button findAllButton = new Button();
    final Button findNextButton = new Button();
    final Button findPreviousButton = new Button();
    final Button replaceButton = new Button();
    final Button replaceAllButton = new Button();
    final ComboBox<FindReplaceEngine.FormatFilter> formatBox = new ComboBox<>();
    final ComboBox<SearchPresetStore.Preset> presetBox = new ComboBox<>();
    private final SearchPresetStore presets = new SearchPresetStore(FeatureUi.profileDirectory());
    private Window ownerWindow;
    private java.util.function.BiConsumer<String, FindReplaceEngine.FindOptions> criteriaHandler;
    /** Receives remembered criteria independently of the actual dialog owner. */
    public void setCriteriaHandler(java.util.function.BiConsumer<String, FindReplaceEngine.FindOptions> handler) {
        criteriaHandler = handler;
    }
    public FindReplaceDialog(Window owner, FindReplaceEngine engine, java.util.function.Function<Boolean, Boolean> wrapConfirmed) {
        this.ownerWindow = owner;
        this.engine = engine;
        this.wrapConfirmed = wrapConfirmed == null ? (Boolean b) -> true : wrapConfirmed;
        initOwner(owner);
        initModality(Modality.NONE);
        setOnHidden(event -> engine.moveCaretToSelectionEnd());
        setTitle(I18n.text("dialog.findReplace.title"));
        setResizable(true);
        build();
    }
    private void build() {
        findBox.setEditable(true);
        replaceBox.setEditable(true);
        findBox.setMaxWidth(Double.MAX_VALUE);
        replaceBox.setMaxWidth(Double.MAX_VALUE);
        matchCaseBox.setText(I18n.text("dialog.findReplace.matchCase"));
        wholeWordsBox.setText(I18n.text("dialog.findReplace.wholeWords"));
        regexBox.setText(I18n.text("dialog.findReplace.regex"));
        wildcardsBox.setText(I18n.text("dialog.findReplace.wildcards"));
        backwardsBox.setText(I18n.text("dialog.findReplace.backwards"));
        selectionOnlyBox.setText(I18n.text("dialog.findReplace.selectionOnly"));
        strikethroughOnlyBox.setText(I18n.text("dialog.findReplace.strikethroughOnly"));
        findAllButton.setText(I18n.text("dialog.findReplace.findAll"));
        findNextButton.setText(I18n.text("dialog.findReplace.findNext"));
        findPreviousButton.setText(I18n.text("dialog.findReplace.findPrevious"));
        replaceButton.setText(I18n.text("dialog.findReplace.replace"));
        replaceAllButton.setText(I18n.text("dialog.findReplace.replaceAll"));
        findNextButton.setDefaultButton(true);
        regexBox.selectedProperty().addListener((obs, old, sel) -> { if (sel) wildcardsBox.setSelected(false); });
        wildcardsBox.selectedProperty().addListener((obs, old, sel) -> { if (sel) regexBox.setSelected(false); });
        GridPane fields = new GridPane();
        fields.setHgap(8);
        fields.setVgap(8);
        ColumnConstraints c0 = new ColumnConstraints();
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        c1.setFillWidth(true);
        fields.getColumnConstraints().addAll(c0, c1);
        fields.add(new Label(I18n.text("dialog.findReplace.find")), 0, 0);
        fields.add(findBox, 1, 0);
        fields.add(new Label(I18n.text("dialog.findReplace.replaceWith")), 0, 1);
        fields.add(replaceBox, 1, 1);
        GridPane.setHgrow(findBox, Priority.ALWAYS);
        GridPane.setHgrow(replaceBox, Priority.ALWAYS);
        formatBox.getItems().setAll(FindReplaceEngine.FormatFilter.values());
        formatBox.setValue(FindReplaceEngine.FormatFilter.ANY);
        formatBox.setId("search-format-filter");
        formatBox.setConverter(new StringConverter<>() {
            @Override public String toString(FindReplaceEngine.FormatFilter value) {
                if (value == null) return "";
                return switch (value) {
                    case ANY -> text("Alle Formatierungen", "Any formatting");
                    case BOLD -> text("Fett", "Bold"); case ITALIC -> text("Kursiv", "Italic");
                    case UNDERLINE -> text("Unterstrichen", "Underline");
                    case STRIKETHROUGH -> text("Durchgestrichen", "Strikethrough");
                    case LINK -> text("Links", "Links"); case HEADING -> text("Überschriften", "Headings");
                    case CODE -> text("Code", "Code");
                };
            }
            @Override public FindReplaceEngine.FormatFilter fromString(String value) { return FindReplaceEngine.FormatFilter.valueOf(value); }
        });
        formatBox.setTooltip(new Tooltip(text("Treffer müssen vollständig diese Auszeichnung besitzen", "Matches must fully have this formatting")));
        VBox opts = new VBox(4, matchCaseBox, wholeWordsBox, regexBox, wildcardsBox, backwardsBox, selectionOnlyBox, strikethroughOnlyBox,
                new HBox(8, new Label(text("Formatfilter", "Format filter")), formatBox));
        TitledPane pane = new TitledPane(I18n.text("dialog.findReplace.options"), opts);
        pane.setExpanded(false);
        pane.setCollapsible(true);
        HBox buttons = new HBox(8, findAllButton, findNextButton, findPreviousButton, replaceButton, replaceAllButton);
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        Button results = FeatureUi.button(text("Trefferliste", "Results"), text("Alle Treffer anzeigen und zu Textstellen springen", "List matches and jump to their positions"), () -> runSearchAction(() -> showResults(false)));
        results.setId("search-results");
        Button preview = FeatureUi.button(text("Ersetzungen prüfen…", "Preview replacements…"), text("Vorschau anzeigen und nur ausgewählte Treffer ersetzen", "Preview and replace selected matches only"), () -> runSearchAction(() -> showResults(true)));
        preview.setId("search-replacement-preview");
        Button load = FeatureUi.button(text("Laden", "Load"), text("Gespeicherte Suchen auflisten und ausgewählte Suche laden", "List saved searches and load the selected search"), () -> {
            try {
                if (presetBox.getValue() != null) applyPreset(presetBox.getValue());
                presetBox.getItems().setAll(presets.list());
                presetBox.show();
            } catch (Exception error) { statusLabel.setText(error.getMessage()); }
        });
        presetBox.setPromptText(text("Gespeicherte Suchen", "Saved searches"));
        presetBox.setOnAction(event -> { if (presetBox.getValue() != null) applyPreset(presetBox.getValue()); });
        Button save = FeatureUi.button(text("Speichern…", "Save…"), text("Suchbegriff, Ersatz und Optionen im Benutzerprofil speichern", "Save query, replacement and options in the user profile"), () -> {
            TextInputDialog name = new TextInputDialog(findText());
            name.initOwner(this); name.setHeaderText(text("Name der gespeicherten Suche", "Saved search name"));
            name.showAndWait().ifPresent(value -> {
                try { savePreset(value); } catch (Exception error) { statusLabel.setText(error.getMessage()); }
            });
        });
        Button delete = FeatureUi.button(text("Löschen", "Delete"), text("Ausgewählte gespeicherte Suche löschen", "Delete the selected saved search"), () -> {
            if (presetBox.getValue() == null) return;
            try { presets.delete(presetBox.getValue().id()); presetBox.getItems().setAll(presets.list()); }
            catch (Exception error) { statusLabel.setText(error.getMessage()); }
        });
        for (Button button : List.of(findAllButton, findNextButton, findPreviousButton, replaceButton, replaceAllButton))
            button.setTooltip(new Tooltip(button.getText()));
        VBox root = new VBox(10, fields, pane, buttons, new HBox(8, results, preview),
                new HBox(8, presetBox, load, save, delete), statusLabel);
        root.setPadding(new Insets(12));
        root.getStyleClass().add("rme-find-replace");
        Scene scene = new Scene(root, 760, 470);
        var css = getClass().getResource("/de/richmarkdown/ui/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        setScene(scene);
        findAllButton.setOnAction(e -> runSearchAction(this::findAll));
        findNextButton.setOnAction(e -> runSearchAction(this::findNext));
        findPreviousButton.setOnAction(e -> runSearchAction(this::findPrevious));
        replaceButton.setOnAction(e -> runSearchAction(this::replace));
        replaceAllButton.setOnAction(e -> runSearchAction(this::replaceAll));
    }
    private void runSearchAction(Runnable action) {
        try { action.run(); }
        catch (RuntimeException error) { statusLabel.setText(error.getMessage()); }
        catch (StackOverflowError error) { statusLabel.setText(text("Regulärer Ausdruck ist zu komplex.", "Regular expression is too complex.")); }
    }
    FindReplaceEngine.FindOptions buildOptions() {
        FindReplaceEngine.FindOptions o = new FindReplaceEngine.FindOptions();
        o.setMatchCase(matchCaseBox.isSelected());
        o.setWholeWords(wholeWordsBox.isSelected());
        o.setRegex(regexBox.isSelected());
        o.setWildcards(wildcardsBox.isSelected());
        o.setBackwards(backwardsBox.isSelected());
        o.setSelectionOnly(selectionOnlyBox.isSelected());
        o.setStrikethroughOnly(strikethroughOnlyBox.isSelected());
        o.setFormatFilter(formatBox.getValue());
        return o;
    }
    String findText() {
        String v = findBox.getEditor().getText();
        return v == null ? "" : v;
    }
    String replaceText() {
        String v = replaceBox.getEditor().getText();
        return v == null ? "" : v;
    }
    void remember(ComboBox<String> box, String value) {
        if (value == null || value.isEmpty()) return;
        // Editor-Text vor der Items-Änderung sichern: JavaFX bindet
        // editor.textProperty() bidirektional an selectionModel.selectedItemProperty().
        // Ein remove() des aktuell angezeigten Werts würde sonst den Editor-Text leeren.
        String currentText = box.getEditor().getText();
        box.getItems().remove(value);
        box.getItems().add(0, value);
        while (box.getItems().size() > HISTORY_LIMIT) box.getItems().remove(box.getItems().size() - 1);
        box.getEditor().setText(currentText == null ? value : currentText);
    }
    void findAll() {
        String search = findText();
        if (search.isEmpty()) { statusLabel.setText(""); return; }
        FindReplaceEngine.FindOptions o = buildOptions();
        if (engine.compilePattern(search, o) == null) { statusLabel.setText(I18n.text("dialog.findReplace.invalidRegex")); return; }
        List<FindReplaceEngine.Match> all = engine.findAll(search, o);
        remember(findBox, search);
        rememberCriteria(search, o);
        if (all.isEmpty()) { statusLabel.setText(I18n.text("dialog.findReplace.noMatch")); return; }
        engine.selectMatch(all.get(0), false);
        statusLabel.setText(MessageFormat.format(I18n.text("dialog.findReplace.foundCount"), all.size()));
    }
    void findNext() {
        String search = findText();
        if (search.isEmpty()) { statusLabel.setText(""); focusFindField(); return; }
        FindReplaceEngine.FindOptions o = buildOptions();
        if (engine.compilePattern(search, o) == null) { statusLabel.setText(I18n.text("dialog.findReplace.invalidRegex")); focusFindField(); return; }
        FindReplaceEngine.SearchResult r = engine.findNext(search, o);
        remember(findBox, search);
        rememberCriteria(search, o);
        if (r == null) { statusLabel.setText(I18n.text("dialog.findReplace.noMatch")); focusFindField(); return; }
        if (r.getWrap() == FindReplaceEngine.Wrap.WRAPPED && !askWrap(o.isBackwards())) { focusFindField(); return; }
        engine.selectMatch(r.getMatch(), false);
        statusLabel.setText("");
        focusFindField();
    }
    /**
     * Sucht rueckwaerts (LO: „Suche nach oben“ bzw. „Vorheriges suchen“).
     * Erzwingt die Richtung unabhaengig vom „Rueckwaerts“-Kasten.
     */
    void findPrevious() {
        String search = findText();
        if (search.isEmpty()) { statusLabel.setText(""); focusFindField(); return; }
        FindReplaceEngine.FindOptions o = buildOptions();
        o.setBackwards(true);
        if (engine.compilePattern(search, o) == null) { statusLabel.setText(I18n.text("dialog.findReplace.invalidRegex")); focusFindField(); return; }
        FindReplaceEngine.SearchResult r = engine.findNext(search, o);
        remember(findBox, search);
        rememberCriteria(search, o);
        if (r == null) { statusLabel.setText(I18n.text("dialog.findReplace.noMatch")); focusFindField(); return; }
        if (r.getWrap() == FindReplaceEngine.Wrap.WRAPPED && !askWrap(true)) { focusFindField(); return; }
        engine.selectMatch(r.getMatch(), false);
        statusLabel.setText("");
        focusFindField();
    }
    void replace() {
        String search = findText();
        if (search.isEmpty()) { focusFindField(); return; }
        FindReplaceEngine.FindOptions o = buildOptions();
        if (engine.compilePattern(search, o) == null) { statusLabel.setText(I18n.text("dialog.findReplace.invalidRegex")); focusFindField(); return; }
        FindReplaceEngine.SearchResult r = engine.replaceSelection(search, replaceText(), o);
        rememberCriteria(search, o);
        remember(findBox, search);
        remember(replaceBox, replaceText());
        if (r == null) { statusLabel.setText(I18n.text("dialog.findReplace.noMatch")); focusFindField(); return; }
        if (r.getWrap() == FindReplaceEngine.Wrap.NOT_FOUND) {
            // Erfolgreich ersetzt, aber kein weiterer Treffer vorhanden.
            engine.clearMatchSelection();
            statusLabel.setText(I18n.text("dialog.findReplace.noFurtherMatch"));
            focusFindField();
            return;
        }
        if (r.getWrap() == FindReplaceEngine.Wrap.WRAPPED && !askWrap(o.isBackwards())) { focusFindField(); return; }
        engine.selectMatch(r.getMatch(), false);
        statusLabel.setText("");
        focusFindField();
    }
    void replaceAll() {
        String search = findText();
        FindReplaceEngine.FindOptions o = buildOptions();
        if (search.isEmpty()) { statusLabel.setText(""); return; }
        if (engine.compilePattern(search, o) == null) { statusLabel.setText(I18n.text("dialog.findReplace.invalidRegex")); return; }
        int replaced = engine.replaceAll(search, replaceText(), o);
        int skipped = engine.getLastSkippedCount();
        rememberCriteria(search, o);
        remember(findBox, search);
        remember(replaceBox, replaceText());
        if (replaced == 0 && skipped == 0) {
            statusLabel.setText(I18n.text("dialog.findReplace.noMatch"));
        } else if (skipped > 0) {
            statusLabel.setText(MessageFormat.format(I18n.text("dialog.findReplace.replacedSkippedCount"), replaced, skipped));
        } else {
            statusLabel.setText(MessageFormat.format(I18n.text("dialog.findReplace.replacedCount"), replaced));
        }
    }
    void applyPreset(SearchPresetStore.Preset preset) {
        findBox.getEditor().setText(preset.query()); replaceBox.getEditor().setText(preset.replacement());
        var o = preset.options();
        matchCaseBox.setSelected(o.isMatchCase()); wholeWordsBox.setSelected(o.isWholeWords());
        regexBox.setSelected(o.isRegex()); wildcardsBox.setSelected(o.isWildcards());
        backwardsBox.setSelected(o.isBackwards()); selectionOnlyBox.setSelected(o.isSelectionOnly());
        strikethroughOnlyBox.setSelected(o.isStrikethroughOnly()); formatBox.setValue(o.getFormatFilter());
    }
    void savePreset(String name) throws java.io.IOException {
        if (findText().isEmpty()) throw new IllegalArgumentException(text("Suchbegriff fehlt", "Search query is empty"));
        presets.save(name, findText(), replaceText(), buildOptions());
        presetBox.getItems().setAll(presets.list());
    }
    private void showResults(boolean replacements) {
        if (findText().isEmpty()) return;
        var options = buildOptions();
        if (engine.compilePattern(findText(), options) == null) {
            statusLabel.setText(I18n.text("dialog.findReplace.invalidRegex")); return;
        }
        try {
            String original = engine.getText();
            var rows = engine.previewReplacements(findText(), replacements ? replaceText() : "", options);
            if (rows.isEmpty()) { statusLabel.setText(I18n.text("dialog.findReplace.noMatch")); return; }
            var dialog = new SearchResultsDialog(this, rows, replacements, match -> engine.selectMatch(match, false));
            dialog.showAndWait().ifPresent(selected -> {
                try {
                    int count = engine.applyPreview(original, selected);
                    statusLabel.setText(MessageFormat.format(I18n.text("dialog.findReplace.replacedCount"), count));
                } catch (Exception error) { statusLabel.setText(error.getMessage()); }
            });
        } catch (Exception error) { statusLabel.setText(error.getMessage()); }
    }
    private void rememberCriteria(String search, FindReplaceEngine.FindOptions o) {
        if (criteriaHandler != null) criteriaHandler.accept(search, o);
        else if (ownerWindow instanceof de.richmarkdown.ui.EditorWindow w) w.rememberFindCriteria(search, o);
    }
    private boolean askWrap(boolean backwards) {
        // Genau eine Rueckfrage pro Wrap (LO: Am Anfang/Ende weitersuchen?).
        // Der uebergebene Handler zeigt den Dialog oder liefert die
        // Test-Bestaetigung; kein zweiter Alert hier.
        return wrapConfirmed.apply(backwards);
    }
    /**
     * Holt den Fokus zurueck ins Suchfeld, damit Enter zuverlaessig wieder
     * „Weitersuchen“ ausloest. Noetig v. a. nach dem modalen Wrap-Abfragedialog:
     * Dessen Schliessen gibt den Fokus sonst an den Editor zurueck, und Enter
     * wuerde dort den Absatzumbruch-Handler feuern statt weiterzusuchen
     * (LO: Fokus bleibt im Suchen-Dialog).
     */
    private void focusFindField() {
        findBox.requestFocus();
    }
    /**
     * Uebernimmt die aktuelle Auswahl als Suchtext und oeffnet den Dialog.
     */
    public void showWithSelection(String selectedText) {
        boolean wasShowing = isShowing();
        if (selectedText != null && !selectedText.isEmpty() && findText().isEmpty()) {
            findBox.getEditor().setText(selectedText);
        }
        if (!wasShowing) show();
        toFront();
        findBox.requestFocus();
        // Auswahl als Such-Scope merken – aber nur beim allerersten Oeffnen.
        // Bei wiederholtem Oeffnen (Menue/Strg+H) darf ein bereits geankerter
        // Scope nicht ueberschrieben werden (sonst springt „Nur Auswahl“
        // unerwartet in die aktuelle Treffer-Selektion).
        if (!wasShowing) {
            Selection sel = engine.getSelection();
            if (sel != null && sel.isDefined()) {
                int len = engine.getText().length();
                engine.lockSelectionScope(sel.getStart(), sel.getEnd(), len);
            }
        }
    }
}
