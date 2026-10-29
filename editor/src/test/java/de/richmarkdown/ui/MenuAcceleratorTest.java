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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tastenkuerzel des Menues „Bearbeiten“.
 *
 * <p>Strg+F oeffnet den Suchen-&-Ersetzen-Dialog (wie in den meisten Editoren),
 * F3 wiederholt die letzte Suche ohne Dialog. Strg+H ist bewusst nicht belegt:
 * es gab sonst zwei Menueintraege, die beide zum selben Dialog fuehrten, weil
 * „Weitersuchen“ den Dialog beim ersten Aufruf ebenfalls oeffnet.
 */
class MenuAcceleratorTest {

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
        I18n.setSettingsStore(ApplicationSettings.inMemory());
        I18n.setCurrentLanguage("de");
        I18n.setDocumentStructureEnabled(false);
        I18n.setLineNumbersEnabled(false);
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

    private MenuItem itemByText(EditorWindow window, String text) {
        return FxTestUtil.callAndWait(() -> menuBar(window).getMenus().stream()
                .map(Menu::getItems)
                .flatMap(List::stream)
                .filter(item -> text.equals(item.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Menueeintrag nicht gefunden: " + text)));
    }

    private List<MenuItem> allItems(EditorWindow window) {
        return FxTestUtil.callAndWait(() -> menuBar(window).getMenus().stream()
                .map(Menu::getItems)
                .flatMap(List::stream)
                .toList());
    }

    /**
     * Prueft Tastenkuerzel (Taste und Modifikatoren) eines Menueeintrags.
     * Der Name deckt die Modifikatoren ab ("Ctrl+F"), der KeyCode die Taste.
     */
    private static boolean isBoundTo(MenuItem item, KeyCode code, String expectedName) {
        KeyCombination accelerator = item.getAccelerator();
        return accelerator instanceof KeyCodeCombination combination
                && combination.getCode() == code
                && expectedName.equals(accelerator.getName());
    }

    // ---------- Strg+F: Suchen & Ersetzen ----------

    @Test
    void findReplaceItemIsBoundToControlF() {
        EditorWindow window = newWindow();
        MenuItem find = itemByText(window, I18n.text("menu.findReplace"));
        KeyCombination accelerator = find.getAccelerator();
        assertNotNull(accelerator, "Suchen-&-Ersetzen-Eintrag hat kein Tastenkuerzel");
        assertTrue(isBoundTo(find, KeyCode.F, "Ctrl+F"),
                "Suchen & Ersetzen muss an Strg+F gebunden sein, ist aber: " + accelerator.getName());
        assertEquals("Ctrl+F", accelerator.getName());
    }

    // ---------- F3: Weitersuchen ----------

    @Test
    void findNextItemIsBoundToF3() {
        EditorWindow window = newWindow();
        MenuItem findNext = itemByText(window, I18n.text("menu.findNext"));
        KeyCombination accelerator = findNext.getAccelerator();
        assertNotNull(accelerator, "Weitersuchen-Eintrag hat kein Tastenkuerzel");
        assertTrue(isBoundTo(findNext, KeyCode.F3, "F3"),
                "Weitersuchen muss an F3 gebunden sein, ist aber: " + accelerator.getName());
        assertEquals("F3", accelerator.getName());
    }

    // ---------- Kein zweiter Einstieg in den Suchen-Dialog ----------

    @Test
    void controlHIsNotBoundToAnyMenuItem() {
        EditorWindow window = newWindow();
        List<MenuItem> withControlH = allItems(window).stream()
                .filter(item -> isBoundTo(item, KeyCode.H, "Ctrl+H"))
                .toList();
        assertTrue(withControlH.isEmpty(),
                "Strg+H darf nicht mehr belegt sein, da es sonst einen zweiten "
                        + "Einstieg in den Suchen-Dialog gibt: " + withControlH);
    }

    @Test
    void searchDialogHasExactlyOneAcceleratorEntry() {
        EditorWindow window = newWindow();
        long acceleratorsToDialog = allItems(window).stream()
                .filter(item -> item.getAccelerator() != null)
                .filter(item -> I18n.text("menu.findReplace").equals(item.getText())
                        || I18n.text("menu.findNext").equals(item.getText()))
                .count();
        assertEquals(2, acceleratorsToDialog,
                "Genau zwei Eintraege (Suchen & Ersetzen, Weitersuchen) duerfen ein "
                        + "Tastenkuerzel tragen");
        assertFalse(I18n.text("menu.findReplace").equals(I18n.text("menu.findNext")),
                "Die beiden Eintraege muessen unterschiedliche Beschriftungen haben");
    }
}
