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

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.i18n.I18n;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test für den Link-Bestätigungsdialog (LinkConfirmDialog).
 */
class LinkConfirmDialogTest {

    private Stage stage;

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
        I18n.setCurrentLanguage("en");
    }

    @BeforeEach
    void setUp() {
        FxTestUtil.runAndWait(() -> {
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(), 600, 400));
            stage.show();
        });
    }

    @AfterAll
    static void tearDown() {
        // kein stage.hide(): blockt den FX-Thread in Monocle headless
    }

    private void flush() {
        FxTestUtil.flushFxEvents();
    }

    @Test
    void dialogShowsUrlAndHasButtons() {
        String testUrl = "https://example.com";
        LinkConfirmDialog dialog = FxTestUtil.callAndWait(() -> new LinkConfirmDialog(testUrl));
        DialogPane pane = FxTestUtil.callAndWait(dialog::getDialogPane);

        assertNotNull(pane);
        assertEquals("Open Link", FxTestUtil.callAndWait(dialog::getTitle));
        assertTrue(FxTestUtil.callAndWait(dialog::getHeaderText).contains("browser"));

        // Buttons: Open (OK) und Cancel
        assertNotNull(pane.lookupButton(DialogButtons.OK));
        assertNotNull(pane.lookupButton(DialogButtons.CANCEL));
    }

    @Test
    void dialogReturnsOpenButtonTypeOnConfirm() {
        String testUrl = "https://example.com";
        LinkConfirmDialog dialog = FxTestUtil.callAndWait(() -> new LinkConfirmDialog(testUrl));

        Optional<ButtonType> result = FxTestUtil.callAndWait(() -> {
            dialog.setResult(DialogButtons.OK);
            return Optional.ofNullable(dialog.getResult());
        });

        assertTrue(result.isPresent());
        assertEquals(DialogButtons.OK, result.get());
    }

    @Test
    void dialogReturnsEmptyOnCancel() {
        String testUrl = "https://example.com";
        LinkConfirmDialog dialog = FxTestUtil.callAndWait(() -> new LinkConfirmDialog(testUrl));

        Optional<ButtonType> result = FxTestUtil.callAndWait(() -> {
            dialog.setResult(null);
            return Optional.ofNullable(dialog.getResult());
        });

        assertFalse(result.isPresent());
    }

    @Test
    void dialogUrlFieldIsNotEditable() {
        String testUrl = "https://example.com";
        LinkConfirmDialog dialog = FxTestUtil.callAndWait(() -> new LinkConfirmDialog(testUrl));
        DialogPane pane = FxTestUtil.callAndWait(dialog::getDialogPane);

        var urlField = (javafx.scene.control.TextField) pane.getContent().lookup(".text-field");
        assertNotNull(urlField);
        assertFalse(urlField.isEditable());
    }
}