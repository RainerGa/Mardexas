/**
 * Moduldeskriptor des RichMarkDownEditor.
 *
 * Basis ist das JavaFX-Control {@link com.gluonhq.richtextarea.RichTextArea}
 * (RichTextArea-Fork aus dem im Maven-Reactor eingebundenen Git-Submodul).
 * FXML wird nicht benötigt (reine Java-UI).
 */
module RichMarkDownEditor {
    requires transitive javafx.controls;
    requires transitive com.gluonhq.richtextarea;
    // java.awt.Desktop für das Öffnen von Links (RME-002)
    requires java.desktop;
    requires java.prefs;

    exports de.richmarkdown;
    exports de.richmarkdown.editor;
    exports de.richmarkdown.markdown;
}
