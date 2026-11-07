package de.richmarkdown.ui;

import java.util.*;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;

/** One dispatcher per document; presentations share state, never JavaFX nodes. */
public final class WindowCommandRegistry implements AutoCloseable {
    private static final Set<String> FORMAT_TOGGLES = Set.of("menu.bold", "menu.italic", "menu.underline",
            "menu.strikethrough", "menu.inlineCode", "menu.bulletList", "menu.numberedList");
    private record Entry(MenuItem item, EventHandler<ActionEvent> action, EditorCommand command) { }
    private final EditorWindow window;
    private final MenuBar menuBar;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, Runnable> handlers = new HashMap<>();
    private final Set<String> blocked = new HashSet<>();
    private java.util.function.Consumer<RuntimeException> errorHandler;
    private final List<Runnable> removers = new ArrayList<>();
    private final InvalidationListener listener = observable -> schedule();
    private final Runnable documentListener = this::schedule;
    private final EventHandler<KeyEvent> keys = this::keyPressed;
    private boolean pending, refreshing, closed;

    public WindowCommandRegistry(EditorWindow window, MenuBar menuBar) {
        this.window = window; this.menuBar = menuBar;
        CommandIdentity.assign(menuBar);
        for (Menu menu : menuBar.getMenus()) collect(menu);
        var control = window.getEditorControl();
        for (var property : List.of(control.caretPositionProperty(), control.selectionProperty(),
                control.editableProperty(), control.decorationAtCaretProperty(), control.decorationAtParagraphProperty())) {
            property.addListener(listener); removers.add(() -> property.removeListener(listener));
        }
        window.getEditor().addChangeListener(documentListener);
        window.getEditorRoot().addEventFilter(KeyEvent.KEY_PRESSED, keys);
        refresh();
    }

    private void collect(MenuItem item) {
        item.setGraphic(CommandIcons.create(item.getId(), 18));
        for (var property : List.of(item.disableProperty(), item.visibleProperty(), item.acceleratorProperty(), item.textProperty())) {
            property.addListener(listener); removers.add(() -> property.removeListener(listener));
        }
        javafx.collections.MapChangeListener<Object, Object> descriptionChanged = change -> {
            if ("command.description".equals(change.getKey())) schedule();
        };
        item.getProperties().addListener(descriptionChanged);
        removers.add(() -> item.getProperties().removeListener(descriptionChanged));
        if (item instanceof Menu menu) {
            var explanation = javafx.beans.binding.Bindings.createStringBinding(() -> menu.getText().replace("_", ""), menu.textProperty());
            installTooltip(menu, explanation);
            for (MenuItem child : menu.getItems()) if (!(child instanceof SeparatorMenuItem)) collect(child);
            return;
        }
        EditorCommand command = new EditorCommand(item.getId(), item.getText().replace("_", ""),
                item.getText().replace("_", ""), item instanceof CheckMenuItem || item instanceof RadioMenuItem || FORMAT_TOGGLES.contains(item.getId()));
        if (mutatesDocument(item.getId()) && !item.disableProperty().isBound())
            item.disableProperty().bind(window.getEditorControl().editableProperty().not());
        var explanation = javafx.beans.binding.Bindings.createStringBinding(command::explanation,
                command.descriptionProperty(), command.labelProperty(), command.enabledProperty(), command.shortcutProperty(), command.selectedProperty());
        installTooltip(item, explanation);
        Entry entry = new Entry(item, item.getOnAction(), command);
        entries.putIfAbsent(item.getId(), entry);
        item.setOnAction(event -> dispatch(item.getId(), false));
        if (item instanceof CheckMenuItem check) { check.selectedProperty().addListener(listener);
            removers.add(() -> check.selectedProperty().removeListener(listener)); }
        if (item instanceof RadioMenuItem radio) { radio.selectedProperty().addListener(listener);
            removers.add(() -> radio.selectedProperty().removeListener(listener)); }
    }

    private void installTooltip(MenuItem item, javafx.beans.binding.StringBinding explanation) {
        Tooltip tooltip = new Tooltip(); tooltip.textProperty().bind(explanation);
        // JavaFX MenuItem is not a Node; its owned graphic is the supported hover target.
        Tooltip.install(item.getGraphic(), tooltip);
        item.getProperties().put("command.tooltip", tooltip);
        removers.add(() -> {
            Tooltip.uninstall(item.getGraphic(), tooltip); tooltip.textProperty().unbind(); explanation.dispose();
            item.getProperties().remove("command.tooltip");
        });
    }

    private void schedule() {
        if (pending || refreshing || closed) return;
        pending = true;
        Platform.runLater(() -> { pending = false; if (!closed) refresh(); });
    }

    public void refresh() {
        if (closed || refreshing) return;
        refreshing = true;
        try {
            for (Menu menu : menuBar.getMenus()) refreshContext(menu);
            var selectionStyles = selectionStyles();
            for (Entry entry : entries.values()) {
                boolean available = true, visible = true;
                for (MenuItem item = entry.item(); item != null; item = item.getParentMenu()) {
                    available &= !item.isDisable(); visible &= item.isVisible();
                }
                boolean selected = entry.item() instanceof CheckMenuItem check ? check.isSelected()
                        : entry.item() instanceof RadioMenuItem radio && radio.isSelected();
                if (FORMAT_TOGGLES.contains(entry.command().id())) selected = formatSelected(entry.command().id(), selectionStyles);
                entry.command().presentation(entry.item().getText().replace("_", ""),
                        Objects.toString(entry.item().getProperties().get("command.description"), entry.item().getText().replace("_", "")));
                entry.command().update(available && visible && !blocked.contains(entry.command().id()), visible, selected, entry.item().getAccelerator());
            }
        } finally { refreshing = false; }
    }

    private static boolean mutatesDocument(String id) {
        return FORMAT_TOGGLES.contains(id) || Set.of("menu.heading1", "menu.heading2", "menu.heading3", "menu.heading4",
                "menu.heading5", "menu.heading6", "menu.body", "menu.quote", "menu.indent", "menu.outdent",
                "menu.listStartNumber", "menu.horizontalRule", "menu.link", "menu.image", "menu.codeBlock", "menu.insertTable").contains(id);
    }
    private List<com.gluonhq.richtextarea.model.DecorationModel> selectionStyles() {
        var selection = window.getEditorControl().getSelection();
        if (selection == null || !selection.isDefined()
                || !(window.getEditorControl().getSkin() instanceof com.gluonhq.richtextarea.RichTextAreaSkin skin)) return null;
        var buffer = skin.getViewModel().getTextBuffer();
        int start = buffer.getText(0, selection.getStart()).length();
        int end = buffer.getText(0, selection.getEnd()).length();
        return CommandDocumentSnapshot.get(window.getEditor()).getDecorations().stream()
                .filter(run -> run.getStart() < end && run.getStart() + run.getLength() > start).toList();
    }
    private boolean formatSelected(String id, List<com.gluonhq.richtextarea.model.DecorationModel> selectionStyles) {
        if (selectionStyles != null) return !selectionStyles.isEmpty() && selectionStyles.stream()
                .allMatch(run -> formatMatches(id, run.getDecoration(), run.getParagraphDecoration()));
        return formatMatches(id, window.getEditorControl().getDecorationAtCaret(),
                window.getEditorControl().getDecorationAtParagraph());
    }
    private boolean formatMatches(String id, com.gluonhq.richtextarea.model.Decoration decoration,
                                  com.gluonhq.richtextarea.model.ParagraphDecoration pd) {
        var td = decoration instanceof com.gluonhq.richtextarea.model.TextDecoration text ? text : null;
        return switch (id) {
            case "menu.bold" -> td != null && td.getFontWeight() == javafx.scene.text.FontWeight.BOLD;
            case "menu.italic" -> td != null && td.getFontPosture() == javafx.scene.text.FontPosture.ITALIC;
            case "menu.underline" -> td != null && td.isUnderline();
            case "menu.strikethrough" -> td != null && td.isStrikethrough();
            case "menu.inlineCode" -> td != null && de.richmarkdown.markdown.MarkdownStyles.MONOSPACED.equals(td.getFontFamily());
            case "menu.bulletList" -> pd != null && pd.getGraphicType() == com.gluonhq.richtextarea.model.ParagraphDecoration.GraphicType.BULLETED_LIST;
            case "menu.numberedList" -> pd != null && pd.getGraphicType() == com.gluonhq.richtextarea.model.ParagraphDecoration.GraphicType.NUMBERED_LIST;
            default -> false;
        };
    }
    private void refreshContext(Menu menu) {
        Object action = menu.getProperties().get(CommandIdentity.REFRESH);
        if (action instanceof Runnable runnable) runnable.run();
        for (MenuItem child : menu.getItems()) if (child instanceof Menu nested) refreshContext(nested);
    }

    public Collection<EditorCommand> commands() { return entries.values().stream().map(Entry::command).toList(); }
    public Set<String> ids() { return Set.copyOf(entries.keySet()); }
    public EditorCommand get(String id) { return require(id).command(); }
    MenuItem item(String id) { return require(id).item(); }
    MenuBar menuBar() { return menuBar; }
    private Entry require(String id) {
        Entry result = entries.get(id);
        if (result == null) throw new IllegalArgumentException("Unknown command: " + id);
        return result;
    }
    public boolean execute(String id) { checkThread(); return dispatch(id, true); }

    private static void checkThread() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Use the JavaFX application thread");
    }

    /** Overrides a command for host file/window policy. Null restores its default handler. */
    public void setHandler(String id, Runnable handler) {
        checkThread();
        require(id);
        if (closed) throw new IllegalStateException("Command registry is closed");
        if (handler == null) handlers.remove(id); else handlers.put(id, handler);
    }
    /** Additional host capability gate; document context/read-only checks still apply. */
    public void setAllowed(String id, boolean allowed) {
        checkThread();
        require(id);
        if (closed) throw new IllegalStateException("Command registry is closed");
        if (allowed) blocked.remove(id); else blocked.add(id);
        refresh();
    }
    /** Receives failed commands instead of displaying the default error dialog. */
    public void setErrorHandler(java.util.function.Consumer<RuntimeException> handler) {
        checkThread(); errorHandler = handler;
    }
    private boolean dispatch(String id, boolean toggle) {
        refresh(); Entry entry = require(id);
        if (closed || !entry.command().isEnabled()) return false;
        if (toggle && entry.item() instanceof CheckMenuItem check) check.setSelected(!check.isSelected());
        if (toggle && entry.item() instanceof RadioMenuItem radio) radio.setSelected(true);
        try {
            Runnable handler = handlers.get(id);
            if (handler != null) handler.run();
            else if (entry.action() != null) entry.action().handle(new ActionEvent(entry.item(), entry.item()));
        } catch (RuntimeException failure) {
            refresh();
            if (errorHandler != null) errorHandler.accept(failure); else FeatureUi.error(window.getDialogOwner(), failure);
            return false;
        }
        refresh(); return true;
    }
    private void keyPressed(KeyEvent event) {
        if (event.getTarget() instanceof TextInputControl) {
            // Native text-field editing must keep its own clipboard/undo behavior.
            // Document format accelerators must be consumed here: the Scene's
            // MenuBar accelerator would otherwise still format the document.
            for (Entry entry : entries.values()) {
                var key = entry.item().getAccelerator();
                if (key != null && key.match(event)
                        && mutatesDocument(entry.command().id())) {
                    event.consume(); break;
                }
            }
            return;
        }
        for (Entry entry : entries.values()) {
            var key = entry.item().getAccelerator();
            if (key != null && key.match(event)) { execute(entry.command().id()); event.consume(); return; }
        }
    }
    @Override public void close() {
        if (closed) return;
        closed = true; removers.forEach(Runnable::run); removers.clear();
        handlers.clear(); blocked.clear(); errorHandler = null;
        window.getEditor().removeChangeListener(documentListener);
        CommandDocumentSnapshot.clear(window.getEditor());
        window.getEditorRoot().removeEventFilter(KeyEvent.KEY_PRESSED, keys);
    }
}
