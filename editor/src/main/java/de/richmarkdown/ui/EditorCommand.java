package de.richmarkdown.ui;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.input.KeyCombination;

/** Shared observable command state, independent of the controls presenting it. */
public final class EditorCommand {
    private final String id;
    private final javafx.beans.property.ReadOnlyStringWrapper label = new javafx.beans.property.ReadOnlyStringWrapper();
    private final javafx.beans.property.ReadOnlyStringWrapper description = new javafx.beans.property.ReadOnlyStringWrapper();
    private final boolean toggle;
    private final ReadOnlyBooleanWrapper enabled = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper visible = new ReadOnlyBooleanWrapper(true);
    private final ReadOnlyBooleanWrapper selected = new ReadOnlyBooleanWrapper();
    private final ReadOnlyObjectWrapper<KeyCombination> shortcut = new ReadOnlyObjectWrapper<>();

    EditorCommand(String id, String label, String description, boolean toggle) {
        this.id = id; this.label.set(label); this.description.set(description); this.toggle = toggle;
    }
    public String id() { return id; }
    public String iconId() { return id; }
    public String label() { return label.get(); }
    public javafx.beans.property.ReadOnlyStringProperty labelProperty() { return label.getReadOnlyProperty(); }
    public String description() { return description.get(); }
    public javafx.beans.property.ReadOnlyStringProperty descriptionProperty() { return description.getReadOnlyProperty(); }
    void presentation(String text, String explanation) { label.set(text); description.set(explanation); }
    public boolean isToggle() { return toggle; }
    public boolean isEnabled() { return enabled.get(); }
    public boolean isVisible() { return visible.get(); }
    public boolean isSelected() { return selected.get(); }
    public ReadOnlyBooleanProperty enabledProperty() { return enabled.getReadOnlyProperty(); }
    public ReadOnlyBooleanProperty visibleProperty() { return visible.getReadOnlyProperty(); }
    public ReadOnlyBooleanProperty selectedProperty() { return selected.getReadOnlyProperty(); }
    public ReadOnlyObjectProperty<KeyCombination> shortcutProperty() { return shortcut.getReadOnlyProperty(); }
    public KeyCombination shortcut() { return shortcut.get(); }
    void update(boolean available, boolean shown, boolean checked, KeyCombination key) {
        enabled.set(available); visible.set(shown); selected.set(checked); shortcut.set(key);
    }
    public String explanation() {
        return description.get() + (shortcut.get() == null ? "" : " (" + shortcut.get().getDisplayText() + ")")
                + (isEnabled() ? "" : "\n" + FeatureUi.text("Im aktuellen Kontext nicht verfügbar.", "Unavailable in the current context."));
    }
}
