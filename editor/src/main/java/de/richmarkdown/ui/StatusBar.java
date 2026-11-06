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
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Statusleiste des Editors mit Änderungsstatus, Cursorposition, Absatzstil und
 * optionalen Bedienelementen wie der Zoomsteuerung.
 *
 * <p>Die Werte werden an die Properties der {@link MarkdownEditor}-Fassade
 * gebunden, sodass Änderungen im Dokument ohne Polling angezeigt werden.
 */
public class StatusBar extends HBox {

    public enum ViewMode {
        FLOW,
        PAGES
    }

    private final Label statusLabel = new Label();
    private final Label positionLabel = new Label();
    private final Label styleLabel = new Label();
    private final ToggleButton flowViewButton = new ToggleButton();
    private final ToggleButton pageViewButton = new ToggleButton();
    private Consumer<ViewMode> viewModeChangeHandler = mode -> { };

    /**
     * Erstellt eine Statusleiste, die sich an die Zustände des Editors bindet.
     *
     * @param editor Editor-Fassade, deren Änderungs-, Caret-, Text- und
     *               Formatierungszustände dargestellt werden
     */
    public StatusBar(MarkdownEditor editor) {
        RichTextArea control = editor.getControl();
        getStyleClass().add("rme-status-bar");
        setSpacing(12);
        setPadding(new Insets(4, 8, 4, 8));

        // '*' marker für ungespeicherte Änderungen (PROJEKTPLAN 7.1)
        statusLabel.textProperty().bind(
                Bindings.when(editor.modifiedProperty()).then("*").otherwise(""));
        positionLabel.textProperty().bind(Bindings.createStringBinding(
                () -> MessageFormat.format(I18n.text("status.position"),
                        editor.caretPositionProperty().get(), editor.textLengthProperty().get()),
                editor.caretPositionProperty(), editor.textLengthProperty()));
        // Stil hängt von beiden Dekorations-Properties ab (Caret und Absatz)
        styleLabel.textProperty().bind(Bindings.createStringBinding(
                () -> MessageFormat.format(I18n.text("status.style"),
                        CaretStyle.detect(control).getLabel()),
                control.decorationAtCaretProperty(),
                control.decorationAtParagraphProperty()));

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToggleGroup viewModes = new ToggleGroup();
        flowViewButton.setToggleGroup(viewModes);
        pageViewButton.setToggleGroup(viewModes);
        flowViewButton.setGraphic(createFlowIcon());
        pageViewButton.setGraphic(createPageIcon());
        flowViewButton.setTooltip(new Tooltip(I18n.text("status.view.flow")));
        pageViewButton.setTooltip(new Tooltip(I18n.text("status.view.pages")));
        flowViewButton.setAccessibleText(I18n.text("status.view.flow"));
        pageViewButton.setAccessibleText(I18n.text("status.view.pages"));
        flowViewButton.getStyleClass().add("rme-view-mode-button");
        pageViewButton.getStyleClass().add("rme-view-mode-button");
        flowViewButton.setFocusTraversable(false);
        pageViewButton.setFocusTraversable(false);
        flowViewButton.setSelected(true);
        flowViewButton.setOnAction(event -> selectViewMode(ViewMode.FLOW));
        pageViewButton.setOnAction(event -> selectViewMode(ViewMode.PAGES));
        HBox viewModeControls = new HBox(2, flowViewButton, pageViewButton);
        viewModeControls.setAlignment(Pos.CENTER);
        viewModeControls.getStyleClass().add("rme-view-mode-controls");

        getChildren().addAll(statusLabel, spacer, styleLabel, positionLabel, viewModeControls);
    }

    /**
     * Liefert das Label, das bei ungespeicherten Änderungen markiert wird.
     *
     * @return Label für den Indikator ungespeicherter Änderungen
     */
    public Label getStatusLabel() {
        return statusLabel;
    }

    /**
     * Liefert das Label mit Caret-Position und Dokumentlänge.
     *
     * @return Label mit aktueller Cursor- und Dokumentposition
     */
    public Label getPositionLabel() {
        return positionLabel;
    }

    /**
     * Liefert das Label des am Caret erkannten Absatzformats.
     *
     * @return Label des Absatzformats an der Caret-Position
     */
    public Label getStyleLabel() {
        return styleLabel;
    }

    public ToggleButton getFlowViewButton() {
        return flowViewButton;
    }

    public ToggleButton getPageViewButton() {
        return pageViewButton;
    }

    public ViewMode getViewMode() {
        return pageViewButton.isSelected() ? ViewMode.PAGES : ViewMode.FLOW;
    }

    public void setViewModeChangeHandler(Consumer<ViewMode> handler) {
        viewModeChangeHandler = Objects.requireNonNull(handler, "handler");
    }

    private void selectViewMode(ViewMode mode) {
        if (mode == ViewMode.FLOW) {
            flowViewButton.setSelected(true);
        } else {
            pageViewButton.setSelected(true);
        }
        viewModeChangeHandler.accept(mode);
    }

    private Node createFlowIcon() {
        Pane icon = createIconPane("rme-flow-view-icon");
        Rectangle page = outline(2.5, 1.5, 14, 14);
        icon.getChildren().addAll(page,
                line(5, 5, 9), line(5, 8, 7), line(5, 11, 9), line(5, 14, 5));
        return icon;
    }

    private Node createPageIcon() {
        Pane icon = createIconPane("rme-page-view-icon");
        icon.getChildren().addAll(outline(1.5, 3.5, 10, 11),
                outline(6.5, 1.5, 10, 13));
        return icon;
    }

    private Pane createIconPane(String styleClass) {
        Pane icon = new Pane();
        icon.setMinSize(18, 18);
        icon.setPrefSize(18, 18);
        icon.setMaxSize(18, 18);
        icon.getStyleClass().add(styleClass);
        return icon;
    }

    private Rectangle outline(double x, double y, double width, double height) {
        Rectangle shape = new Rectangle(x, y, width, height);
        shape.setFill(Color.TRANSPARENT);
        shape.getStyleClass().add("rme-view-icon-outline");
        return shape;
    }

    private Rectangle line(double x, double y, double width) {
        Rectangle shape = new Rectangle(x, y, width, 0.8);
        shape.getStyleClass().add("rme-view-icon-line");
        return shape;
    }

    /**
     * Fügt die angegebenen Steuerelemente am Ende der Statusleiste ein.
     *
     * @param nodes Steuerelemente, beispielsweise Zoomregler und Prozentanzeige
     */
    public void addZoomControls(javafx.scene.Node... nodes) {
        if (nodes.length > 0) {
            getChildren().add(new Separator(Orientation.VERTICAL));
        }
        getChildren().addAll(nodes);
    }
}
