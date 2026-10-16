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

class ApplicationSettingsTest {

    @TempDir
    Path tempDirectory;

    @Test
    void storesLanguageInApplicationSpecificSettingsFile() throws Exception {
        ApplicationSettings settings = new ApplicationSettings(tempDirectory);

        settings.save("language", "fr");
        settings.saveBoolean("documentStructure", true);
        settings.saveBoolean("lineNumbers", false);

        assertEquals("fr", settings.read("language"));
        assertEquals(true, settings.readBoolean("documentStructure", false));
        assertEquals(false, settings.readBoolean("lineNumbers", true));
        assertEquals("fr", Files.readString(tempDirectory.resolve(
                "RichMarkDownEditor/settings.properties")).lines()
                .filter(line -> line.startsWith("language="))
                .map(line -> line.substring("language=".length()))
                .findFirst()
                .orElseThrow());
    }

    @Test
    void missingViewPreferencesDefaultToDisabledAndSavingOneKeepsOtherValues() {
        ApplicationSettings settings = new ApplicationSettings(tempDirectory);
        assertEquals(false, settings.readBoolean("documentStructure", false));
        assertEquals(false, settings.readBoolean("lineNumbers", false));

        settings.save("language", "ja");
        settings.saveBoolean("documentStructure", true);

        assertEquals("ja", settings.read("language"));
        assertEquals(true, settings.readBoolean("documentStructure", false));
        assertEquals(false, settings.readBoolean("lineNumbers", false));
    }

    @Test
    void usesPlatformConfigurationDirectories() {
        assertEquals(Path.of("/home/test/.config"),
                ApplicationSettings.defaultConfigDirectory("Linux", "/home/test", "", ""));
        assertEquals(Path.of("/custom/config"),
                ApplicationSettings.defaultConfigDirectory("Linux", "/home/test",
                        "/custom/config", ""));
        assertEquals(Path.of("C:/Users/test/AppData/Roaming"),
                ApplicationSettings.defaultConfigDirectory("Windows 11",
                        "C:/Users/test", "", ""));
        assertEquals(Path.of("C:/custom/appdata"),
                ApplicationSettings.defaultConfigDirectory("Windows 11",
                        "C:/Users/test", "", "C:/custom/appdata"));
        assertEquals(Path.of("/Users/test/Library/Application Support"),
                ApplicationSettings.defaultConfigDirectory("Mac OS X", "/Users/test", "", ""));
    }
}
