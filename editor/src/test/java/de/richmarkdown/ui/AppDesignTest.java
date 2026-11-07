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
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.settings.ApplicationSettings;
import javafx.scene.Scene;
import javafx.scene.control.MenuBar;
import javafx.scene.control.ToolBar;
import javafx.scene.paint.Color;
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
        I18n.setSettingsStore(ApplicationSettings.inMemory());
        I18n.setCurrentLanguage("de");
        I18n.setDocumentStructureEnabled(false);
        I18n.setLineNumbersEnabled(false);
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
    void formatTogglesUseTheSameVectorMotifsAsMenusAndRibbon() {
        FxTestUtil.runAndWait(() -> {
            FormatToolBar toolbar = window.getToolBar();
            var toggles = java.util.List.of(toolbar.getBoldToggle(), toolbar.getItalicToggle(),
                    toolbar.getUnderlineToggle(), toolbar.getStrikethroughToggle());
            var commands = java.util.List.of("menu.bold", "menu.italic", "menu.underline", "menu.strikethrough");
            for (int index = 0; index < toggles.size(); index++) {
                var graphic = toggles.get(index).getGraphic();
                assertEquals(commands.get(index), graphic.getProperties().get("commandId"));
                assertTrue(ToolbarIcons.countOf(graphic, javafx.scene.shape.SVGPath.class) > 0);
                assertEquals(0, ToolbarIcons.countOf(graphic, Text.class));
                assertNotNull(toggles.get(index).getAccessibleText());
            }
        });
    }

    @Test
    void selectedTogglePaintsVectorWhite() {
        FxTestUtil.runAndWait(() -> {
            FormatToolBar toolbar = window.getToolBar();
            assertNotNull(toolbar.getScene());
            toolbar.getBoldToggle().setSelected(false);
            toolbar.getScene().getRoot().applyCss();
            var shape = findFirst((javafx.scene.Parent) toolbar.getBoldToggle().getGraphic(), javafx.scene.shape.SVGPath.class);
            assertEquals(Color.web("#202020"), shape.getStroke());
            toolbar.getBoldToggle().setSelected(true);
            toolbar.getScene().getRoot().applyCss();
            assertEquals(Color.WHITE, shape.getStroke());
        });
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
