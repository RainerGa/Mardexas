package de.richmarkdown.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ImageAssetsTest {
    @TempDir Path directory;
    @Test void resolvesRelativeAndSpacedPathsAgainstDocument() {
        assertEquals(directory.resolve("images/my image.png").toUri().toASCIIString(), ImageAssets.resolve("images/my image.png", directory));
        assertEquals(directory.resolve("image.png").toUri().toASCIIString(), ImageAssets.resolve(directory.resolve("image.png").toString(), null));
        assertThrows(IllegalArgumentException.class, () -> ImageAssets.resolve("image.png", null));
    }
    @Test void validatesSchemesAndNetworkHost() {
        assertEquals("https://example.org/image.png", ImageAssets.resolve(" https://example.org/image.png ", null));
        for (String target : new String[]{"", "javascript:alert(1)", "https:/invalid", "a\nb"})
            assertThrows(IllegalArgumentException.class, () -> ImageAssets.resolve(target, directory), target);
    }
    @Test void importsWithoutOverwritingAndReusesAlreadyManagedAsset() throws Exception {
        Path input = directory.resolve("image.png"); Files.writeString(input, "original");
        Path assets = directory.resolve("images");
        Path first = ImageAssets.copyInto(input, assets); Files.writeString(input, "second");
        Path second = ImageAssets.copyInto(input, assets);
        assertNotEquals(first, second); assertEquals("original", Files.readString(first)); assertEquals("second", Files.readString(second));
        assertEquals(first, ImageAssets.copyInto(first, assets));
    }
}
