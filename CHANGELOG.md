# Changelog

All notable changes to the RichMarkDownEditor / Mardexas project are documented
in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

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

### Known issues
- Five UI tests fail: three in `EditorWindowTest`, one in `LineNumberGutterTest`, one in `PrintSupportTest`. These must be resolved before the next release.