package de.richmarkdown.editor;

import com.gluonhq.emoji.Emoji;
import com.gluonhq.emoji.EmojiData;
import com.gluonhq.emoji.EmojiLoaderFactory;
import com.gluonhq.emoji.EmojiSkinTone;
import com.gluonhq.emoji.util.EmojiImageUtils;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.GuiTestSupport;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javafx.event.ActionEvent;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Real shown editor, image pixels and Glass mouse/keyboard input; no mocked emoji renderer. */
class NotoEmojiGuiTest {
    private Stage stage;

    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }

    @AfterEach void close() {
        if (stage != null) FxTestUtil.runAndWait(stage::close);
    }

    @Test void loaderIsLocalImmediatelyReadyAndCannotDiscoverAnAlternateProvider() throws Exception {
        var loader = EmojiLoaderFactory.getEmojiImageLoader();
        assertEquals("com.gluonhq.emoji.NotoEmojiSpriteLoader", loader.getClass().getName());
        assertSame(loader, EmojiLoaderFactory.getEmojiImageLoader());
        assertTrue(loader.isInitialized());
        assertTrue(loader.initialize().isDone());
        assertTrue(loader.initialize().join());
        assertThrows(IllegalArgumentException.class, () -> loader.loadEmojiSprite(16));
        assertThrows(IllegalArgumentException.class, () -> loader.loadEmojiSprite(-1));
        try (var csv = loader.loadCSV()) {
            assertTrue(new String(csv.readAllBytes(), StandardCharsets.UTF_8).startsWith("HASH KEY#"));
        }
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.gluonhq.emoji.DownloadableEmojiSpriteLoader"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.gluonhq.emoji.offline.LocalEmojiSpriteLoader"));
        assertNull(Emoji.class.getResource("offline/sheet_apple_32.png"));
    }

    @Test void licenseResourcesAreAlsoAccessibleWhenStartedOnTheModulePath() throws Exception {
        var artifact = java.nio.file.Path.of(Emoji.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var descriptor = java.lang.module.ModuleFinder.of(artifact).find("com.gluonhq.emoji")
                .orElseThrow().descriptor();
        assertTrue(descriptor.opens().stream().anyMatch(open ->
                open.source().equals("com.gluonhq.emoji.noto") && !open.isQualified()),
                "The license dialog must read the notices across module boundaries under start.sh");
    }

    @ParameterizedTest @ValueSource(ints = {20, 32, 64})
    void allUnicodeCoordinatesIncludingSkinTonesFitTheMatchingCleanSheet(int size) {
        FxTestUtil.runAndWait(() -> {
            Image sheet = switch (size) {
                case 20 -> EmojiImageUtils.getImage20();
                case 32 -> EmojiImageUtils.getImage32();
                default -> EmojiImageUtils.getImage64();
            };
            assertFalse(sheet.isError());
            assertTrue(sheet.getUrl().endsWith("sheet_google_" + size + "_clean.png"));
            assertSame(sheet, switch (size) {
                case 20 -> EmojiImageUtils.getImage20();
                case 32 -> EmojiImageUtils.getImage32();
                default -> EmojiImageUtils.getImage64();
            });
            assertTrue(EmojiData.getEmojiCollection().size() > 1800);
            for (Emoji emoji : EmojiData.getEmojiCollection()) {
                Rectangle2D viewport = switch (size) {
                    case 20 -> EmojiImageUtils.getViewportFor20(emoji);
                    case 32 -> EmojiImageUtils.getViewportFor32(emoji);
                    default -> EmojiImageUtils.getViewportFor64(emoji);
                };
                assertTrue(viewport.getMinX() >= 0 && viewport.getMinY() >= 0, emoji.getUnified());
                assertTrue(viewport.getMaxX() <= sheet.getWidth(), emoji.getUnified());
                assertTrue(viewport.getMaxY() <= sheet.getHeight(), emoji.getUnified());
            }
        });
    }

    @Test void bundledArtworkAndOriginalNoticesMatchTheAuditedSource() throws Exception {
        String[] hashes = {"93a0ead1cf0f82b4b4406dc372a62d75a501954d759a5b497a7de9e47d5aa78e",
                "113896ca5f7d6e9c01edcdb5ba9aeca22bd94c9b541111da1c24616735f2e37a",
                "12f4498fbcb18f1c7291fecdd971573bf6bca6d342041ee3676d42d3de7dc174"};
        int index = 0;
        for (int size : new int[] {20, 32, 64}) {
            try (var stream = Emoji.class.getResourceAsStream("noto/sheet_google_" + size + "_clean.png")) {
                assertNotNull(stream);
                assertEquals(hashes[index++], HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())));
            }
        }
        for (String name : new String[] {"LICENSE-NOTO-APACHE-2.0.txt", "LICENSE-NOTO-FLAGS.txt",
                "LICENSE-EMOJI-DATA-MIT.txt", "LICENSE-GLUON-GPL-3.0.txt", "NOTICE.txt"}) {
            try (var stream = Emoji.class.getResourceAsStream("noto/" + name)) {
                assertNotNull(stream, name);
                assertTrue(stream.readAllBytes().length > 500, name);
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"😀", "👍🏽", "👩‍💻", "🇩🇪"})
    void realEditorRendersNotoAndEditsAfterEmojiWithoutChangingUnicode(String unicode) throws Exception {
        String original = "Start " + unicode + " Ende";
        MarkdownEditor editor = FxTestUtil.callAndWait(() -> {
            var value = new MarkdownEditor();
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(value.getControl()), 750, 460));
            stage.show();
            value.setMarkdown(original);
            return value;
        });
        GuiTestSupport.pulses();
        ImageView glyph = FxTestUtil.callAndWait(() -> editor.getControl().lookupAll("*").stream()
                .filter(ImageView.class::isInstance).map(ImageView.class::cast)
                .filter(view -> view.getProperties().containsKey(EmojiImageUtils.IMAGE_VIEW_EMOJI_PROPERTY))
                .findFirst().orElseThrow());
        FxTestUtil.runAndWait(() -> {
            assertEquals(original, editor.getMarkdown());
            assertFalse(glyph.getImage().isError());
            assertEquals(EmojiData.emojiFromUnicodeString(unicode).orElseThrow().getUnified(),
                    glyph.getProperties().get(EmojiImageUtils.IMAGE_VIEW_EMOJI_PROPERTY));
            var pixels = glyph.snapshot(null, null).getPixelReader();
            assertNotNull(pixels);
            int colorful = 0;
            var bounds = glyph.getBoundsInLocal();
            for (int x = 1; x < bounds.getWidth() - 1; x++) {
                for (int y = 1; y < bounds.getHeight() - 1; y++) {
                    var color = pixels.getColor(x, y);
                    if (color.getOpacity() > .5 && color.getSaturation() > .25) colorful++;
                }
            }
            assertTrue(colorful > 10, "A colored emoji must actually be rendered");
        });
        var bounds = FxTestUtil.callAndWait(glyph::getBoundsInLocal);
        GuiTestSupport.click(glyph, bounds.getMaxX() - 1, bounds.getCenterY());
        GuiTestSupport.key(KeyCode.X);
        FxTestUtil.runAndWait(() -> assertEquals("Start " + unicode + "x Ende", editor.getMarkdown()));
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().undo().execute(new ActionEvent()));
        GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> assertEquals(original, editor.getMarkdown()));
        FxTestUtil.runAndWait(() -> editor.getControl().getActionFactory().redo().execute(new ActionEvent()));
        GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> assertEquals("Start " + unicode + "x Ende", editor.getMarkdown()));
    }

    @Test void selectingSkinToneChangesOnlyTheViewAndUnknownUnicodeIsPreserved() throws Exception {
        String original = "Daumen 👍 und unbekannt 🫨";
        MarkdownEditor editor = FxTestUtil.callAndWait(() -> {
            var value = new MarkdownEditor();
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(value.getControl()), 750, 460));
            stage.show();
            value.setMarkdown(original);
            value.getControl().setSkinTone(EmojiSkinTone.DARK_SKIN_TONE);
            return value;
        });
        GuiTestSupport.pulses();
        FxTestUtil.runAndWait(() -> {
            assertEquals(original, editor.getMarkdown());
            assertTrue(editor.getControl().lookupAll("*").stream()
                    .anyMatch(node -> "1F44D-1F3FF".equals(node.getProperties().get("emoji_unified"))));
        });
    }
}
