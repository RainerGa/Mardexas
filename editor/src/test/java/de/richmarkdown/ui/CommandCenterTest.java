package de.richmarkdown.ui;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.settings.ApplicationSettings;
import java.nio.file.Path;
import javafx.scene.control.*;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CommandCenterTest {
    @TempDir Path directory;
    private EditorWindow window;
    private CommandCenter center;
    private MenuItem alpha, beta, copy;
    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @BeforeEach void setup() {
        I18n.setSettingsStore(new ApplicationSettings(directory));
        FxTestUtil.runAndWait(() -> {
            window = new EditorWindow();
            alpha = new MenuItem("Alpha"); alpha.setId("test.alpha");
            beta = new MenuItem("Beta"); beta.setId("test.beta");
            copy = new MenuItem("Copy"); copy.setId("menu.copy");
            Menu menu = new Menu("Commands"); menu.getItems().addAll(alpha, beta, copy);
            center = new CommandCenter(window, new MenuBar(menu));
        });
    }
    @AfterEach void cleanup() { FxTestUtil.runAndWait(() -> { window.disposeFeatures(); window.close(); }); I18n.setSettingsStore(ApplicationSettings.inMemory()); }
    private CommandCenter.Command command(String query) { return center.matching(query).getFirst(); }
    @Test void searchShortcutAndPinPersistOutsideDocument() {
        FxTestUtil.runAndWait(() -> {
            assertEquals(1, center.matching("ALPHA").size());
            var command = command("Alpha");
            center.assignShortcut(command, "Shortcut+Alt+K");
            assertEquals(KeyCombination.keyCombination("Shortcut+Alt+K"), alpha.getAccelerator());
            assertEquals(alpha.getAccelerator().getName(), I18n.preference("shortcut.test.alpha", ""));
            center.pin(command, true); assertTrue(center.isPinned(command));
            var toolbar = (javafx.scene.layout.HBox) window.getChrome().getChildren().getLast();
            assertTrue(toolbar.isManaged());
            Button button = (Button) toolbar.getChildren().getFirst(); assertNotNull(button.getTooltip());
            alpha.setDisable(true); assertTrue(button.isDisable());
            center.pin(command, false); assertFalse(toolbar.isManaged());
            center.assignShortcut(command, ""); assertNull(alpha.getAccelerator());
            assertFalse(window.getEditor().isModified());
        });
    }
    @Test void rejectsCollisionsReservedAndMalformedShortcuts() {
        FxTestUtil.runAndWait(() -> {
            var a = command("Alpha"); var b = command("Beta");
            center.assignShortcut(a, "Shortcut+Alt+K");
            assertThrows(IllegalArgumentException.class, () -> center.assignShortcut(b, "Shortcut+Alt+K"));
            for (String key : new String[]{"Shortcut+A", "Shortcut+C", "Shortcut+X", "Shortcut+V", "Shortcut+Z", "Shortcut+Y", "Shortcut+Shift+Z", "Shortcut+T", "Shortcut+Shift+T", "Shortcut+W", "Ctrl+Tab", "Ctrl+Shift+Tab", "Shortcut+Shift+P"}) {
                assertThrows(IllegalArgumentException.class, () -> center.assignShortcut(b, key), key);
            }
            assertThrows(IllegalArgumentException.class, () -> center.assignShortcut(b, "not a shortcut"));
            center.assignShortcut(command("Copy"), "Shortcut+C");
            assertEquals(KeyCombination.keyCombination("Shortcut+C"), copy.getAccelerator());
            assertNull(beta.getAccelerator());
        });
    }
    @Test void profileSnapshotIsImmutableAndReadsCurrentValues() {
        I18n.setPreference("test", "value");
        var snapshot = I18n.preferences();
        assertEquals("value", snapshot.get("test"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("bad", "value"));
        I18n.setPreference("test", "changed"); assertEquals("value", snapshot.get("test"));
    }
    @Test void featureIdsAndStoredShortcutsSurviveTranslatedLabels() {
        FxTestUtil.runAndWait(() -> {
            MenuItem german = new MenuItem("Schreibansicht");
            Menu first = new Menu("Ansicht"); first.getItems().add(german);
            MenuBar original = new MenuBar(first); FeatureMenus.assignMissingCommandIds(original);
            var before = new CommandCenter(window, original);
            before.assignShortcut(before.matching("Schreibansicht").getFirst(), "Shortcut+Alt+K");
            MenuItem english = new MenuItem("Writing view");
            Menu translated = new Menu("View"); translated.getItems().add(english);
            MenuBar rebuilt = new MenuBar(translated); FeatureMenus.assignMissingCommandIds(rebuilt);
            new CommandCenter(window, rebuilt);
            assertEquals(german.getId(), english.getId());
            assertEquals(german.getAccelerator(), english.getAccelerator());
        });
    }

    @Test void visibleFavoriteHonorsParentDisableAndTogglesCheckMenuState() {
        FxTestUtil.runAndWait(() -> {
            window.show();
            CheckMenuItem toggle = new CheckMenuItem("Focus paragraph");
            toggle.setId("test.toggle");
            Menu parent = new Menu("Writing"); parent.getItems().add(toggle);
            Menu root = new Menu("View"); root.getItems().add(parent);
            CommandCenter nested = new CommandCenter(window, new MenuBar(root));
            var command = nested.matching("Focus paragraph").getFirst();
            nested.pin(command, true);
            var toolbar = (javafx.scene.layout.HBox) window.getChrome().getChildren().getLast();
            Button button = (Button) toolbar.getChildren().getFirst();
            window.getScene().getRoot().applyCss(); window.getScene().getRoot().layout();
            assertTrue(button.isVisible()); assertFalse(button.isDisabled());
            button.fire(); assertTrue(toggle.isSelected());
            button.fire(); assertFalse(toggle.isSelected());
            parent.setDisable(true); assertTrue(button.isDisabled());
            assertFalse(nested.execute(command)); assertFalse(toggle.isSelected());
            parent.setDisable(false); root.setVisible(false); assertTrue(button.isDisabled());
            root.setVisible(true); assertFalse(button.isDisabled());
            assertTrue(nested.execute(command)); assertTrue(toggle.isSelected());
        });
    }
    @Test void favoriteRadioPreservesToggleGroupAndCommandSearchUsesSameInvocation() {
        FxTestUtil.runAndWait(() -> {
            window.show();
            RadioMenuItem first = new RadioMenuItem("First"); first.setId("radio.first");
            RadioMenuItem second = new RadioMenuItem("Second"); second.setId("radio.second");
            ToggleGroup group = new ToggleGroup(); first.setToggleGroup(group); second.setToggleGroup(group);
            first.setSelected(true);
            Menu menu = new Menu("Radio"); menu.getItems().addAll(first, second);
            CommandCenter radio = new CommandCenter(window, new MenuBar(menu));
            var command = radio.matching("Second").getFirst();
            assertTrue(radio.execute(command)); assertTrue(second.isSelected()); assertFalse(first.isSelected());
            assertTrue(radio.execute(command)); assertTrue(second.isSelected());
            second.setDisable(true); assertFalse(radio.execute(command)); assertTrue(second.isSelected());
            assertThrows(IllegalArgumentException.class, () -> radio.pin(command("Alpha"), true));
        });
    }

    @Test void migratesLegacyFeatureShortcutsAndFavoritesToSemanticIds() {
        FxTestUtil.runAndWait(() -> {
            MenuItem item = new MenuItem("Move paragraph up"); item.setId("edit.moveParagraphUp");
            item.getProperties().put(CommandIdentity.LEGACY_ID, "feature.menu1.item19.item6");
            Menu menu = new Menu("Text"); menu.getItems().add(item);
            I18n.setPreference("shortcut.feature.menu1.item19.item6", "Shortcut+Alt+M");
            I18n.setPreference("toolbar.pinned", "feature.menu1.item19.item6");
            var migrated = new CommandCenter(window, new MenuBar(menu));
            var command = migrated.matching("Move paragraph up").getFirst();
            assertEquals(KeyCombination.keyCombination("Shortcut+Alt+M"), item.getAccelerator());
            assertEquals("Shortcut+Alt+M", I18n.preference("shortcut.edit.moveParagraphUp", ""));
            assertTrue(migrated.isPinned(command));
            assertEquals("edit.moveParagraphUp", I18n.preference("toolbar.pinned", ""));
            migrated.assignShortcut(command, "Shortcut+Alt+M");
            migrated.assignShortcut(command, "");
            new CommandCenter(window, new MenuBar(menu));
            assertNull(item.getAccelerator(), "An explicitly removed semantic shortcut must not be restored from its legacy alias");
        });
    }

    @Test void tabShortcutOwnersMayUseTheirReservedKeys() {
        FxTestUtil.runAndWait(() -> {
            MenuItem tab = new MenuItem("New tab"); tab.setId("tabs.new");
            Menu menu = new Menu("Tabs"); menu.getItems().add(tab);
            var tabs = new CommandCenter(window, new MenuBar(menu));
            var command = tabs.matching("New tab").getFirst();
            tabs.assignShortcut(command, "Shortcut+T");
            tabs.assignShortcut(tabs.matching("New tab").getFirst(), "Shortcut+T");
            assertEquals(KeyCombination.keyCombination("Shortcut+T"), tab.getAccelerator());
        });
    }

    @Test void favoriteIconsAndExplanationTrackShortcutToggleAndAvailability() {
        FxTestUtil.runAndWait(() -> {
            CheckMenuItem item = new CheckMenuItem("Focus paragraph"); item.setId("view.focusParagraph");
            Menu menu = new Menu("Writing view"); menu.getItems().add(item);
            var favoriteCenter = new CommandCenter(window, new MenuBar(menu));
            var command = favoriteCenter.matching("Focus paragraph").getFirst(); favoriteCenter.pin(command, true);
            var bar = (javafx.scene.layout.HBox) window.getChrome().getChildren().getLast();
            Button button = (Button) bar.getChildren().getFirst();
            assertNotNull(button.getGraphic());
            favoriteCenter.assignShortcut(command, "Shortcut+Alt+F");
            assertTrue(button.getTooltip().getText().contains(item.getAccelerator().getDisplayText()));
            String inactive = button.getTooltip().getText();
            button.fire(); assertTrue(item.isSelected());
            assertNotEquals(inactive, button.getTooltip().getText());
            menu.setDisable(true); assertTrue(button.isDisable());
            assertTrue(button.getTooltip().getText().contains(FeatureUi.text("nicht verfügbar", "Unavailable")));
            favoriteCenter.pin(command, false); assertFalse(bar.isVisible());
            favoriteCenter.dispose();
        });
    }

}
