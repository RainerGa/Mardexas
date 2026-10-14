package de.richmarkdown.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.List;

/**
 * Vektor-Icons für die Format-Toolbar im Stil von Word/LibreOffice
 * (PROJEKTPLAN Kap. 0 Phase 13).
 *
 * <p>Die Icons werden bewusst ohne externe Bilddateien aus JavaFX-Shapes
 * aufgebaut – sie sind damit auflösungsunabhängig, skalieren mit der
 * Schriftgröße und lassen sich über CSS einfärben (Style-Klasse
 * {@value #SHAPE_STYLE_CLASS}). Alle Icons liegen auf einem
 * {@value #ICON_SIZE}×{@value #ICON_SIZE}-Raster.
 */
public final class ToolbarIcons {

    /** Kantenlänge des Icon-Rasters (übliche Toolbar-Icon-Größe). */
    public static final double ICON_SIZE = 16.0;

    /** Gemeinsame Style-Klasse aller Icons. */
    public static final String ICON_STYLE_CLASS = "rme-icon";

    /** Style-Klasse der einzelnen Shape-Elemente (CSS-Einfärbung). */
    public static final String SHAPE_STYLE_CLASS = "rme-icon-shape";

    /** Spezifische Style-Klassen der einzelnen Icons (CSS-Selektoren). */
    public static final List<String> ICON_STYLE_CLASSES = List.of(
            "rme-icon-bullet-list", "rme-icon-numbered-list",
            "rme-icon-indent", "rme-icon-outdent", "rme-icon-inline-code");

    private ToolbarIcons() {
    }

    /**
     * Erstellt ein Vektor-Icon mit drei Aufzählungspunkten und Textzeilen.
     *
     * @return JavaFX-Knotengruppe im Icon-Raster
     */
    public static Node bulletListIcon() {
        Group icon = new Group();
        icon.getStyleClass().addAll(ICON_STYLE_CLASS, "rme-icon-bullet-list");
        for (double y : new double[]{4.0, 8.0, 12.0}) {
            icon.getChildren().addAll(bullet(3.0, y), line(6.5, y, 8.0));
        }
        return icon;
    }

    /**
     * Erstellt ein Vektor-Icon mit den Listennummern 1 bis 3.
     *
     * @return JavaFX-Knotengruppe im Icon-Raster
     */
    public static Node numberedListIcon() {
        Group icon = new Group();
        icon.getStyleClass().addAll(ICON_STYLE_CLASS, "rme-icon-numbered-list");
        String[] numbers = {"1", "2", "3"};
        double[] rows = {4.0, 7.5, 11.0};
        for (int i = 0; i < rows.length; i++) {
            Text number = new Text(0, rows[i], numbers[i]);
            number.getStyleClass().add(SHAPE_STYLE_CLASS);
            number.setFont(Font.font(6.0));
            icon.getChildren().addAll(number, line(5.5, rows[i] - 1.5, 9.0));
        }
        return icon;
    }

    /**
     * Erstellt das Symbol zum Vergrößern des Einzugs.
     *
     * @return JavaFX-Knotengruppe im Icon-Raster
     */
    public static Node indentIcon() {
        return indentLikeIcon("rme-icon-indent",
                new double[]{6.5, 8.0, 2.0, 4.8, 3.8, 8.0, 2.0, 11.2});
    }

    /**
     * Erstellt das Symbol zum Verringern des Einzugs.
     *
     * @return JavaFX-Knotengruppe im Icon-Raster
     */
    public static Node outdentIcon() {
        return indentLikeIcon("rme-icon-outdent",
                new double[]{2.0, 8.0, 6.5, 4.8, 4.7, 8.0, 6.5, 11.2});
    }

    /**
     * Erstellt das Inline-Code-Symbol aus Chevrons und Schrägstrich.
     *
     * @return JavaFX-Knotengruppe im Icon-Raster
     */
    public static Node inlineCodeIcon() {
        Group icon = new Group();
        icon.getStyleClass().addAll(ICON_STYLE_CLASS, "rme-icon-inline-code");
        icon.getChildren().addAll(
                chevron(false),
                slash(),
                chevron(true));
        return icon;
    }

    /**
     * Zählt den Wurzelknoten und alle darunterliegenden Knoten des angegebenen Typs.
     *
     * <p>Die rekursive Auswertung ist insbesondere für die strukturelle
     * Verifikation zusammengesetzter Vektor-Icons nützlich.
     *
     * @param node Wurzel des zu durchsuchenden Szenengraphen
     * @param type gesuchter JavaFX-Knotentyp
     * @return Anzahl passender Knoten einschließlich der Wurzel
     */
    public static int countOf(Node node, Class<? extends Node> type) {
        int count = type.isInstance(node) ? 1 : 0;
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                count += countOf(child, type);
            }
        }
        return count;
    }

    // ---------- Interna ----------

    private static Node indentLikeIcon(String styleClass, double[] arrowPoints) {
        Group icon = new Group();
        icon.getStyleClass().addAll(ICON_STYLE_CLASS, styleClass);
        Polygon arrow = new Polygon(arrowPoints);
        arrow.getStyleClass().add(SHAPE_STYLE_CLASS);
        icon.getChildren().add(arrow);
        for (double y : new double[]{4.0, 8.0, 12.0}) {
            icon.getChildren().add(line(7.5, y, 7.0));
        }
        return icon;
    }

    private static Shape bullet(double centerX, double centerY) {
        Circle circle = new Circle(centerX, centerY, 1.6, Color.BLACK);
        circle.getStyleClass().add(SHAPE_STYLE_CLASS);
        return circle;
    }

    private static Shape line(double x, double centerY, double width) {
        Rectangle rectangle = new Rectangle(x, centerY - 0.7, width, 1.4);
        rectangle.setFill(Color.BLACK);
        rectangle.getStyleClass().add(SHAPE_STYLE_CLASS);
        return rectangle;
    }

    /** Chevron „&lt;" bzw. „&gt;" – rechts heißt: Spitze zeigt nach rechts. */
    private static Shape chevron(boolean right) {
        double[] points = right
                ? new double[]{2.0, 3.0, 5.0, 8.0, 2.0, 13.0, 3.6, 13.0, 6.6, 8.0, 3.6, 3.0}
                : new double[]{14.0, 3.0, 11.0, 8.0, 14.0, 13.0, 12.4, 13.0, 9.4, 8.0, 12.4, 3.0};
        Polygon polygon = new Polygon(points);
        polygon.getStyleClass().add(SHAPE_STYLE_CLASS);
        return polygon;
    }

    private static Shape slash() {
        Polygon polygon = new Polygon(7.0, 13.0, 8.6, 13.0, 12.0, 3.0, 10.4, 3.0);
        polygon.getStyleClass().add(SHAPE_STYLE_CLASS);
        return polygon;
    }
}
