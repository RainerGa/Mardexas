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
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.PrintColor;
import javafx.print.PrintSides;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * TDD: JavaFX-nativer Druckdialog ohne AWT/Swing (ersetzt
 * {@code PrinterJob.showPrintDialog()}).
 */
class PrintDialogTest {

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    @AfterAll
    static void tearDown() {
        // kein hide(): blockiert den FX-Thread in Monocle headless
    }

    @Test
    void dialogExposesAllControls() {
        PrintDialog dialog = FxTestUtil.callAndWait(
                () -> new PrintDialog(List.of(), null));
        assertNotNull(FxTestUtil.callAndWait(dialog::getPrinterBox));
        assertNotNull(FxTestUtil.callAndWait(dialog::getCopiesSpinner));
        assertNotNull(FxTestUtil.callAndWait(dialog::getOrientationBox));
        assertNotNull(FxTestUtil.callAndWait(dialog::getPaperBox));
        assertNotNull(FxTestUtil.callAndWait(dialog::getSidesBox));
        assertNotNull(FxTestUtil.callAndWait(dialog::getColorBox));
    }

    @Test
    void dialogHasPrintAndCancelButtons() {
        PrintDialog dialog = FxTestUtil.callAndWait(
                () -> new PrintDialog(List.of(), null));
        var pane = FxTestUtil.callAndWait(dialog::getDialogPane);
        assertNotNull(pane.lookupButton(DialogButtons.PRINT));
        assertNotNull(pane.lookupButton(DialogButtons.CANCEL));
    }

    @Test
    void dialogDefaultsToSingleCopyPortraitA4() {
        PrintDialog dialog = FxTestUtil.callAndWait(
                () -> new PrintDialog(List.of(), null));
        assertEquals(1, FxTestUtil.callAndWait(
                () -> dialog.getCopiesSpinner().getValue()));
        assertEquals(PageOrientation.PORTRAIT, FxTestUtil.callAndWait(
                () -> dialog.getOrientationBox().getValue()));
        assertEquals(Paper.A4, FxTestUtil.callAndWait(
                () -> dialog.getPaperBox().getValue()));
        assertEquals(PrintSides.ONE_SIDED, FxTestUtil.callAndWait(
                () -> dialog.getSidesBox().getValue()));
        assertEquals(PrintColor.COLOR, FxTestUtil.callAndWait(
                () -> dialog.getColorBox().getValue()));
    }

    @Test
    void cancelReturnsNull() {
        PrintDialog dialog = FxTestUtil.callAndWait(
                () -> new PrintDialog(List.of(), null));
        PrintDialogResult result = FxTestUtil.callAndWait(() -> {
            dialog.setResult(null);
            return dialog.getResult();
        });
        assertNull(result);
    }
}
