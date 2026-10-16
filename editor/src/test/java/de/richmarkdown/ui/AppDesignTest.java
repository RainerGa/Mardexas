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

package de.richmarkdown.ui;

import de.richmarkdown.FxTestUtil;
import javafx.scene.Scene;
import javafx.scene.control.MenuBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD Phase 13: Anwendungs-Design im Stil von Word/LibreOffice – ein
 * Stylesheet für Menüleiste, Toolbar, Editorfläche und Statusleiste sowie
 * Icon-Buttons in der Format-Toolbar.
 */
class AppDesignTest {

    private static final String STYLESHEET =
            "/de/richmarkdown/ui/app.css";

    private EditorWindow window;

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    @BeforeEach
    void setUp() {
        FxTestUtil.runAndWait(() -> window = new EditorWindow());
    }

    @AfterEach
    void tearDown() {
        FxTestUtil.runAndWait(() -> window.hide());
    }

    // ---------- Stylesheet ----------

    @Test
    void stylesheetResourceExists() {
        assertNotNull(AppDesignTest.class.getResource(STYLESHEET),
                "Stylesheet " + STYLESHEET + " fehlt in den Ressourcen");
    }

    @Test
    void stylesheetIsAppliedToTheScene() {
        Scene scene = FxTestUtil.callAndWait(() -> window.getScene());
        assertNotNull(scene);
        List<String> urls = scene.getStylesheets().stream()
                .map(url -> url.replace("\\", "/"))
                .collect(Collectors.toList());
        assertTrue(urls.stream().anyMatch(url -> url.endsWith("de/richmarkdown/ui/app.css")),
                "Szene bindet das Anwendungs-Stylesheet nicht ein: " + urls);
    }

    @Test
    void stylesheetStylesAllMainAreas() throws IOException {
        String css = readStylesheet();
        for (String selector : new String[]{
                ".rme-menu-bar", ".rme-toolbar", ".rme-status-bar",
                ".rme-editor-root", ".rme-structure-view", ".rme-line-number-gutter",
                ".rme-zoom-viewport"}) {
            assertTrue(css.contains(selector),
                    "Stylesheet enthält keine Regeln für " + selector);
        }
    }

    @Test
    void stylesheetStylesToolbarButtons() throws IOException {
        String css = readStylesheet();
        for (String selector : new String[]{
                ".rme-toolbar-button", ".rme-toolbar-button:hover",
                ".rme-toolbar-button:selected", ".rme-icon-shape"}) {
            assertTrue(css.contains(selector),
                    "Stylesheet enthält keine Regeln für " + selector);
        }
    }

    @Test
    void stylesheetUsesOfficeLikeColors() throws IOException {
        String css = readStylesheet();
        // Word/LibreOffice-Typik: helle Flächen, graue Leisten, blauer Akzent
        assertTrue(containsLightBackground(css),
                "heller Hintergrund für die Anwendungsfläche erwartet");
        assertTrue(containsAccent(css),
                "blauer Akzent (Word-Farbe #2b579a) erwartet");
    }

    // ---------- Style-Klassen im UI ----------

    @Test
    void mainAreasCarryStyleClasses() {
        BorderPane root = FxTestUtil.callAndWait(
                () -> (BorderPane) window.getScene().getRoot());
        assertTrue(root.getStyleClass().contains("rme-editor-root"),
                "Wurzel ohne Stil-Klasse rme-editor-root");
        assertNotNull(root.getTop());

        FxTestUtil.runAndWait(() -> {
            MenuBar menuBar = findFirst(root, MenuBar.class);
            assertNotNull(menuBar, "Menüleiste fehlt");
            assertTrue(menuBar.getStyleClass().contains("rme-menu-bar"),
                    "Menüleiste ohne Stil-Klasse rme-menu-bar");

            ToolBar toolBar = findFirst(root, ToolBar.class);
            assertNotNull(toolBar, "Toolbar fehlt");
            assertTrue(toolBar.getStyleClass().contains("rme-toolbar"),
                    "Toolbar ohne Stil-Klasse rme-toolbar");

            StatusBar statusBar = window.getStatusBar();
            assertTrue(statusBar.getStyleClass().contains("rme-status-bar"),
                    "Statusleiste ohne Stil-Klasse rme-status-bar");
        });
    }

    @Test
    void panesCarryStyleClasses() {
        FxTestUtil.runAndWait(() -> {
            assertTrue(window.getZoomController().getViewport().getStyleClass()
                            .contains("rme-zoom-viewport"),
                    "Editor-Viewport ohne Stil-Klasse rme-zoom-viewport");
            assertTrue(window.getLineNumberGutter().getStyleClass()
                            .contains("rme-line-number-gutter"),
                    "Zeilennummern ohne Stil-Klasse rme-line-number-gutter");
            assertTrue(window.getStructureView().getStyleClass()
                            .contains("rme-structure-view"),
                    "Dokumentstruktur ohne Stil-Klasse rme-structure-view");
        });
    }

    @Test
    void toolbarButtonsCarryStyleClassAndIcons() {
        FxTestUtil.runAndWait(() -> {
            FormatToolBar toolBar = window.getToolBar();
            for (var node : toolBar.getItems()) {
                if (node instanceof javafx.scene.control.ButtonBase button) {
                    assertTrue(button.getStyleClass().contains("rme-toolbar-button"),
                            "Button ohne Stil-Klasse rme-toolbar-button: " + button);
                    assertNotNull(button.getTooltip(), "Button ohne Tooltip: " + button);
                }
            }
        });
    }

    @Test
    void toolbarIsStyledByTheStylesheet() {
        FxTestUtil.runAndWait(() -> {
            FormatToolBar toolBar = window.getToolBar();
            assertNotNull(toolBar.getScene(), "Toolbar ist keiner Szene zugeordnet");
            toolBar.getScene().getRoot().applyCss();

            var fills = toolBar.getBackground().getFills();
            assertFalse(fills.isEmpty(), "Toolbar ohne Hintergrund-Füllung");
            assertEquals(Color.web("#f9f9f9"), fills.get(0).getFill(),
                    "Toolbar-Hintergrund entspricht nicht dem Word/LibreOffice-Design");
        });
    }

    @Test
    void listButtonsUseVectorIcons() {
        FxTestUtil.runAndWait(() -> {
            FormatToolBar toolBar = window.getToolBar();
            assertNotNull(toolBar.getBulletListToggle().getGraphic(),
                    "Aufzählungs-Button ohne Icon");
            assertNotNull(toolBar.getNumberedListToggle().getGraphic(),
                    "Nummerierungs-Button ohne Icon");
            assertNotNull(toolBar.getIndentButton().getGraphic(),
                    "Einzug-Button ohne Icon");
            assertNotNull(toolBar.getOutdentButton().getGraphic(),
                    "Einzug-Button ohne Icon");
            assertTrue(toolBar.getBulletListToggle().getStyleClass()
                            .contains("rme-toolbar-button"),
                    "Aufzählungs-Button ohne Stil-Klasse");
        });
    }

    @Test
    void letterTogglesAreTypographic() {
        FxTestUtil.runAndWait(() -> {
            FormatToolBar toolBar = window.getToolBar();
            Text boldLetter = letterOf(toolBar.getBoldToggle());
            assertEquals("B", boldLetter.getText(), "Fett-Toggle zeigt nicht B");
            assertEquals("Bold", boldLetter.getFont().getStyle(), "Fett-Toggle nicht fett");
            assertEquals(FormatToolBar.LETTER_TEXT_SIZE, boldLetter.getFont().getSize(),
                    "Fett-Toggle hat nicht die Buchstaben-Schriftgröße");

            Text italicLetter = letterOf(toolBar.getItalicToggle());
            assertEquals("K", italicLetter.getText(), "Kursiv-Toggle zeigt nicht K");
            assertEquals("Italic", italicLetter.getFont().getStyle(), "Kursiv-Toggle nicht kursiv");

            assertTrue(letterOf(toolBar.getUnderlineToggle()).isUnderline(),
                    "U-Toggle nicht unterstrichen");
            assertTrue(letterOf(toolBar.getStrikethroughToggle()).isStrikethrough(),
                    "S-Toggle nicht durchgestrichen");
            assertFalse(letterOf(toolBar.getUnderlineToggle()).isStrikethrough(),
                    "U-Toggle unerwartet durchgestrichen");
            assertFalse(letterOf(toolBar.getStrikethroughToggle()).isUnderline(),
                    "S-Toggle unerwartet unterstrichen");
        });
    }

    @Test
    void selectedTogglePaintsLetterWhite() {
        FxTestUtil.runAndWait(() -> {
            FormatToolBar toolBar = window.getToolBar();
            assertNotNull(toolBar.getScene(), "Toolbar ist keiner Szene zugeordnet");
            toolBar.getBoldToggle().setSelected(false);
            toolBar.getScene().getRoot().applyCss();
            Paint restFill = letterOf(toolBar.getBoldToggle()).getFill();
            assertEquals(Color.web("#202020"), restFill, "Ruhender Toggle färbt falsch");

            toolBar.getBoldToggle().setSelected(true);
            toolBar.getScene().getRoot().applyCss();
            Paint selectedFill = letterOf(toolBar.getBoldToggle()).getFill();
            assertEquals(Color.WHITE, selectedFill,
                    "Aktives Toggle färbt den Buchstaben nicht weiß");
        });
    }

    // ---------- Hilfsmittel ----------

    private Text letterOf(ToggleButton toggle) {
        Object graphic = toggle.getGraphic();
        assertInstanceOf(Text.class, graphic,
                "Toggle ohne Buchstaben-Grafik: " + toggle.getTooltip());
        return (Text) graphic;
    }
    private boolean containsLightBackground(String css) {
        return css.contains("#ffffff") || css.contains("-fx-base");
    }

    private boolean containsAccent(String css) {
        return css.contains("#2b579a") || css.contains("#4472c4") || css.contains("-fx-accent");
    }

    @SuppressWarnings("unchecked")
    private <T> T findFirst(javafx.scene.Parent parent, Class<T> type) {
        if (type.isInstance(parent)) {
            return (T) parent;
        }
        for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
            if (type.isInstance(child)) {
                return (T) child;
            }
            if (child instanceof javafx.scene.Parent sub) {
                T found = findFirst(sub, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String readStylesheet() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                AppDesignTest.class.getResourceAsStream(STYLESHEET), StandardCharsets.UTF_8))) {
            assertNotNull(reader, "Stylesheet nicht lesbar");
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
