# Lizenzabgleich der Menü- und Ribbon-Icons

Geprüft am 6. November 2026. Umfang: der lokal eingebundene Tabler-Katalog,
seine JavaFX-Ableitungen und die zugehörigen Auslieferungsressourcen.

## Herkunft und Ergebnis

Alle **143 Original-SVGs** wurden bytegenau mit `icons/outline/` des offiziellen
[Tabler-Releases v3.34.1](https://github.com/tabler/tabler-icons/tree/v3.34.1)
verglichen. Auch der vollständige [MIT-Lizenztext dieses Releases](https://github.com/tabler/tabler-icons/blob/v3.34.1/LICENSE)
ist unverändert vorhanden, einschließlich Copyright (c) 2020-2024 Paweł Kuna.
Es gab keine Abweichungen. Die zusätzliche `gallery.svg` ist eine lokale
Zusammenstellung dieser Motive und kein weiteres Original aus dem Release.

| Bestandteil | Lizenz | Abgleich und einzuhaltende Bedingungen |
|---|---|---|
| Mardexas-Code | GPL-3.0-or-later | Projektlizenz in `LICENSE`; eigene Quelltextköpfe bleiben erhalten. |
| 143 Tabler-SVGs | MIT (Expat) | Nutzung, Bearbeitung und lokale Weitergabe sind erlaubt; vollständiger Copyright- und Lizenzhinweis muss erhalten bleiben. |
| Normalisierte JavaFX-Pfade, verkleinerte Motive, kombinierte Symbol-Badges und Galerie | MIT-Anteile aus Tabler | Der Originalhinweis gilt auch für diese Ableitungen; er liegt im selben Ressourcenverzeichnis und wird mit ausgeliefert. |
| Selbst erstellte Zahlen-Badges für zuletzt verwendete Dateien | GPL-3.0-or-later | Eigene Vektorgeometrie; Herkunft im Ressourcen-README dokumentiert. |

Die FSF führt die [Expat-Lizenz als GPL-kompatibel](https://www.gnu.org/licenses/license-list.html#Expat).
Die Kombination mit der Projektlizenz ist damit möglich; die Tabler-Hinweise
werden dabei beibehalten. Dass SVGs direkt im Projekt liegen, verletzt diese
Lizenz nicht. Für den geprüften Icon-Katalog sind die erforderlichen Hinweise
vorhanden. Microsoft-Office-Grafiken werden in diesem Katalog nicht verwendet.

## Einbindung und Weitergabe

`CommandIcons` liest ausschließlich lokale Ressourcen. `commands.properties`
ordnet Befehle den Originalmotiven zu, `paths.properties` enthält die für JavaFX
normalisierten Pfade. Es wird keine externe Schrift oder Grafik nachgeladen.
Der vollständige MIT-Text ist unter **Hilfe → Lizenzierung** einsehbar und liegt
im JAR unter `de/richmarkdown/ui/icons/tabler/LICENSE`.

Die Standard-Maven-Ressourcenverarbeitung übernimmt das gesamte Verzeichnis,
einschließlich Originalen, README, Prüfsummen und Lizenz. Auch das Fat-JAR muss
diese Dateien unverändert enthalten: `packaging/build-fat-jar.sh` prüft dies vor
der Kopie nach `dist/`. Native Pakete, die dieses JAR enthalten, müssen es mit
seinen Ressourcen übernehmen. App-Image und Native-Image prüfen ebenfalls ihr
Eingangs-JAR und legen `Tabler-Icons-MIT.txt` zu den mitgelieferten Lizenztexten.
Die gemeinsame Native-Image-Konfiguration nimmt den gesamten Icon-Ordner auf.
Die Prüfung eines konkreten Pakets erfolgt am
darin enthaltenen JAR; eine Prüfung aller zukünftigen Installer wird hiermit
nicht behauptet.

## Wiederholbare Prüfung

`upstream-sha256.properties` enthält die einzeln gegen das offizielle Archiv
geprüften SHA-256-Werte sämtlicher Originale und des Lizenztexts. Die JUnit-Tests
prüfen diese Bytes über den Laufzeit-Classpath sowie die Abdeckung aller
verwendeten Motive. Geometrie, eindeutige Motive und tatsächliches Rendering
werden zusätzlich in `CommandIconsTest` geprüft.

Offizielles Archiv:
`https://codeload.github.com/tabler/tabler-icons/tar.gz/refs/tags/v3.34.1`

Archiv-SHA-256:
`c9c5b022f56b9c7ff77f95206f7a324c6c00a02859a4a4e0560fec012c7adcb4`

Lizenz-SHA-256:
`d5a3f948ae5cb95bfc4d2f767ef2bf6a778815b0ddb2d1c48037744cf636518e`

```sh
# Lokaler Bestand gegen die geprüften Prüfsummen (offline):
python3 packaging/audit-icons.py
# Optional zusätzlich gegen ein separat heruntergeladenes offizielles Archiv:
python3 packaging/audit-icons.py --upstream /tmp/mardexas-tabler-v3.34.1.tar.gz
# Alle Icon-Ressourcen einschließlich Lizenz im konkreten Auslieferungs-JAR:
python3 packaging/audit-icons.py --jar /pfad/zur/anwendung.jar
```

Bei einem Release- oder Icon-Wechsel müssen Herkunft, Lizenz und Prüfsummen
erneut gegen die Originalquelle abgeglichen werden. Die Prüfung ist bewusst
auf diesen Icon-Bestand begrenzt; die übrigen Abhängigkeiten sind separat in
`THIRD-PARTY-NOTICES.md` aufgeführt. Der Ersatz der früheren Apple-Emoji-Bilddateien durch lokale Noto-Grafiken wird
in [EMOJI_LICENSE_AUDIT.md](EMOJI_LICENSE_AUDIT.md) separat geprüft; diese Bilder
gehören nicht zum hier geprüften Tabler-Katalog.

## Durchgeführte Validierung

Am Prüftag bestanden `mvn -o -f editor/pom.xml
-Dmardexas.build.directory=/tmp/mardexas-index-license verify -Pfatjar`
mit 1.124 Testfällen, ohne Fehler und mit einem übersprungenen Druckertest
(kein Drucker verfügbar). Checkstyle und SpotBugs meldeten jeweils null Befunde;
die JaCoCo-Vorgaben wurden erfüllt. Die Icon-Prüfung bestand sowohl für das
normale JAR als auch für das Fat-JAR und zusätzlich gegen das offizielle Archiv.
Sechs Python-Tests prüfen insbesondere manipulierte oder fehlende Lizenztexte,
unbekannte Icons und ein falsches Upstream-Archiv. Diese Prüfungen laufen auch
in der CI. Die nativen Installer wurden bei diesem Abgleich nicht gebaut.
