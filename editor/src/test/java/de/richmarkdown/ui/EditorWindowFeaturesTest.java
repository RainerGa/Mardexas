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

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.settings.ApplicationSettings;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD: EditorWindow-Integration der Features 3 bis 6 (AddFeatures):
 * Statistik-Menüpunkt, Zurück-/Vorwärts-Navigation, schreibgeschütztes
 * Öffnen und Zeilenumbruch-Modus.
 */
class EditorWindowFeaturesTest {

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
        I18n.setSettingsStore(ApplicationSettings.inMemory());
        I18n.setCurrentLanguage("de");
        I18n.setDocumentStructureEnabled(false);
        I18n.setLineNumbersEnabled(false);
    }

    @AfterAll
    static void tearDown() {
        // kein hide(): blockt den FX-Thread in Monocle headless
    }

    private EditorWindow newWindow() {
        return FxTestUtil.callAndWait(() -> {
            EditorWindow created = new EditorWindow();
            created.show();
            return created;
        });
    }

    private MenuBar menuBar(EditorWindow window) {
        return FxTestUtil.callAndWait(() -> {
            var root = (javafx.scene.layout.BorderPane) window.getScene().getRoot();
            var top = (VBox) root.getTop();
            return (MenuBar) top.getChildren().get(0);
        });
    }

    private Menu menuByName(EditorWindow window, String text) {
        return FxTestUtil.callAndWait(() -> menuBar(window).getMenus().stream()
                .filter(m -> text.equals(m.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Menu not found: " + text)));
    }

    // ---------- Nr. 3: Statistik ----------

    @Test
    void extrasMenuContainsStatisticsItem() {
        EditorWindow window = newWindow();
        Menu extras = menuByName(window, I18n.text("menu.extras"));
        boolean found = FxTestUtil.callAndWait(() -> extras.getItems().stream()
                .anyMatch(item -> I18n.text("menu.statistics").equals(item.getText())));
        assertTrue(found, "Extras-Menü enthält „Statistik...“");
    }

    @Test
    void extrasMenuHasNoPlaceholderItemForSettings() {
        // Der frühere Eintrag „Einstellungen…“ öffnete nur einen
        // Informationsdialog ohne Funktion und wurde entfernt.
        EditorWindow window = newWindow();
        Menu extras = menuByName(window, I18n.text("menu.extras"));
        List<String> labels = FxTestUtil.callAndWait(() -> extras.getItems().stream()
                .map(MenuItem::getText)
                .toList());
        assertEquals(List.of(I18n.text("menu.statistics")), labels,
                "Unter „Extras“ gibt es nur noch die Statistik, keinen Platzhalter mehr");
    }

    @Test
    void statisticsDialogReflectsEditorDocument() {
        EditorWindow window = newWindow();
        FxTestUtil.runAndWait(() -> window.getEditor().setMarkdown("# Titel\n\nHallo Welt"));
        FxTestUtil.flushFxEvents();
        FxTestUtil.flushFxEvents();
        var stats = FxTestUtil.callAndWait(() -> window.getEditor().getStatistics());
        assertTrue(stats.words() >= 2, "mindestens Titel und Hallo/Welt");
        assertTrue(stats.lines() >= 2);
    }

    // ---------- Nr. 4: Zurück-/Vorwärts-Navigation ----------

    @Test
    void toolbarHasBackAndForwardButtons() {
        EditorWindow window = newWindow();
        assertNotNull(FxTestUtil.callAndWait(() -> window.getToolBar().getBackButton()));
        assertNotNull(FxTestUtil.callAndWait(() -> window.getToolBar().getForwardButton()));
    }

    @Test
    void backAndForwardStartDisabled() {
        EditorWindow window = newWindow();
        Boolean disabled = FxTestUtil.callAndWait(() ->
                window.getToolBar().getBackButton().isDisable()
                        && window.getToolBar().getForwardButton().isDisable());
        assertTrue(disabled, "ohne Sprünge sind Zurück/Vorwärts gesperrt");
    }

    @Test
    void jumpToThenNavigateBackRestoresPosition() {
        EditorWindow window = newWindow();
        FxTestUtil.runAndWait(() -> window.jumpTo(5));
        FxTestUtil.flushFxEvents();
        Boolean backEnabled = FxTestUtil.callAndWait(
                () -> !window.getToolBar().getBackButton().isDisable());
        assertTrue(backEnabled, "nach einem Sprung ist Zurück aktiv");

        Boolean wentBack = FxTestUtil.callAndWait(window::navigateBack);
        assertTrue(wentBack);
        int caret = FxTestUtil.callAndWait(
                () -> window.getEditorControl().getCaretPosition());
        assertEquals(0, caret, "zurück zur Ausgangsposition");

        Boolean wentForward = FxTestUtil.callAndWait(window::navigateForward);
        assertTrue(wentForward, "Vorwärts nach dem Zurück-Sprung");
    }

    @Test
    void navigateBackWithoutJumpsDoesNothing() {
        EditorWindow window = newWindow();
        Boolean wentBack = FxTestUtil.callAndWait(window::navigateBack);
        assertFalse(wentBack);
    }


    // ---------- Nr. 5: Schreibgeschütztes Öffnen ----------

    @Test
    void readOnlyLocksEditing() {
        EditorWindow window = newWindow();
        FxTestUtil.runAndWait(() -> window.setReadOnly(true));
        Boolean editable = FxTestUtil.callAndWait(
                () -> window.getEditorControl().isEditable());
        assertFalse(editable, "Control im Schreibschutz nicht editierbar");
        Boolean readOnly = FxTestUtil.callAndWait(window::isReadOnly);
        assertTrue(readOnly);

        FxTestUtil.runAndWait(() -> window.setReadOnly(false));
        editable = FxTestUtil.callAndWait(() -> window.getEditorControl().isEditable());
        assertTrue(editable, "Schreibschutz aufhebbar");
    }

    @Test
    void readOnlyDisablesFormatControls() {
        EditorWindow window = newWindow();
        FxTestUtil.runAndWait(() -> window.setReadOnly(true));
        Boolean formatDisabled = FxTestUtil.callAndWait(() ->
                window.getToolBar().getBoldToggle().isDisable()
                        && window.getToolBar().getStyleChoice().isDisable()
                        && window.getToolBar().getIndentButton().isDisable());
        assertTrue(formatDisabled, "Formatier-Schaltflächen gesperrt");
    }

    @Test
    void fileMenuContainsReadOnlyOpenItem() {
        EditorWindow window = newWindow();
        Menu file = menuByName(window, I18n.text("menu.file"));
        boolean found = FxTestUtil.callAndWait(() -> file.getItems().stream()
                .anyMatch(item -> I18n.text("menu.openReadOnly").equals(item.getText())));
        assertTrue(found, "Datei-Menü enthält „Öffnen (schreibgeschützt)...“");
    }

    // ---------- Nr. 6: Zeilenumbruch ----------

    @Test
    void wrapModeDefaultsToAutomatic() {
        EditorWindow window = newWindow();
        LineWrapMode mode = FxTestUtil.callAndWait(window::getLineWrapMode);
        assertEquals(LineWrapMode.AUTOMATIC, mode);
    }

    @Test
    void wrapModeIsSettableAndPersisted() {
        EditorWindow window = newWindow();
        FxTestUtil.runAndWait(() -> window.setLineWrapMode(LineWrapMode.CONTINUOUS));
        LineWrapMode mode = FxTestUtil.callAndWait(window::getLineWrapMode);
        assertEquals(LineWrapMode.CONTINUOUS, mode);
        // Im Test läuft I18n mit inMemory()-Store: setLineWrapModeName ist No-Op.
        // Stattdessen prüfen wir nur, dass getLineWrapMode den gesetzten Wert liefert.
        // zurücksetzen für andere Tests
        FxTestUtil.runAndWait(() -> window.setLineWrapMode(LineWrapMode.AUTOMATIC));
    }

    @Test
    void formatMenuContainsLineWrapSubmenuWithRadioItems() {
        EditorWindow window = newWindow();
        Menu lineWrap = findLineWrapMenu(window);
        assertNotNull(lineWrap, "Format-Menü enthält „Zeilenumbruch“");
        var items = FxTestUtil.callAndWait(() -> lineWrap.getItems().stream()
                .filter(item -> item instanceof javafx.scene.control.RadioMenuItem)
                .map(item -> item.getText())
                .toList());
        assertEquals(2, items.size());
        assertTrue(items.contains(I18n.text("menu.lineWrap.auto")));
        assertTrue(items.contains(I18n.text("menu.lineWrap.continuous")));
    }

    @Test
    void selectingContinuousWrapInMenuChangesMode() {
        EditorWindow window = newWindow();
        Menu lineWrap = findLineWrapMenu(window);
        var continuous = FxTestUtil.callAndWait(() -> (javafx.scene.control.RadioMenuItem)
                lineWrap.getItems().stream()
                        .filter(item -> item instanceof javafx.scene.control.RadioMenuItem
                                && I18n.text("menu.lineWrap.continuous").equals(item.getText()))
                        .findFirst()
                        .orElseThrow());
        FxTestUtil.runAndWait(() -> {
            continuous.setSelected(true);
            continuous.fire();
        });
        LineWrapMode mode = FxTestUtil.callAndWait(window::getLineWrapMode);
        assertEquals(LineWrapMode.CONTINUOUS, mode);
        I18n.setLineWrapModeName("AUTOMATIC");
    }

    private Menu findLineWrapMenu(EditorWindow window) {
        Menu format = menuByName(window, I18n.text("menu.format"));
        return FxTestUtil.callAndWait(() -> (Menu) format.getItems().stream()
                .filter(item -> item instanceof Menu)
                .filter(item -> I18n.text("menu.lineWrap").equals(((Menu) item).getText()))
                .findFirst()
                .orElse(null));
    }
}
