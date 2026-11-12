package de.richmarkdown.ui;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.GuiTestSupport;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.settings.ApplicationSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RecentCommandsGuiTest {
    @TempDir Path directory;
    private EditorWindow window;
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @BeforeEach void open() throws Exception {
        FxTestUtil.runAndWait(() -> {
            I18n.setSettingsStore(new ApplicationSettings(directory.resolve("settings")));
            I18n.setCurrentLanguage("de"); UiModeService.reset(); I18n.setDocumentStructureEnabled(false);
            window = new EditorWindow(); window.setWidth(1000); window.setHeight(700); window.show();
            window.getCommandSurface().setMode(UiMode.RIBBON);
            // The Office ribbon opens on Start; recent-file controls belong to the
            // lazily rendered Datei tab and must be rendered before inspection.
            window.getCommandSurface().getRibbon().getTabs().getSelectionModel().select(0);
        });
        GuiTestSupport.pulses();
    }
    @AfterEach void close() { FxTestUtil.runAndWait(() -> {
        window.hide(); UiModeService.reset(); I18n.setSettingsStore(ApplicationSettings.inMemory());
    }); }

    @Test void recentCommandsUpdateLabelsPathsAvailabilityAndCurrentActionWithoutRecreatingWindow() throws Exception {
        Path alpha = directory.resolve("alpha.md"), beta = directory.resolve("beta.md");
        Files.writeString(alpha, "Alpha document"); Files.writeString(beta, "Beta document");
        FxTestUtil.runAndWait(() -> {
            var registry = window.getCommandRegistry();
            assertFalse(registry.get("menu.recentFiles.1").isVisible());
            assertFalse(registry.execute("menu.recentFiles.1"));
            assertFalse(registry.get("menu.recentFiles.clear").isEnabled());
            I18n.addRecentFile(alpha.toString()); registry.refresh();
            assertTrue(registry.get("menu.recentFiles.1").isVisible());
            assertEquals("1  alpha.md", registry.get("menu.recentFiles.1").label());
            registry.item("menu.recentFiles.1").setAccelerator(KeyCombination.keyCombination("Alt+1"));
            I18n.addRecentFile(beta.toString()); registry.refresh();
            assertEquals("1  beta.md", registry.get("menu.recentFiles.1").label());
            assertEquals(beta.toString(), registry.get("menu.recentFiles.1").description());
            assertEquals("2  alpha.md", registry.get("menu.recentFiles.2").label());
            var button = (ButtonBase) window.getCommandSurface().getRibbon().findControl("menu.recentFiles.1");
            assertEquals("1  beta.md", button.getText());
            assertTrue(button.getTooltip().getText().contains(beta.toString()));
            assertTrue(button.getTooltip().getText().contains(registry.item("menu.recentFiles.1").getAccelerator().getDisplayText()));
            var classicTooltip = (Tooltip) registry.item("menu.recentFiles.1").getProperties().get("command.tooltip");
            assertTrue(classicTooltip.getText().contains(beta.toString()));
            assertTrue(classicTooltip.getText().contains(registry.item("menu.recentFiles.1").getAccelerator().getDisplayText()));
            assertTrue(registry.execute("menu.recentFiles.1"));
        });
        GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> {
            assertEquals(beta.toFile(), window.getCurrentFile());
            assertTrue(window.getEditor().getDocumentSnapshot().getText().contains("Beta document"));
            var registry = window.getCommandRegistry();
            assertTrue(registry.execute("menu.recentFiles.clear")); registry.refresh();
            assertTrue(I18n.getRecentFiles().isEmpty());
            assertFalse(registry.get("menu.recentFiles.1").isEnabled());
            assertFalse(registry.get("menu.recentFiles.1").isVisible());
            assertFalse(registry.execute("menu.recentFiles.1"));
            assertFalse(registry.get("menu.recentFiles.clear").isEnabled());
            assertTrue(registry.get("menu.recentFiles.empty").isVisible());
            assertEquals(KeyCombination.keyCombination("Alt+1"), registry.get("menu.recentFiles.1").shortcut());
        });
    }

    @Test void pinnedRecentCommandRemainsUsableAfterSearchRefreshAndFileReordering() throws Exception {
        Path first = directory.resolve("first.md"), next = directory.resolve("next.md");
        Files.writeString(first, "First"); Files.writeString(next, "Next");
        FxTestUtil.runAndWait(() -> {
            var registry = window.getCommandRegistry();
            I18n.addRecentFile(first.toString()); registry.refresh();
            var center = new CommandCenter(window, registry);
            var original = center.matching("first.md").stream()
                    .filter(command -> "menu.recentFiles.1".equals(command.id())).findFirst().orElseThrow();
            center.pin(original, true);
            I18n.addRecentFile(next.toString()); registry.refresh();
            assertTrue(center.matching("next.md").stream().anyMatch(command -> original.id().equals(command.id())));
            center.assignShortcut(original, "Alt+1");
            var favorite = (javafx.scene.control.Button) window.getChrome().lookupAll(".button").stream()
                    .filter(node -> "favorite-menu.recentFiles.1".equals(node.getId())).findFirst().orElseThrow();
            assertEquals("1  next.md", favorite.getText());
            assertTrue(favorite.getTooltip().getText().contains(next.toString()));
            favorite.fire();
        });
        GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> assertEquals(next.toFile(), window.getCurrentFile()));
    }

    @Test void identicalFilenamesStillRefreshDescriptionAndTooltipsAndNotifyEveryOpenWindow() throws Exception {
        Path first = Files.createDirectories(directory.resolve("one")).resolve("same.md");
        Path second = Files.createDirectories(directory.resolve("two")).resolve("same.md");
        Files.writeString(first, "One"); Files.writeString(second, "Two");
        EditorWindow[] other = new EditorWindow[1];
        try {
            FxTestUtil.runAndWait(() -> {
                other[0] = new EditorWindow(); other[0].show();
                I18n.addRecentFile(first.toString()); window.getCommandRegistry().refresh();
                I18n.addRecentFile(second.toString());
            });
            GuiTestSupport.pulses();
            FxTestUtil.runAndWait(() -> {
                assertEquals(second.toString(), window.getCommandRegistry().get("menu.recentFiles.1").description());
                assertEquals(second.toString(), other[0].getCommandRegistry().get("menu.recentFiles.1").description());
                var button = (ButtonBase) window.getCommandSurface().getRibbon().findControl("menu.recentFiles.1");
                assertEquals("1  same.md", button.getText());
                assertTrue(button.getTooltip().getText().contains(second.toString()));
                I18n.removeRecentFile(second.toString());
            });
            GuiTestSupport.pulses();
            FxTestUtil.runAndWait(() -> assertEquals(first.toString(), window.getCommandRegistry().get("menu.recentFiles.1").description()));
        } finally { FxTestUtil.runAndWait(() -> { if (other[0] != null) other[0].hide(); }); }
    }
}
