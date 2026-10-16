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
import de.richmarkdown.ui.TableSize;
import javafx.scene.control.DialogPane;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD: Dialoge zum Einfügen von Bildern, Code-Blöcken und Tabellen
 * (PROJEKTPLAN 7.2, Phase 13).
 */
class InsertDialogsTest {

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    @AfterAll
    static void tearDown() {
        // kein hide(): blockiert den FX-Thread in Monocle headless
    }

    @Test
    void imageDialogHasUrlField() {
        ImageDialog dialog = FxTestUtil.callAndWait(ImageDialog::new);
        DialogPane pane = FxTestUtil.callAndWait(dialog::getDialogPane);
        assertNotNull(pane);
        assertNotNull(FxTestUtil.callAndWait(dialog::getUrlField));
        assertNotNull(pane.lookupButton(DialogButtons.OK));
        assertNotNull(pane.lookupButton(DialogButtons.CANCEL));
    }

    @Test
    void imageDialogReturnsUrl() {
        ImageDialog dialog = FxTestUtil.callAndWait(ImageDialog::new);
        FxTestUtil.runAndWait(() -> dialog.getUrlField().setText("images/pic.png"));
        Optional<String> result = FxTestUtil.callAndWait(() -> {
            dialog.setResult("images/pic.png");
            return Optional.of(dialog.getResult());
        });
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("pic.png"));
    }

    @Test
    void imageDialogCancelReturnsEmpty() {
        ImageDialog dialog = FxTestUtil.callAndWait(ImageDialog::new);
        Optional<String> result = FxTestUtil.callAndWait(() -> {
            dialog.setResult(null);
            return Optional.ofNullable(dialog.getResult());
        });
        assertFalse(result.isPresent());
    }

    @Test
    void codeBlockDialogHasTextArea() {
        CodeBlockDialog dialog = FxTestUtil.callAndWait(CodeBlockDialog::new);
        DialogPane pane = FxTestUtil.callAndWait(dialog::getDialogPane);
        assertNotNull(pane);
        assertNotNull(FxTestUtil.callAndWait(dialog::getCodeArea));
        assertNotNull(pane.lookupButton(DialogButtons.OK));
        assertNotNull(pane.lookupButton(DialogButtons.CANCEL));
    }

    @Test
    void codeBlockDialogReturnsCode() {
        CodeBlockDialog dialog = FxTestUtil.callAndWait(CodeBlockDialog::new);
        FxTestUtil.runAndWait(() -> dialog.getCodeArea().setText("int x = 1;"));
        Optional<String> result = FxTestUtil.callAndWait(() -> {
            dialog.setResult("int x = 1;");
            return Optional.of(dialog.getResult());
        });
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("int x = 1;"));
    }

    @Test
    void codeBlockDialogCancelReturnsEmpty() {
        CodeBlockDialog dialog = FxTestUtil.callAndWait(CodeBlockDialog::new);
        Optional<String> result = FxTestUtil.callAndWait(() -> {
            dialog.setResult(null);
            return Optional.ofNullable(dialog.getResult());
        });
        assertFalse(result.isPresent());
    }

    @Test
    void dialogsExposeControls() {
        ImageDialog image = FxTestUtil.callAndWait(ImageDialog::new);
        CodeBlockDialog code = FxTestUtil.callAndWait(CodeBlockDialog::new);
        assertNotNull(image.getUrlField());
        assertNotNull(code.getCodeArea());
    }

    // ---------- Tabelle einfügen (Phase 13: native Tabellen-Darstellung) ----------

    @Test
    void tableDialogHasRowsAndColumnsFields() {
        TableDialog dialog = FxTestUtil.callAndWait(TableDialog::new);
        DialogPane pane = FxTestUtil.callAndWait(dialog::getDialogPane);
        assertNotNull(pane);
        assertNotNull(FxTestUtil.callAndWait(dialog::getRowsSpinner));
        assertNotNull(FxTestUtil.callAndWait(dialog::getColumnsSpinner));
        assertNotNull(pane.lookupButton(DialogButtons.OK));
        assertNotNull(pane.lookupButton(DialogButtons.CANCEL));
    }

    @Test
    void tableDialogReturnsChosenSize() {
        TableDialog dialog = FxTestUtil.callAndWait(TableDialog::new);
        FxTestUtil.runAndWait(() -> {
            dialog.getRowsSpinner().getValueFactory().setValue(4);
            dialog.getColumnsSpinner().getValueFactory().setValue(2);
        });
        Optional<TableSize> result = FxTestUtil.callAndWait(() -> {
            dialog.setResult(new TableSize(
                    dialog.getRowsSpinner().getValue(),
                    dialog.getColumnsSpinner().getValue()));
            return Optional.of(dialog.getResult());
        });
        assertTrue(result.isPresent());
        assertEquals(4, result.get().rows());
        assertEquals(2, result.get().columns());
    }

    @Test
    void tableDialogCancelReturnsEmpty() {
        TableDialog dialog = FxTestUtil.callAndWait(TableDialog::new);
        Optional<TableSize> result = FxTestUtil.callAndWait(() -> {
            dialog.setResult(null);
            return Optional.ofNullable(dialog.getResult());
        });
        assertFalse(result.isPresent());
    }

    @Test
    void tableDialogDefaultsToThreeByThree() {
        TableDialog dialog = FxTestUtil.callAndWait(TableDialog::new);
        assertEquals(3, FxTestUtil.callAndWait(() -> dialog.getRowsSpinner().getValue()));
        assertEquals(3, FxTestUtil.callAndWait(() -> dialog.getColumnsSpinner().getValue()));
    }
}
