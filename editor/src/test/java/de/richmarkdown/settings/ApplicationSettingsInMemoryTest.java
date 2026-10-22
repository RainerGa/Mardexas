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

package de.richmarkdown.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AP-05: In-Memory-Store ohne Dateizugriff.
 */
class ApplicationSettingsInMemoryTest {

    @TempDir
    Path tempDir;

    @Test
    void inMemorySaveIsNoOpAndReadReturnsDefaults() {
        ApplicationSettings settings = ApplicationSettings.inMemory();
        settings.save("language", "fr");
        settings.saveBoolean("documentStructure", true);
        assertNull(settings.read("language"));
        assertFalse(settings.readBoolean("documentStructure", false));
        assertTrue(settings.readBoolean("missing", true));
    }

    @Test
    void inMemoryCreatesNoFile() throws Exception {
        ApplicationSettings settings = ApplicationSettings.inMemory();
        settings.save("language", "en");
        try (var stream = Files.list(tempDir)) {
            assertEquals(0, stream.count());
        }
    }
}
