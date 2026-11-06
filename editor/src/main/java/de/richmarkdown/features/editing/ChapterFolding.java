package de.richmarkdown.features.editing;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.Selection;
import de.richmarkdown.editor.MarkdownEditor;
import java.util.Objects;

/** View-only chapter folding. Any edit or navigation into hidden content safely unfolds it. */
public final class ChapterFolding {
    private final MarkdownEditor editor;
    private int hiddenStart = -1, hiddenEnd = -1;
    public ChapterFolding(MarkdownEditor editor) {
        this.editor = Objects.requireNonNull(editor);
        editor.getControl().caretPositionProperty().addListener((obs, old, value) -> {
            int caret = editor.getInternalCaretPosition();
            if (isHidden(caret)) expandAll();
        });
        editor.getControl().selectionProperty().addListener((obs, old, selection) -> {
            if (selection != null && selection.isDefined() && hiddenStart >= 0
                    && selection.getEnd() >= hiddenStart && selection.getStart() < hiddenEnd) expandAll();
        });
        editor.getControl().textLengthProperty().addListener((obs, old, value) -> expandAll());
        editor.addChangeListener(this::expandAll);
    }
    public boolean isHidden(int internalPosition) {
        return hiddenStart >= 0 && internalPosition >= hiddenStart && internalPosition < hiddenEnd;
    }
    public boolean isFolded() { return hiddenStart >= 0; }
    public void fold(ChapterModel.Chapter chapter) {
        var skin = skin();
        if (chapter.bodyStart() >= chapter.end()) return;
        var selection = skin.getViewModel().getTextBuffer().getInternalSelection(
                new Selection(chapter.bodyStart(), chapter.end()));
        if (!selection.isDefined()) return;
        // Keep the caret on the visible heading before applying the display filter.
        var heading = skin.getViewModel().getTextBuffer().getInternalSelection(
                new Selection(chapter.start(), chapter.bodyStart()));
        editor.goToPosition(heading.getStart());
        hiddenStart = selection.getStart(); hiddenEnd = selection.getEnd();
        skin.setParagraphVisibility(position -> !isHidden(position));
    }
    public void expandAll() {
        if (hiddenStart < 0) return;
        hiddenStart = hiddenEnd = -1;
        skin().setParagraphVisibility(null);
    }
    private RichTextAreaSkin skin() {
        if (!(editor.getControl().getSkin() instanceof RichTextAreaSkin skin)) throw new IllegalStateException("Editor is not ready");
        return skin;
    }
}
