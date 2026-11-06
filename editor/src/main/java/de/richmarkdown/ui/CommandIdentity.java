package de.richmarkdown.ui;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

/** Stable application command identities shared by menus, ribbons, shortcuts and favorites. */
public final class CommandIdentity {
    public static final String REFRESH = "command.refresh";
    public static final String LEGACY_ID = "command.legacyId";
    private static final Map<String, String> KNOWN_NAMES = Map.ofEntries(
            Map.entry("Datei", "menu.file"), Map.entry("File", "menu.file"),
            Map.entry("Bearbeiten", "menu.edit"), Map.entry("Edit", "menu.edit"),
            Map.entry("Ansicht", "menu.view"), Map.entry("View", "menu.view"),
            Map.entry("Einfügen", "menu.insert"), Map.entry("Insert", "menu.insert"),
            Map.entry("Format", "menu.format"), Map.entry("Tabelle", "menu.table"), Map.entry("Table", "menu.table"),
            Map.entry("Extras", "menu.extras"), Map.entry("Tools", "menu.extras"),
            Map.entry("Fenster", "menu.window"), Map.entry("Window", "menu.window"),
            Map.entry("Hilfe", "menu.help"), Map.entry("Help", "menu.help"),
            Map.entry("Schreibansicht", "menu.writingView"), Map.entry("Writing view", "menu.writingView"));

    private CommandIdentity() { }

    /** Explicit semantic IDs are retained. Legacy positional IDs remain available for profile migration. */
    public static void assign(MenuBar menus) {
        for (int i = 0; i < menus.getMenus().size(); i++) {
            Menu menu = menus.getMenus().get(i);
            assign(menu, "feature.menu" + i, "menu");
        }
    }

    private static void assign(MenuItem item, String legacyPath, String parent) {
        String oldId = item.getId();
        item.getProperties().putIfAbsent(LEGACY_ID,
                oldId != null && oldId.startsWith("feature.menu") ? oldId : legacyPath);
        if (oldId == null || oldId.isBlank() || oldId.startsWith("feature.menu")) {
            String known = KNOWN_NAMES.get(item.getText());
            item.setId(known != null ? known : parent + "." + slug(item.getText()));
        }
        if (item instanceof Menu menu) {
            for (int i = 0; i < menu.getItems().size(); i++) {
                MenuItem child = menu.getItems().get(i);
                if (!(child instanceof SeparatorMenuItem)) assign(child, legacyPath + ".item" + i, item.getId());
            }
        }
    }

    /** Finds a semantic root without relying on menu ordering or translated display labels. */
    public static Menu menu(MenuBar menus, String id) {
        return menus.getMenus().stream().filter(menu -> id.equals(menu.getId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing application menu: " + id));
    }

    private static String slug(String label) {
        String value = Normalizer.normalize(label == null ? "unnamed" : label, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-|-$", "");
        return value.isBlank() ? "unnamed" : value;
    }
}
