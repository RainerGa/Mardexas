/* Copyright (C) 2026 Rainer Garbotz. SPDX-License-Identifier: GPL-3.0-or-later */
package de.richmarkdown.api;

import de.richmarkdown.ui.CommandIcons;
import de.richmarkdown.ui.EditorCommand;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A host menu contribution that can follow the selected editor tab.
 * Each contribution owns independent MenuItems/icons, and removes only its own menus.
 * All methods run on the FX thread. Close before closing the active editor.
 * Hosts can build arbitrary toolbars/context menus directly from EditorCommand as well.
 */
public final class EditorMenus implements AutoCloseable {
    private final MenuBar target;
    private final List<Menu> contributed = new ArrayList<>();
    private final List<Runnable> removers = new ArrayList<>();
    private boolean closed;

    /** Appends editor menus after existing host menus; initially no editor is active. */
    public EditorMenus(MenuBar target) {
        EditorPane.checkThread(); this.target = Objects.requireNonNull(target, "target");
    }
    /** Rebinds to a different document. Null removes the contribution, preserving host menus. */
    public void setActiveEditor(EditorPane editor) {
        EditorPane.checkThread();
        if (closed) throw new IllegalStateException("EditorMenus is closed");
        if (editor != null && editor.isClosed()) throw new IllegalStateException("EditorPane is closed");
        clear();
        if (editor == null) return;
        editor.getCommands().refresh();
        try {
            for (Menu source : editor.menuSource().getMenus()) {
                contributed.add((Menu) copy(source, editor));
            }
            target.getMenus().addAll(contributed);
        } catch (RuntimeException failure) { clear(); throw failure; }
    }
    private MenuItem copy(MenuItem source, EditorPane editor) {
        if (source instanceof SeparatorMenuItem) return new SeparatorMenuItem();
        MenuItem item;
        if (source instanceof Menu sourceMenu) {
            Menu menu = new Menu();
            for (MenuItem child : sourceMenu.getItems()) menu.getItems().add(copy(child, editor));
            menu.textProperty().bind(source.textProperty());
            menu.disableProperty().bind(source.disableProperty());
            menu.visibleProperty().bind(source.visibleProperty());
            menu.setOnShowing(event -> editor.getCommands().refresh());
            removers.add(() -> { menu.textProperty().unbind(); menu.disableProperty().unbind(); menu.visibleProperty().unbind(); menu.setOnShowing(null); });
            item = menu;
        } else {
            EditorCommand command = editor.getCommands().get(source.getId());
            item = command.isToggle() ? new CheckMenuItem() : new MenuItem();
            item.textProperty().bind(command.labelProperty());
            item.disableProperty().bind(command.enabledProperty().not());
            item.visibleProperty().bind(command.visibleProperty());
            item.acceleratorProperty().bind(command.shortcutProperty());
            MenuItem actionItem = item;
            item.setOnAction(event -> {
                if (!editor.isClosed()) editor.getCommands().execute(command.id());
                if (actionItem instanceof CheckMenuItem check) check.setSelected(command.isSelected());
            });
            if (item instanceof CheckMenuItem check) {
                check.setSelected(command.isSelected());
                ChangeListener<Boolean> selected = (observable, oldValue, value) -> check.setSelected(value);
                command.selectedProperty().addListener(selected);
                removers.add(() -> command.selectedProperty().removeListener(selected));
            }
            MenuItem owned = item;
            removers.add(() -> {
                owned.textProperty().unbind(); owned.disableProperty().unbind(); owned.visibleProperty().unbind();
                owned.acceleratorProperty().unbind(); owned.setOnAction(null);
            });
        }
        item.setId(source.getId());
        item.setGraphic(CommandIcons.create(source.getId(), 18));
        Tooltip tooltip = new Tooltip(); tooltip.textProperty().bind(source.textProperty());
        Tooltip.install(item.getGraphic(), tooltip);
        MenuItem owned = item;
        removers.add(() -> { Tooltip.uninstall(owned.getGraphic(), tooltip); tooltip.textProperty().unbind(); });
        return item;
    }
    private void clear() {
        target.getMenus().removeAll(contributed); contributed.clear();
        removers.forEach(Runnable::run); removers.clear();
    }
    @Override public void close() {
        EditorPane.checkThread();
        if (!closed) { closed = true; clear(); }
    }
}
