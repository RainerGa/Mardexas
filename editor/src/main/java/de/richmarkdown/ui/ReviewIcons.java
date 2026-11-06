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

import de.richmarkdown.i18n.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.shape.SVGPath;

/** Font-independent review icons with accessible names and tooltips. */
final class ReviewIcons {
    private ReviewIcons() { }

    static Button button(String action, Runnable handler) {
        String path = switch (action) {
            case "previous" -> "M8 14 V2 M3 7 L8 2 L13 7";
            case "next" -> "M8 2 V14 M3 9 L8 14 L13 9";
            case "accept", "acceptAll" -> "M2 8 L6 12 L14 3";
            case "reject", "rejectAll" -> "M3 3 L13 13 M13 3 L3 13";
            case "undo" -> "M6 3 L2 7 L6 11 M2 7 H9 Q15 7 13 14";
            case "redo" -> "M10 3 L14 7 L10 11 M14 7 H7 Q1 7 3 14";
            case "reset" -> "M3 6 A6 6 0 1 1 3 11 M3 2 V6 H7";
            default -> "M2 2 H14 V11 H8 L4 15 V11 H2 Z M5 5 H11 M5 8 H10";
        };
        Pane graphic = new Pane();
        graphic.setMinSize(18, 18);
        graphic.setPrefSize(18, 18);
        graphic.setMaxSize(18, 18);
        SVGPath shape = new SVGPath();
        shape.setContent(path);
        shape.setFill(null);
        shape.setStroke(javafx.scene.paint.Color.web(action.startsWith("accept") ? "#19733a"
                : action.startsWith("reject") ? "#b83028" : "#334155"));
        shape.setStrokeWidth(1.8);
        graphic.getChildren().add(shape);
        if (action.endsWith("All")) {
            SVGPath second = new SVGPath();
            second.setContent("M2 16 H14");
            second.setFill(null);
            second.setStroke(shape.getStroke());
            second.setStrokeWidth(1.8);
            graphic.getChildren().add(second);
        }
        Button button = new Button();
        button.setGraphic(graphic);
        button.setId("review-" + action);
        button.setTooltip(new Tooltip(I18n.text("trackChanges." + action)));
        button.setAccessibleText(I18n.text("trackChanges." + action));
        button.setMinSize(30, 30);
        button.setPrefSize(30, 30);
        button.setMaxSize(30, 30);
        button.setOnAction(event -> handler.run());
        return button;
    }
}
