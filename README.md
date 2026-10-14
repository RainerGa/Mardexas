# Mardexas

Mardexas is a JavaFX desktop application for editing Markdown documents in a
rich-text, WYSIWYG interface. The current application code and UI still use the
legacy name `RichMarkDownEditor`.

## Features

- Open, edit, and save UTF-8 Markdown files.
- Format text with headings, bold, italic, strikethrough, inline code, links,
  images, lists, block quotes, fenced code blocks, horizontal rules, and tables.
- Navigate headings through a document-structure panel.
- Optional paragraph line numbers, zoom controls, status bar, and printing.
- Localized interface: German, English, French, Spanish, Brazilian Portuguese,
  Japanese, Simplified Chinese, Italian, Korean, and Dutch.

The Markdown reader and writer support a defined subset of CommonMark/GitHub
Flavored Markdown; they are not full conformance implementations. Underlining
is serialized as an HTML `<u>` element. Formatting or syntax that the editor
does not support can be normalized when a document is edited and saved. A
single line break may be rendered differently by other Markdown viewers.

## Requirements

- JDK 25 or newer.
- Maven 3.8 or newer.
- Linux is the configured JavaFX Maven platform in the current editor POM.
  Other operating systems may need a matching JavaFX platform configuration.

The application is a modular JavaFX project. The project does not include a JDK
or redistribute a packaged application/runtime image.

## Clone

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

## Build and test

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

## Run

On Linux/macOS, use the helper script:

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
`PrintSupportTest`. The run also logged `Invalid URL` exceptions while
RichTextArea laid out images. Resolve and rerun the affected UI tests before
treating a release as validated.
