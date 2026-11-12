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

package de.richmarkdown.editor;

import com.gluonhq.richtextarea.model.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceIndexTest {
    @Test void indexedRangesRemainCorrectAcrossEditsAndUndoRedo() {
        PieceTable table = new PieceTable(new Document("first\nsecond\nthird"));
        var bold = TextDecoration.builder().presets().fontWeight(javafx.scene.text.FontWeight.BOLD).build();
        for (int step = 0; step < 40; step++) {
            table.decorate(0, 2, bold); table.insert("x", 7); table.delete(1, 1);
            verifyRanges(table);
            table.undo(); table.undo(); table.undo(); verifyRanges(table);
            table.redo(); table.redo(); table.redo(); verifyRanges(table);
        }
    }
    private void verifyRanges(PieceTable table) {
        String full = table.getText();
        table.resetCharacterIterator();
        for (int i = 0; i < full.length(); i++) assertEquals(full.charAt(i), table.charAt(i));
        assertEquals(java.util.stream.IntStream.range(0, full.length()).filter(i -> full.charAt(i) == '\n').boxed().toList(), table.getLineFeeds());
        for (int start = 0; start < full.length(); start++) {
            int end = Math.min(full.length(), start + 5);
            assertEquals(full.substring(start, end), table.getText(start, end));
            StringBuilder fragments = new StringBuilder();
            table.walkFragments((unit, style) -> fragments.append(unit.getText()), start, end);
            assertEquals(full.substring(start, end), fragments.toString());
            var decorations = table.getDecorationModelList(start, end);
            assertEquals(end - start, decorations.stream().mapToInt(DecorationModel::getLength).sum());
            assertEquals(0, decorations.getFirst().getStart());
        }
    }
    @Test void revisionsAdvanceBeforeListenersAndUndoDecorationRangesUseDocumentCoordinates() {
        PieceTable table = new PieceTable(new Document("abcdef"));
        table.insert("prefix ", 0);
        long revision = table.getRevision();
        List<TextBuffer.Event> events = new ArrayList<>();
        table.addChangeListener(event -> { assertTrue(table.getRevision() > revision); events.add(event); });
        table.decorate(8, 10, TextDecoration.builder().presets().fontSize(30).build());
        table.undo();
        assertTrue(events.stream().allMatch(event -> event instanceof TextBuffer.DecorateEvent changed
                && changed.getStart() == 8 && changed.getEnd() == 10));
    }
}
