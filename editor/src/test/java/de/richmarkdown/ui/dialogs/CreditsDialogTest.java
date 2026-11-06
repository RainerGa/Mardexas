package de.richmarkdown.ui.dialogs;

import de.richmarkdown.FxTestUtil;
import de.richmarkdown.GuiTestSupport;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.settings.ApplicationSettings;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Shown JavaFX dialogs with real pointer and keyboard input, plus persistent startup regressions. */
class CreditsDialogTest {
    @TempDir Path profile;
    private Stage owner;
    private CreditsDialog dialog;

    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }

    @BeforeEach void setup() {
        FxTestUtil.runAndWait(() -> {
            I18n.setSettingsStore(new ApplicationSettings(profile));
            I18n.setCurrentLanguage("de");
            owner = new Stage();
            owner.setScene(new Scene(new StackPane(), 900, 720));
            owner.show();
        });
    }

    @AfterEach void cleanup() {
        FxTestUtil.runAndWait(() -> {
            for (Window window : java.util.List.copyOf(Window.getWindows())) {
                if (window != owner) window.hide();
            }
            owner.hide();
            I18n.setSettingsStore(ApplicationSettings.inMemory());
            I18n.setCurrentLanguage("de");
        });
    }

    private void show(boolean thanks) throws Exception {
        dialog = FxTestUtil.callAndWait(() -> {
            CreditsDialog value = new CreditsDialog(owner, thanks);
            value.show();
            return value;
        });
        GuiTestSupport.pulses();
    }

    private void click(Node node) throws Exception {
        Bounds bounds = FxTestUtil.callAndWait(node::getBoundsInLocal);
        GuiTestSupport.click(node, bounds.getCenterX(), bounds.getCenterY());
    }

    private TabPane tabs() {
        return FxTestUtil.callAndWait(() -> (TabPane) dialog.getDialogPane().lookup("#credits-tabs"));
    }

    private void clickTab(int index) throws Exception {
        String title = FxTestUtil.callAndWait(() -> tabs().getTabs().get(index).getText());
        Node header = FxTestUtil.callAndWait(() -> tabs().lookupAll(".tab").stream()
                .filter(node -> node.lookup(".tab-label") instanceof Label label && label.getText().equals(title))
                .findFirst().orElseThrow());
        click(header);
        assertEquals(index, FxTestUtil.callAndWait(() -> tabs().getSelectionModel().getSelectedIndex()));
    }

    @Test void originalEmojiLicensesCanBeExpandedAndCopiedInTheShownWindow() throws Exception {
        show(false);
        clickTab(2);
        for (String filename : new String[] {"LICENSE-NOTO-APACHE-2.0.txt", "LICENSE-NOTO-FLAGS.txt",
                "NOTICE.txt", "LICENSE-EMOJI-DATA-MIT.txt"}) {
            var pane = FxTestUtil.callAndWait(() -> (javafx.scene.control.TitledPane)
                    dialog.getDialogPane().lookup("#emoji-license-" + filename.replace('.', '-')));
            assertNotNull(pane);
            FxTestUtil.runAndWait(() -> {
                var scroll = (javafx.scene.control.ScrollPane) tabs().getTabs().get(2).getContent();
                var content = scroll.getContent();
                double top = content.sceneToLocal(pane.localToScene(pane.getBoundsInLocal())).getMinY();
                double overflow = content.getBoundsInLocal().getHeight() - scroll.getViewportBounds().getHeight();
                scroll.setVvalue(overflow <= 0 ? 0 : Math.max(0, Math.min(1, top / overflow)));
            });
            GuiTestSupport.pulses();
            click(FxTestUtil.callAndWait(() -> pane.lookup(".title")));
            FxTestUtil.runAndWait(() -> {
                assertTrue(pane.isExpanded());
                var text = (TextArea) pane.getContent();
                assertFalse(text.isEditable());
                assertTrue(text.isWrapText());
                try (var original = com.gluonhq.emoji.Emoji.class.getResourceAsStream("noto/" + filename)) {
                    assertNotNull(original);
                    assertEquals(new String(original.readAllBytes(), StandardCharsets.UTF_8), text.getText());
                } catch (java.io.IOException error) {
                    throw new java.io.UncheckedIOException(error);
                }
                text.selectAll();
                assertEquals(text.getText(), text.getSelectedText());
            });
            FxTestUtil.runAndWait(() -> pane.setExpanded(false));
            GuiTestSupport.pulses();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"de", "en", "es", "fr", "it", "ja", "ko", "nl", "pt-BR", "zh-CN"})
    void licenseTabsAreLocalizedAndShowAllComponentsAndUnmodifiedMit(String language) throws Exception {
        FxTestUtil.runAndWait(() -> I18n.setCurrentLanguage(language));
        show(false);
        if ("de".equals(language)) snapshot("application");
        assertEquals(4, FxTestUtil.callAndWait(() -> tabs().getTabs().size()));
        for (int index = 0; index < 4; index++) {
            clickTab(index);
            var group = ComponentCredits.Group.values()[index];
            FxTestUtil.runAndWait(() -> {
                for (var component : ComponentCredits.in(group)) {
                    Node card = dialog.getDialogPane().lookup("#credit-" + component.id());
                    assertNotNull(card, component.name());
                    if (component.id().equals("noto")) {
                        assertTrue(card.lookupAll(".label").stream().filter(Label.class::isInstance)
                                .map(Label.class::cast).anyMatch(label ->
                                        label.getText().equals(I18n.text("credits.emojiNotice"))));
                    }
                    assertTrue(card.getBoundsInLocal().getWidth() > 500, "Cards must use the dialog width");
                    assertTrue(card.lookupAll(".label").stream().filter(Label.class::isInstance)
                            .map(Label.class::cast).anyMatch(label -> label.getText().contains(component.license())));
                }
            });
            if (group == ComponentCredits.Group.ICONS) {
                TextArea notice = FxTestUtil.callAndWait(() ->
                        (TextArea) dialog.getDialogPane().lookup("#tabler-mit-notice"));
                if ("de".equals(language)) snapshot("icons");
                assertEquals(CreditsDialog.iconLicense(), FxTestUtil.callAndWait(notice::getText));
                assertFalse(FxTestUtil.callAndWait(notice::isEditable));
                assertTrue(FxTestUtil.callAndWait(notice::isWrapText));
                FxTestUtil.runAndWait(() -> {
                    Bounds viewport = notice.localToScreen(notice.getBoundsInLocal());
                    assertTrue(viewport.getHeight() >= 140, "License text needs a readable viewport");
                });
            }
        }
        GuiTestSupport.key(KeyCode.ESCAPE);
        assertFalse(FxTestUtil.callAndWait(dialog::isShowing));
    }

    @ParameterizedTest
    @ValueSource(strings = {"de", "en", "es", "fr", "it", "ja", "ko", "nl", "pt-BR", "zh-CN"})
    void thankYouShowsEveryCreatorsGroupAndHasTranslatedUsableActions(String language) throws Exception {
        FxTestUtil.runAndWait(() -> I18n.setCurrentLanguage(language));
        show(true);
        if ("de".equals(language)) snapshot("thanks");
        assertEquals(3, FxTestUtil.callAndWait(() -> tabs().getTabs().size()));
        for (int index = 0; index < 3; index++) {
            clickTab(index);
            var group = ComponentCredits.Group.values()[index + 1];
            FxTestUtil.runAndWait(() -> {
                for (var component : ComponentCredits.in(group)) {
                    Node card = dialog.getDialogPane().lookup("#credit-" + component.id());
                    assertNotNull(card);
                    assertTrue(card.lookupAll(".label").stream().filter(Label.class::isInstance)
                            .map(Label.class::cast).anyMatch(label -> label.getText().contains(component.creators())));
                }
                assertEquals(I18n.text("credits.thanksTitle"), dialog.getTitle());
            });
        }
        var close = FxTestUtil.callAndWait(() -> dialog.getDialogPane().getButtonTypes().stream()
                .filter(button -> button.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE)
                .findFirst().orElseThrow());
        assertEquals(I18n.text("dialog.close"), close.getText());
        click(FxTestUtil.callAndWait(() -> dialog.getDialogPane().lookupButton(close)));
        assertFalse(FxTestUtil.callAndWait(dialog::isShowing));
        assertEquals("false", I18n.preference("acknowledgements.seen", "false"),
                "Opening the window from Help must not silently change the startup preference");
    }

    @Test void firstStartIsRememberedAcrossSettingsReloadAndIndependentProfiles() throws Exception {
        I18n.setPreference("unrelated", "preserved");
        dialog = FxTestUtil.callAndWait(() -> CreditsDialog.showOnFirstStart(owner));
        GuiTestSupport.pulses();
        assertNotNull(dialog);
        assertTrue(FxTestUtil.callAndWait(dialog::isShowing));
        assertNull(FxTestUtil.callAndWait(() -> CreditsDialog.showOnFirstStart(owner)),
                "No second popup while the first one is still open");
        GuiTestSupport.key(KeyCode.ESCAPE);
        FxTestUtil.runAndWait(() -> I18n.setSettingsStore(new ApplicationSettings(profile)));
        assertNull(FxTestUtil.callAndWait(() -> CreditsDialog.showOnFirstStart(owner)));
        assertEquals("preserved", I18n.preference("unrelated", ""));
        FxTestUtil.runAndWait(() -> I18n.setSettingsStore(new ApplicationSettings(profile.resolve("another-user"))));
        dialog = FxTestUtil.callAndWait(() -> CreditsDialog.showOnFirstStart(owner));
        assertNotNull(dialog);
    }

    @Test void unreadableProfileDoesNotPreventDialogAndDisplaysWarning() throws Exception {
        Path directory = profile.resolve("RichMarkDownEditor/settings.properties");
        Files.createDirectories(directory);
        dialog = FxTestUtil.callAndWait(() -> CreditsDialog.showOnFirstStart(owner));
        GuiTestSupport.pulses();
        assertTrue(FxTestUtil.callAndWait(dialog::isShowing));
        assertTrue(FxTestUtil.callAndWait(() -> dialog.getDialogPane().lookup("#credits-status").isVisible()));
    }

    @Test void unwritableProfileDoesNotPreventDialogOrLoseOtherSettings() throws Exception {
        Path blocked = profile.resolve("blocked");
        Files.writeString(blocked, "file in place of configuration directory");
        FxTestUtil.runAndWait(() -> I18n.setSettingsStore(new ApplicationSettings(blocked)));
        dialog = FxTestUtil.callAndWait(() -> CreditsDialog.showOnFirstStart(owner));
        GuiTestSupport.pulses();
        assertTrue(FxTestUtil.callAndWait(dialog::isShowing));
        assertTrue(FxTestUtil.callAndWait(() -> dialog.getDialogPane().lookup("#credits-status").isManaged()));
        assertEquals("file in place of configuration directory", Files.readString(blocked));
    }

    @Test void licenseActionOpensOwnedLicenseWindowWithoutClosingThanks() throws Exception {
        show(true);
        var action = FxTestUtil.callAndWait(() -> dialog.getDialogPane().getButtonTypes().stream()
                .filter(button -> button.getButtonData() == ButtonBar.ButtonData.LEFT).findFirst().orElseThrow());
        click(FxTestUtil.callAndWait(() -> dialog.getDialogPane().lookupButton(action)));
        FxTestUtil.runAndWait(() -> {
            assertTrue(dialog.isShowing());
            assertTrue(Window.getWindows().stream().anyMatch(window ->
                    window.getScene().lookup("#license-dialog") != null));
        });
        GuiTestSupport.key(KeyCode.ESCAPE);
        assertTrue(FxTestUtil.callAndWait(dialog::isShowing));
    }

    @Test void allNewStringsExistInEveryLanguageWithoutFallback() throws Exception {
        for (String language : I18n.supportedLanguages()) {
            Properties properties = new Properties();
            try (var input = CreditsDialog.class.getResourceAsStream(
                    "/de/richmarkdown/i18n/messages_" + language.replace('-', '_') + ".properties")) {
                assertNotNull(input);
                properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            }
            for (String key : java.util.List.of("menu.thanks", "credits.thanksTitle", "credits.thanksHeader",
                    "credits.thanksText", "credits.group.APPLICATION", "credits.group.RUNTIME", "credits.group.ICONS",
                    "credits.group.DEVELOPMENT", "credits.testOnly", "credits.creators", "credits.license",
                    "credits.project", "credits.fullLicense", "credits.copyLink", "credits.iconNotice",
                    "credits.redistribution", "credits.redistributionText", "credits.profileWarning", "credits.emojiNotice")) {
                assertNotNull(properties.getProperty(key), language + ": " + key);
                assertFalse(properties.getProperty(key).isBlank());
            }
        }
    }

    private void snapshot(String name) throws Exception {
        String directory = System.getProperty("mardexas.credits.preview");
        if (directory == null) return;
        var image = FxTestUtil.callAndWait(() -> dialog.getDialogPane()
                .snapshot(new javafx.scene.SnapshotParameters(), null));
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height,
                javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
        var png = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        png.setRGB(0, 0, width, height, pixels, 0, width);
        Files.createDirectories(Path.of(directory));
        assertTrue(javax.imageio.ImageIO.write(png, "png", Path.of(directory, name + ".png").toFile()));
    }

    @Test void inventoryIsImmutableCompleteAndMitNoticeMatchesReviewedOriginal() throws Exception {
        assertEquals(16, ComponentCredits.all().size());
        assertEquals(3, ComponentCredits.in(ComponentCredits.Group.RUNTIME).size());
        assertEquals(3, ComponentCredits.in(ComponentCredits.Group.ICONS).size());
        assertTrue(ComponentCredits.all().stream().noneMatch(component -> component.id().equals("offline")));
        assertEquals(9, ComponentCredits.in(ComponentCredits.Group.DEVELOPMENT).size());
        assertThrows(UnsupportedOperationException.class, () -> ComponentCredits.all().clear());
        assertThrows(UnsupportedOperationException.class, () -> ComponentCredits.in(ComponentCredits.Group.ICONS).clear());
        assertEquals(16, ComponentCredits.all().stream().map(ComponentCredits.Component::id).distinct().count());
        for (var component : ComponentCredits.all()) {
            assertFalse(component.creators().isBlank());
            assertFalse(component.license().isBlank());
            assertFalse(component.version().isBlank());
            assertTrue(component.source().startsWith("https://"));
            assertTrue(component.licenseUrl().startsWith("https://"));
        }
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(CreditsDialog.iconLicense().getBytes(StandardCharsets.UTF_8)));
        assertEquals("d5a3f948ae5cb95bfc4d2f767ef2bf6a778815b0ddb2d1c48037744cf636518e", digest);
    }
}
