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

import com.gluonhq.richtextarea.RichTextArea;
import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.ui.dialogs.DialogButtons;
import de.richmarkdown.ui.dialogs.PrintDialog;
import de.richmarkdown.ui.dialogs.PrintDialogResult;
import de.richmarkdown.ui.dialogs.TemplateDialog;
import de.richmarkdown.io.MarkdownFileHandler;
import javafx.application.Platform;
import javafx.print.JobSettings;
import javafx.print.PageLayout;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.text.MessageFormat;

/**
 * Hauptfenster des RichMarkDownEditor mit Menüleiste, Formatierungsleiste,
 * Dokumentansichten, Statusleiste sowie Datei- und Druckaktionen.
 *
 * <p>Optionale Default-Shell für Standalone-Betrieb und Integratoren – keine
 * Pflicht: Wer nur den Kern braucht, bettet
 * {@link MarkdownEditor#getControl()} direkt ein und nutzt die Datei-API auf
 * {@link MarkdownEditor} ({@code open}/{@code save}/{@code getCurrentFile}/
 * {@code clear}). Das Fenster verbindet die UI-Komponenten mit
 * {@link MarkdownEditor} und {@link MarkdownFileHandler}. Dateioperationen
 * berücksichtigen ungespeicherte Änderungen über einen Dirty-Check.
 * Sichtbarkeit von Dokumentstruktur und Zeilennummern sowie der Zoomfaktor
 * werden über {@link I18n} dauerhaft gespeichert und bei der
 * Fensterinitialisierung wiederhergestellt.
 *
 * <p>Die geschützten Druck- und Dateiauswahlschritte sind überschreibbar, damit
 * Tests und spezialisierte Fensterimplementierungen Systemdialoge und
 * Druckinfrastruktur kontrolliert ersetzen können.
 */
public class EditorWindow extends Stage {
    private java.util.concurrent.CompletableFuture<Void> pendingFileOperation = java.util.concurrent.CompletableFuture.completedFuture(null);
    private final java.util.concurrent.atomic.AtomicLong fileOperationRevision = new java.util.concurrent.atomic.AtomicLong();
    private boolean featuresDisposed;
    private File openingFile;
    File openingFile() { return openingFile; }

    /** Completes on FX after loading/saving and UI reconciliation; hosts must await it off FX. */
    public java.util.concurrent.CompletableFuture<Void> pendingFileOperation() { return pendingFileOperation; }


    /**
     * Strategie für die Benutzerbestätigung beim Verwerfen ungespeicherter
     * Änderungen.
     */
    public interface ConfirmationHandler {
        /**
         * Fragt, ob die Änderungen verworfen werden dürfen.
         *
         * @param title Dialogtitel
         * @param message erklärender Dialogtext
         * @return {@code true}, wenn die Aktion fortgesetzt werden darf
         */
        boolean confirm(String title, String message);
    }

    private final MarkdownEditor editor;
    private final FormatToolBar toolBar;
    private final StatusBar statusBar;
    private final ViewModeController viewModeController;

    private WorkspaceWindow workspace;
    private BorderPane editorRoot;
    private VBox chrome;
    private final List<Runnable> featureDisposers = new ArrayList<>();
    private record PrintRequest(com.gluonhq.richtextarea.model.Document document,
                                de.richmarkdown.features.printing.PrintOptions options) { }
    private final java.util.concurrent.ConcurrentMap<PrinterJob, PrintRequest> printRequests = new java.util.concurrent.ConcurrentHashMap<>();
    private String lastError;
    private java.util.function.Consumer<String> errorHandler;
    private ConfirmationHandler confirmationHandler;
    private ZoomController zoomController;
    private DocumentStructureView structureView;
    private LineNumberGutter lineNumberGutter;
    private final NavigationHistory navigationHistory = new NavigationHistory();
    private final javafx.beans.property.ReadOnlyBooleanWrapper readOnly =
            new javafx.beans.property.ReadOnlyBooleanWrapper(false);
    private de.richmarkdown.ui.dialogs.FindReplaceDialog findReplaceDialog;
    private String lastFindText = "";
    private de.richmarkdown.findreplace.FindReplaceEngine.FindOptions lastFindOptions =
            new de.richmarkdown.findreplace.FindReplaceEngine.FindOptions();

/**
     * Erstellt das vollständige Editorfenster einschließlich Editor, Menüs,
     * Formatierungsleiste und Statusleiste.
     */
    public EditorWindow() { this(null, true); }

    EditorWindow(WorkspaceWindow host) { this(host, true); }

    /** Creates the shared document shell; embedded shells keep their UI mode local. */
    protected EditorWindow(WorkspaceWindow host, boolean followApplicationMode) {
        this.workspace = host;
        this.editor = new MarkdownEditor();
        this.toolBar = new FormatToolBar(editor);
        toolBar.setNewDocumentHandler(this::newDocument);
        toolBar.setOpenFileHandler(this::showOpenDialog);
        toolBar.setSaveHandler(this::save);
        this.statusBar = new StatusBar(editor);

        // Zoom: der Controller stellt den Viewport (eigenes Region, keine
        // Zentrierung/Begrenzung) und skaliert den Editor darin
        this.zoomController = new ZoomController(editor.getControl());
        this.zoomController.setZoom(I18n.zoomPercent());
        this.viewModeController = new ViewModeController(editor.getControl(), zoomController);
        statusBar.setViewModeChangeHandler(mode -> {
            if (mode == StatusBar.ViewMode.PAGES) {
                viewModeController.showPageView();
            } else {
                viewModeController.showFlowView();
            }
        });
        // Zeilenumbruch-Modus wiederherstellen (AddFeatures #6)
        this.zoomController.setWrapMode(LineWrapMode.valueOf(I18n.lineWrapModeName()));

        // Zurück-/Vorwärts-Navigation (AddFeatures #4): Sprünge aus der
        // Strukturansicht werden gemerkt; Toolbar-Schaltflächen navigieren
        // hin und zurück und folgen dem Stack-Zustand
        toolBar.setBackHandler(() -> navigateBack());
        toolBar.setForwardHandler(() -> navigateForward());
        toolBar.getBackButton().disableProperty()
                .bind(navigationHistory.canGoBackProperty().not());
        toolBar.getForwardButton().disableProperty()
                .bind(navigationHistory.canGoForwardProperty().not());

        MenuBar menuBar = MenuBarFactory.create(this);

        this.zoomController.getViewport().getStyleClass().add("rme-zoom-viewport");
        statusBar.addZoomControls(this.zoomController.createControls());
        setOnHiding(event -> {
            I18n.saveViewPreferences(zoomController.getZoom(), zoomController.getWrapMode().name());
        });

        // Dokumentstruktur (Navigationsbereich) links vom Editor
        this.structureView = new DocumentStructureView(editor);
        // Sprünge aus der Strukturansicht in die Navigations-Historie eintragen
        structureView.setJumpHandler(this::jumpTo);

        // Zeilennummern links vom Editor (zwischen Struktur und Editor)
        this.lineNumberGutter = new LineNumberGutter(editor.getControl(),
                zoomController.zoomProperty());
        setStructureViewVisible(I18n.documentStructureEnabled());
        setLineNumbersVisible(I18n.lineNumbersEnabled());

        HBox editorRow = new HBox(lineNumberGutter, zoomController.getViewport());
        HBox.setHgrow(zoomController.getViewport(), Priority.ALWAYS);

        BorderPane root = new BorderPane();
        this.editorRoot = root;
        root.getStyleClass().add("rme-editor-root");
        VBox topBar = new VBox(menuBar, toolBar);
        this.chrome = topBar;
        topBar.setSpacing(4.0);
        root.setTop(topBar);
        root.setCenter(editorRow);
        root.setBottom(statusBar);
        root.setLeft(structureView);

        updateTitle();
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double sceneWidth = Math.min(1000, Math.max(1, screenBounds.getWidth() - 40));
        double sceneHeight = Math.min(700, Math.max(1, screenBounds.getHeight() - 80));
        Scene scene = new Scene(root, sceneWidth, sceneHeight);
        // Anwendungs-Design im Stil von Word/LibreOffice (Phase 13)
        String stylesheet = loadApplicationStylesheet();
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet);
        }
        setScene(scene);
        FeatureMenus.install(this, menuBar);
        Menu modes = new Menu(FeatureUi.text("Bedienoberfläche", "Interface"));
        modes.setId("menu.commandSurface");
        javafx.scene.control.ToggleGroup modeGroup = new javafx.scene.control.ToggleGroup();
        for (UiMode value : UiMode.values()) {
            javafx.scene.control.RadioMenuItem item = new javafx.scene.control.RadioMenuItem(value == UiMode.CLASSIC
                    ? FeatureUi.text("Klassisches Menü", "Classic menu") : "Ribbon");
            item.setId("ui.mode." + value.name().toLowerCase(java.util.Locale.ROOT));
            item.setToggleGroup(modeGroup); item.setSelected(value == UiMode.CLASSIC);
            item.setOnAction(event -> commandSurface.setMode(value)); modes.getItems().add(item);
        }
        CommandIdentity.menu(menuBar, "menu.view").getItems().add(modes);
        MenuItem searchCommands = new MenuItem(FeatureUi.text("Befehle und Tastenkürzel …", "Commands and shortcuts …"));
        searchCommands.setId("commands.search");
        searchCommands.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Shortcut+Shift+P"));
        CommandIdentity.menu(menuBar, "menu.extras").getItems().add(searchCommands);
        CommandCenter[] centerHolder = new CommandCenter[1];
        searchCommands.setOnAction(event -> centerHolder[0].show());
        commandRegistry = new WindowCommandRegistry(this, menuBar);
        centerHolder[0] = new CommandCenter(this, commandRegistry);
        commandSurface = new CommandSurfaceController(this, menuBar, commandRegistry, followApplicationMode);
        addFeatureDisposer(commandSurface::close); addFeatureDisposer(commandRegistry::close);
        addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, event -> disposeFeatures());
        // Druckersystem schon beim ersten Anzeigen vorbereiten
        setOnShowing(event -> warmUpPrintSystem());
    }

    /** Lädt das Anwendungs-Stylesheet defensiv; fehlt die Ressource im Classpath/JAR,
     *  wird kein Stylesheet hinzugefügt (keine NPE).
     *
     * @return externe URL des Stylesheets oder {@code null}, wenn die Ressource nicht gefunden wurde
     */
    private String loadApplicationStylesheet() {
        var css = EditorWindow.class.getResource("/de/richmarkdown/ui/app.css");
        if (css != null) {
            return css.toExternalForm();
        }
        System.err.println("Ressource /de/richmarkdown/ui/app.css nicht gefunden – Stylesheet wird übersprungen.");
        return null;
    }

    /** Liefert die Editor-Fassade, die Dokumentfunktionen dieses Fensters steuert.
     *
     * @return Editor-Fassade dieses Fensters
     */
    public MarkdownEditor getEditor() {
        return editor;
    }

    /** Liefert die Formatierungsleiste des Fensters.
     *
     * @return Formatierungsleiste des Fensters
     */
    private WindowCommandRegistry commandRegistry;
    private CommandSurfaceController commandSurface;
    public WindowCommandRegistry getCommandRegistry() { return commandRegistry; }
    public CommandSurfaceController getCommandSurface() { return commandSurface; }

    public FormatToolBar getToolBar() {
        return toolBar;
    }

    /** Liefert die Statusleiste mit Dokumentstatus und Zoomsteuerung.
     *
     * @return Statusleiste des Fensters
     */
    public StatusBar getStatusBar() {
        return statusBar;
    }

    /** Liefert das JavaFX-Control, das den Dokumentinhalt rendert und bearbeitet.
     *
     * @return zugrunde liegendes Rich-Text-Area-Control
     */
    public RichTextArea getEditorControl() {
        return editor.getControl();
    }

    /** Liefert den Datei-I/O-Dienst, der von den Dateiaktionen genutzt wird.
     *
     * @return Datei-I/O-Dienst für Markdown-Dateien (stateless Engine aus dem
     *         Editor, kein Fenster-Zustand)
     */
    public MarkdownFileHandler getFileHandler() {
        return editor.getFileHandler();
    }

    /** Liefert den Controller für Zoomfaktor, Viewport und Zoom-Bedienelemente.
     *
     * @return Controller der Zoomsteuerung und des Editor-Viewports
     */
    public ZoomController getZoomController() {
        return zoomController;
    }

    /** Liefert die hierarchische, mit dem Caret synchronisierte Strukturansicht.
     *
     * @return navigierbare Dokumentstrukturansicht
     */
    public DocumentStructureView getStructureView() {
        return structureView;
    }

    /** Liefert die am sichtbaren Absatzlayout ausgerichtete Nummernleiste.
     *
     * @return Zeilennummernleiste dieses Fensters
     */
    public LineNumberGutter getLineNumberGutter() {
        return lineNumberGutter;
    }

    /** Schaltet die Sichtbarkeit der Dokumentstruktur um. */
    public void toggleStructureView() {
        setStructureViewVisible(!structureView.isVisible());
    }

    /**
     * Setzt die Sichtbarkeit der Dokumentstruktur und speichert den Zustand.
     *
     * @param visible {@code true}, um die Strukturansicht anzuzeigen
     */
    public void setStructureViewVisible(boolean visible) {
        structureView.setVisible(visible);
        structureView.setManaged(visible);
        I18n.setDocumentStructureEnabled(visible);
        if (visible) {
            structureView.refresh();
        }
    }

    /** Schaltet die Sichtbarkeit der Zeilennummern um. */
    public void toggleLineNumbers() {
        setLineNumbersVisible(!lineNumberGutter.isVisible());
    }

    /**
     * Setzt die Sichtbarkeit der Zeilennummern und speichert den Zustand.
     *
     * @param visible {@code true}, um die Zeilennummern anzuzeigen
     */
    public void setLineNumbersVisible(boolean visible) {
        lineNumberGutter.setVisible(visible);
        lineNumberGutter.setManaged(visible);
        I18n.setLineNumbersEnabled(visible);
        if (visible) {
            lineNumberGutter.refresh();
        }
    }

    // ---------- Zurück-/Vorwärts-Navigation (AddFeatures #4) ----------

    /**
     * Springt zur angegebenen Position und merkt sich die Ausgangsposition
     * für die Zurück-Navigation (Vorbild LO-Navigationsleiste).
     *
     * @param position Zielposition im Dokument
     */
    public void jumpTo(int position) {
        navigationHistory.jumpPerformed(editor.getInternalCaretPosition());
        editor.goToPosition(position);
    }

    /**
     * Springt zur angegebenen Position mit Label und Sprungtyp.
     *
     * @param position Zielposition im Dokument
     * @param label optionales Label (z. B. Überschriftentext)
     * @param type Art des Sprungs
     */
    public void jumpTo(int position, String label, NavigationHistory.JumpType type) {
        navigationHistory.jumpPerformed(editor.getInternalCaretPosition(), label, type);
        editor.goToPosition(position);
    }

    /**
     * Springt zurück zur letzten Sprungquelle.
     *
     * @return {@code true}, wenn ein Zurück-Sprung ausgeführt wurde
     */
    public boolean navigateBack() {
        var target = navigationHistory.goBack(editor.getInternalCaretPosition());
        target.ifPresent(jt -> editor.goToPosition(jt.position()));
        return target.isPresent();
    }

    /**
     * Springt vorwärts zur nächsten gemerkten Position.
     *
     * @return {@code true}, wenn ein Vorwärts-Sprung ausgeführt wurde
     */
    public boolean navigateForward() {
        var target = navigationHistory.goForward(editor.getInternalCaretPosition());
        target.ifPresent(jt -> editor.goToPosition(jt.position()));
        return target.isPresent();
    }

    /**
     * Liefert die Navigations-Historie dieses Fensters (für Tests und
     * weitere Sprungquellen).
     *
     * @return Navigations-Historie der Zurück-/Vorwärts-Navigation
     */
    public NavigationHistory getNavigationHistory() {
        return navigationHistory;
    }

    /**
     * Liefert den ViewModeController (für Tests).
     */
    public ViewModeController getViewModeController() {
        return viewModeController;
    }

    /**
     * Erstellt ein neues Dokument aus einer Vorlage (AddFeatures #17).
     */
    public void newFromTemplate() {
        TemplateDialog dialog = new TemplateDialog(getDialogOwner());
        dialog.showAndWait().ifPresent(templateContent -> FeatureMenus.newFromMarkdown(this, templateContent));
    }

    /**
     * Öffnet den Formatfehler-Prüf-Dialog (Extras → Dokument auf Formatfehler
     * prüfen). Prüft das aktuelle Dokument und springt per Klick auf ein Issue
     * an die betreffende Stelle (NavigationHistory zeichnet den Sprung auf).
     */
    public void showIssueCheckDialog() {
        de.richmarkdown.features.checking.CheckingTools.showIssues(editor, getDialogOwner(), sourcePosition -> {
            int exported = de.richmarkdown.features.checking.IssueNavigation.exportedParagraph(
                    editor.getMarkdownSource(), sourcePosition,
                    de.richmarkdown.features.checking.CheckingTools.directory(editor));
            if (editor.getControl().getSkin() instanceof com.gluonhq.richtextarea.RichTextAreaSkin skin) {
                int internal = skin.getViewModel().getTextBuffer().getInternalPosition(exported);
                jumpTo(internal, null, NavigationHistory.JumpType.ISSUE);
            }
        });
    }

    // ---------- Schreibgeschützter Modus (AddFeatures #5) ----------

    /**
     * Gibt an, ob das Dokument schreibgeschützt geöffnet ist
     * (Vorbild LO „Datei → Öffnen (schreibgeschützt)“).
     *
     * @return {@code true}, wenn Bearbeitungen gesperrt sind
     */
    public boolean isReadOnly() {
        return readOnly.get();
    }

    /**
     * Liefert die lesbare Property des Schreibgeschützt-Zustands (Bindings
     * für Menüeinträge).
     *
     * @return Property des Schreibgeschützt-Zustands
     */
    public javafx.beans.property.ReadOnlyBooleanProperty readOnlyProperty() {
        return readOnly.getReadOnlyProperty();
    }

    /**
     * Setzt den Schreibgeschützt-Zustand: Das Control wird nicht mehr
     * editierbar, Bearbeitungs-Schaltflächen und Speichern-Aktionen der
     * Toolbar werden gesperrt (graue Menüeinträge wie in LO).
     *
     * @param value {@code true}, um Bearbeitungen zu sperren
     */
    public void setReadOnly(boolean value) {
        readOnly.set(value);
        editor.getControl().setEditable(!value);
        toolBar.setReadOnly(value);
    }

    /**
     * Öffnet die über den Dialog gewählte Datei schreibgeschützt.
     */
    public void showOpenDialogReadOnly() {
        if (workspace != null) {
            File file = chooseFileToOpen();
            if (file != null) workspace.openFile(file).setReadOnly(true);
            return;
        }
        if (!confirmDiscardChanges()) {
            return;
        }
        File file = chooseFileToOpen();
        if (file != null) {
            loadFile(file);
            setReadOnly(true);
        }
    }

    /**
     * Öffnet die angegebene Datei schreibgeschützt (Dirty-Check inklusive).
     *
     * @param file Markdown-Datei, die geladen werden soll
     */
    public void openFileReadOnly(File file) {
        if (workspace != null) { workspace.openFile(file).setReadOnly(true); return; }
        if (!confirmDiscardChanges()) {
            return;
        }
        loadFile(file);
        setReadOnly(true);
    }

    // ---------- Zeilenumbruch (AddFeatures #6) ----------

    /**
     * Setzt den Zeilenumbruch-Modus (Format → Zeilenumbruch, Vorbild LO).
     *
     * @param mode gewünschter Umbruchmodus
     */
    public void setLineWrapMode(LineWrapMode mode) {
        zoomController.setWrapMode(mode);
        I18n.setLineWrapModeName(mode.name());
    }

    /**
     * Liefert den aktuellen Zeilenumbruch-Modus.
     *
     * @return aktueller Umbruchmodus
     */
    public LineWrapMode getLineWrapMode() {
        return zoomController.getWrapMode();
    }

    // ---------- Statistik (AddFeatures #3) ----------

    /**
     * Öffnet den Statistik-Dialog (Extras → Statistik, Vorbild LO) mit den
     * aktuellen Zählfeldern des Dokuments.
     */
    public void showStatisticsDialog() {
        de.richmarkdown.ui.dialogs.StatisticsDialog dialog =
                new de.richmarkdown.ui.dialogs.StatisticsDialog(editor::getStatistics);
        dialog.initOwner(getDialogOwner());
        dialog.showAndWait();
    }

    /**
     * Oeffnet den Suchen-und-Ersetzen-Dialog (Bearbeiten, Vorbild LO).
     * Nicht-modal: bleibt offen, waehrend im Dokument gearbeitet wird.
     * Es gibt genau eine Instanz; die Auswahl wird als Suchtext uebernommen.
     */
    public void showFindReplaceDialog() {
        com.gluonhq.richtextarea.RichTextArea control = editor.getControl();
        com.gluonhq.richtextarea.RichTextAreaSkin skin =
                (com.gluonhq.richtextarea.RichTextAreaSkin) control.getSkin();
        if (skin == null) {
            return;
        }
        if (findReplaceDialog == null) {
            de.richmarkdown.findreplace.FindReplaceEngine engine =
                    new de.richmarkdown.findreplace.FindReplaceEngine(control,
                            skin.getViewModel());
            // Treffer nach der Markierung in den Sichtbereich scrollen
            // (reine Viewport-Aenderung, keine Selection-/Fokus-Eingriffe).
            engine.setRevealHandler(editor::revealPosition);
            findReplaceDialog = new de.richmarkdown.ui.dialogs.FindReplaceDialog(
                    getDialogOwner(), engine, backwards -> confirmWrap(backwards));
            findReplaceDialog.setCriteriaHandler(this::rememberFindCriteria);
            findReplaceDialog.setOnHidden(event -> findReplaceDialog = null);
        }
        String selected = selectedTextForFind();
        findReplaceDialog.showWithSelection(selected);
    }

    /**
     * Sucht ohne Dialog weiter (Strg+F): nutzt die letzten Suchkriterien.
     */
    public void findNextWithoutDialog() {
        if (lastFindText == null || lastFindText.isEmpty()) {
            showFindReplaceDialog();
            return;
        }
        com.gluonhq.richtextarea.RichTextArea control = editor.getControl();
        com.gluonhq.richtextarea.RichTextAreaSkin skin =
                (com.gluonhq.richtextarea.RichTextAreaSkin) control.getSkin();
        if (skin == null) {
            return;
        }
        de.richmarkdown.findreplace.FindReplaceEngine engine =
                new de.richmarkdown.findreplace.FindReplaceEngine(control,
                        skin.getViewModel());
        engine.setRevealHandler(editor::revealPosition);
        de.richmarkdown.findreplace.FindReplaceEngine.SearchResult result;
        try {
            result = engine.findNext(lastFindText, lastFindOptions.copy());
        } catch (IllegalArgumentException error) {
            lastError = error.getMessage();
            showError(lastError);
            return;
        } catch (StackOverflowError error) {
            lastError = de.richmarkdown.ui.FeatureUi.text("Regulärer Ausdruck ist zu komplex.", "Regular expression is too complex.");
            showError(lastError);
            return;
        }
        if (result == null) {
            showError(I18n.text("dialog.findReplace.noMatch"));
            return;
        }
        if (result.getWrap() == de.richmarkdown.findreplace.FindReplaceEngine.Wrap.WRAPPED
                && !confirmWrap(lastFindOptions.isBackwards())) {
            return;
        }
        engine.selectMatch(result.getMatch());
    }

    /** Merkt die letzten Suchkriterien fuer Strg+F (wird vom Dialog gesetzt). */
    public void rememberFindCriteria(String findText,
            de.richmarkdown.findreplace.FindReplaceEngine.FindOptions options) {
        this.lastFindText = findText == null ? "" : findText;
        this.lastFindOptions = options == null
                ? new de.richmarkdown.findreplace.FindReplaceEngine.FindOptions()
                : options.copy();
    }

    private boolean confirmWrap(boolean backwards) {
        String key = backwards ? "dialog.findReplace.wrapToEnd" : "dialog.findReplace.wrapToStart";
        if (confirmationHandler != null) {
            return confirmationHandler.confirm(I18n.text("dialog.findReplace.title"),
                    I18n.text(key));
        }
        javafx.scene.control.Alert alert =
                new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.CONFIRMATION);
        // Owner ist der Suchen-Dialog (falls offen): Nach dem Schliessen kehrt
        // der Fokus dorthin zurueck, sodass Enter wieder „Weitersuchen“ ausloest.
        // Mit Hauptfenster als Owner wuerde der Fokus in den Editor fallen und
        // Enter dort den Absatzumbruch-Handler feuern (Formatierung kaputt).
        alert.initOwner(findReplaceDialog != null && findReplaceDialog.isShowing()
                ? findReplaceDialog : getDialogOwner());
        alert.setTitle(I18n.text("dialog.findReplace.title"));
        alert.setHeaderText(null);
        alert.setContentText(I18n.text(key));
        return alert.showAndWait()
                .filter(response -> response == javafx.scene.control.ButtonType.OK)
                .isPresent();
    }

    private String selectedTextForFind() {
        com.gluonhq.richtextarea.Selection selection = editor.getControl().getSelection();
        if (selection == null || !selection.isDefined()) {
            return "";
        }
        com.gluonhq.richtextarea.model.Document document = editor.getControl().getDocument();
        if (document == null) {
            return "";
        }
        int start = Math.max(0, Math.min(selection.getStart(), document.getText().length()));
        int end = Math.max(0, Math.min(selection.getEnd(), document.getText().length()));
        if (end <= start || end - start > 500) {
            return "";
        }
        String selected = document.getText().substring(start, end);
        return selected.contains("\n") ? "" : selected;
    }

    /**
     * Liefert die momentan geöffnete Datei oder {@code null}, wenn noch keine
     * Datei zugeordnet ist.
     *
     * <p>Durchreichung zu {@link MarkdownEditor#getCurrentFile()} – der
     * Editor ist die einzige Stelle mit Dateizuordnung (kein Fenster-Duplikat).
     *
     * @return aktuell geöffnete Datei oder {@code null}
     */
    public File getCurrentFile() {
        return editor.getCurrentFile().orElse(null);
    }

    /**
     * Liefert die letzte bei einer Dateioperation gespeicherte Fehlermeldung.
     *
     * @return Fehlertext oder {@code null}, wenn seit dem letzten erfolgreichen
     *         Vorgang kein Fehler aufgetreten ist
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * Setzt den Handler für Bestätigungen bei nicht gespeicherten Änderungen.
     *
     * @param handler Bestätigungsstrategie, insbesondere zum Ersetzen des
     *                interaktiven Bestätigungsdialogs in Tests
     */
    public void setConfirmationHandler(ConfirmationHandler handler) {
        this.confirmationHandler = handler;
    }

    /** Overrides file/validation error presentation; null restores the default owned Alert. */
    public void setErrorHandler(java.util.function.Consumer<String> handler) { errorHandler = handler; }

    /** Owner used by dialogs whether this document is standalone or inside a tab. */
    public javafx.stage.Window getDialogOwner() {
        var scene = editor.getControl().getScene();
        return scene != null && scene.getWindow() != null ? scene.getWindow() : workspace == null ? this : workspace;
    }

    void attachWorkspace(WorkspaceWindow host) { this.workspace = host; }
    void openFileInPlace(File file) { loadFile(file); }
    public boolean mayCloseDocument() { return confirmDiscardChanges(); }
    public void addFeatureDisposer(Runnable disposer) { featureDisposers.add(disposer); }
    public void removeFeatureDisposer(Runnable disposer) { featureDisposers.remove(disposer); }
    public void disposeFeatures() {
        featuresDisposed = true; fileOperationRevision.incrementAndGet();
        editor.cancelPendingIo();
        structureView.close(); lineNumberGutter.close();
        var disposers = List.copyOf(featureDisposers);
        featureDisposers.clear();
        RuntimeException failure = null;
        for (Runnable disposer : disposers) {
            try { disposer.run(); }
            catch (RuntimeException error) {
                if (failure == null) failure = error; else failure.addSuppressed(error);
            }
        }
        if (findReplaceDialog != null) { findReplaceDialog.close(); findReplaceDialog = null; }
        if (failure != null) throw failure;
    }
    public WorkspaceWindow getWorkspace() { return workspace; }
    public BorderPane getEditorRoot() { return editorRoot; }
    public VBox getChrome() { return chrome; }

    public double captureScroll() {
        var bar = editor.getControl().lookup(".scroll-bar:vertical");
        return bar instanceof javafx.scene.control.ScrollBar scroll ? Math.max(0, scroll.getValue()) : 0;
    }
    public void restoreScroll(double value) {
        var bar = editor.getControl().lookup(".scroll-bar:vertical");
        if (bar instanceof javafx.scene.control.ScrollBar scroll) scroll.setValue(Math.min(scroll.getMax(), Math.max(scroll.getMin(), value)));
    }
    void showExternalChangeNotice(String message) {
        if (editorRoot == null) return;
        if (message == null) { editorRoot.setTop(chrome); return; }
        HBox notice = new HBox(8, new javafx.scene.control.Label(message));
        notice.getChildren().add(FeatureUi.button(FeatureUi.text("Neu laden", "Reload"),
                FeatureUi.text("Datei nach Bestätigung neu laden", "Reload the disk file after confirmation"), () -> {
                    if (confirmDiscardChanges() && getCurrentFile() != null) loadFile(getCurrentFile());
                }));
        notice.getChildren().add(FeatureUi.button(FeatureUi.text("Vergleichen", "Compare"),
                FeatureUi.text("Editorinhalt mit dem aktuellen Dateistand vergleichen", "Compare editor contents with the disk file"), () -> {
                    File file = getCurrentFile();
                    if (file != null && file.isFile()) {
                        new DiffDialog(getDialogOwner(), editor, file, file, editor.getDocumentSnapshot(), isReadOnly(), this::showError).show();
                    }
                }));
        notice.getChildren().add(de.richmarkdown.ui.dialogs.WorkflowDialogs.button("saveAs", this::showSaveDialog));
        notice.getChildren().add(FeatureUi.button(FeatureUi.text("Ausblenden", "Dismiss"),
                FeatureUi.text("Hinweis ausblenden; Überschreibschutz bleibt aktiv", "Hide the notice; overwrite protection remains active"), () -> editorRoot.setTop(chrome)));
        editorRoot.setTop(new VBox(chrome, notice));
    }

    // ---------- Datei-Operationen ----------

    /** Erstellt ein leeres Dokument, sofern der Dirty-Check dies zulässt. */
    public void newDocument() {
        if (workspace != null) { workspace.newDocument(); return; }
        if (!confirmDiscardChanges()) {
            return;
        }
        editor.clear();
        lastError = null;
        setReadOnly(false);
        updateTitle();
    }

    /**
     * Öffnet die angegebene Datei, sofern ungespeicherte Änderungen verworfen
     * werden dürfen.
     *
     * @param file Markdown-Datei, die geladen werden soll
     */
    public void openFile(File file) {
        if (workspace != null) { workspace.openFile(file); return; }
        if (!confirmDiscardChanges()) {
            return;
        }
        loadFile(file);
    }

    /** Öffnet den Dateiauswahldialog und lädt die ausgewählte Markdown-Datei. */
    public void showOpenDialog() {
        if (workspace != null) {
            File selected = chooseFileToOpen();
            if (selected != null) workspace.openFile(selected);
            return;
        }
        if (!confirmDiscardChanges()) {
            return;
        }
        File file = chooseFileToOpen();
        if (file != null) {
            loadFile(file);
        }
    }

    /** Speichert in die aktuelle Datei oder startet „Speichern unter“. */
    public void save() {
        if (editor.getCurrentFile().isEmpty()) {
            showSaveDialog();
        } else {
            saveToFile(editor.getCurrentFile().orElseThrow());
        }
    }

    /** Öffnet „Speichern unter“ und speichert bei Auswahl in die Zieldatei. */
    public void showSaveDialog() {
        File file = chooseFileToSave();
        if (file != null) {
            saveToFile(file);
        }
    }

    /**
     * Speichert das aktuelle Dokument als UTF-8-Markdown in eine Datei.
     * Delegiert asynchron an {@link MarkdownEditor#saveAsync(File)} – Dirty-Check,
     * Fehler-UI und Titel bleiben Fenster-Sache.
     *
     * @param file Zieldatei
     */
    public void saveToFile(File file) {
        long request = fileOperationRevision.incrementAndGet();
        var allowed = workspace == null ? java.util.concurrent.CompletableFuture.completedFuture(true)
                : workspace.confirmExternalOverwriteAsync(this, file);
        pendingFileOperation = allowed.thenCompose(confirmed -> confirmed && !featuresDisposed && request == fileOperationRevision.get() ? editor.saveAsync(file)
                : java.util.concurrent.CompletableFuture.failedFuture(new java.util.concurrent.CancellationException()));
        pendingFileOperation = pendingFileOperation.whenComplete((ignored, failure) -> {
            if (featuresDisposed || request != fileOperationRevision.get()) return;
            if (failure == null) {
                if (workspace != null) workspace.documentSaved(this);
                lastError = null;
                I18n.addRecentFile(file.getAbsolutePath()); updateTitle();
            } else if (!(unwrap(failure) instanceof java.util.concurrent.CancellationException)) {
                lastError = MessageFormat.format(I18n.text("error.save"), file.getName(), unwrap(failure).getMessage());
                showError(lastError);
            }
        });
    }

    private static Throwable unwrap(Throwable failure) {
        while (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null) failure = failure.getCause();
        return failure;
    }

    /** Schließt das Fenster, wenn der Dirty-Check das Verwerfen erlaubt. */
    public void exit() {
        if (workspace != null) { if (workspace.closeWorkspace()) workspace.close(); return; }
        if (confirmDiscardChanges()) {
            close();
        }
    }

    /**
     * Opens the file comparison dialog (File → Compare with another file...).
     * Compares current editor contents with a second file chosen via file dialog,
     * compares both and shows the differences in a Track Changes view
     * (Word/LibreOffice Writer style inline diff).
     */
    public void showDiffDialog() {
        File current = editor.getCurrentFile().orElse(null);
        try {
            File other = chooseFileToOpen();
            if (other == null) return;
            var currentContents = editor.getDocumentSnapshot();
            lastError = null;
            new DiffDialog(getDialogOwner(), editor, current, other, currentContents, isReadOnly(),
                    message -> lastError = message).showAndWait();
        } catch (RuntimeException failure) {
            lastError = MessageFormat.format(I18n.text("trackChanges.error"), failure.getMessage());
            showError(lastError);
        }
    }

    // ---------- Drucken ----------

    private Printer cachedPrinter;

    /**
     * Druckt das Dokument (Datei → Drucken...), wie in Word/LibreOffice:
     * JavaFX-nativer Druckdialog, dann seitenweiser Ausdruck der
     * Druckfassung. Ist kein Drucker verfügbar, erscheint ein Hinweis.
     *
     * <p>Die Vorbereitung (CUPS-Druckersuche) läuft auf einem
     * Hintergrund-Thread – die Oberfläche bleibt bedienbar. Das ist die
     * ausdrückliche Empfehlung der {@code PrinterJob}-Dokumentation
     * („perform printing on a new thread"). Der modale JavaFX-Dialog läuft
     * per {@code showAndWait} auf dem FX-Thread (verschachtelte
     * Ereignisschleife); er hat bewusst kein Owner-Fenster, damit das
     * Hauptfenster unter X11/GTK nach Abbruch bedienbar bleibt. Nur das
     * Rendern der Druckfassung muss auf dem FX-Thread erfolgen
     * (neue Szene/Stage).
     */
    public void print() {
        print(de.richmarkdown.features.printing.PrintOptions.load(java.util.prefs.Preferences.userNodeForPackage(EditorWindow.class).node("printing")));
    }

    public void configurePrint() {
        print();
    }
    private record PrintSetup(com.gluonhq.richtextarea.model.Document source, int caret, int start, int end,
                              de.richmarkdown.features.printing.PrintOptions initial) { }
    private final ThreadLocal<PrintSetup> printSetup = new ThreadLocal<>();

    private void print(de.richmarkdown.features.printing.PrintOptions options) {
        PrintSetup setup;
        try {
            editorRoot.applyCss();
            var snapshot = editor.getDocumentSnapshot();
            var selection = editor.getControl().getSelection();
            int start = 0, end = 0;
            if (selection != null && selection.isDefined() && editor.getControl().getSkin() instanceof com.gluonhq.richtextarea.RichTextAreaSkin skin) {
                var buffer = skin.getViewModel().getTextBuffer();
                start = buffer.getText(0, selection.getStart()).length(); end = buffer.getText(0, selection.getEnd()).length();
            }
            int caret = Math.max(0, editor.getControl().getCaretPosition());
            setup = new PrintSetup(snapshot, caret, start, end, options);
        } catch (RuntimeException failure) { FeatureUi.error(getDialogOwner(), failure); return; }
        Thread thread = new Thread(() -> {
            List<Printer> printers;
            try {
                printers = listPrinters();
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    showPrintUnavailableMessage();
                });
                return;
            }
            if (printers.isEmpty()) {
                Platform.runLater(() -> {
                    showNoPrinterMessage();
                });
                return;
            }
            PrintDialogResult choice;
            try {
                printSetup.set(setup);
                try { choice = showPrintDialog(printers); } finally { printSetup.remove(); }
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    showPrintUnavailableMessage();
                });
                return;
            }
            if (choice == null) {
                return;
            }
            PrinterJob job;
            try {
                job = createPrinterJob(choice.printer());
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    showPrintUnavailableMessage();
                });
                return;
            }
            if (job == null) {
                Platform.runLater(() -> {
                    showNoPrinterMessage();
                });
                return;
            }
            try {
                var chosenOptions = choice.options() == null ? options : choice.options();
                PrintRequest request = new PrintRequest(de.richmarkdown.features.printing.PrintDocuments.prepare(setup.source(), chosenOptions, setup.caret(), setup.start(), setup.end()), chosenOptions);
                chosenOptions.save(java.util.prefs.Preferences.userNodeForPackage(EditorWindow.class).node("printing"));
                applyPrintChoice(job, choice);
                PageLayout chosenLayout = choice.printer().createPageLayout(choice.paper(), choice.orientation(),
                        de.richmarkdown.features.printing.PrintOptions.points(chosenOptions.left()),
                        de.richmarkdown.features.printing.PrintOptions.points(chosenOptions.right()),
                        de.richmarkdown.features.printing.PrintOptions.points(chosenOptions.top()),
                        de.richmarkdown.features.printing.PrintOptions.points(chosenOptions.bottom()));
                job.getJobSettings().setPageLayout(chosenLayout);
                printRequests.put(job, request);
                Platform.runLater(() -> printDocument(job));
            } catch (RuntimeException failure) {
                job.cancelJob(); Platform.runLater(() -> FeatureUi.error(getDialogOwner(), failure));
            }
        }, "rme-print");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, e) -> {
            Platform.runLater(() -> {
                showPrintUnavailableMessage();
            });
        });
        thread.start();
    }

    /**
     * Druckeraufzählung für den Hintergrund-Thread (überschreibbar für
     * Tests). Sortiert ist nichts – {@link PrintDialog} sortiert selbst.
     *
     * @return verfügbare Drucker, ggf. leer
     */
    protected List<Printer> listPrinters() {
        return new ArrayList<>(Printer.getAllPrinters());
    }

    /**
     * Rein JavaFX-nativer Druckdialog. Ersetzt
     * {@code PrinterJob.showPrintDialog()} (Swing-{@code ServiceDialog}),
     * der im GraalVM-Native-Image mit
     * {@code Error: java.home property not set} abstürzt.
     *
     * <p>Die CUPS-Aufzählung lief bereits auf dem Hintergrund-Thread; der
     * modale JavaFX-Dialog muss auf dem FX-Thread in einer verschachtelten
     * Ereignisschleife laufen ({@code showAndWait}). Der Aufrufer
     * ({@code rme-print}) wartet per Latch – der FX-Thread bleibt dabei
     * frei, die Menüs bleiben nach Abbruch bedienbar: Der Dialog hat bewusst
     * <b>kein</b> Owner-Fenster (kein {@code setEnabled(false)} auf dem
     * Hauptfenster, das unter X11/GTK nicht zuverlässig zurückgenommen
     * wurde).
     *
     * @param printers verfügbare Drucker vom Hintergrund-Thread
     * @return Dialogergebnis oder {@code null} bei Abbruch
     */
    protected PrintDialogResult showPrintDialog(List<Printer> printers) {
        PrintSetup setup = printSetup.get();
        if (Platform.isFxApplicationThread()) {
            PrintDialog dialog = createPrintDialog(printers, setup);
            return dialog.showAndWait().orElse(null);
        }
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<PrintDialogResult> result =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> error =
                new java.util.concurrent.atomic.AtomicReference<>();
        Platform.runLater(() -> {
            try {
                PrintDialog dialog = createPrintDialog(printers, setup);
                result.set(dialog.showAndWait().orElse(null));
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        Throwable failure = error.get();
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure instanceof Error err) {
            throw err;
        }
        if (failure != null) {
            throw new IllegalStateException(failure);
        }
        return result.get();
    }

    private PrintDialog createPrintDialog(List<Printer> printers, PrintSetup setup) {
        if (setup == null) return new PrintDialog(printers, cachedPrinter);
        return new PrintDialog(printers, cachedPrinter, setup.initial(), setup.end() > setup.start(), (choice, owner) -> {
            var document = de.richmarkdown.features.printing.PrintDocuments.prepare(setup.source(), choice.options(), setup.caret(), setup.start(), setup.end());
            PrintPreview.show(owner, editor, document, choice);
        });
    }

    /**
     * Übernimmt die Dialogauswahl in den Druckauftrag: Drucker, Kopienzahl,
     * Seitenlayout (Papier + Ausrichtung) sowie Duplex und Farbe, soweit der
     * Drucker sie meldet.
     *
     * @param job bestätigter Druckauftrag
     * @param choice Dialogergebnis
     */
    protected void applyPrintChoice(PrinterJob job, PrintDialogResult choice) {
        job.setPrinter(choice.printer());
        cachedPrinter = choice.printer();
        JobSettings settings = job.getJobSettings();
        settings.setCopies(Math.max(1, choice.copies()));
        Printer printer = choice.printer();
        PageLayout layout = printer.createPageLayout(
                choice.paper(), choice.orientation(),
                Printer.MarginType.DEFAULT);
        settings.setPageLayout(layout);
        if (printer.getPrinterAttributes().getSupportedPrintSides()
                .contains(choice.sides())) {
            settings.setPrintSides(choice.sides());
        }
        if (printer.getPrinterAttributes().getSupportedPrintColors()
                .contains(choice.color())) {
            settings.setPrintColor(choice.color());
        }
    }

    /** Hinweis gegenüber dem Anwender, wenn kein Drucker verfügbar ist. */
    protected void showNoPrinterMessage() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(I18n.text("error.print.title"));
        alert.setHeaderText(null);
        alert.setContentText(I18n.text("error.noPrinter"));
        alert.show();
    }

    /**
     * Hinweis, wenn das Drucksystem im Native-Image nicht nutzbar ist
     * (z. B. AWT-FontConfig ohne {@code java.home}). Der JVM-/jpackage-Build
     * druckt normal; nur das GraalVM-Binary kann den Swing-basierten
     * Systemdialog nicht aufbauen.
     */
    protected void showPrintUnavailableMessage() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(I18n.text("error.print.title"));
        alert.setHeaderText(null);
        alert.setContentText(I18n.text("error.printUnavailable"));
        alert.show();
    }

    /**
     * Factory für den Druckauftrag auf dem gewählten Drucker (überschreibbar
     * für Tests). Der gewählte Drucker wird gemerkt – die CUPS-Abfrage kostet
     * pro Aufruf rund eine Sekunde, das Job-Bauen dagegen nur ~20 ms.
     *
     * @param printer im Dialog gewählter Drucker
     * @return Druckauftrag für den Drucker oder {@code null}, wenn keiner
     *         erstellt werden kann
     */
    protected PrinterJob createPrinterJob(Printer printer) {
        if (printer == null) {
            return null;
        }
        if (printer.equals(cachedPrinter)) {
            PrinterJob job = PrinterJob.createPrinterJob(printer);
            if (job != null) {
                return job;
            }
            cachedPrinter = null; // Drucker nicht mehr verfügbar -> neu wählen
            return null;
        }
        cachedPrinter = printer;
        return PrinterJob.createPrinterJob(printer);
    }

    /**
     * Druckerermittlung: Systemstandarddrucker; ist keiner eingerichtet
     * (auf Linux häufig), der erste verfügbare – sonst könnte der Anwender
     * nie einen Druckdialog öffnen.
     *
     * @return Systemstandarddrucker oder erster verfügbarer Drucker;
     *         {@code null}, wenn keiner verfügbar ist
     */
    protected Printer selectPrinter() {
        Printer printer = Printer.getDefaultPrinter();
        if (printer == null) {
            for (Printer available : Printer.getAllPrinters()) {
                return available;
            }
        }
        return printer;
    }

    /**
     * Ältere auftragsbasierte Factory (nur für Abwärtskompatibilität der
     * Tests): baut den Auftrag auf dem gemerkten bzw. ermittelten Drucker.
     *
     * @return Druckauftrag oder {@code null}, wenn kein Drucker verfügbar ist
     */
    protected PrinterJob createPrinterJob() {
        Printer printer = cachedPrinter;
        if (printer != null) {
            PrinterJob job = PrinterJob.createPrinterJob(printer);
            if (job != null) {
                return job;
            }
            cachedPrinter = null; // Drucker nicht mehr verfügbar -> neu wählen
        }
        printer = selectPrinter();
        if (printer == null) {
            return null;
        }
        cachedPrinter = printer;
        return PrinterJob.createPrinterJob(printer);
    }

    /**
     * Bereitet das Druckersystem schon beim Fensteraufbau im Hintergrund
     * vor (CUPS-Aufzählung + Job-Bauen ~2 s): Der erste Klick auf
     * „Drucken..." öffnet den Dialog dann ohne Wartezeit.
     */
    private void warmUpPrintSystem() {
        Thread thread = new Thread(() -> {
            try {
                Printer printer = selectPrinter();
                if (printer != null) {
                    cachedPrinter = printer;
                }
            } catch (Throwable t) {
                // Native-Image ohne java.home: AWT-Font-Init schlägt fehl –
                // kein Warm-up, Drucken meldet später gezielt den Hinweis.
            }
        }, "rme-print-warmup");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, e) -> {
        });
        thread.start();
    }

    /**
     * Baut die Druckfassung auf und schichtet sie auf die Seiten des
     * Druckauftrags. JavaFX rendert je {@code printPage} genau eine Seite –
     * die Kopie wird deshalb pro Seite um die bedruckbare Seitenhöhe
     * verschoben (siehe {@link PrintSupport}).
     *
     * @param job bestätigter Druckauftrag
     */
    protected void printDocument(PrinterJob job) {
        JobSettings settings = job.getJobSettings();
        settings.setJobName(editor.getCurrentFile().map(File::getName).orElse("RichMarkDownEditor"));
        PageLayout layout = settings.getPageLayout();
        RichTextArea printCopy = editor.createPrintCopy();
        printCopy.setPrefWidth(layout.getPrintableWidth());
        Stage printStage = new Stage();
        printStage.setScene(new Scene(new Pane(printCopy),
                layout.getPrintableWidth(), layout.getPrintableHeight()));
        // Skin ohne sichtbares Fenster aufbauen
        printStage.getScene().getRoot().applyCss();
        PrintRequest request = printRequests.remove(job);
        var options = request == null ? de.richmarkdown.features.printing.PrintOptions.defaults() : request.options();
        if (request == null) editor.loadPrintCopy(printCopy);
        else if (printCopy.getSkin() instanceof com.gluonhq.richtextarea.RichTextAreaSkin skin) {
            new com.gluonhq.richtextarea.viewmodel.ActionCmdFactory().open(request.document()).apply(skin.getViewModel());
        }
        // Die Bibliothek übernimmt das Dokument asynchron (ein Puls) – Messung
        // und Paginierung folgen danach
        Platform.runLater(() -> Platform.runLater(
                () -> printLoadedCopy(printCopy, job, layout, printStage, options)));
    }

    private void printLoadedCopy(RichTextArea printCopy, PrinterJob job,
                                 PageLayout layout, Stage printStage, de.richmarkdown.features.printing.PrintOptions options) {
        try {
            printCopy.resize(layout.getPrintableWidth(), layout.getPrintableHeight());
            double contentHeight = PrintSupport.measureContentHeight(printCopy);
            printCopy.setPrefHeight(contentHeight);
            printCopy.resize(layout.getPrintableWidth(), contentHeight);
            printCopy.applyCss();
            printCopy.requestLayout();
            printCopy.layout();
            double bodyHeight = PrintPageView.bodyHeight(layout.getPrintableHeight(), options);
            int pages = PrintSupport.pageCount(contentHeight, bodyHeight);
            var selectedPages = options.pages(pages);
            ((Pane) printCopy.getParent()).getChildren().remove(printCopy);
            PrintPageView pageView = new PrintPageView(printCopy, layout.getPrintableWidth(), layout.getPrintableHeight(), options);
            printStage.getScene().setRoot(pageView); pageView.applyCss(); pageView.layout();
            boolean successful = true;
            for (int page : selectedPages) {
                pageView.showPage(page, pages);
                if (!job.printPage(pageView)) { successful = false; break; }
            }
            if (successful) job.endJob(); else job.cancelJob();
        } catch (RuntimeException failure) {
            job.cancelJob(); FeatureUi.error(getDialogOwner(), failure);
        } finally {
            printStage.close();
        }
    }

    // ---------- Interna ----------

    private void loadFile(File file) {
        openingFile = file;
        long request = fileOperationRevision.incrementAndGet();
        setReadOnly(false);
        pendingFileOperation = editor.openAsync(file).whenComplete((ignored, failure) -> {
            if (featuresDisposed || request != fileOperationRevision.get()) return;
            openingFile = null;
            if (failure == null) {
                if (workspace != null) workspace.documentSaved(this);
                lastError = null;
                I18n.addRecentFile(file.getAbsolutePath()); updateTitle();
            } else if (!(unwrap(failure) instanceof java.util.concurrent.CancellationException)) {
                lastError = MessageFormat.format(I18n.text("error.open"), file.getName(), unwrap(failure).getMessage());
                showError(lastError);
            }
        });
    }

    /**
     * Dirty-Check: Fragt nur nach, wenn das Dokument ungespeicherte Änderungen
     * hat. Gtrue zurück, wenn die Änderungen verworfen werden dürfen.
     */
    private boolean confirmDiscardChanges() {
        if (!editor.isModified()) {
            return true;
        }
        if (confirmationHandler != null) {
            return confirmationHandler.confirm(I18n.text("confirm.discard.title"),
                    I18n.text("confirm.discard"));
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(getDialogOwner());
        alert.setTitle(I18n.text("confirm.discard.title"));
        alert.setHeaderText(null);
        alert.setContentText(editor.getCurrentFile().map(File::getName).orElse(I18n.text("app.untitled")) + "\n" + I18n.text("confirm.discard"));
        ButtonType save = new ButtonType(de.richmarkdown.ui.dialogs.WorkflowDialogs.text("save"), javafx.scene.control.ButtonBar.ButtonData.YES);
        alert.getButtonTypes().setAll(save, DialogButtons.DISCARD, DialogButtons.CANCEL);
        DialogButtons.applyDialogStyles(alert.getDialogPane());
        ButtonType result = alert.showAndWait().orElse(DialogButtons.CANCEL);
        if (result == DialogButtons.DISCARD) return true;
        if (result != save) return false;
        File destination = editor.getCurrentFile().orElseGet(this::chooseFileToSave);
        if (destination == null) return false;
        javafx.scene.control.Dialog<Void> progress = new javafx.scene.control.Dialog<>();
        progress.initOwner(getDialogOwner()); progress.setTitle(de.richmarkdown.ui.dialogs.WorkflowDialogs.text("save"));
        progress.getDialogPane().setContent(new javafx.scene.control.ProgressIndicator());
        progress.getDialogPane().getButtonTypes().add(DialogButtons.CANCEL);
        progress.getDialogPane().lookupButton(DialogButtons.CANCEL).setDisable(true);
        DialogButtons.applyDialogStyles(progress.getDialogPane());
        java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean();
        progress.setOnCloseRequest(event -> { if (!completed.get()) event.consume(); });
        progress.setOnShown(event -> {
            saveToFile(destination);
            pendingFileOperation.whenComplete((value, failure) -> Platform.runLater(() -> { completed.set(true); progress.close(); }));
        });
        progress.showAndWait();
        return !editor.isModified() && lastError == null;
    }

    /**
     * Fehleranzeige. Bewusst non-blocking ({@code show()} statt
     * {@code showAndWait()}) – ein modaler Dialog würde in headless-Umgebungen
     * (Tests) den FX-Thread blockieren.
     */
    private void showError(String message) {
        if (errorHandler != null) { errorHandler.accept(message); return; }
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(getDialogOwner());
        alert.setTitle(I18n.text("error.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        javafx.scene.control.TextArea details = de.richmarkdown.ui.dialogs.WorkflowDialogs.output(message);
        alert.getDialogPane().setExpandableContent(new VBox(8, details,
                de.richmarkdown.ui.dialogs.WorkflowDialogs.button("copy", () -> de.richmarkdown.ui.dialogs.WorkflowDialogs.copy(message))));
        DialogButtons.applyDialogStyles(alert.getDialogPane());
        alert.getDialogPane().getButtonTypes().forEach(type -> {
            if (alert.getDialogPane().lookupButton(type) instanceof javafx.scene.control.Button button) {
                button.setTooltip(new javafx.scene.control.Tooltip(type.getText()));
            }
        });
        alert.show();
    }

    private void updateTitle() {
        String name = editor.getCurrentFile().map(File::getName).orElse(I18n.text("app.untitled"));
        setTitle(MessageFormat.format(I18n.text("app.title.document"), name));
    }

    /**
     * Öffnet den Dialog zur Auswahl einer Markdown-Datei.
     *
     * @return ausgewählte Datei oder {@code null}, wenn der Dialog abgebrochen wurde
     */
    protected File chooseFileToOpen() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.text("file.open.title"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(I18n.text("file.filter"),
                        "*.md", "*.markdown", "*.txt"));
        initialDirectory(chooser);
        File chosen = chooser.showOpenDialog(getDialogOwner());
        if (chosen != null && chosen.getParent() != null) I18n.setPreference("files.directory", chosen.getParent());
        return chosen;
    }

    /**
     * Öffnet den Dialog zur Auswahl der Zieldatei.
     *
     * @return ausgewählte Zieldatei oder {@code null}, wenn der Dialog abgebrochen wurde
     */
    private void initialDirectory(FileChooser chooser) {
        String saved = I18n.preference("files.directory", "");
        File directory = editor.getCurrentFile().map(File::getAbsoluteFile).map(File::getParentFile).orElse(saved.isBlank() ? null : new File(saved));
        if (directory != null && directory.isDirectory()) chooser.setInitialDirectory(directory);
    }

    protected File chooseFileToSave() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.text("file.save.title"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(I18n.text("file.filter"), "*.md"));
        initialDirectory(chooser);
        chooser.setInitialFileName(editor.getCurrentFile().map(File::getName).orElse("document.md"));
        File chosen = chooser.showSaveDialog(getDialogOwner());
        if (chosen != null) {
            String name = chosen.getName();
            if (!name.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(md|markdown)$")) {
                chosen = new File(chosen.getParentFile(), name + ".md");
                if (chosen.exists()) {
                    Alert overwrite = new Alert(Alert.AlertType.CONFIRMATION, chosen.getAbsolutePath(), de.richmarkdown.ui.dialogs.DialogButtons.OK, de.richmarkdown.ui.dialogs.DialogButtons.CANCEL);
                    overwrite.initOwner(getDialogOwner()); overwrite.setHeaderText(I18n.text("file.save.title"));
                    de.richmarkdown.ui.dialogs.DialogButtons.applyDialogStyles(overwrite.getDialogPane());
                    if (overwrite.showAndWait().orElse(de.richmarkdown.ui.dialogs.DialogButtons.CANCEL) != de.richmarkdown.ui.dialogs.DialogButtons.OK) return null;
                }
            }
            if (chosen.getParent() != null) I18n.setPreference("files.directory", chosen.getParent());
        }
        return chosen;
    }
}
