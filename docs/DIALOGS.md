# Anwendungsdialoge

Alle Eingabedialoge verwenden `dialogs.css` und die lokalisierten `DialogButtons`.
Der gemeinsame Stil setzt Abstände, Feldbeschriftungen, Sektionen, primäre
Aktionen und Inline-Fehler. Primäre Dialogaktionen unterstützen ein Mnemonic
(Alt + erster Buchstabe); Enter bestätigt, Escape bricht ab. Suchen/Ersetzen
und Ordnersuche bleiben modellose Fenster. Mehrzeilige Eingaben und Vorschauen
lassen sich vergrößern.

Die separaten Dialogfenster und Suchpanels übernehmen die gespeicherten
Einstellungen für Dunkelmodus, hohen Kontrast und Oberflächenschriftgröße.
Neue Sprachschlüssel sind in allen zehn Sprachdateien enthalten. Für neue
Schlüssel außerhalb Deutsch/Englisch sind teilweise englische Ersatztexte
hinterlegt.

Link, Bild, Codeblock und Vorlagenfelder benötigen eine nichtleere Eingabe.
Tabellengrößen werden anhand des tatsächlich sichtbaren Spinnertexts geprüft:
1–20 Zeilen, 1–12 Spalten. Druckoptionen prüfen Ränder, Schriftgröße,
Seitenbereiche und die Verfügbarkeit einer Textauswahl direkt bei Änderungen.
Ungültige Werte deaktivieren die Bestätigung und zeigen einen Inline-Hinweis.
Die Link-Sicherheitsprüfung bleibt beim aufrufenden Editor.

Tabellengröße, Druckerauswahl/Druckeinstellungen und Drucklayout werden erst
bei Bestätigung in `ApplicationSettings` gespeichert und beim nächsten Öffnen
wiederhergestellt. Abbrechen speichert keine Eingaben. Fehlende Drucker und
ungültige gespeicherte Werte führen zu einer sicheren Vorauswahl bzw. zu einer
bereits deaktivierten Druckaktion.

Unter **Datei → Einstellungen → Vorlagenverzeichnis…** oder direkt im
Vorlagendialog lässt sich ein eigener Ordner wählen. Er wird im
Anwendungsprofil unter `features.templates.directory` gespeichert. Die vier
mitgelieferten Vorlagen bleiben verfügbar. Reguläre UTF-8-Dateien mit Endung
`.md` oder `.markdown` aus dem gewählten Ordner ergänzen die Liste. Die
Großschreibung der Endung ist unerheblich. Ein gleicher Dateistamm ersetzt
die mitgelieferte Vorlage an ihrer bisherigen Position. Zusätzliche Vorlagen
werden nach Dateinamen sortiert angehängt. Der Dialog lädt bis zu 200 zusätzliche
Vorlagen mit höchstens 256 KiB pro Datei aus dem gewählten Ordner.
Fehlende, ungültige oder nicht lesbare Ordner werden ignoriert; einzelne
unlesbare Dateien verhindern nicht das Laden der übrigen Vorlagen. Ein
ungültiger gespeicherter Ordner wird im Dialog angezeigt. Ein leerer Ordner
stellt wieder ausschließlich die mitgelieferten Vorlagen bereit.

Regressionstests: `DialogProfessionalizationTest` sowie die bestehenden
Dialogtests prüfen Validierung, Ergebnisse, Abbruch, Persistenz,
Vorlagenzusammenführung, Sprachwechsel und Themeneinstellungen. Die Tests
laufen mit TestFX/Monocle ohne sichtbares Desktopfenster.

## Einfügen und Bearbeiten

- **Bild** nimmt eine lokale Datei über Dateiauswahl oder Ziehen und Ablegen an.
  Die Zwischenablage kann eine Datei, eine Adresse oder Bilddaten liefern.
  Bilddaten werden nach Auswahl eines Speicherorts als PNG gespeichert. Eingefügte
  Bilder erhalten auf Wunsch Alternativtext und feste Abmessungen. Die Vorschau
  zeigt Originalgröße und Ladefehler. Für gespeicherte Dokumente kann eine lokale
  Datei mit kollisionsfreiem Namen in den Unterordner `images` kopiert werden.
  Relative Pfade sind auf das Dokumentverzeichnis bezogen.
- **Link und Codeblock** übernehmen die Auswahl oder erkennen ein bestehendes
  Element an der Schreibmarke. Links haben einen sichtbaren Text und können auf
  eine Datei oder Dokumentüberschrift zeigen. Beim Entfernen bleibt der Linktext
  erhalten. Der Codeassistent zeigt eine Vorschau und übernimmt Sprache und Code.
  Änderungen an Bild, Link und Codeblock sind jeweils in einem Schritt rückgängig
  zu machen.
- **Tabellen** bieten Spaltennamen, Ausrichtung und eine Vorschau. CSV und TSV
  lassen sich mit gewähltem Trennzeichen und optionaler Kopfzeile prüfen. Die
  Vorschau für Tabellenoperationen zeigt das Ergebnis vor der Übernahme.
  Sortierungen können mehrere Kriterien mit eigener Richtung und Vergleichsart
  enthalten.
- **Schreibhilfen** bieten eine Suche nach Unicode-Zeichen und Textbausteinen.
  Datum und Uhrzeit unterstützen eigene Formatmuster mit Vorschau. Vorlagen und
  Bibliothekseinträge lassen sich als Markdown anzeigen. Vorlagenfelder können
  mehrere Zeilen enthalten. Die Bibliothek fragt beim Verlassen eines Entwurfs
  nach und unterstützt Markdown-Export.

## Suchen, Prüfen und Drucken

- Suchergebnisse zeigen Fundstellen im Kontext. Beim Ersetzen lassen sich einzelne
  Treffer abwählen. Die Ordnersuche filtert nach Dateimustern und ausgeschlossenen
  Verzeichnissen. Die Vorschau zeigt den Text vor und nach dem Ersetzen. Der Dialog
  erfasst die beim Ersetzen erstellten Sicherungen und kann den vorherigen Inhalt
  wiederherstellen, sofern Datei und Sicherung seitdem unverändert sind.
- Die Markdownprüfung filtert Hinweise nach Schweregrad und Regel. Aus dem Dialog
  lässt sich zur Fundstelle springen. Die Linkübersicht erfasst Ziele und kann sie
  auf Anforderung prüfen. Beim Sammelersatz wird der Umfang vorab angezeigt.
- Der Druckdialog vereint Drucker, Papier und Layout. Die Vorschau verwendet
  dieselbe Seiteneinteilung wie der Druck und berücksichtigt Ränder, Kopf- und
  Fußzeile sowie die gewählte Seitenauswahl.
- Anzeigeoptionen sind sofort sichtbar. Abbrechen stellt die bisherige Darstellung
  wieder her. Die Befehlszentrale meldet Konflikte beim Aufzeichnen von Kürzeln.
  Beim Schließen geänderter Dokumente stehen Speichern, Verwerfen und Abbrechen
  zur Wahl. Bei Konflikten mit Änderungen auf Datenträger sind Vergleichen,
  Neuladen und Speichern unter verfügbar.

Die Ablaufprüfungen in `DialogWorkflowTest` und `DialogStateWorkflowTest` ergänzen
Einzeltests für Bildmetadaten, Tabellensortierung und die Wiederherstellung nach
Ordnerersetzungen.
