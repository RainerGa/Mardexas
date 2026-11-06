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
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.settings.ApplicationSettings;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests f�r den TemplateDialog (AddFeatures #17).
 * Pr�ft Dialog-Erstellung, Titel, Kopfzeile und Vorlagen-Ressourcen.
 */
class TemplateDialogTest {

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
        I18n.setSettingsStore(ApplicationSettings.inMemory());
        I18n.setCurrentLanguage("de");
    }

    @AfterAll
    static void tearDown() {
        // kein stage.hide(): blockt den FX-Thread in Monocle headless
    }

    private TemplateDialog createDialog() {
        return FxTestUtil.callAndWait(() -> {
            Stage owner = new Stage();
            owner.setScene(new Scene(new StackPane(), 100, 100));
            return new TemplateDialog(owner);
        });
    }

    @Test
    void dialogIsCreatedWithoutException() {
        TemplateDialog dialog = createDialog();
        assertNotNull(dialog, "TemplateDialog wurde erstellt");
    }

    @Test
    void dialogHasTitleAndHeader() {
        TemplateDialog dialog = createDialog();

        assertEquals(I18n.text("dialog.template.title"), FxTestUtil.callAndWait(dialog::getTitle),
                "Dialog-Titel ist gesetzt");
        assertEquals(I18n.text("dialog.template.header"), FxTestUtil.callAndWait(dialog::getHeaderText),
                "Dialog-Kopfzeile ist gesetzt");
    }

    @Test
    void templateResourcesExistOnClasspath() {
        // Alle vier Vorlagen m�ssen als Ressourcen verf�gbar sein
        String[] names = {"article", "letter", "meeting-notes", "readme"};
        for (String name : names) {
            Path templatePath = Path.of("src/main/resources/templates/" + name + ".md");
            try {
                assertTrue(Files.exists(templatePath),
                        "Vorlagen-Ressource existiert: " + name + ".md");
                assertTrue(Files.size(templatePath) > 0,
                        "Vorlagen-Ressource ist nicht leer: " + name + ".md");
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    void templateContentIsReadable() {
        // Vorlagen-Inhalte k�nnen gelesen werden und enthalten Markdown
        Path article = Path.of("src/main/resources/templates/article.md");
        String content;
        try {
            content = Files.readString(article, StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        assertFalse(content.isBlank(), "article.md enth�lt Inhalt");
        assertTrue(content.contains("#") || content.contains("*") || content.contains("-"),
                "article.md enth�lt Markdown-Formatierung");
    }

    @Test
    void i18nTemplateKeysAreDefined() {
        // Alle I18n-Schl�ssel f�r Vorlagen-Labels sind definiert
        assertNotNull(I18n.text("template.article"), "template.article ist definiert");
        assertNotNull(I18n.text("template.letter"), "template.letter ist definiert");
        assertNotNull(I18n.text("template.meetingNotes"), "template.meetingNotes ist definiert");
        assertNotNull(I18n.text("template.readme"), "template.readme ist definiert");
    }
}
