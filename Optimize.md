# Optimize.md – Analyse und Optimierungsplan des RichMarkDownEditor (Mardexas) v1.0.0

**Datum:** 27.10.2026  
**Projekt:** RichMarkDownEditor / Mardexas  
**Version:** 1.0.0  
**Technologie-Stack:** Java 25, JavaFX 25.0.4, Maven, RichTextArea 3.0-RG  
**Basis der Analyse:** Quellcode, Testsuite, Maven-Build-Konfiguration, CI-Workflows (`.github/workflows/`), Packaging-Skripte sowie Projektdokumentation (`README.md`, `CHANGELOG.md`, `AddFeatures.md`, `IMPLEMENTATION_PLAN.md`, `docs/API.md`)

---

## 1. Zusammenfassung & Projektstatus

Das Projekt **RichMarkDownEditor (Mardexas)** ist ein funktional weit fortgeschrittener WYSIWYG-Markdown-Editor für den Desktop auf Basis von JavaFX 25 und einem projektintern gepflegten Fork der Gluon RichTextArea (`3.0-RG`).

Die Architektur ist klar gegliedert:
- **`MarkdownEditor`:** Zentrale Fassade für Dokumentbearbeitung, Textdekoration und dateibezogene I/O-Methoden (als eigenständige Library einbettbar).
- **`EditorWindow`:** UI-Shell mit Menüleiste, Toolbar, Dokumentstruktur-Seitenleiste, Statusleiste und Zeilennummern.
- **`MarkdownParser` / `MarkdownWriter`:** Robuste CommonMark/GFM-Subset-Serialisierung.
- **`FindReplaceEngine` / `FindReplaceDialog`:** Umfangreiche LO-Writer-inspirierte Suchen-und-Ersetzen-Engine.
- **I18n:** Mehrsprachige Unterstützung (10 Sprachen) über ResourceBundles.

### Reale Kernbefunde der aktuellen Analyse:
1. **Kein generelles Test-Versagen, sondern Test-Isolierungsproblem:** Die Unit- und UI-Tests für Line Numbers (`LineNumberGutterTest`) und Drucksupport (`PrintSupportTest`) laufen vollständig fehlerfrei durch. Die Fehler in `EditorWindowTest`, `EditorWindowFeaturesTest` und `EditorWindowFileTest` entstehen dadurch, dass die Testklassen nicht das bereitgestellte `ApplicationSettings.inMemory()` initialisieren, wodurch in isolierten Testumgebungen versucht wird, auf `~/.config/RichMarkDownEditor/settings.properties` zu schreiben.
2. **Ein logischer Testfehler:** In `FindReplaceDialogTest.java` scheitert ein neuer Testfall (`dialog_replaceAllSkipsParagraphBoundary`), da er die Ziffer `"0"` im Statuslabel erwartet, der Dialog bei 0 Treffern jedoch die lokalisierte Meldung *"Keine Übereinstimmung gefunden"* anzeigt.
3. **Fehlende CI-Pipeline für PRs:** Automatisierte Testläufe existieren derzeit nur im Release-Workflow (`release-packages.yml`). Für Pull Requests und reguläre Pushes auf `main` fehlt ein schlanker Test-Workflow.
4. **Fehlende statische Codeanalyse & Code-Style-Vorgaben:** Es fehlen Checkstyle, SpotBugs sowie eine einheitliche `.editorconfig`.
5. **Kein Encoding-Bug:** Entgegen früherer Notizen ist die Einstellungsverwaltung in `ApplicationSettings` bereits vollständig auf UTF-8 über `Files.newBufferedReader` / `BufferedWriter(..., StandardCharsets.UTF_8)` umgestellt; `Properties.load(Reader)` arbeitet standardkonform mit UTF-8.

---

## 2. Kritische Handlungsfelder (Sofortbedarf)

### 2.1 Test-Isolierung und Behebung von Testfehlern

| Handlungsfeld | Detail | Betroffene Dateien | Priorität |
| :--- | :--- | :--- | :--- |
| **Test-Setup-Isolierung von Benutzereinstellungen** | Tests greifen auf die Standard-Einstellungsdatei unter `~/.config/` zu. In Sandbox-, Container- oder Read-Only-Umgebungen führt dies zu `FileSystemException` / `UncheckedIOException`. | `EditorWindowTest.java`<br>`EditorWindowFeaturesTest.java`<br>`EditorWindowFileTest.java` | **Kritisch** |
| **Behebung `FindReplaceDialogTest`** | `dialog_replaceAllSkipsParagraphBoundary` erwartet fälschlich `"0"` im Statusstring (`assertTrue(status.contains("0"))`), während `FindReplaceDialog` bei 0 Ersetzungen und 0 Überspringungen `dialog.findReplace.noMatch` (*"Keine Übereinstimmung gefunden"*) anzeigt. | `FindReplaceDialogTest.java:281` | **Hoch** |
| **Aktualisierung `CHANGELOG.md`** | Der Abschnitt *Known issues* in `CHANGELOG.md` listet noch 5 historische Testfehler aus Version 1.0.0-rc auf, die in den Unit-Tests (`LineNumberGutterTest`, `PrintSupportTest`) längst behoben sind. | `CHANGELOG.md` | **Mittel** |

**Maßnahmen:**
1. In den `@BeforeAll`-Methoden von `EditorWindowTest`, `EditorWindowFeaturesTest` und `EditorWindowFileTest` zwingend vor allen UI-Initialisierungen aufrufen:
   ```java
   I18n.setSettingsStore(ApplicationSettings.inMemory());
   ```
2. In `FindReplaceDialogTest.java` Zeile 281 die Assertion an die tatsächliche i18n-Ausgabe anpassen oder auf `I18n.text("dialog.findReplace.noMatch")` prüfen.
3. Dokumentation in `CHANGELOG.md` mit dem tatsächlichen Teststatus synchronisieren.

---

### 2.2 Fehlende CI-Pipeline für Pull Requests und Pushes

| Handlungsfeld | Detail | Betroffene Datei | Priorität |
| :--- | :--- | :--- | :--- |
| **Kein Pull-Request-Build** | `.github/workflows/` enthält nur `release-packages.yml` und `pages.yml`. Fehlerhafter Code in PRs wird vor dem Merge nicht automatisch durch `mvn test` validiert. | Neu: `.github/workflows/ci.yml` | **Kritisch** |

**Maßnahmen:**
- Erstellung einer schlanken GitHub Actions Workflow-Datei `.github/workflows/ci.yml`:
  - Trigger auf `push` (Branches: `main`) und `pull_request` (Target: `main`).
  - Matrix-Build auf Linux (mit Xvfb oder Monocle headless), Windows und macOS.
  - Ausführung von `mvn clean test`.

---

## 3. Hohe Priorität (Code-Qualität, Robustheit & Build)

### 3.1 Fehlerbehandlung und Robustheit

| Bereich | Problem & Risiko | Betroffene Datei | Lösung |
| :--- | :--- | :--- | :--- |
| **CSS-Ressourcenladung** | `getClass().getResource("/de/richmarkdown/ui/app.css").toExternalForm()` wirft eine NPE, sollte die CSS-Ressource im Classpath/JAR fehlen. | `EditorWindow.java:181` | Defensiver Null-Check mit Logging oder Fallback, analog zur Absicherung in `FindReplaceDialog.java:121`. |
| **Generics in PrintSupport** | `lookup(".paragraph-list-view")` wird ungeprüft auf `ListView<Node>` gecastet, obwohl `cellAtIndex` mit `ListView<?>` arbeitet. | `PrintSupport.java:57` | Cast durch `ListView<?>` ersetzen; das vermeidet Compiler-Warnungen und unnötige Typannahmen. |
| **Logging-Inkonsistenz** | Trace- und Fehlerausgaben erfolgen teils über `System.out`, `System.err` oder ad-hoc Logging-Pfade (`target/print-timing.log`). | Projektweit (`EditorWindow`, `PrintSupport`) | Einführung einer einheitlichen Logging-Fassade (SLF4J mit simplelogger oder logback). |

---

### 3.2 Build-Integrität & Statische Analyse

| Werkzeug / Konfiguration | Zielsetzung | Betroffene Datei |
| :--- | :--- | :--- |
| **`.editorconfig`** | Festlegung von UTF-8, LF-Zeilenumbrüchen, 4 Spaces Einrückung und Trim-Whitespace für alle Java-, XML- und Markdown-Dateien. | Neu: `.editorconfig` |
| **Checkstyle Plugin** | Automatisierte Einhaltung von Codierungsstandards und Imports-Sortierung im Maven-Build. | `editor/pom.xml` |
| **SpotBugs Plugin** | Erkennung potenzieller Bugs (Ressourcen-Leaks, NPE-Risiken, fehlerhafte Vergleiche) bei `mvn verify`. | `editor/pom.xml` |
| **JaCoCo Plugin** | Messung und Berichterstattung der Testabdeckung; Festlegung eines Mindest-Schwellenwerts (z. B. 75 % Line Coverage). | `editor/pom.xml` |

---

## 4. Mittlere Priorität (Architektur & Wartbarkeit)

### 4.1 Entflechtung von `EditorWindow`
`EditorWindow` umfasst derzeit rund 1.100 Zeilen und bündelt mehrere Verantwortlichkeiten:
- UI-Layout und Toolbar-/Menü-Verdrahtung
- Datei-Operationen (Öffnen, Speichern, Dirty-Handling, Recent-Files)
- Druck-Lifecycle und Druck-Dialog-Koordination
- Fenstertitel- und Statusverwaltung

**Empfehlung:**
- Auslagerung der Dateilogik in einen `EditorFileController` (bzw. `FileActionHandler`).
- Auslagerung der Druck-Koordination in einen dedizierten `PrintJobController`.
- `EditorWindow` verbleibt als reine Layout-Shell.

### 4.2 Konsistenz von UI-Konstanten & Styling
- **`CaretStyle.java` & `ToolbarIcons.java`:** Numerische Dimensionen (Icon-Größen, Cursor-Breiten) als klar dokumentierte Konstanten definieren, um zukünftige HiDPI-Skalierungen zu erleichtern.
- **`MarkdownStyles.java`:** Farb- und Größenwerte (z. B. für Zitate, Codeblöcke und Überschriften) bündeln, um künftige Theming-Möglichkeiten (Dark Mode) vorzubereiten.
- **Javadoc-Vervollständigung:** Fehlende Param- und Return-Tags in öffentlichen Methoden von `MarkdownEditor` und `I18n` ergänzen.

---

## 5. Plattformen & Verpackung

### 5.1 Flatpak-Berechtigungen
- **Status:** Die Flatpak-Konfiguration (`packaging/flatpak/io.github.rainerga.Mardexas.yml`) nutzt `--filesystem=home`.
- **Bewertung:** Dies ist für einen vollwertigen Desktop-Texteditor, der Dokumente aus beliebigen Verzeichnissen (`~/Dokumente`, `~/Projekte`, etc.) öffnen und speichern können muss, die Standardkonfiguration, solange Dateidialoge nicht vollständig über XDG Desktop Portals abgebildet werden.
- **Empfehlung:** Berechtigung `--filesystem=home` beibehalten. Erst bei einer eventuellen Migration auf reine Flatpak-Portal-Dateiauswahl (`org.freedesktop.portal.FileChooser`) prüfen, ob die Berechtigung reduziert werden kann.

### 5.2 GraalVM Native Image Reachability Metadata
- **Status:** Reachability-Metadaten liegen unter `packaging/native-image/` für Linux, Windows und macOS vor.
- **Empfehlung:** Bei jeder Erweiterung der Kernklassen oder Bibliotheken die Trace-Generierung (`packaging/trace-reachability.sh`) ausführen und die Diffs per `packaging/trace-diff.py` verifizieren. Trace-Validierung in den Release-Workflow einbinden.

---

## 6. Feature-Roadmap (Abgleich mit AddFeatures.md & IMPLEMENTATION_PLAN.md)

### 6.1 Bereits erfolgreich implementiert (Stufe 1 & Kern-Features)
Folgende Funktionen aus `AddFeatures.md` und früheren Plänen sind bereits vollständig umgesetzt und getestet:
- **Dokumentstatistik:** Live-Zählung in der Statusleiste (`StatusBar`) sowie detaillierter Statistik-Dialog (`DocumentStatistics`, `StatisticsDialog`).
- **Navigationshistorie:** Vor- und Zurückspringen an letzte Bearbeitungspositionen (`NavigationHistory`, Toolbar-Buttons).
- **Schreibgeschütztes Öffnen:** Modus für reine Ansicht ohne Bearbeitungsmöglichkeit (`EditorWindow.setReadOnly`).
- **Zeilenumbruch-Modus:** Umschaltung zwischen automatischem und festem Umbruch (`LineWrapMode`).
- **Zuletzt geöffnete Dateien:** Recent-Files-Menü im Datei-Menü mit Persistenz (`ApplicationSettings.readRecentFiles`).
- **Suchen & Ersetzen:** Vollständiger LO-Writer-kompatibler Dialog mit RegEx, Ganzwort, Treffermarkierung (`FindReplaceEngine`, `FindReplaceDialog`).

### 6.2 Nächste Meilensteine (Stufe 2 & 3)

| Meilenstein / Feature | Herkunft | Beschreibung & Nutzen | Aufwand |
| :--- | :--- | :--- | :--- |
| **Autospeichern & Crash-Recovery** | `AddFeatures.md` #15 | Periodisches Speichern in ein temporäres Schattenverzeichnis (`~/.cache/Mardexas/recovery/`) zur Wiederherstellung bei Absturz. | Mittel |
| **Formatierungsfehler-Prüfer** | `AddFeatures.md` #16 | Heuristische Prüfung auf ungeschlossene Formatierungsbereiche oder inkonsistente Tabellenstrukturen. | Mittel |
| **Listen mit Startnummer** | `IMPLEMENTATION_PLAN.md` #9 | Unterstützung für Markdown-Listen, die mit einer Ziffer ungleich 1 beginnen (z. B. `5. Punkt`). | Mittel |
| **Verschachtelte Blockzitate** | `IMPLEMENTATION_PLAN.md` #10 | Mehrstufige Zitate (`>> Verschachtelt`) im Parser und Editor abbilden. | Mittel bis Hoch |
| **Dokumentenvorlagen** | `AddFeatures.md` #17 | Auswählen vordefinierter Markdown-Muster (Protokoll, Notiz, technischer Bericht) bei „Neu aus Vorlage...“. | Gering bis Mittel |
| **Tabellen-Spaltenbreite & Zellausrichtung** | `AddFeatures.md` #21 | Feingliedrige Steuerung der Tabellenausrichtung (Links, Zentriert, Rechts) visuell im Dialog bearbeiten. | Hoch (RTA-Abhängigkeit) |

---

## 7. Priorisierter Aktionsplan

### Phase 1: Test-Stabilität & Isolierung (Sofort) — ✅ erledigt
1. **Test-Setup absichern:** `I18n.setSettingsStore(ApplicationSettings.inMemory())` in `@BeforeAll` von `EditorWindowTest`, `EditorWindowFeaturesTest`, `EditorWindowFileTest` (plus `AppDesignTest`, `MenuAcceleratorTest`) hinzugefügt. `rme.config.dir`-Surefire-Property isoliert den I18n-Default-Store, ohne `ApplicationSettings.defaultConfigDirectory()` zu verändern.
2. **`FindReplaceDialogTest` korrigiert:** Assertion prüft nun `I18n.text("dialog.findReplace.noMatch")` statt `"0"`.
3. **Maven Testlauf verifiziert:** `mvn test` → BUILD SUCCESS: 580 Tests, 0 Failures, 0 Errors, 7 Skipped; Checkstyle läuft grün durch.
4. **`CHANGELOG.md` bereinigt:** Veraltete "Five UI tests fail"-Notiz unter *Known issues* entfernt.

### Phase 2: CI-Pipeline & Entwickler-Werkzeuge (Woche 1–2) — ✅ erledigt
5. **PR-Workflow erstellt:** `.github/workflows/ci.yml` (Push `main` / Pull Request `main`, Matrix Ubuntu/Windows/macOS mit Java 25, `mvn clean test`).
6. **Code-Style sichergestellt:** `.editorconfig` im Root-Verzeichnis bereits vorhanden (UTF-8, LF, 4 Spaces, Trim-Whitespace).
7. **Statische Analyse:** Checkstyle (`editor/checkstyle.xml`, Projektkonventionen mit Suppressions für i18n-Properties), SpotBugs (`failOnError=false`, `threshold=Medium`) und JaCoCo (75 % Line Coverage) in `editor/pom.xml` eingebunden; Checkstyle an die `validate`-Phase, SpotBugs/JaCoCo an `verify` gekoppelt.
8. **JaCoCo eingerichtet:** Test-Coverage-Report und Mindest-Schwellenwert (75 %) im Maven-Build integriert.

### Phase 3: Code-Qualität & Refactoring (Woche 3–4) — ✅ erledigt
9. **CSS-Laden abgesichert:** Defensiver Null-Check via `loadApplicationStylesheet()` in `EditorWindow.java`; NPE bei fehlender `app.css`-Ressource vermieden.
10. **Generics aufgeräumt:** Unchecked Cast `ListView<?>` → `ListView<Node>` in `PrintSupport.java` entfernt; `ListView<?>` verwendet.
11. **Dokumentation:** `SECURITY.md` hinzugefügt; API-Javadoc in `MarkdownEditor` und `I18n` geprüft und als vollständig bestätigt (@param/@return vorhanden wo nötig).

### Phase 4: Feature-Erweiterungen (Folgeversionen) — offen
12. **Autosave & Wiederherstellung:** gemäß `AddFeatures.md` #15 (nicht umgesetzt).
13. **Listen-Startnummern & verschachtelte Zitate:** Features #9/#10 aus `IMPLEMENTATION_PLAN.md` (nicht umgesetzt).
14. **Vorlagen-System:** Basisvorlagen im Datei-Menü (nicht umgesetzt).

---

## 8. Fazit

Der RichMarkDownEditor (Mardexas) ist architektonisch sauber aufgebaut und verfügt über eine umfangreiche Testsuite und vollständige Kernfeatures. Die zuvor formulierten „Blocker“ beruhten weitgehend auf überholten Dokumentationsständen oder isolierten Test-Konfigurationsdetails.

Mit der Umsetzung der **Phasen 1–3** (Test-Isolierung, PR-CI-Workflow & statische Analyse, Code-Qualität) erreicht das Projekt vollständige Teststabilität und professionelle Qualitätsstandards für Releases und Weiterentwicklungen. **Phase 4** (Feature-Erweiterungen: Autosave, Listen-Startnummern, Vorlagen-System) bleibt für Folgeversionen offen.
