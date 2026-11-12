# Changelog

All notable changes to the RichMarkDownEditor / Mardexas project are documented
in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.1.0] - 2026-11-12

### Added
- Extended find/replace, navigation history, configurable wrapping and document statistics.
- Editing commands, snippets, templates, link checks, document comparison and print options.
- Public JavaFX embedding API, disposable host-menu contributions and ten integration demos.
- Asynchronous file operations, revision-cached document snapshots and performance regressions.
- Integration library ZIP, source archive and package/font verification with release checksums.

### Changed
- Updated the RichTextArea fork to 3.5-RG, including indexed buffers and virtualized layout improvements.
- Reduced repeated parsing, serialization and auxiliary-view work for large documents.
- Moved recovery writes and settings persistence away from the JavaFX application thread.
- Updated GitHub Actions and runner images; corrected macOS Monocle keyboard handling.
- Limited GraalVM native targets to Linux and Windows; retained macOS JVM packages.

See [release notes](releases/1.1.0.md) for package selection and current limitations.

## [1.0.0] - 2026-10-16

### Added
- Initial public release of RichMarkDownEditor (Mardexas)
- Rich-text WYSIWYG Markdown editor based on JavaFX 25 and RichTextArea
- Markdown parser/writer supporting CommonMark/GFM subset (headings, lists,
  quotes, code fences, tables, images, inline formatting, links, escapes)
- Document structure panel and optional line-number gutter
- Zoom control, status bar, print support with pagination
- File open/save with UTF-8 encoding and dirty-check
- Internationalisation (de, en, fr, es, pt-BR, ja, zh-CN, it, ko, nl)
- GraalVM native image build, Flatpak, MSI, and portable packages
- Maven reactor with pinned `rich-text-area` Git submodule (commit e3796be5)
- GPL-3.0 licensing with copyright headers in all source files

### Fixed
- UI test stability improvements for headless Monocle test execution
