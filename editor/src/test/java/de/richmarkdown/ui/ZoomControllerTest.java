package de.richmarkdown.ui;

import com.gluonhq.richtextarea.RichTextArea;
import de.richmarkdown.FxTestUtil;
import javafx.scene.Scene;
import javafx.scene.control.Slider;
import javafx.scene.input.ScrollEvent;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD: Zoom-Mechanismus (Word/LibreOffice-artig, Schieberegler rechts unten)
 * – PROJEKTPLAN 7.1 Nachtrag.
 */
class ZoomControllerTest {

    private RichTextArea control;
    private javafx.scene.layout.Region viewport;
    private ZoomController zoom;
    private Stage stage;

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    @BeforeEach
    void setUp() {
        FxTestUtil.runAndWait(() -> {
            control = new RichTextArea();
            zoom = new ZoomController(control);
            viewport = zoom.getViewport();
            stage = new Stage();
            stage.setScene(new Scene(zoom.getViewport(), 600, 400));
            stage.show();
        });
    }

    @AfterAll
    static void tearDown() {
        // kein hide(): blockt den FX-Thread in Monocle headless
    }

    private void resizeViewport(double width, double height) {
        FxTestUtil.runAndWait(() -> {
            viewport.resize(width, height);
            viewport.layout();
        });
    }

    /** Zoom setzen und das Layout synchron nachziehen (in der App: Puls). */
    private void zoomAndLayout(double percent) {
        FxTestUtil.runAndWait(() -> {
            zoom.setZoom(percent);
            viewport.layout();
        });
    }

    /** Feuert ein Strg+Mausrad-Event (deltaY > 0 = hoch, < 0 = runter). */
    private void fireCtrlScroll(double deltaY) {
        FxTestUtil.runAndWait(() -> control.fireEvent(new ScrollEvent(
                ScrollEvent.SCROLL,
                0, 0, 0, 0,                          // scroll/scroll/screen/screen
                false, true, false, false,           // shift, control, alt, meta
                false, false,                        // direct, inertia
                0, deltaY,                           // deltaX, deltaY
                1, 0,                                // multiplier, textDeltaV
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0,
                1, null)));                          // touchCount, pickResult
    }

    private Scale scaleTransform() {
        return FxTestUtil.callAndWait(() -> control.getTransforms().stream()
                .filter(t -> t instanceof Scale)
                .map(t -> (Scale) t)
                .findFirst()
                .orElse(null));
    }

    // ---------- Grundzustand ----------

    @Test
    void initialZoomIs100Percent() {
        assertEquals(100d, zoom.getZoom(), 0.001);
    }

    @Test
    void scaleTransformIsInstalled() {
        assertNotNull(scaleTransform());
        assertEquals(1d, scaleTransform().getX(), 0.001);
        assertEquals(1d, scaleTransform().getY(), 0.001);
    }

    // ---------- Zoom setzen ----------

    @Test
    void setZoomAppliesScale() {
        zoom.setZoom(150);
        assertEquals(1.5d, scaleTransform().getX(), 0.001);
        assertEquals(1.5d, scaleTransform().getY(), 0.001);
    }

    @Test
    void setZoomBelow50Shrinks() {
        zoom.setZoom(50);
        assertEquals(0.5d, scaleTransform().getX(), 0.001);
    }

    @Test
    void zoomIsClampedToRange() {
        zoom.setZoom(1000);
        assertEquals(ZoomController.MAX_ZOOM, zoom.getZoom(), 0.001);

        zoom.setZoom(1);
        assertEquals(ZoomController.MIN_ZOOM, zoom.getZoom(), 0.001);
    }

    // ---------- Inverses Layout (Inhalt passt ins Viewport) ----------

    @Test
    void controlIsResizedInverselyToZoom() {
        resizeViewport(600, 400);
        zoom.setZoom(200);

        // Logische Breite = Viewport / Zoom → skaliert passt es ins Viewport
        assertEquals(300d, FxTestUtil.callAndWait(control::getPrefWidth), 0.5);
        assertEquals(200d, FxTestUtil.callAndWait(control::getPrefHeight), 0.5);
    }

    @Test
    void controlFillsViewportAt100Percent() {
        resizeViewport(600, 400);
        zoom.setZoom(100);

        assertEquals(600d, FxTestUtil.callAndWait(control::getPrefWidth), 0.5);
        assertEquals(400d, FxTestUtil.callAndWait(control::getPrefHeight), 0.5);
    }

    @Test
    void controlResizingFollowsViewportChanges() {
        resizeViewport(800, 600);
        zoom.setZoom(100);
        assertEquals(800d, FxTestUtil.callAndWait(control::getPrefWidth), 0.5);

        resizeViewport(400, 300);
        assertEquals(400d, FxTestUtil.callAndWait(control::getPrefWidth), 0.5);
    }

    // ---------- Visual bounds: Inhalt überdeckt das Viewport lückenlos ----------

    @Test
    void scaledContentCoversViewportWhenZoomingIn() {
        resizeViewport(600, 400);
        zoomAndLayout(200);
        assertCoversViewport(600, 400);
    }

    @Test
    void scaledContentCoversViewportAtAllZoomLevels() {
        resizeViewport(600, 400);
        for (double percent : new double[]{25, 50, 100, 150, 200, 300, 400}) {
            zoomAndLayout(percent);
            assertCoversViewport(600, 400);
        }
    }

    @Test
    void scaledContentCoversViewportAfterViewportResize() {
        resizeViewport(800, 600);
        zoomAndLayout(175);
        assertCoversViewport(800, 600);

        resizeViewport(400, 300);
        assertCoversViewport(400, 300);
    }

    /**
     * Der skalierte Inhalt muss das Viewport lückenlos überdecken – sonst
     * entstehen graue Ränder (Zentrierung/Begrenzung durch den Container).
     * Ein kleiner Überhang ist zulässig (visuelle Grenzen des Controls
     * überschreiten die Layout-Grenzen um den CSS-Border der Bibliothek).
     */
    private void assertCoversViewport(double width, double height) {
        javafx.geometry.Bounds visual = FxTestUtil.callAndWait(control::getBoundsInParent);
        double zoomPercent = zoom.getZoom();
        assertTrue(visual.getMinX() <= 0.5,
                "grauer Rand links bei " + zoomPercent + "%: minX=" + visual.getMinX());
        assertTrue(visual.getMinY() <= 0.5,
                "grauer Rand oben bei " + zoomPercent + "%: minY=" + visual.getMinY());
        assertTrue(visual.getMaxX() >= width - 0.5,
                "grauer Rand rechts bei " + zoomPercent + "%: maxX=" + visual.getMaxX());
        assertTrue(visual.getMaxY() >= height - 0.5,
                "grauer Rand unten bei " + zoomPercent + "%: maxY=" + visual.getMaxY());

        // Überhang begrenzt (Border-Overhang skaliert mit dem Zoom)
        double maxOverhang = 5 * zoomPercent / 100;
        assertTrue(visual.getMinX() >= -maxOverhang,
                "Überhang links zu groß bei " + zoomPercent + "%: " + visual.getMinX());
        assertTrue(visual.getMinY() >= -maxOverhang,
                "Überhang oben zu groß bei " + zoomPercent + "%: " + visual.getMinY());
        assertTrue(visual.getMaxX() <= width + maxOverhang,
                "Überhang rechts zu groß bei " + zoomPercent + "%: " + visual.getMaxX());
        assertTrue(visual.getMaxY() <= height + maxOverhang,
                "Überhang unten zu groß bei " + zoomPercent + "%: " + visual.getMaxY());
    }

    // ---------- Zoom In/Out/Reset ----------

    @Test
    void zoomInIncreasesByStep() {
        zoom.setZoom(100);
        zoom.zoomIn();
        assertEquals(110d, zoom.getZoom(), 0.001);
    }

    @Test
    void zoomOutDecreasesByStep() {
        zoom.setZoom(100);
        zoom.zoomOut();
        assertEquals(90d, zoom.getZoom(), 0.001);
    }

    @Test
    void zoomInStopsAtMaximum() {
        zoom.setZoom(ZoomController.MAX_ZOOM);
        zoom.zoomIn();
        assertEquals(ZoomController.MAX_ZOOM, zoom.getZoom(), 0.001);
    }

    @Test
    void zoomOutStopsAtMinimum() {
        zoom.setZoom(ZoomController.MIN_ZOOM);
        zoom.zoomOut();
        assertEquals(ZoomController.MIN_ZOOM, zoom.getZoom(), 0.001);
    }

    @Test
    void resetZoomRestores100() {
        zoom.setZoom(250);
        zoom.resetZoom();
        assertEquals(100d, zoom.getZoom(), 0.001);
    }

    // ---------- Strg+Mausrad ----------

    @Test
    void ctrlWheelUpZoomsIn() {
        zoom.setZoom(100);
        fireCtrlScroll(10);
        assertEquals(110d, zoom.getZoom(), 0.001);
    }

    @Test
    void ctrlWheelDownZoomsOut() {
        zoom.setZoom(100);
        fireCtrlScroll(-10);
        assertEquals(90d, zoom.getZoom(), 0.001);
    }

    @Test
    void wheelWithoutCtrlDoesNotZoom() {
        zoom.setZoom(100);
        FxTestUtil.runAndWait(() -> control.fireEvent(new ScrollEvent(
                ScrollEvent.SCROLL,
                0, 0, 0, 0,
                false, false, false, false,
                false, false,
                0, 10,
                1, 0,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0,
                1, null)));
        assertEquals(100d, zoom.getZoom(), 0.001);
    }

    // ---------- Schieberegler ----------

    @Test
    void sliderReflectsZoom() {
        Slider slider = zoom.createSlider();
        zoom.setZoom(175);
        assertEquals(175d, slider.getValue(), 0.001);
    }

    @Test
    void sliderChangesZoom() {
        Slider slider = zoom.createSlider();
        FxTestUtil.runAndWait(() -> slider.setValue(225));
        assertEquals(225d, zoom.getZoom(), 0.001);
    }

    @Test
    void sliderHasSensibleRange() {
        Slider slider = zoom.createSlider();
        assertEquals(ZoomController.MIN_ZOOM, slider.getMin(), 0.001);
        assertEquals(ZoomController.MAX_ZOOM, slider.getMax(), 0.001);
        assertEquals(100d, slider.getValue(), 0.001);
    }

    @Test
    void zoomLabelShowsPercentage() {
        javafx.scene.control.Label label = zoom.createLabel();
        zoom.setZoom(140);
        assertEquals("140%", FxTestUtil.callAndWait(label::getText));
    }

    @Test
    void zoomControlsPlaceLabelNextToSlider() {
        // Wie in Word/LibreOffice: Prozentanzeige NEBEN dem Schieberegler,
        // nicht davor/dahinter gestapelt
        javafx.scene.Parent controls = zoom.createControls();
        Slider slider = zoom.createSlider();
        javafx.scene.control.Label label = zoom.createLabel();
        FxTestUtil.runAndWait(() -> {
            stage.setScene(new Scene(controls, 600, 400));
            controls.applyCss();
            controls.requestLayout();
            controls.layout();
        });
        FxTestUtil.flushFxEvents();
        var sliderBounds = FxTestUtil.callAndWait(() -> slider.localToScene(slider.getBoundsInLocal()));
        var labelBounds = FxTestUtil.callAndWait(() -> label.localToScene(label.getBoundsInLocal()));
        assertTrue(labelBounds.getMinX() >= sliderBounds.getMaxX() + 1d,
                "label must be right of the slider, slider=" + sliderBounds + " label=" + labelBounds);
        // gleiche Zeile (vertikale Überlappung)
        assertTrue(labelBounds.getMaxY() > sliderBounds.getMinY()
                        && labelBounds.getMinY() < sliderBounds.getMaxY(),
                "label must share the slider row");
    }

    @Test
    void zoomPropertyIsObservable() {
        javafx.beans.property.DoubleProperty observed = new javafx.beans.property.SimpleDoubleProperty();
        observed.bind(zoom.zoomProperty());
        zoom.setZoom(130);
        assertEquals(130d, observed.get(), 0.001);
    }

    @Test
    void zoomDoesNotAffectDocumentModel() {
        // Der Zoom darf das Dokumentmodell nicht verändern (reine Ansicht)
        String before = FxTestUtil.callAndWait(() -> {
            control.getActionFactory().insertText("text").execute(new javafx.event.ActionEvent());
            return "text";
        });
        zoom.setZoom(300);
        zoom.setZoom(50);
        assertEquals("text", before);
        assertFalse(control.getTransforms().isEmpty());
        assertTrue(scaleTransform().getX() < 1d);
    }
}
