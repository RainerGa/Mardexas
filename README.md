# Mardexas

**Write Markdown without having to write every formatting marker by hand.**
Mardexas is a desktop Markdown editor with a rich-text, WYSIWYG interface. Edit
your document visually, then save it as a UTF-8 Markdown file that you can open
in a text editor, review in Git, or use with other Markdown tools.

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
![Java 25](https://img.shields.io/badge/Java-25-orange.svg)
![JavaFX 25.0.4](https://img.shields.io/badge/JavaFX-25.0.4-royalblue.svg)
![Platform: Linux](https://img.shields.io/badge/Platform-Linux-lightgrey.svg)
[![GitHub repository](https://img.shields.io/badge/GitHub-Private-lightgrey?logo=github)](https://github.com/RainerGa/Mardexas)

> **Availability:** The repository is currently private and does not yet provide
> a downloadable installer or packaged application. Until a release is
> published, the application must be built from source (see
> [Build and run](#build-and-run-from-source)).

## Why use Mardexas?

Mardexas is for people who want the portability and simplicity of Markdown but
prefer a visual editing workflow. You can concentrate on the content and use
the toolbar and menus for common formatting instead of memorizing syntax.

- **Work with existing documents:** Open `.md`, `.markdown`, and `.txt` files;
  read and write document text as UTF-8. Markdown files are saved with `.md`
  by default.
- **Format visually:** Apply headings, emphasis, lists, quotes, code, links,
  images, horizontal rules, and tables from the editor.
- **Stay oriented:** Browse document headings in the structure panel and
  optionally show paragraph line numbers.
- **Adjust the workspace:** Change zoom, check cursor position and paragraph
  style in the status bar, and print the document.
- **Use your language:** The interface is available in German, English,
  French, Spanish, Brazilian Portuguese, Japanese, Simplified Chinese, Italian,
  Korean, and Dutch.

The application and its UI currently use the name `RichMarkDownEditor`; the
GitHub project is named **Mardexas**.

## Get started

There is not yet a ready-to-install application package. If you are comfortable
building Java projects, you can try the current source version:

1. Install [JDK 25](https://adoptium.net/) and [Maven](https://maven.apache.org/).
2. Clone the repository and its RichTextArea submodule:

   ```sh
   git clone --recurse-submodules https://github.com/RainerGa/Mardexas.git
   cd Mardexas
   ```

3. Start the editor on Linux:

   ```sh
   ./editor/start.sh
   ```

The script builds the project before launching the editor. The repository is
private at present, so GitHub access is required to clone it.

## What Markdown does it support?

The reader and writer support a practical subset of CommonMark/GitHub Flavored
Markdown, including:

- Paragraphs, blank lines, and headings (levels 1–6).
- Bold, italic, strikethrough, and inline code.
- Links and images.
- Bulleted, numbered, and nested lists.
- Block quotes, fenced code blocks, and horizontal rules.
- GitHub-style tables.

Underlining is serialized as an HTML `<u>` element; it is an extension, not part
of the CommonMark/GFM core syntax. Mardexas is not a full CommonMark or GFM
conformance implementation. Unsupported syntax or formatting can be normalized
when a document is edited and saved. A single line break may also appear
differently in other Markdown viewers. Keep a backup or review the saved diff
when editing documents that rely on unsupported Markdown features.

---

## Build and run from source

### Requirements

- JDK 25 or newer.
- Maven 3.8 or newer.
- Linux is the configured JavaFX Maven platform in the current editor POM.
  Other operating systems are not currently configured or verified.

The application is a modular JavaFX project. The project does not include a JDK
or redistribute a packaged application/runtime image.

### Clone and RichTextArea submodule

The RichTextArea fork is a Git submodule pinned by the Mardexas repository to
commit `e3796be52aca7c553b1531eef04dbf138114912e` on the fork's `main` branch.
That commit declares the Maven artifact version `3.0-RG`. The exact commit is
recorded in the superproject, so builds do not float to a future `main` commit.

Clone and initialize the submodule:

```sh
git clone --recurse-submodules https://github.com/RainerGa/Mardexas.git
cd Mardexas
```

If the repository was cloned without submodules:

```sh
git submodule update --init --recursive
```

The `rich-text-area` source is not copied into this repository and is not
published as a separate Maven artifact. Maven builds its `rta` module as part
of the reactor.

### Build and test

From the repository root:

```sh
mvn test
```

Build without running tests:

```sh
mvn -DskipTests package
```

The tests include JavaFX UI tests configured for headless Monocle. A successful
compile/package does not by itself establish that all UI tests pass.

### Run

On a supported Linux system, use the helper script:

```sh
./editor/start.sh
```

Alternatively, build/install the reactor modules and start the editor:

```sh
mvn -pl editor -am install
mvn -f editor/pom.xml javafx:run
```

The helper script checks for Java and Maven, builds the reactor, then starts the
editor. It expects the Git submodule to have been initialized.

## Project layout

- `pom.xml` — Maven reactor parent.
- `editor/` — Mardexas / RichMarkDownEditor application and tests.
- `rich-text-area/` — Git submodule containing the upstream-derived fork.
- `.gitmodules` — submodule URL; the selected commit is recorded in Git's
  submodule entry.

## License and third-party software

Mardexas application source is licensed under the GNU General Public License,
version 3 (GPL-3.0). The complete license text is in [LICENSE](LICENSE). The
RichTextArea fork declares GPL version 3, and Gluon Emoji runtime components
declare GPL-3.0 in their source POMs. OpenJFX is distributed under GPL version
2 with the Classpath Exception. Test-only dependencies have their own
licenses.

See [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for the versions, roles,
and license references identified from the current Maven dependency tree.
License metadata and notices are informational; the actual license texts,
copyright notices, selected artifacts, and distribution method govern. Anyone
redistributing binaries must comply with the applicable licenses, including
source-code and notice requirements. This repository currently documents and
builds source; it does not provide a binary release.

## Current validation status

The Maven reactor builds from the pinned submodule, but the latest full test
run is not green: 451 tests ran, with five failures and no test errors. Three
failures were in `EditorWindowTest`, one in `LineNumberGutterTest`, and one in
`PrintSupportTest`. A separate focused run reproduced four failures in those
areas. Earlier output also logged `Invalid URL` exceptions while RichTextArea
laid out images. These issues are relevant to release readiness; the editor is
not yet represented as a validated release.
