package de.richmarkdown.ui;

import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Vektor-Icons für die Format-Toolbar im Stil von Word/LibreOffice
 * (PROJEKTPLAN Kap. 0 Phase 13).
 *
 * <p>Die Listen-, Einzugs- und Inline-Code-Icons werden bewusst ohne externe
 * Bilddateien aus JavaFX-Shapes aufgebaut – sie sind damit auflösungsunabhängig,
 * skalieren mit der Schriftgröße und lassen sich über CSS einfärben
 * (Style-Klasse {@value #SHAPE_STYLE_CLASS}). Die Datei-Icons („Neu“, „Öffnen“,
 * „Speichern“) werden aus der PNG-Ressource {@code file-icons.png} geladen.
 *
 * <p>Alle Icons liegen auf einem {@value #ICON_SIZE}×{@value #ICON_SIZE}-Raster.
 */
public final class ToolbarIcons {

    /** Kantenlänge des Icon-Rasters (übliche Toolbar-Icon-Größe). */
    public static final double ICON_SIZE = 22.0;

    /** Gemeinsame Style-Klasse aller Icons. */
    public static final String ICON_STYLE_CLASS = "rme-icon";

    /** Style-Klasse der einzelnen Shape-Elemente (CSS-Einfärbung). */
    public static final String SHAPE_STYLE_CLASS = "rme-icon-shape";

    /** Spezifische Style-Klassen der einzelnen Icons (CSS-Selektoren). */
    public static final List<String> ICON_STYLE_CLASSES = List.of(
            "rme-icon-new-document", "rme-icon-open-file", "rme-icon-save-file",
            "rme-icon-bullet-list", "rme-icon-numbered-list",
            "rme-icon-indent", "rme-icon-outdent", "rme-icon-inline-code");

    /** Geladenes Sprite-Bild mit den drei Datei-Icons nebeneinander. */
    private static final Image FILE_ICONS_IMAGE = loadFileIconsImage();

    private ToolbarIcons() {
    }

    /**
     * Lädt das Sprite-Bild mit den Datei-Icons aus den Klassenressourcen.
     *
     * @return das geladene Bild oder wirft eine Ausnahme, wenn es fehlt
     */
    private static Image loadFileIconsImage() {
        try (InputStream is = ToolbarIcons.class.getResourceAsStream(
                "/de/richmarkdown/ui/icons/file-icons.png")) {
            if (is == null) {
                throw new IllegalStateException(
                        "Icon-Ressource nicht gefunden: /de/richmarkdown/ui/icons/file-icons.png");
            }
            return new Image(is);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Icon-Ressource konnte nicht geladen werden: /de/richmarkdown/ui/icons/file-icons.png", e);
        }
    }

    /**
     * Erstellt ein Vektor-Icon mit drei Aufzählungspunkten und Textzeilen.
     *
     * @return JavaFX-Knotengruppe im Icon-Raster
     */
    public static Node bulletListIcon() {
        Pane icon = new Pane();
        icon.getStyleClass().addAll(ICON_STYLE_CLASS, "rme-icon-bullet-list");
        icon.setMinSize(ICON_SIZE, ICON_SIZE);
        icon.setPrefSize(ICON_SIZE, ICON_SIZE);
        icon.setMaxSize(ICON_SIZE, ICON_SIZE);
        for (double y : new double[]{5.0, 9.5, 14.0}) {
            icon.getChildren().addAll(bullet(3.5, y), line(7.0, y, 9.0));
        }
        return icon;
    }

    /**
     * Erstellt ein Vektor-Icon mit den Listennummern 1 bis 3.
     *
     * @return JavaFX-Knotengruppe im Icon-Raster
     */
    public static Node numberedListIcon() {
        Pane icon = new Pane();
        icon.getStyleClass().addAll(ICON_STYLE_CLASS, "rme-icon-numbered-list");
        icon.setMinSize(ICON_SIZE, ICON_SIZE);
        icon.setPrefSize(ICON_SIZE, ICON_SIZE);
        icon.setMaxSize(ICON_SIZE, ICON_SIZE);
        String[] numbers = {"1", "2", "3"};
        double[] rows = {5.0, 9.5, 14.0};
        for (int i = 0; i < rows.length; i++) {
            Text number = new Text(0, rows[i], numbers[i]);
            number.getStyleClass().add(SHAPE_STYLE_CLASS);
            number.setFont(Font.font(7.0));
            icon.getChildren().addAll(number, line(6.0, rows[i] - 1.5, 10.0));
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
                new double[]{7.0, 9.5, 2.5, 6.0, 4.5, 9.5, 2.5, 13.0});
    }

    /**
     * Erstellt das Symbol zum Verringern des Einzugs.
     *
     * @return JavaFX-Knotengruppe im Icon-Raster
     */
    public static Node outdentIcon() {
        return indentLikeIcon("rme-icon-outdent",
                new double[]{2.5, 9.5, 7.0, 6.0, 5.0, 9.5, 7.0, 13.0});
    }

    /**
     * Erstellt das Inline-Code-Symbol aus Chevrons und Schrägstrich.
     *
     * @return JavaFX-Knotengruppe im Icon-Raster
     */
    public static Node inlineCodeIcon() {
        Pane icon = new Pane();
        icon.getStyleClass().addAll(ICON_STYLE_CLASS, "rme-icon-inline-code");
        icon.setMinSize(ICON_SIZE, ICON_SIZE);
        icon.setPrefSize(ICON_SIZE, ICON_SIZE);
        icon.setMaxSize(ICON_SIZE, ICON_SIZE);
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
        Pane icon = new Pane();
        icon.getStyleClass().addAll(ICON_STYLE_CLASS, styleClass);
        icon.setMinSize(ICON_SIZE, ICON_SIZE);
        icon.setPrefSize(ICON_SIZE, ICON_SIZE);
        icon.setMaxSize(ICON_SIZE, ICON_SIZE);
        Polygon arrow = new Polygon(arrowPoints);
        arrow.getStyleClass().add(SHAPE_STYLE_CLASS);
        icon.getChildren().add(arrow);
        for (double y : new double[]{5.0, 9.5, 14.0}) {
            icon.getChildren().add(line(8.0, y, 8.0));
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

    /**
     * Erstellt das Symbol für „Neu“ – ein leeres Dokument mit Falsschlinge.
     *
     * @return JavaFX-Knotengruppe im Icon-Raster
     */
    public static Node newDocumentIcon() {
        ImageView icon = new ImageView(FILE_ICONS_IMAGE);
        icon.getStyleClass().addAll(ICON_STYLE_CLASS, "rme-icon-new-document");
        double third = FILE_ICONS_IMAGE.getWidth() / 3.0;
        icon.setViewport(new Rectangle2D(0, 0, third, FILE_ICONS_IMAGE.getHeight()));
        icon.setFitWidth(ICON_SIZE);
        icon.setFitHeight(ICON_SIZE);
        return icon;
    }

    /**
     * Erstellt das Symbol für „Öffnen“ – einen Ordner.
     *
     * @return JavaFX-Knotengruppe im Icon-Raster
     */
    public static Node openFileIcon() {
        ImageView icon = new ImageView(FILE_ICONS_IMAGE);
        icon.getStyleClass().addAll(ICON_STYLE_CLASS, "rme-icon-open-file");
        double third = FILE_ICONS_IMAGE.getWidth() / 3.0;
        icon.setViewport(new Rectangle2D(third, 0, third, FILE_ICONS_IMAGE.getHeight()));
        icon.setFitWidth(ICON_SIZE);
        icon.setFitHeight(ICON_SIZE);
        return icon;
    }

    /**
     * Erstellt das Symbol für „Speichern“ – eine Diskette.
     *
     * @return JavaFX-Knotengruppe im Icon-Raster
     */
    public static Node saveFileIcon() {
        ImageView icon = new ImageView(FILE_ICONS_IMAGE);
        icon.getStyleClass().addAll(ICON_STYLE_CLASS, "rme-icon-save-file");
        double third = FILE_ICONS_IMAGE.getWidth() / 3.0;
        icon.setViewport(new Rectangle2D(2 * third, 0, third, FILE_ICONS_IMAGE.getHeight()));
        icon.setFitWidth(ICON_SIZE);
        icon.setFitHeight(ICON_SIZE);
        return icon;
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
