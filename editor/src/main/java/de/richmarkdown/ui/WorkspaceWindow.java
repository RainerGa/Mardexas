package de.richmarkdown.ui;

import de.richmarkdown.io.ExternalFileMonitor;
import de.richmarkdown.io.WorkspaceSession;
import de.richmarkdown.i18n.I18n;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/** Tab host. Each document retains its own control, undo history, selection and read-only state. */
public final class WorkspaceWindow extends Stage {
    private final TabPane tabs = new TabPane();
    private final BorderPane root = new BorderPane(tabs);
    private Tab sideTab;
    private javafx.scene.Node sideContent;
    private final Map<Tab, EditorWindow> documents = new HashMap<>();
    private final Map<EditorWindow, String> diskContents = new HashMap<>();
    private final Map<EditorWindow, PauseTransition> pendingChecks = new HashMap<>();
    private final ArrayDeque<Path> closedFiles = new ArrayDeque<>();
    private final WorkspaceSession session;
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mardexas-workspace-io"); thread.setDaemon(true); return thread;
    });
    private final java.util.concurrent.ConcurrentMap<EditorWindow, Path> queuedChecks = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean checking = new java.util.concurrent.atomic.AtomicBoolean();
    private ExternalFileMonitor monitor;
    private volatile boolean disposed;

    public WorkspaceWindow() { this(new WorkspaceSession(FeatureUi.profileDirectory().resolve("workspace.properties"))); }
    public WorkspaceWindow(WorkspaceSession session) {
        this.session = session;
        setTitle("Mardexas");
        setScene(new Scene(root, 1100, 780));
        getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN), this::closeActive);
        getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN), this::newDocument);
        getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN,
                KeyCombination.SHIFT_DOWN), this::reopenClosed);
        getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.TAB, KeyCombination.CONTROL_DOWN), () -> selectRelative(1));
        getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.TAB, KeyCombination.CONTROL_DOWN,
                KeyCombination.SHIFT_DOWN), () -> selectRelative(-1));
        tabs.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab == sideTab) closeSideBySide();
            EditorWindow document = documents.get(newTab);
            if (document != null) { setTitle(document.getTitle()); document.getEditorControl().requestFocus(); }
        });
        setOnCloseRequest(event -> { if (!closeWorkspace()) event.consume(); });
        setOnHidden(event -> dispose());
        try {
            monitor = new ExternalFileMonitor(path -> Platform.runLater(() -> changed(path)));
        } catch (IOException failure) { Platform.runLater(() -> FeatureUi.error(this, failure)); }
    }

    public EditorWindow newDocument() {
        if (documents.size() >= WorkspaceSession.MAX_DOCUMENTS) throw new IllegalStateException("Too many open documents");
        EditorWindow editor = new EditorWindow(this);
        editor.initOwner(this);
        editor.attachWorkspace(this);
        Parent content = editor.getScene().getRoot();
        var styles = new ArrayList<>(editor.getScene().getStylesheets());
        editor.getScene().setRoot(new BorderPane());
        content.getStylesheets().addAll(styles);
        Tab tab = new Tab(editor.getTitle(), content);
        documents.put(tab, editor);
        editor.titleProperty().addListener((observable, oldTitle, title) -> {
            tab.setText(title);
            if (tabs.getSelectionModel().getSelectedItem() == tab) setTitle(title);
        });
        tab.setOnCloseRequest(event -> { if (!editor.mayCloseDocument()) event.consume(); });
        tab.setOnClosed(event -> remove(tab));
        tabs.getTabs().add(tab);
        tabs.getSelectionModel().select(tab);
        root.applyCss();
        return editor;
    }

    public EditorWindow openFile(File file) {
        Path path = file.toPath().toAbsolutePath().normalize();
        for (var entry : documents.entrySet()) {
            File current = entry.getValue().getCurrentFile();
            if (current != null && current.toPath().toAbsolutePath().normalize().equals(path)) {
                tabs.getSelectionModel().select(entry.getKey()); return entry.getValue();
            }
        }
        EditorWindow active = activeDocument();
        EditorWindow document = active != null && active.getCurrentFile() == null && !active.getEditor().isModified()
                ? active : newDocument();
        document.openFileInPlace(file);
        return document;
    }

    public EditorWindow activeDocument() { return documents.get(tabs.getSelectionModel().getSelectedItem()); }
    public int documentCount() { return documents.size(); }
    public void selectRelative(int offset) {
        if (!tabs.getTabs().isEmpty()) tabs.getSelectionModel().select(Math.floorMod(
                tabs.getSelectionModel().getSelectedIndex() + offset, tabs.getTabs().size()));
    }
    public boolean closeActive() {
        Tab tab = tabs.getSelectionModel().getSelectedItem();
        if (tab == null || !documents.get(tab).mayCloseDocument()) return false;
        tabs.getTabs().remove(tab); remove(tab); return true;
    }
    public void reopenClosed() { if (!closedFiles.isEmpty()) openFile(closedFiles.pop().toFile()); }

    /** Displays the next document beside the active tab without copying either document model. */
    public boolean showSideBySide() {
        closeSideBySide();
        if (tabs.getTabs().size() < 2) return false;
        int index = tabs.getSelectionModel().getSelectedIndex();
        sideTab = tabs.getTabs().get((index + 1) % tabs.getTabs().size());
        sideContent = sideTab.getContent();
        sideTab.setContent(null);
        var close = FeatureUi.button(FeatureUi.text("Ansicht schließen", "Close side view"),
                FeatureUi.text("Das zweite Dokument wieder als Tab anzeigen", "Return the second document to its tab"), this::closeSideBySide);
        var pane = new BorderPane(sideContent);
        pane.setTop(new HBox(10, new Label(sideTab.getText()), close));
        var split = new javafx.scene.control.SplitPane(tabs, pane);
        split.setDividerPositions(0.5);
        root.setCenter(split);
        return true;
    }
    public boolean isSideBySide() { return sideTab != null; }
    public void closeSideBySide() {
        if (sideTab == null) return;
        if (root.getCenter() instanceof javafx.scene.control.SplitPane split) {
            if (split.getItems().size() > 1 && split.getItems().get(1) instanceof BorderPane pane) pane.setCenter(null);
            split.getItems().clear();
        }
        sideTab.setContent(sideContent);
        sideTab = null; sideContent = null;
        root.setCenter(tabs);
    }

    private void remove(Tab tab) {
        if (tab == sideTab) closeSideBySide();
        EditorWindow editor = documents.remove(tab);
        if (editor == null) return;
        File file = editor.getCurrentFile();
        if (file != null) {
            closedFiles.push(file.toPath());
            while (closedFiles.size() > 20) closedFiles.removeLast();
        }
        PauseTransition timer = pendingChecks.remove(editor);
        if (timer != null) timer.stop();
        diskContents.remove(editor);
        queuedChecks.remove(editor);
        editor.disposeFeatures();
        refreshWatches();
        if (documents.isEmpty() && !disposed) newDocument();
    }

    public void documentSaved(EditorWindow document) {
        File file = document.getCurrentFile();
        if (file == null) return;
        try { diskContents.put(document, fingerprint(file.toPath())); }
        catch (IOException failure) { FeatureUi.error(this, failure); }
        document.showExternalChangeNotice(null);
        refreshWatches();
    }

    private void refreshWatches() {
        if (monitor == null || disposed) return;
        HashSet<Path> paths = new HashSet<>();
        for (EditorWindow document : documents.values()) {
            File file = document.getCurrentFile(); if (file != null) paths.add(file.toPath());
        }
        try { monitor.setFiles(paths); } catch (IOException failure) { FeatureUi.error(this, failure); }
    }

    private void changed(Path path) {
        if (disposed) return;
        for (EditorWindow document : documents.values()) {
            File file = document.getCurrentFile();
            if (file == null || !path.equals(file.toPath().toAbsolutePath().normalize())) continue;
            PauseTransition timer = pendingChecks.computeIfAbsent(document, ignored -> new PauseTransition(Duration.millis(300)));
            timer.setOnFinished(event -> queueExternalCheck(document, path));
            timer.playFromStart();
        }
    }

    private void queueExternalCheck(EditorWindow document, Path path) {
        if (disposed) return;
        queuedChecks.put(document, path);
        startChecks();
    }
    private void startChecks() {
        if (io.isShutdown() || !checking.compareAndSet(false, true)) return;
        io.submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted() && !queuedChecks.isEmpty()) {
                    for (var entry : queuedChecks.entrySet()) {
                        if (!queuedChecks.remove(entry.getKey(), entry.getValue())) continue;
                        try {
                            String hash = Files.exists(entry.getValue()) ? fingerprint(entry.getValue()) : null;
                            Platform.runLater(() -> applyExternalFingerprint(entry.getKey(), entry.getValue(), hash));
                        } catch (IOException failure) { Platform.runLater(() -> { if (!disposed) FeatureUi.error(this, failure); }); }
                    }
                }
            } finally {
                checking.set(false);
                if (!queuedChecks.isEmpty()) startChecks();
            }
        });
    }

    public void applyExternalChange(EditorWindow document, Path path, String content) {
        applyExternalFingerprint(document, path, content == null ? null : fingerprint(content));
    }
    private void applyExternalFingerprint(EditorWindow document, Path path, String hash) {
        if (disposed || !documents.containsValue(document) || document.getCurrentFile() == null
                || !document.getCurrentFile().toPath().toAbsolutePath().normalize().equals(path)) return;
        if (java.util.Objects.equals(diskContents.get(document), hash)) return;
        document.showExternalChangeNotice(hash == null
                ? FeatureUi.text("Die Datei wurde außerhalb von Mardexas gelöscht.", "The file was deleted outside Mardexas.")
                : FeatureUi.text("Die Datei wurde außerhalb von Mardexas geändert.", "The file was changed outside Mardexas."));
    }

    public boolean confirmExternalOverwrite(EditorWindow document, File destination) {
        File current = document.getCurrentFile();
        if (current == null || !current.toPath().toAbsolutePath().normalize().equals(destination.toPath().toAbsolutePath().normalize())) return true;
        try {
            String content = Files.exists(destination.toPath()) ? fingerprint(destination.toPath()) : null;
            if (java.util.Objects.equals(content, diskContents.get(document))) return true;
        } catch (IOException failure) { FeatureUi.error(this, failure); return false; }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                FeatureUi.text("Die Datei wurde extern geändert. Mit dem Editorinhalt überschreiben?",
                        "The file changed externally. Overwrite it with the editor contents?"), ButtonType.YES, ButtonType.CANCEL);
        alert.initOwner(this);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.YES;
    }

    private static String fingerprint(String content) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String fingerprint(Path path) throws IOException {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] bytes = new byte[8192]; int read;
                while ((read = input.read(bytes)) >= 0) if (read > 0) digest.update(bytes, 0, read);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public void restoreSession() {
        try {
            WorkspaceSession.State state = session.load();
            EditorWindow active = null;
            for (int index = 0; index < state.entries().size(); index++) {
                WorkspaceSession.Entry entry = state.entries().get(index);
                Path path = Path.of(entry.path());
                if (!Files.isRegularFile(path)) continue;
                EditorWindow document = openFile(path.toFile());
                if (index == state.active()) active = document;
                document.setReadOnly(entry.readOnly());
                Platform.runLater(() -> { document.getEditor().goToPosition(entry.caret()); document.restoreScroll(entry.scroll()); });
            }
            if (documents.isEmpty()) newDocument();
            if (active != null) {
                EditorWindow restoredActive = active;
                documents.entrySet().stream().filter(entry -> entry.getValue() == restoredActive)
                        .findFirst().ifPresent(entry -> tabs.getSelectionModel().select(entry.getKey()));
            } else tabs.getSelectionModel().select(Math.min(state.active(), tabs.getTabs().size() - 1));
        } catch (IOException | IllegalArgumentException failure) {
            FeatureUi.error(this, failure); if (documents.isEmpty()) newDocument();
        }
    }

    public WorkspaceSession.State captureSession() {
        ArrayList<WorkspaceSession.Entry> entries = new ArrayList<>(); int active = 0;
        for (Tab tab : tabs.getTabs()) {
            EditorWindow document = documents.get(tab); File file = document.getCurrentFile();
            if (file == null) continue;
            if (document == activeDocument()) active = entries.size();
            entries.add(new WorkspaceSession.Entry(file.getAbsolutePath(), Math.max(0, document.getEditor().getInternalCaretPosition()),
                    document.captureScroll(), document.isReadOnly()));
        }
        return new WorkspaceSession.State(entries, active);
    }

    public boolean closeWorkspace() {
        for (Tab tab : tabs.getTabs()) if (!documents.get(tab).mayCloseDocument()) return false;
        try { session.save(captureSession()); } catch (IOException failure) { FeatureUi.error(this, failure); return false; }
        dispose(); return true;
    }
    public void dispose() {
        if (disposed) return;
        disposed = true;
        closeSideBySide();
        for (PauseTransition timer : pendingChecks.values()) timer.stop();
        for (EditorWindow document : documents.values()) document.disposeFeatures();
        queuedChecks.clear();
        io.shutdownNow();
        if (monitor != null) try { monitor.close(); } catch (IOException failure) { java.util.logging.Logger.getLogger(WorkspaceWindow.class.getName()).log(java.util.logging.Level.WARNING, "Could not close file monitor", failure); }
    }
}
