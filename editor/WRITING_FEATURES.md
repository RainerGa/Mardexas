# Schreibfunktionen ohne zusätzliche Markdown-Syntax

Die Erweiterungen bearbeiten gewöhnliche CommonMark-/GFM-Inhalte oder ausschließlich die Bedienoberfläche. Es gibt keine eigenen Dokument-Tags, versteckten Felder oder im Markdown gespeicherten Anwendungseinstellungen. Vorlagenfelder werden vor dem Einfügen durch normalen Text ersetzt. Tabellenberechnungen erzeugen feste Werte; eine Tabellenkalkulations-Formelsprache wird nicht gespeichert.

## Funktionsübersicht

Die Nummern entsprechen der ausgewählten Funktionsliste.

| Nr. | Umsetzung und Zugang | Wesentliche Unit-Tests |
| --- | --- | --- |
| 6 | Einfügen → Schreibbausteine: eigene Snippets verwalten, auswählen, Kürzel expandieren | MarkdownLibraryStoreTest, WritingAssistDialogsTest |
| 7 | Eigene Dokumentvorlagen speichern, verwalten und als neues Dokument verwenden | MarkdownLibraryStoreTest, WritingAssistDialogsTest |
| 8 | Datum/Uhrzeit, Sonderzeichen und aufgelöste Vorlagenfelder einfügen | InsertionContentTest, TemplateFieldsTest |
| 9 | Bearbeiten → Textwerkzeuge: Groß-/Kleinschreibung, Satzschreibung, Zeilen sortieren, Duplikate entfernen | DocumentEditsTest, EditingFeaturesTest |
| 10 | Absätze auswählen, verschieben und duplizieren | DocumentEditsTest, EditingFeaturesTest |
| 11 | Textformatierung bzw. Links entfernen; Absatz auf Fließtext zurücksetzen | DocumentEditsTest, EditingFeaturesTest |
| 12 | Zwischenablage als normalen Text, Markdown oder wörtlichen Quelltext einfügen | EditingFeaturesTest, MarkdownWriterTest |
| 14 | Tabelle → Tabellenwerkzeuge: Tabellenkalkulationsdaten aus der Zwischenablage als Tabelle einfügen | DelimitedTablesTest, TableEditorTest |
| 15 | Dokumentstruktur: Kapitel einschließlich Unterkapiteln verschieben | EditingFeaturesTest |
| 17 | Dokumentstruktur durchsuchen, maximale Überschriftenebene und Kapitelwortzahlen | EditingFeaturesTest, DocumentStructureViewTest |
| 18 | Kapitel in der Ansicht ein-/ausklappen; Navigation macht Zielabsätze wieder sichtbar | EditingFeaturesTest |
| 19 | Suchen/Ersetzen: Trefferliste, Ersetzungsvorschau, einzelne Treffer auswählen, Suchen speichern | FindReplaceEngineTest, SearchPresetStoreTest, SearchFeaturesDialogTest |
| 20 | Formatfilter: fett, kursiv, unterstrichen, durchgestrichen, Links, Überschriften und Code | FindReplaceEngineTest, SearchFeaturesDialogTest |
| 21 | Extras → Ordner durchsuchen: rekursiv suchen, Dateien auswählen, Vorschau, atomare Ersetzung und Sicherungskopien | FolderSearchTest, SearchFeaturesDialogTest |
| 22 | Tabellen nach mehreren Spalten numerisch oder alphabetisch sortieren | TableDataTest, TableEditorTest |
| 23 | Tabellen transponieren, Zeilen/Spalten verschieben oder duplizieren, Tabellen teilen und verbinden | TableDataTest, TableDocumentsTest, TableEditorTest |
| 24 | CSV-/TSV-Import und -Export sowie TSV-Kopie | DelimitedTablesTest, TableEditorTest |
| 25 | Summe, Mittelwert, Minimum, Maximum und Anzahl als normalen Tabellenwert einfügen | TableDataTest, TableEditorTest |
| 30 | Extras → Links verwalten: Inventar, lokale Ziele, ausdrückliche Webprüfung und Linkziele gesammelt ersetzen | CheckingTest, CheckingEditorTest |
| 32 | Einfügen → Code-Assistent: Sprache, Kopieren, Einrücken, Tabs/Leerzeichen, Syntaxvorschau; Sprache bleibt im normalen Codezaun erhalten | CodeEditingTest, CodeAssistantDialogTest, MarkdownParserTest, MarkdownWriterTest |
| 34 | Ansicht → Schreibansicht: Ablenkungsfreiheit, Absatzfokus, Schreibmaschinenmodus, begrenzte Lesebreite | EditingFeaturesTest, ZoomControllerTest |
| 35 | Dokument-Tabs mit getrenntem Undo, Wiedervorlage geschlossener Dateien und zwei Dokumenten nebeneinander | WorkspaceWindowTest |
| 36 | Extras → Schreibstatistik: Dokument, Auswahl oder Kapitel; Worthäufigkeit und Satz-/Absatzkennzahlen | WritingMetricsTest, WritingDashboardTest |
| 37 | Schreibziel mit Fortschritt für netto neu geschriebene Wörter und Sitzungsdauer | WritingMetricsTest, WritingDashboardTest |
| 40 | Externe Dateiänderungen: Hinweis mit Neuladen/Vergleichen; Schutz vor unbeabsichtigtem Überschreiben | ExternalFileMonitorTest, WorkspaceWindowTest |
| 41 | Dateisitzung mit aktiver Datei, Cursor, Scrollposition und Schreibschutz wiederherstellen | WorkspaceSessionTest, WorkspaceWindowTest |
| 42 | Extras → Befehle und Tastenkürzel: Befehlssuche, eigene konfliktgeprüfte Kürzel und Symbolleistenfavoriten | CommandCenterTest |
| 43 | Lesbarkeit: dunkle Bedienoberfläche, hoher Kontrast und UI-Schriftgröße; Vorlesen mit System-Sprachausgabe | AccessibilityOptionsTest, WritingDashboardTest, SpeechReaderTest |
| 44 | Datei → Druckbereich und Gestaltung: Dokument, Auswahl, Kapitel und Seitenbereiche | PrintDocumentsTest, PrintOptionsTest |
| 45 | Druckränder, Schrift, Kopf-/Fußzeile und Seitennummern ausschließlich in der Druckfassung | PrintDocumentsTest, PrintOptionsTest, PrintPageViewTest |
| 46 | Erweiterte Markdown-Prüfung, verzögerte Live-Anzeige, ignorierbare Hinweise, angebotene Schnellkorrekturen und Absatznavigation | CheckingTest, LiveMarkdownCheckTest, IssueNavigationTest |

## Verhalten und Grenzen

- Bearbeitungen an Dokumentmodellen bleiben rückgängig machbar. Selektive Ersetzung erzeugt einen Undo-Schritt. Ordnerersetzungen betreffen Dateien außerhalb der Editor-Undo-Historie und hinterlassen für jede bearbeitete Datei eine Sicherung im jeweiligen Verzeichnis. Eine Mehrdateien-Ersetzung ist keine dateisystemübergreifende Transaktion; nach einem Fehler können bereits bearbeitete Dateien anhand ihrer Sicherungen wiederhergestellt werden.
- Sitzung, Suchdefinitionen, Snippets, Vorlagen, Tastenkürzel und Anzeigeeinstellungen liegen im Anwendungsprofil. Druckeinstellungen liegen in Java-Preferences. Ungespeicherte Dokumentinhalte werden nicht stillschweigend durch die Dateisitzung gesichert; beim Schließen greift die Abfrage für ungespeicherte Änderungen.
- CSV unterstützt gequotete Felder und mehrzeilige Daten. Die Übernahme mehrzeiliger Zellinhalte in eine GFM-Tabelle wird mit einer verständlichen Fehlermeldung abgelehnt, weil das Tabellenformat diese Inhalte nicht verlustfrei darstellen kann. Kein künstliches `<br>` wird erzeugt.
- Die Ordnersuche untersucht `.md` und `.markdown`, folgt keinen symbolischen Links und begrenzt sich auf 2.000 Dateien, 2 MiB je Datei, insgesamt 32 MiB Quelldaten und 10.000 Treffer. Nur angezeigte Treffer werden ersetzt. Reguläre Ausdrücke haben ein Arbeitsbudget; Abbruch und zwischenzeitliche Dateiänderungen werden berücksichtigt.
- Live-Prüfung und Schreibstatistik rechnen verzögert; veraltete Ergebnisse werden verworfen. Dateibeobachtung verwendet einen schlafenden Betriebssystem-Watcher statt periodischer Dateiabfragen. Externe Dateivergleiche halten nur Prüfsummen vor und lesen Dateien blockweise.
- Die Linkprüfung nutzt das Netz nur nach ausdrücklichem Aufruf. Vorlesen benötigt unter Linux `espeak`; macOS und Windows nutzen ihre Systemstimme. Fehlende Sprachausgabe wird als Fehler angezeigt. Es werden keine Dokumenttexte an einen Webdienst gesendet.
- Hinweise navigieren zum gerenderten Absatz. Markdown-Quellspalten enthalten unsichtbare Syntax und entsprechen daher nicht direkt den sichtbaren Zeichenpositionen.
- Deutsche und englische Oberflächentexte sind für die neuen Schreibwerkzeuge vorhanden; andere gewählte Sprachen verwenden für diese Werkzeuge englische Texte, soweit noch keine Übersetzung existiert.

## Cursor und Auswahl unter „Text und Absätze“

Die 15 Aktionen verwenden rückgängig machbare Bearbeitungsbefehle. Der ursprüngliche Dokumentaustausch setzte den Cursor unabhängig vom Ergebnis auf Position 0 und hob die Auswahl auf. Die Befehle berücksichtigen jetzt Ergebnisposition und Auswahl auch beim Wiederholen; Rückgängig stellt die vorherige Position und Auswahl wieder her.

Beim Verschieben eines Absatzes werden nur die beiden betroffenen Absätze ausgetauscht. Der übrige Textpuffer bleibt bestehen, statt das ganze Dokument vorübergehend zu leeren und neu aufzubauen. Nach Befehlsabschluss und Rückkehr des Fokus wird die Cursor-Sichtbarkeit außerdem nach dem JavaFX-Layout geprüft; mehrere Anforderungen innerhalb eines Bildaufbaus werden zusammengefasst.

| Aktionen | Verhalten |
| --- | --- |
| Groß-/Kleinschreibung, Satzanfänge | Cursor und Auswahl bleiben am bearbeiteten Text; Längenänderungen wie `ß → SS` werden berücksichtigt. Ohne Auswahl bleibt die Schreibposition innerhalb des aktuellen Absatzes erhalten. |
| Zeilen sortieren, doppelte Zeilen entfernen | Die bearbeitete Auswahl bleibt markiert; ihr Ende passt sich der neuen Länge an. |
| Textauszeichnung, Links oder Absatzformatierung entfernen | Schreibposition und Auswahlrichtung bleiben erhalten. |
| Absatz nach oben/unten verschieben | Der Cursor folgt dem verschobenen Absatz und behält seine Spalte. Eine Auswahl innerhalb dieses Absatzes folgt ebenfalls. |
| Absatz duplizieren | Der Cursor steht an der entsprechenden Stelle der Kopie. Eine vollständig innerhalb des Absatzes liegende Auswahl wird auf die Kopie übertragen. |
| Absatz auswählen | Der Absatz einschließlich seines Zeilenumbruchs wird markiert. Folgeaktionen bearbeiten diesen Absatz, auch wenn das Auswahlende am Anfang des nächsten Absatzes liegt. |
| Nur Text, als Markdown oder Quelltext wörtlich einfügen | Eine vorhandene Auswahl wird ersetzt. Anschließend steht der Cursor hinter dem eingefügten Inhalt. |

Unveränderte Umwandlungen und Bewegungen über den Dokumentrand erzeugen keinen Undo-Eintrag. `EditingPositionTest` sichert alle Aktionen sowie Unicode, rückwärts markierte Auswahlen, Fokus, Scrollposition und Undo/Redo über die tatsächliche Editor-Bibliothek ab. Die Verschiebetests verwenden das vollständige Editorfenster mit geöffnetem Menü und prüfen nach mehreren echten JavaFX-Pulsen den sichtbaren Cursor, den Fokus und die Scrollposition. Weitere Tests decken formatierte Inhalte, Links, Bilder, Dokumentenden und ungültige Bereichsoperationen ab.

## Verifikation

Stand 5. November 2026: Der vollständige Editor-Lauf erfasst 1.023 Tests mit 0 fehlgeschlagenen Tests, 0 Fehlern und 1 übersprungenem Test. Checkstyle meldet 0 Verstöße; SpotBugs meldet 0 Bugs und 0 Analysefehler. Die Funktionsübersicht oben nennt die zugehörigen Tests; der folgende Befehl führt die Editor-Suite erneut aus.

Editor-Suite einschließlich der neuen Unit- und JavaFX-Integrationstests:

```sh
mvn -o -pl editor -Dmardexas.build.directory=/tmp/mardexas-audit-build clean test spotbugs:check
```

Die geänderte lokale RichTextArea-Bibliothek muss zuvor als aktuelle Abhängigkeit gebaut sein. Das isolierte Buildverzeichnis verhindert Konflikte mit einer parallel laufenden Eclipse-Kompilierung. Die Tests laufen gegen diese Bibliothek und prüfen unter anderem Standard-Markdown-Roundtrips, GFM-Tabellenausrichtungen, Literaltext, Unicode-/Emoji-Positionen, Undo, Schreibschutz, Abbruch sowie Datei- und Profilehler. Ergänzende Tests zeigen echte JavaFX-Fenster im Headless-Toolkit und bedienen gerenderten Text und Menüs mit Robot-Maus-/Tastaturereignissen; sie prüfen Cursorform, Bildschirmposition, Fokus und anschließende Texteingabe. Ein erfolgreicher Testlauf ersetzt keine Prüfung von Druckertreibern und installierten Systemstimmen auf allen Betriebssystemen.

Zusätzlich wurden 22 gezielte RichTextArea-Modelltests (`UnitBufferTests`, `PieceTableInternalSelectionTests`) erfolgreich ausgeführt. Der vollständige Legacy-Testlauf dieser Bibliothek ist derzeit durch bereits bestehende API-Abweichungen in `FieldTest` und `StyleRegistryTest` beim Kompilieren blockiert. Das betrifft nicht die eigenständige Editor-Testsuite.
