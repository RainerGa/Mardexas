package de.richmarkdown.ui;

import com.gluonhq.richtextarea.RichTextArea;
import de.richmarkdown.editor.DocumentHeading;
import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.FxTestUtil;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Path;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.testfx.util.WaitForAsyncUtils.waitForFxEvents;

/**
 * TDD: Klick auf einen Eintrag der Dokumentstruktur soll den Caret im Dokument
 * an die betreffende Stelle setzen (und dorthin scrollen) – wie in Word
 * (Navigationsbereich) bzw. LibreOffice (Navigator).
 *
 * <p>Anders als die programmatischen Tests der {@link DocumentStructureViewTest}
 * wird hier der reale Anwender-Pfad geprüft: Mausklick (Roboter) auf die
 * Listenzeile der Dokumentstruktur.
 */
@ExtendWith(ApplicationExtension.class)
class StructureNavigationClickTest {

    private static final String DOCUMENT = """
            # Erstes Kapitel

            Absatz

            Absatz

            Absatz

            Absatz

            ## Zweites Kapitel

            Absatz

            Absatz

            Absatz

            Absatz

            ### Drittes Kapitel

            Absatz

            Absatz

            Absatz

            Absatz
            """;

    private MarkdownEditor editor;
    private RichTextArea rta;
    private DocumentStructureView structureView;

    @Start
    public void start(Stage stage) {
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor();
            rta = editor.getControl();
            structureView = new DocumentStructureView(editor);
            HBox row = new HBox(structureView, new StackPane(rta));
            HBox.setHgrow(row.getChildren().get(1), Priority.ALWAYS);
            stage.setScene(new Scene(row, 900, 400));
            stage.show();
        });
    }

    private ListView<DocumentHeading> list() {
        return structureView.getListView();
    }

    private List<DocumentHeading> headings() {
        return FxTestUtil.callAndWait(() -> List.copyOf(list().getItems()));
    }

    private void openDocument(String markdown) {
        FxTestUtil.runAndWait(() -> editor.setMarkdown(markdown));
        waitForFxEvents();
        waitForFxEvents();
    }

    private int caret() {
        return FxTestUtil.callAndWait(rta::getCaretPosition);
    }

    /** Klickt mit der Maus auf den Eintrag mit dem angegebenen Index. */
    @SuppressWarnings("unchecked")
    private void clickEntry(FxRobot robot, int index) throws Exception {
        ListCell<DocumentHeading> cell = FxTestUtil.callAndWait(() -> {
            List<ListCell<DocumentHeading>> cells = list().lookupAll(".list-cell").stream()
                    .filter(ListCell.class::isInstance)
                    .map(c -> (ListCell<DocumentHeading>) c)
                    .filter(c -> c.getItem() != null)
                    .toList();
            return cells.stream()
                    .filter(c -> c.getItem() == list().getItems().get(index))
                    .findFirst()
                    .orElse(null);
        });
        assertNotNull(cell, "Zelle für Eintrag #" + index + " muss gerendert sein");
        Point2D at = FxTestUtil.callAndWait(() ->
                cell.localToScreen(cell.getWidth() * 0.5, cell.getHeight() * 0.5));
        robot.clickOn(at.getX(), at.getY());
        waitForFxEvents();
    }

    @Test
    @DisplayName("Klick auf einen Eintrag setzt den Caret an die Überschrift")
    void clickOnEntryMovesCaretToHeading(FxRobot robot) throws Exception {
        openDocument(DOCUMENT);
        List<DocumentHeading> items = headings();
        assertEquals(3, items.size());

        clickEntry(robot, 2);

        assertEquals(items.get(2).position(), caret(),
                "Der Caret muss an den Anfang der angeklickten Überschrift springen");
    }

    @Test
    @DisplayName("Klick auf den ersten Eintrag setzt den Caret an den Dokumentanfang")
    void clickOnFirstEntryMovesCaretToStart(FxRobot robot) throws Exception {
        openDocument(DOCUMENT);

        clickEntry(robot, 2);
        int afterLast = caret();
        assertTrue(afterLast > 0, "Caret steht nach der Navigation im Dokument");

        clickEntry(robot, 0);

        assertEquals(0, caret(),
                "Der Caret muss an den Anfang der ersten Überschrift springen");
    }

    @Test
    @DisplayName("Klick auf einen Eintrag scrollt die Überschrift in den Sichtbereich")
    void clickOnEntryScrollsHeadingIntoView(FxRobot robot) throws Exception {
        openDocument(DOCUMENT);
        List<DocumentHeading> items = headings();

        assertFalse(isHeadingVisible(items.get(2).text()),
                "Die dritte Überschrift liegt vor dem Klick außerhalb des Sichtbereichs");

        clickEntry(robot, 2);
        waitForFxEvents();
        // Die Skin scrollt über Platform.runLater
        waitForFxEvents();
        waitForFxEvents();

        assertTrue(isHeadingVisible(items.get(2).text()),
                "Die Überschrift muss nach dem Klick im sichtbaren Bereich des Editors liegen");
        assertTrue(items.get(2).position() > 0);
    }

    /** Steht der Textfluss der Überschrift im sichtbaren Bereich des Editors? */
    private boolean isHeadingVisible(String headingText) {
        javafx.geometry.Bounds bounds = headingFlowBounds(headingText);
        return bounds != null && withinViewport(bounds);
    }

    /** Bildschirmbereich des Textflusses mit dem angegebenen Überschriftentext. */
    private javafx.geometry.Bounds headingFlowBounds(String headingText) {
        return FxTestUtil.callAndWait(() -> {
            javafx.geometry.Bounds editor = editorBounds();
            for (Node node : rta.lookupAll(".text-flow")) {
                if (node instanceof javafx.scene.text.TextFlow flow) {
                    javafx.geometry.Bounds b = flow.localToScreen(flow.getBoundsInLocal());
                    if (b == null || !withinViewport(b, editor)) {
                        continue;
                    }
                    String text = flow.getChildren().stream()
                            .filter(javafx.scene.text.Text.class::isInstance)
                            .map(t -> ((javafx.scene.text.Text) t).getText())
                            .reduce(String::concat).orElse("").trim();
                    if (text.equals(headingText.trim())) {
                        return b;
                    }
                }
            }
            return null;
        });
    }

    private boolean withinViewport(javafx.geometry.Bounds bounds) {
        return FxTestUtil.callAndWait(() -> withinViewport(bounds, editorBounds()));
    }

    private boolean withinViewport(javafx.geometry.Bounds bounds, javafx.geometry.Bounds editor) {
        return bounds.getMinY() >= editor.getMinY() - 1 && bounds.getMaxY() <= editor.getMaxY() + 1;
    }

    private javafx.geometry.Bounds editorBounds() {
        return FxTestUtil.callAndWait(() -> rta.localToScreen(rta.getBoundsInLocal()));
    }

    /** Der gerenderte Caret (nur vorhanden, wenn der Editor den Fokus hat). */
    private Path caretNode() {
        return FxTestUtil.callAndWait(() -> rta.lookupAll(".caret").stream()
                .filter(Path.class::isInstance)
                .map(Path.class::cast)
                .filter(p -> !p.getElements().isEmpty())
                .findFirst()
                .orElse(null));
    }

    @Test
    @DisplayName("Klick auf einen Eintrag macht den Caret an der Überschrift sichtbar")
    void clickOnEntryShowsCaretAtHeading(FxRobot robot) throws Exception {
        openDocument(DOCUMENT);
        List<DocumentHeading> items = headings();

        clickEntry(robot, 2);
        waitForFxEvents();
        waitForFxEvents();

        Path caret = caretNode();
        assertNotNull(caret, "Der Caret muss nach der Navigation gerendert sein (Fokus im Dokument)");
        javafx.geometry.Bounds heading = headingFlowBounds(items.get(2).text());
        assertNotNull(heading, "Die Zielüberschrift muss im Sichtbereich gerendert sein");

        javafx.geometry.Bounds caretBounds = FxTestUtil.callAndWait(() ->
                caret.localToScreen(caret.getBoundsInLocal()));
        assertTrue(caretBounds.getHeight() > 3, "Die Caret-Form muss eine sichtbare Höhe haben");
        // Die Caret-Glyphe ist etwas größer als die Textzeile (vgl. ParagraphTile).
        double slack = 3d;
        assertTrue(caretBounds.getMinY() >= heading.getMinY() - slack
                        && caretBounds.getMaxY() <= heading.getMaxY() + slack,
                "Der Caret muss in der Zeile der Zielüberschrift stehen: caret "
                        + caretBounds.getMinY() + ".." + caretBounds.getMaxY()
                        + " heading " + heading.getMinY() + ".." + heading.getMaxY());
    }

    @Test
    @DisplayName("Klick auf einen Eintrag verändert das Dokument nicht")
    void clickOnEntryDoesNotModifyDocument(FxRobot robot) throws Exception {
        openDocument(DOCUMENT);
        String before = FxTestUtil.callAndWait(editor::getMarkdown);

        clickEntry(robot, 1);

        assertEquals(before, FxTestUtil.callAndWait(editor::getMarkdown),
                "Navigation darf das Dokument nicht verändern");
    }

    @Test
    @DisplayName("Klick auf einen Eintrag hinterlässt keine Selektion")
    void clickOnEntryLeavesNoSelection(FxRobot robot) throws Exception {
        openDocument(DOCUMENT);

        clickEntry(robot, 1);

        assertFalse(FxTestUtil.callAndWait(() -> rta.getSelection().isDefined()),
                "Navigation per Dokumentstruktur darf keine Text-Selektion hinterlassen");
    }
}
