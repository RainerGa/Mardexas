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

package de.richmarkdown.i18n;

import de.richmarkdown.settings.ApplicationSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AP-04: I18n bleibt prozessglobal, ist aber konfigurierbar.
 */
class I18nConfigTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void resetStore() {
        I18n.resetSettingsStore();
        I18n.setCurrentLanguage("de");
    }

    @Test
    void setCurrentLanguageSwitchesBundleText() {
        I18n.setCurrentLanguage("en");
        assertEquals("File", I18n.text("menu.file"));
        I18n.setCurrentLanguage("de");
        assertEquals("Datei", I18n.text("menu.file"));
    }

    @Test
    void unsupportedLanguageThrows() {
        assertThrows(IllegalArgumentException.class, () -> I18n.setCurrentLanguage("xx"));
    }

    @Test
    void nullStoreDisablesPersistence() {
        I18n.setSettingsStore(null);
        // In-Memory: Speichern ist No-Op, Lesen liefert Defaults
        I18n.setDocumentStructureEnabled(true);
        org.junit.jupiter.api.Assertions.assertFalse(I18n.documentStructureEnabled(), "in-memory store must not persist");
    }

    @Test
    void customStoreIsUsed() {
        ApplicationSettings custom = new ApplicationSettings(tempDir);
        I18n.setSettingsStore(custom);
        I18n.setLineNumbersEnabled(true);
        assertTrue(I18n.lineNumbersEnabled());
        assertTrue(custom.readBoolean("lineNumbers", false));
        I18n.setLineNumbersEnabled(false);
    }

    @Test
    void resetRestoresDefaultStore() {
        I18n.setSettingsStore(ApplicationSettings.inMemory());
        I18n.resetSettingsStore();
        I18n.setZoomPercent(100d);
    }
}
