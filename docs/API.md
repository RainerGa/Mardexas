# Mardexas as a Library (API)

Mardexas remains a standalone WYSIWYG Markdown editor. The same module can
additionally be embedded as a JavaFX library: no `Application` subclass, no
`EditorWindow`, no dialogs are required. All distribution formats (native Linux
build, fat JAR, etc.) are unchanged; `RichMarkDownEditorApp` + `Launcher` +
`editor/pom.xml` + `packaging/*` were left untouched and there is no duplicated
code (file I/O and `currentFile` live only in `MarkdownEditor`).

> License: GPL-3.0 with copyleft. Any derivative that embeds this library must
> comply with GPL-3.0, including source-code and notice requirements. See
> `LICENSE` and `THIRD-PARTY-NOTICES.md`.

## Dependency

No separate artifact: library and application share module/artifact
`de.richmarkdown:RichMarkDownEditor:1.0.0` / module `RichMarkDownEditor`.
The RichTextArea fork `3.0-RG` comes transitively from the reactor/submodule
(no Central release) -- build from a full checkout with submodules.

```xml
<dependency>
  <groupId>de.richmarkdown</groupId>
  <artifactId>RichMarkDownEditor</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Module

```java
requires RichMarkDownEditor;
```

Exports: `de.richmarkdown`, `de.richmarkdown.editor`, `de.richmarkdown.markdown`,
`de.richmarkdown.io`, `de.richmarkdown.ui`, `de.richmarkdown.ui.dialogs`,
`de.richmarkdown.i18n`, `de.richmarkdown.settings`. No `opens` (no FXML/
reflection). Transitive: `javafx.controls` + `com.gluonhq.richtextarea` +
`java.desktop`/`java.prefs`.

Direct `viewmodel` access needs the same `--add-exports` workaround as
`editor/pom.xml`. `ui.dialogs` is exported as part of the default shell
(`LinkDialog`/`TableDialog`/..., usable directly by integrators).

## Minimal embedding

```java
// FX Application Thread, in a shown scene:
MarkdownEditor editor = new MarkdownEditor();
myPane.getChildren().add(editor.getControl());
try {
  editor.open(file);
} catch (IOException e) { /* own error UI */ }
```

`getControl(): RichTextArea` is the single embedding point (FX thread; skin
forms only in a shown scene).

## External control (AP-01)

```java
RichTextArea control = new RichTextArea();
MarkdownEditor editor = new MarkdownEditor(control); // null -> NPE
```

The passed control is fully adopted: link callback with schema allowlist
(RME-002), Enter rules, Ctrl+U accelerator -- same as the no-arg constructor
(`this(new RichTextArea())`, shared `initControl()`).

## File API (AP-02, no dialogs)

```java
editor.open(file);            // UTF-8, \n-normalized; throws IOException
editor.save(file);            // UTF-8; throws IOException
editor.save();                // uses associated file; throws IllegalStateException if none
Optional<File> current = editor.getCurrentFile(); // never null
editor.clear();               // empties document, resets file (no confirm)
```

No `saveAs`, no dialog interface: "Save as" is just `save(File)` with a new
file. Errors propagate as `IOException`/`IllegalStateException` (no `Alert`, no
`I18n` in the core). Loading is async via the ActionFactory like
`setMarkdown`; the saved-state resets one pulse later. Dirty check,
`ConfirmationHandler`, `FileChooser`, `Alert`/`lastError`/title stay in
`EditorWindow`, which only delegates (`editor.open/save/clear`,
`editor.getCurrentFile()`).

## Global configuration (AP-04/AP-05)

`I18n` stays a process-global singleton (no per-window locales by design):
set the language once at startup.

```java
I18n.setCurrentLanguage("en"); // unsupported -> IllegalArgumentException
I18n.setSettingsStore(new ApplicationSettings(customDir)); // own directory
I18n.setSettingsStore(ApplicationSettings.inMemory());     // no persistence
I18n.setSettingsStore(null); // same as inMemory()
I18n.resetSettingsStore();   // platform default (for tests)
```

## Menus, toolbar and `BorderPane` embedding

The UI parts a library user needs are exported (`de.richmarkdown.ui`) and are
built against `MarkdownEditor`, not against `EditorWindow`. You can therefore
assemble the complete editing surface yourself in any layout. `EditorWindow`
itself is just a `BorderPane` (menus+toolbar on top, editor in the center,
status bar at the bottom) -- the example below is the same composition inside
your own scene.

What the library gives you:

| Part | Construction | Notes |
| --- | --- | --- |
| Format/bulk actions | `MarkdownEditor` methods (`toggleBold`, `applyHeading`, `toggleBulletList`, `indent`, ...) | no dialogs, no exceptions for normal use |
| `FormatToolBar` | `new FormatToolBar(editor)` | format toggles + style choice; new/open/save buttons need handlers |
| `StatusBar` | `new StatusBar(editor)` | caret/paragraph info, optional zoom controls |
| `ZoomController` | `new ZoomController(editor.getControl())` | zoom viewport + controls |
| `DocumentStructureView` | `new DocumentStructureView(editor)` | heading navigation |
| `LineNumberGutter` | `new LineNumberGutter(control, zoom)` | per-paragraph line numbers |
| Edit/Insert menu | `MenuBarFactory.create(editor)` | reduced menu, always available |
| File/View/Format/... menus | **not** available without a window | build your own from the editor API (below) |
| Dialogs | `de.richmarkdown.ui.dialogs` (`LinkDialog`, `TableDialog`, ...) | optional, used by the menus |

```java
import de.richmarkdown.editor.MarkdownEditor;
import de.richmarkdown.i18n.I18n;
import de.richmarkdown.ui.FormatToolBar;
import de.richmarkdown.ui.MenuBarFactory;
import de.richmarkdown.ui.StatusBar;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;

// on the FX Application Thread, inside your started application:
MarkdownEditor editor = new MarkdownEditor();
FormatToolBar toolBar = new FormatToolBar(editor);
StatusBar statusBar = new StatusBar(editor);

// The toolbar's file buttons are handler-based (no dialogs baked in):
toolBar.setNewDocumentHandler(editor::clear);
toolBar.setSaveHandler(this::saveWithDialog);   // your FileChooser logic
toolBar.setOpenFileHandler(this::openWithDialog);

// Reduced Edit+Insert menu straight from the library:
MenuBar menuBar = MenuBarFactory.create(editor);

// Optional: full File menu wired to your own file-chooser logic
Menu file = new Menu(I18n.text("menu.file"));
MenuItem saveItem = new MenuItem(I18n.text("menu.save"));
saveItem.setOnAction(e -> saveWithDialog());
file.getItems().addAll(saveItem);
menuBar.getMenus().add(0, file);

BorderPane root = new BorderPane();
root.setTop(new javafx.scene.layout.VBox(menuBar, toolBar));
root.setCenter(editor.getControl());
root.setBottom(statusBar);

Scene scene = new Scene(root, 900, 700);
scene.getStylesheets().add(getClass()
        .getResource("/de/richmarkdown/ui/app.css").toExternalForm());
```

File actions stay dialog-free in the core, so an integrator supplies the
`FileChooser` and error UI:

```java
private void saveWithDialog() {
    FileChooser chooser = new FileChooser();
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            "Markdown", "*.md", "*.markdown", "*.txt"));
    File target = chooser.showSaveDialog(scene.getWindow());
    if (target == null) {
        return; // cancelled
    }
    try {
        editor.save(target);
    } catch (IOException ex) {
        new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
    }
}
```

`MenuBarFactory.create(editor)` intentionally returns only Edit and Insert:
everything else (File, View, Format, Table, Extras, Window, Help) needs
window-level state (dialogs, dirty-check, zoom, structure visibility) and is
part of the optional `EditorWindow` shell. The formatting/insert actions
themselves are ordinary public methods on `MarkdownEditor`, so building a
custom menu is just wiring `MenuItem.setOnAction` to them.

## Optional default shell

```java
EditorWindow window = new EditorWindow();
MarkdownEditor editor = window.getEditor();
window.show();
```

`EditorWindow` + `FormatToolBar`/`StatusBar`/`ZoomController`/
`LineNumberGutter`/`DocumentStructureView`/dialogs remain the ready-made UI
for standalone use and for integrators who want a finished window.

## Threading

All `MarkdownEditor` methods belong on the FX Application Thread. File I/O
blocks (offload to a `Task` if needed). `getMarkdown()` syncs the view model
and resets the saved state (library behavior) -- `open`/`clear` therefore
queue their `markSaved()` two pulses later to mark exactly the new document.

## Standalone stays

`RichMarkDownEditorApp`, `Launcher`, `editor/pom.xml` (`mainClass` only for
`javafx:run`), and `packaging/*` (jpackage/native-image/fat-jar checks) are
unchanged. Future development builds on this: the standalone editor persists
permanently, the library reuses the same code.
