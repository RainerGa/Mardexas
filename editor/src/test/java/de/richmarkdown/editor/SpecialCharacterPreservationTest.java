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

package de.richmarkdown.editor;

import de.richmarkdown.FxTestUtil;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressionstest für den Verlust von Sonderzeichen beim Laden eines Dokuments.
 *
 * <p>Die Emoji-Bibliothek der Rich-Text-Area behandelt jeden Codepoint zwischen U+200D und
 * U+3299 als Emoji. Zeichen wie der Halbgeviertstrich (U+2013), der Geviertstrich (U+2014),
 * der Auslassungspunkt (U+2026) oder das Eurozeichen (U+20AC) stehen aber in keiner
 * Emoji-Datenbank – sie wurden beim Laden des Dokuments stillschweigend verworfen.
 */
class SpecialCharacterPreservationTest {

    /** Zeichen, die von der Emoji-Bibliothek verschluckt wurden. */
    private static final char[] DROPPED_CHARACTERS = {'–', '—', '…', '€', '←', '─'};

    private MarkdownEditor editor;
    private Stage stage;

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    @BeforeEach
    void setUp() {
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor();
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(editor.getControl()), 600, 400));
            stage.show();
        });
        FxTestUtil.flushFxEvents();
    }

    @Test
    void specialCharactersSurviveTheRoundTrip() {
        String markdown = "Ein Gedankenstrich – ein Geviertstrich — ein Auslassungspunkt … "
                + "ein Eurozeichen € und ein Pfeil ←.\n";

        editor.setMarkdown(markdown);
        FxTestUtil.flushFxEvents();
        String written = FxTestUtil.callAndWait(editor::getMarkdown);

        assertEquals(markdown, written, "Die Sonderzeichen dürfen beim Laden nicht verloren gehen");
    }

    @Test
    void specialCharactersSurviveTheRoundTripWithFormatting() {
        String markdown = "# Überschrift – mit Gedankenstrich\n\n"
                + "Ein Absatz mit **Fett**, *Kursiv* und ~~Durchgestrichen~~ sowie einem "
                + "Auslassungspunkt …\n\n"
                + "> Zitat — mit Geviertstrich\n\n"
                + "- Listenpunkt – mit Gedankenstrich\n"
                + "- Zweiter Punkt\n";

        editor.setMarkdown(markdown);
        FxTestUtil.flushFxEvents();
        String written = FxTestUtil.callAndWait(editor::getMarkdown);

        for (char character : DROPPED_CHARACTERS) {
            assertEquals(count(markdown, character), count(written, character),
                    "Das Zeichen U+" + Integer.toHexString(character) + " ging beim Laden verloren");
        }
    }

    @Test
    void demoDocumentKeepsItsSpecialCharacters() throws IOException {
        Path demo = demoFile();
        assertTrue(Files.exists(demo), "demo.md wurde nicht gefunden: " + demo.toAbsolutePath());
        String markdown = Files.readString(demo, StandardCharsets.UTF_8);

        editor.setMarkdown(markdown);
        FxTestUtil.flushFxEvents();
        String written = FxTestUtil.callAndWait(editor::getMarkdown);

        for (char character : DROPPED_CHARACTERS) {
            assertEquals(count(markdown, character), count(written, character),
                    "Das Zeichen U+" + Integer.toHexString(character) + " ging beim Laden von "
                            + demo.getFileName() + " verloren");
        }
    }

    private static Path demoFile() {
        Path candidate = Path.of("demo.md");
        return Files.exists(candidate) ? candidate : Path.of("..", "demo.md");
    }

    private static int count(String text, char character) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == character) {
                count++;
            }
        }
        return count;
    }
}
