package de.richmarkdown.features.editing;

import static de.richmarkdown.ui.FeatureUi.text;

import com.gluonhq.richtextarea.RichTextAreaSkin;
import de.richmarkdown.editor.MarkdownEditor;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import java.util.Objects;

/** Lightweight writing preferences. No document model or Markdown changes. */
public final class WritingViewController {
    private final MarkdownEditor editor;
    private final BooleanProperty distractionFree = new SimpleBooleanProperty();
    private final BooleanProperty focusParagraph = new SimpleBooleanProperty();
    private final BooleanProperty typewriter = new SimpleBooleanProperty();
    private final BooleanProperty readingWidth = new SimpleBooleanProperty();
    private final double originalMaximumWidth;
    public WritingViewController(MarkdownEditor editor) {
        this.editor = Objects.requireNonNull(editor);
        originalMaximumWidth = editor.getControl().getMaxWidth();
        focusParagraph.addListener((obs, old, enabled) -> updateSkin());
        typewriter.addListener((obs, old, enabled) -> updateSkin());
        readingWidth.addListener((obs, old, enabled) -> editor.getControl().setMaxWidth(enabled ? 820 : originalMaximumWidth));
        editor.getControl().skinProperty().addListener((obs, old, skin) -> updateSkin());
    }
    private void updateSkin() {
        if (editor.getControl().getSkin() instanceof RichTextAreaSkin skin) {
            skin.setFocusCurrentParagraph(focusParagraph.get());
            skin.setTypewriterMode(typewriter.get());
        }
    }
    public BooleanProperty distractionFreeProperty() { return distractionFree; }
    public BooleanProperty focusParagraphProperty() { return focusParagraph; }
    public BooleanProperty typewriterProperty() { return typewriter; }
    public BooleanProperty readingWidthProperty() { return readingWidth; }
    public Menu createMenu() {
        var menu = new Menu(text("Schreibansicht", "Writing view"));
        menu.setId("menu.writingView");
        add(menu, "view.distractionFree", text("Ablenkungsfrei schreiben", "Distraction-free writing"), distractionFree);
        add(menu, "view.focusParagraph", text("Aktuellen Absatz hervorheben", "Focus current paragraph"), focusParagraph);
        add(menu, "view.typewriter", text("Schreibmaschinenmodus", "Typewriter mode"), typewriter);
        add(menu, "view.readingWidth", text("Lesebreite begrenzen", "Limit reading width"), readingWidth);
        return menu;
    }
    private static void add(Menu menu, String id, String label, BooleanProperty property) {
        var item = new CheckMenuItem(label); item.setId(id); item.selectedProperty().bindBidirectional(property); menu.getItems().add(item);
    }
}
