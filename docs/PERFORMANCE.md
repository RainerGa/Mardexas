# Performance: Messverfahren, Befunde und Refactoring

Stand: 7. November 2026. JavaFX-Desktopeditor, kein Browser/DOM. Die folgenden
Benchmarks sind ausführbare JUnit-Integrationstests mit den vorhandenen
JavaFX-/Monocle-Abhängigkeiten. Sie benötigen keine neue Benchmark-Laufzeit.
Sie ersetzen keine JMH-Mikrobenchmarks für einzelne CPU-Instruktionen.

## Reproduzieren

Aus dem Repository-Hauptverzeichnis mit JDK 25:

```bash
export JAVA_HOME=/home/rainer/.sdkman/candidates/java/25.0.2-graalce
export PATH="$JAVA_HOME/bin:$PATH"
mvn -o --batch-mode --no-transfer-progress -pl editor -am \
  -Dmardexas.build.directory=target/performance-check -Pperformance test
```

Für zusätzliche UI-Stresstests mit 1.000 und 3.000 Nutztextabsätzen denselben
Aufruf mit `-Dperf.stress=true` ergänzen. Diese Fälle können mit dem aktuellen
vollständigen Höhenmesspfad den 60-Sekunden-Timeout überschreiten. Die normale
UI-Messreihe verwendet 200 und 400 Absätze; Pipeline-Messungen reichen immer
bis 3.000 Absätze. Nicht mehr benötigte CSVs vor einem neuen Vergleich in ein
separates Archiv verschieben, damit ältere Stressszenarien nicht mitgezählt werden.

Den lokalen JDK-Pfad gegebenenfalls anpassen. `-o` setzt bereits vorhandene
Maven-Abhängigkeiten voraus. Das relative Build-Verzeichnis wird pro Modul
aufgelöst; ein gemeinsamer absoluter Pfad für alle Reactor-Module würde deren
Modulklassen überschreiben. IDE-Ausgaben bleiben damit separat.

Das Profil wählt ausschließlich `@Tag("performance")`, einen Fork und keine
parallelen Tests. JaCoCo ist für diesen Lauf deaktiviert. Normales `mvn test`
schließt Performance-Tests aus; die Tests für Auswertung und CSV-Ausgabe laufen
weiter regulär. Für die Auswertung des Python-Regressionsvergleichs:

```bash
python3 -m unittest discover -s packaging -p test_compare_performance.py
```

Ergebnisse liegen unter `editor/target/performance-check/performance/`:

* `*-samples.csv`: einzelne Werte mit Metrik, Einheit und Sample-Index.
* `*-summary.csv`: Anzahl, Mittelwert, Median, p95 und Maximum.
* `*.properties`: Zeitstempel, JDK/JavaFX, OS, Renderer, Heap, Prozessorzahl,
  Viewport und Dokumentgröße. Absatzanzahl bei den gemischten Fixtures meint
  Nutztextabsätze; zusätzliche Überschriften und Leerabsätze kommen hinzu.

Vor einem Refactoring diesen Ordner kopieren und die Revisionen von Hauptrepo
und Submodul sowie `git diff` separat festhalten. Nach dem Refactoring mit
identischer Konfiguration messen und vergleichen:

```bash
python3 packaging/compare-performance.py /tmp/performance-before \
  editor/target/performance-check/performance --tolerance 0.20
```

Der Vergleich schlägt bei fehlenden Szenarien/Metriken, geänderten Einheiten,
mehr als 20 % höherer p95-Latenz oder mehr als 20 % niedrigerer Pulsrate fehl.
Zellzahl und Anteil langer Intervalle bleiben Diagnosewerte. Hardware,
Renderer, JDK, Fixture und Hintergrundlast müssen vor dem Vergleich geprüft
werden. Mindestens drei komplette Läufe je Revision durchführen; einzelne
Ausreißer untersuchen, nicht als belastbaren Trend behandeln. Keine universellen
Millisekunden-Grenzen in normalen CI-Tests: Monocle und geteilte Runner schwanken.
Bei drei, fünf oder sieben Samples ist der Nearest-Rank-p95 jeweils das
Maximum; er ist keine statistisch stabile Schätzung des langen Verteilungstails.

## Was gemessen wird

| Test | Arbeitslast | Messgrenze |
| --- | --- | --- |
| `MarkdownPipelinePerformanceTest` | 200/1.000/3.000 Absätze mit Überschriften, Bold, Italic, Code, Strike und Links | Parsing zum Blockmodell, Parsing zum `Document`, `Document` → Markdown jeweils separat, ms/Operation |
| derselbe Test | langer Inline-Absatz, offene Delimiter, 500 Tabellenzeilen, 1.500 Codezeilen | dieselben drei CPU-Operationen mit anderer Tokenstruktur |
| derselbe Test | UTF-8-Lesen/Schreiben einer temporären Datei | ms/Datei, warmer Dateisystemcache, ohne `fsync`/Datenträger-Durabilität |
| `EditorRenderingPerformanceTest` | sichtbares Control, 900 × 600, große formatierte Dokumente | Import inklusive Parsing, Befehlswarteschlange, CSS und nächstem abgeschlossenen Layout |
| derselbe Test | Fett-Toggle einer 100-Zeichen-Selektion | ms bis nach Layout; tatsächlicher Abschluss der asynchronen Formatierung wird geprüft |
| derselbe Test | schnelles Durchlaufen des vertikalen Scrollbalkens in beide Richtungen | 120 Pulsintervalle nach 30 Warmup-Pulsen, Pulsrate, pre→post-Layout-Zeit, synchrone Scrollhandlerzeit, Anteil >33,333 ms |
| derselbe Test | dieselbe Scrollsequenz | stichprobenartige Zahl materialisierter Listenzellen; Assertion gegen Materialisierung des ganzen Dokuments |

CPU-/I/O-Messungen haben drei Warmups und sieben Samples. UI-Import hat einen
Warmup/drei Samples, Formatierung zwei Warmups/fünf Samples. Ergebnisobjekte
werden konsumiert, Roundtrips und Dokumentinhalt geprüft. Keine feste Wartezeit
als Ersatz für das asynchrone Kommandoende: Die Layoutbeobachtung wird nach den
verschachtelten Action-/ViewModel-Warteschlangen aktiviert. Jede FX-Wartegrenze
beträgt 60 Sekunden; ein Timeout ist ein fehlgeschlagener Stressfall.

**Pulsrate ist keine gemessene Monitor-FPS.** JavaFXs öffentlicher
`addPostLayoutPulseListener` läuft nach CSS/Layout, aber **vor** dem Rendern.
`scroll_pulses_per_second` ist deshalb in Hz beschriftet. Der Zeitabschnitt
`scroll_pre_to_post_layout` enthält den Scrollhandler, CSS/Layout und andere
Listener zwischen den Messpunkten; er ist keine reine GPU-Repaint-Zeit.
Die konstanten Scrollsprünge passieren einmal pro Puls. Der Test misst
Scrollbalken-/VirtualFlow-Arbeit, keine Hardware-Mausradfrequenz oder
Event-Queue-Sättigung. Bei einem langsamen Puls sinkt auch die Eingaberate.

Monocle/Software-Rendering liefert eine wiederholbare Diagnoseumgebung, keine
Aussage über Desktop-GPU-FPS. Der Kernbenchmark enthält keine Shell-Panels,
keinen Seitenmodus, keine Bilder und keine vollständige `EditorWindow`-Oberfläche.
Für Release-Ziele anschließend auf realer Hardware zusätzlich Seitenmodus,
Bilder, lange Einzelabsätze, Tabellen, Zoom und aktivierte Zusatzansichten testen.
Die Zellzählung erfasst angehängte Zellen, keine Allokationen oder internen Caches.
Für Allokationen, GC und CPU-Anteile einen JFR-Lauf ergänzen; diese Werte werden
hier nicht aus Laufzeiten geschätzt.

API-Referenz für die Messgrenze:
[JavaFX 25 Scene pulse listeners](https://openjfx.io/javadoc/25/javafx.graphics/javafx/scene/Scene.html#addPostLayoutPulseListener(java.lang.Runnable)).

## Lokale Messergebnisse vom 7. November 2026

Gemessen mit JDK 25.0.2, JavaFX 25.0.4, Linux amd64, Monocle Headless,
`prism.order=sw`, 900 × 600. Hauptrepo-HEAD:
`6bfbfa3cfe51cc0b19ecfbb74be490b69fd9b5bc`, ausgechecktes Submodul:
`94cf18316cc05638c28eda64d193675b2154ee8a`. Die Benchmarkdateien und weitere
Arbeitsbaumänderungen waren noch nicht committed. Es handelt sich um eine
lokale Ausgangsmessung, keinen Vorher-/Nachher-Nachweis einer Optimierung.

| UI-Metrik | 200 Nutztextabsätze | 400 Nutztextabsätze |
| --- | ---: | ---: |
| Markdown-Zeichen | 19.644 | 39.404 |
| Import bis nach Layout, Mittelwert | 943,3 ms | 7.255,7 ms |
| Fett für 100 Zeichen bis nach Layout, Mittelwert | 666,4 ms | 5.322,1 ms |
| Scroll-Pulsrate | 28,40 Hz | 5,41 Hz |
| Scroll-Pulsintervall, p95 | 68,8 ms | 457,1 ms |
| Scroll pre→post Layout, Mittelwert | 26,81 ms | 177,14 ms |
| Synchroner Scrollhandler, Mittelwert | 0,046 ms | 0,040 ms |
| Materialisierte Zellen, Maximum der Stichproben | 29 | 27 |
| Pulsintervalle über 33,333 ms | 45,0 % | 84,2 % |

Damit ist eine starke Verschlechterung trotz begrenzter Zellzahl gemessen.
Der synchrone Scrollbalken-Setter ist in diesem Kern-Szenario viel kürzer als
der anschließende Layoutabschnitt. Das spricht für Arbeit im Layout-/Zellpfad;
welcher Anteil auf Messung, Fragmentaufbau oder GC entfällt, ist ohne Profiling
nicht bestimmt. Im 400er-Lauf meldete VirtualFlow zweimal
`index exceeds maxCellCount`; das ist ein weiterer Diagnosehinweis für
Größenberechnung/Zellaufbau, kein Testfehler. Die Virtualisierungsassertion bestand.

| Pipeline, Mittelwerte aus abschließendem Wiederholungslauf | 200 Absätze | 1.000 Absätze | 3.000 Absätze |
| --- | ---: | ---: | ---: |
| Parsing zum Document | 2,17 ms | 5,11 ms | 12,32 ms |
| Document → Markdown | 8,30 ms | 167,88 ms | 1.966,72 ms |
| UTF-8-Datei schreiben, warmer Cache | 0,154 ms | 0,459 ms | 0,857 ms |
| UTF-8-Datei lesen, warmer Cache | 0,078 ms | 0,225 ms | 0,440 ms |

Die 3.000er-Datei enthält 298.384 Markdown-Zeichen und 36.300 Dekorationsruns.
Die Writer-Skalierung passt zum wiederholten linearen Run-Lookup; sie beweist
keinen exakten CPU-Anteil. I/O auf einem langsamen/entfernten Dateisystem wird
durch die niedrigen lokalen Cache-Latenzen ausdrücklich nicht abgebildet.

Die vollständige Performance-Suite bestand mit neun ausgeführten Fällen;
die zwei zusätzlichen UI-Stressfälle waren deaktiviert. Ein zuvor gestarteter
1.000er-UI-Lauf wurde wegen langer Blockaden abgebrochen, bevor vollständige
Messwerte vorlagen; er ist kein erfolgreicher Stressnachweis. Der Parser-/Writer-
Wiederholungslauf mit allen sieben Fällen bestand ebenfalls. Rohdaten und
Zusammenfassungen bleiben lokal im oben angegebenen Build-Verzeichnis.

Abschließende Validierung: reguläres `mvn -o --batch-mode --no-transfer-progress
-Dmardexas.build.directory=target/performance-check verify` mit JDK 25 war
erfolgreich. 1.185 Editor-Tests ohne Fehler, davon ein Druckertest wegen
fehlendem Systemdrucker übersprungen; außerdem 13 erfolgreiche Integration-Demo-Tests.
Checkstyle ohne Verstöße, SpotBugs ohne
Bugs und Coverage-Prüfung bestanden. Die sechs Python-Tests für den
Baseline-Vergleich bestanden ebenfalls. Das Maven-Profil des Submoduls
überspringt dessen eigene Tests standardmäßig; diese Prüfung ist kein neuer
vollständiger Testlauf der Bibliothek. Produktionscode wurde durch diese
Benchmark-/Analyseänderung nicht refaktoriert; die Lösungen unten sind der
konkrete nächste Umsetzungsschritt.

## Systematische Codeanalyse

Untersucht wurden die Editor-Pakete `markdown`, `editor`, `ui`, `api`, `io`,
`features`, `findreplace`, `settings` sowie die zugehörigen Rendering-,
ViewModel- und Bufferpfade in `rich-text-area/rta`. Einstieg waren Listener,
vollständige Dokumentdurchläufe, Allokationen, Layoutaufrufe, Dateizugriffe und
Workergrenzen. Die Tabelle enthält statisch belegte Arbeit bzw. daraus
abgeleitete Skalierungsrisiken. Exakte CPU-Anteile erfordern einen Profiler.

| Priorität | Fundstelle / Ursache | Wirkung und Vorschlag | Aufwand |
| --- | --- | --- | --- |
| P0 | `RichTextAreaSkin.refreshTextFlow`, `computeFullHeight`, `measureParagraphHeight`: jeder Buffer-Event misst im Flussmodus alle Absätze und erzeugt dafür TextFlows/Textknoten | Hoher Verdacht auf den dominanten Formatierungs-/Tipplatenzpfad. Absatzhöhen nach Inhalts-/Stilrevision und Breite cachen; nur betroffene Absätze invalidieren; mehrere Events pro Puls zusammenfassen. Aktuell trotz Zellvirtualisierung vollständige Messarbeit. | mittel–hoch, Submodul |
| P0 | `MarkdownWriter.toModel`, `inlineRuns`, `decorationModelAt`: Lookup beginnt pro Absatz und Run wieder bei der ersten Dekoration; Horizontal-Rule-Prüfung sogar pro Zeichen | Bei D Runs bis O(D²), zusätzlich O(P·D) für P Absätze. Ein Index pro Serialisierung bzw. geordneter Sweep; Gap-/Overlap-/Zero-Length-Semantik erhalten. | mittel |
| P1 | `MarkdownEditor.open/setMarkdown/save`, `EditorWindow.loadFile/saveToFile`, `AutoSaveService.saveIfModified` | Lesen, Parsing, Serialisierung und Schreiben auf dem FX-Aufrufpfad. Autosave ist ein `AnimationTimer`, also ebenfalls FX. Snapshot auf FX, CPU/I/O in einem begrenzten Worker; Ergebnis mit Revision übernehmen. | mittel–hoch |
| P1 | `AutoSaveService` ruft `getMarkdown()`; `MarkdownEditor.markdownForDirectory` markiert vor dem Dateischreiben gespeichert | Zusätzlich zur Latenz ein Statusrisiko: `getMarkdown()` führt einen ViewModel-Save aus. Recovery darf den Dirty-Zustand nicht löschen; manueller Save darf erst nach erfolgreichem Schreiben die passende Revision bestätigen. | mittel; zusammen mit Async-I/O lösen |
| P1 | `DocumentStructureView` hört separat auf Dokument, Textlänge und Fassadenänderungen; `refresh` exportiert Headings und danach einen vollständigen Snapshot für Kapitel | Mehrfache Vollanalysen derselben Revision und `setAll` auf der Struktur-Liste. Dirty-Flag/Coalescing, gemeinsamer Snapshot, Heading-/Kapitelcache nach Bufferrevision; ausgeblendete Ansicht erst bei Einblenden aktualisieren. | mittel |
| P1 | `LineNumberGutter.requestSync/sync`: sofortige Synchronisation plus `runLater`, Lookup und Geometrieabfragen, Font-/Style-/Labeländerungen pro Scrollsignal | Zusätzliche Layout-/CSS-Invalidierungen und mehrfaches Auslesen innerhalb eines Pulses; potenzielles Layout-Thrashing. Einmal pro Puls synchronisieren; Font/Style nur bei Zoomwechsel; Geometrie zuerst lesen, danach Labels schreiben. | klein–mittel |
| P1 | `RichListCell.updateItem`, `ParagraphListView.updateLayout`: neue Textfragmente; `rebuildCells()` bei Layoutaktualisierung | Zellvirtualisierung vorhanden, Inhalte werden trotzdem neu aufgebaut. Nur veränderte sichtbare Absätze aktualisieren, Fragment-/Style-Diffs; Scroll-/Caret-/Tabellenpositionen als Regressionstests beibehalten. | hoch, Submodul |
| P2 | `MarkdownParser.scan`: rekursive Teilstrings und Suche nach schließenden Delimitern; `htmlAttribute` kompiliert Pattern je Aufruf | Adversariale/lange Inlinefolgen separat messen. Scanner mit Indexbereichen und Delimiter-Stack; feste HTML-Attributpatterns vorab kompilieren. Blockpatterns sind bereits statisch, kein pauschaler Regex-Austausch nötig. | mittel–hoch |
| P2 | `MarkdownWriter.mergeCodeBlocks`: kopiert beim Zusammenführen jede bisherige Codezeilenliste erneut | Lange Codeblöcke können zusätzlich quadratische Kopierarbeit erzeugen. Codezeilen in einem lokalen Builder sammeln und Block erst am Ende materialisieren. | mittel |
| P2 | `PieceTable.getText/getDecorationModelList`, `MarkdownEditor.getHeadings/getStatistics/getDocumentSnapshot` | Export durchläuft Pieces und erzeugt neue Strings/Listen; häufige Snapshot-Anfragen multiplizieren Kosten nach vielen Edits. Gemeinsamer revisionsbasierter Export; Piece-Anzahl und Undo-Historie mit JFR prüfen. Keine gemessene Leak-Behauptung. | mittel–hoch |
| P2 | `WorkspaceWindow.documentSaved` ruft `fingerprint(Path)` synchron nach Laden/Speichern | Zusätzlicher vollständiger Datei-Read auf FX. Hash im vorhandenen I/O-Worker berechnen oder während des Schreibens erzeugen, Anwendung mit Dateipfad/Revision absichern. Externe Watcher-Prüfungen sind bereits im Worker und debounced. | klein–mittel |
| P2 | `LiveMarkdownCheck.checkNow`, `WritingDashboard.refresh` | Prüfung/Analyse bereits asynchron und gegen veraltete Ergebnisse gesichert. Quellerfassung/Markdown-Serialisierung bzw. Snapshot bleibt auf FX. Snapshot einmal erfassen, Writer in Worker verschieben; Cancellation bis zum Parser/Checker durchreichen. | mittel |
| P2 | `ParagraphTile.createGridBox`, lange TextFlows und Tabellen | Eine große Tabelle ist ein Absatz mit vollständigem Grid; Absatzvirtualisierung virtualisiert keine Tabellenzeilen oder Zeilen innerhalb langer Einzelabsätze. Große Tabellen/Einzelabsätze gesondert profilieren; erst danach Untervirtualisierung bzw. Größenbudget. | hoch |
| P2 | `RichListCell.buildImage`, `RichTextAreaSkin.createImageView` und Bildvalidierung | `Image::new`/`new Image(url)` laden ohne expliziten Background-Modus. Große/entfernte Bilder können den UI-Pfad belasten. Asynchron vorladen, Platzhalter, Ladefehler und layoutabhängige Größenänderungen behandeln; Cache begrenzen. | mittel |
| P3 | `ApplicationSettings.save`, Session-/Preset-/Bibliothekszugriffe | Synchrone kleine Konfigurationsdateien; gegenüber Dokument-Rendering vermutlich nachrangig. Änderungen bündeln, echte Latenz prüfen; Bibliotheksdialoge nutzen bereits Tasks. | klein |
| P3 | Diff-/Review-/Druckpfade | `TrackChangesEngine` begrenzt große unähnliche LCS-Bereiche; `DiffDialog` arbeitet bereits asynchron. Inline-Review baut Ansichten neu auf, Druck misst vollständigen Inhalt. Nur bei aktiver Funktion optimieren; Druckmessung nicht mit Scroll-Virtualisierung verwechseln. | mittel–hoch |

Bereits sinnvolle Mechanismen: `VirtualFlow`/`RichVirtualFlow`, Font-/Bildcaches,
`CommandDocumentSnapshot` als revisionsinvalidierter Exportcache, debounced
`LiveMarkdownCheck`, begrenzte Worker, WatchService statt Polling,
`DocumentView` mit coalesced Relayout und Absatzlayoutcache. Diese Mechanismen
sollen als Ausgangspunkt dienen. Insbesondere ist „fehlende Virtualisierung“
für den normalen Absatzeditor keine zutreffende Diagnose.

## Konkrete Refactoring-Bausteine

Die folgenden Ausschnitte sind Vorschläge für die angegebenen Methoden. Sie
sind bewusst von den implementierten Messinstrumenten getrennt; Änderungen
der Produktionssemantik und des Submoduls sind damit noch nicht durchgeführt.

### 1. Dekorationsindex im MarkdownWriter

Für validierte, sortierte, lückenlose, nicht überlappende Runs kann ein
aufruflokaler Index die wiederholte lineare Suche ersetzen:

```java
final class DecorationIndex {
    private final List<DecorationModel> runs;
    DecorationIndex(List<DecorationModel> runs) { this.runs = List.copyOf(runs); }

    DecorationModel covering(int position) {
        int lo = 0, hi = runs.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (runs.get(mid).getStart() <= position) lo = mid + 1;
            else hi = mid;
        }
        if (lo == 0) return null;
        DecorationModel run = runs.get(lo - 1);
        return position < (long) run.getStart() + run.getLength() ? run : null;
    }
}
```

Index einmal in `toModel` aufbauen und an `classify`, `inlineRuns` und die
Zugriffshilfen weitergeben. Keinen veränderlichen Cache als Writer-Feld halten:
Writer werden künftig auch nebenläufig verwendet. Für sortierte Inline-Runs
ist ein monotoner Cursor noch günstiger. Der bisherige Fallback für Lücken,
unsortierte/überlappende und leere Runs muss entweder durch validierte
Normalisierung oder den bisherigen Lookup erhalten bleiben. Besonders mehrere
Dekorationen mit gleichem Start dürfen nicht stillschweigend anders ausgewählt
werden. Deshalb den Index nicht blind in die bisher öffentliche Writer-API
einsetzen. Testmatrix: bestehende Writer-/Roundtrip-/Hardening-Tests plus
Lücken, gleiche Startposition, EOF/Leerabsatz, Tabellen, Bilder und Emojis.

### 2. Dokumentweite Höhenmessung ersetzen

Zielstruktur für `RichTextAreaSkin`:

```java
record HeightKey(long paragraphRevision, long styleRevision, double width) { }
// Pro Absatzidentität genau einen aktuellen Eintrag halten, keine Historie.
record MeasuredHeight(HeightKey key, double height) { }

double heightOf(Paragraph paragraph, HeightKey key) {
    MeasuredHeight old = heights.get(paragraph); // IdentityHashMap, FX-thread only
    if (old != null && old.key().equals(key)) return old.height();
    double measured = measureParagraphHeight(paragraph, key.width());
    heights.put(paragraph, new MeasuredHeight(key, measured));
    return measured;
}
```

`heights` ist eine Skin-eigene `IdentityHashMap<Paragraph, MeasuredHeight>`.
Buffer-Events liefern die Änderungsrange; nur diese Absätze erhalten eine neue
Revision. Bei Breite, Font, Emoji-Ton, Bildladeabschluss oder Absatzstil die
betroffenen Werte invalidieren. Gelöschte Absätze aus der Map entfernen und
beim Dispose leeren. Höhe eines Edits per Delta zur Summe addieren; einen
vollständigen Durchlauf nur beim Öffnen bzw. globalen Layoutwechsel ausführen.
Zusätzliche Verbesserung: mehrere Bufferevents vor dem nächsten Puls sammeln.
Nicht nur `computeFullHeight` entfernen: `fullHeightProperty` ist öffentlich,
und Druck-/Seitenumbrüche benötigen weiterhin korrekte Höhen.

### 3. Schwere Listener bündeln

Für die Strukturansicht kann dieser FX-interne Einstieg die drei direkten
`refresh()`-Listener ersetzen:

```java
private boolean refreshPending;

private void requestRefresh() {
    if (refreshPending) return;
    refreshPending = true;
    Platform.runLater(() -> {
        refreshPending = false;
        if (isVisible() && getScene() != null) refresh();
    });
}
```

Das bündelt eine Queue-Runde, garantiert aber noch nicht „einmal pro Puls“.
Für die Zeilennummern stattdessen einen einzigen Pre-/Post-Layout-Listener mit
Dirty-Flag verwenden: im Pre-Layout nur Styles/Größen ändern, nach dem Layout
Geometrie lesen. Unsichtbare Ansichten behalten ein Dirty-Flag und aktualisieren
beim Einblenden. Revisionscache zusätzlich einsetzen, damit mehrere Runden
derselben Revision nicht erneut exportieren. Listener und Pulse-Callbacks beim
Dispose entfernen. Tests: gleiche Länge bei Ersetzen, reine Formatänderung,
Undo/Redo, Wechsel des Buffers, Ein-/Ausblenden und Zoom.

### 4. Snapshot auf FX, Serialisierung und I/O im Worker

Das bestehende `getMarkdown()` darf für Autosave nicht verwendet werden.
Stattdessen einen abgelösten Snapshot aufnehmen und ein JavaFX-Task bauen:

```java
Document live = editor.getDocumentSnapshot(); // FX thread
Document captured = new Document(live.getText(), List.copyOf(live.getDecorations()),
        0, live.getPageFormat(), List.copyOf(live.getHeadersFooters()));
Path directory = target.toAbsolutePath().getParent();
Task<Void> write = new Task<>() {
    @Override protected Void call() throws Exception {
        String markdown = new MarkdownWriter().write(captured, directory);
        new MarkdownFileHandler().write(target.toFile(), markdown);
        return null;
    }
};
write.setOnFailed(event -> showSaveError(write.getException())); // FX callback
ioExecutor.execute(write); // bounded single worker owned by the document/workspace
```

`showSaveError`, `target` und der lifecyclegebundene `ioExecutor` sind im
aufrufenden Controller zu integrieren. Der Erfolgspfad benötigt eine separate
Revision: nur wenn aktuelle Bufferrevision, Dateipfad und Dokumentidentität zum
Snapshot passen, darf der manuelle Save den Saved-Marker setzen. Bei Edit
während des Schreibens bleibt Dirty=true. Recovery-Save setzt niemals diesen
Marker. Für atomisches Speichern im Zielverzeichnis temporär schreiben und
danach verschieben; pro Dokument eine geordnete Schreibwarteschlange verwenden.
Recovery-Löschen und Worker-Schreiben müssen dieselbe Reihenfolge benutzen,
sonst kann ein später Worker ein gerade gelöschtes Recovery wiederherstellen.

Laden analog: `MarkdownFileHandler.read` und `MarkdownParser.parseInto(source,
directory)` im Task, nur die Installation des fertigen Dokuments auf FX. Die
asynchrone Open-Action besitzt einen eigenen Abschlusszeitpunkt; Dateiidentität
und Saved-Marker erst danach aktualisieren. Tasks bei Dispose abbrechen und
späte Ergebnisse anhand einer Request-ID verwerfen. Keine Steuerelemente aus
dem Worker lesen. Tests: Schreibfehler, zwei Saves, Edit während Save, neuer
Dateiname, geschlossener Tab, externe Dateiänderung und Recovery-Rennen.

## Reihenfolge der Umsetzung

1. Messdaten samt Revisionen sichern; Höhenmessung und Writer-Run-Lookup mit
   JFR auf CPU/Allokationen eingrenzen.
2. Höhenmessung im Submodul inkrementell machen; Core-Renderingbenchmark und
   vorhandene Caret-/Viewport-/Druck-/Seiten-Regressionstests ausführen.
3. Writer-Index plus Codeblock-Builder mit semantischen Tests einführen.
4. I/O und Save-Zustand gemeinsam asynchron refaktorieren.
5. Struktur-/Gutter-Listener bündeln, anschließend mit sichtbaren Zusatzpanels
   messen. Tabellenzeilen-/Einzelabsatzvirtualisierung erst nach diesen Schritten.

Commit/Push und Änderung des Submodulzeigers sind nicht Teil der Messimplementierung.
