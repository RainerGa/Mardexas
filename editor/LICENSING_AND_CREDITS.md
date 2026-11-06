# Lizenzierung und Dank

Die Lizenzansicht unter **Hilfe → Lizenzierung** enthält vier Reiter:
Anwendung, Bibliotheken, Icons und Entwicklung & Tests. Jede Komponente hat
eine eigene Karte mit Name, Version, Urheber/Projektteam, Lizenzkennung,
Quellcode-Adresse und Adresse des vollständigen Lizenztextes. Die Adressen
lassen sich markieren und kopieren. Die Entwicklungskomponenten sind ausdrücklich
als nicht für den Betrieb des Editors benötigt gekennzeichnet.

**Icons** nennt Tabler Icons 3.34.1 und Paweł Kuna und zeigt die vollständig
mitgelieferte, unveränderte MIT-Lizenz. Original-Icons und Herkunftsnachweise
bleiben erhalten; siehe [ICON_LICENSE_AUDIT.md](ICON_LICENSE_AUDIT.md).

**Hilfe → Danke** ist auch im Ribbon erreichbar. Das Fenster dankt den Autoren,
Teams und allen Beitragenden sämtlicher Laufzeit-, Icon- und Testkomponenten.
Alle Bedienelemente und erklärenden Texte sind in den zehn unterstützten Sprachen
vorhanden. Eigennamen, Lizenzkennungen und Original-Lizenztexte bleiben unverändert.

Der Anwendungseinstieg zeigt den Dank nach Sprachauswahl und Öffnen des
Hauptfensters beim ersten Start an. Erst nach erfolgreicher Anzeige wird
`features.acknowledgements.seen=true` im lokalen Benutzerprofil gespeichert.
Weitere Dokument-Tabs erzeugen keine zusätzlichen Erststart-Fenster. Auf einem
anderen Rechner oder unter einem anderen Benutzerprofil erscheint der Dank erneut.
Ein manueller Aufruf über Hilfe ändert den Erststart-Merker nicht. Bei fehlenden
Schreibrechten bleibt das Fenster benutzbar und erklärt, dass es beim nächsten
Start erneut erscheinen kann. Markdown-Dateien werden dadurch nicht verändert.

`ComponentCredits` ist die gemeinsame, unveränderliche Datenbasis beider
Fenster. Bei Abhängigkeitsupdates sind diese Liste und
[THIRD-PARTY-NOTICES.md](../THIRD-PARTY-NOTICES.md) gemeinsam zu aktualisieren.
JUnit Jupiter 5.11.3 verwendet JUnit Platform 1.11.3; beide Versionsnummern werden
angegeben. Das zusätzliche Stylesheet ist auch für Native Image registriert.

## Emoji-Bilder: lokaler Ersatz

Die frühere Apple-Emoji-Abhängigkeit wurde durch `emoji-noto/` ersetzt. Der
Gluon-Code bleibt GPL-3.0-or-later; die reinen Google-Noto-Clean-Sheets stehen
unter Apache-2.0. Die gesonderten Originalhinweise zu Flaggen und die MIT-Lizenz
von emoji-data werden ebenfalls mitgeliefert. Es gibt keinen Bild-Download und
keinen Zugriff auf alte Emoji-Caches. Noto und emoji-data erscheinen als eigene
Komponenten im Lizenz- und Danke-Fenster. Die Originaltexte lassen sich in der
Lizenzansicht aufklappen und kopieren. Alle zehn Sprachen erklären den Ersatz.
Quellen, Prüfsummen, Tests und Paketprüfung: [EMOJI_LICENSE_AUDIT.md](EMOJI_LICENSE_AUDIT.md).

## Prüfungen

`CreditsDialogTest` zeigt echte JavaFX-Fenster und verwendet den Glass-Robot:
Reiterwechsel, sämtliche Komponenten und Urhebergruppen in allen zehn Sprachen,
unveränderter MIT-Text, Schließen mit Maus und Escape, Wechsel vom Dank zu den
Lizenzen, Sprachressourcen ohne Fallback, unveränderliche Datenbasis,
Erststart-Persistenz über Profil-Neuladen, unabhängige Profile sowie nicht lesbare
und nicht beschreibbare Einstellungsdateien. Andere Einstellungen bleiben erhalten.

`RibbonSurfaceGuiTest` öffnet beide Dialoge über tatsächliche Klicks in klassischem
Menü und Ribbon und prüft dabei auch den unveränderten Dokumentinhalt und Cursor.
`CommandIconsTest` prüft eindeutige Icon-Geometrie und gerenderte Pixel sowie die
Lizenz- und Herkunftsdateien. Die Paketprüfung erfolgt zusätzlich mit
`packaging/audit-icons.py --jar <FAT-JAR>`.

Die bestehenden Cursor-/Viewport-Tests in `EditingPositionTest` warten auf
abgeschlossene Renderingzyklen anstelle bloßer Durchläufe der FX-Ereigniswarteschlange.
Dadurch wird die Anfangs-Scrollposition erst nach dem tatsächlichen Layout geprüft;
die ursprünglichen Anforderungen an Cursor, Auswahl, Scrollstand und Undo/Redo
bleiben erhalten.

Screenshots lassen sich bei den Dialogtests optional mit
`-Dmardexas.credits.preview=/tmp/mardexas-credits-preview` erzeugen.

## Validierung am 6. November 2026

`mvn -o -f editor/pom.xml -Dmardexas.build.directory=/tmp/mardexas-credits verify -Pfatjar`
bestand mit 1.142 Testfällen: null Fehler, ein übersprungener Druckertest
(kein Drucker verfügbar). Checkstyle und SpotBugs meldeten keine Befunde.
Die JaCoCo-Vorgaben wurden erfüllt. Beide JAR-Varianten bestanden den
Tabler-Ressourcencheck; das FAT-JAR zusätzlich den Abgleich mit dem offiziellen
Archiv. Auch die sechs Python-Tests der Icon-Prüfung bestanden.

Der damalige separate Apple-Emoji-Befund wurde anschließend durch den oben
beschriebenen Noto-Ersatz behoben; siehe dessen gesonderte Validierung.
