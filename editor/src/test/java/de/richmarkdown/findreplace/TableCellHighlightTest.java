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

package de.richmarkdown.findreplace;

import com.gluonhq.richtextarea.RichTextArea;
import com.gluonhq.richtextarea.RichTextAreaSkin;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.scene.Node;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressionstest fuer die Hervorhebung eines Suchtreffers in einer Tabelle.
 *
 * <p>Der Test bildet den Ablauf der Anwendung nach: Die Tabelle wird erst
 * sichtbar, nachdem die Auswahl schon auf dem Treffer steht. Beim Anlegen der
 * Tabellenzelle hat der Text noch keine Breite, deshalb muss die Hervorhebung
 * nach dem Layout neu berechnet werden - sonst wird jedes Zeichen in eine
 * eigene Zeile gelegt und die Markierung ragt ueber die folgenden
 * Tabellenzeilen hinaus.
 */
class TableCellHighlightTest {

    /** Fuelltext, damit die Tabelle anfangs ausserhalb des Sichtbereichs liegt. */
    private static final String FILLER = "Absatz mit Fuelltext.\n\n".repeat(60);

    private static final String TABLE_MARKDOWN = FILLER + """
            | Feature | Supported | Notes |
            |---------|-----------|-------|
            | Bold | Yes | Via toolbar |
            | Italic | Yes | Via toolbar |
            | Headings | Yes | H1 through H6 |
            | Tables | Yes | Via pipe syntax |
            """;

    @BeforeAll
    static void initToolkit() throws Exception {
        FxTestUtil.startToolkit();
    }

    @Test
    void searchMatchInTableIsHighlightedOnlyInItsOwnCell() {
        RichTextArea rta = FxTestUtil.callAndWait(() -> {
            MarkdownEditor editor = new MarkdownEditor();
            RichTextArea control = editor.getControl();
            Stage stage = new Stage();
            // Kleines Fenster: die Tabelle wird erst beim Scrollen gerendert.
            stage.setScene(new Scene(new StackPane(control), 600, 400));
            stage.show();
            editor.setMarkdown(TABLE_MARKDOWN);
            return control;
        });
        for (int i = 0; i < 6; i++) {
            FxTestUtil.flushFxEvents();
        }

        FindReplaceEngine engine = FxTestUtil.callAndWait(() ->
                new FindReplaceEngine(rta, ((RichTextAreaSkin) rta.getSkin()).getViewModel()));

        // Auswahl auf den Tabellentreffer setzen, BEVOR die Tabelle gerendert wird.
        FindReplaceEngine.Match tableMatch = FxTestUtil.callAndWait(() -> {
            List<FindReplaceEngine.Match> matches = engine.findAll("Heading", new FindReplaceEngine.FindOptions());
            assertEquals(1, matches.size(), "Genau ein Treffer erwartet");
            FindReplaceEngine.Match match = matches.get(0);
            engine.selectMatch(match, false);
            return match;
        });
        for (int i = 0; i < 3; i++) {
            FxTestUtil.flushFxEvents();
        }

        // Tabelle in den sichtbaren Bereich scrollen: erst jetzt entstehen die Zellen.
        FxTestUtil.runAndWait(() -> scrollToParagraph(rta, tableMatch.getStart()));
        for (int i = 0; i < 8; i++) {
            FxTestUtil.flushFxEvents();
        }

        FxTestUtil.runAndWait(() -> {
            rta.applyCss();
            rta.layout();

            List<Node> highlighted = rta.lookupAll(".table .layer").stream()
                    .filter(layer -> {
                        Path selection = (Path) layer.lookup(".selection");
                        return selection != null && !selection.getElements().isEmpty();
                    })
                    .toList();
            assertEquals(1, highlighted.size(), "Nur die Zelle mit dem Treffer darf markiert sein");

            Node layer = highlighted.get(0);
            Path selection = (Path) layer.lookup(".selection");
            TextFlow textFlow = (TextFlow) layer.lookup(".text-flow");
            String cellText = textFlow.getChildren().stream()
                    .filter(Text.class::isInstance)
                    .map(Text.class::cast)
                    .map(Text::getText)
                    .collect(Collectors.joining());
            assertTrue(cellText.startsWith("Headings"),
                    "Markiert werden muss die Zelle 'Headings', nicht '" + cellText + "'");

            // Die gezeichnete Hervorhebung muss der Textbereichsform des TextFlows
            // entsprechen: ein Rechteck ueber "Heading", das in der Zeile bleibt.
            PathElement[] expected = textFlow.rangeShape(0, "Heading".length());
            assertTrue(expected.length > 0, "Textbereichsform muss vorhanden sein");
            javafx.geometry.Bounds expectedBounds = boundsOf(expected);
            javafx.geometry.Bounds actualBounds = selection.getBoundsInLocal();

            assertEquals(expectedBounds.getWidth(), actualBounds.getWidth(), 1.0,
                    "Hervorhebung muss genau 'Heading' abdecken");
            assertEquals(expectedBounds.getHeight(), actualBounds.getHeight(), 1.0,
                    "Hervorhebung darf nicht ueber die Zeile hinausreichen");
            assertTrue(actualBounds.getHeight() <= textFlow.getLayoutBounds().getHeight() + 3.0,
                    "Hervorhebung muss innerhalb der Tabellenzeile bleiben (Hoehe "
                            + actualBounds.getHeight() + " statt max. "
                            + textFlow.getLayoutBounds().getHeight() + ")");
        });
    }

    private static javafx.geometry.Bounds boundsOf(PathElement[] elements) {
        Path path = new Path();
        path.getElements().setAll(elements);
        return path.getBoundsInLocal();
    }

    /** Scrollt den VirtualFlow auf den Absatz, der die Position enthaelt. */
    private static void scrollToParagraph(RichTextArea rta, int position) {
        RichTextAreaSkin skin = (RichTextAreaSkin) rta.getSkin();
        int index = 0;
        int target = 0;
        for (var paragraph : skin.getViewModel().getParagraphList()) {
            if (paragraph.getStart() <= position && position < paragraph.getEnd()) {
                target = index;
            }
            index++;
        }
        for (Node node : rta.lookupAll(".virtual-flow")) {
            if (node instanceof javafx.scene.control.skin.VirtualFlow<?> flow) {
                flow.scrollTo(target);
            }
        }
    }
}
