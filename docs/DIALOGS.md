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
`.md` aus dem gewählten Ordner ergänzen die Liste; gleicher Dateistamm ersetzt
die mitgelieferte Vorlage an ihrer bisherigen Position. Zusätzliche Vorlagen
werden nach Dateinamen sortiert angehängt. Unterordner werden nicht durchsucht.
Fehlende, ungültige oder nicht lesbare Ordner werden ignoriert; einzelne
unlesbare Dateien verhindern nicht das Laden der übrigen Vorlagen. Ein
ungültiger gespeicherter Ordner wird im Dialog angezeigt. Ein leerer Ordner
stellt wieder ausschließlich die mitgelieferten Vorlagen bereit.

Regressionstests: `DialogProfessionalizationTest` sowie die bestehenden
Dialogtests prüfen Validierung, Ergebnisse, Abbruch, Persistenz,
Vorlagenzusammenführung, Sprachwechsel und Themeneinstellungen. Die Tests
laufen mit TestFX/Monocle ohne sichtbares Desktopfenster.
