/*
 * Copyright (C) 2026 Rainer Garbotz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package de.richmarkdown.ui.dialogs;

import com.gluonhq.richtextarea.RichTextArea;
import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.Selection;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.findreplace.FindReplaceEngine;
import de.richmarkdown.i18n.I18n;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integrationstests fuer den Suchen-und-Ersetzen-Dialog.
 */
class FindReplaceDialogTest {
    private MarkdownEditor editor;
    private RichTextArea rta;
    private Stage stage;
    private FindReplaceEngine engine;
    private FindReplaceDialog dialog;

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    @BeforeEach
    void setUp() {
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor();
            rta = editor.getControl();
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(rta), 600, 400));
            stage.show();
        });
        FxTestUtil.flushFxEvents();
    }

    @AfterEach
    void tearDown() {
        FxTestUtil.runAndWait(() -> {
            if (dialog != null) {
                dialog.hide();
                dialog = null;
            }
            if (stage != null) {
                stage.hide();
                stage = null;
            }
        });
        FxTestUtil.flushFxEvents();
    }

    private void setText(String text) {
        FxTestUtil.runAndWait(() -> editor.setMarkdown(text));
        FxTestUtil.flushFxEvents();
        FxTestUtil.flushFxEvents();
        engine = FxTestUtil.callAndWait(
                () -> new FindReplaceEngine(rta, ((RichTextAreaSkin) rta.getSkin()).getViewModel()));
    }

    private String text() {
        FxTestUtil.runAndWait(() -> rta.getActionFactory().save().execute(new ActionEvent()));
        FxTestUtil.flushFxEvents();
        return FxTestUtil.callAndWait(() -> rta.getDocument().getText());
    }

    private void openDialog() {
        FxTestUtil.runAndWait(() -> {
            dialog = new FindReplaceDialog(stage, engine, backwards -> true);
            dialog.showWithSelection(null);
        });
        FxTestUtil.flushFxEvents();
    }

    @Test
    void dialog_opensAndExposesAllButtons() {
        setText("eins zwei eins");
        openDialog();
        FxTestUtil.runAndWait(() -> {
            assertNotNull(dialog.findBox);
            assertNotNull(dialog.replaceBox);
            assertNotNull(dialog.findPreviousButton);
            assertTrue(dialog.findPreviousButton.getText() != null
                    && !dialog.findPreviousButton.getText().isEmpty());
            assertNotNull(dialog.findNextButton);
            assertNotNull(dialog.replaceButton);
            assertNotNull(dialog.replaceAllButton);
            assertNotNull(dialog.findAllButton);
        });
    }

    @Test
    void dialog_findNextSelectsMatch() {
        setText("eins zwei eins");
        openDialog();
        FxTestUtil.runAndWait(() -> {
            dialog.findBox.getEditor().setText("zwei");
            dialog.findNext();
        });
        FxTestUtil.flushFxEvents();
        String selected = FxTestUtil.callAndWait(
                () -> engine.getSelection() == null ? null
                        : engine.getSelection().isDefined() ? "defined" : null);
        assertEquals("defined", selected);
    }

    @Test
    void dialog_findPreviousSearchesBackwards() {
        setText("eins zwei eins");
        openDialog();
        FxTestUtil.runAndWait(() -> {
            var vm = ((RichTextAreaSkin) rta.getSkin()).getViewModel();
            vm.setCaretPosition(engine.getText().length());
            vm.clearSelection();
            dialog.findBox.getEditor().setText("zwei");
            dialog.findPrevious();
        });
        FxTestUtil.flushFxEvents();
        FindReplaceEngine.Match m = FxTestUtil.callAndWait(() -> {
            var sel = engine.getSelection();
            if (sel == null || !sel.isDefined()) {
                return null;
            }
            return new FindReplaceEngine.Match(sel.getStart(), sel.getEnd());
        });
        assertNotNull(m);
        String matched = FxTestUtil.callAndWait(engine::getText)
                .substring(m.getStart(), m.getEnd());
        assertEquals("zwei", matched);
    }

    @Test
    void dialog_replaceAllReplacesAndCounts() {
        setText("eins zwei eins");
        openDialog();
        FxTestUtil.runAndWait(() -> {
            dialog.findBox.getEditor().setText("eins");
            dialog.replaceBox.getEditor().setText("1");
            dialog.replaceAll();
        });
        FxTestUtil.flushFxEvents();
        assertEquals("1 zwei 1", text());
    }

    @Test
    void dialog_findNextWithoutTextClearsStatus() {
        setText("eins zwei eins");
        openDialog();
        FxTestUtil.runAndWait(() -> {
            dialog.findBox.getEditor().setText("");
            dialog.findNext();
        });
        assertEquals("", FxTestUtil.callAndWait(() -> dialog.statusLabel.getText()));
    }

    @Test
    void dialog_historyLimitIsEnforced() {
        setText("eins zwei eins");
        openDialog();
        FxTestUtil.runAndWait(() -> {
            for (int i = 0; i < FindReplaceDialog.HISTORY_LIMIT + 5; i++) {
                dialog.findBox.getEditor().setText("begriff" + i);
                dialog.findNext();
            }
        });
        List<String> items = FxTestUtil.callAndWait(() -> List.copyOf(dialog.findBox.getItems()));
        assertTrue(items.size() <= FindReplaceDialog.HISTORY_LIMIT);
    }

    @Test
    void dialog_reopenKeepsSelectionScope() {
        setText("eins zwei eins");
        openDialog();
        // „Nur Auswahl“ aktivieren und Anker beim ersten Oeffnen setzen.
        FxTestUtil.runAndWait(() -> {
            dialog.selectionOnlyBox.setSelected(true);
            engine.selectMatch(new FindReplaceEngine.Match(0, 9));
            dialog.showWithSelection(null);
            // Erste Suche verankert den Scope (0..9).
            engine.findAll("eins", dialog.buildOptions());
        });
        int[] first = FxTestUtil.callAndWait(engine::selectionAnchor);
        // Treffer-Selektion aendern, Dialog erneut „oeffnen“ (Menue/Strg+F):
        // Anker darf NICHT auf die Treffer-Selektion springen.
        FxTestUtil.runAndWait(() -> {
            engine.selectMatch(new FindReplaceEngine.Match(10, 14));
            dialog.showWithSelection(null);
        });
        int[] second = FxTestUtil.callAndWait(engine::selectionAnchor);
        assertNotNull(first);
        assertEquals(0, first[0]);
        assertEquals(9, first[1]);
        assertNotNull(second);
        assertEquals(first[0], second[0]);
        assertEquals(first[1], second[1]);
    }

    @Test
    void dialog_findNextAfterWrapKeepsFocusInDialog() {
        // Regression: Nach dem Wrap-Abfragedialog wanderte der Fokus in den
        // Editor; Enter feuerte dort den Absatzumbruch-Handler statt
        // „Weitersuchen“ und zerstoerte die Treffer-Formatierung.
        setText("eins zwei eins");
        openDialog();
        FxTestUtil.runAndWait(() -> {
            var vm = ((RichTextAreaSkin) rta.getSkin()).getViewModel();
            vm.setCaretPosition(engine.getText().length());
            vm.clearSelection();
            dialog.findBox.getEditor().setText("eins");
            dialog.findNext();
        });
        FxTestUtil.flushFxEvents();
        // Wrap-Bestaetigung (Test-Handler liefert true), erster Treffer markiert.
        FindReplaceEngine.Match m = FxTestUtil.callAndWait(() -> {
            var sel = engine.getSelection();
            return sel == null ? null
                    : new FindReplaceEngine.Match(sel.getStart(), sel.getEnd());
        });
        assertNotNull(m);
        // Fokus muss im Dialog bleiben – Enter loest dort den Default-Button
        // („Weitersuchen“) aus, nicht den Editor-Enter-Handler.
        boolean dialogFocused = FxTestUtil.callAndWait(() ->
                dialog.getScene().getFocusOwner() != null
                        && dialog.getScene().getWindow() == dialog
                        && dialog.isFocused());
        assertTrue(dialogFocused || FxTestUtil.callAndWait(() -> dialog.findBox.isFocused()),
                "Nach Wrap-Suche muss der Fokus im Suchen-Dialog liegen");
    }

    @Test
    void dialog_replaceAllReportsReplacedAndSkipped() {
        setText("eins zwei eins");
        openDialog();
        FxTestUtil.runAndWait(() -> {
            dialog.findBox.getEditor().setText("eins");
            dialog.replaceBox.getEditor().setText("1");
            dialog.replaceAll();
        });
        FxTestUtil.flushFxEvents();
        assertEquals("1 zwei 1", text());
        // Status sollte ersetzte Anzahl anzeigen (keine skipped)
        String status = FxTestUtil.callAndWait(() -> dialog.statusLabel.getText());
        assertTrue(status.contains("2"), "Status soll 2 ersetzte anzeigen: " + status);
    }

    @Test
    void dialog_replaceAllSkipsParagraphBoundary() {
        setText("abc\ndef abc\ndef");
        openDialog();
        FxTestUtil.runAndWait(() -> {
            dialog.findBox.getEditor().setText("abc\ndef");
            dialog.replaceBox.getEditor().setText("XYZ");
            dialog.replaceAll();
        });
        FxTestUtil.flushFxEvents();
        // Alle Treffer enthalten Absatzgrenze -> nichts ersetzt
        assertEquals("abc\ndef abc\ndef", text());
        String status = FxTestUtil.callAndWait(() -> dialog.statusLabel.getText());
        // Bei 0 Treffern zeigt der Dialog den i18n-Text "Keine Übereinstimmung gefunden"
        assertEquals(I18n.text("dialog.findReplace.noMatch"), status,
                "Status soll 'Keine Übereinstimmung gefunden' anzeigen: " + status);
    }
}

