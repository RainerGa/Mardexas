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
 * @see ApplicationSettings
 */
public final class I18n {

    private static final String BUNDLE_NAME = "de.richmarkdown.i18n.messages";
    private static final String LANGUAGE_KEY = "language";
    private static final String DOCUMENT_STRUCTURE_KEY = "documentStructure";
    private static final String LINE_NUMBERS_KEY = "lineNumbers";
    private static final String ZOOM_PERCENT_KEY = "zoomPercent";
    private static final Preferences LEGACY_PREFERENCES =
            Preferences.userRoot().node("de/kilocode/richmarkdown/i18n");
    private static final ApplicationSettings SETTINGS = new ApplicationSettings(
            ApplicationSettings.defaultConfigDirectory(
                    System.getProperty("os.name", ""),
                    System.getProperty("user.home", ""),
                    System.getenv("XDG_CONFIG_HOME"),
                    System.getenv("APPDATA")));
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
        String savedLanguage = SETTINGS.read(LANGUAGE_KEY);
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
                SETTINGS.save(LANGUAGE_KEY, systemLanguage.get());
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
        SETTINGS.save(LANGUAGE_KEY, language);
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
        SETTINGS.save(LANGUAGE_KEY, language);
    }

    /**
     * Gibt an, ob die Dokumentstruktur beim letzten Speichern aktiviert war.
     *
     * @return gespeicherter Zustand; standardmäßig {@code false}
     */
    public static boolean documentStructureEnabled() {
        return SETTINGS.readBoolean(DOCUMENT_STRUCTURE_KEY, false);
    }

    /**
     * Speichert den Sichtbarkeitszustand der Dokumentstruktur.
     *
     * @param enabled {@code true}, wenn die Dokumentstruktur angezeigt werden soll
     */
    public static void setDocumentStructureEnabled(boolean enabled) {
        SETTINGS.saveBoolean(DOCUMENT_STRUCTURE_KEY, enabled);
    }

    /**
     * Gibt an, ob Zeilennummern beim letzten Speichern aktiviert waren.
     *
     * @return gespeicherter Zustand; standardmäßig {@code false}
     */
    public static boolean lineNumbersEnabled() {
        return SETTINGS.readBoolean(LINE_NUMBERS_KEY, false);
    }

    /**
     * Speichert den Sichtbarkeitszustand der Zeilennummern.
     *
     * @param enabled {@code true}, wenn Zeilennummern angezeigt werden sollen
     */
    public static void setLineNumbersEnabled(boolean enabled) {
        SETTINGS.saveBoolean(LINE_NUMBERS_KEY, enabled);
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
        String value = SETTINGS.read(ZOOM_PERCENT_KEY);
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
        SETTINGS.save(ZOOM_PERCENT_KEY, Double.toString(percent));
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

    public static void setCurrentLanguage(String language) {
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
        SETTINGS.save(LANGUAGE_KEY, legacyLanguage);
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
