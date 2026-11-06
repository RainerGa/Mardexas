package de.richmarkdown.editor;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import de.richmarkdown.FxTestUtil;
import de.richmarkdown.GuiTestSupport;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Path;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Comparator;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** Pointer placement must keep the clicked line on screen, including after VirtualFlow recycling. */
class EditorPointerViewportTest {
    private MarkdownEditor editor;
    private Stage stage;

    @BeforeAll static void toolkit() throws Exception { FxTestUtil.startToolkit(); }
    @BeforeEach void show() throws Exception {
        FxTestUtil.runAndWait(() -> {
            editor = new MarkdownEditor();
            stage = new Stage(); stage.setX(10); stage.setY(10);
            stage.setScene(new Scene(new StackPane(editor.getControl()), 750, 460)); stage.show();
            editor.setMarkdown(IntStream.range(0, 220).mapToObj(i -> String.format("Zeile %03d mit sichtbarem Text.", i))
                    .collect(java.util.stream.Collectors.joining("\n\n")));
        });
        GuiTestSupport.pulses();
    }
    @AfterEach void close() { FxTestUtil.runAndWait(stage::close); }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void lowerThirdClickKeepsClickedTextAndCaretAtPointer(boolean unfocused, boolean typewriter) throws Exception {
        FxTestUtil.runAndWait(() -> {
            ((RichTextAreaSkin) editor.getControl().getSkin()).setTypewriterMode(typewriter);
            if (unfocused) {
                stage.getScene().getRoot().setFocusTraversable(true);
                stage.getScene().getRoot().requestFocus();
            }
            ((ScrollBar) editor.getControl().lookup(".scroll-bar:vertical")).setValue(.55);
        });
        GuiTestSupport.pulses();
        Text target = FxTestUtil.callAndWait(() -> {
            Bounds viewport = viewport();
            double desiredY = viewport.getMinY() + viewport.getHeight() * .8;
            return editor.getControl().lookupAll("Text").stream().filter(Text.class::isInstance).map(Text.class::cast)
                    .filter(t -> t.getText().startsWith("Zeile "))
                    .filter(t -> { Bounds b = t.localToScene(t.getBoundsInLocal());
                        return b.getMinY() > viewport.getMinY() + viewport.getHeight() * .67 && b.getMaxY() < viewport.getMaxY(); })
                    .min(Comparator.comparingDouble(t -> Math.abs(t.localToScene(t.getBoundsInLocal()).getCenterY() - desiredY)))
                    .orElseThrow(() -> new AssertionError("A real text line must be rendered in the lower third"));
        });
        String line = FxTestUtil.callAndWait(target::getText);
        int expected = FxTestUtil.callAndWait(() -> editor.getDocumentSnapshot().getText().indexOf(line));
        assertTrue(expected > 0);
        Bounds before = FxTestUtil.callAndWait(() -> target.localToScene(target.getBoundsInLocal()));
        Bounds local = FxTestUtil.callAndWait(target::getBoundsInLocal);
        GuiTestSupport.click(target, local.getMinX() + 1, local.getCenterY());
        FxTestUtil.runAndWait(() -> {
            assertEquals(expected, editor.getControl().getCaretPosition(), "The pointer must hit the intended text");
            assertTrue(editor.getControl().isFocused());
            Bounds caret = activeCaretBounds();
            assertEquals(before.getCenterY(), caret.getCenterY(), 3, "Clicking must not reposition the line vertically");
        });
        GuiTestSupport.key(KeyCode.X);
        FxTestUtil.runAndWait(() -> assertEquals('x', editor.getDocumentSnapshot().getText().charAt(expected),
                "Typing must affect the clicked paragraph"));
    }

    private Bounds viewport() {
        var flow = editor.getControl().lookup(".virtual-flow");
        return flow.localToScene(flow.getBoundsInLocal());
    }
    private Bounds activeCaretBounds() {
        var carets = editor.getControl().lookupAll(".caret").stream().filter(Path.class::isInstance).map(Path.class::cast)
                .filter(p -> !p.getElements().isEmpty()).toList();
        assertEquals(1, carets.size(), "Only the current caret may have a rendered shape");
        return carets.getFirst().localToScene(carets.getFirst().getBoundsInLocal());
    }
}
