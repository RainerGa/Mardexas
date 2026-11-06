package de.richmarkdown.ui;

import java.util.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.Binding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Lazily populated command groups with a compact presentation for narrow windows. */
public final class RibbonView extends VBox implements AutoCloseable {
    private record GroupSource(String id, String label, String icon, List<String> large,
                               List<String> small, List<String> icons, List<String> overflow) {
        List<String> all() {
            List<String> ids = new ArrayList<>(); ids.addAll(large); ids.addAll(small);
            ids.addAll(icons); ids.addAll(overflow); return ids;
        }
    }
    private record GroupLayout(VBox box, HBox actions, MenuButton compact, VBox graphic, Label compactLabel, Label title, BooleanProperty forced) { }
    private final WindowCommandRegistry registry;
    private final TabPane tabs = new TabPane();
    private final Map<String, ButtonBase> controls = new HashMap<>();
    private final Map<String, Tab> locations = new HashMap<>();
    private final Map<Tab, List<GroupSource>> sources = new LinkedHashMap<>();
    private final Map<Tab, ScrollPane> scrolls = new HashMap<>();
    private final Map<Tab, BooleanProperty> expandedGroups = new HashMap<>();
    private final Map<Tab, List<GroupLayout>> layouts = new HashMap<>();
    private final List<Binding<?>> bindings = new ArrayList<>();
    private final List<Runnable> removers = new ArrayList<>();
    private final EditorWindow window;
    private boolean collapsed;

    RibbonView(EditorWindow window, WindowCommandRegistry registry) {
        this.window = window; this.registry = registry;
        getStyleClass().add("rme-ribbon");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        addTab("file", FeatureUi.text("Datei", "File"), "menu.file",
            spec("files", "Dokumente", "Documents", "menu.file", "menu.new,menu.open", "menu.newFromTemplate,menu.openReadOnly", "", ""),
            spec("save", "Speichern", "Save", "menu.save", "menu.save", "menu.saveAs", "", ""),
            spec("print", "Ausgabe", "Output", "menu.print", "menu.print", "file.printLayout", "", ""),
            spec("recent", "Zuletzt geöffnet", "Recent files", "menu.recentFiles", "", "", "", "menu.recentFiles.empty,menu.recentFiles.1,menu.recentFiles.2,menu.recentFiles.3,menu.recentFiles.4,menu.recentFiles.5,menu.recentFiles.6,menu.recentFiles.7,menu.recentFiles.8,menu.recentFiles.9,menu.recentFiles.10,menu.recentFiles.clear"),
            spec("application", "Anwendung", "Application", "menu.quit", "", "menu.quit", "", ""));
        addTab("home", FeatureUi.text("Start", "Home"), "menu.format",
            spec("clipboard", "Zwischenablage", "Clipboard", "menu.paste", "menu.paste", "menu.cut,menu.copy", "", "edit.pastePlainText,edit.pasteMarkdown,edit.pasteLiteralMarkdown"),
            spec("history", "Verlauf", "History", "menu.undo", "", "", "menu.undo,menu.redo", ""),
            spec("font", "Schrift", "Font", "menu.format", "", "", "menu.bold,menu.italic,menu.underline,menu.strikethrough,menu.inlineCode", "edit.uppercase,edit.lowercase,edit.capitalizeSentences,edit.clearTextFormatting,edit.removeLinks"),
            spec("paragraph", "Absatz", "Paragraph", "menu.body", "", "", "menu.bulletList,menu.numberedList,menu.outdent,menu.indent,menu.quote", "menu.listStartNumber,menu.horizontalRule,edit.moveParagraphUp,edit.moveParagraphDown,edit.duplicateParagraph,edit.selectParagraph,edit.ordinaryParagraph,edit.sortLines,edit.uniqueLines"),
            spec("styles", "Formatvorlagen", "Styles", "menu.heading1", "", "menu.body,menu.heading1,menu.heading2", "", "menu.heading3,menu.heading4,menu.heading5,menu.heading6"),
            spec("editing", "Bearbeiten", "Editing", "menu.findReplace", "", "menu.findReplace,menu.findNext,menu.selectAll", "", "commands.search"));
        addTab("insert", FeatureUi.text("Einfügen", "Insert"), "menu.insert",
            spec("content", "Inhalte", "Content", "menu.insert", "menu.image,menu.link", "menu.horizontalRule,menu.codeBlock,insert.codeAssistant", "", ""),
            spec("insert-table", "Tabelle", "Table", "menu.table", "menu.insertTable", "table.pasteTsv,table.importDelimited", "", ""),
            spec("symbols", "Symbole und Datum", "Symbols and date", "insert.specialCharacter", "", "insert.specialCharacter,insert.dateTime", "", ""),
            spec("snippets", "Textbausteine", "Snippets", "menu.writingAssist", "insert.manageSnippets", "insert.insertShortcut,insert.saveSnippet", "", ""),
            spec("templates", "Vorlagen", "Templates", "menu.newFromTemplate", "", "insert.manageTemplates,insert.saveTemplate", "", ""));
        addTab("document", FeatureUi.text("Dokument", "Document"), "menu.statistics",
            spec("statistics", "Statistik und Ziele", "Statistics and goals", "menu.statistics", "tools.writingDashboard", "menu.statistics", "", ""),
            spec("speech", "Vorlesen", "Read aloud", "menu.speech", "speech.read", "speech.stop", "", ""));
        addTab("review", FeatureUi.text("Prüfen", "Review"), "menu.tools.checkIssues",
            spec("proofing", "Dokumentprüfung", "Proofing", "menu.tools.checkIssues", "menu.tools.checkIssues", "tools.accessibility", "", ""),
            spec("compare", "Vergleichen", "Compare", "menu.file.compareWith", "menu.file.compareWith", "", "", ""),
            spec("links", "Links", "Links", "menu.links", "", "links.inventory,links.checkLocal,links.checkWeb", "", "links.replaceDestination"),
            spec("folder", "Dateiübergreifend", "Across files", "search.folder", "search.folder", "", "", ""));
        addTab("view", FeatureUi.text("Ansicht", "View"), "menu.view",
            spec("panes", "Anzeigen", "Show", "menu.view", "", "menu.documentStructure,menu.lineNumbers", "", ""),
            spec("writing", "Schreibansicht", "Writing view", "menu.writingView", "view.distractionFree", "view.focusParagraph,view.typewriter,view.readingWidth", "", ""),
            spec("wrap", "Zeilenumbruch", "Line wrapping", "menu.lineWrap", "", "menu.lineWrap.AUTOMATIC,menu.lineWrap.CONTINUOUS", "", ""),
            spec("surface", "Bedienoberfläche", "Interface", "menu.commandSurface", "", "ui.mode.classic,ui.mode.ribbon", "", ""));
        addTab("table", FeatureUi.text("Tabelle", "Table"), "menu.table",
            spec("tables", "Tabelle erstellen", "Create table", "menu.table", "menu.insertTable", "table.importDelimited,table.pasteTsv", "", "table.exportDelimited"),
            spec("cells", "Zellen", "Cells", "table.copyRegion", "", "table.copyRegion,table.pasteRegion", "", ""),
            spec("rows", "Zeilen und Spalten", "Rows and columns", "table.moveRow", "", "table.moveRow,table.moveColumn,table.duplicateRow", "", "table.duplicateColumn"),
            spec("data", "Daten", "Data", "table.sortColumns", "", "table.sortColumns,table.calculateColumn,table.transpose", "", ""),
            spec("structure", "Tabellenstruktur", "Table structure", "table.split", "", "table.split,table.joinNext", "", ""));
        addTab("window", FeatureUi.text("Fenster", "Window"), "menu.window",
            spec("tabs", "Dokument-Tabs", "Document tabs", "menu.documentTabs", "tabs.new", "tabs.next,tabs.previous,tabs.reopenClosed", "", "tabs.close"),
            spec("arrange", "Anordnen", "Arrange", "tabs.sideBySide", "tabs.sideBySide", "tabs.closeSideView,menu.minimize,menu.maximize", "", "menu.closeAll"));
        addTab("help", FeatureUi.text("Hilfe", "Help"), "menu.help",
            spec("help", "Hilfe und Informationen", "Help and information", "menu.help", "menu.showHelp", "menu.about,menu.licensing,menu.thanks", "", ""),
            spec("language", "Sprache", "Language", "menu.language", "", "", "", "menu.language.de,menu.language.en,menu.language.fr,menu.language.es,menu.language.pt-BR,menu.language.ja,menu.language.zh-CN,menu.language.it,menu.language.ko,menu.language.nl"));
        // New classic commands remain discoverable even before a dedicated layout is added.
        List<String> missing = registry.ids().stream().filter(id -> !locations.containsKey(id)).sorted().toList();
        if (!missing.isEmpty()) {
            Tab help = tabs.getTabs().getLast();
            List<GroupSource> groups = new ArrayList<>(sources.get(help));
            GroupSource extra = new GroupSource("additional", FeatureUi.text("Weitere Befehle", "More commands"), "commands.search", List.of(), List.of(), List.of(), missing);
            groups.add(extra); sources.put(help, List.copyOf(groups)); missing.forEach(id -> locations.put(id, help));
        }
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            if (old != null && expandedGroups.containsKey(old)) expandedGroups.get(old).set(false);
            populate(value);
        });
        Button collapse = new Button(); collapse.setId("ribbon-collapse");
        collapse.setGraphic(CommandIcons.create("view.distractionFree", 14));
        collapse.getStyleClass().add("rme-ribbon-collapse");
        String explanation = FeatureUi.text("Ribbon ein- oder ausklappen", "Collapse or expand ribbon");
        collapse.setAccessibleText(explanation); collapse.setTooltip(new Tooltip(explanation));
        collapse.setOnAction(event -> setCollapsed(!collapsed));
        StackPane shell = new StackPane(tabs, collapse);
        StackPane.setAlignment(collapse, javafx.geometry.Pos.TOP_RIGHT);
        collapse.setMaxSize(26, 26); collapse.setMinSize(26, 26);
        tabs.setMinHeight(Region.USE_PREF_SIZE); tabs.setMaxHeight(Region.USE_PREF_SIZE);
        getChildren().add(shell);
        tabs.getSelectionModel().select(1);
        populate(tabs.getSelectionModel().getSelectedItem());
    }
    private static List<String> ids(String value) { return value.isEmpty() ? List.of() : List.of(value.split(",")); }
    private GroupSource spec(String id, String de, String en, String icon, String large, String small, String icons, String overflow) {
        return new GroupSource(id, FeatureUi.text(de, en), icon, present(large), present(small), present(icons), present(overflow));
    }
    private List<String> present(String value) { return ids(value).stream().filter(id -> registry.get(id) != null).toList(); }
    private void addTab(String id, String title, String icon, GroupSource... groups) {
        Tab tab = new Tab(title); tab.setId("ribbon-tab-" + id); tab.setGraphic(CommandIcons.create(icon, 16));
        sources.put(tab, List.of(groups)); tabs.getTabs().add(tab);
        for (GroupSource group : groups) for (String command : group.all()) locations.putIfAbsent(command, tab);
    }
    private void setCollapsed(boolean value) {
        collapsed = value;
        tabs.setPrefHeight(value ? 34 : Region.USE_COMPUTED_SIZE);
    }
    private void populate(Tab tab) {
        if (tab == null || tab.getContent() != null) return;
        BooleanProperty forceExpanded = new SimpleBooleanProperty(); expandedGroups.put(tab, forceExpanded);
        HBox groups = new HBox(0); groups.setMinWidth(0); groups.getStyleClass().add("rme-ribbon-groups");
        List<GroupLayout> tabLayouts = new ArrayList<>(); layouts.put(tab, tabLayouts);
        for (GroupSource source : sources.get(tab)) {
            if (source.all().isEmpty()) continue;
            tabLayouts.add(group(source, groups, forceExpanded));
        }
        ScrollPane scroll = new ScrollPane(groups); scroll.setFitToHeight(true); scroll.setFitToWidth(true);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        // A vertical resize can change viewport height without changing the ribbon width.
        // Do not dismantle and rebuild the responsive groups for those notifications.
        scroll.viewportBoundsProperty().addListener((obs, old, value) -> {
            if (Double.compare(old.getWidth(), value.getWidth()) != 0) adapt(tab, value.getWidth());
        });
        forceExpanded.addListener((obs, old, value) -> { scroll.setFitToWidth(!value); adapt(tab, scroll.getViewportBounds().getWidth()); });
        tab.setContent(scroll); scrolls.put(tab, scroll);
    }
    private GroupLayout group(GroupSource source, HBox parent, BooleanProperty forceExpanded) {
        HBox actions = new HBox(4); actions.getStyleClass().add("rme-ribbon-group-actions");
        for (String id : source.large()) actions.getChildren().add(sizedButton(id, "large"));
        addColumns(actions, source.small(), "small");
        VBox iconStack = null;
        if (!source.icons().isEmpty()) {
            HBox iconRow = new HBox(2); iconRow.getStyleClass().add("rme-ribbon-icon-row");
            source.icons().forEach(id -> iconRow.getChildren().add(sizedButton(id, "icon")));
            iconStack = new VBox(8, iconRow); actions.getChildren().add(iconStack);
        }
        if (!source.overflow().isEmpty()) {
            MenuButton more = new MenuButton(FeatureUi.text("Mehr", "More"), CommandIcons.create(source.icon(), 16));
            more.setId("ribbon-more-" + source.id()); more.getStyleClass().add("rme-ribbon-overflow");
            if (source.large().isEmpty() && source.small().isEmpty() && source.icons().isEmpty()) more.setText(source.label());
            more.setAccessibleText(source.label() + ": " + FeatureUi.text("Weitere Befehle", "More commands"));
            more.setTooltip(new Tooltip(more.getAccessibleText()));
            source.overflow().forEach(id -> more.getItems().add(proxy(registry.get(id))));
            HBox extra = new HBox(3, more);
            HBox expanded = new HBox(3);
            expanded.visibleProperty().bind(forceExpanded); expanded.managedProperty().bind(forceExpanded);
            for (int i = 0; i < source.overflow().size(); i += 3) {
                VBox column = new VBox(2);
                column.visibleProperty().bind(forceExpanded); column.managedProperty().bind(forceExpanded);
                for (int row = i; row < Math.min(i + 3, source.overflow().size()); row++)
                    column.getChildren().add(sizedButton(source.overflow().get(row), "small"));
                expanded.getChildren().add(column);
            }
            if (iconStack != null) iconStack.getChildren().add(extra);
            else if (source.id().equals("clipboard") && actions.getChildren().getLast() instanceof VBox column)
                column.getChildren().add(extra);
            else actions.getChildren().add(extra);
            actions.getChildren().add(expanded);
        }
        MenuButton compact = new MenuButton();
        compact.setId("ribbon-compact-" + source.id()); compact.getStyleClass().add("rme-ribbon-compact-group");
        String compactName = switch (source.id()) {
            case "clipboard" -> FeatureUi.text("Ablage", "Clipboard");
            case "styles" -> FeatureUi.text("Vorlagen", "Styles");
            default -> source.label();
        };
        Label compactLabel = new Label(compactName);
        compactLabel.setId("ribbon-compact-label-" + source.id());
        compactLabel.getStyleClass().add("rme-ribbon-compact-label");
        compactLabel.setWrapText(true); compactLabel.setTextOverrun(OverrunStyle.CLIP);
        compactLabel.setAlignment(javafx.geometry.Pos.CENTER);
        compactLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        compactLabel.setMinWidth(0); compactLabel.setPrefWidth(60); compactLabel.setMaxWidth(60);
        compactLabel.setMinHeight(Region.USE_PREF_SIZE);
        compactLabel.setMaxHeight(Double.MAX_VALUE);
        VBox compactGraphic = new VBox(4, CommandIcons.create(source.icon(), 24), compactLabel);
        compactGraphic.setMinWidth(0); compactGraphic.setPrefWidth(60); compactGraphic.setMaxWidth(60);
        compactGraphic.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        compact.setGraphic(compactGraphic); compact.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        compact.setAccessibleText(source.label()); compact.setTooltip(new Tooltip(source.label()));
        compact.setMinHeight(94); compact.setMaxHeight(94); compact.setMinWidth(90); compact.setPrefWidth(90);
        source.all().forEach(id -> compact.getItems().add(proxy(registry.get(id))));
        compact.setVisible(false); compact.setManaged(false);
        Label title = new Label(source.label()); title.getStyleClass().add("rme-ribbon-group-title");
        title.setMinHeight(Region.USE_PREF_SIZE);
        title.setMaxWidth(Double.MAX_VALUE); title.setAlignment(javafx.geometry.Pos.CENTER);
        VBox body = new VBox(actions, compact); body.setMinSize(0, 0); body.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        VBox.setVgrow(body, Priority.ALWAYS);
        VBox box = new VBox(2, body, title); box.setId("ribbon-group-" + source.id());
        box.getStyleClass().add("rme-ribbon-group"); box.setMinHeight(106); box.setPrefHeight(106); box.setMaxHeight(106);
        parent.getChildren().add(box); return new GroupLayout(box, actions, compact, compactGraphic, compactLabel, title, forceExpanded);
    }
    private void addColumns(HBox actions, List<String> ids, String size) {
        for (int i = 0; i < ids.size(); i += 3) {
            VBox column = new VBox(2); column.getStyleClass().add("rme-ribbon-column");
            for (int row = i; row < Math.min(i + 3, ids.size()); row++) column.getChildren().add(sizedButton(ids.get(row), size));
            actions.getChildren().add(column);
        }
    }
    private ButtonBase sizedButton(String id, String size) {
        ButtonBase button = button(registry.get(id));
        button.getStyleClass().add("rme-ribbon-" + size + "-command");
        if (size.equals("large")) {
            button.setGraphic(CommandIcons.create(id, 32)); button.setContentDisplay(ContentDisplay.TOP);
            button.setMinHeight(80); button.setMaxHeight(80); button.setMinWidth(64); button.setPrefWidth(82); button.setMaxWidth(100);
            String shortLabel = switch (id) {
                case "insert.manageSnippets" -> FeatureUi.text("Textbausteine", "Snippets");
                case "tools.writingDashboard" -> FeatureUi.text("Schreibziele", "Writing goals");
                case "menu.tools.checkIssues" -> FeatureUi.text("Format prüfen", "Check format");
                case "menu.file.compareWith" -> FeatureUi.text("Vergleichen", "Compare");
                case "search.folder" -> FeatureUi.text("Ordner suchen", "Search folder");
                case "tabs.sideBySide" -> FeatureUi.text("Nebeneinander", "Side by side");
                case "speech.read" -> FeatureUi.text("Vorlesen", "Read aloud");
                default -> null;
            };
            if (shortLabel != null) { button.textProperty().unbind(); button.setText(shortLabel); }
            button.setWrapText(true); button.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        } else if (size.equals("icon")) {
            button.textProperty().unbind(); button.setText(""); button.setMinSize(28, 28); button.setMaxSize(28, 28);
        }
        return button;
    }
    private void adapt(Tab tab, double available) {
        List<GroupLayout> groups = layouts.get(tab); if (groups == null || available <= 0) return;
        boolean narrow = available < 650 && !expandedGroups.get(tab).get();
        // Measure both presentations without toggling visible/managed nodes. Resetting
        // every group to expanded before measuring invalidates the ScrollPane layout
        // during its own viewport notification and makes unchanged groups flicker.
        double[] expandedWidths = new double[groups.size()];
        double required = 0;
        for (int i = 0; i < groups.size(); i++) {
            GroupLayout group = groups.get(i);
            expandedWidths[i] = Math.max(group.actions().prefWidth(-1), group.title().prefWidth(-1))
                    + group.box().getInsets().getLeft() + group.box().getInsets().getRight();
            required += expandedWidths[i];
        }
        boolean[] compact = new boolean[groups.size()];
        if (!narrow) {
            for (int i = groups.size() - 1; i >= 0 && required > available - 4; i--) {
                GroupLayout group = groups.get(i);
                if (group.forced().get()) continue;
                compact[i] = true;
                double compactWidth = 90 + group.box().getInsets().getLeft() + group.box().getInsets().getRight();
                required -= expandedWidths[i] - compactWidth;
            }
        }
        for (int i = 0; i < groups.size(); i++) {
            GroupLayout group = groups.get(i);
            double cell = narrow ? Math.max(42, (available - 26) / groups.size()) : Region.USE_COMPUTED_SIZE;
            double labelWidth = narrow ? Math.max(18, cell - 36) : 60;
            double compactWidth = narrow ? Math.max(28, cell - 14) : 90;
            group.box().setMinWidth(0);
            group.box().setPrefWidth(cell);
            group.box().setMaxWidth(narrow ? cell : Double.MAX_VALUE);
            group.compact().setMinWidth(narrow ? 0 : 90);
            group.compact().setPrefWidth(compactWidth);
            group.compact().setMaxWidth(compactWidth);
            group.graphic().setPrefWidth(labelWidth); group.graphic().setMaxWidth(labelWidth);
            group.compactLabel().setPrefWidth(labelWidth); group.compactLabel().setMaxWidth(labelWidth);
            boolean showActions = !narrow && !compact[i];
            group.actions().setVisible(showActions); group.actions().setManaged(showActions);
            group.title().setVisible(showActions); group.title().setManaged(showActions);
            group.compact().setVisible(!showActions); group.compact().setManaged(!showActions);
        }
    }

    private MenuItem proxy(EditorCommand command) {
        MenuItem item = command.isToggle() ? new CheckMenuItem(command.label()) : new MenuItem(command.label());
        item.textProperty().bind(command.labelProperty());
        item.setId("ribbon-menu-" + command.id()); item.setGraphic(CommandIcons.create(command.id(), 16));
        item.disableProperty().bind(command.enabledProperty().not()); item.visibleProperty().bind(command.visibleProperty());
        item.acceleratorProperty().bind(command.shortcutProperty());
        if (item instanceof CheckMenuItem check) {
            check.setSelected(command.isSelected());
            javafx.beans.value.ChangeListener<Boolean> selected = (obs, old, value) -> check.setSelected(value);
            command.selectedProperty().addListener(selected); removers.add(() -> command.selectedProperty().removeListener(selected));
        }
        Tooltip.install(item.getGraphic(), tooltip(command));
        item.setOnAction(event -> execute(command));
        return item;
    }
    private Tooltip tooltip(EditorCommand command) {
        Tooltip tooltip = new Tooltip();
        var explanation = Bindings.createStringBinding(command::explanation,
                command.enabledProperty(), command.shortcutProperty(), command.selectedProperty(), command.descriptionProperty(), command.labelProperty());
        bindings.add(explanation); tooltip.textProperty().bind(explanation); return tooltip;
    }
    private ButtonBase button(EditorCommand command) {
        ButtonBase button = command.isToggle() ? new ToggleButton(command.label()) : new Button(command.label());
        button.textProperty().bind(command.labelProperty());
        button.setGraphic(CommandIcons.create(command.iconId(), 20));
        button.setId("ribbon-" + command.id()); button.accessibleTextProperty().bind(command.labelProperty());
        button.setMinHeight(28); button.setMaxHeight(28); button.setMinWidth(Region.USE_PREF_SIZE);
        button.disableProperty().bind(command.enabledProperty().not());
        button.visibleProperty().bind(command.visibleProperty()); button.managedProperty().bind(button.visibleProperty());
        if (button instanceof ToggleButton toggle) {
            toggle.setSelected(command.isSelected());
            javafx.beans.value.ChangeListener<Boolean> selected = (obs, old, value) -> toggle.setSelected(value);
            command.selectedProperty().addListener(selected); removers.add(() -> command.selectedProperty().removeListener(selected));
        }
        button.setTooltip(tooltip(command));
        button.setOnAction(event -> {
            try { execute(command); }
            finally { if (button instanceof ToggleButton toggle) toggle.setSelected(command.isSelected()); }
        });
        controls.putIfAbsent(command.id(), button); return button;
    }
    private void execute(EditorCommand command) {
        registry.execute(command.id());
        if (window.getDialogOwner().getScene() != null && !command.id().startsWith("ui.mode."))
            window.getEditorControl().requestFocus();
    }
    public TabPane getTabs() { return tabs; }
    public Set<String> commandIds() { return Set.copyOf(locations.keySet()); }
    public String tabTitle(String commandId) {
        Tab tab = locations.get(commandId);
        if (tab == null) throw new IllegalArgumentException("Unknown ribbon command: " + commandId);
        return tab.getText();
    }
    public Node findControl(String id) { return controls.get(id); }
    public void selectCommand(String id) {
        Tab tab = locations.get(id);
        if (tab == null) throw new IllegalArgumentException("Unknown ribbon command: " + id);
        populate(tab); tabs.getSelectionModel().select(tab); expandedGroups.get(tab).set(true);
        setCollapsed(false);
        Platform.runLater(() -> {
            applyCss(); layout();
            ScrollPane scroll = scrolls.get(tab); Node button = controls.get(id);
            if (button != null && scroll != null) {
                double width = scroll.getContent().getBoundsInLocal().getWidth() - scroll.getViewportBounds().getWidth();
                double x = scroll.getContent().sceneToLocal(button.localToScene(button.getBoundsInLocal())).getMinX();
                scroll.setHvalue(width <= 0 ? 0 : Math.min(1, Math.max(0, x / width)));
            }
        });
    }
    @Override public void close() {
        removers.forEach(Runnable::run); removers.clear();
        bindings.forEach(Binding::dispose); bindings.clear();
        controls.clear(); locations.clear(); sources.clear(); scrolls.clear();
        expandedGroups.clear(); layouts.clear(); tabs.getTabs().clear();
    }
}
