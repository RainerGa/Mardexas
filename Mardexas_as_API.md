# Mardexas als JavaFX-Bibliothek (API) – Projektplan

**Projekt:** RichMarkDownEditor / Mardexas  
**Ziel:** Die eigenständige JavaFX-Anwendung Mardexas bleibt dauerhaft bestehen. ZUSÄTZLICH soll eine wiederverwendbare JavaFX-Bibliothek zur Integration in andere JavaFX-Anwendungen bereitgestellt werden. Beide Vertriebsformen existieren parallel.  
**Ausgangspunkt:** Version 1.0.0, Java 25, JavaFX 25.0.4, GPL-3.0  
**Erstellt:** 2026-10-16

---

## 1. Zielsetzung

Mardexas soll nicht mehr nur als eigenständige Editor-Anwendung (mit eigenem Hauptfenster, Menüleiste, Dialoge etc.) verfügbar sein, sondern als Bibliothek (API), die Entwickler in ihre eigenen JavaFX-Anwendungen einbetten können. Die Kernfunktionalität (Markdown-Editor auf Basis von `rich-text-area`) bleibt erhalten, wird aber von der Anwendungsschale (Fenster, Menüs, Dateidialoge, Einstellungen) entkoppelt.

---

## 2. Nicht-Ziele (ausgeschlossen)

- Änderung der GPL-3.0-Lizenz
- Entfernung oder Ersetzung der eigenständigen Anwendung (sie bleibt dauerhaft bestehen)
- Unterstützung von FXML (die UI bleibt rein programmatisch in Java)
- Mehrsprachige Instanzen pro Fenster (I18n bleibt global, aber konfigurierbar)

---

## 3. Arbeitspakete (in empfohlener Reihenfolge)

### AP-01: `MarkdownEditor` für externe `RichTextArea` öffnen

**Beschreibung:** Der Konstruktor von `MarkdownEditor` erstellt derzeit immer ein eigenes `RichTextArea`. Ein Integrator muss sein eigenes Control einbringen können.

**Änderungen:**
- Neuer Konstruktor `MarkdownEditor(RichTextArea control)` hinzufügen
- Bestehender parameterloser Konstruktor ruft `this(new RichTextArea())` auf (Convenience)
- Alle internen Verwendungen von `control` bleiben unverändert

**Datei:** `editor/src/main/java/de/richmarkdown/editor/MarkdownEditor.java`

**Aufwand:** ca. 1–2 Stunden

---

### AP-02: Öffentliche Datei-API auf `MarkdownEditor`

**Beschreibung:** Der Integrator soll Dateien öffnen und speichern können, ohne `EditorWindow` zu kennen. Dateiauswahldialoge werden vom Integrator bereitgestellt (Callback-Interface).

**Änderungen:**
- `void open(File file)` – lädt Markdown aus einer Datei
- `void save(File file)` – speichert als Markdown (überschreibt)
- `void saveAs(File file)` – speichert unter neuem Namen
- `Optional<File> getCurrentFile()` – liefert die aktuell geladene Datei
- Interface `FileDialogProvider` (oder ähnlich) für den Integrator, um eigene Dateidialoge einzubinden
- `EditorWindow` wird refaktoriert, um die neue API von `MarkdownEditor` zu nutzen (statt eigener privater Datei-Methoden)

**Dateien:** `editor/src/main/java/de/richmarkdown/editor/MarkdownEditor.java`, `editor/src/main/java/de/richmarkdown/ui/EditorWindow.java`

**Aufwand:** ca. 4–6 Stunden

---

### AP-03: UI-Shell von Kern entkoppeln

**Beschreibung:** `EditorWindow`, `FormatToolBar`, `MenuBarFactory`, `StatusBar`, `ZoomController`, `LineNumberGutter`, `DocumentStructureView` bleiben als optionale Default-Shell, sind aber nicht Teil der Kern-Bibliothek. `MarkdownEditor` darf keine Abhängigkeit zu `EditorWindow` haben.

**Änderungen:**
- Prüfen, ob `MarkdownEditor` indirekt auf UI-Klassen zugreift (falls ja: entfernen)
- `EditorWindow` nutzt `MarkdownEditor` als Komponente (nicht umgekehrt)
- Neue Klasse (z. B. `MarkdownEditorComponent`) die das `RichTextArea`-Control direkt zurückgibt für einfaches Einbetten

**Dateien:** `editor/src/main/java/de/richmarkdown/ui/EditorWindow.java`, `editor/src/main/java/de/richmarkdown/ui/FormatToolBar.java`, `editor/src/main/java/de/richmarkdown/ui/MenuBarFactory.java`, `editor/src/main/java/de/richmarkdown/ui/StatusBar.java`, `editor/src/main/java/de/richmarkdown/ui/ZoomController.java`, `editor/src/main/java/de/richmarkdown/ui/LineNumberGutter.java`, `editor/src/main/java/de/richmarkdown/ui/DocumentStructureView.java`

**Aufwand:** ca. 6–10 Stunden

---

### AP-04: `I18n` konfigurierbar machen

**Beschreibung:** Derzeit sind alle Methoden `static` mit `private static volatile` Feldern. Für eine Bibliothek sollte es möglich sein, Instanzen zu erstellen oder zumindest die Sprache extern zu steuern, ohne den globalen Zustand zu verändern.

**Änderungen:**
- Statische Methoden für Abwärtskompatibilität beibehalten
- Neue Instanz-API: `I18n create()`, `void setLocale(Locale)`, `String text(String key)` als Instanzmethode
- Oder: Eine Instanz-Klasse `I18nBundle` neben dem statischen `I18n`

**Datei:** `editor/src/main/java/de/richmarkdown/i18n/I18n.java`

**Aufwand:** ca. 2–4 Stunden

---

### AP-05: `ApplicationSettings` konfigurierbaren Pfad geben

**Beschreibung:** Derzeit wird hart in `~/.config/RichMarkDownEditor/` (Linux) bzw. `%APPDATA%` (Windows) geschrieben. Eine Bibliothek sollte den Speicherort konfigurierbar machen oder das Speichern ganz deaktivieren können.

**Änderungen:**
- Konstruktor `ApplicationSettings(Path configDir)` hinzufügen
- Methode `setConfigDir(Path)` zur späteren Änderung
- Optional: Möglichkeit, das Speichern zu deaktivieren

**Datei:** `editor/src/main/java/de/richmarkdown/settings/ApplicationSettings.java`

**Aufwand:** ca. 2–3 Stunden

---

### AP-06: `module-info.java` erweitern

**Beschreibung:** Das Paket `de.richmarkdown.ui` ist derzeit nicht exportiert. Ein externes Modul kann UI-Klassen nicht verwenden. Zudem fehlt `opens` für Reflection (z. B. für Binding-Frameworks).

**Änderungen:**
- `exports de.richmarkdown.ui;` hinzufügen
- `exports de.richmarkdown.settings;` hinzufügen
- `exports de.richmarkdown.i18n;` hinzufügen
- `opens de.richmarkdown.ui;` für Reflection
- `opens de.richmarkdown.settings;` für Reflection
- `opens de.richmarkdown.i18n;` für Reflection

**Datei:** `editor/src/main/java/module-info.java`

**Aufwand:** ca. 1 Stunde

---

### AP-07: `pom.xml` als Bibliothek konfigurieren

**Beschreibung:** Das Artefakt ist derzeit auf eine eigenständige Anwendung ausgelegt (`mainClass`, FAT-JAR-Profil, `javafx-maven-plugin` mit Hauptklasse).

**Änderungen:**
- `<mainClass>` entfernen oder optional machen
- `javafx-maven-plugin` Konfiguration anpassen (keine zwingende Hauptklasse für Bibliothek)
- FAT-JAR-Profil als optional markieren (nur für eigenständige Verteilung)
- Dokumentation, wie das Artefakt als Abhängigkeit in andere Projekte eingebunden wird

**Datei:** `editor/pom.xml`

**Aufwand:** ca. 1–2 Stunden

---

### AP-08: Optionale Standalone-Starter-Klasse für die eigenständige Anwendung

**Beschreibung:** Die eigenständige Anwendung bleibt dauerhaft bestehen. Eine separate Starter-Klasse erleichtert den Start als eigenständige Anwendung, ohne die Bibliotheks-Nutzung zu beeinträchtigen.

**Änderungen:**
- Neue Klasse `de.richmarkdown.standalone.RichMarkDownEditorLauncher` extends `Application` für den eigenständigen Start
- `RichMarkDownEditorApp` bleibt unverändert und wird nicht entfernt
- README aktualisieren (Start über Launcher-Klasse oder bestehende App-Klasse)

**Dateien:** Neue Datei `editor/src/main/java/de/richmarkdown/standalone/RichMarkDownEditorLauncher.java`, `editor/src/main/java/de/richmarkdown/RichMarkDownEditorApp.java` (unverändert)

**Aufwand:** ca. 1–2 Stunden

---

### AP-09: Dokumentation und Integrations-Beispiele

**Beschreibung:** Ein `README` für Integratoren mit Beispielcode, wie der Editor in eine eigene JavaFX-Anwendung eingebettet wird.

**Änderungen:**
- Datei `Mardexas_as_API.md` (dieser Plan) als Grundlage
- Beispielcode:
  ```java
  MarkdownEditor editor = new MarkdownEditor();
  scene.setRoot(editor.getControl());
  ```
- Erklärung der Datei-API (open/save)
- Erklärung der I18n-Konfiguration
- Erklärung der Einstellungsspeicherung

**Dateien:** `Mardexas_as_API.md`, ggf. `README.md` (Verweis auf API-Dokumentation)

**Aufwand:** ca. 4–6 Stunden

---

### AP-10: Build und Tests validieren

**Beschreibung:** Nach allen Änderungen muss sichergestellt werden, dass das Projekt weiterhin kompiliert und die bestehenden Tests (soweit möglich) laufen.

**Änderungen:**
- `mvn -pl editor compile` erfolgreich
- `mvn -pl editor test` (sofern Umgebung es zulässt)
- Manuelle Prüfung der API aus Integrator-Sicht

**Aufwand:** ca. 2–3 Stunden

---

## 4. Zeitplan (geschätzt)

| Phase | Arbeitspakete | Geschätzter Aufwand |
|-------|---------------|---------------------|
| 1. Kern-Entkopplung | AP-01, AP-02, AP-03 | ca. 11–18 Stunden |
| 2. Konfiguration | AP-04, AP-05, AP-06 | ca. 5–8 Stunden |
| 3. Build & Verpackung | AP-07, AP-08 | ca. 2–4 Stunden |
| 4. Dokumentation | AP-09 | ca. 4–6 Stunden |
| 5. Validierung | AP-10 | ca. 2–3 Stunden |
| **Gesamt** | | **ca. 24–39 Stunden** |

---

## 5. Risiken und Abhängigkeiten

| Risiko | Auswirkung | Gegenmaßnahme |
|--------|------------|---------------|
| `rich-text-area` Submodul nicht im Arbeitsbereich | Build schlägt fehl | Submodul initialisieren (`git submodule update --init`) |
| `I18n` als globaler Singleton | Integrator kann keine eigene Sprache pro Instanz setzen | Instanz-API neben statischer API beibehalten |
| `EditorWindow` hat private Datei-Methoden | Refaktoring berührt viele Stellen | Sorgfältige Trennung: `MarkdownEditor` erhält Datei-API, `EditorWindow` nutzt sie |
| `module-info.java` Änderungen brechen bestehende Module | Externe Module können nicht mehr auf nicht-exportierte Pakete zugreifen | Alle relevanten Pakete explizit exportieren |
| GPL-3.0 Lizenz | Integratoren müssen GPL-3.0 einhalten | Lizenzhinweis in Dokumentation prominent platzieren |

---

## 6. Abnahmekriterien (Definition of Done)

- [ ] `MarkdownEditor` kann mit einem externen `RichTextArea` instanziiert werden
- [ ] `MarkdownEditor` bietet öffentliche Methoden `open(File)`, `save(File)`, `saveAs(File)`
- [ ] `EditorWindow` nutzt die neue Datei-API von `MarkdownEditor` (keine eigenen privaten Datei-Methoden mehr)
- [ ] `module-info.java` exportiert `de.richmarkdown.ui`, `de.richmarkdown.settings`, `de.richmarkdown.i18n`
- [ ] `pom.xml` baut ohne zwingende `mainClass` als Bibliothek
- [ ] Eine eigenständige Starter-Klasse existiert für den Anwendungsbetrieb
- [ ] `mvn -pl editor compile` ist erfolgreich
- [ ] `Mardexas_as_API.md` enthält vollständige Integrations-Dokumentation mit Beispielcode

---

## 7. Offene Fragen (zur Klärung mit dem Product Owner)

1. Soll die Bibliothek als separates Maven-Artefakt veröffentlicht werden (z. B. `de.richmarkdown:mardexas-api`) oder bleibt sie im selben `editor`-Modul?
2. Soll `I18n` vollständig instanzbasiert werden oder reicht eine statische API mit konfigurierbarem Pfad?
3. Soll die Datei-API einen `FileDialogProvider`-Callback unterstützen, oder öffnet/speichert die Bibliothek direkt ohne Dialog?
4. Soll die eigenständige Anwendung (`RichMarkDownEditorApp`) entfernt oder nur als `@Deprecated` markiert werden?

---

## 8. Anhänge

- Analyse der aktuellen Code-Struktur (siehe vorherige Analyse)
- Liste aller betroffenen Dateien
- Geschätzter Aufwand pro Arbeitspaket
