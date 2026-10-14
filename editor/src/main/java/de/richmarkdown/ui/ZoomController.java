package de.richmarkdown.ui;

import com.gluonhq.richtextarea.RichTextArea;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.transform.Scale;

/**
 * Zoom-Mechanismus für das gesamte Dokument – Word/LibreOffice-artig mit
 * Schieberegler rechts unten (PROJEKTPLAN 7.1, Nachtrag).
 *
 * <p>Funktionsweise: Das {@link RichTextArea} wird mit einem {@link Scale}-Transform
 * skaliert (reine Ansicht – das Dokumentmodell bleibt unverändert). Damit der
 * Inhalt bei jedem Zoomfaktor exakt in den Viewport passt, wird die logische
 * Größe des Controls invers zum Zoomfaktor gesetzt:
 * {@code logischeBreite = viewportBreite / zoom}.
 *
 * <ul>
 *   <li>Schieberegler (25 %–400 %) mit Prozentanzeige</li>
 *   <li>Strg+Mausrad zum Vergrößern und Verkleinern</li>
 *   <li>Ein-/Auszoomen in 10-Prozentpunkt-Schritten sowie Rücksetzen auf 100 %</li>
 * </ul>
 *
 * <p>Der Controller verwaltet keine Persistenz. Die aufrufende Fensterebene
 * liest und speichert den Zoomzustand über die Anwendungseinstellungen.
 */
public class ZoomController {

    /** Minimaler Zoomfaktor in Prozent. */
    public static final double MIN_ZOOM = 25;
    /** Maximaler Zoomfaktor in Prozent. */
    public static final double MAX_ZOOM = 400;
    /** Standard-Zoomfaktor in Prozent. */
    public static final double DEFAULT_ZOOM = 100;
    /** Schrittweite für Zoom-In/Out in Prozentpunkten. */
    public static final double ZOOM_STEP = 10;


    private final RichTextArea control;
    private final Viewport viewport;
    private final Scale scale = new Scale(1, 1, 0, 0);
    private final ReadOnlyDoubleWrapper zoom = new ReadOnlyDoubleWrapper(DEFAULT_ZOOM);

    private Slider slider;
    private Label label;
    private HBox controls;

    /**
     * Erstellt einen Zoom-Controller einschließlich eigenem Viewport.
     *
     * <p>Der Viewport ist ein eigenes
     * {@link Region}, das den Editor **unbeengt** an Position (0,0) mit seiner
     * Preferred Size platziert – ein StackPane würde zentrieren und auf die
     * eigene Größe begrenzen, was graue Ränder links/oben bzw. rechts/unten
     * erzeugen würde.
     *
     * @param control zu skalierendes Rich-Text-Area-Control
     */
    public ZoomController(RichTextArea control) {
        this.control = control;
        this.viewport = new Viewport(control);

        control.getTransforms().add(scale);
        // Der Control-Resize erfolgt ausschließlich über die gebundene Preferred Size
        control.setMaxWidth(Region.USE_PREF_SIZE);
        control.setMaxHeight(Region.USE_PREF_SIZE);
        control.prefWidthProperty().bind(Bindings.createDoubleBinding(
                this::logicalWidth, viewport.widthProperty(), zoom.getReadOnlyProperty()));
        control.prefHeightProperty().bind(Bindings.createDoubleBinding(
                this::logicalHeight, viewport.heightProperty(), zoom.getReadOnlyProperty()));

        zoom.addListener((obs, old, nv) -> applyZoom());

        // Strg+Mausrad wie in Word/LibreOffice
        control.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                event.consume();
                if (event.getDeltaY() > 0) {
                    zoomIn();
                } else if (event.getDeltaY() < 0) {
                    zoomOut();
                }
            }
        });

        applyZoom();
    }

    /**
     * Liefert den Viewport, der den Editor aufnimmt und in die UI eingefügt wird.
     *
     * @return vom Controller verwalteter Viewport
     */
    public Region getViewport() {
        return viewport;
    }

    // ---------- Öffentliche API ----------

    /**
     * Liefert den aktuellen Zoomfaktor in Prozent.
     *
     * @return begrenzter Zoomwert im Bereich {@link #MIN_ZOOM} bis {@link #MAX_ZOOM}
     */
    public double getZoom() {
        return zoom.get();
    }

    /**
     * Liefert eine lesbare Observable-Property für Bindings und Beobachter.
     *
     * @return Property des aktuellen Zoomfaktors
     */
    public ReadOnlyDoubleProperty zoomProperty() {
        return zoom.getReadOnlyProperty();
    }

    /**
     * Setzt den Zoomfaktor in Prozent.
     *
     * @param percent gewünschter Wert; wird auf {@link #MIN_ZOOM} bis
     *                {@link #MAX_ZOOM} begrenzt
     */
    public void setZoom(double percent) {
        zoom.set(clamp(percent));
    }

    /** Erhöht den Zoom um {@link #ZOOM_STEP}, begrenzt auf den Maximalwert. */
    public void zoomIn() {
        setZoom(zoom.get() + ZOOM_STEP);
    }

    /** Verringert den Zoom um {@link #ZOOM_STEP}, begrenzt auf den Minimalwert. */
    public void zoomOut() {
        setZoom(zoom.get() - ZOOM_STEP);
    }

    /** Setzt den Zoom auf {@link #DEFAULT_ZOOM} Prozent zurück. */
    public void resetZoom() {
        setZoom(DEFAULT_ZOOM);
    }

    // ---------- UI-Komponenten (Schieberegler rechts unten) ----------

    /**
     * Liefert den Schieberegler für den Zoom.
     *
     * <p>Der Regler wird einmalig erzeugt und anschließend bidirektional mit
     * dem Zoomwert synchronisiert.
     *
     * @return wiederverwendbarer Zoom-Schieberegler
     */
    public Slider createSlider() {
        if (slider == null) {
            slider = new Slider(MIN_ZOOM, MAX_ZOOM, DEFAULT_ZOOM);
            slider.setPrefWidth(120);
            slider.setFocusTraversable(false);
            // bidirektionale Bindung: Regler ↔ Zoomfaktor
            slider.valueProperty().addListener((obs, old, nv) -> {
                if (Math.abs(nv.doubleValue() - zoom.get()) > 0.001) {
                    setZoom(nv.doubleValue());
                }
            });
            zoom.addListener((obs, old, nv) -> {
                if (Math.abs(slider.getValue() - nv.doubleValue()) > 0.001) {
                    slider.setValue(nv.doubleValue());
                }
            });
        }
        return slider;
    }

    /**
     * Liefert die an den Zoomwert gebundene Prozentanzeige.
     *
     * @return wiederverwendbares Label
     */
    public Label createLabel() {
        if (label == null) {
            label = new Label();
            label.textProperty().bind(Bindings.format("%.0f%%", zoom.getReadOnlyProperty()));
        }
        return label;
    }

    /**
     * Liefert die kompakte Zoomsteuerung aus Regler und Prozentanzeige.
     *
     * @return wiederverwendbarer Container für die Statusleiste
     */
    public HBox createControls() {
        if (controls == null) {
            controls = new HBox(4, createSlider(), createLabel());
            controls.setAlignment(Pos.CENTER_LEFT);
            controls.getStyleClass().add("zoom-controls");
        }
        return controls;
    }

    // ---------- Interna ----------

    private double logicalWidth() {
        return viewport.getWidth() / zoomFactor();
    }

    private double logicalHeight() {
        return viewport.getHeight() / zoomFactor();
    }

    private double zoomFactor() {
        return zoom.get() / 100d;
    }

    private void applyZoom() {
        double factor = zoomFactor();
        scale.setX(factor);
        scale.setY(factor);
        // Der Control ändert seine Preferred Size → neu layouten
        viewport.requestLayout();
    }

    private double clamp(double percent) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, percent));
    }

    /**
     * Viewport für den Editor: platziert das Kind unbeengt an (0,0) mit seiner
     * Preferred Size (keine Zentrierung, keine Größenbegrenzung).
     */
    private static final class Viewport extends Region {

        private final javafx.scene.Node child;

        private Viewport(javafx.scene.Node child) {
            getChildren().add(child);
            this.child = child;
        }

        @Override
        protected void layoutChildren() {
            child.resizeRelocate(0, 0, child.prefWidth(-1), child.prefHeight(-1));
        }

        @Override
        protected double computePrefWidth(double height) {
            return 0;
        }

        @Override
        protected double computePrefHeight(double width) {
            return 0;
        }
    }
}
