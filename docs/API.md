# Embedding Mardexas in another JavaFX application

Mardexas supplies a bare Markdown control, a complete editor pane, independent
classic/Ribbon surfaces and disposable contributions to a host MenuBar. The
standalone application remains available. All compositions use the same editing
engines and save ordinary UTF-8 Markdown; host settings, tabs and menu choices
are not encoded into the Markdown document.

The library is GPL-3.0 licensed. See [LICENSE](../LICENSE) and
[third-party notices](../THIRD-PARTY-NOTICES.md) for the actual terms and sources.

## Build, dependency and module

Requirements: JDK 25, JavaFX 25.0.4 and the pinned RichTextArea fork. Use a full
checkout including submodules. The fork and offline Noto emoji module are local
Maven artifacts; replacing them with similarly named Central artifacts changes
the implementation and emoji assets.

```sh
git submodule update --init --recursive
mvn verify
mvn -pl integration-demos -am install
```

Application/library artifact: `de.richmarkdown:RichMarkDownEditor:1.1.0`.
Use the **thin** JAR as a dependency, rather than an executable FAT JAR:

```xml
<dependency>
  <groupId>de.richmarkdown</groupId>
  <artifactId>RichMarkDownEditor</artifactId>
  <version>1.1.0</version>
</dependency>
```

```java
module my.novel.application {
    requires RichMarkDownEditor;
}
```

`javafx.controls` and `com.gluonhq.richtextarea` are transitive module requirements.
`java.prefs` is also transitive because the public print API exposes preferences;
`java.desktop` is an internal requirement.
The pinned RichTextArea fork exports its viewmodel package: consumers need no
`--add-exports` or `--add-opens`. Native JavaFX loading on JDK 25 should be enabled
with `--enable-native-access=javafx.graphics`. Compile and run with matching
JavaFX native libraries for the host architecture.

`bash packaging/build-library.sh` creates
`dist/Mardexas-1.1.0-library.zip`, containing the three custom artifacts and their
POMs in `repository/`, generated `javadoc/`, this reference, the complete command
catalog, third-party notices and the executable demo project. Point a Maven
repository at the extracted directory, for example:

```xml
<repositories>
  <repository>
    <id>mardexas-local</id>
    <url>file:///absolute/path/Mardexas-library/repository</url>
  </repository>
</repositories>
```

JavaFX and test dependencies are resolved normally from their declared
repositories. The archive does not bundle a JDK or a universal JavaFX runtime.
The release source archive includes the complete application and fork sources.

## Choose a composition

| Integration | Entry point | Executable demo (`--case=...`) |
| --- | --- | --- |
| Just the text field | `new MarkdownEditor().getControl()` | `BARE` |
| Custom host toolbar / individual actions | `MarkdownEditor`, `EditingCommands` | `TOOLBAR` |
| Complete editor with classic menus | `new EditorPane(CLASSIC)` | `CLASSIC` |
| Complete editor with Ribbon | `new EditorPane(RIBBON)` | `RIBBON` |
| Editor commands inside the host menu | `EditorMenus`, `EditorPane(TOOLBAR)` | `HOST_MENUS` |
| Menus inside each document tab | One `EditorPane` per `Tab` | `TAB_LOCAL` |
| One host menu following the active document tab | `EditorMenus.setActiveEditor(...)` | `TABS_HOST_MENUS` |
| Independent editable and read-only panes | `SplitPane`, `setReadOnly(true)` | `SPLIT_PREVIEW` |
| Commands in a host context menu | `getCommands()`, `EditorCommand` | `CONTEXT_MENU` |
| Editor inside a host-owned dialog | `Dialog`, `EditorPane` | `HOST_DIALOG` |

A docking container is another JavaFX parent: use the same pane and lifecycle
as a tab. For multiple host windows, create separate panes/menu contributions;
a JavaFX Node or MenuItem cannot have two parents. FXML support is deliberately
not required; a host can add these programmatically created nodes from its own
FXML controller without opening library packages for reflection.

The full pane reuses the standalone document shell rather than duplicating its
features. Its internal Stage stays unshown; the displayed scene, dialog owner,
window lifecycle and close policy belong to the host. The bare control does not
construct that shell, Ribbon, live checks or speech service.

## Threading, asynchronous actions and lifecycle

Construct, access and dispose live editors, commands and menus on the **JavaFX
application thread**. `EditorPane` and `EditorMenus` enforce this contract. Pure
engines operating on strings/immutable snapshots can run on host workers; do not
access live JavaFX controls from those workers.

Show/attach the control to a Scene before expecting a skin, geometry, selection
or action execution. `setMarkdown`, open, format, insert and undo use RichTextArea
actions and can complete on subsequent FX pulses. A return from such a method
is not a document-loaded notification. Observe the control's document/text-length
properties or wait for rendered pulses in GUI tests; do not block the FX thread.
Use `MarkdownEditor.openAsync(File)` / `saveAsync(File)` for UI file operations.
Call on FX after skin creation. Reading, parsing, serialization and atomic file
replacement run on a bounded shared worker. Their `CompletableFuture<Void>`
completes on FX after installation/saved-state reconciliation; never `join()` or
`get()` it on FX. A successful save clears dirty only if the captured buffer
revision is still current. An obsolete load completes exceptionally with
`CancellationException`, preserving intervening edits or a replacement document.
`EditorWindow` file commands use this path; `pendingFileOperation()` is its
completion barrier. The legacy `open`/`save` methods retain synchronous CPU/I/O
for compatibility (legacy `open` still queues document installation).

`EditorPane.close()` is idempotent and releases its live checking, speech,
command listeners and UI-mode subscriptions. It does not ask to save, close the
host Stage or call `Platform.exit()`. First apply your save/discard policy using
`mayClose()`; a cancelled close keeps the pane alive. Temporary reparenting does
not require disposal. After close, pane access throws `IllegalStateException`.
A previously retained command registry refuses command execution after close.

Close `EditorMenus` **before** its active pane. On tab changes call
`setActiveEditor(newPane)` (or `null` when none remains); it detaches old bindings
and removes only the menus it contributed. Host menus are retained. Never retain
an active menu binding to a disposed document.

## Bare editor and custom toolbar

```java
MarkdownEditor editor = new MarkdownEditor();
hostPane.setCenter(editor.getControl());
editor.setMarkdown("# Chapter\n\nStart writing.");
Button bold = new Button("Bold");
bold.setOnAction(event -> editor.toggleBold());
```

The alternative `new MarkdownEditor(existingRichTextArea)` adopts the supplied
control, installing the editor's link handling, Enter behavior and shortcuts.
Do not wrap one control in multiple MarkdownEditor instances.

File operations do not open dialogs:

```java
editor.openAsync(file).thenRun(() -> updateHostTitle()); // Completion on FX
editor.saveAsync(file).thenRun(() -> updateHostTitle()); // Error via exceptional completion
editor.open(file);             // Legacy synchronous read/parse, queued installation
editor.save(file);             // Save as / associate target, throws IOException
editor.save();                 // Existing association; otherwise IllegalStateException
editor.getCurrentFile();       // Optional<File>
editor.clear();                // Explicitly empties document and clears association
```

These low-level methods do not prompt before discarding or overwriting. The host
handles that policy and IOException. `getMarkdownSource()` exports the current
content without changing dirty; `getDocumentSnapshot()` returns an immutable,
revision-cached rich-text snapshot for the pure engines. `getMarkdown()` retains
its legacy saved-state side effect and must not be used for previews or recovery.
`getDiskFingerprint()` exposes the hash of the last successful file operation's
actual bytes, including original line endings when reading.
`cancelPendingIo()` invalidates pending load/save UI callbacks when a standalone
host disposes or replaces an editor. Accepted writes still finish in order;
`EditorPane.close()` already invalidates its owned callbacks.
`replaceDocument(...)` / range replacement provide undoable modifications; use
`EditingCommands` for the existing selection/caret-preserving transformations.

Optional independent UI parts remain available: `FormatToolBar`, `StatusBar`,
`ZoomController`, `DocumentStructureView`, `LineNumberGutter`, `ViewModeController`
and reduced `MenuBarFactory.create(editor)`. `EditorWindow` is a **Stage**, not a
BorderPane; use `EditorPane` for a complete embedded surface.

## Full pane and host policy

```java
EditorPane pane = new EditorPane(EditorPane.Presentation.RIBBON);
hostPane.setCenter(pane);
pane.setOutlineVisible(false);
pane.setLineNumbersVisible(true);
pane.setStatusVisible(true);
pane.getEditor().setMarkdown(chapterText);
pane.setErrorHandler(message -> hostStatus.setText(message));
pane.setConfirmationHandler((title, message) -> hostConfirmsDiscard(title, message));
```

`setPresentation` supports `CLASSIC`, `RIBBON`, `TOOLBAR`, `EDITOR_ONLY` without
replacing the document or resetting undo history. Embedded modes are local;
standalone windows continue to follow their shared preference.
`EDITOR_ONLY` hides top chrome; outline, status and line numbers have independent
visibility setters. `setReadOnly(true)` disables document-mutating UI commands;
host code can still intentionally load/update that document with the core API.

Default file/template/print/find/check/diff dialogs resolve the actual host window
when created. Close open auxiliary dialogs before moving an editor to a different
host window: JavaFX dialog owners cannot be reassigned. `setErrorHandler` covers shell file/validation and
uncaught command errors; specialized feature dialogs retain their own validation
UI. For a completely custom interaction policy, override the relevant command or
use the corresponding lower-level service.

```java
var commands = pane.getCommands();
commands.setHandler("menu.save", () -> saveChapterInHost(pane.getEditor()));
commands.setHandler("menu.open", () -> openChapterInHost(pane.getEditor()));
commands.setHandler("menu.new", () -> newChapterInHost(pane.getEditor()));
commands.setHandler("tabs.close", () -> closeDocumentTabIfConfirmed(pane));
commands.setAllowed("tabs.close", true);
```

Toolbar New/Open/Save uses the same dispatcher. `setHandler(id, null)` restores
the original action; `setAllowed(id, false)` applies an additional host gate.
Context/read-only restrictions still apply to overridden commands. Unknown IDs
throw `IllegalArgumentException`. `execute(id)` returns false if unavailable,
closed or the handler fails. `refresh()` updates context immediately when a host
has changed state; regular editor changes already schedule an update.

Embedded `menu.quit`, `menu.minimize`, `menu.maximize`, `menu.closeAll`, and all
`tabs.*` commands are initially blocked. Enable only those implemented by your
host. This avoids closing an unrelated window or offering ineffective tab actions.
The library does not provide a second document manager with implicit host policy.

## Host menus, buttons and active tabs

```java
EditorPane pane = new EditorPane(EditorPane.Presentation.TOOLBAR);
EditorMenus contribution = new EditorMenus(hostMenuBar);
contribution.setActiveEditor(pane);
// On tab change:
contribution.setActiveEditor(selectedPane);  // null removes editor menus
// On host view disposal:
contribution.close();
if (pane.mayClose()) pane.close();
```

`EditorMenus` clones the hierarchy and creates fresh icon nodes, binds command
availability/selection/labels and routes actions to the selected pane. It never
moves the editor's own MenuItems into the host MenuBar. The host decides whether
to offer internal classic/Ribbon mode commands alongside its own menus; block
`ui.mode.classic` / `ui.mode.ribbon` when the host fixes the presentation.

For selected commands in your own toolbar, context menu or command palette, use
`commands.get(id)` and `commands.execute(id)`. `EditorCommand` exposes read-only
label, description, enabled, visible, selected and shortcut properties plus a
stable `id()` / `iconId()`. `CommandIcons.create(id, size)` creates an independent
MIT-licensed icon node; `command.explanation()` includes contextual availability
and the shortcut. Bind button disable to `command.enabledProperty().not()` and
update its tooltip when these properties change. Do not bind a user-toggleable
CheckMenuItem selected property bidirectionally to read-only command state.

See [COMMANDS.md](COMMANDS.md) for every actual command ID, label and shortcut.
The catalog is generated from the registry by the external demo, not maintained
as a second hand-written dispatch implementation.

## Public service map

Generated Javadoc documents public/protected signatures in every exported package.
The following table maps feature families to their reusable entry points. Full
menus, Ribbon and `getCommands()` connect the same services to UI actions.

| Family | Public entry points |
| --- | --- |
| Markdown parsing, writing, formatting, headings, blocks, issues | `markdown.MarkdownParser`, `MarkdownWriter`, `MarkdownModel`, `MarkdownChecker`, `MarkdownStyles`, block/inline types; `editor.MarkdownEditor` |
| Selection, caret, navigation, document statistics | `MarkdownEditor`, `DocumentHeading`, `DocumentStatistics`, `WritingMetrics`, `ui.NavigationHistory` |
| Case, sorting, duplicates, paragraph/chapter operations, focus views | `features.editing.EditingCommands`, `DocumentEdits`, `ChapterModel`, `ChapterFolding`, `WritingViewController` |
| Find/replace, regular expressions, replacement previews, folder search | `findreplace.FindReplaceEngine`, `FolderSearch`, `io.SearchPresetStore`, search dialogs |
| Tables, cell operations, numeric calculations, delimited import/export | `features.tables.TableData`, `TableDocuments`, `DelimitedTables`, `TableTools` |
| Markdown/live checks, link management and quick fixes | `features.checking.AdvancedMarkdownCheck`, `LiveMarkdownCheck`, `LinkManagement`, `CheckingTools`, `IssueNavigation`, `MarkdownSourceMask` |
| Diff, accept/reject, review undo/redo, comments and review reports | `markdown.TrackChangesEngine`, change/status/view types; `ui.TrackChangesView`, `ReviewPane`, `DiffDialog` |
| Print scope, page selection/layout, print preparation/preview | `features.printing.PrintOptions`, `PrintDocuments`, `PrintOptionsDialog`; `ui.PrintPageView`, `PrintSupport`, full-pane print commands |
| Templates, snippets, dates, special characters and code assistance | `io.MarkdownLibraryStore`, `TemplateFields`, `InsertionContent`; `ui.dialogs.*`, `markdown.CodeEditing` |
| File I/O, autosave, external changes, sessions and speech | `io.MarkdownFileHandler`, `AutoSaveService`, `ExternalFileMonitor`, `WorkspaceSession`, `SpeechReader` |
| Chrome, Ribbon, palettes, accessibility, statistics, credits | `ui.*`, `ui.dialogs.*`; full `EditorPane` commands |
| Settings and language | `settings.ApplicationSettings`, `i18n.I18n` |

For services implementing `AutoCloseable`, the creator owns disposal. File/network
and speech services can perform I/O and propagate their declared failures. Print
requires an OS printer; speech requires the documented platform speech facility.
Snapshots and UI positions use different coordinate spaces when images/tables
are present: use the editor/engine conversion methods rather than assuming every
rendered object occupies the length of its Markdown source.

## Settings, language and persistence

Language/settings remain intentionally process-global. Configure once **before**
constructing editors; per-instance languages are not supported:

```java
I18n.setSettingsStore(ApplicationSettings.inMemory()); // no settings-file writes
// Or: new ApplicationSettings(hostSettingsDirectory)
I18n.setCurrentLanguage("de");
```

Supported language bundles: de, en, es, fr, it, ja, ko, nl, pt-BR, zh-CN.
Some existing extended-feature dialogs still contain German or German/English
labels; a fully localized embedded UI is not claimed by this integration API.
Some services (for example print settings and snippet libraries) have their own
Preferences/user-library persistence. An in-memory I18n store does not sandbox all
I/O: select your own stores or override those commands when the host needs that.

`PrintOptions.save/load` exposes `java.util.prefs.Preferences`. The editor module
therefore requires `java.prefs` transitively; a JPMS host that requires
`RichMarkDownEditor` can use these persistence methods without declaring an
additional `requires java.prefs`.

## Run and verify every demo

After `JAVA_HOME` points to JDK 25, on Linux/macOS:

```sh
bash packaging/build-library.sh
python3 packaging/run-demo.py --case=BARE
python3 packaging/run-demo.py --case=RIBBON
python3 packaging/run-demo.py --case=TABS_HOST_MENUS
```

`run-demo.py` uses the platform path separator and Java executable on all three
operating systems. On Windows, prepare its inputs with Maven:

```powershell
mvn -pl integration-demos -am install
mvn -f editor/pom.xml dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/library-input
python packaging/run-demo.py --case=HOST_MENUS
```

Each case listed above is separately executable. The demo source shows active-tab
switches, close confirmation, host command overrides and independent preview
updates. Its tests run in a separate module against only the exported API.

```sh
mvn verify
python3 -m unittest discover -s packaging -p 'test_*.py'
```

API tests use shown JavaFX stages and real skins/actions, with rendered pulses,
selection, undo, menu switching, read-only checks, dialog ownership and disposal.
The demo tests render all ten compositions. Packaging separately launches the
modular JVM, portable launcher, FAT JAR and native executable and compares rendered
font metrics/pixels. Automated GUI coverage does not establish correctness on an
OS/architecture that has not actually run those checks.
