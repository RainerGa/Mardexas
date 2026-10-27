# Mardexas — Moderner WYSIWYG-Markdown-Editor für den Desktop

<p align="center">
  <strong>Markdown visuell schreiben, ohne Formatierungszeichen von Hand eintippen zu müssen.</strong><br>
  <em>Ein schneller, nativer Desktop-Editor für Linux, Windows und macOS, der saubere UTF-8 CommonMark- & GFM-Dateien speichert.</em>
</p>

<p align="center">
  <a href="https://rainerga.github.io/Mardexas/"><img src="https://img.shields.io/badge/Website-rainerga.github.io%2FMardexas-blue.svg" alt="Projekt-Website"></a>
  <a href="https://github.com/RainerGa/Mardexas/releases"><img src="https://img.shields.io/github/v/release/RainerGa/Mardexas?include_prereleases&color=success" alt="Neuestes Release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/Lizenz-GPL--3.0-blue.svg" alt="Lizenz: GPL-3.0"></a>
  <a href="https://adoptium.net/"><img src="https://img.shields.io/badge/Java-25-orange.svg" alt="Java 25"></a>
  <a href="https://openjfx.io/"><img src="https://img.shields.io/badge/JavaFX-25.0.4-royalblue.svg" alt="JavaFX 25.0.4"></a>
  <a href="#download--installation"><img src="https://img.shields.io/badge/Plattformen-Linux%20%7C%20Windows%20%7C%20macOS-lightgrey.svg" alt="Plattformen"></a>
  <a href="README.md"><img src="https://img.shields.io/badge/README-English-blue" alt="README English"></a>
</p>

---

![Mardexas Screenshot](screenshot.png)

![Mardexas Screenshot – Tabellen](screenshot_tables.png)

---

## Was ist Mardexas?

**Mardexas (RichMarkDownEditor)** ist ein Desktop-Markdown-Editor für Autoren, Wissenschaftler, Entwickler und Notizschreiber, die die Schlichtheit und Portabilität von Standard-Markdown schätzen, aber einen echten visuellen Schreibfluss bevorzugen, ohne Markdown-Syntax (`#`, `**`, `*`, `|---|`) manuell eingeben zu müssen.

Im Gegensatz zu hybriden Vorschau-Editoren oder ressourcenhungrigen Electron-Browser-Apps basiert Mardexas nativ auf **JavaFX 25**:
- **Echtes WYSIWYG-Schreiben:** Überschriften, Fett, Kursiv, Durchgestrichen, Unterstrichen, Zitate, Links und Tabellen werden direkt während des Schreibens live formatiert.
- **Sauberes Standard-Markdown:** Dokumente werden als echtes, portables UTF-8 CommonMark und GitHub Flavored Markdown (GFM) gespeichert – perfekt kompatibel mit Git, VS Code, Dokumenten-Pipelines und statischen Website-Generatoren.
- **100% Offline & Datenschutzfreundlich:** Keine Telemetrie, keine Zwangs-Cloud, keine Benutzerkonten.
- **Ressourcenschonend & Schnell:** Gebündelte eigenständige Laufzeitumgebungen oder GraalVM-Native-Binärdateien starten blitzschnell mit minimalem RAM-Verbrauch.
- **In 10 Sprachen verfügbar:** Deutsch, Englisch, Französisch, Spanisch, Brasilianisches Portugiesisch, Japanisch, Vereinfachtes Chinesisch, Italienisch, Koreanisch und Niederländisch.

> Dieses Projekt basiert auf der rich-text-area-Bibliothek von [Gluon](https://gluonhq.com/). Wir danken Gluon herzlich für diese hervorragende Grundlage.

---

## Download & Installation

Die vorgefertigten Release-Pakete enthalten eine optimierte Java-Laufzeitumgebung — **Sie müssen kein Java installieren**:

| Plattform | Paketformat | Installation / Start |
| :--- | :--- | :--- |
| **Linux (x86-64)** | **Flatpak** (`.flatpak`) | `flatpak install --user ./Mardexas-<version>-linux-x86_64.flatpak`<br>Start: `flatpak run io.github.rainerga.Mardexas` |
| **Linux (x86-64)** | **Portables Archiv** (`.tar.gz`) | Archiv entpacken und `./Mardexas/bin/Mardexas` ausführen |
| **Linux (x86-64)** | **GraalVM Native Binary** | Entpacken und `./Mardexas` direkt starten (sofortiger Start) |
| **Windows (x86-64)** | **MSI Installer** (`.msi`) | Doppelklick auf die Installationsdatei |
| **Windows (x86-64)** | **Portables ZIP** (`.zip`) | Archiv entpacken und `Mardexas\bin\Mardexas.exe` starten |
| **macOS** | **Portable App** (`.zip`) | Entpacken und `Mardexas.app` in den Programme-Ordner ziehen |

👉 **[Aktuelle Release-Pakete auf GitHub herunterladen](https://github.com/RainerGa/Mardexas/releases)**

---

## Wichtigste Funktionen

- **✨ Visuelles Formatieren:** Überschriften (H1–H6), Fett, Kursiv, Unterstrichen, Durchgestrichen, Zitate, Listen, Links, Bilder und Trennlinien per Tastenkürzel oder Symbolleiste anwenden.
- **📊 Interaktiver Tabelleneditor:** GitHub-Flavored-Markdown-Tabellen mit Spaltenausrichtung, Tastaturnavigation zwischen Zellen und visueller Zeilen-/Spaltenverwaltung erstellen und bearbeiten.
- **🔍 Suchen & Ersetzen:** Text- und Tabellenzellensuche mit Groß-/Kleinschreibung, Ganzwortsuche, visueller Treffer-Hervorhebung und Sofortersetzung.
- **📈 Dokumentstatistiken:** Live-Wortzählung, Zeichenzählung, Lesezeit-Schätzung und Absatzstil-Anzeige in der Statusleiste.
- **🧭 Gliederung & Navigationsverlauf:** Dokumentüberschriften im Strukturpanel durchsuchen und im Bearbeitungsverlauf vor- und zurücknavigieren.
- **📏 Optionale Zeilennummern:** Zeilennummern am linken Rand für Absätze ein- oder ausblenden.
- **🖨️ Drucken & Export:** Direkte Druckfunktion und saubere UTF-8 `.md`-Speicherung.
- **🌍 10 Sprachen:** Vollständig übersetzte Benutzeroberfläche (Deutsch, Englisch, Französisch, Spanisch, Portugiesisch, Japanisch, Chinesisch, Italienisch, Koreanisch, Niederländisch).

---

## Welches Markdown wird unterstützt?

Mardexas unterstützt einen praxiserprobten Standard-Umfang von CommonMark und GitHub Flavored Markdown:
- Überschriften (Ebenen 1–6)
- Absätze und Zeilenumbrüche
- Fett, Kursiv, Durchgestrichen und Inline-Code
- Hyperlinks und Bilder
- Nummerierte, Aufzählungs- und verschachtelte Listen
- Blockzitate und horizontale Trennlinien
- Codeblöcke mit Syntaxgrenzen
- GitHub-style Tabellen (mit links-, zentrierter und rechtsbündiger Ausrichtung)
- Unterstreichungen (sauber als Standard-HTML-Tag `<u>` serialisiert)

---

## Als JavaFX-Bibliothek einbetten

Mardexas kann als Editor-Komponente ohne Dialoge direkt in eigene JavaFX-Anwendungen integriert werden:

### 1. Maven-Abhängigkeit

```xml
<dependency>
  <groupId>de.richmarkdown</groupId>
  <artifactId>RichMarkDownEditor</artifactId>
  <version>1.0.0</version>
</dependency>
```

### 2. In den Scene Graph einbinden

```java
import de.richmarkdown.editor.MarkdownEditor;
import javafx.scene.layout.StackPane;

MarkdownEditor editor = new MarkdownEditor();
myPane.getChildren().add(editor.getControl());

// Datei-API ohne Dialoge:
editor.open(new File("dokument.md"));
editor.save(new File("dokument.md"));
String markdown = editor.getMarkdown();
```

Ausführliche Details finden Sie in [docs/API.md](docs/API.md).

---

## Aus dem Quellcode bauen und ausführen

### Voraussetzungen
- [JDK 25](https://adoptium.net/) oder neuer
- [Maven 3.8+](https://maven.apache.org/)
- Git mit Submodul-Unterstützung

### 1. Klonen mit Submodulen
```sh
git clone --recurse-submodules https://github.com/RainerGa/Mardexas.git
cd Mardexas
```

### 2. Bauen und Tests ausführen
```sh
mvn test
```

### 3. Editor starten
Unter Linux:
```sh
./editor/start.sh
```

Oder direkt über Maven:
```sh
mvn -pl editor -am install
mvn -f editor/pom.xml javafx:run
```

---

## Lizenz & Drittanbieter-Software

- **Mardexas** ist unter der **GNU General Public License, Version 3 (GPL-3.0)** lizenziert. Siehe [LICENSE](LICENSE).
- Die **RichTextArea**-Bibliothek steht unter GPL-3.0.
- **OpenJFX** wird unter GPL-2.0 mit Classpath Exception vertrieben.
- Eine vollständige Auflistung aller Lizenzen und Abhängigkeiten finden Sie in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

---

## Mitwirken & Community

Fehlerberichte, Verbesserungsvorschläge und Pull Requests sind jederzeit herzlich willkommen!
- Probleme melden oder diskutieren auf [GitHub Issues](https://github.com/RainerGa/Mardexas/issues).
- Richtlinien zum Mitwirken: [CONTRIBUTING.md](CONTRIBUTING.md).
- Offizielle Projektseite: [https://rainerga.github.io/Mardexas/](https://rainerga.github.io/Mardexas/)
