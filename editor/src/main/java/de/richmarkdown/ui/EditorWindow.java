package de.richmarkdown.ui;

import com.gluonhq.richtextarea.RichTextArea;
import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.ui.dialogs.DialogButtons;
import de.richmarkdown.io.MarkdownFileHandler;
import javafx.application.Platform;
import javafx.print.JobSettings;
import javafx.print.PageLayout;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.animation.PauseTransition;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.text.MessageFormat;

/**
 * Hauptfenster des RichMarkDownEditor mit Menüleiste, Formatierungsleiste,
 * Dokumentansichten, Statusleiste sowie Datei- und Druckaktionen.
 *
 * <p>Das Fenster verbindet die UI-Komponenten mit {@link MarkdownEditor} und
 * {@link MarkdownFileHandler}. Dateioperationen berücksichtigen ungespeicherte
 * Änderungen über einen Dirty-Check. Sichtbarkeit von Dokumentstruktur und
 * Zeilennummern sowie der Zoomfaktor werden über {@link I18n} dauerhaft
 * gespeichert und bei der Fensterinitialisierung wiederhergestellt.
 *
 * <p>Die geschützten Druck- und Dateiauswahlschritte sind überschreibbar, damit
 * Tests und spezialisierte Fensterimplementierungen Systemdialoge und
 * Druckinfrastruktur kontrolliert ersetzen können.
 */
public class EditorWindow extends Stage {

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
    private final MarkdownFileHandler fileHandler = new MarkdownFileHandler();

    private File currentFile;
    private String lastError;
    private ConfirmationHandler confirmationHandler;
    private ZoomController zoomController;
    private DocumentStructureView structureView;
    private LineNumberGutter lineNumberGutter;

    /**
     * Erstellt das vollständige Editorfenster einschließlich Editor, Menüs,
     * Formatierungsleiste und Statusleiste.
     */
    public EditorWindow() {
        this.editor = new MarkdownEditor();
        this.toolBar = new FormatToolBar(editor);
        this.statusBar = new StatusBar(editor);

        MenuBar menuBar = MenuBarFactory.create(this);

        // Zoom: der Controller stellt den Viewport (eigenes Region, keine
        // Zentrierung/Begrenzung) und skaliert den Editor darin
        this.zoomController = new ZoomController(editor.getControl());
        this.zoomController.setZoom(I18n.zoomPercent());
        this.zoomController.getViewport().getStyleClass().add("rme-zoom-viewport");
        statusBar.addZoomControls(this.zoomController.createControls());
        setOnHiding(event -> I18n.setZoomPercent(zoomController.getZoom()));

        // Dokumentstruktur (Navigationsbereich) links vom Editor
        this.structureView = new DocumentStructureView(editor);

        // Zeilennummern links vom Editor (zwischen Struktur und Editor)
        this.lineNumberGutter = new LineNumberGutter(editor.getControl(),
                zoomController.zoomProperty());
        setStructureViewVisible(I18n.documentStructureEnabled());
        setLineNumbersVisible(I18n.lineNumbersEnabled());

        HBox editorRow = new HBox(lineNumberGutter, zoomController.getViewport());
        HBox.setHgrow(zoomController.getViewport(), Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("rme-editor-root");
        root.setTop(new VBox(menuBar, toolBar));
        root.setCenter(editorRow);
        root.setBottom(statusBar);
        root.setLeft(this.structureView);

        setTitle(I18n.text("app.title"));
        Scene scene = new Scene(root, 1000, 700);
        // Anwendungs-Design im Stil von Word/LibreOffice (Phase 13)
        scene.getStylesheets().add(
                getClass().getResource("/de/richmarkdown/ui/app.css").toExternalForm());
        setScene(scene);
        // Druckersystem schon beim ersten Anzeigen vorbereiten
        setOnShowing(event -> warmUpPrintSystem());
        if (System.getenv("RME_DEBUG_LAYOUT") != null) {
            setOnShown(event -> {
                PauseTransition wait = new PauseTransition(Duration.millis(400));
                wait.setOnFinished(e2 -> debugLayout());
                wait.play();
            });
        }
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
     * @return Datei-I/O-Dienst für Markdown-Dateien
     */
    public MarkdownFileHandler getFileHandler() {
        return fileHandler;
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

    /**
     * Liefert die momentan geöffnete Datei oder {@code null}, wenn noch keine
     * Datei zugeordnet ist.
     *
     * @return aktuell geöffnete Datei oder {@code null}
     */
    public File getCurrentFile() {
        return currentFile;
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

    // ---------- Datei-Operationen ----------

    /** Erstellt ein leeres Dokument, sofern der Dirty-Check dies zulässt. */
    public void newDocument() {
        if (!confirmDiscardChanges()) {
            return;
        }
        editor.setMarkdown("");
        currentFile = null;
        updateTitle();
    }

    /**
     * Öffnet die angegebene Datei, sofern ungespeicherte Änderungen verworfen
     * werden dürfen.
     *
     * @param file Markdown-Datei, die geladen werden soll
     */
    public void openFile(File file) {
        if (!confirmDiscardChanges()) {
            return;
        }
        loadFile(file);
    }

    /** Öffnet den Dateiauswahldialog und lädt die ausgewählte Markdown-Datei. */
    public void showOpenDialog() {
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
        if (currentFile == null) {
            showSaveDialog();
        } else {
            saveToFile(currentFile);
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
     *
     * @param file Zieldatei
     */
    public void saveToFile(File file) {
        try {
            fileHandler.write(file, editor.getMarkdown());
            currentFile = file;
            lastError = null;
            updateTitle();
        } catch (IOException e) {
            lastError = MessageFormat.format(I18n.text("error.save"),
                    file.getName(), e.getMessage());
            showError(lastError);
        }
    }

    /** Schließt das Fenster, wenn der Dirty-Check das Verwerfen erlaubt. */
    public void exit() {
        if (confirmDiscardChanges()) {
            close();
        }
    }

    // ---------- Drucken ----------

    /** Diagnose-Log der Druckphasen (nur im Build-Verzeichnis). */
    private static final Path TRACE_LOG = Path.of("target", "print-timing.log");
    private static volatile long printStart;
    private Printer cachedPrinter;

    /**
     * Druckt das Dokument (Datei → Drucken...), wie in Word/LibreOffice:
     * System-Druckdialog, dann seitenweiser Ausdruck der Druckfassung.
     * Ist kein Drucker verfügbar, erscheint ein Hinweis.
     *
     * <p>Vorbereitung (CUPS-Druckersuche) und Systemdialog laufen auf einem
     * Hintergrund-Thread – die Oberfläche bleibt bedienbar. Das ist die
     * ausdrückliche Empfehlung der {@code PrinterJob}-Dokumentation
     * („perform printing on a new thread"). Der Dialog darf keinesfalls auf
     * dem FX-Thread laufen: JavaFX betriebe ihn dort in einer verschachtelten
     * Ereignisschleife, nach deren Ende (Abbrechen) die Menüs der Anwendung
     * nicht mehr bedienbar waren. Nur das Rendern der Druckfassung muss auf
     * dem FX-Thread erfolgen (neue Szene/Stage).
     */
    public void print() {
        printStart = System.currentTimeMillis();
        trace("print gestartet", true);
        Thread thread = new Thread(() -> {
            PrinterJob job = createPrinterJob();
            trace("createPrinterJob", job != null);
            if (job == null) {
                Platform.runLater(() -> {
                    showNoPrinterMessage();
                    trace("showNoPrinterMessage", true);
                });
                return;
            }
            trace("showPrintDialog beginnt", true);
            if (showPrintDialog(job)) {
                trace("showPrintDialog bestaetigt", true);
                Platform.runLater(() -> printDocument(job));
            } else {
                trace("showPrintDialog abgebrochen", false);
                cancelJob(job);
            }
        }, "rme-print");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Systemdialog wurde abgebrochen – der Auftrag wird verworfen. Auch das
     * darf auf dem Hintergrund-Thread geschehen ({@code PrinterJob}-Doku:
     * ein abgebrochener Job „should simply be discarded").
     *
     * @param job abzubrechender Druckauftrag
     */
    protected void cancelJob(PrinterJob job) {
        job.cancelJob();
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
     * Factory für den Druckauftrag (überschreibbar für Tests). Nutzt den
     * ermittelten Drucker ({@link #selectPrinter()}); der gewählte Drucker
     * wird gemerkt – die CUPS-Abfrage kostet pro Aufruf rund eine Sekunde,
     * das Job-Bauen dagegen nur ~20 ms. Fällt der gemerkte Drucker aus,
     * wird neu ermittelt.
     *
     * @return Druckauftrag für den ausgewählten Drucker oder {@code null}, wenn
     *         kein Auftrag erstellt werden kann
     */
    protected PrinterJob createPrinterJob() {
        Printer printer = cachedPrinter;
        if (printer != null) {
            PrinterJob job = PrinterJob.createPrinterJob(printer);
            if (job != null) {
                trace("createPrinterJob aus Cache", true);
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
     * Bereitet das Druckersystem schon beim Fensteraufbau im Hintergrund
     * vor (CUPS-Aufzählung + Job-Bauen ~2 s): Der erste Klick auf
     * „Drucken..." öffnet den Dialog dann ohne Wartezeit.
     */
    private void warmUpPrintSystem() {
        Thread thread = new Thread(() -> {
            Printer printer = selectPrinter();
            if (printer != null) {
                cachedPrinter = printer;
                trace("Warm-up Druckersystem", PrinterJob.createPrinterJob(printer) != null);
            }
        }, "rme-print-warmup");
        thread.setDaemon(true);
        thread.start();
    }

    private static synchronized void trace(String phase, boolean ok) {
        try {
            Files.writeString(TRACE_LOG, String.format(
                    "%s (%b) +%d ms%n", phase, ok,
                    System.currentTimeMillis() - printStart),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // reine Diagnose – kein Funktionsverlust
        }
    }

    /**
     * TEMPORAER (Laufzeitdiagnose): schreibt die Bildschirmkoordinaten der
     * Menüleisten-Schalter und des geöffneten Datei-Menüs nach
     * /tmp/kilo/layout.txt, damit Klicks exakt gesetzt werden können.
     */
    private void debugLayout() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("window %.0f,%.0f %.0fx%.0f%n",
                    getX(), getY(), getWidth(), getHeight()));
            javafx.scene.layout.BorderPane bp =
                    (javafx.scene.layout.BorderPane) getScene().getRoot();
            MenuBar bar = (MenuBar) ((VBox) bp.getTop()).getChildren().get(0);
            Bounds bb = bar.localToScreen(bar.getBoundsInLocal());
            sb.append(String.format("menubar %.0f,%.0f %.0fx%.0f%n",
                    bb.getMinX(), bb.getMinY(), bb.getWidth(), bb.getHeight()));
            for (Node n : bar.lookupAll(".menu-button")) {
                if (n instanceof MenuButton mb) {
                    Bounds b = n.localToScreen(n.getBoundsInLocal());
                    sb.append(String.format("menubutton '%s' %.0f,%.0f %.0fx%.0f%n",
                            mb.getText(), b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight()));
                }
            }
            Menu datei = bar.getMenus().get(0);
            datei.show();
            PauseTransition wait = new PauseTransition(Duration.millis(400));
            wait.setOnFinished(ev -> {
                try {
                    for (Window w : Window.getWindows()) {
                        if (w instanceof PopupWindow pw && pw.getScene() != null) {
                            for (Node n : collectNodes(pw.getScene().getRoot())) {
                                if (n.getStyleClass().contains("menu-item")) {
                                    Bounds b = n.localToScreen(n.getBoundsInLocal());
                                    sb.append(String.format("popupitem '%s' %.0f,%.0f %.0fx%.0f%n",
                                            textOf(n), b.getMinX(), b.getMinY(),
                                            b.getWidth(), b.getHeight()));
                                }
                            }
                        }
                    }
                    datei.hide();
                    Files.writeString(Path.of("/tmp/kilo/layout.txt"), sb.toString());
                } catch (IOException ignored) {
                    // Diagnose only
                }
            });
            wait.play();
        } catch (RuntimeException ex) {
            try (var sw = new java.io.StringWriter();
                 var pw = new java.io.PrintWriter(sw)) {
                ex.printStackTrace(pw);
                Files.writeString(Path.of("/tmp/kilo/layout.txt"), sw.toString());
            } catch (IOException ignored) {
                // Diagnose only
            }
        }
    }

    private static List<Node> collectNodes(Node root) {
        List<Node> all = new ArrayList<>();
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Node n = stack.pop();
            all.add(n);
            if (n instanceof Parent p) {
                p.getChildrenUnmodifiable().forEach(stack::push);
            }
        }
        return all;
    }

    private static String textOf(Node n) {
        StringBuilder sb = new StringBuilder();
        for (Node c : collectNodes(n)) {
            if (c instanceof Text t) {
                sb.append(t.getText()).append(' ');
            }
        }
        return sb.toString().trim();
    }

    /**
     * System-Druckdialog. Bewusst <b>ohne</b> Owner-Fenster: JavaFX sperrt
     * das Owner-Fenster für die Dauer des Dialogs (intern
     * {@code setEnabled(false)}) und gibt es unter Linux (X11/GTK) nicht
     * zuverlässig wieder frei – danach waren die Menüs der Anwendung nicht
     * mehr bedienbar. Ohne Owner entfällt das Sperren.
     *
     * @param job Druckauftrag für den Systemdialog
     * @return {@code true}, wenn der Benutzer den Druckauftrag bestätigt hat
     */
    protected boolean showPrintDialog(PrinterJob job) {
        return job.showPrintDialog(null);
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
        trace("printDocument", true);
        JobSettings settings = job.getJobSettings();
        settings.setJobName(currentFile != null ? currentFile.getName() : "RichMarkDownEditor");
        PageLayout layout = settings.getPageLayout();
        RichTextArea printCopy = editor.createPrintCopy();
        printCopy.setPrefWidth(layout.getPrintableWidth());
        Stage printStage = new Stage();
        printStage.setScene(new Scene(new Pane(printCopy),
                layout.getPrintableWidth(), layout.getPrintableHeight()));
        // Skin ohne sichtbares Fenster aufbauen
        printStage.getScene().getRoot().applyCss();
        editor.loadPrintCopy(printCopy);
        // Die Bibliothek übernimmt das Dokument asynchron (ein Puls) – Messung
        // und Paginierung folgen danach
        Platform.runLater(() -> Platform.runLater(
                () -> printLoadedCopy(printCopy, job, layout, printStage)));
    }

    private void printLoadedCopy(RichTextArea printCopy, PrinterJob job,
                                 PageLayout layout, Stage printStage) {
        try {
            printCopy.resize(layout.getPrintableWidth(), layout.getPrintableHeight());
            double contentHeight = PrintSupport.measureContentHeight(printCopy);
            printCopy.setPrefHeight(contentHeight);
            printCopy.resize(layout.getPrintableWidth(), contentHeight);
            printCopy.applyCss();
            printCopy.requestLayout();
            printCopy.layout();
            int pages = PrintSupport.pageCount(contentHeight, layout.getPrintableHeight());
            trace("Paginierung", pages > 0);
            for (int page = 0; page < pages; page++) {
                printCopy.setTranslateY(PrintSupport.sliceOffset(page, layout.getPrintableHeight()));
                if (!job.printPage(printCopy)) {
                    trace("Seite " + (page + 1), false);
                    break;
                }
            }
            job.endJob();
            trace("endJob", true);
        } finally {
            printStage.close();
        }
    }

    // ---------- Interna ----------

    private void loadFile(File file) {
        try {
            String content = fileHandler.read(file);
            editor.setMarkdown(content);
            currentFile = file;
            lastError = null;
            updateTitle();
        } catch (IOException e) {
            lastError = MessageFormat.format(I18n.text("error.open"),
                    file.getName(), e.getMessage());
            showError(lastError);
        }
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
        alert.setTitle(I18n.text("confirm.discard.title"));
        alert.setHeaderText(null);
        alert.setContentText(I18n.text("confirm.discard"));
        alert.getButtonTypes().setAll(DialogButtons.DISCARD, DialogButtons.KEEP);
        return alert.showAndWait()
                .map(response -> response == DialogButtons.DISCARD)
                .orElse(false);
    }

    /**
     * Fehleranzeige. Bewusst non-blocking ({@code show()} statt
     * {@code showAndWait()}) – ein modaler Dialog würde in headless-Umgebungen
     * (Tests) den FX-Thread blockieren.
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18n.text("error.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }

    private void updateTitle() {
        String name = currentFile != null ? currentFile.getName() : I18n.text("app.untitled");
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
        return chooser.showOpenDialog(this);
    }

    /**
     * Öffnet den Dialog zur Auswahl der Zieldatei.
     *
     * @return ausgewählte Zieldatei oder {@code null}, wenn der Dialog abgebrochen wurde
     */
    protected File chooseFileToSave() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.text("file.save.title"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(I18n.text("file.filter"), "*.md"));
        return chooser.showSaveDialog(this);
    }
}
