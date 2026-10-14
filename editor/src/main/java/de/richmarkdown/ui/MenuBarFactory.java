package de.richmarkdown.ui;

import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.ui.dialogs.CodeBlockDialog;
import de.richmarkdown.ui.dialogs.DialogButtons;
import de.richmarkdown.ui.dialogs.ImageDialog;
import de.richmarkdown.ui.dialogs.LinkDialog;
import de.richmarkdown.ui.dialogs.TableDialog;
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
            menu.getItems().addAll(
                    menuItem("menu.new", window::newDocument),
                    menuItem("menu.open", window::showOpenDialog),
                    separator(),
                    menuItem("menu.save", window::save),
                    menuItem("menu.saveAs", window::showSaveDialog),
                    menuItem("menu.print", window::print),
                    separator(),
                    menuItem("menu.quit", window::exit));
            return menu;
        }

        private Menu buildEditMenu() {
            Menu menu = new Menu(I18n.text("menu.edit"));
            menu.getItems().addAll(
                    menuItem("menu.undo", editor.getControl().getActionFactory()::undo),
                    menuItem("menu.redo", editor.getControl().getActionFactory()::redo),
                    separator(),
                    menuItem("menu.cut", editor.getControl().getActionFactory()::cut),
                    menuItem("menu.copy", editor.getControl().getActionFactory()::copy),
                    menuItem("menu.paste", editor.getControl().getActionFactory()::paste),
                    separator(),
                    menuItem("menu.selectAll", editor.getControl().getActionFactory()::selectAll));
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
                    menuItem("menu.body", editor::applyParagraphStyle),
                    menuItem("menu.quote", editor::applyQuote),
                    separator(),
                    menuItem("menu.bulletList", editor::toggleBulletList),
                    menuItem("menu.numberedList", editor::toggleNumberedList),
                    separator(),
                    menuItem("menu.indent", editor::indent),
                    menuItem("menu.outdent", editor::outdent));
            return menu;
        }

    private Menu buildTableMenu() {
        Menu menu = new Menu(I18n.text("menu.table"));
        menu.getItems().addAll(
                menuItem("menu.insertTable", this::showTableDialog));
        return menu;
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


        private static final String HELP_TEXT = """
                MARKDOWN-UNTERSTÜTZUNG

                Der Editor orientiert sich an GitHub Flavored Markdown (GFM), das auf
                CommonMark aufbaut. Er implementiert eine unterstützte Teilmenge und
                ist kein vollständiger CommonMark- oder GFM-Konformitätsprüfer.
                GFM-Spezifikation: https://github.github.com/gfm/
                CommonMark-Spezifikation: https://spec.commonmark.org/

                Unterstützt werden unter anderem Absätze und Leerzeilen, Überschriften
                (# bis ######), Fett, Kursiv, Durchgestrichen, Inline-Code, Links,
                Bilder, Aufzählungs- und Nummernlisten, verschachtelte Listen, Zitate,
                eingezäunte Codeblöcke, horizontale Trennlinien und GFM-Tabellen.
                Unterstreichung wird als HTML <u>-Element geschrieben und ist eine
                Erweiterung, kein Bestandteil des GFM-Kernumfangs.

                ZEILEN- UND LEERZEILEN

                Ein einzelner Zeilenumbruch zwischen normalen Textzeilen bleibt im
                Editor sichtbar und wird beim Speichern als einzelner Zeilenumbruch
                ausgegeben. Der Editor behandelt solche Zeilen intern als getrennte
                Absätze; andere Markdown-Renderer können einen einzelnen Umbruch
                gemäß GFM als weichen Umbruch darstellen.

                Eine Leerzeile trennt Markdown-Blöcke. Mehrere aufeinanderfolgende
                leere Editorzeilen werden beim Speichern auf eine Markdown-Leerzeile
                zwischen den Blöcken normalisiert. Noch mehr Leerzeilen erzeugen in
                Markdown üblicherweise keinen größeren Abstand und werden deshalb
                nicht als zusätzliche vertikale Abstände bewahrt. Für größeren
                sichtbaren Abstand gibt es im Markdown-Format keine portable,
                standardisierte Absatzabstands-Notation.

                DATEIEN UND FORMATIERUNG

                Markdown-Dateien werden als UTF-8 gelesen und geschrieben. Beim Lesen
                werden Windows- und klassische Mac-Zeilenenden normalisiert. Der
                Editor speichert unterstützte Formatierungen in Markdown; nicht
                unterstützte Syntax oder Quelltextformatierung kann beim Bearbeiten
                normalisiert werden. Für die bestmögliche Kompatibilität sollten
                Dateien mit den oben aufgeführten Elementen erstellt werden.
                """;

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
