# Emoji-Ersatz und Herkunftsnachweis

Stand: 6. November 2026. Die frühere Gluon-Offline-Abhängigkeit lieferte
Apple-Bilddateien ohne gesonderten Nachweis der Weitergaberechte. Sie wurde
vollständig aus der Anwendung entfernt. Ein bloßer Wechsel auf den ursprünglichen
Online-Loader wäre unzureichend gewesen: Dieser hätte wieder Apple-Bilder geladen.

## Verwendeter Ersatz

Das neue Reactor-Modul `emoji-noto/` bewahrt die öffentliche API von Gluon Emoji
1.1.0 und dessen Unicode-Erkennung. Der Quellcode stammt aus dem offiziellen
Maven-Sources-JAR; Download-Adresse, SHA-256 des Archivs und Originaldateien
stehen in `emoji-noto/upstream-source-sha256.json`. Die Original-Copyright-Hinweise
und GPL-3.0-or-later bleiben erhalten. Geändert sind:

- Die Factory liefert ausschließlich den lokalen `NotoEmojiSpriteLoader`.
- `DownloadableEmojiSpriteLoader` wurde nicht übernommen. Es gibt keine
  Provider-Suche, Reflexion, Netzwerkanfragen oder Zugriffe auf alte Bild-Caches.
- Der Loader liest nur drei fest benannte lokale Google-Clean-Sheets.
  Unzulässige Größen, fehlende und defekte Ressourcen führen zu expliziten Fehlern.
- `EmojiImageUtils` kopiert hochaufgelöste Glyphen mit dem JavaFX-Bulk-Crop.
  Bilder werden erst bei Bedarf geladen; die bisherigen Größen-Caches bleiben.

Die Unicode-Daten bleiben bytegleich. Markdown-Dateien enthalten weiterhin
gewöhnliche Unicode-Zeichen; es werden keine Bildverweise oder Sonderformate
eingefügt. Neuere, noch nicht in der Datenbank enthaltene Zeichen bleiben Text.

## Getrennte Lizenzen

| Bestandteil | Quelle | Lizenz / Hinweis |
|---|---|---|
| Bibliothekscode | Gluon Emoji 1.1.0, lokaler Mardexas-Fork | GPL-3.0-or-later |
| Emoji-Bilder | Noto Emoji v2.038, Google LLC und Beitragende | Apache-2.0 |
| Länderflaggen | Noto `third_party/region-flags/LICENSE` | Laut Originalhinweis gemeinfrei oder vom Urheberrecht ausgenommen; vollständiger gesonderter Hinweis enthalten |
| Daten und Anordnung | emoji-data, Cal Henderson | MIT, Copyright (c) 2013 Cal Henderson |

Es werden keine Noto-Fonts ausgeliefert. Die Font-Lizenz wird daher nicht mit
der Lizenz der PNG-Bilder vermischt. Der vollständige Apache-Text, Flaggenhinweis,
MIT-Text, GPL-Text, beide Original-READMEs und ein eigener Änderungs-/Quellenhinweis
liegen unter `com/gluonhq/emoji/noto/` im Ersatz-JAR und im Fat-JAR. Lizenzdialog
und Danke-Fenster nennen Noto und emoji-data separat; der Lizenzdialog zeigt
Originaltexte aufklappbar. Der Erklärungstext ist in allen zehn Sprachen vorhanden.

Primärquellen:

- [Noto v2.038: Original-Lizenz](https://github.com/googlefonts/noto-emoji/blob/v2.038/LICENSE)
- [Noto v2.038: Lizenzzuordnung im README](https://github.com/googlefonts/noto-emoji/blob/v2.038/README.md#license)
- [Noto: gesonderter Flaggenhinweis](https://github.com/googlefonts/noto-emoji/blob/v2.038/third_party/region-flags/LICENSE)
- [emoji-data: exakt verwendeter Quellenstand](https://github.com/iamcal/emoji-data/tree/063f328d7951cb2e2a6678b06dcbdf8dd599fad7)

Es werden ausdrücklich die Dateien aus `sheets-clean/` verwendet. Laut README
dieses Quellenstands enthalten diese ausschließlich Bilder des jeweiligen
Anbieters. Normale Google-Sheets können hingegen fremde Ersatzgrafiken enthalten.
Alle drei Clean-Sheets wurden unverändert übernommen, mit genau demselben
Quellenstand wie die von Gluon erzeugte `emoji.csv`. Dadurch bleiben auch
Hauttöne und zusammengesetzte Emoji den richtigen Bildpositionen zugeordnet.

| Originaldatei | SHA-256 |
|---|---|
| `sheet_google_20_clean.png` | `93a0ead1cf0f82b4b4406dc372a62d75a501954d759a5b497a7de9e47d5aa78e` |
| `sheet_google_32_clean.png` | `113896ca5f7d6e9c01edcdb5ba9aeca22bd94c9b541111da1c24616735f2e37a` |
| `sheet_google_64_clean.png` | `12f4498fbcb18f1c7291fecdd971573bf6bca6d342041ee3676d42d3de7dc174` |

## Automatische Prüfungen

`NotoEmojiGuiTest` prüft den ausschließlich lokalen Loader, Initialisierung,
unzulässige Größen, fehlende alte Loader/Apple-Ressourcen, Originalprüfsummen,
Lizenztexte und sämtliche Unicode-Bildkoordinaten in allen drei Auflösungen.
Im tatsächlich angezeigten Editor prüft der Glass-Robot farbige Pixel,
gewöhnliche Emoji, Hauttöne, ZWJ-Sequenzen und Flaggen sowie Klicken, Eingabe
und Undo/Redo neben den Bildern. Anzeige-Hauttöne und unbekannte Unicode-Zeichen
dürfen den gespeicherten Markdown-Text nicht verändern.

`packaging/audit-emoji.py` prüft offline die Originalbytes, alle Originalhinweise,
das Ersatz-JAR/Fat-JAR oder sämtliche Laufzeit-JARs eines Installer-Verzeichnisses.
Alte Gluon-Emoji-Abhängigkeiten, Apple-Sheets, fremde/mischende Sheets und der
Download-Loader führen zum Abbruch. Sieben Python-Regressionstests sichern auch
manipulierte und fehlende Dateien sowie zusätzliche alte Abhängigkeiten ab.
Alle drei Paketbauskripte führen die Prüfung vor der Auslieferung durch. Die
Native-Image-Konfiguration registriert Daten, Bilder und Lizenzressourcen.

```bash
python3 -B -m unittest discover -s packaging -p test_audit_emoji.py
python3 packaging/audit-emoji.py
python3 packaging/audit-emoji.py --jar /pfad/zum/emoji-noto-1.1.0-mardexas-1.jar
python3 packaging/audit-emoji.py --jar /pfad/zum/Mardexas-fat.jar
python3 packaging/audit-emoji.py --input-dir editor/target/jpackage-input
```

Bei Updates müssen Code, Daten, ausschließlich reine Google-Grafiken und
deren Lizenznachweise gemeinsam geprüft werden. Die Prüfung dokumentiert die
konkret ausgelieferten Ressourcen; sie ist keine allgemeine Rechtsgarantie.

## Bereits vorhandene Auslieferungspakete

Das vorhandene `dist/Mardexas-1.0.0-fat.jar` enthielt nachweislich die drei
Apple-Sheets und wurde durch das neu gebaute, geprüfte Fat-JAR ersetzt. Auch
der alte native Linux-Build enthielt den früheren `LocalEmojiSpriteLoader`.
Er wurde aus `dist/native/linux` in eine temporäre Sicherung ausgelagert; ein
neuer nativer Build wurde bei diesem Auftrag nicht erstellt. Vor einer nativen
Auslieferung ist `JAVAFX_PLATFORM=linux packaging/build-native-image.sh` auszuführen.

Temporäre Sicherung der alten, nicht auszuliefernden Pakete: `/tmp/mardexas-retired-apple-fvhy9dh3`.
Das neue Fat-JAR ist `dist/Mardexas-1.0.0-fat.jar` (34.911.025 Bytes).

## Validierung am 6. November 2026

Der vollständige abschließende Lauf
`mvn -o -f editor/pom.xml -Dmardexas.build.directory=/tmp/mardexas-noto verify -Pfatjar`
bestand mit 1.154 Testfällen, null Fehlern und einem übersprungenen Druckertest
(kein Drucker verfügbar). Checkstyle und SpotBugs meldeten null Befunde; die
JaCoCo-Vorgaben wurden erfüllt. Die elf Emoji-Tests und 27 Lizenzdialog-Tests
sind darin enthalten. Zusätzlich bestanden alle 13 Python-Tests der Icon- und
Emoji-Prüfungen.

Das neue Fat-JAR bestand beide Ressourcenprüfungen. Ein mit
`dependency:copy-dependencies` erzeugtes vollständiges Laufzeitverzeichnis bestand
ebenfalls die Emoji-Prüfung; der Dependency Tree enthält nur `emoji-noto`, keine
ursprünglichen Gluon-Emoji-Artefakte. Ein separater Java-Prozess auf dem echten
Modulpfad konnte alle vier im Dialog angezeigten Emoji-Lizenz-/Hinweisdateien
lesen. Die Ressourcenfreigabe des Moduls wird zusätzlich im Unit-Test geprüft.
Der gesamte Reactor ließ sich mit den neuen Abhängigkeiten bauen und installieren.

Die Angaben zu den ursprünglichen Bildkoordinaten wurden gegen das originale
`emoji.json` desselben Commits geprüft: alle 1.875 Basiseinträge stimmen überein;
die Quelle enthält mit Hauttönen insgesamt 3.660 Einträge.
