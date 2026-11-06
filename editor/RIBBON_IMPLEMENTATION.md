# Klassisches Menü und Ribbon

Unter **Ansicht → Bedienoberfläche** kann zwischen **Klassisches Menü** und **Ribbon** gewechselt werden. Die Einstellung gilt auch für weitere Dokumente und Fenster und wird im Benutzerprofil gespeichert. Bei einem Speicherfehler bleibt die gewählte Oberfläche für die Sitzung verwendbar; eine Fehlermeldung informiert über das Problem. Markdown-Dateien erhalten keine zusätzlichen Einstellungen oder Metadaten.

Das Ribbon enthält die Register Datei, Start, Einfügen, Dokument, Prüfen, Ansicht, Tabelle, Fenster und Hilfe. Das Register Start zeigt die häufigen Schreibfunktionen. Die Befehle sind nach Aufgaben gruppiert: große Hauptaktionen mit Symbol über der Beschriftung, kompakte Formatierungssymbole und bündige Reihen kleinerer Aktionen. Gruppentitel und Trennlinien bilden eine feste Orientierung; seltenere Befehle liegen in beschrifteten Gruppenmenüs. Bei schmalen Fenstern werden Gruppen schrittweise kompakter. Die Leiste kann über die Schaltfläche rechts oben eingeklappt werden. F10 beziehungsweise Alt fokussiert die Bedienoberfläche; Escape führt aus dem Ribbon zum Editor zurück. Die Befehlssuche ist über Strg+Umschalt+P erreichbar (auf macOS mit der systemüblichen Befehlstaste). Favoriten und angepasste Tastenkürzel gelten für beide Ansichten.

## Gemeinsame Befehle

`CommandIdentity` vergibt semantische IDs und erhält bisherige positionsabhängige IDs als Migrationsaliase. `WindowCommandRegistry` verbindet die bestehenden Fachaktionen mit `EditorCommand`, dem beobachtbaren Befehlszustand. Klassische Menüs, Ribbon, Befehlssuche und Favoriten greifen auf diese Grundlage zu. Vor jeder Ausführung wird der Kontext erneut geprüft. Fehler werden angezeigt, deaktivierte Befehle nicht ausgeführt. Formatierungszustände berücksichtigen auch ausgewählte und gemischt formatierte Texte.

`CommandSurfaceController` tauscht die Bedienleiste aus, ohne den Editor neu zu erzeugen. Er erhält Dokument, Auswahl und Undo-Historie und stellt nach dem Layout den sichtbaren Absatz samt Pixelversatz wieder her. `UiModeService` verteilt Änderungen und behandelt Profilfehler. Listener und Bindungen werden beim Schließen entfernt. `CommandDocumentSnapshot` hält einen Export pro Dokumentrevision vor; Cursorbewegungen lösen keinen erneuten vollständigen Export aus. Kontextmeldungen werden zusammengefasst. Ribbon-Register werden bei Bedarf aufgebaut.

## Icons und Herkunft

`CommandIcons` erzeugt für jedes Control eigene JavaFX-Vektorknoten aus einem gemeinsamen Geometriekatalog. Die Zuordnung verwendet Tabler Icons **v3.34.1** unter MIT-Lizenz und dokumentierte geometrische Ergänzungen für Befehlsvarianten. Dynamische Dateieinträge erhalten eigene Nummern. Original-SVGs, Lizenz, Herkunft und eine Galerie liegen unter `src/main/resources/de/richmarkdown/ui/icons/tabler/` und werden mit ausgeliefert. Die Lizenz ist außerdem in der Hilfe zugänglich. Weitere Angaben enthält [THIRD-PARTY-NOTICES.md](../THIRD-PARTY-NOTICES.md).

Ein lokales Befehlsinventar als Markdown und TSV lässt sich mit `CommandInventoryTest` aus tatsächlich aufgebauten Fenstern erzeugen (siehe unten). Die erzeugten Dateien werden von Git ignoriert. Tests prüfen vollständige Zuordnung und Erreichbarkeit, unterschiedliche Geometrien und gerenderte Pixel sowie helle, dunkle und kontrastreiche Darstellung. Die frühere PNG-Ressource für Dateiaktionen wurde ersetzt.

## Prüfung

Die Erweiterung umfasst Unit-Tests und GUI-Regressionstests mit tatsächlich dargestellten JavaFX-Stages, Robot-Eingaben und abgewartetem Layout. Geprüft werden unter anderem Kontextzustände, Tastenkürzel ohne Mehrfachausführung, Formatierung einschließlich Ausschalten und gemischter Auswahl, Undo/Redo, Schreibschutz, Umschaltung im langen Dokument, Cursor und sichtbarer Textanker, mehrere Dokumente und geteilte Ansichten, schmale Fenster, Menüüberlauf, Fokus, Favoritenmigration, Profilfehler, Ressourcen und Listenerabbau.

Die GUI-Suite verwendet das Monocle-Testtoolkit. Damit werden echte JavaFX-Controls und ihr Layout geprüft; native Fenstermanager und betriebssystemspezifische Tastaturbesonderheiten aller Zielsysteme sind dadurch nicht vollständig abgedeckt. Der Druckertest wird ohne vorhandenen Drucker übersprungen.

Die 30 gezielten Ribbon-GUI-Tests prüfen außerdem gemeinsame Unterkanten der Gruppentitel, getrennte Trefferflächen mit mindestens einem Pixel Abstand, große Hauptaktionen, kompakte Formatierungssymbole, Gruppenmenüs und Einklappen. Die Ribbon-Höhe folgt dem bevorzugten Inhaltsmaß statt einer festen Pixelhöhe. Gruppentitel haben 12-Pixel-Schrift und Abstand zum Editor; Tests prüfen vollständige Beschriftungen und mindestens sechs Pixel unteren Freiraum in allen neun Registern bei 13 und 16 Pixeln Oberflächenschrift. Bei 500 und 700 Pixeln Fensterbreite werden die tatsächlich gerenderten Zeichen der Gruppennamen und ihre Grenzen geprüft, um Ellipsen und abgeschnittene Beschriftungen zu erkennen. Menütests klicken die sichtbaren Menüeinträge statt mehrdeutiger globaler Texttreffer an. Das virtuelle Testdisplay ist ausdrücklich auf 1600 × 1000 Pixel eingestellt.

Reproduzierbarer Gesamtlauf aus dem Repository-Verzeichnis:

```sh
mvn -o -f editor/pom.xml -Dmardexas.build.directory=/tmp/mardexas-ribbon-verify verify
```

Ein eigenes Build-Verzeichnis verhindert Konflikte mit gleichzeitig laufenden Eclipse-Builds. Der Lauf umfasst Tests, Checkstyle, SpotBugs und die JaCoCo-Prüfung. Für das Inventar kann `-Dmardexas.inventory.output=/absoluter/pfad/zum/editor` bei `CommandInventoryTest` ergänzt werden; `-Dmardexas.preview.output=/tmp/mardexas-ribbon-preview.png` erzeugt zusätzlich Oberflächenabbildungen.

Verifiziert am 6. November 2026 mit Java 25 und OpenJFX 25.0.4: 1114 Testfälle, keine Fehler, ein mangels Drucker übersprungener Test; Checkstyle und SpotBugs ohne Befund. Die gemessene Zeilenabdeckung des gesamten Editor-Moduls beträgt 82,89 % (8417 von 10155 Zeilen), die konfigurierte Mindestabdeckung 75 %. Das ist keine vollständige Zeilenabdeckung. Im gebauten JAR wurden alle 144 SVG-Ressourcen sowie Herkunft und MIT-Lizenz geprüft.
