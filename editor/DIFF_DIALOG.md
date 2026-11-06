# Dateivergleich und Änderungsprüfung

Über **Datei → Mit einer anderen Datei vergleichen…** wird der aktuelle Editorinhalt
mit einer zweiten Markdown-Datei verglichen. Ungespeicherte Eingaben bleiben erhalten
und werden im Vergleich berücksichtigt. Dafür wird der aktuelle Text- und Formatierungszustand
direkt übernommen, ohne ihn zuvor als Markdown zu schreiben und erneut einzulesen.
So erzeugen die Normalisierung von Leerzeilen und Links keine zusätzlichen Unterschiede.
Die Namen beider Quellen stehen über der Ansicht.
Ohne geöffnete Datei erscheint ein Hinweis; Abbrechen verändert das Dokument nicht.

Der Vergleich arbeitet mit der formatierten Markdown-Darstellung. Unterschiedliche
Markdown-Schreibweisen mit identischer Darstellung gelten daher als gleich. Ersetzungen
bestehen aus einer Löschung und einer Einfügung, die getrennt geprüft werden können.

## Ansichten

- **Alle Markierungen:** Ausstehende Einfügungen grün, Löschungen rot und durchgestrichen,
  Formatänderungen gelb. Nummerierte Randmarkierungen erfassen auch Bilder.
- **Einfache Markierungen:** Ergebnis ohne Inline-Markierungen, Änderungen über nummerierte
  Randmarkierungen auswählen. Ausstehende Löschungen werden im Text ausgeblendet.
- **Keine Markierungen:** Bereinigtes Ergebnis einschließlich der bisherigen Entscheidungen.
- **Original:** Ursprünglicher Editorinhalt mit ursprünglicher Formatierung.

Ausstehende Änderungen gelten in der Ergebnisvorschau und beim Export als vorgeschlagen:
Einfügungen sind enthalten, Löschungen entfernt, neue Formatierungen aktiv.
Abgelehnte Formatänderungen verwenden die ursprüngliche Formatierung.

## Entscheidungen und Kommentare

Änderungen lassen sich über Dokument, Randmarkierungen und Prüfliste auswählen.
Die Prüfliste zeigt Typ, Status und vorhandene Kommentare. Statusfilter und Zähler
helfen bei längeren Vergleichen. Die angegebenen Änderungen stammen aus dem Vergleich;
es werden keine vermeintlichen Autoren oder Bearbeitungszeiten angezeigt.

Einzelne Entscheidungen und Kommentare sowie Sammelaktionen sind rückgängig machbar.
Die Historie hält bis zu 100 Prüfaktionen. Nach einer neuen Aktion verfällt der
Wiederholen-Zweig. Ein entschiedener Eintrag kann außerdem direkt wieder auf
**Ausstehend** zurückgesetzt werden. Sammelaktionen betreffen ausschließlich noch
ausstehende Änderungen und überschreiben keine früheren Entscheidungen.

| Aktion | Windows/Linux | macOS |
| --- | --- | --- |
| Rückgängig | Strg+Z | Cmd+Z |
| Wiederholen | Strg+Y oder Strg+Umschalt+Z | Cmd+Y oder Cmd+Umschalt+Z |
| Annehmen | Strg+Enter | Cmd+Enter |
| Ablehnen | Strg+Rücktaste | Cmd+Rücktaste |
| Auf ausstehend zurücksetzen | Strg+Umschalt+Rücktaste | Cmd+Umschalt+Rücktaste |
| Nächste Änderung | F7 | F7 |
| Vorherige Änderung | Umschalt+F7 | Umschalt+F7 |

Alle Aktionsbuttons besitzen Mouse-over-Texte. Die Prüfsymbole bestehen aus
Vektorgrafiken und benötigen keine besonderen Schriftzeichen.

## Ergebnisse verwenden

- **In Editor übernehmen** fragt, ob das Ergebnis übernommen und der Diff-Dialog
  geschlossen werden soll. Abbrechen lässt den Vergleich unverändert geöffnet.
  Nach erfolgreicher Übernahme schließt sich der Dialog; bei Fehlern bleibt er geöffnet.
  Die Übernahme ist im Editor als eine Aktion rückgängig machbar und speichert die
  Datei noch nicht. Absatzformatierungen, Tabellen und Bilder bleiben erhalten.
  Vorhandene Kommentare werden im Bestätigungsdialog ausdrücklich erwähnt; sie lassen
  sich vor der Übernahme über den Prüfbericht sichern.
- **Ergebnis speichern unter…** schreibt das bereinigte Ergebnis als UTF-8-Markdown.
  Wird die aktuelle Datei als Ziel gewählt, wird auch der Editorstand aktualisiert.
  Im schreibgeschützten Modus ist nur der Export in eine andere Datei möglich.
- **Prüfbericht speichern…** exportiert Änderungstypen, Entscheidungen und Kommentare
  als lesbaren Markdown-Bericht. Dieser Bericht ist keine wieder einlesbare Prüfsitzung.
  Er darf die aktuelle Dokumentdatei nicht überschreiben.

Markdown-Ergebnisdateien enthalten keine Prüfmarkierungen oder Kommentare. Kommentare
werden über den Prüfbericht gesichert. Beim Schließen erscheint eine Bestätigung,
wenn Prüfentscheidungen oder Kommentare noch nicht übernommen beziehungsweise exportiert sind.

## Dateien und große Dokumente

Lesen, Parsen und Vergleichen laufen im Hintergrund. Währenddessen kann der Dialog
geschlossen werden; die Vergleichsberechnung wird abgebrochen. Lese- und Schreibfehler
erscheinen im Dialog, fehlgeschlagene Exporte lassen die Prüfsitzung erhalten.
Dateien werden zunächst temporär geschrieben und anschließend ersetzt, sodass ein
Schreibfehler die bestehende Zieldatei nicht vorzeitig leert.

Bilder werden relativ zur jeweiligen Quelldatei aufgelöst. Auch fehlende Bilder behalten
beim Export ihre ursprüngliche Verzeichniszuordnung. Die Auswahl einer Änderung bewegt
die native Textauswahl, ohne das gesamte Dokument erneut aufzubauen.

Der Wortvergleich verwendet linearen Zusatzspeicher. Sehr große, nicht übereinstimmende
Bereiche mit mehr als 20 Millionen möglichen Tokenpaaren werden als zusammenhängender
Austausch gruppiert. Dabei bleibt der Ergebnisinhalt vollständig erhalten; die
Granularität der einzelnen Prüfentscheidungen wird gröber.

## Entwicklung und Tests

Die Bibliothek enthält eine neue undo-fähige Dokumentersetzung. Vor separaten
Editor-Builds die lokale Bibliothek aktualisieren:

```sh
mvn -o -pl rich-text-area/rta -Dmaven.test.skip=true install
mvn -o -pl editor test
```

Der Test-Skip im ersten Befehl umgeht bereits vorhandene, nicht zu den aktuellen APIs
passende Bibliothekstests (`FieldTest`, `StyleRegistryTest`). Die Editor-Tests prüfen
den neuen Bibliotheksbefehl einschließlich Absatzformatierungen, Bildern und Undo/Redo.
