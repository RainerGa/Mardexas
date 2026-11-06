package de.richmarkdown.ui;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import com.gluonhq.richtextarea.model.Document;
import com.gluonhq.richtextarea.model.TextBuffer;
import de.richmarkdown.editor.MarkdownEditor;
import java.util.List;
import java.util.function.Consumer;

/** Shares one text/style export per buffer revision between command context checks. FX thread only. */
public final class CommandDocumentSnapshot {
    private static final Object KEY = new Object();
    private TextBuffer buffer;
    private Document snapshot;
    private final Consumer<TextBuffer.Event> changed = event -> snapshot = null;

    private CommandDocumentSnapshot() { }

    public static Document get(MarkdownEditor editor) {
        var properties = editor.getControl().getProperties();
        var cache = (CommandDocumentSnapshot) properties.computeIfAbsent(KEY, ignored -> new CommandDocumentSnapshot());
        if (!(editor.getControl().getSkin() instanceof RichTextAreaSkin skin))
            throw new IllegalStateException("Editor is not ready");
        TextBuffer current = skin.getViewModel().getTextBuffer();
        if (cache.buffer != current) {
            cache.detach();
            cache.buffer = current;
            current.addChangeListener(cache.changed);
        }
        if (cache.snapshot == null) {
            Document source = editor.getDocumentSnapshot();
            cache.snapshot = new Document(source.getText(), List.copyOf(source.getDecorations()), 0,
                    source.getPageFormat(), List.copyOf(source.getHeadersFooters()));
        }
        // The snapshot's collections are immutable; a small wrapper prevents callers
        // from receiving the cache's internal Document instance.
        Document source = cache.snapshot;
        return new Document(source.getText(), source.getDecorations(), 0, source.getPageFormat(), source.getHeadersFooters());
    }

    public static void clear(MarkdownEditor editor) {
        Object cache = editor.getControl().getProperties().remove(KEY);
        if (cache instanceof CommandDocumentSnapshot snapshot) snapshot.detach();
    }

    private void detach() {
        if (buffer != null) buffer.removeChangeListener(changed);
        buffer = null; snapshot = null;
    }
}
