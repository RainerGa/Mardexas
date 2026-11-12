package de.richmarkdown.features.editing;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.Selection;
import de.richmarkdown.editor.MarkdownEditor;
import java.util.Objects;

/** View-only chapter folding. Any edit or navigation into hidden content safely unfolds it. */
public final class ChapterFolding implements AutoCloseable {
    private final MarkdownEditor editor;
    private int hiddenStart = -1, hiddenEnd = -1;
    private final javafx.beans.value.ChangeListener<Number> caretChanged = (obs, old, value) -> {
        caretMoved();
    };
    private final javafx.beans.value.ChangeListener<Selection> selectionChanged = (obs, old, selection) -> {
        if (selection != null && selection.isDefined() && hiddenStart >= 0
                && selection.getEnd() >= hiddenStart && selection.getStart() < hiddenEnd) expandAll();
    };
    private final javafx.beans.value.ChangeListener<Number> lengthChanged = (obs, old, value) -> expandAll();
    private final Runnable changed = this::expandAll;
    public ChapterFolding(MarkdownEditor editor) {
        this.editor = Objects.requireNonNull(editor);
        editor.getControl().caretPositionProperty().addListener(caretChanged);
        editor.getControl().selectionProperty().addListener(selectionChanged);
        editor.getControl().textLengthProperty().addListener(lengthChanged);
        editor.addChangeListener(changed);
    }
    @Override public void close() {
        editor.getControl().caretPositionProperty().removeListener(caretChanged);
        editor.getControl().selectionProperty().removeListener(selectionChanged);
        editor.getControl().textLengthProperty().removeListener(lengthChanged);
        editor.removeChangeListener(changed);
        expandAll();
    }
    private void caretMoved() { if (isHidden(editor.getInternalCaretPosition())) expandAll(); }
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
