/* Copyright (C) 2026 Rainer Garbotz. SPDX-License-Identifier: GPL-3.0-or-later */
package de.richmarkdown.ui;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.GuiTestSupport;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.settings.ApplicationSettings;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Inventories the real initialized window, not a duplicate hand-maintained list. */
class CommandInventoryTest {
    private record Row(String id, String label, String classicPath, String ribbonTab, String sources,
                       String tooltip, boolean group) { }

    @TempDir Path profile;

    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }

    @Test void everyLiveMenuAndDynamicVariantHasTheSameRibbonCommandAndDocumentedIcon() throws IOException {
        List<Row> rows = FxTestUtil.callAndWait(() -> {
            I18n.setSettingsStore(new ApplicationSettings(profile)); I18n.setCurrentLanguage("de"); UiModeService.reset();
            Map<String, Row> inventory = new LinkedHashMap<>();
            collectWindow(inventory);
            for (int n = 1; n <= 10; n++) I18n.addRecentFile("/tmp/mardexas-inventory-example-" + n + ".md");
            collectWindow(inventory);
            I18n.setSettingsStore(ApplicationSettings.inMemory()); UiModeService.reset();
            return List.copyOf(inventory.values());
        });
        for (String id : List.of("menu.new", "menu.bold", "menu.heading6", "edit.moveParagraphDown",
                "table.transpose", "insert.manageTemplates", "links.checkWeb", "ui.mode.ribbon")) {
            assertTrue(rows.stream().anyMatch(row -> row.id.equals(id)), "Missing live command " + id);
        }
        for (Row row : rows) {
            assertTrue(CommandIcons.supports(row.id), row.id);
            assertFalse(row.sources.isBlank(), row.id);
            assertFalse(row.tooltip.isBlank(), row.id);
        }
        assertTrue(rows.stream().anyMatch(row -> row.id.equals("menu.recentFiles.empty")));
        assertTrue(rows.stream().anyMatch(row -> row.id.equals("menu.recentFiles.10")));
        String output = System.getProperty("mardexas.inventory.output");
        if (output != null) {
            Path directory = Path.of(output); Files.createDirectories(directory);
            Files.writeString(directory.resolve("COMMAND_INVENTORY.tsv"), tsv(rows), StandardCharsets.UTF_8);
            Files.writeString(directory.resolve("COMMAND_INVENTORY.md"), markdown(rows), StandardCharsets.UTF_8);
        }
    }

    @Test void optionalReproducibleShownRibbonPreview() throws Exception {
        String output = System.getProperty("mardexas.preview.output");
        if (output == null) return;
        EditorWindow window = FxTestUtil.callAndWait(() -> {
            I18n.setSettingsStore(ApplicationSettings.inMemory()); I18n.setCurrentLanguage("de"); UiModeService.reset();
            EditorWindow preview = new EditorWindow();
            preview.setX(0); preview.setY(0); preview.setWidth(1100); preview.setHeight(740);
            preview.getEditor().setMarkdown("# Mardexas – Schreiben mit Markdown\n\n"
                    + "Die neue Oberfläche bietet klassische Menüs und ein Ribbon mit denselben Befehlen.\n\n"
                    + "## Ein vertrauter Arbeitsplatz\n\n"
                    + "Text bearbeiten, Absätze ordnen und Dokumente prüfen: Jede Aktion behält ihre Bedeutung.\n\n"
                    + "- Einheitliche Vektor-Icons\n- Tastenkürzel und Favoriten\n- Umschalten ohne Dokumentänderung\n\n"
                    + "## Aufgabenübersicht\n\n| Funktion | Oberfläche |\n| --- | --- |\n| Formatieren | Start |\n| Einfügen | Einfügen |\n");
            preview.show(); preview.getCommandSurface().setMode(UiMode.RIBBON);
            var ribbon = preview.getCommandSurface().getRibbon();
            ribbon.setStyle("-fx-font-size: " + Integer.getInteger("mardexas.preview.fontSize", 13) + "px;");
            ribbon.getTabs().getSelectionModel().select(Integer.getInteger("mardexas.preview.tab", 1));
            return preview;
        });
        try {
            GuiTestSupport.pulses();
            saveSnapshot(window, Path.of(output));
            FxTestUtil.runAndWait(() -> {
                window.getEditorRoot().getStyleClass().add("rme-dark"); window.setWidth(700);
            });
            GuiTestSupport.pulses();
            Path narrow = Path.of(output.replaceFirst("\\.png$", "-dark-narrow.png"));
            saveSnapshot(window, narrow);
        } finally { FxTestUtil.runAndWait(window::hide); }
    }

    private void saveSnapshot(EditorWindow window, Path path) throws IOException {
        var image = FxTestUtil.callAndWait(() -> {
            window.getEditorRoot().applyCss(); window.getEditorRoot().layout();
            return window.getEditorRoot().snapshot(new javafx.scene.SnapshotParameters(), null);
        });
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
        java.awt.image.BufferedImage buffered = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, width, height, pixels, 0, width);
        assertTrue(javax.imageio.ImageIO.write(buffered, "png", path.toFile()));
    }

    private void collectWindow(Map<String, Row> result) {
        EditorWindow window = new EditorWindow();
        try {
            window.show(); window.getEditorRoot().applyCss(); window.getEditorRoot().layout();
            var ribbon = window.getCommandSurface().getRibbon();
            for (Menu menu : window.getCommandSurface().getMenuBar().getMenus()) {
                String tab = "menu.format".equals(menu.getId()) ? "Start" : menu.getText();
                walk(menu, "", tab, result, window.getCommandRegistry(), ribbon);
            }
        } finally { window.hide(); window.disposeFeatures(); }
    }

    private void walk(MenuItem item, String parent, String tab, Map<String, Row> rows,
                      WindowCommandRegistry registry, RibbonView ribbon) {
        if (item instanceof SeparatorMenuItem) return;
        assertNotNull(item.getGraphic(), item.getId());
        String classic = parent.isEmpty() ? item.getText() : parent + " → " + item.getText();
        boolean group = item instanceof Menu;
        String tooltip = group ? item.getText() : registry.get(item.getId()).explanation();
        String actualTab = ribbonTabs(item, ribbon);
        rows.putIfAbsent(item.getId(), new Row(item.getId(), item.getText(), classic, actualTab,
                String.join(" + ", CommandIcons.sourceNames(item.getId())), tooltip, group));
        if (item instanceof Menu menu) {
            for (MenuItem child : menu.getItems()) walk(child, classic, tab, rows, registry, ribbon);
        } else {
            assertTrue(ribbon.commandIds().contains(item.getId()), "Not reachable in ribbon: " + item.getId());
            assertEquals(item.getText().replace("_", ""), registry.get(item.getId()).label());
        }
    }

    private String ribbonTabs(MenuItem item, RibbonView ribbon) {
        if (item instanceof Menu menu) {
            java.util.Set<String> tabs = new java.util.LinkedHashSet<>();
            for (MenuItem child : menu.getItems()) {
                if (!(child instanceof SeparatorMenuItem)) tabs.add(ribbonTabs(child, ribbon));
            }
            return String.join(" / ", tabs);
        }
        return ribbon.tabTitle(item.getId());
    }

    private static String tsv(List<Row> rows) {
        StringBuilder text = new StringBuilder("id\tlabel\tclassicPath\tribbonTab\ticonSources\ttooltip\tkind\n");
        for (Row row : rows) text.append(String.join("\t", clean(row.id), clean(row.label), clean(row.classicPath),
                clean(row.ribbonTab), clean(row.sources), clean(row.tooltip), row.group ? "group" : "command")).append('\n');
        return text.toString();
    }
    private static String markdown(List<Row> rows) {
        StringBuilder text = new StringBuilder("# Befehlsinventar der aufgebauten Oberfläche\n\n"
                + "Automatisch aus zwei tatsächlich aufgebauten `EditorWindow`-Instanzen erzeugt: leere Liste und zehn zuletzt geöffnete Dateien. "
                + "Sprache: Deutsch. Gruppen sind ausdrücklich gekennzeichnet; Trennlinien sind ausgenommen. "
                + "Die Zahlenzusätze der letzten Dateien bestehen aus zusätzlichen Vektorpfaden.\n\n"
                + "Regenerierung: `mvn -o -pl editor -Dmardexas.build.directory=/tmp/mardexas-inventory-build "
                + "-Dtest=CommandInventoryTest -Dmardexas.inventory.output=. test` (Ausgabe im Editor-Modul; alternativ einen absoluten Ausgabepfad verwenden).\n\n"
                + "| ID | Beschriftung | Klassischer Pfad | Ribbon-Register | Icon-Originale | Erklärung | Art |\n"
                + "| --- | --- | --- | --- | --- | --- | --- |\n");
        for (Row row : rows) text.append("| ").append(String.join(" | ", cell(row.id), cell(row.label),
                cell(row.classicPath), cell(row.ribbonTab), cell(row.sources), cell(row.tooltip), row.group ? "Gruppe" : "Befehl")).append(" |\n");
        return text.toString();
    }
    private static String clean(String value) { return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' '); }
    private static String cell(String value) { return clean(value).replace("|", "\\|"); }
}
