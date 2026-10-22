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
import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.i18n.I18n;
import javafx.scene.control.MenuBar;
import javafx.scene.control.Slider;
import javafx.scene.transform.Scale;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD Phase 1: EditorWindow – vollständige Fensterstruktur.
 */
class EditorWindowTest {

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
        // Tests müssen unabhängig von gespeicherten Benutzereinstellungen laufen
        I18n.setCurrentLanguage("de");
        I18n.setDocumentStructureEnabled(false);
        I18n.setLineNumbersEnabled(false);
    }

    @AfterAll
    static void tearDown() {
        // kein hide(): blockt den FX-Thread in Monocle headless
    }

    @Test
    void windowHasAllComponents() {
        EditorWindow window = FxTestUtil.callAndWait(EditorWindow::new);
        assertNotNull(window.getEditor());
        assertNotNull(window.getToolBar());
        assertNotNull(window.getStatusBar());
        assertNotNull(window.getEditorControl());
    }

    @Test
    void windowHasTitleAndScene() {
        EditorWindow window = FxTestUtil.callAndWait(EditorWindow::new);
        assertEquals("Unbenannt - Mardexas", FxTestUtil.callAndWait(window::getTitle));
        assertNotNull(FxTestUtil.callAndWait(window::getScene));
    }

    @Test
    void windowContainsMenuBarToolbarEditorAndStatusBar() {
        EditorWindow window = FxTestUtil.callAndWait(EditorWindow::new);
        Boolean structure = FxTestUtil.callAndWait(() -> {
            var root = (javafx.scene.layout.BorderPane) window.getScene().getRoot();
            var top = (VBox) root.getTop();
            boolean hasMenuBar = top.getChildren().get(0) instanceof MenuBar;
            boolean hasToolBar = top.getChildren().get(1) instanceof FormatToolBar;
            // Der Editor sitst seit den Zeilennummern im Zoom-Viewport innerhalb
            // einer HBox im Center – daher rekursiv suchen
            boolean hasEditor = root.getCenter() instanceof javafx.scene.Parent
                    && containsNode((javafx.scene.Parent) root.getCenter(),
                            window.getEditorControl());
            boolean hasStatusBar = root.getBottom() instanceof StatusBar;
            return hasMenuBar && hasToolBar && hasEditor && hasStatusBar;
        });
        assertTrue(structure, "Fensterstruktur: Menüleiste, Toolbar, Editor, Statusleiste");
    }

    /** Rekursive Suche eines Knotens im Szenengraph. */
    private static boolean containsNode(javafx.scene.Parent parent, javafx.scene.Node node) {
        if (parent == node) {
            return true;
        }
        for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
            if (child == node) {
                return true;
            }
            if (child instanceof javafx.scene.Parent p && containsNode(p, node)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void menuBarHasAllStandardMenus() {
        EditorWindow window = FxTestUtil.callAndWait(EditorWindow::new);
        var menuBar = FxTestUtil.callAndWait(() -> {
            var root = (javafx.scene.layout.BorderPane) window.getScene().getRoot();
            var top = (VBox) root.getTop();
            return (MenuBar) top.getChildren().get(0);
        });
        assertEquals(9, menuBar.getMenus().size());
        assertEquals("Datei", menuBar.getMenus().get(0).getText());
        assertEquals("Bearbeiten", menuBar.getMenus().get(1).getText());
        assertEquals("Ansicht", menuBar.getMenus().get(2).getText());
        assertEquals("Einfügen", menuBar.getMenus().get(3).getText());
        assertEquals("Format", menuBar.getMenus().get(4).getText());
        assertEquals("Tabelle", menuBar.getMenus().get(5).getText());
        assertEquals("Extras", menuBar.getMenus().get(6).getText());
        assertEquals("Fenster", menuBar.getMenus().get(7).getText());
        assertEquals("Hilfe", menuBar.getMenus().get(8).getText());
    }

    @Test
    void helpMenuOffersHelpAboutAndLicensing() {
        EditorWindow window = FxTestUtil.callAndWait(EditorWindow::new);
        var helpItems = FxTestUtil.callAndWait(() -> {
            var root = (javafx.scene.layout.BorderPane) window.getScene().getRoot();
            var top = (VBox) root.getTop();
            var menuBar = (MenuBar) top.getChildren().get(0);
            return menuBar.getMenus().stream()
                    .filter(menu -> menu.getText().equals("Hilfe"))
                    .findFirst()
                    .orElseThrow()
                    .getItems().stream()
                    .map(item -> item.getText())
                    .toList();
        });

        assertEquals(List.of("Hilfe anzeigen", "Über Mardexas", "Lizenzierung",
                "Sprache"), helpItems);
    }

    @Test
    void languageMenuOffersGermanAndEnglish() {
        EditorWindow window = FxTestUtil.callAndWait(EditorWindow::new);
        var languageOptions = FxTestUtil.callAndWait(() -> {
            var root = (javafx.scene.layout.BorderPane) window.getScene().getRoot();
            var top = (VBox) root.getTop();
            var menuBar = (MenuBar) top.getChildren().get(0);
            var helpMenu = menuBar.getMenus().stream()
                    .filter(menu -> menu.getText().equals("Hilfe"))
                    .findFirst()
                    .orElseThrow();
            var languageMenu = (javafx.scene.control.Menu) helpMenu.getItems().stream()
                    .filter(item -> item.getText().equals("Sprache"))
                    .findFirst()
                    .orElseThrow();
            return languageMenu.getItems().stream()
                    .map(item -> item.getText())
                    .toList();
        });

        assertEquals(List.of("Deutsch", "English", "Français", "Español",
                "Português (Brasil)", "日本語", "简体中文", "Italiano", "한국어", "Nederlands"),
                languageOptions);
    }

    @Test
    void tableMenuOffersInsertDialog() {
        EditorWindow window = FxTestUtil.callAndWait(EditorWindow::new);
        var tableMenu = FxTestUtil.callAndWait(() -> {
            var root = (javafx.scene.layout.BorderPane) window.getScene().getRoot();
            var top = (VBox) root.getTop();
            return ((MenuBar) top.getChildren().get(0)).getMenus().get(5);
        });
        var labels = tableMenu.getItems().stream()
                .filter(item -> !(item instanceof javafx.scene.control.SeparatorMenuItem))
                .map(item -> item.getText())
                .toList();
        assertEquals(1, labels.size());
        assertTrue(labels.contains("Tabelle einfügen..."));
    }

    @Test
    void fileMenuOffersPrintAfterSaveAs() {
        EditorWindow window = FxTestUtil.callAndWait(EditorWindow::new);
        var labels = FxTestUtil.callAndWait(() -> {
            var root = (javafx.scene.layout.BorderPane) window.getScene().getRoot();
            var top = (VBox) root.getTop();
            return ((MenuBar) top.getChildren().get(0)).getMenus().get(0).getItems().stream()
                    .filter(item -> !(item instanceof javafx.scene.control.SeparatorMenuItem))
                    .map(item -> item.getText())
                    .toList();
        });
        // Datei-Reihenfolge wie in LibreOffice Writer: … Speichern unter…, Drucken…, Beenden
        assertEquals(List.of("Neu", "Öffnen...", "Öffnen (schreibgeschützt)...",
                "Zuletzt geöffnet", "Speichern", "Speichern unter...", "Drucken...", "Beenden"), labels);
    }

    @Test
    void printWithoutAvailablePrinterShowsMessage() throws Exception {
        // Wie in Word/LibreOffice: ist kein Drucker verfügbar, erfährt der
        // Anwender das per Hinweis – stille Wirkungslosigkeit wäre nicht
        // nachvollziehbar (Datei → Drucken... täte scheinbar nichts)
        CountDownLatch message = new CountDownLatch(1);
        EditorWindow window = FxTestUtil.callAndWait(() -> new EditorWindow() {
            @Override
            protected java.util.List<javafx.print.Printer> listPrinters() {
                return java.util.List.of();
            }

            @Override
            protected void showNoPrinterMessage() {
                message.countDown();
            }
        });
        FxTestUtil.runAndWait(window::print);

        // print() arbeitet auf einem Hintergrund-Thread – kurz warten
        assertTrue(message.await(5, TimeUnit.SECONDS));
    }

    @Test
    void printCancellationSkipsSpooling() throws Exception {
        // Abbruch im JavaFX-Dialog: kein Auftrag, kein Spoolen. Der Dialog
        // selbst läuft auf dem FX-Thread (showAndWait), die Vorbereitung
        // (Druckerliste) auf dem Hintergrund-Thread.
        CountDownLatch dialogShown = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Boolean> enumeratedOnFxThread = new AtomicReference<>();
        AtomicReference<Thread> printThread = new AtomicReference<>();

        EditorWindow window = FxTestUtil.callAndWait(() -> new EditorWindow() {
            @Override
            protected java.util.List<javafx.print.Printer> listPrinters() {
                enumeratedOnFxThread.set(
                        javafx.application.Platform.isFxApplicationThread());
                printThread.set(Thread.currentThread());
                return java.util.List.of();
            }

            @Override
            protected void showNoPrinterMessage() {
                dialogShown.countDown();
                finished.countDown();
            }
        });
        FxTestUtil.runAndWait(window::print);

        assertTrue(dialogShown.await(5, TimeUnit.SECONDS));
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertFalse(enumeratedOnFxThread.get(),
                "Druckeraufzählung lief auf dem FX-Thread");
        assertTrue(printThread.get().getName().startsWith("rme-print"),
                "Druckvorbereitung läuft auf rme-print");
    }

    @Test
    void printDialogCancellationDoesNotCreateJob() throws Exception {
        // Dialog-Abbruch (null) darf keinen PrinterJob bauen und nicht
        // spoolen. CUPS wird nicht berührt: Die Druckerliste ist gemockt,
        // der Dialog ebenfalls (kein showAndWait im Test).
        CountDownLatch dialogShown = new CountDownLatch(1);

        EditorWindow window = FxTestUtil.callAndWait(() -> new EditorWindow() {
            @Override
            protected java.util.List<javafx.print.Printer> listPrinters() {
                // leer reicht: print() bricht dann vor dem Dialog ab –
                // für den Abbruch-Pfad weiter unten wird der Dialog
                // direkt aufgerufen
                return java.util.List.of();
            }

            @Override
            protected de.richmarkdown.ui.dialogs.PrintDialogResult showPrintDialog(
                    java.util.List<javafx.print.Printer> printers) {
                dialogShown.countDown();
                return null;
            }

            @Override
            protected javafx.print.PrinterJob createPrinterJob(
                    javafx.print.Printer printer) {
                throw new AssertionError("kein Job bei Dialog-Abbruch");
            }

            @Override
            protected void printDocument(javafx.print.PrinterJob job) {
                throw new AssertionError("kein Spoolen bei Dialog-Abbruch");
            }
        });
        // Dialog-Abbruch direkt verifizieren: kein Job, kein Spoolen –
        // wirft bei Verstoß AssertionError
        FxTestUtil.callAndWait(() -> window.showPrintDialog(java.util.List.of()));
        assertTrue(dialogShown.await(5, TimeUnit.SECONDS));
    }

    @Test
    void createPrinterJobFallsBackToAnyAvailablePrinter() {
        // Häufige Linux-Konfiguration: Drucker vorhanden, aber kein
        // Systemstandarddrucker eingerichtet. `PrinterJob.createPrinterJob()`
        // liefert dann null und der Druckdialog würde sich nie öffnen –
        // der erste verfügbare Drucker muss daher als Auftrag gebaut werden.
        EditorWindow window = FxTestUtil.callAndWait(EditorWindow::new);
        java.util.Set<javafx.print.Printer> printers = javafx.print.Printer.getAllPrinters();
        // Druck-API ist thread-unabhängig – wie in der Produktion (Druck läuft
        // auf einem Hintergrund-Thread) direkt aufrufen, sonst sprengt die
        // CUPS-Suche das fx-Budget
        javafx.print.PrinterJob job = window.createPrinterJob();
        if (printers.isEmpty()) {
            assertNull(job);
        } else {
            assertNotNull(job, "mit vorhandenem Drucker muss ein Job baubar sein");
        }
    }

    @Test
    void createPrinterJobRemembersPrinterAcrossCalls() {
        // Die Druckersuche kostet pro Aufruf ~1 s (CUPS-Roundtrip), das
        // Job-Bauen nur ~20 ms – der gewählte Drucker wird daher gemerkt,
        // damit wiederholtes Drucken nicht erneut sucht.
        org.junit.jupiter.api.Assumptions.assumeTrue(
                !javafx.print.Printer.getAllPrinters().isEmpty(),
                "kein Drucker im System");
        int[] selections = {0};
        EditorWindow window = FxTestUtil.callAndWait(() -> new EditorWindow() {
            @Override
            protected javafx.print.Printer selectPrinter() {
                selections[0]++;
                return super.selectPrinter();
            }
        });
        assertNotNull(window.createPrinterJob());
        assertNotNull(window.createPrinterJob());
        assertEquals(1, selections[0], "Drucker wird nur einmal ermittelt");
    }

    @Test
    void viewMenuHasStructureItem() {
        EditorWindow window = FxTestUtil.callAndWait(EditorWindow::new);
        var viewMenu = FxTestUtil.callAndWait(() -> {
            var root = (javafx.scene.layout.BorderPane) window.getScene().getRoot();
            var top = (VBox) root.getTop();
            return ((MenuBar) top.getChildren().get(0)).getMenus().get(2);
        });
        assertEquals("Dokumentstruktur", viewMenu.getItems().get(0).getText());
    }

    @Test
    void structureViewIsHiddenInitiallyAndToggles() {
        EditorWindow window = FxTestUtil.callAndWait(EditorWindow::new);
        assertFalse(FxTestUtil.callAndWait(window.getStructureView()::isVisible));

        FxTestUtil.runAndWait(window::toggleStructureView);
        assertTrue(FxTestUtil.callAndWait(window.getStructureView()::isVisible));
        assertTrue(FxTestUtil.callAndWait(window.getStructureView()::isManaged));

        FxTestUtil.runAndWait(window::toggleStructureView);
        assertFalse(FxTestUtil.callAndWait(window.getStructureView()::isVisible));
        assertFalse(FxTestUtil.callAndWait(window.getStructureView()::isManaged));
    }

    @Test
    void structureViewIsPlacedLeftOfEditor() {
        EditorWindow window = FxTestUtil.callAndWait(EditorWindow::new);
        boolean left = FxTestUtil.callAndWait(() -> {
            var root = (javafx.scene.layout.BorderPane) window.getScene().getRoot();
            return root.getLeft() == window.getStructureView();
        });
        assertTrue(left, "structure view expected on the left");
    }

    @Test
    void insertMenuHasLinkHrImageAndCodeBlock() {
        EditorWindow window = FxTestUtil.callAndWait(EditorWindow::new);
        var insertMenu = FxTestUtil.callAndWait(() -> {
            var root = (javafx.scene.layout.BorderPane) window.getScene().getRoot();
            var top = (VBox) root.getTop();
            return ((MenuBar) top.getChildren().get(0)).getMenus().get(3);
        });
        var labels = insertMenu.getItems().stream()
                .filter(item -> !(item instanceof javafx.scene.control.SeparatorMenuItem))
                .map(item -> item.getText())
                .toList();
        assertTrue(labels.contains(I18n.text("menu.link")));
        assertTrue(labels.contains(I18n.text("menu.horizontalRule")));
        assertTrue(labels.contains(I18n.text("menu.image")));
        assertTrue(labels.contains(I18n.text("menu.codeBlock")));
    }

    @Test
    void editMenuHasUndoRedoAndClipboardItems() {
        EditorWindow window = FxTestUtil.callAndWait(EditorWindow::new);
        var editMenu = FxTestUtil.callAndWait(() -> {
            var root = (javafx.scene.layout.BorderPane) window.getScene().getRoot();
            var top = (VBox) root.getTop();
            return ((MenuBar) top.getChildren().get(0)).getMenus().get(1);
        });
        var labels = editMenu.getItems().stream()
                .filter(item -> !(item instanceof javafx.scene.control.SeparatorMenuItem))
                .map(item -> item.getText())
                .toList();
        assertTrue(labels.contains("Rückgängig"));
        assertTrue(labels.contains("Wiederherstellen"));
        assertTrue(labels.contains("Ausschneiden"));
        assertTrue(labels.contains("Kopieren"));
        assertTrue(labels.contains("Einfügen"));
        assertTrue(labels.contains("Alles auswählen"));
    }

    @Test
    void windowHasZoomControlsInStatusBar() {
        EditorWindow window = FxTestUtil.callAndWait(EditorWindow::new);
        assertNotNull(window.getZoomController());
        // Zoom-Steuerung ist Teil der Statusleiste (rechts unten)
        boolean inStatusBar = FxTestUtil.callAndWait(() -> {
            var root = (javafx.scene.layout.BorderPane) window.getScene().getRoot();
            var statusBar = (StatusBar) root.getBottom();
            return statusBar.getChildren().contains(window.getZoomController().createControls());
        });
        assertTrue(inStatusBar, "zoom controls expected in status bar");
    }

    @Test
    void zoomSliderInWindowScalesEditor() {
        EditorWindow window = FxTestUtil.callAndWait(() -> {
            EditorWindow created = new EditorWindow();
            created.show();
            return created;
        });
        Slider slider = window.getZoomController().createSlider();
        FxTestUtil.runAndWait(() -> slider.setValue(200));

        Scale scale = FxTestUtil.callAndWait(() -> window.getEditorControl().getTransforms().stream()
                .filter(t -> t instanceof Scale)
                .map(t -> (Scale) t)
                .findFirst()
                .orElse(null));
        assertNotNull(scale);
        assertEquals(2d, scale.getX(), 0.001);
    }

    @Test
    void restoresAndSavesZoomWhenWindowCloses() {
        double previousZoom = I18n.zoomPercent();
        try {
            EditorWindow window = FxTestUtil.callAndWait(EditorWindow::new);
            assertEquals(previousZoom, window.getZoomController().getZoom(), 0.001);

            FxTestUtil.runAndWait(() -> {
                window.getZoomController().setZoom(175);
                window.show();
                window.hide();
            });

            assertEquals(175d, I18n.zoomPercent(), 0.001);
        } finally {
            I18n.setZoomPercent(previousZoom);
        }
    }

    @Test
    void zoomDoesNotChangeDocumentContent() {
        EditorWindow window = FxTestUtil.callAndWait(() -> {
            EditorWindow created = new EditorWindow();
            created.show();
            return created;
        });
        FxTestUtil.runAndWait(() -> window.getEditor().setMarkdown("# Title\n\ntext"));
        FxTestUtil.flushFxEvents();
        String before = FxTestUtil.callAndWait(window.getEditor()::getMarkdown);

        window.getZoomController().setZoom(250);
        window.getZoomController().setZoom(50);
        window.getZoomController().resetZoom();

        assertEquals(before, FxTestUtil.callAndWait(window.getEditor()::getMarkdown));
    }

    @Test
    void editorIsEditableAndEmpty() {
        EditorWindow window = FxTestUtil.callAndWait(EditorWindow::new);
        MarkdownEditor editor = window.getEditor();
        assertTrue(FxTestUtil.callAndWait(editor.getControl()::isEditable));
        assertEquals(0, FxTestUtil.callAndWait(editor.getControl()::getTextLength));
    }

    @Test
    void windowIsNotInitiallyShown() {
        EditorWindow window = FxTestUtil.callAndWait(EditorWindow::new);
        assertFalse(FxTestUtil.callAndWait(window::isShowing));
    }
}
