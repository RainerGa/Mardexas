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
import java.util.Locale;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;
import java.util.ArrayList;

/**
 * Zentraler Zugriffspunkt für Übersetzungen, Gebietsschema und dauerhaft
 * gespeicherte Anzeigeeinstellungen.
 *
 * <p>Texte werden aus den Ressourcen {@code de.richmarkdown.i18n.messages} und
 * deren sprachspezifischen Varianten geladen. Beim ersten Start wird eine
 * gespeicherte Sprachauswahl bevorzugt; andernfalls wird die Betriebssystem-
 * Locale auf eine unterstützte Sprache abgebildet. Ist keine Zuordnung möglich,
 * signalisiert {@link #initializeForStartup(Locale)} der Anwendung, dass vor
 * dem Öffnen des Editors eine Sprachwahl erforderlich ist.
 *
 * <p>Die aktuelle Locale und das zugehörige Resource-Bundle sind für Zugriffe
 * aus mehreren UI-Komponenten als {@code volatile} veröffentlicht. Änderungen
 * an der Sprache erfolgen synchronisiert. Anwendungseinstellungen werden über
 * {@link ApplicationSettings} außerhalb des Arbeitsverzeichnisses gespeichert;
 * der frühere Preferences-Knoten wird ausschließlich zur Migration der
 * bisherigen Spracheinstellung gelesen.
 *
 * <p><b>Prozessglobal (Nicht-Ziel, bewusst kein Instanz-I18n):</b> Ein
 * Integrator setzt die Sprache einmalig beim Start
 * ({@link #setCurrentLanguage(String)}); parallele Locales pro Fenster werden
 * nicht unterstützt. Der Settings-Store ist global umschaltbar
 * ({@link #setSettingsStore(ApplicationSettings)}/
 * {@link #resetSettingsStore()}), z. B. auf
 * {@link ApplicationSettings#inMemory()} oder ein eigenes Verzeichnis.
 *
 * @see ApplicationSettings
 */
public final class I18n {

    private static final String BUNDLE_NAME = "de.richmarkdown.i18n.messages";
    private static final String LANGUAGE_KEY = "language";
    private static final String DOCUMENT_STRUCTURE_KEY = "documentStructure";
    private static final String LINE_NUMBERS_KEY = "lineNumbers";
    private static final String ZOOM_PERCENT_KEY = "zoomPercent";
    private static final String LINE_WRAP_MODE_KEY = "lineWrapMode";
    private static final String RECENT_FILES_KEY_PREFIX = "recentFile.";
    private static final int MAX_RECENT_FILES = 10;
    private static final Preferences LEGACY_PREFERENCES =
            Preferences.userRoot().node("de/kilocode/richmarkdown/i18n");
    private static volatile ApplicationSettings settingsStore = defaultSettingsStore();
    private static final List<String> SUPPORTED_LANGUAGES =
            List.of("de", "en", "fr", "es", "pt-BR", "ja", "zh-CN", "it", "ko", "nl");
    private static volatile Locale locale = Locale.GERMAN;
    private static volatile ResourceBundle bundle = loadBundle(locale);
    private static volatile boolean initialLanguageSelectionRequired;

    private I18n() {
    }

    /**
     * Liefert den lokalisierten Text für einen Ressourcenschlüssel der aktuellen Sprache.
     *
     * @param key Schlüssel aus dem Nachrichten-Resource-Bundle
     * @return übersetzter Text
     * @throws IllegalArgumentException wenn der Schlüssel im Bundle fehlt
     */
    public static String text(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            throw new IllegalArgumentException("Missing localization key: " + key, e);
        }
    }

    /**
     * Liefert das aktuell aktive Gebietsschema.
     *
     * @return aktive Locale für Texte und Lokalisierung
     */
    public static Locale locale() {
        return locale;
    }

    /**
     * Gibt an, ob die aktuell ausgewählte Sprache Englisch ist.
     *
     * @return {@code true}, wenn das aktive Locale den Sprachcode {@code en} hat
     */
    public static boolean isEnglish() {
        return Locale.ENGLISH.getLanguage().equals(locale.getLanguage());
    }

    /**
     * Liefert die unterstützten Sprachcodes in der Reihenfolge des Sprachmenüs.
     *
     * @return unveränderliche Liste mit BCP-47-kompatiblen Sprachcodes
     */
    public static List<String> supportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    /**
     * Liefert den kanonischen Sprachcode der aktiven Locale.
     *
     * @return unterstützter Sprachcode oder {@code de}, falls keine exakte
     *         Zuordnung vorhanden ist
     */
    public static String currentLanguageCode() {
        String languageTag = locale.toLanguageTag();
        return SUPPORTED_LANGUAGES.stream()
                .filter(code -> localeForLanguage(code).toLanguageTag().equals(languageTag))
                .findFirst()
                .orElse("de");
    }

    /**
     * Initialisiert die Sprache anhand gespeicherter Auswahl oder System-Locale.
     *
     * <p>Die gespeicherte Auswahl hat Vorrang. Fehlt sie, wird eine unterstützte
     * System-Locale übernommen und für künftige Starts gespeichert. Eine nicht
     * unterstützte System-Locale aktiviert Englisch als vorläufige Anzeige und
     * erfordert eine explizite Auswahl durch den Benutzer.
     *
     * @param systemLocale vom Betriebssystem gemeldete Locale; {@code null} gilt
     *                     als nicht unterstützte Locale
     * @return {@code true}, wenn die Anwendung vor dem Editorstart eine
     *         Sprachauswahl anzeigen soll
     * @throws IllegalArgumentException wenn eine gespeicherte Sprache nicht
     *                                  mehr unterstützt wird
     */
    public static synchronized boolean initializeForStartup(Locale systemLocale) {
        String savedLanguage = settingsStore.read(LANGUAGE_KEY);
        if (savedLanguage == null) {
            savedLanguage = migrateLegacyLanguagePreference();
        }
        if (savedLanguage != null) {
            requireSupportedLanguage(savedLanguage);
            setCurrentLanguage(savedLanguage);
            initialLanguageSelectionRequired = false;
        } else {
            Optional<String> systemLanguage = languageForSystemLocale(systemLocale);
            if (systemLanguage.isPresent()) {
                setCurrentLanguage(systemLanguage.get());
                settingsStore.save(LANGUAGE_KEY, systemLanguage.get());
                initialLanguageSelectionRequired = false;
            } else {
                setCurrentLanguage("en");
                initialLanguageSelectionRequired = true;
            }
        }
        return initialLanguageSelectionRequired;
    }

    /**
     * Gibt an, ob beim aktuellen Start noch die erstmalige Sprachwahl aussteht.
     *
     * @return {@code true}, wenn die System-Locale nicht unterstützt wurde und
     *         noch keine Auswahl bestätigt ist
     */
    public static boolean isInitialLanguageSelectionRequired() {
        return initialLanguageSelectionRequired;
    }

    /**
     * Aktiviert und speichert die Sprache aus dem Dialog beim ersten Start.
     *
     * @param language unterstützter Sprachcode
     * @throws IllegalArgumentException wenn der Sprachcode nicht unterstützt wird
     */
    public static synchronized void selectInitialLanguage(String language) {
        requireSupportedLanguage(language);
        setCurrentLanguage(language);
        settingsStore.save(LANGUAGE_KEY, language);
        initialLanguageSelectionRequired = false;
    }

    /**
     * Speichert die Sprache für den nächsten Anwendungsstart.
     *
     * <p>Diese Methode ändert die aktuell geladene Locale nicht. Für die
     * Umschaltung während der laufenden Sitzung ist zusätzlich die vorhandene
     * UI-Sprachwechsel-Logik zuständig.
     *
     * @param language unterstützter Sprachcode
     * @throws IllegalArgumentException wenn der Sprachcode nicht unterstützt wird
     */
    public static void setLanguagePreference(String language) {
        requireSupportedLanguage(language);
        settingsStore.save(LANGUAGE_KEY, language);
    }

    /**
     * Setzt den globalen Settings-Store um (AP-04, Integrator-Konfiguration).
     *
     * <p>{@code null} deaktiviert die Persistenz (wie
     * {@link ApplicationSettings#inMemory()}); synchronisiert, prozessglobal.
     * Für Tests {@link #resetSettingsStore()} nutzen.
     *
     * @param store neuer Store oder {@code null} für In-Memory-Deaktivierung
     */
    public static synchronized void setSettingsStore(ApplicationSettings store) {
        settingsStore = store != null ? store : ApplicationSettings.inMemory();
    }

    /**
     * Stellt den Plattform-Default-Store wieder her (für Tests).
     */
    public static synchronized void resetSettingsStore() {
        settingsStore = defaultSettingsStore();
    }

    private static ApplicationSettings defaultSettingsStore() {
        return new ApplicationSettings(
                ApplicationSettings.defaultConfigDirectory(
                        System.getProperty("os.name", ""),
                        System.getProperty("user.home", ""),
                        System.getenv("XDG_CONFIG_HOME"),
                        System.getenv("APPDATA")));
    }

    /**
     * Gibt an, ob die Dokumentstruktur beim letzten Speichern aktiviert war.
     *
     * @return gespeicherter Zustand; standardmäßig {@code false}
     */
    public static boolean documentStructureEnabled() {
        return settingsStore.readBoolean(DOCUMENT_STRUCTURE_KEY, false);
    }

    /**
     * Speichert den Sichtbarkeitszustand der Dokumentstruktur.
     *
     * @param enabled {@code true}, wenn die Dokumentstruktur angezeigt werden soll
     */
    public static void setDocumentStructureEnabled(boolean enabled) {
        settingsStore.saveBoolean(DOCUMENT_STRUCTURE_KEY, enabled);
    }

    /**
     * Gibt an, ob Zeilennummern beim letzten Speichern aktiviert waren.
     *
     * @return gespeicherter Zustand; standardmäßig {@code false}
     */
    public static boolean lineNumbersEnabled() {
        return settingsStore.readBoolean(LINE_NUMBERS_KEY, false);
    }

    /**
     * Speichert den Sichtbarkeitszustand der Zeilennummern.
     *
     * @param enabled {@code true}, wenn Zeilennummern angezeigt werden sollen
     */
    public static void setLineNumbersEnabled(boolean enabled) {
        settingsStore.saveBoolean(LINE_NUMBERS_KEY, enabled);
    }

    /**
     * Liefert den gespeicherten Zoomfaktor in Prozent.
     *
     * @return gespeicherter Prozentwert oder {@code 100}, falls noch keiner
     *         gespeichert wurde
     * @throws IllegalStateException wenn der gespeicherte Wert keine endliche
     *                               Zahl ist oder nicht geparst werden kann
     */
    public static double zoomPercent() {
        String value = settingsStore.read(ZOOM_PERCENT_KEY);
        if (value == null) {
            return 100d;
        }
        try {
            double percent = Double.parseDouble(value);
            if (Double.isFinite(percent)) {
                return percent;
            }
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid saved zoom percentage: " + value, e);
        }
        throw new IllegalStateException("Invalid saved zoom percentage: " + value);
    }

    /**
     * Speichert den Zoomfaktor für den nächsten Fensterstart.
     *
     * @param percent Zoomfaktor in Prozent; Bereichsbegrenzungen werden vom
     *                Zoom-Controller angewandt
     * @throws IllegalArgumentException wenn der Wert NaN oder unendlich ist
     */
    public static void setZoomPercent(double percent) {
        if (!Double.isFinite(percent)) {
            throw new IllegalArgumentException("Zoom percentage must be finite: " + percent);
        }
        settingsStore.save(ZOOM_PERCENT_KEY, Double.toString(percent));
    }

    /**
     * Liefert den gespeicherten Zeilenumbruch-Modus als Enum-Namen
     * („AUTOMATIC“ oder „CONTINUOUS“, AddFeatures #6).
     *
     * <p>Die Persistenz speichert bewusst nur den Namen (keine UI-Abhängigkeit
     * des I18n-Packages); die Zuordnung zum Enum erfolgt in der UI-Schicht.
     *
     * @return gespeicherter Modus-Name oder {@code AUTOMATIC}, falls noch
     *         keiner gespeichert wurde (LO-Default)
     * @throws IllegalStateException wenn der gespeicherte Name kein bekannter
     *                               Zeilenumbruch-Modus ist
     */
    public static String lineWrapModeName() {
        String value = settingsStore.read(LINE_WRAP_MODE_KEY);
        if (value == null) {
            return "AUTOMATIC";
        }
        if ("AUTOMATIC".equals(value) || "CONTINUOUS".equals(value)) {
            return value;
        }
        throw new IllegalStateException("Invalid saved line wrap mode: " + value);
    }

    /**
     * Speichert den Zeilenumbruch-Modus für den nächsten Fensterstart.
     *
     * @param modeName Enum-Name des Modus ({@code AUTOMATIC} oder
     *                 {@code CONTINUOUS}); {@code null} wird abgelehnt
     * @throws IllegalArgumentException wenn der Name kein bekannter
     *                                  Zeilenumbruch-Modus ist
     */
    public static void setLineWrapModeName(String modeName) {
        if (!"AUTOMATIC".equals(modeName) && !"CONTINUOUS".equals(modeName)) {
            throw new IllegalArgumentException("Unknown line wrap mode: " + modeName);
        }
        settingsStore.save(LINE_WRAP_MODE_KEY, modeName);
    }

    /**
     * Liefert die Liste der zuletzt geöffneten Dateien (maximal 10, neueste zuerst).
     *
     * @return Liste der Dateipfade
     */
    public static List<String> getRecentFiles() {
        return settingsStore.readRecentFiles();
    }

    /**
     * Fügt eine Datei zur Liste der zuletzt geöffneten Dateien hinzu.
     *
     * @param filePath Pfad der Datei
     */
    public static void addRecentFile(String filePath) {
        settingsStore.addRecentFile(filePath);
    }

    /**
     * Entfernt eine Datei aus der Liste der zuletzt geöffneten Dateien.
     *
     * @param filePath Pfad der zu entfernenden Datei
     */
    public static void removeRecentFile(String filePath) {
        settingsStore.removeRecentFile(filePath);
    }

    static Optional<String> languageForSystemLocale(Locale systemLocale) {
        if (systemLocale == null) {
            return Optional.empty();
        }
        String language = systemLocale.getLanguage();
        if ("zh".equals(language)) {
            String country = systemLocale.getCountry();
            return country.isEmpty() || "CN".equals(country) || "SG".equals(country)
                    ? Optional.of("zh-CN") : Optional.empty();
        }
        if ("pt".equals(language)) {
            return Optional.of("pt-BR");
        }
        return SUPPORTED_LANGUAGES.stream()
                .filter(code -> localeForLanguage(code).getLanguage().equals(language))
                .findFirst();
    }

    private static void requireSupportedLanguage(String language) {
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            throw new IllegalArgumentException("Unsupported language: " + language);
        }
    }

    public static synchronized void setCurrentLanguage(String language) {
        requireSupportedLanguage(language);
        locale = localeForLanguage(language);
        bundle = loadBundle(locale);
    }

    private static String migrateLegacyLanguagePreference() {
        String legacyLanguage = LEGACY_PREFERENCES.get(LANGUAGE_KEY, null);
        if (legacyLanguage == null) {
            return null;
        }
        if (!SUPPORTED_LANGUAGES.contains(legacyLanguage)) {
            LEGACY_PREFERENCES.remove(LANGUAGE_KEY);
            return null;
        }
        settingsStore.save(LANGUAGE_KEY, legacyLanguage);
        LEGACY_PREFERENCES.remove(LANGUAGE_KEY);
        return legacyLanguage;
    }

    static Locale localeForLanguage(String language) {
        return switch (language == null ? "" : language) {
            case "en" -> Locale.ENGLISH;
            case "fr" -> Locale.FRENCH;
            case "es" -> Locale.forLanguageTag("es");
            case "pt-BR" -> Locale.forLanguageTag("pt-BR");
            case "ja" -> Locale.JAPANESE;
            case "zh-CN" -> Locale.SIMPLIFIED_CHINESE;
            case "it" -> Locale.ITALIAN;
            case "ko" -> Locale.KOREAN;
            case "nl" -> Locale.forLanguageTag("nl");
            default -> Locale.GERMAN;
        };
    }

    private static ResourceBundle loadBundle(Locale selectedLocale) {
        return ResourceBundle.getBundle(BUNDLE_NAME, selectedLocale);
    }
}
