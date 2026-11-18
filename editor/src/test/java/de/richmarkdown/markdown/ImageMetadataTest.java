package de.richmarkdown.markdown;

import com.gluonhq.richtextarea.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ImageMetadataTest {
    @TempDir Path directory;
    private final MarkdownParser parser = new MarkdownParser();
    private final MarkdownWriter writer = new MarkdownWriter();
    @Test void markdownEscapedAlternativeTextSurvivesRepeatedRoundTrips() {
        String source = "![Diagramm \\[A\\] \\\\ Ω](https://example.org/image.png)";
        for (int i = 0; i < 3; i++) {
            Document parsed = parser.parseInto(source);
            var image = (ImageDecoration) parsed.getDecorations().getFirst().getDecoration();
            assertEquals("Diagramm [A] \\ Ω", AccessibleImageDecoration.alt(image));
            source = writer.write(parsed);
        }
        assertEquals("![Diagramm \\[A\\] \\\\ Ω](https://example.org/image.png)", source);
    }
    @Test void htmlLinkedSizedImageRetainsAlternativeTextAndEscaping() {
        String source = "<a href=\"https://example.org\"><img src=\"https://example.org/a.png\" width=\"90\" height=\"45\" alt=\"A &amp; &quot;B&quot;\" /></a>";
        for (int i = 0; i < 3; i++) {
            Document document = parser.parseInto(source);
            ImageDecoration image = (ImageDecoration) document.getDecorations().getFirst().getDecoration();
            assertEquals("A & \"B\"", AccessibleImageDecoration.alt(image));
            assertEquals(90, image.getWidth()); assertEquals(45, image.getHeight()); assertEquals("https://example.org", image.getLink());
            source = writer.write(document);
        }
    }
    @Test void missingImagePlaceholderPreservesAllMetadata() {
        Document document = parser.parseInto("<img src=\"missing.png\" width=\"80\" alt=\"Description\" />", directory);
        String output = writer.write(document);
        assertTrue(output.contains("Description")); assertTrue(output.contains("80")); assertTrue(output.contains("missing.png"));
        assertNotNull(parser.normalizeComparisonImages(document, null));
        var old = new ImagePlaceholder("x", null, -1, -1);
        assertEquals("", ImagePlaceholder.fromMarker(old.marker()).orElseThrow().altText());
        var metadata = new ImagePlaceholder("x", "link", 20, 10, "[a] | Ω");
        assertEquals(metadata, ImagePlaceholder.fromMarker(metadata.marker()).orElseThrow());
    }
    @Test void unsafeMarkdownImagePathUsesHtmlAndRetainsOriginalAddress() {
        var image = new AccessibleImageDecoration("https://example.org/a(b).png", -1, -1, null, "A");
        Document document = new Document("\ufeff", List.of(new DecorationModel(0, 1, image, ParagraphDecoration.builder().presets().build())), 0);
        String output = writer.write(document); assertTrue(output.contains("<img"));
        Document reparsed = parser.parseInto(output);
        assertEquals(image, reparsed.getDecorations().getFirst().getDecoration());
    }
}
