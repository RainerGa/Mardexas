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
Aufruf mit `-Dperf.stress=true` ergänzen. Diese Fälle bestanden nach dem Refactoring; vor dem Refactoring wurde
ein 1.000er-Lauf wegen langer Blockaden abgebrochen. Die normale
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
Zusammenfassungen dieser Ausgangsmessung wurden vor dem Refactoring unter
`/tmp/mardexas-performance-before-optimization` archiviert.

Validierung der damaligen Benchmark-/Analyseänderung: reguläres `mvn -o --batch-mode --no-transfer-progress
-Dmardexas.build.directory=target/performance-check verify` mit JDK 25 war
erfolgreich. 1.185 Editor-Tests ohne Fehler, davon ein Druckertest wegen
fehlendem Systemdrucker übersprungen; außerdem 13 erfolgreiche Integration-Demo-Tests.
Checkstyle ohne Verstöße, SpotBugs ohne
Bugs und Coverage-Prüfung bestanden. Die sechs Python-Tests für den
Baseline-Vergleich bestanden ebenfalls. Das Maven-Profil des Submoduls
überspringt dessen eigene Tests standardmäßig; diese Prüfung ist kein neuer
vollständiger Testlauf der Bibliothek. Dieser Ausgangslauf enthielt noch keine Produktionsoptimierungen.
Der aktuelle Umsetzungsstand folgt unten.

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

## Umgesetzte Refactorings

Die vorherigen Bausteinvorschläge sind jetzt in Produktionscode überführt:

| Bereich | Umsetzung |
| --- | --- |
| Absatzmessung | `RichTextAreaSkin` speichert Höhen nach Absatzidentität, Bereich, Stil und Breite. Stiländerungen invalidieren den betroffenen Bereich; Einfügen/Löschen konservativ den nachfolgenden Bereich. Bufferwechsel, Bildabschluss und Skin-Tone invalidieren passend. Breitenänderungen bündeln Layout und Neuberechnung. |
| PieceTable | Fauler kumulativer Positionsindex, binäre Bereichs-/Caret-Lookups, Export nur über überlappende Pieces; keine vollständigen Stringpräfixe pro Absatz. Zeicheniterator sucht binär und ermittelt Zeilenumbrüche direkt aus Pieces. Revision steigt vor Buffer-Change-Listenern, auch bei Undo/Redo; Undo meldet Dokumentkoordinaten. |
| Sichtbare Zellen | `RichListCell` verwendet unveränderte Fragmente bei gleicher Bufferrevision, Absatzidentität/-dekoration, Skin-Tone und Bildrevision wieder. Geometrie wird weiter aktualisiert. Bestehende Absatzvirtualisierung bleibt erhalten. |
| MarkdownWriter | Ein aufruflokaler Index für geordnete, nicht überlappende positive Runs; alte lineare Semantik als Fallback für überlappende/unsortierte/Null-Längen-Runs. Lücken behalten die frühere Fallback-Semantik. Codeblöcke und benachbarte Inlines werden linear gesammelt. |
| MarkdownParser | Expliziter Scan-Stack mit Indexbereichen für Link-/Emphasis-/Underline-Inhalte; nur dekodierte HTML-Anker brauchen einen eigenen Quellstring. Negative Delimitersuchen werden pro Bereich gemerkt, HTML-Attributpatterns einmal kompiliert und Inline-Texte mit Buildern zusammengeführt. Abbruch wird beim Scan und Blockdurchlauf geprüft. |
| Dokument-Snapshots | Unveränderlicher Exportcache nach Bufferidentität, Revision, Textlänge und Seiten-/Headerformat. Headings und Statistiken teilen denselben Snapshot. |
| Dateiaktionen | `MarkdownEditor.openAsync/saveAsync`: Snapshot auf FX, CPU und I/O in geordnetem Worker mit begrenzter Queue, Ergebnisabgleich auf FX. Veraltete Loads überschreiben keine Eingaben; Saves bestätigen nur die passende Revision. `EditorWindow` und Session-Dokumentladen verwenden die neue Grenze. |
| Atomisches Speichern | Temporäre Datei im Zielverzeichnis, atomisches Ersetzen mit Fallback; bestehende Symlinks und POSIX-Rechte bleiben erhalten. Fehler bestätigen keinen gespeicherten Stand. Kein `fsync`-Durabilitätsversprechen. |
| Autosave | Serialisiert einen Snapshot im eigenen Worker und ändert Dirty nicht. Recovery-Schreiben und -Löschen laufen geordnet. `pendingWrite()` und `close()` ermöglichen überprüfbaren Abschluss/Lifecycle. |
| Strukturansicht | Coalesced Dirty-Refresh, gemeinsame Snapshot-/Kapitelcaches, ausgeblendete Ansicht erst beim Einblenden; unnötiges `setAll` entfällt. Buffer-Stiländerungen werden auch ohne Textlängenwechsel erkannt. Listener inklusive ChapterFolding werden abgemeldet. |
| Zeilennummern | Eine Synchronisation nach Layout pro Puls, zuerst Zellgeometrie lesen, dann Labels aktualisieren; Fonts nur bei Größenwechsel, Clip wiederverwenden; Scene-/Scroll-/Property-Listener beim Schließen entfernen. |
| Liveprüfung und Dashboard | Revisionsgesicherter Hintergrundpfad bleibt; Markdown-Serialisierung und Kapitel-/Bereichsanalyse laufen jetzt ebenfalls im Worker. |
| Workspace | Hash der tatsächlich gelesenen/geschriebenen Bytes aus dem Dateiworker übernehmen, kein erneuter vollständiger Read nach erfolgreichem Öffnen/Speichern. Externes Überschreiben vorab im Workspace-Worker prüfen. Session erfasst auch gerade ladende Dateipfade. |
| Bilder | Hintergrundladen mit Platzhalter und nachfolgender Mess-/Zellinvalidierung bei Erfolg oder Fehler. LRU-Cache auf 256 Bilder begrenzt; ausstehende Loader beim Evict/Dispose abmelden/abbrechen. Drag-and-drop-Validierung im begrenzten Worker, Einfügen nur bei unverändertem Dokument und Auswahl; Datei-Reihenfolge und URL-Fallback bleiben erhalten. |
| Kleine Konfiguration | `ApplicationSettings.saveAll` bündelt Fenstereinstellungen, überspringt unveränderte Gruppen und erhält andere Schlüssel. Recent-Files-Änderung liest die Datei nur einmal. Session wird bereits atomisch und nur beim Schließen gespeichert. |

### Asynchrone API und Zustandsgrenzen

```java
// Auf FX, nachdem das Control eine Skin besitzt:
editor.saveAsync(target).whenComplete((ignored, failure) -> {
    if (failure != null) showSaveError(failure);
    else updateTitle();
});
editor.openAsync(source).thenRun(this::updateTitle);
```

Die Futures schließen erst nach Installation bzw. Statusabgleich auf FX ab.
Nicht auf FX darauf warten. `EditorWindow.pendingFileOperation()` und
`WorkspaceWindow.pendingFileOperations()` erlauben Hosts/Tests dieselbe Grenze.
Eigenständige Hosts rufen beim Dispose `cancelPendingIo()`; `EditorPane.close()`
erledigt dies bereits. Schon akzeptierte Schreiboperationen werden geordnet
zu Ende geführt. Die synchronen API-Methoden bleiben kompatibel verfügbar;
`setMarkdown` ist weiterhin eine synchrone Parsing-API mit gequeuter Installation.
`getMarkdownSource` liest ohne Saved-Side-Effect; das alte `getMarkdown` behält
seine bestehende Save-Semantik. Details: [API.md](API.md).

### Weiterhin bedingte Profiling-Themen

Die Analyse enthielt ausdrücklich bedingte Vorschläge, keine nachgewiesenen
Flaschenhälse: Untervirtualisierung einzelner Tabellen/Einzelabsätze sowie
aktive Druck-/Reviewpfade. Das Refactoring ersetzt keine Tabellenzeilen durch
virtuelle Zeilen: Auswahl, Caret, Undo und Druck benötigen weiter ihre vollständige
Geometrie. Die bestehenden Druck- und Reviewtests prüfen diese Semantik.
Session-/Preset-Dateien und einzelne Einstellungsänderungen bleiben synchron;
es gibt dafür keinen gemessenen dominanten Latenzbefund.

Für lange Einzelabsätze und große Tabellen gibt es jetzt zusätzliche ausführbare
UI-Szenarien (200 formatierte Absätze plus 500 Tabellenzeilen bzw. 1.000 Inlinefolgen):

```bash
mvn -o --batch-mode --no-transfer-progress -pl editor -am \
  -Dmardexas.build.directory=target/performance-check -Pperformance \
  -Dperf.shapes=true test
```

Sie messen dieselben Layout-/Format-/Scrollgrenzen. Eine Teilvirtualisierung ist
kein durch diese Änderungen behaupteter Leistungsgewinn; Release-Ziele mit
extremen Tabellen, Bildern, Seitenmodus und realer GPU benötigen weiterhin
Profiling auf Zielhardware. `VirtualFlow` kann auch im optimierten Lauf noch
`index exceeds maxCellCount` protokollieren; die Zellzahlassertion prüft weiter,
dass nicht das gesamte Dokument materialisiert wird.

## Nachmessung und Regressionen

Die lokale Ausgangsmessung dient als Diagnosevergleich. Es liegen nicht drei
neue Baseline-Läufe desselben alten Arbeitsbaums vor; eine statistisch belastbare
Vergleichsaussage oder universelle Desktop-FPS wird daraus nicht abgeleitet.
Atomisches Dateiersetzen hat zusätzliche Dateisystemoperationen gegenüber dem
alten In-place-Schreiben: steigende reine Schreibzeiten müssen zusammen mit
dieser absichtlichen Änderung der Messoperation beurteilt werden.

Aktuelle Ergebnisse und Abschlussprüfung werden nach den Wiederholungsläufen ergänzt.
