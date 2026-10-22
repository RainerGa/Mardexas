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

    private static final class MenuBuilder {

        private final MarkdownEditor editor;
        private final EditorWindow window;

        private MenuBuilder(MarkdownEditor editor, EditorWindow window) {
            this.editor = editor;
            this.window = window;
        }

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

        private Menu buildViewMenu() {
            Menu menu = new Menu(I18n.text("menu.view"));
            javafx.scene.control.CheckMenuItem item =
                    new javafx.scene.control.CheckMenuItem(I18n.text("menu.documentStructure"));
            item.setSelected(I18n.documentStructureEnabled());
            item.setOnAction(event -> window.setStructureViewVisible(item.isSelected()));
            menu.getItems().add(item);

            javafx.scene.control.CheckMenuItem lineNumbers =
                    new javafx.scene.control.CheckMenuItem(I18n.text("menu.lineNumbers"));
            lineNumbers.setSelected(I18n.lineNumbersEnabled());
            lineNumbers.setOnAction(event -> window.setLineNumbersVisible(lineNumbers.isSelected()));
            menu.getItems().add(lineNumbers);
            return menu;
        }

        private Menu buildFileMenu() {
            Menu menu = new Menu(I18n.text("menu.file"));
            javafx.scene.control.MenuItem saveItem = menuItem("menu.save", window::save);
            javafx.scene.control.MenuItem saveAsItem = menuItem("menu.saveAs", window::showSaveDialog);
            // Schreibgeschützt: Speichern ist gesperrt wie in LO (AddFeatures #5)
            saveItem.disableProperty().bind(window.readOnlyProperty());
            saveAsItem.disableProperty().bind(window.readOnlyProperty());
            menu.getItems().addAll(
                    menuItem("menu.new", window::newDocument),
                    menuItem("menu.open", window::showOpenDialog),
                    menuItem("menu.openReadOnly", window::showOpenDialogReadOnly),
                    separator(),
                    buildRecentFilesMenu(),
                    separator(),
                    saveItem,
                    saveAsItem,
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
            Menu menu = new Menu(I18n.text("menu.recentFiles"));
            List<String> recentFiles = I18n.getRecentFiles();
            if (recentFiles.isEmpty()) {
                MenuItem emptyItem = new MenuItem(I18n.text("menu.recentFiles.empty"));
                emptyItem.setDisable(true);
                menu.getItems().add(emptyItem);
            } else {
                for (int i = 0; i < recentFiles.size(); i++) {
                    String filePath = recentFiles.get(i);
                    String label = (i + 1) + "  " + new java.io.File(filePath).getName();
                    MenuItem item = new MenuItem(label);
                    // MenuItem doesn't support tooltips directly in JavaFX
                    int index = i;
                    item.setOnAction(event -> {
                        java.io.File file = new java.io.File(recentFiles.get(index));
                        if (file.exists()) {
                            window.openFile(file);
                        } else {
                            // Datei existiert nicht mehr -> aus Liste entfernen
                            I18n.removeRecentFile(filePath);
                            // Menü neu aufbauen (einfacher Ansatz: Fenster neu laden oder Menü aktualisieren)
                            // Hier nur Fehlermeldung anzeigen
                            showMessage(MessageFormat.format(I18n.text("error.recentFileNotFound"), filePath));
                        }
                    });
                    menu.getItems().add(item);
                }
                menu.getItems().add(separator());
                MenuItem clearItem = new MenuItem(I18n.text("menu.recentFiles.clear"));
                clearItem.setOnAction(event -> {
                    // Alle kürzlich geöffneten Dateien löschen
                    for (String path : new ArrayList<>(I18n.getRecentFiles())) {
                        I18n.removeRecentFile(path);
                    }
                });
                menu.getItems().add(clearItem);
            }
            return menu;
        }

        private Menu buildEditMenu() {
            Menu menu = new Menu(I18n.text("menu.edit"));
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
                    menuItem("menu.undo", editor.getControl().getActionFactory()::undo),
                    menuItem("menu.redo", editor.getControl().getActionFactory()::redo),
                    separator(),
                    menuItem("menu.cut", editor.getControl().getActionFactory()::cut),
                    menuItem("menu.copy", editor.getControl().getActionFactory()::copy),
                    menuItem("menu.paste", editor.getControl().getActionFactory()::paste),
                    separator(),
                    menuItem("menu.selectAll", editor.getControl().getActionFactory()::selectAll),
                    separator(),
                    findItem,
                    findNextItem);
            return menu;
        }

        private Menu buildInsertMenu() {
            Menu menu = new Menu(I18n.text("menu.insert"));
            menu.getItems().addAll(
                    menuItem("menu.link", this::showLinkDialog),
                    separator(),
                    menuItem("menu.horizontalRule", editor::insertHorizontalRule),
                    menuItem("menu.image", this::showImageDialog),
                    menuItem("menu.codeBlock", this::showCodeBlockDialog));
            return menu;
        }

        private Menu buildFormatMenu() {
            Menu menu = new Menu(I18n.text("menu.format"));
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
            Menu menu = new Menu(I18n.text("menu.lineWrap"));
            ToggleGroup group = new ToggleGroup();
            for (LineWrapMode mode : LineWrapMode.values()) {
                javafx.scene.control.RadioMenuItem item =
                        new javafx.scene.control.RadioMenuItem(mode.getLabel());
                item.setToggleGroup(group);
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
        Menu menu = new Menu(I18n.text("menu.table"));
        menu.getItems().addAll(
                menuItem("menu.insertTable", this::showTableDialog));
        return menu;
    }

    private Menu buildExtrasMenu() {
        Menu menu = new Menu(I18n.text("menu.extras"));
        // Kein Einstellungs-Dialog: Sprache, Dokumentstruktur und Zeilennummern
        // werden direkt ueber die Menueleiste bzw. die Statusleiste gesteuert.
        menu.getItems().addAll(
                menuItem("menu.statistics", this::showStatisticsDialog));
        return menu;
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

    private void showFindReplaceDialog() {
        if (window == null) {
            return;
        }
        window.showFindReplaceDialog();
    }

    private void findNextWithoutDialog() {
        if (window == null) {
            return;
        }
        window.findNextWithoutDialog();
    }

    private Menu buildWindowMenu() {
            Menu menu = new Menu(I18n.text("menu.window"));
            menu.getItems().addAll(
                    menuItem("menu.minimize", () -> window.setIconified(true)),
                    menuItem("menu.maximize", () -> window.setMaximized(!window.isMaximized())),
                    menuItem("menu.closeAll", () -> showMessage(I18n.text("window.closeAll"))));
            return menu;
        }

        private Menu buildHelpMenu() {
            Menu menu = new Menu(I18n.text("menu.help"));
            menu.getItems().addAll(
                    menuItem("menu.showHelp", () -> showTextDialog(
                            I18n.text("help.title"), I18n.text("help.header"),
                            I18n.text("help.text") + "\n\n"
                                    + I18n.text("help.languages"))),
                    menuItem("menu.about", () -> showTextDialog(
                            I18n.text("about.title"), I18n.text("about.header"),
                            I18n.text("about.text"))),
                    menuItem("menu.licensing", () -> showTextDialog(
                            I18n.text("license.title"), I18n.text("license.header"),
                            I18n.text("license.text"))),
                    buildLanguageMenu());
            return menu;
        }

        private Menu buildLanguageMenu() {
            Menu menu = new Menu(I18n.text("menu.language"));
            ToggleGroup group = new ToggleGroup();
            for (String language : I18n.supportedLanguages()) {
                menu.getItems().add(languageItem(language, "language." + language, group));
            }
            return menu;
        }

        private RadioMenuItem languageItem(String language, String labelKey, ToggleGroup group) {
            RadioMenuItem item = new RadioMenuItem(I18n.text(labelKey));
            item.setToggleGroup(group);
            item.setSelected(language.equals(I18n.currentLanguageCode()));
            item.setOnAction(event -> {
                if (item.isSelected() && !language.equals(I18n.currentLanguageCode())) {
                    I18n.setLanguagePreference(language);
                    showMessage(I18n.text("language.restart.message"));
                }
            });
            return item;
        }


        private static final String ABOUT_TEXT = """
                RichMarkDownEditor 1.0.0

                Ein WYSIWYG-Markdown-Editor für JavaFX.
                Lizenz der Anwendung: GNU General Public License, Version 3
                (GPL-3.0). Lizenztext: https://www.gnu.org/licenses/gpl-3.0.html

                Verwendete Laufzeitbibliotheken:
                • Gluon Rich Text Area 1.2.6-patched – Rich-Text-Editor-Steuerelement.
                  Projektinterner Patch-Build auf Basis des Gluon-Quellstands 1.2.5.
                • OpenJFX 25.0.4 – JavaFX Controls und Graphics.

                Testbibliotheken (nur für Entwicklung und Tests):
                • JUnit Jupiter 5.11.3
                • TestFX 4.0.19-gluon und OpenJFX Monocle 21.0.2

                Unser herzlicher Dank gilt dem Gluon-Team und allen Beitragenden
                hinter Rich Text Area sowie den OpenJFX-Entwicklerinnen und
                -Entwicklern. Ebenso danken wir den Teams und Beitragenden von
                JUnit und TestFX. Ihre freie Software und ihre Arbeit machen dieses
                Projekt möglich. Bitte beachten Sie die jeweiligen Lizenzhinweise
                unter „Hilfe → Lizenzierung“.
                """;

        private static final String LICENSE_TEXT = """
                LIZENZ DER ANWENDUNG

                RichMarkDownEditor ist unter der GNU General Public License,
                Version 3 (GPL-3.0), lizenziert. Diese Lizenz gilt für den
                Anwendungscode dieses Projekts.
                Vollständiger Lizenztext:
                https://www.gnu.org/licenses/gpl-3.0.html

                LIZENZEN DER VERWENDETEN KOMPONENTEN

                Laufzeitkomponenten:

                Gluon Rich Text Area 1.2.6-patched
                Lizenz laut Projektquelle: GNU General Public License, Version 3
                (GPL-3.0). Der lokale Build basiert auf Gluons Quellstand 1.2.5 und
                enthält projektseitige Änderungen. Upstream-Projekt:
                https://github.com/gluonhq/rich-text-area
                Lizenztext: https://www.gnu.org/licenses/gpl-3.0.html

                OpenJFX 25.0.4 (Controls und Graphics)
                GPL-2.0-only mit Classpath Exception.
                https://openjdk.org/legal/gplv2+ce.html
                OpenJFX-Projekt: https://openjfx.io/

                Entwicklungs- und Testabhängigkeiten (nicht als Laufzeitfunktionen
                des Editors gedacht):

                JUnit Jupiter 5.11.3 – Eclipse Public License 2.0 (EPL-2.0)
                https://www.eclipse.org/legal/epl-2.0/

                TestFX 4.0.19-gluon – European Union Public Licence 1.1 (EUPL-1.1),
                gemäß den Metadaten der verwendeten Artefakte.
                http://ec.europa.eu/idabc/eupl.html

                OpenJFX Monocle 21.0.2 – GPL-2.0-only mit Classpath Exception,
                Testabhängigkeit.
                https://openjdk.org/legal/gplv2+ce.html

                WICHTIG FÜR WEITERGABE

                Bei einer Weitergabe des Editors müssen die GPL-3.0-Bedingungen
                sowie die Bedingungen aller mitgelieferten Komponenten eingehalten
                werden. Dazu gehören insbesondere die Bereitstellung des vollständigen
                korrespondierenden Quellcodes einschließlich Änderungen sowie Kopien
                der Lizenztexte und Copyright-Hinweise.

                Diese Anzeige ist ein Hinweis, keine Rechtsberatung und keine
                Garantie, dass ein beliebiges Distributionsmodell alle Bedingungen
                erfüllt. Vor einer Veröffentlichung oder Weitergabe bitte die
                vollständigen Lizenztexte, die konkreten Artefakte und die
                Distributionsform prüfen. Die in den Links genannten Lizenztexte
                sind maßgeblich.
                """;

        private void showLinkDialog() {
            LinkDialog dialog = new LinkDialog();
            dialog.showAndWait().ifPresent(editor::applyLink);
        }

        private void showImageDialog() {
            ImageDialog dialog = new ImageDialog();
            dialog.showAndWait().ifPresent(editor::insertImage);
        }

    private void showCodeBlockDialog() {
        CodeBlockDialog dialog = new CodeBlockDialog();
        dialog.showAndWait().ifPresent(editor::insertCodeBlock);
    }

    private void showTableDialog() {
        TableDialog dialog = new TableDialog();
        dialog.showAndWait().ifPresent(size -> editor.insertTable(size.rows(), size.columns()));
    }

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

        private String joinWrappedLines(String text) {
            return text.replaceAll("(?<!\\n)\\n(?!\\n)", " ");
        }

        private void showMessage(String message) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle(I18n.text("app.title"));
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
            });
        }

        private MenuItem menuItem(String key, Runnable action) {
            MenuItem item = new MenuItem(I18n.text(key));
            item.setOnAction(event -> action.run());
            return item;
        }

        private SeparatorMenuItem separator() {
            return new SeparatorMenuItem();
        }
    }
}
