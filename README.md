# Mardexas — Modern WYSIWYG Markdown Editor for Desktop

<p align="center">
  <strong>Write Markdown visually without typing formatting markers by hand.</strong><br>
  <em>A fast, native desktop editor for Linux, Windows, and macOS that saves clean, standard UTF-8 CommonMark & GFM files.</em>
</p>

<p align="center">
  <a href="https://rainerga.github.io/Mardexas/"><img src="https://img.shields.io/badge/Website-rainerga.github.io%2FMardexas-blue.svg" alt="Project Website"></a>
  <a href="https://github.com/RainerGa/Mardexas/releases"><img src="https://img.shields.io/github/v/release/RainerGa/Mardexas?include_prereleases&color=success" alt="Latest Release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg" alt="License: GPL-3.0"></a>
  <a href="https://adoptium.net/"><img src="https://img.shields.io/badge/Java-25-orange.svg" alt="Java 25"></a>
  <a href="https://openjfx.io/"><img src="https://img.shields.io/badge/JavaFX-25.0.4-royalblue.svg" alt="JavaFX 25.0.4"></a>
  <a href="#download--installation"><img src="https://img.shields.io/badge/Platforms-Linux%20%7C%20Windows%20%7C%20macOS-lightgrey.svg" alt="Platforms"></a>
  <a href="README-de.md"><img src="https://img.shields.io/badge/README-Deutsch-ffe082" alt="README Deutsch"></a>
</p>

---

![Mardexas screenshot](screenshot.png)

![Mardexas screenshot – tables](screenshot_tables.png)

---

## What is Mardexas?

**Mardexas (RichMarkDownEditor)** is a desktop Markdown editor designed for writers, researchers, developers, and note-takers who want the portability and simplicity of plain Markdown without having to write formatting markers (`#`, `**`, `*`, `|---|`) manually.

Unlike hybrid preview editors or heavy web-based Electron applications, Mardexas is built natively with **JavaFX 25** and delivers:
- **True WYSIWYG editing:** Headings, bold, italic, strikethrough, underline, quotes, links, and tables style live in place as you type.
- **Clean Standard Markdown:** Files are read and saved as 100% standard UTF-8 CommonMark and GitHub Flavored Markdown (GFM), ready for Git, Obsidian, Hugo, or static site generators.
- **100% Offline & Private:** Zero telemetry, no cloud sync lock-in, no accounts required.
- **Lightweight & Fast:** Bundled standalone runtimes or GraalVM native binaries start instantly with minimal RAM usage.
- **Available in 10 Languages:** English, German, French, Spanish, Brazilian Portuguese, Japanese, Simplified Chinese, Italian, Korean, and Dutch.

> This project is built on the rich-text-area library provided by [Gluon](https://gluonhq.com/). We warmly thank Gluon for providing this foundation.

---

## Download & Installation

Ready-to-run release packages bundle an optimized runtime image — **you do not need to install Java**:

| Platform | Package Format | Quick Start / Install |
| :--- | :--- | :--- |
| **Linux (x86-64)** | **Flatpak** (`.flatpak`) | `flatpak install --user ./Mardexas-<version>-linux-x86_64.flatpak`<br>Run: `flatpak run io.github.rainerga.Mardexas` |
| **Linux (x86-64)** | **Portable Tarball** (`.tar.gz`) | Extract archive and run `./Mardexas/bin/Mardexas` |
| **Linux (x86-64)** | **GraalVM Native Binary** | Extract archive and execute `./Mardexas` directly (instant startup) |
| **Windows (x86-64)** | **MSI Installer** (`.msi`) | Double-click installer and follow the setup wizard |
| **Windows (x86-64)** | **Portable ZIP** (`.zip`) | Extract archive and run `Mardexas\bin\Mardexas.exe` |
| **macOS** | **Portable App** (`.zip`) | Extract archive and drag `Mardexas.app` to your Applications folder |

👉 **[Download the Latest Releases on GitHub](https://github.com/RainerGa/Mardexas/releases)**

---

## Key Features

- **✨ Visual Formatting:** Apply headings (H1–H6), bold, italic, underline, strikethrough, blockquotes, lists, links, images, and horizontal rules using standard hotkeys or the intuitive toolbar.
- **📊 Interactive Table Editor:** Create and edit GitHub-Flavored Markdown tables with column alignment, keyboard navigation across cells, and visual row/column management.
- **🔍 Advanced Find & Replace:** Find text and table cell content with case matching, whole-word filtering, occurrences highlighting, and instant replacement.
- **📈 Document Statistics:** Live word count, character count, estimated reading time, and paragraph style indicator in the status bar.
- **🧭 Structure & Navigation:** Browse headings in the collapsible document outline panel and navigate backward/forward through your editing history.
- **📏 Optional Line Numbers:** Toggle paragraph line numbers in the gutter for quick reference.
- **🖨️ Print & Export:** Direct print support and clean UTF-8 `.md` export.
- **🌍 10 UI Languages:** Fully localized interface in English, German, French, Spanish, Portuguese, Japanese, Chinese, Italian, Korean, and Dutch.

---

## Comparison: Mardexas vs. Alternatives

| Feature | Mardexas | Typora | Obsidian | MarkText |
| :--- | :---: | :---: | :---: | :---: |
| **Open Source** | **Yes (GPL-3.0)** | No (Proprietary) | No (Proprietary Core) | Yes (MIT) |
| **Price** | **100% Free** | $14.99 | Free / Paid Sync | Free |
| **WYSIWYG Editing** | **Full Visual** | Inline Hybrid | Split / Live Preview | Inline Hybrid |
| **Architecture** | **JavaFX / Native** | Electron | Electron | Electron |
| **RAM Footprint** | **~70–150 MB** | ~250–400 MB | ~400–800 MB | ~400–700 MB |
| **Standard CommonMark/GFM** | **Yes** | Yes | Yes (with wiki links) | Yes |
| **Offline & Private** | **100% Local** | 100% Local | Local-first | 100% Local |
| **Embeddable Library** | **Yes (JavaFX API)** | No | No | No |

---

## What Markdown Does It Support?

Mardexas supports a practical subset of CommonMark and GitHub Flavored Markdown:
- Headings (levels 1–6)
- Paragraphs and line breaks
- Bold, italic, strikethrough, and inline code
- Links and images
- Bulleted, numbered, and nested lists
- Blockquotes and horizontal rules
- Fenced code blocks
- GitHub-style Markdown tables (with left, center, and right column alignments)
- Underlining (serialized cleanly as standard HTML `<u>` tags)

---

## Use as a JavaFX Library

Mardexas can be embedded into your own JavaFX application as an interactive editor component without dialogs:

### 1. Maven Dependency

```xml
<dependency>
  <groupId>de.richmarkdown</groupId>
  <artifactId>RichMarkDownEditor</artifactId>
  <version>1.0.0</version>
</dependency>
```

### 2. Embed into your Scene Graph

```java
import de.richmarkdown.editor.MarkdownEditor;
import javafx.scene.layout.StackPane;

MarkdownEditor editor = new MarkdownEditor();
myPane.getChildren().add(editor.getControl());

// Direct File API (without dialogs):
editor.open(new File("document.md"));
editor.save(new File("document.md"));
String markdown = editor.getMarkdown();
```

See [docs/API.md](docs/API.md) for the complete library API documentation.

---

## Build and Run from Source

If you prefer building from source:

### Requirements
- [JDK 25](https://adoptium.net/) or newer
- [Maven 3.8+](https://maven.apache.org/)
- Git with submodule support

### 1. Clone with Submodules
```sh
git clone --recurse-submodules https://github.com/RainerGa/Mardexas.git
cd Mardexas
```

### 2. Build and Test
```sh
mvn test
```

### 3. Run the Editor
On Linux:
```sh
./editor/start.sh
```

Or via Maven:
```sh
mvn -pl editor -am install
mvn -f editor/pom.xml javafx:run
```

---

## Project Layout

- `editor/` — Mardexas / RichMarkDownEditor application, UI components, and unit tests.
- `rich-text-area/` — Git submodule containing the upstream-derived RichTextArea fork.
- `packaging/` — Build and packaging scripts for Linux (Flatpak, portable, native), Windows (MSI, portable), and macOS.
- `docs/` — Project landing page hosted at [https://rainerga.github.io/Mardexas/](https://rainerga.github.io/Mardexas/).

---

## License & Third-Party Software

- **Mardexas** is licensed under the **GNU General Public License, Version 3 (GPL-3.0)**. See [LICENSE](LICENSE).
- The **RichTextArea** library fork declares GPL-3.0.
- **OpenJFX** is distributed under GPL-2.0 with the Classpath Exception.
- For complete dependency details and licenses, see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

---

## Contributing & Community

Bug reports, feature suggestions, and pull requests are warmly appreciated!
- Open an issue or join discussions on [GitHub Issues](https://github.com/RainerGa/Mardexas/issues).
- Check [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.
- Visit the official website: [https://rainerga.github.io/Mardexas/](https://rainerga.github.io/Mardexas/)
