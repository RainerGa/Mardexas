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
import de.richmarkdown.editor.DocumentStatistics;
import de.richmarkdown.i18n.I18n;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * TDD: Statistik-Dialog nach Vorbild LO „Extras → Statistik“
 * (AddFeatures #3).
 */
class StatisticsDialogTest {

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
        I18n.setCurrentLanguage("de");
    }

    @AfterAll
    static void tearDown() {
        // kein hide(): blockt den FX-Thread in Monocle headless
    }

    @Test
    void showsValuesFromSupplier() {
        StatisticsDialog dialog = FxTestUtil.callAndWait(() ->
                new StatisticsDialog(() -> DocumentStatistics.of("Hallo Welt. Zweiter Satz!", 1, 2)));
        assertEquals("4", FxTestUtil.callAndWait(
                () -> dialog.getValueLabel("dialog.statistics.words").getText()));
        assertEquals("1", FxTestUtil.callAndWait(
                () -> dialog.getValueLabel("dialog.statistics.tables").getText()));
        assertEquals("2", FxTestUtil.callAndWait(
                () -> dialog.getValueLabel("dialog.statistics.images").getText()));
    }

    @Test
    void updateButtonRecalculatesValues() {
        AtomicReference<DocumentStatistics> current =
                new AtomicReference<>(DocumentStatistics.of("Alt", 0, 0));
        StatisticsDialog dialog = FxTestUtil.callAndWait(() -> new StatisticsDialog(current::get));
        assertEquals("1", FxTestUtil.callAndWait(
                () -> dialog.getValueLabel("dialog.statistics.words").getText()));

        current.set(DocumentStatistics.of("Neu und mehr Wörter", 3, 0));
        FxTestUtil.runAndWait(dialog.getUpdateButton()::fire);
        assertEquals("4", FxTestUtil.callAndWait(
                () -> dialog.getValueLabel("dialog.statistics.words").getText()));
        assertEquals("3", FxTestUtil.callAndWait(
                () -> dialog.getValueLabel("dialog.statistics.tables").getText()));
    }

    @Test
    void hasUpdateAndCloseControls() {
        StatisticsDialog dialog = FxTestUtil.callAndWait(() ->
                new StatisticsDialog(DocumentStatistics::empty));
        assertNotNull(FxTestUtil.callAndWait(dialog::getUpdateButton));
        assertNotNull(FxTestUtil.callAndWait(() ->
                dialog.getDialogPane().lookupButton(DialogButtons.CLOSE)));
    }

    @Test
    void showsLocalisedLabels() {
        StatisticsDialog dialog = FxTestUtil.callAndWait(() ->
                new StatisticsDialog(DocumentStatistics::empty));
        String header = FxTestUtil.callAndWait(dialog::getHeaderText);
        assertEquals(I18n.text("dialog.statistics.header"), header);
    }
}
