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

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Liest und schreibt anwendungsspezifische Einstellungen in einer
 * {@code settings.properties}-Datei im plattformüblichen Benutzerkonfigurations-
 * verzeichnis.
 *
 * <p>Die Klasse kapselt den Dateipfad und erhält beim Ändern eines einzelnen
 * Werts sämtliche übrigen gespeicherten Eigenschaften. Fehlende Dateien werden
 * als leere Konfiguration behandelt; Datei-I/O-Fehler werden ausdrücklich als
 * {@link java.io.UncheckedIOException} weitergegeben.
 *
 * <p>Varianten: Pfad-Konstruktion via
 * {@link #defaultConfigDirectory(String, String, String, String)} (Plattform-
 * Default) vs. eigene {@code Path}-Injektion (Integrator-Verzeichnis) vs.
 * {@link #inMemory()} (keine Persistenz). Globaler Umschaltpunkt ist
 * {@code I18n.setSettingsStore(...)} (AP-04).
 *
 * @see #defaultConfigDirectory(String, String, String, String)
 * @see #inMemory()
 */
public final class ApplicationSettings {

    private static final String FILE_NAME = "settings.properties";
    private static final String APPLICATION_DIRECTORY = "RichMarkDownEditor";

    private final Path settingsFile;
    private final boolean inMemory;

    /**
     * Erstellt einen Einstellungszugriff im angegebenen Basiskonfigurationsordner.
     *
     * <p>Die Datei wird unter
     * {@code configDirectory/RichMarkDownEditor/settings.properties} abgelegt.
     * Das Verzeichnis wird erst beim ersten Schreibzugriff erzeugt.
     *
     * @param configDirectory plattformabhängiger Konfigurationsordner des Benutzers
     */
    public ApplicationSettings(Path configDirectory) {
        this.settingsFile = configDirectory.resolve(APPLICATION_DIRECTORY).resolve(FILE_NAME);
        this.inMemory = false;
    }

    private ApplicationSettings(boolean inMemory) {
        this.settingsFile = null;
        this.inMemory = inMemory;
    }

    /**
     * Erzeugt einen Null-Object-Store ohne Dateizugriff (AP-05): {@code read}
     * liefert {@code null}/Default, {@code save} ist No-Op. Für Integratoren,
     * die keine Persistenz wollen – global via
     * {@code I18n.setSettingsStore(ApplicationSettings.inMemory())}.
     *
     * @return speicherloser Einstellungsstore
     */
    public static ApplicationSettings inMemory() {
        return new ApplicationSettings(true);
    }

    /**
     * Ermittelt den plattformüblichen Basiskonfigurationsordner.
     *
     * @param osName Betriebssystemname, üblicherweise {@code os.name}
     * @param userHome Benutzer-Home-Verzeichnis
     * @param xdgConfigHome optionaler Wert von {@code XDG_CONFIG_HOME}
     * @param appData optionaler Wert von {@code APPDATA}
     * @return Basispfad ohne anwendungsspezifisches Unterverzeichnis
     */
    public static Path defaultConfigDirectory(String osName, String userHome, String xdgConfigHome,
                                              String appData) {
        if (osName.startsWith("Windows")) {
            return nonEmptyPath(appData).orElseGet(() ->
                    Path.of(userHome, "AppData", "Roaming"));
        }
        if (osName.startsWith("Mac")) {
            return Path.of(userHome, "Library", "Application Support");
        }
        return nonEmptyPath(xdgConfigHome).orElseGet(() -> Path.of(userHome, ".config"));
    }

    /**
     * Liest einen Zeichenkettenwert aus der Einstellungsdatei.
     *
     * @param key Eigenschaftsschlüssel
     * @return gespeicherter Wert oder {@code null}, wenn Datei oder Schlüssel fehlen
     */
    public String read(String key) {
        return readProperties().getProperty(key);
    }

    /**
     * Liest einen booleschen Wert und verwendet bei fehlendem Schlüssel den Default.
     *
     * @param key Eigenschaftsschlüssel
     * @param defaultValue Rückgabewert, wenn kein Wert gespeichert wurde
     * @return gespeicherter boolescher Wert oder {@code defaultValue}
     */
    public boolean readBoolean(String key, boolean defaultValue) {
        String value = read(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    /**
     * Speichert einen Zeichenkettenwert, ohne andere Einstellungen zu entfernen.
     *
     * @param key Eigenschaftsschlüssel
     * @param value zu speichernder Wert
     * @throws java.io.UncheckedIOException wenn die Einstellungsdatei nicht
     *                                      gelesen oder geschrieben werden kann
     */
    public void save(String key, String value) {
        if (inMemory) {
            return;
        }
        Properties properties = readProperties();
        properties.setProperty(key, value);
        writeProperties(properties);
    }

    /**
     * Speichert einen booleschen Wert, ohne andere Einstellungen zu entfernen.
     *
     * @param key Eigenschaftsschlüssel
     * @param value zu speichernder Zustand
     */
    public void saveBoolean(String key, boolean value) {
        save(key, Boolean.toString(value));
    }

    private Properties readProperties() {
        Properties properties = new Properties();
        if (inMemory || settingsFile == null || !Files.exists(settingsFile)) {
            return properties;
        }
        try (Reader reader = Files.newBufferedReader(settingsFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + settingsFile, e);
        }
    }

    private void writeProperties(Properties properties) {
        try {
            Files.createDirectories(settingsFile.getParent());
            try (Writer writer = Files.newBufferedWriter(settingsFile, StandardCharsets.UTF_8)) {
                properties.store(writer, "RichMarkDownEditor settings");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + settingsFile, e);
        }
    }

    private static Optional<Path> nonEmptyPath(String value) {
        return value == null || value.isBlank()
                ? Optional.empty() : Optional.of(Path.of(value));
    }

    /**
     * Liest die Liste der zuletzt geöffneten Dateien.
     *
     * @return Liste der Dateipfade (maximal 10), neueste zuerst
     */
    public List<String> readRecentFiles() {
        List<String> files = new ArrayList<>();
        if (inMemory || settingsFile == null || !Files.exists(settingsFile)) {
            return files;
        }
        try (Reader reader = Files.newBufferedReader(settingsFile, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            for (int i = 0; i < 10; i++) {
                String path = properties.getProperty("recentFile." + i);
                if (path != null && !path.isBlank()) {
                    files.add(path);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + settingsFile, e);
        }
        return files;
    }

    /**
     * Fügt eine Datei zur Liste der zuletzt geöffneten Dateien hinzu.
     * Die Datei wird an den Anfang gesetzt, Duplikate entfernt, Liste auf 10 begrenzt.
     *
     * @param filePath Pfad der Datei
     */
    public void addRecentFile(String filePath) {
        if (inMemory) {
            return;
        }
        List<String> files = readRecentFiles();
        files.remove(filePath);
        files.add(0, filePath);
        if (files.size() > 10) {
            files = files.subList(0, 10);
        }
        Properties properties = readProperties();
        // Clear existing recent file entries
        for (int i = 0; i < 10; i++) {
            properties.remove("recentFile." + i);
        }
        // Write updated list
        for (int i = 0; i < files.size(); i++) {
            properties.setProperty("recentFile." + i, files.get(i));
        }
        writeProperties(properties);
    }

    /**
     * Entfernt eine Datei aus der Liste der zuletzt geöffneten Dateien.
     *
     * @param filePath Pfad der zu entfernenden Datei
     */
    public void removeRecentFile(String filePath) {
        if (inMemory) {
            return;
        }
        List<String> files = readRecentFiles();
        files.remove(filePath);
        Properties properties = readProperties();
        for (int i = 0; i < 10; i++) {
            properties.remove("recentFile." + i);
        }
        for (int i = 0; i < files.size(); i++) {
            properties.setProperty("recentFile." + i, files.get(i));
        }
        writeProperties(properties);
    }
}
