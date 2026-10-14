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
