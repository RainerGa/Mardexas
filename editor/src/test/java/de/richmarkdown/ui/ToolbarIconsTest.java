package de.richmarkdown.ui;

import de.richmarkdown.FxTestUtil;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD Phase 13: Toolbar-Icons – Vektor-Icons für Listen-, Einzugs- und
 * Inline-Code-Buttons (wie in Word/LibreOffice üblich) statt Textlabels.
 */
class ToolbarIconsTest {

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    @BeforeEach
    void setUp() {
        // Icons werden auf dem FX-Thread erzeugt (Nodes sind nicht thread-sicher)
        FxTestUtil.runAndWait(() -> {
        });
    }

    private Node icon(java.util.function.Supplier<Node> factory) {
        return FxTestUtil.callAndWait(factory::get);
    }

    @Test
    void bulletListIconShowsBulletsAndLines() {
        Node icon = icon(ToolbarIcons::bulletListIcon);
        assertNotNull(icon);
        // drei Bullet-Punkte und drei Zeilen
        assertEquals(3, ToolbarIcons.countOf(icon, javafx.scene.shape.Circle.class));
        assertEquals(3, ToolbarIcons.countOf(icon, javafx.scene.shape.Rectangle.class));
        assertTrue(icon.getStyleClass().contains("rme-icon-bullet-list"));
        assertTrue(ToolbarIcons.ICON_STYLE_CLASSES.stream().anyMatch(icon.getStyleClass()::contains));
    }

    @Test
    void numberedListIconShowsNumbersAndLines() {
        Node icon = icon(ToolbarIcons::numberedListIcon);
        assertNotNull(icon);
        assertEquals(3, ToolbarIcons.countOf(icon, javafx.scene.text.Text.class));
        assertEquals(3, ToolbarIcons.countOf(icon, javafx.scene.shape.Rectangle.class));
        assertTrue(icon.getStyleClass().contains("rme-icon-numbered-list"));
    }

    @Test
    void indentIconIsVectorGraphic() {
        Node icon = icon(ToolbarIcons::indentIcon);
        assertNotNull(icon);
        assertEquals(3, ToolbarIcons.countOf(icon, javafx.scene.shape.Rectangle.class));
        assertTrue(icon.getStyleClass().contains("rme-icon-indent"));
    }

    @Test
    void outdentIconIsVectorGraphic() {
        Node icon = icon(ToolbarIcons::outdentIcon);
        assertNotNull(icon);
        assertEquals(3, ToolbarIcons.countOf(icon, javafx.scene.shape.Rectangle.class));
        assertTrue(icon.getStyleClass().contains("rme-icon-outdent"));
    }

    @Test
    void inlineCodeIconIsVectorGraphic() {
        Node icon = icon(ToolbarIcons::inlineCodeIcon);
        assertNotNull(icon);
        // zwei spitze Klammern und ein Schrägstrich
        assertEquals(3, ToolbarIcons.countOf(icon, javafx.scene.shape.Polygon.class));
        assertTrue(icon.getStyleClass().contains("rme-icon-inline-code"));
    }

    @Test
    void iconsAreIconSized() {
        for (Node icon : new Node[]{
                icon(ToolbarIcons::bulletListIcon),
                icon(ToolbarIcons::numberedListIcon),
                icon(ToolbarIcons::indentIcon),
                icon(ToolbarIcons::outdentIcon),
                icon(ToolbarIcons::inlineCodeIcon)}) {
            assertTrue(icon instanceof Region || icon instanceof javafx.scene.Group,
                    "Icon sollte ein zeichenbarer Node sein: " + icon);
            double w = icon.prefWidth(-1);
            double h = icon.prefHeight(-1);
            assertTrue(w > 0 && w <= ToolbarIcons.ICON_SIZE + 0.5,
                    "Icon-Breite außerhalb des üblichen Rasters: " + w);
            assertTrue(h > 0 && h <= ToolbarIcons.ICON_SIZE + 0.5,
                    "Icon-Höhe außerhalb des üblichen Rasters: " + h);
        }
    }

    @Test
    void iconsCarryShapeStyleClassForTheming() {
        for (Node icon : new Node[]{
                icon(ToolbarIcons::bulletListIcon),
                icon(ToolbarIcons::numberedListIcon),
                icon(ToolbarIcons::indentIcon),
                icon(ToolbarIcons::outdentIcon),
                icon(ToolbarIcons::inlineCodeIcon)}) {
            assertEquals(0, countUnthemedShapes(icon),
                    "jedes Shape-Element soll über " + ToolbarIcons.SHAPE_STYLE_CLASS
                            + " einfärbbar sein: " + icon);
            assertTrue(ToolbarIcons.countOf(icon, javafx.scene.shape.Shape.class) > 0,
                    "Icon ohne Shape-Elemente: " + icon);
        }
    }

    @Test
    void iconsPaintVisiblePixels() {
        for (java.util.function.Supplier<Node> factory : new java.util.function.Supplier[]{
                ToolbarIcons::bulletListIcon, ToolbarIcons::numberedListIcon,
                ToolbarIcons::indentIcon, ToolbarIcons::outdentIcon,
                ToolbarIcons::inlineCodeIcon}) {
            Node icon = icon(factory);
            javafx.scene.image.Image image = FxTestUtil.callAndWait(() -> {
                return icon.snapshot(new javafx.scene.SnapshotParameters(), null);
            });
            long painted = 0;
            var reader = image.getPixelReader();
            for (int x = 0; x < image.getWidth(); x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    if (reader.getArgb(x, y) != 0) {
                        painted++;
                    }
                }
            }
            assertTrue(painted > 4, "Icon zeichnet keine sichtbaren Pixel: " + icon);
        }
    }

    private int countUnthemedShapes(Node node) {
        int count = 0;
        if (node instanceof javafx.scene.shape.Shape
                && !node.getStyleClass().contains(ToolbarIcons.SHAPE_STYLE_CLASS)) {
            count++;
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                count += countUnthemedShapes(child);
            }
        }
        return count;
    }
}
