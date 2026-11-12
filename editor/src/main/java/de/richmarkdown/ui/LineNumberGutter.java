/*
 * Copyright (C) 2026 Rainer Garbotz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.richmarkdown.ui;

import com.gluonhq.richtextarea.RichTextArea;
import javafx.application.Platform;
import javafx.beans.value.ObservableDoubleValue;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Zeilennummern-Anzeige (Gutter) links vom Editor – ergänzend zur
 * Dokumentstruktur (PROJEKTPLAN, Nachtrag Zeilennummern).
 *
 * <p>Die Anzeige spiegelt die <b>sichtbaren</b> Absätze des Editors: für jede
 * sichtbare Zeile der Paragraph-Liste wird die Nummer (1-basiert) auf gleicher
 * Höhe wie die Zeile selbst dargestellt. Die Position wird nicht selbst
 * berechnet, sondern aus der Geometrie der Editor-Zellen abgeleitet
 * ({@code localToScene}) – dadurch passen Höhenversatz, Zeilenumbrüche,
 * Überschrift-Größen und der Zoomfaktor ohne eigene Maßannahmen.
 *
 * <p>Aktualisiert wird bei: Scrollen (Scrollbalken der Editor-Liste sowie
 * Scroll-Ereignisse), Caret-Bewegung, Dokument-/Textänderung, Zoomänderung,
 * Größenänderung und beim Einblenden. Die Nummern bezeichnen sichtbare
 * Absatzzeilen und sind keine Markdown-Quellzeilenzähler.
 */
public class LineNumberGutter extends Region implements AutoCloseable {

    private static final String GUTTER_STYLE_CLASS = "line-number-gutter";
    private static final String NUMBER_STYLE_CLASS = "line-number";

    private static final double BASE_FONT_SIZE = 12d;
    private static final double TOP_OFFSET = 2d;
    private static final double H_PADDING = 8d;
    private static final double MIN_WIDTH = 42d;
    private static final double MAX_WIDTH = 110d;

    private final RichTextArea control;
    private final ObservableDoubleValue zoomPercent;

    private ListView<?> paragraphListView;
    private ScrollBar wiredScrollBar;
    private javafx.beans.InvalidationListener scrollBarListener;
    private final List<Label> numberLabels = new ArrayList<>();
    private boolean dirty;
    private boolean closed;
    private javafx.scene.Scene pulseScene;
    private final Runnable postLayout = () -> { if (dirty && !closed) { dirty = false; sync(); } };
    private final List<Runnable> removers = new ArrayList<>();
    private final javafx.event.EventHandler<ScrollEvent> scrolled = event -> requestSync();

    private void observe(javafx.beans.Observable observable) {
        javafx.beans.InvalidationListener listener = ignored -> {
            if (observable instanceof javafx.beans.value.ObservableValue<?> value) value.getValue();
            requestSync();
        };
        observable.addListener(listener);
        removers.add(() -> observable.removeListener(listener));
    }

    @Override public void close() {
        closed = true;
        if (pulseScene != null) pulseScene.removePostLayoutPulseListener(postLayout);
        if (wiredScrollBar != null && scrollBarListener != null) wiredScrollBar.valueProperty().removeListener(scrollBarListener);
        control.removeEventHandler(ScrollEvent.SCROLL, scrolled);
        removers.forEach(Runnable::run); removers.clear();
    }

    /**
     * Erstellt eine zunächst ausgeblendete Zeilennummernleiste für ein Editor-Control.
     *
     * @param control Editor-Control, dessen sichtbare Absätze nummeriert werden
     * @param zoomPercent beobachtbarer Zoomfaktor in Prozent (100 entspricht 100 %)
     */
    public LineNumberGutter(RichTextArea control, ObservableDoubleValue zoomPercent) {
        this.control = control;
        this.zoomPercent = zoomPercent;

        getStyleClass().addAll(GUTTER_STYLE_CLASS, "rme-line-number-gutter");
        setStyle("-fx-background-color: #f4f4f4; -fx-border-color: #d0d0d0;"
                + " -fx-border-width: 0 1 0 0;");
        setManaged(false);
        setVisible(false);
        // Feste Startbreite: der Container darf die Anzeige nie auf 0 quetschen
        // (sonst entstünde ein Zirkelbezug zwischen Breite und Inhalt)
        setPrefWidth(MIN_WIDTH);
        minWidthProperty().bind(prefWidthProperty());

        observe(control.sceneProperty());
        javafx.beans.InvalidationListener skinChanged = ignored -> { paragraphListView = null; requestSync(); };
        control.skinProperty().addListener(skinChanged);
        removers.add(() -> control.skinProperty().removeListener(skinChanged));
        observe(control.caretPositionProperty()); observe(control.documentProperty()); observe(control.textLengthProperty());
        control.addEventHandler(ScrollEvent.SCROLL, scrolled);
        observe(zoomPercent); observe(widthProperty()); observe(heightProperty()); observe(sceneProperty()); observe(visibleProperty());

    }

    /**
     * Fordert eine Neuberechnung der Nummernpositionen an.
     *
     * <p>Ist das Control nicht sichtbar oder noch nicht in einer Szene,
     * verschiebt die Implementierung die Synchronisierung bis zu einem
     * geeigneten Layout-Puls.
     */
    public void refresh() {
        requestSync();
    }

    private final Rectangle viewportClip = new Rectangle();

    @Override
    protected void layoutChildren() {
        // Ausschnitt auf die eigene Fläche begrenzen, damit Nummern nicht in den
        // Editor hineinragen
        viewportClip.setWidth(getWidth()); viewportClip.setHeight(getHeight());
        if (getClip() != viewportClip) setClip(viewportClip);
        super.layoutChildren();
        requestSync();
    }

    // ---------- Interna ----------

    /** Coalesces geometry reads until layout has completed for this pulse. */
    void requestSync() {
        if (!Platform.isFxApplicationThread()) { Platform.runLater(this::requestSync); return; }
        if (closed) return;
        var scene = getScene();
        if (scene != pulseScene) {
            if (pulseScene != null) pulseScene.removePostLayoutPulseListener(postLayout);
            pulseScene = scene;
            if (scene != null) scene.addPostLayoutPulseListener(postLayout);
        }
        dirty = true;
        if (isVisible() && scene != null) Platform.requestNextPulse();
    }

    /** Liest die sichtbaren Editor-Zellen und positioniert die Nummern. */
    private void sync() {
        if (!isVisible() || control.getScene() == null || getWidth() <= 0) {
            return;
        }
        ListView<?> listView = findListView();
        if (listView == null) {
            return;
        }
        wireScrollBar(listView);

        double gutterTop = localToScene(0, 0).getY();
        double gutterBottom = gutterTop + getHeight();
        double zoomFactor = Math.max(0.01, zoomPercent.get() / 100d);
        double fontSize = BASE_FONT_SIZE * zoomFactor;

        record VisibleCell(int index, double y) { }
        List<VisibleCell> visible = new ArrayList<>();
        int itemCount = listView.getItems().size();
        for (Node node : listView.lookupAll(".list-cell")) {
            if (node instanceof ListCell<?> cell && cell.getIndex() >= 0
                    && cell.getIndex() < itemCount) {
                Point2D top = cell.localToScene(0, 0);
                Point2D bottom = cell.localToScene(0, cell.getHeight());
                if (top.getY() < gutterBottom && bottom.getY() > gutterTop) {
                    visible.add(new VisibleCell(cell.getIndex(), top.getY() - gutterTop + TOP_OFFSET));
                }
            }
        }
        visible.sort(Comparator.comparingInt(VisibleCell::index));

        while (numberLabels.size() < visible.size()) {
            Label label = new Label();
            label.getStyleClass().add(NUMBER_STYLE_CLASS);
            label.setMouseTransparent(true);
            getChildren().add(label);
            numberLabels.add(label);
        }

        double maxNumberWidth = 0d;
        for (int i = 0; i < numberLabels.size(); i++) {
            Label label = numberLabels.get(i);
            if (i >= visible.size()) {
                label.setVisible(false);
                continue;
            }
            VisibleCell cell = visible.get(i);
            label.setText(String.valueOf(cell.index() + 1));
            if (Double.compare(label.getFont().getSize(), fontSize) != 0) label.setFont(Font.font(fontSize));
            if (label.getStyle().isEmpty()) label.setStyle("-fx-text-fill: #8a8a8a;");
            double labelWidth = label.prefWidth(-1);
            double labelHeight = label.prefHeight(labelWidth);
            double x = Math.max(H_PADDING / 2, getWidth() - H_PADDING - labelWidth);
            double y = cell.y();
            label.resizeRelocate(x, y, labelWidth, labelHeight);
            label.setVisible(true);
            maxNumberWidth = Math.max(maxNumberWidth, labelWidth);
        }

        double prefWidth = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, maxNumberWidth + 2 * H_PADDING));
        if (Math.abs(prefWidth - getPrefWidth()) > 0.5) {
            setPrefWidth(prefWidth);
        }
    }

    /** Sucht die Paragraph-Liste im Skin (per Style-Class, öffentliche API). */
    private ListView<?> findListView() {
        if (paragraphListView == null) {
            Node node = control.lookup(".paragraph-list-view");
            if (node instanceof ListView<?> listView) {
                paragraphListView = listView;
            }
        }
        return paragraphListView;
    }

    /**
     * Verbindet den vertikalen Scrollbalken der Editor-Liste als Auslöser.
     * Der VirtualFlow der Bibliothek pflegt den Balken bei <b>jeder</b>
     * Positionsänderung (Mausrad, Tastatur, Ziehen, programmatisch) – das ist
     * der verlässlichste Sync-Auslöser.
     */
    private void wireScrollBar(ListView<?> listView) {
        ScrollBar found = null;
        for (Node node : listView.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar bar
                    && bar.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
                found = bar;
                break;
            }
        }
        if (found == wiredScrollBar) {
            return;
        }
        if (wiredScrollBar != null && scrollBarListener != null) {
            wiredScrollBar.valueProperty().removeListener(scrollBarListener);
        }
        wiredScrollBar = found;
        if (found != null) {
            scrollBarListener = obs -> requestSync();
            found.valueProperty().addListener(scrollBarListener);
        }
    }
}
