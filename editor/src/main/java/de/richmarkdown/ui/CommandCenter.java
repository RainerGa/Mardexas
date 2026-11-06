package de.richmarkdown.ui;

import de.richmarkdown.i18n.I18n;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Searchable commands, shortcuts and an optional quick-access toolbar. */
public final class CommandCenter {
    public record Command(String id, String label, MenuItem item) { }
    private final EditorWindow window;
    private final WindowCommandRegistry registry;
    private List<Command> commands;
    private final List<javafx.beans.binding.Binding<?>> toolbarBindings = new ArrayList<>();
    private final javafx.event.EventHandler<javafx.scene.input.KeyEvent> searchShortcut = event -> {
        if (new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN).match(event)) {
            show(); event.consume();
        }
    };
    private final HBox quickAccess = new HBox(4);
    private final Set<String> pinned = new HashSet<>();
    public CommandCenter(EditorWindow window, MenuBar menuBar) {
        this(window, null, collect(menuBar));
    }
    public CommandCenter(EditorWindow window, WindowCommandRegistry registry) {
        this(window, java.util.Objects.requireNonNull(registry), List.of());
    }
    private CommandCenter(EditorWindow window, WindowCommandRegistry registry, List<Command> initial) {
        this.window = window;
        this.registry = registry;
        this.commands = List.copyOf(initial);
        syncCommands();
        var preferences = I18n.preferences();
        String stored = preferences.getOrDefault("toolbar.pinned", "");
        if (!stored.isBlank()) pinned.addAll(List.of(stored.split("\\|")));
        boolean migrated = false;
        for (Command command : commands) {
            Object legacy = command.item().getProperties().get(CommandIdentity.LEGACY_ID);
            String key = "shortcut." + command.id();
            String shortcut = preferences.getOrDefault(key, "");
            if (!preferences.containsKey(key) && legacy instanceof String oldId) {
                shortcut = preferences.getOrDefault("shortcut." + oldId, "");
                if (!shortcut.isBlank()) { I18n.setPreference(key, shortcut); migrated = true; }
            }
            if (legacy instanceof String oldId && !command.id().equals(oldId) && pinned.remove(oldId)) {
                pinned.add(command.id()); migrated = true;
            }
            if (!shortcut.isBlank()) {
                try { applyShortcut(command, KeyCombination.keyCombination(shortcut)); }
                catch (IllegalArgumentException invalid) { /* stale profile entry */ }
            }
        }
        if (migrated) I18n.setPreference("toolbar.pinned", storedPins());
        quickAccess.setId("command-favorites");
        window.getChrome().getChildren().add(quickAccess);
        rebuildToolbar();
        if (registry == null) window.getEditorRoot().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, searchShortcut);
        window.addFeatureDisposer(this::dispose);
    }
    private static List<Command> collect(MenuBar menuBar) {
        var found = new ArrayList<Command>();
        for (Menu menu : menuBar.getMenus()) collect(menu, "", found);
        return List.copyOf(found);
    }
    private void syncCommands() {
        if (registry == null) return;
        commands = registry.commands().stream().map(command -> new Command(command.id(), command.description(), registry.item(command.id()))).toList();
    }
    private String storedPins() { return String.join("|", pinned.stream().sorted().toList()); }
    public void dispose() {
        clearToolbarBindings();
        window.getEditorRoot().removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, searchShortcut);
        quickAccess.getChildren().clear();
    }
    private void clearToolbarBindings() {
        toolbarBindings.forEach(javafx.beans.binding.Binding::dispose);
        toolbarBindings.clear();
    }
    private static void collect(Menu menu, String path, List<Command> commands) {
        String prefix = path + menu.getText().replace("_", "") + " / ";
        for (MenuItem item : menu.getItems()) {
            if (item instanceof Menu submenu) collect(submenu, prefix, commands);
            else if (!(item instanceof SeparatorMenuItem)) {
                String label = prefix + item.getText().replace("_", "");
                commands.add(new Command(item.getId() == null ? label : item.getId(), label, item));
            }
        }
    }
    public List<Command> matching(String query) {
        syncCommands();
        String needle = query.toLowerCase(Locale.ROOT);
        return commands.stream().filter(command -> command.label().toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
    public void assignShortcut(Command command, String shortcut) {
        KeyCombination key = shortcut.isBlank() ? null : KeyCombination.keyCombination(shortcut);
        validateShortcut(command, key);
        I18n.setPreference("shortcut." + command.id(), key == null ? "" : key.getName());
        command.item().setAccelerator(key);
    }
    private void applyShortcut(Command command, KeyCombination key) {
        validateShortcut(command, key);
        command.item().setAccelerator(key);
    }
    private void validateShortcut(Command command, KeyCombination key) {
        if (!contains(command)) throw new IllegalArgumentException("Unknown command");
        if (key == null) return;
        if (!(key instanceof KeyCodeCombination)) throw new IllegalArgumentException(
                FeatureUi.text("Bitte eine gültige Tastenkombination eingeben.", "Enter a valid key combination."));
        java.util.Map<KeyCombination, String> reserved = java.util.Map.ofEntries(
                java.util.Map.entry(KeyCombination.keyCombination("Shortcut+A"), "menu.selectAll"),
                java.util.Map.entry(KeyCombination.keyCombination("Shortcut+C"), "menu.copy"),
                java.util.Map.entry(KeyCombination.keyCombination("Shortcut+X"), "menu.cut"),
                java.util.Map.entry(KeyCombination.keyCombination("Shortcut+V"), "menu.paste"),
                java.util.Map.entry(KeyCombination.keyCombination("Shortcut+Z"), "menu.undo"),
                java.util.Map.entry(KeyCombination.keyCombination("Shortcut+Y"), "menu.redo"),
                java.util.Map.entry(KeyCombination.keyCombination("Shortcut+Shift+Z"), "menu.redo"),
                java.util.Map.entry(KeyCombination.keyCombination("Shortcut+T"), "tabs.new"),
                java.util.Map.entry(KeyCombination.keyCombination("Shortcut+Shift+T"), "tabs.reopenClosed"),
                java.util.Map.entry(KeyCombination.keyCombination("Shortcut+W"), "tabs.close"),
                java.util.Map.entry(KeyCombination.keyCombination("Ctrl+Tab"), "tabs.next"),
                java.util.Map.entry(KeyCombination.keyCombination("Ctrl+Shift+Tab"), "tabs.previous"),
                java.util.Map.entry(KeyCombination.keyCombination("Shortcut+Shift+P"), "commands.search"));
        String owner = reserved.entrySet().stream().filter(entry -> effective(entry.getKey()).equals(effective(key)))
                .map(java.util.Map.Entry::getValue).findFirst().orElse(null);
        if (owner != null && !owner.equals(command.id())) {
            throw new IllegalArgumentException(FeatureUi.text("Dieses Kürzel ist reserviert.", "This shortcut is reserved."));
        }
        for (Command other : commands) if (!other.id().equals(command.id()) && effective(key).equals(effective(other.item().getAccelerator()))) {
            throw new IllegalArgumentException(FeatureUi.text("Kürzel bereits belegt: ", "Shortcut already assigned: ") + other.label());
        }
    }
    private static KeyCombination effective(KeyCombination key) {
        if (key == null) return null;
        return KeyCombination.keyCombination(key.getName().replace("Shortcut", 
                System.getProperty("os.name", "").startsWith("Mac") ? "Meta" : "Ctrl"));
    }
    public void pin(Command command, boolean value) {
        if (!contains(command)) throw new IllegalArgumentException("Unknown command");
        if (value) pinned.add(command.id()); else pinned.remove(command.id());
        I18n.setPreference("toolbar.pinned", storedPins()); rebuildToolbar();
    }
    private boolean contains(Command command) {
        return commands.stream().anyMatch(current -> current.id().equals(command.id()) && current.item() == command.item());
    }
    public boolean isPinned(Command command) { return pinned.contains(command.id()); }
    private void rebuildToolbar() {
        syncCommands();
        clearToolbarBindings();
        quickAccess.getChildren().clear();
        for (Command command : commands) if (pinned.contains(command.id())) {
            Button button = FeatureUi.button(command.item().getText(), command.label(), () -> execute(command));
            button.textProperty().bind(command.item().textProperty());
            button.setId("favorite-" + command.id());
            if (CommandIcons.supports(command.id())) button.setGraphic(CommandIcons.create(command.id(), 16));
            var dependencies = new ArrayList<javafx.beans.Observable>();
            for (MenuItem item = command.item(); item != null; item = item.getParentMenu()) {
                dependencies.add(item.disableProperty());
                dependencies.add(item.visibleProperty());
            }
            dependencies.add(command.item().acceleratorProperty());
            dependencies.add(command.item().textProperty());
            dependencies.add(command.item().getProperties());
            if (command.item() instanceof CheckMenuItem check) dependencies.add(check.selectedProperty());
            if (command.item() instanceof RadioMenuItem radio) dependencies.add(radio.selectedProperty());
            var disabled = javafx.beans.binding.Bindings.createBooleanBinding(
                    () -> !available(command.item()), dependencies.toArray(javafx.beans.Observable[]::new));
            button.disableProperty().bind(disabled); toolbarBindings.add(disabled);
            var explanation = javafx.beans.binding.Bindings.createStringBinding(() -> explanation(command),
                    dependencies.toArray(javafx.beans.Observable[]::new));
            button.getTooltip().textProperty().bind(explanation);
            button.accessibleHelpProperty().bind(explanation); toolbarBindings.add(explanation);
            quickAccess.getChildren().add(button);
        }
        quickAccess.setManaged(!quickAccess.getChildren().isEmpty()); quickAccess.setVisible(quickAccess.isManaged());
    }
    private static String explanation(Command command) {
        String result = java.util.Objects.toString(command.item().getProperties().get("command.description"), command.label());
        var shortcut = command.item().getAccelerator();
        if (shortcut != null) result += " (" + shortcut.getDisplayText() + ")";
        boolean toggle = command.item() instanceof CheckMenuItem || command.item() instanceof RadioMenuItem;
        boolean selected = command.item() instanceof CheckMenuItem check && check.isSelected()
                || command.item() instanceof RadioMenuItem radio && radio.isSelected();
        if (toggle) result += "\n" + (selected ? FeatureUi.text("Aktiv", "Active") : FeatureUi.text("Inaktiv", "Inactive"));
        if (!available(command.item())) result += "\n" + FeatureUi.text("Im aktuellen Kontext nicht verfügbar.", "Unavailable in the current context.");
        return result;
    }
    /** Programmatic actions must follow the same enabled state and toggle behavior as menu clicks. */
    boolean execute(Command command) {
        if (!contains(command)) throw new IllegalArgumentException("Unknown command");
        if (registry != null) return registry.execute(command.id());
        MenuItem item = command.item();
        if (!available(item)) return false;
        if (item instanceof RadioMenuItem radio) {
            if (radio.getToggleGroup() == null || !radio.isSelected()) radio.setSelected(!radio.isSelected());
        } else if (item instanceof CheckMenuItem check) check.setSelected(!check.isSelected());
        item.fire();
        return true;
    }
    private static boolean available(MenuItem item) {
        for (MenuItem current = item; current != null; current = current.getParentMenu()) {
            if (current.isDisable() || !current.isVisible()) return false;
        }
        return true;
    }
    public void show() {
        if (registry != null) registry.refresh();
        syncCommands();
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.initOwner(window.getDialogOwner());
        dialog.setTitle(FeatureUi.text("Befehle, Tastenkürzel und Symbolleiste", "Commands, shortcuts and toolbar"));
        TextField filter = new TextField(); filter.setPromptText(FeatureUi.text("Befehl suchen …", "Search commands …"));
        ListView<Command> list = new ListView<>();
        list.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(Command command, boolean empty) {
                super.updateItem(command, empty);
                setGraphic(empty || command == null || !CommandIcons.supports(command.id()) ? null : CommandIcons.create(command.id(), 16));
                setText(empty || command == null ? null : command.label()
                        + (command.item().getAccelerator() == null ? "" : " [" + command.item().getAccelerator().getDisplayText() + "]"));
            }
        });
        list.getItems().setAll(commands);
        filter.textProperty().addListener((observable, oldValue, value) -> list.getItems().setAll(matching(value)));
        TextField shortcut = new TextField(); shortcut.setPromptText("Shortcut+Shift+K");
        CheckBox favorite = new CheckBox(FeatureUi.text("In Symbolleiste anzeigen", "Show in toolbar"));
        list.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, value) -> {
            shortcut.setText(value == null || value.item().getAccelerator() == null ? "" : value.item().getAccelerator().getName());
            favorite.setSelected(value != null && isPinned(value));
        });
        favorite.setOnAction(event -> {
            Command command = list.getSelectionModel().getSelectedItem();
            if (command != null) try { pin(command, favorite.isSelected()); }
            catch (RuntimeException failure) { FeatureUi.error(window.getDialogOwner(), failure); }
        });
        Button assign = FeatureUi.button(FeatureUi.text("Kürzel zuweisen", "Assign shortcut"),
                FeatureUi.text("Kürzel für den ausgewählten Befehl speichern; leer entfernt es", "Save shortcut for the selected command; leave empty to remove"), () -> {
                    Command command = list.getSelectionModel().getSelectedItem();
                    if (command != null) try { assignShortcut(command, shortcut.getText()); list.refresh(); }
                    catch (RuntimeException failure) { FeatureUi.error(window.getDialogOwner(), failure); }
                });
        Button execute = FeatureUi.button(FeatureUi.text("Ausführen", "Run"), FeatureUi.text("Ausgewählten Befehl ausführen", "Run selected command"), () -> {
            Command command = list.getSelectionModel().getSelectedItem();
            if (command != null) {
                if (registry != null) registry.refresh();
                if (available(command.item())) { dialog.close(); execute(command); }
            }
        });
        list.setOnMouseClicked(event -> { if (event.getClickCount() == 2) execute.fire(); });
        dialog.getDialogPane().setContent(new VBox(8, filter, list, shortcut, assign, favorite, execute));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(650, 600); dialog.setResizable(true); dialog.show();
        filter.requestFocus();
    }
}
