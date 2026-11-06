/*
 * Copyright (C) 2026 Rainer Garbotz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.richmarkdown.ui;

import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.ui.dialogs.CodeBlockDialog;
import de.richmarkdown.ui.dialogs.CreditsDialog;
import de.richmarkdown.ui.dialogs.DialogButtons;
import de.richmarkdown.ui.dialogs.ImageDialog;
import de.richmarkdown.ui.dialogs.LinkDialog;
import de.richmarkdown.ui.dialogs.TableDialog;
import com.gluonhq.richtextarea.model.ParagraphDecoration;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Baut die zur jeweiligen Anwendungssituation passende Menüleiste.
 *
 * <p>Im vollständigen Editorfenster bindet die Menüleiste Datei-, Ansichts-,
 * Formatierungs-, Tabellen-, Fenster- und Hilfeaktionen ein. Für ein
 * eigenständiges Editor-Control ohne {@link EditorWindow} werden nur die
 * Aktionen bereitgestellt, die keinen Fensterkontext benötigen.
 *
 * <p>Menütexte und Anzeigeeinstellungen stammen aus {@link I18n}; Aktions-
 * Implementierungen werden an die Fassadenklasse {@link MarkdownEditor}
 * beziehungsweise das Fenster delegiert.
 */
public final class MenuBarFactory {

    private MenuBarFactory() {
    }

    /**
     * Erstellt eine reduzierte Menüleiste für ein eigenständiges Editor-Control.
     *
     * @param editor Editor-Fassade, deren Aktionen die Menüeinträge ausführen
     * @return JavaFX-Menüleiste mit Bearbeiten- und Einfügen-Menüs
     */
    public static MenuBar create(MarkdownEditor editor) {
        return new MenuBuilder(editor, null).build();
    }

    /**
     * Erstellt die vollständige Menüleiste des Editorfensters.
     *
     * @param window Fenster, das Datei-, Anzeige- und Fensteraktionen bereitstellt
     * @return JavaFX-Menüleiste für das vollständige Editorfenster
     */
    public static MenuBar create(EditorWindow window) {
        return new MenuBuilder(window.getEditor(), window).build();
    }

    /**
     * Interner Builder, der die Menüleiste aus den gegebenen Komponenten
     * zusammenbaut. Je nachdem, ob ein {@link EditorWindow} vorhanden ist,
     * entsteht eine reduzierte (nur Bearbeiten/Einfügen) oder eine vollständige
     * Menüleiste (alle Menüs inkl. Datei, Ansicht, Tabelle, Extras, Fenster, Hilfe).
     */
    private static final class MenuBuilder {

        private final MarkdownEditor editor;
        private final EditorWindow window;

        private MenuBuilder(MarkdownEditor editor, EditorWindow window) {
            this.editor = editor;
            this.window = window;
        }

        /**
         * Baut die Menüleiste und fügt alle Menüs hinzu.
         *
         * @return fertig konfigurierte JavaFX-Menüleiste
         */
        MenuBar build() {
            MenuBar menuBar = new MenuBar();
            menuBar.getStyleClass().add("rme-menu-bar");
            if (window != null) {
                menuBar.getMenus().add(buildFileMenu());
                menuBar.getMenus().add(buildEditMenu());
                menuBar.getMenus().add(buildViewMenu());
                menuBar.getMenus().add(buildInsertMenu());
                menuBar.getMenus().add(buildFormatMenu());
                menuBar.getMenus().add(buildTableMenu());
                menuBar.getMenus().add(buildExtrasMenu());
                menuBar.getMenus().add(buildWindowMenu());
                menuBar.getMenus().add(buildHelpMenu());
            } else {
                menuBar.getMenus().addAll(buildEditMenu(), buildInsertMenu());
            }
            return menuBar;
        }

        /**
         * Baut das Ansicht-Menü mit Dokumentstruktur- und Zeilennummern-Optionen.
         *
         * @return Ansicht-Menü
         */
        private Menu menu(String key) {
            Menu result = new Menu(I18n.text(key)); result.setId(key); return result;
        }

        private Menu buildViewMenu() {
            Menu menu = menu("menu.view");
            javafx.scene.control.CheckMenuItem item =
                    new javafx.scene.control.CheckMenuItem(I18n.text("menu.documentStructure"));
            item.setId("menu.documentStructure");
            item.setSelected(I18n.documentStructureEnabled());
            item.setOnAction(event -> window.setStructureViewVisible(item.isSelected()));
            menu.getItems().add(item);

            javafx.scene.control.CheckMenuItem lineNumbers =
                    new javafx.scene.control.CheckMenuItem(I18n.text("menu.lineNumbers"));
            lineNumbers.setId("menu.lineNumbers");
            lineNumbers.setSelected(I18n.lineNumbersEnabled());
            lineNumbers.setOnAction(event -> window.setLineNumbersVisible(lineNumbers.isSelected()));
            menu.getItems().add(lineNumbers);

            return menu;
        }

        /**
         * Baut das Datei-Menü mit Neu, Öffnen, Speichern, Drucken, Beenden usw.
         *
         * @return Datei-Menü
         */
        private Menu buildFileMenu() {
            Menu menu = menu("menu.file");
            javafx.scene.control.MenuItem saveItem = menuItem("menu.save", window::save);
            javafx.scene.control.MenuItem saveAsItem = menuItem("menu.saveAs", window::showSaveDialog);
            // Schreibgeschützt: Speichern ist gesperrt wie in LO (AddFeatures #5)
            saveItem.disableProperty().bind(window.readOnlyProperty());
            saveAsItem.disableProperty().bind(window.readOnlyProperty());
            menu.getItems().addAll(
                    menuItem("menu.new", window::newDocument),
                    menuItem("menu.newFromTemplate", window::newFromTemplate),
                    menuItem("menu.open", window::showOpenDialog),
                    menuItem("menu.openReadOnly", window::showOpenDialogReadOnly),
                    separator(),
                    buildRecentFilesMenu(),
                    separator(),
                    saveItem,
                    saveAsItem,
                    menuItem("menu.file.compareWith", window::showDiffDialog),
                    menuItem("menu.print", window::print),
                    separator(),
                    menuItem("menu.quit", window::exit));
            return menu;
        }

        /**
         * Erstellt das Untermenü „Zuletzt geöffnet“ (Datei → Zuletzt geöffnet,
         * Vorbild LO Datei → Zuletzt geöffnet, AddFeatures #7).
         */
        private Menu buildRecentFilesMenu() {
            Menu menu = menu("menu.recentFiles");
            var current = new java.util.concurrent.atomic.AtomicReference<List<String>>(List.of());
            List<MenuItem> slots = new ArrayList<>();
            MenuItem empty = new MenuItem(I18n.text("menu.recentFiles.empty"));
            empty.setId("menu.recentFiles.empty"); empty.setDisable(true);
            menu.getItems().add(empty);
            // Fixed semantic slots keep registry entries, shortcut assignments and ribbon
            // controls valid when files are opened, reordered, removed or cleared.
            for (int index = 0; index < 10; index++) {
                int slot = index;
                MenuItem item = new MenuItem(Integer.toString(index + 1));
                item.setId("menu.recentFiles." + (index + 1));
                item.setOnAction(event -> {
                    List<String> files = current.get();
                    if (slot >= files.size()) return;
                    String path = files.get(slot); java.io.File file = new java.io.File(path);
                    if (file.exists()) window.openFile(file);
                    else {
                        I18n.removeRecentFile(path);
                        showMessage(MessageFormat.format(I18n.text("error.recentFileNotFound"), path));
                    }
                });
                slots.add(item); menu.getItems().add(item);
            }
            menu.getItems().add(separator());
            MenuItem clear = new MenuItem(I18n.text("menu.recentFiles.clear"));
            clear.setId("menu.recentFiles.clear");
            clear.setOnAction(event -> {
                for (String path : List.copyOf(I18n.getRecentFiles())) I18n.removeRecentFile(path);
            });
            menu.getItems().add(clear);
            Runnable refresh = () -> {
                List<String> files;
                try { files = List.copyOf(I18n.getRecentFiles()); }
                catch (RuntimeException failure) {
                    // A damaged profile must not abort notifications to other windows
                    // or prevent the UI-mode service from reporting the profile error.
                    java.util.logging.Logger.getLogger(MenuBarFactory.class.getName()).log(
                            java.util.logging.Level.WARNING, "Could not refresh recent files", failure);
                    files = List.of();
                }
                current.set(files);
                empty.setVisible(files.isEmpty()); clear.setDisable(files.isEmpty());
                for (int index = 0; index < slots.size(); index++) {
                    MenuItem item = slots.get(index); boolean available = index < files.size();
                    String path = available ? files.get(index) : "";
                    item.getProperties().put("command.description", available ? path : I18n.text("menu.recentFiles.empty"));
                    item.setText((index + 1) + "  " + (available ? new java.io.File(path).getName() : "—"));
                    item.setVisible(available); item.setDisable(!available);
                }
            };
            refresh.run();
            if (window != null) window.addFeatureDisposer(I18n.onRecentFilesChanged(() -> {
                if (Platform.isFxApplicationThread()) refresh.run(); else Platform.runLater(refresh);
            }));
            return menu;
        }

        /**
         * Baut das Bearbeiten-Menü mit Rückgängig, Wiederherstellen, Ausschneiden,
         * Kopieren, Einfügen, Alles auswählen, Suchen & Ersetzen.
         *
         * @return Bearbeiten-Menü
         */
        private Menu buildEditMenu() {
            Menu menu = menu("menu.edit");
            // Strg+F oeffnet den Suchen-&-Ersetzen-Dialog (wie in den meisten
            // Editoren). F3 wiederholt die letzte Suche ohne Dialog.
            javafx.scene.control.MenuItem findItem = menuItem("menu.findReplace",
                    this::showFindReplaceDialog);
            findItem.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                    javafx.scene.input.KeyCode.F,
                    javafx.scene.input.KeyCombination.CONTROL_DOWN));
            javafx.scene.control.MenuItem findNextItem = menuItem("menu.findNext",
                    this::findNextWithoutDialog);
            findNextItem.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                    javafx.scene.input.KeyCode.F3));
            menu.getItems().addAll(
                    actionItem("menu.undo", editor.getControl().getActionFactory().undo()),
                    actionItem("menu.redo", editor.getControl().getActionFactory().redo()),
                    separator(),
                    actionItem("menu.cut", editor.getControl().getActionFactory().cut()),
                    actionItem("menu.copy", editor.getControl().getActionFactory().copy()),
                    actionItem("menu.paste", editor.getControl().getActionFactory().paste()),
                    separator(),
                    actionItem("menu.selectAll", editor.getControl().getActionFactory().selectAll()),
                    separator(),
                    findItem,
                    findNextItem);
            return menu;
        }

        /**
         * Baut das Einfügen-Menü mit Link, Horizontale Linie, Bild, Code-Block.
         *
         * @return Einfügen-Menü
         */
        private Menu buildInsertMenu() {
            Menu menu = menu("menu.insert");
            menu.getItems().addAll(
                    menuItem("menu.link", this::showLinkDialog),
                    separator(),
                    menuItem("menu.horizontalRule", editor::insertHorizontalRule),
                    menuItem("menu.image", this::showImageDialog),
                    menuItem("menu.codeBlock", this::showCodeBlockDialog));
            return menu;
        }

        /**
         * Baut das Format-Menü mit Textstile, Überschriften, Listen, Einzug, Zeilenumbruch.
         *
         * @return Format-Menü
         */
        private Menu buildFormatMenu() {
            Menu menu = menu("menu.format");
            menu.getItems().addAll(
                    menuItem("menu.bold", editor::toggleBold),
                    menuItem("menu.italic", editor::toggleItalic),
                    menuItem("menu.underline", editor::toggleUnderline),
                    menuItem("menu.strikethrough", editor::toggleStrikethrough),
                    menuItem("menu.inlineCode", editor::toggleInlineCode),
                    separator(),
                    menuItem("menu.horizontalRule", editor::insertHorizontalRule),
                    separator(),
                    menuItem("menu.heading1", () -> editor.applyHeading(1)),
                    menuItem("menu.heading2", () -> editor.applyHeading(2)),
                    menuItem("menu.heading3", () -> editor.applyHeading(3)),
                    menuItem("menu.heading4", () -> editor.applyHeading(4)),
                    menuItem("menu.heading5", () -> editor.applyHeading(5)),
                    menuItem("menu.heading6", () -> editor.applyHeading(6)),
                    menuItem("menu.body", editor::applyParagraphStyle),
                    menuItem("menu.quote", editor::applyQuote),
                    separator(),
                    menuItem("menu.bulletList", editor::toggleBulletList),
                    menuItem("menu.numberedList", editor::toggleNumberedList),
                    menuItem("menu.listStartNumber", () -> showListStartNumberDialog()),
                    separator(),
                    menuItem("menu.indent", editor::indent),
                    menuItem("menu.outdent", editor::outdent),
                    separator(),
                    buildLineWrapMenu());
            return menu;
        }

        /**
         * Unterformatmenü „Zeilenumbruch“ mit Radio-Items „Automatisch“ und
         * „Durchgehend“ (Vorbild LO Format → Zeilenumbruch, AddFeatures #6).
         */
        private Menu buildLineWrapMenu() {
            Menu menu = menu("menu.lineWrap");
            ToggleGroup group = new ToggleGroup();
            for (LineWrapMode mode : LineWrapMode.values()) {
                javafx.scene.control.RadioMenuItem item =
                        new javafx.scene.control.RadioMenuItem(mode.getLabel());
                item.setToggleGroup(group);
                item.setId("menu.lineWrap." + mode.name());
                item.setSelected(mode == window.getLineWrapMode());
                item.setOnAction(event -> {
                    if (item.isSelected()) {
                        window.setLineWrapMode(mode);
                    }
                });
                menu.getItems().add(item);
            }
            return menu;
        }

    private Menu buildTableMenu() {
        Menu menu = menu("menu.table");
        menu.getItems().addAll(
                menuItem("menu.insertTable", this::showTableDialog));
        return menu;
    }

    /**
     * Baut das Extras-Menü mit Statistik-Dialog.
     *
     * @return Extras-Menü
     */
    private Menu buildExtrasMenu() {
        Menu menu = menu("menu.extras");
        menu.getItems().addAll(
                menuItem("menu.statistics", this::showStatisticsDialog),
                menuItem("menu.tools.checkIssues", this::showIssueCheckDialog));
        return menu;
    }

    /**
     * Öffnet den Formatfehler-Prüf-Dialog (Extras → Dokument auf
     * Formatfehler prüfen, AddFeatures #16).
     */
    private void showIssueCheckDialog() {
        if (window == null) {
            return;
        }
        window.showIssueCheckDialog();
    }

    /**
     * Öffnet den Statistik-Dialog (Extras → Statistik, Vorbild LO,
     * AddFeatures #3).
     */
    private void showStatisticsDialog() {
        if (window == null) {
            return;
        }
        window.showStatisticsDialog();
    }

    /**
     * Öffnet den Suchen-&-Ersetzen-Dialog über das Fenster.
     * Wird nur aufgerufen, wenn ein Fensterkontext vorhanden ist.
     */
    private void showFindReplaceDialog() {
        if (window == null) {
            return;
        }
        window.showFindReplaceDialog();
    }

    /**
     * Wiederholt die letzte Suche ohne Dialog (F3).
     * Wird nur aufgerufen, wenn ein Fensterkontext vorhanden ist.
     */
    private void findNextWithoutDialog() {
        if (window == null) {
            return;
        }
        window.findNextWithoutDialog();
    }

        private Menu buildWindowMenu() {
            Menu menu = menu("menu.window");
            menu.getItems().addAll(
                    menuItem("menu.minimize", () -> window.setIconified(true)),
                    menuItem("menu.maximize", () -> window.setMaximized(!window.isMaximized())),
                    menuItem("menu.closeAll", () -> showMessage(I18n.text("window.closeAll"))));
            return menu;
        }

        /**
         * Baut das Hilfe-Menü mit Hilfe, Über, Lizenzierung und Sprachauswahl.
         *
         * @return Hilfe-Menü
         */
        private Menu buildHelpMenu() {
            Menu menu = menu("menu.help");
            menu.getItems().addAll(
                    menuItem("menu.showHelp", () -> showTextDialog(
                            I18n.text("help.title"), I18n.text("help.header"),
                            I18n.text("help.text") + "\n\n"
                                    + I18n.text("help.languages"))),
                    menuItem("menu.about", () -> showTextDialog(
                            I18n.text("about.title"), I18n.text("about.header"),
                            I18n.text("about.text"))),
                    menuItem("menu.licensing", () -> new CreditsDialog(window.getDialogOwner(), false).show()),
                    menuItem("menu.thanks", () -> new CreditsDialog(window.getDialogOwner(), true).show()),
                    buildLanguageMenu());
            return menu;
        }

        private Menu buildLanguageMenu() {
            Menu menu = menu("menu.language");
            ToggleGroup group = new ToggleGroup();
            for (String language : I18n.supportedLanguages()) {
                menu.getItems().add(languageItem(language, "language." + language, group));
            }
            return menu;
        }

        /**
         * Erstellt ein Sprach-Radio-Menü-Item für die angegebene Sprache.
         *
         * @param language Sprachcode (z. B. "de", "en")
         * @param labelKey I18n-Schlüssel für den Anzeigetext
         * @param group    ToggleGroup für gegenseitiges Ausschließen
         * @return konfiguriertes RadioMenuItem
         */
        private RadioMenuItem languageItem(String language, String labelKey, ToggleGroup group) {
            RadioMenuItem item = new RadioMenuItem(I18n.text(labelKey));
            item.setToggleGroup(group);
            item.setId("menu.language." + language);
            item.setSelected(language.equals(I18n.currentLanguageCode()));
            item.setOnAction(event -> {
                if (item.isSelected() && !language.equals(I18n.currentLanguageCode())) {
                    I18n.setLanguagePreference(language);
                    showMessage(I18n.text("language.restart.message"));
                }
            });
            return item;
        }


        private void showLinkDialog() {
            LinkDialog dialog = new LinkDialog();
            dialog.showAndWait().ifPresent(editor::applyLink);
        }

        private void showImageDialog() {
            ImageDialog dialog = new ImageDialog();
            dialog.showAndWait().ifPresent(editor::insertImage);
        }

        /**
         * Öffnet den Code-Block-Dialog und fügt den eingegebenen Code-Block ein.
         */
        private void showCodeBlockDialog() {
            CodeBlockDialog dialog = new CodeBlockDialog();
            dialog.showAndWait().ifPresent(editor::insertCodeBlock);
        }

        /**
         * Öffnet den Tabellen-Dialog und fügt eine Tabelle der angegebenen Größe ein.
         */
        private void showTableDialog() {
            TableDialog dialog = new TableDialog();
            dialog.showAndWait().ifPresent(size -> editor.insertTable(size.rows(), size.columns()));
        }

        /**
         * Öffnet den Dialog zur Festlegung der Startnummer einer nummerierten Liste.
         * Wird nur angezeigt, wenn sich der Cursor in einer nummerierten Liste befindet.
         */
        private void showListStartNumberDialog() {
        ParagraphDecoration current = editor.getControl().getDecorationAtParagraph();
        if (current == null || current.getGraphicType() != ParagraphDecoration.GraphicType.NUMBERED_LIST) {
            return; // Keine nummerierte Liste an der aktuellen Position
        }
        int currentStart = current.getStartNumber();
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(String.valueOf(currentStart));
        if (window != null) {
            dialog.initOwner(window);
        }
        dialog.setTitle(I18n.text("dialog.listStartNumber.title"));
        dialog.setHeaderText(I18n.text("dialog.listStartNumber.header"));
        dialog.setContentText(I18n.text("dialog.listStartNumber.content"));
        dialog.showAndWait().ifPresent(input -> {
            try {
                int startNumber = Integer.parseInt(input.trim());
                if (startNumber >= 1) {
                    editor.setListStartNumber(startNumber);
                }
            } catch (NumberFormatException ignored) {
                // Ungültige Eingabe ignorieren
            }
        });
    }

        /**
         * Zeigt einen reinen Text-Dialog mit Titel, Kopfzeile und Inhalt an.
         * Der Dialog läuft auf dem FX-Thread und ist modal.
         *
         * @param title   Fenstertitel
         * @param header  Kopfzeile des Dialogs
         * @param content anzuzeigender Textinhalt
         */
        private void showTextDialog(String title, String header, String content) {
            Platform.runLater(() -> {
                Dialog<ButtonType> dialog = new Dialog<>();
                if (window != null) {
                    dialog.initOwner(window);
                }
                dialog.setResizable(true);
                dialog.setTitle(title);
                dialog.getDialogPane().setHeaderText(header);
                dialog.getDialogPane().setPrefWidth(900);

                TextArea textArea = new TextArea(content);
                textArea.setEditable(false);
                textArea.setWrapText(true);
                textArea.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                textArea.setPrefRowCount(25);
                textArea.setText(joinWrappedLines(content));
                dialog.getDialogPane().setContent(textArea);
                dialog.getDialogPane().getButtonTypes().add(DialogButtons.CLOSE);
                dialog.showAndWait();
            });
        }

        /**
         * Fügt einzelne Leerzeichen zwischen Zeilenumbrüche ein, damit der Text
         * in einer TextArea besser umgebrochen wird.
         *
         * @param text zu verarbeitender Text
         * @return Text mit eingefügten Leerzeichen bei einzelnen Zeilenumbrüchen
         */
        private String joinWrappedLines(String text) {
            return text.replaceAll("(?<!\\n)\\n(?!\\n)", " ");
        }

        /**
         * Zeigt eine modale Informationsmeldung mit dem angegebenen Text an.
         *
         * @param message anzuzeigende Meldung
         */
        private void showMessage(String message) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle(I18n.text("app.title"));
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
            });
        }

        /**
         * Erstellt ein Menü-Item mit dem angegebenen I18n-Schlüssel und einer Aktion.
         *
         * @param key    I18n-Schlüssel für den Anzeigetext
         * @param action auszuführende Aktion beim Klick
         * @return konfiguriertes MenuItem
         */
        private void defaultShortcut(MenuItem item) {
            String keys = switch (item.getId()) {
                case "menu.undo" -> "Shortcut+Z"; case "menu.redo" -> "Shortcut+Shift+Z";
                case "menu.cut" -> "Shortcut+X"; case "menu.copy" -> "Shortcut+C";
                case "menu.paste" -> "Shortcut+V"; case "menu.selectAll" -> "Shortcut+A";
                case "menu.new" -> "Shortcut+N"; case "menu.open" -> "Shortcut+O";
                case "menu.save" -> "Shortcut+S"; case "menu.saveAs" -> "Shortcut+Shift+S";
                case "menu.print" -> "Shortcut+P"; case "menu.bold" -> "Shortcut+B";
                case "menu.italic" -> "Shortcut+I"; case "menu.underline" -> "Shortcut+U";
                default -> null;
            };
            if (keys != null) item.setAccelerator(javafx.scene.input.KeyCombination.keyCombination(keys));
        }
        private MenuItem actionItem(String key, com.gluonhq.richtextarea.action.Action action) {
            MenuItem item = new MenuItem(I18n.text(key)); item.setId(key);
            item.setOnAction(action::execute);
            // The library action captures the initial buffer's length property;
            // opening a document replaces that buffer. The control forwards the current one.
            item.disableProperty().bind("menu.selectAll".equals(key)
                    ? editor.getControl().textLengthProperty().lessThanOrEqualTo(0) : action.disabledProperty());
            defaultShortcut(item);
            return item;
        }

        private MenuItem menuItem(String key, Runnable action) {
            MenuItem item = new MenuItem(I18n.text(key));
            item.setId(key);
            item.setOnAction(event -> action.run());
            defaultShortcut(item);
            return item;
        }

        /**
         * Erstellt ein Trennzeichen für Menüs.
         *
         * @return neues SeparatorMenuItem
         */
        private SeparatorMenuItem separator() {
            return new SeparatorMenuItem();
        }
    }
}
