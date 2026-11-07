package de.richmarkdown.editor;

import com.gluonhq.richtextarea.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Editor navigation must map exact exported offsets, including after atomic objects. */
class CoordinateMappingTest {
    @Test void navigationInsideSplitTextNeverJumpsToPieceEnd() {
        PieceTable buffer = new PieceTable(new Document("first second third"));
        buffer.insert("insert", 5);
        for (int i = 0; i <= buffer.getTextLength(); i++) assertEquals(i, buffer.getInternalPosition(i));
    }
    @Test void navigationAfterImageAndEmojiReachesTheRequestedCharacter() {
        String url = "local-image.png";
        PieceTable buffer = new PieceTable(new Document("😀 target"));
        buffer.decorate(0, 0, new ImageDecoration(url));
        String exported = buffer.getText();
        int start = exported.indexOf("target");
        int internal = buffer.getInternalPosition(start + 2);
        assertEquals('r', buffer.getText(internal, internal + 1).charAt(0));
        assertEquals(start + 2, buffer.getText(0, internal).length());
        assertEquals(0, buffer.getInternalPosition(url.length() / 2));
    }
}
