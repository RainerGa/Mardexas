package de.richmarkdown.i18n;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class I18nTest {

    @Test
    void germanIsTheFallbackLanguageAndEnglishCanBeSelected() {
        assertEquals(Locale.GERMAN, I18n.localeForLanguage(null));
        assertEquals(Locale.GERMAN, I18n.localeForLanguage("unsupported"));
        assertEquals(Locale.ENGLISH, I18n.localeForLanguage("en"));
        assertEquals(Locale.FRENCH, I18n.localeForLanguage("fr"));
    }

    @Test
    void germanAndEnglishBundlesHaveMatchingKeysAndTranslatedMenuLabels() {
        String bundleName = "de.richmarkdown.i18n.messages";
        ResourceBundle german = ResourceBundle.getBundle(bundleName, Locale.GERMAN);
        ResourceBundle english = ResourceBundle.getBundle(bundleName, Locale.ENGLISH);

        assertEquals(german.keySet(), english.keySet());
        assertEquals("Datei", german.getString("menu.file"));
        assertEquals("File", english.getString("menu.file"));
        assertNotEquals(german.getString("help.text"), english.getString("help.text"));
    }

    @Test
    void allSupportedLanguagesHaveCompleteTranslatedBundles() {
        String bundleName = "de.richmarkdown.i18n.messages";
        Set<String> expectedKeys = ResourceBundle.getBundle(bundleName, Locale.GERMAN).keySet();

        assertEquals(Set.of("de", "en", "fr", "es", "pt-BR", "ja",
                "zh-CN", "it", "ko", "nl"), Set.copyOf(I18n.supportedLanguages()));
        for (String language : I18n.supportedLanguages()) {
            Locale locale = I18n.localeForLanguage(language);
            ResourceBundle bundle = ResourceBundle.getBundle(bundleName, locale);
            assertEquals(expectedKeys, bundle.keySet(), "Incomplete bundle: " + language);
            assertFalse(bundle.getString("menu.help").isBlank(),
                    "Missing menu label: " + language);
        }
        assertEquals("pt-BR", I18n.localeForLanguage("pt-BR").toLanguageTag());
        assertEquals("zh-CN", I18n.localeForLanguage("zh-CN").toLanguageTag());
    }

    @Test
    void operatingSystemLocalesResolveToSupportedLanguagesOrRequestAChoice() {
        assertEquals(Optional.of("de"), I18n.languageForSystemLocale(Locale.GERMAN));
        assertEquals(Optional.of("fr"), I18n.languageForSystemLocale(
                Locale.forLanguageTag("fr-CA")));
        assertEquals(Optional.of("pt-BR"), I18n.languageForSystemLocale(
                Locale.forLanguageTag("pt-PT")));
        assertEquals(Optional.of("zh-CN"), I18n.languageForSystemLocale(
                Locale.forLanguageTag("zh-SG")));
        assertEquals(Optional.empty(), I18n.languageForSystemLocale(
                Locale.forLanguageTag("zh-TW")));
        assertEquals(Optional.empty(), I18n.languageForSystemLocale(
                Locale.forLanguageTag("ru-RU")));
    }
}
