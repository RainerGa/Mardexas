# Mardexas

![Mardexas Screenshot](screenshot.png)

![Mardexas Screenshot – Tabellen](screenshot_tables.png)

**Markdown schreiben, ohne jedes Formatierungszeichen von Hand setzen zu müssen.**
Mardexas ist ein Desktop-Markdown-Editor mit einer Rich-Text-WYSIWYG-Oberfläche.
Bearbeiten Sie Ihr Dokument visuell und speichern Sie es dann als UTF-8-Markdown-
Datei, die Sie in einem Texteditor öffnen, in Git überprüfen oder mit anderen
Markdown-Tools verwenden können.

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
![Java 25](https://img.shields.io/badge/Java-25-orange.svg)
![JavaFX 25.0.4](https://img.shields.io/badge/JavaFX-25.0.4-royalblue.svg)
![Linux packages](https://img.shields.io/badge/Linux-Flatpak%20%7C%20portable-lightgrey.svg)
![Windows packages](https://img.shields.io/badge/Windows-MSI%20%7C%20portable-lightgrey.svg)
![macOS package](https://img.shields.io/badge/macOS-portable-lightgrey.svg)
[![GitHub repository](https://img.shields.io/badge/GitHub-Private-lightgrey?logo=github)](https://github.com/RainerGa/Mardexas)

> **Verfügbarkeit:** Das Repository ist derzeit privat und bietet noch keinen
> veröffentlichten Download. Release-Pakete werden als Entwurf zur Überprüfung
> vorbereitet; sie werden erst nach der expliziten Veröffentlichung des Releases
> zum Download verfügbar.

## Inhaltsverzeichnis

- [Warum Mardexas?](#warum-mardexas)
- [Erste Schritte](#erste-schritte)
- [Release-Pakete](#release-pakete)
- [Welches Markdown wird unterstützt?](#welches-markdown-wird-unterstützt)
- [Aus dem Quellcode bauen und ausführen](#aus-dem-quellcode-bauen-und-ausführen)
- [Projektstruktur](#projektstruktur)
- [Lizenz und Drittanbieter-Software](#lizenz-und-drittanbieter-software)
- [Aktueller Validierungsstatus](#aktueller-validierungsstatus)
- [English](#english)

## Warum Mardexas?

Mardexas ist für Menschen, die die Portabilität und Einfachheit von Markdown
schätzen, aber einen visuellen Bearbeitungs-Workflow bevorzugen. Sie können
sich auf den Inhalt konzentrieren und die Symbolleiste und Menüs für die gängigen
Formatierungen nutzen, anstatt Syntax zu memorieren.

- **Bestehende Dokumente bearbeiten:** Öffnen Sie `.md`, `.markdown` und `.txt`
  Dateien; lesen und schreiben Sie den Dokumenttext als UTF-8. Markdown-Dateien
  werden standardmäßig mit `.md` gespeichert.
- **Visuell formatieren:** Wenden Sie Überschriften, Hervorhebungen, Listen,
  Zitate, Code, Links, Bilder, Trennlinien und Tabellen direkt im Editor an.
- **Den Überblick behalten:** Durchsuchen Sie Dokumentüberschriften im
  Strukturpanel und zeigen Sie optional Zeilennummern pro Absatz an.
- **Den Arbeitsbereich anpassen:** Ändern Sie den Zoom, prüfen Sie die
  Cursorposition und den Absatzstil in der Statusleiste, und drucken Sie das
  Dokument.
- **Ihre Sprache verwenden:** Die Oberfläche ist verfügbar in:

  🇩🇪 Deutsch · 🇬🇧 Englisch · 🇫🇷 Französisch · 🇪🇸 Spanisch · 🇧🇷 Brasilianisches
  Portugiesisch · 🇯🇵 Japanisch · 🇨🇳 Vereinfachtes Chinesisch · 🇮🇹 Italienisch
  · 🇰🇷 Koreanisch · 🇳🇱 Niederländisch

Die Anwendung und ihre Benutzeroberfläche verwenden derzeit den Namen
`RichMarkDownEditor`; das GitHub-Projekt heißt **Mardexas**.

## Erste Schritte
Wenn Sie mit dem Bauen von Java-Projekten vertraut sind, können Sie die
aktuelle Quellversion ausprobieren:

1. Installieren Sie [JDK 25](https://adoptium.net/) und
   [Maven](https://maven.apache.org/).
2. Klonen Sie das Repository zusammen mit seinem RichTextArea-Untermodul:

   ```sh
   git clone --recurse-submodules https://github.com/RainerGa/Mardexas.git
   cd Mardexas
   ```

3. Starten Sie den Editor unter Linux:

   ```sh
   ./editor/start.sh
   ```

Das Skript baut das Projekt, bevor es den Editor startet. Das Repository ist
derzeit privat, sodass ein GitHub-Zugriff zum Klonen erforderlich ist.

## Release-Pakete

Der Release-Workflow ist so konfiguriert, dass Pakete mit einer gebündelten
Java-Laufzeit gebaut werden, sodass Endbenutzer Java nicht installieren müssen:

| Plattform | Release-Dateien | Verwendung |
| --- | --- | --- |
| Linux x86-64 | Flatpak (`.flatpak`), portable Laufzeit `.tar.gz` und GraalVM Native `.tar.gz` | Installieren Sie das Flatpak mit `flatpak install --user ./Mardexas-<version>-linux-x86_64.flatpak`; führen Sie es mit `flatpak run io.github.rainerga.Mardexas` aus. Das Laufzeit-Archiv enthält Java; das Native-Archiv enthält `Mardexas`, das nach dem Entpacken ausgeführt werden kann. |
| Windows x86-64 | MSI-Installer und portable Laufzeit `.zip` | Führen Sie den MSI aus, oder extrahieren Sie das ZIP. Das portable Paket wird mit `Mardexas\bin\Mardexas.exe` gestartet. |
| macOS | Portables `.zip` mit `Mardexas.app` | Extrahieren Sie das Archiv und verschieben Sie die App ggf. in den Programme-Ordner. Der Dateiname identifiziert die Build-Architektur. |
Der Linux GraalVM Native-Executable wird auf einem Linux GitHub Actions Runner
gebaut und vor dem Anhängen an ein Draft-Release mit einem virtuellen Display
rauchgetestet. Er ist dynamisch verlinkt und benötigt kompatible Systemgrafik-
bibliotheken und glibc; er ist kein vollständig statischer Linux-Binary. Ein
Windows GraalVM Native-Executable ist noch nicht enthalten: Es benötigt eigene,
windows-spezifische JavaFX-Erreichbarkeitsmetadaten und Laufzeitverifizierung.
Der MSI und das portable Windows Runtime-ZIP bleiben unabhängig verfügbar.

Wenn ein Versions-Tag, das zur Editor-Maven-Version passt (derzeit `v1.0.0`),
gepusht wird, baut GitHub Actions die Plattformpakete, den Linux Native-
Executable und das Source-Archiv, und erstellt einen **Draft** GitHub Release.
Überprüfen Sie die Dateien und die Release-Notes und veröffentlichen Sie den
Draft, wenn er fertig ist. Das manuelle Ausführen des Workflows erstellt
herunterladbare Actions-Artefakte; es erstellt kein Release. Das Repository kann
während dieser Tests privat bleiben, aber nur Benutzer mit Repository-Zugriff
können die Pakete herunterladen.

Das Source-Archiv enthält den gepinnten `rich-text-area`-Untermodul-Quellcode,
wie es bei der Weitergabe von GPL-lizenzierten Binaries erforderlich ist.
Windows MSI-Pakete und macOS-Anwendungen sind derzeit nicht signiert; Windows
kann eine SmartScreen-Warnung anzeigen, und macOS kann den ersten Start blockieren.
Codesignierung und macOS-Notarisierung sind nicht konfiguriert. Die
Applikationspakete enthalten die Mardexas- und RichTextArea-GPL-3.0-Lizenztexte
und das Drittanbieter-Hinweis-Inventar; die gebündelte Java-Laufzeit enthält
ihre eigenen rechtlichen Hinweise. GraalVM-Native-Executables bündeln kein Java,
enthalten aber die Projektlizenz und die Drittanbieter-Hinweisdateien.

## Welches Markdown wird unterstützt?

Der Leser und Schreiber unterstützen einen praktischen Teil von CommonMark /
GitHub Flavored Markdown, einschließlich:

- Absätze, Leerzeilen und Überschriften (Stufen 1–6).
- Fett, Kursiv, Durchgestrichen und Inline-Code.
- Links und Bilder.
- Aufzählungslisten, nummerierte Listen und verschachtelte Listen.
- Blockzitate, fenced Code-Blöcke und Trennlinien.
- GitHub-artige Tabellen.

Unterstreichung wird als HTML `<u>`-Element serialisiert; es ist eine
Erweiterung, nicht Teil der CommonMark/GFM-Kernsyntax. Mardexas ist keine
vollständige CommonMark- oder GFM-Konformitätsimplementierung. Nicht unterstützte
Syntax oder Formatierung kann beim Bearbeiten und Speichern normalisiert werden.
Ein einzelner Zeilenumbruch kann in anderen Markdown-Ansichtern unterschiedlich
erscheinen. Halten Sie eine Sicherung bereit oder überprüfen Sie den gespeicherten
Diff, wenn Sie Dokumente bearbeiten, die sich auf nicht unterstützte Markdown-
Features verlassen.

## Aus dem Quellcode bauen und ausführen

### Anforderungen

- JDK 25 oder neuer (einschließlich `jpackage`).
- Maven 3.8 oder neuer.
- Der Quellcode-Build-Helper zielt derzeit auf Linux ab. Die Release-Paketierung
  ist separat für Linux x86-64, Windows x86-64 und die Architektur des macOS
  Runners konfiguriert.

Die Anwendung ist ein modulares JavaFX-Projekt. Der Quellcode-Baum enthält kein
JDK; Release-Pakete bündeln ihre eigene Laufzeit.

### Klonen und RichTextArea-Untermodul

Das RichTextArea-Fork ist ein Git-Untermodul, das vom Mardexas-Repository auf
den Commit `e3796be52aca7c553b1531eef04dbf138114912e` im `main`-Zweig des Forks
gepinnt ist. Dieser Commit deklariert die Maven-Artefaktversion `3.0-RG`. Der
genaue Commit ist im Superprojekt aufgezeichnet, sodass Builds nicht zu einem
zukünftigen `main`-Commit schweben.

Klonen und initialisieren Sie das Untermodul:

```sh
git clone --recurse-submodules https://github.com/RainerGa/Mardexas.git
cd Mardexas
```

Wenn das Repository ohne Untermodul geklont wurde:

```sh
git submodule update --init --recursive
```

Die `rich-text-area`-Quelle wird nicht in dieses Repository kopiert und wird
nicht als separates Maven-Artefakt veröffentlicht. Maven baut das `rta`-Modul
als Teil des Reaktors.

### Bauen und testen

Vom Repository-Stammverzeichnis aus:

```sh
mvn test
```

Bauen ohne Testausführung:

```sh
mvn -DskipTests package
```

Die Tests umfassen JavaFX-UI-Tests, die für headless Monocle konfiguriert sind.
Ein erfolgreicher Kompilier- oder Paketvorgang stellt allein nicht sicher, dass
alle UI-Tests bestehen.

### Ausführen

Unter einem unterstützten Linux-System verwenden Sie das Hilfsskript:

```sh
./editor/start.sh
```

Alternativ bauen/installieren Sie die Reaktormodule und starten den Editor:

```sh
mvn -pl editor -am install
mvn -f editor/pom.xml javafx:run
```

Das Hilfsskript prüft auf Java und Maven, baut den Reaktor und startet dann den
Editor. Es erwartet, dass das Git-Untermodul initialisiert wurde.
- `pom.xml` — Maven-Reaktor-Elternprojekt.
- `editor/` — Mardexas / RichMarkDownEditor-Anwendung und -Tests.
- `rich-text-area/` — Git-Untermodul mit dem upstream-abgeleiteten Fork.
- `.gitmodules` — Submodul-URL; der ausgewählte Commit wird im Git-Submodul-
  Eintrag aufgezeichnet.

## Lizenz und Drittanbieter-Software

Der Mardexas-Anwendungs-Quellcode ist lizenziert unter der GNU General Public
License, Version 3 (GPL-3.0). Der vollständige Lizenztext befindet sich in
[LICENSE](LICENSE). Das RichTextArea-Fork deklariert GPL Version 3, und die
Gluon Emoji-Laufzeitkomponenten deklarieren GPL-3.0 in ihren Quell-POMs. OpenJFX
wird unter GPL Version 2 mit der Classpath-Exception verteilt. Test-only
Abhängigkeiten haben ihre eigenen Lizenzen.

Siehe [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) für die Versionen, Rollen
und Lizenzverweise, die aus dem aktuellen Maven-Abhängigkeitsbaum identifiziert
wurden. Lizenzmetadaten und -hinweise sind informativ; die tatsächlichen
Lizenztexte, Urheberrechtshinweise, ausgewählten Artefakte und die
Distributionsmethode entscheiden. Jeder, der Binaries weiterverbreitet, muss die
geltenden Lizenzen einhalten, einschließlich Quellcode- und Hinweisanforderungen.
Der automatisierte Release-Workflow stellt ein entsprechendes Source-Archiv bereit,
das den RichTextArea-Untermodul enthält.

## Aktueller Validierungsstatus

Der Maven-Reaktor baut aus dem gepinnten Untermodul, aber der letzte vollständige
Testlauf ist nicht grün: 451 Tests wurden ausgeführt, mit fünf Fehlern und
keinen Testfehlern. Drei Fehler waren in `EditorWindowTest`, einer in
`LineNumberGutterTest` und einer in `PrintSupportTest`. Ein separater fokussierter
Lauf reproduzierte vier Fehler in diesen Bereichen. Ein früherer Output
protokollierte auch `Invalid URL`-Ausnahmen während des Layouts von Bildern durch
RichTextArea. Diese Probleme sind für die Release-Bereitschaft relevant; der
Editor ist noch nicht als validiertes Release dargestellt.

## English

Eine englische Version dieser README ist verfügbar unter
[README.md](README.md).

## Projektstruktur