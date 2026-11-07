package de.richmarkdown.editor;

import com.gluonhq.richtextarea.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Buffer invariants underlying empty table insertion and ordinary list formatting. */
class EmptyParagraphStructureTest {
    @Test void emptyTableDecorationSurvivesSnapshotAndUndoRedo() {
        var buffer = new PieceTable(new Document(""));
        var table = ParagraphDecoration.builder().presets().tableDecoration(new TableDecoration(1, 1)).build();
        buffer.decorate(0, 0, table);
        assertEquals(table, buffer.getParagraphDecorationAtCaret(0));
        assertEquals(table, buffer.getDecorationModelList(0, 0).getFirst().getParagraphDecoration());
        buffer.undo();
        assertFalse(buffer.getParagraphDecorationAtCaret(0).hasTableDecoration());
        buffer.redo();
        assertEquals(table, buffer.getParagraphDecorationAtCaret(0));
    }
    @Test void staleEmptyPieceCannotOverrideStyleOfFollowingText() {
        var buffer = new PieceTable(new Document(""));
        buffer.append("item");
        var list = ParagraphDecoration.builder().presets()
                .graphicType(ParagraphDecoration.GraphicType.BULLETED_LIST).indentationLevel(2).build();
        buffer.decorate(0, 4, list);
        assertEquals(list, buffer.getParagraphDecorationAtCaret(0));
        assertEquals(list, buffer.getParagraphDecorationAtCaret(2));
        assertEquals(list, buffer.getDecorationModelList(0, 4).getFirst().getParagraphDecoration());
    }
}
