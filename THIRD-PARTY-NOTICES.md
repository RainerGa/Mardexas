# Third-party software and license notices

This inventory is based on the Maven dependency tree for the current source
revision. Maven plugins and the JDK are build/runtime tooling, not application
dependencies listed here. The application is currently built from source; no
third-party binary bundle is checked into the Mardexas repository.

## Runtime dependencies

| Component | Version | Role | License and source |
|---|---:|---|---|
| RichTextArea fork (`com.gluonhq:rich-text-area`) | 3.0-RG | Rich-text editor control; source is the `RainerGa/rich-text-area` Git submodule pinned by the superproject | GNU GPL version 3, as declared in the fork POM. The fork derives from Gluon's RichTextArea; its source retains upstream notices. [Pinned fork commit](https://github.com/RainerGa/rich-text-area/commit/e3796be52aca7c553b1531eef04dbf138114912e), [upstream project](https://github.com/gluonhq/rich-text-area), [GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.html) |
| Mardexas Noto Emoji (`de.richmarkdown:emoji-noto`), Gluon Emoji fork | 1.1.0-mardexas-1 | Local-only Unicode API and emoji rendering for RichTextArea | Gluon code: GPL-3.0-or-later. Noto artwork: Apache-2.0; region flags: public domain / copyright exemptions per upstream notice. emoji-data: MIT. All source, original licenses and notices are in `emoji-noto/`. See below. |
| OpenJFX Controls, Graphics, and Base (`org.openjfx`) | 25.0.4 | JavaFX UI toolkit | GNU GPL version 2 with the Classpath Exception. [OpenJFX](https://openjfx.io/), [license](https://openjdk.org/legal/gplv2+ce.html) |

## Local vector artwork

**Tabler Icons v3.34.1, outline subset** — Copyright (c) 2020-2024 Paweł Kuna.
Licensed under the MIT License. The original SVG files and complete original
license are shipped in `de/richmarkdown/ui/icons/tabler/` in the application JAR.
Source: https://github.com/tabler/tabler-icons/tree/v3.34.1

The 143 original SVGs and the complete license were compared byte-for-byte with
the official release. MIT (Expat) is compatible with the application's GPL;
the original copyright and permission notice remain bundled. See the
[documented license comparison and reproducible checks](editor/ICON_LICENSE_AUDIT.md).
`upstream-sha256.properties` pins the reviewed original bytes. The FAT-JAR build
checks that all icon resources, including the complete license, survive packaging.

`commands.properties` records the semantic command-to-original mapping.
`paths.properties` normalizes SVG circles to equivalent paths and omits invisible
canvas elements for JavaFX. Rendering scales these paths and combines semantic
badges for command variants. Numeric recent-file badges are original Mardexas
vector geometry under the project's GPL-3.0-or-later license. The former
undocumented PNG sprite has been removed from the application resources. This catalog
contains no Microsoft artwork or icon fonts.

## Development and test dependencies

These dependencies are used for building or testing and are not required by
Mardexas at runtime.

| Component | Version | License and source |
|---|---:|---|
| JUnit Jupiter and JUnit Platform | 5.11.3 / 1.11.3 | Eclipse Public License 2.0. [JUnit](https://github.com/junit-team/junit5), [EPL-2.0](https://www.eclipse.org/legal/epl-2.0/) |
| OpenTest4J | 1.3.0 | Apache License 2.0. [Source](https://github.com/ota4j-team/opentest4j), [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| API Guardian | 1.1.2 | Apache License 2.0. [Source](https://github.com/apiguardian-team/apiguardian), [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| TestFX Core and JUnit 5 integration | 4.0.19-gluon | European Union Public Licence 1.1, as declared in the artifacts' POM metadata. [TestFX](https://github.com/TestFX/TestFX), [EUPL-1.1](https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-11) |
| Hamcrest | 3.0 | BSD 3-Clause. [Source](https://github.com/hamcrest/JavaHamcrest), [BSD-3-Clause](https://opensource.org/license/bsd-3-clause/) |
| AssertJ Core | 3.26.3 | Apache License 2.0. [Source](https://github.com/assertj/assertj), [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Byte Buddy | 1.14.18 | Apache License 2.0. [Source](https://github.com/raphw/byte-buddy), [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| OSGi Core | 8.0.0 | Apache License 2.0, according to the artifact POM. [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| OpenJFX Monocle | 21.0.2 | GNU GPL version 2 with the Classpath Exception. [Source and license](https://github.com/TestFX/Monocle/blob/main/LICENSE) |

## Offline Noto emoji artwork and data

The former Gluon `offline:1.1.0` artifact, its Apple sprite sheets, and the
original downloadable emoji loader have been removed from the application
dependency graph. `emoji-noto/` contains the GPL-3.0-or-later Gluon Emoji 1.1.0
source with an exclusively local Noto loader. It does not discover providers,
read old disk caches, or download artwork. The original source-archive URL,
archive checksum and individual source checksums are recorded in
`emoji-noto/upstream-source-sha256.json`; modification notices are retained.

**Noto Emoji v2.038** — Google LLC and Noto Emoji contributors.
The three unmodified Google-only **clean** sprite sheets are from
[emoji-data commit 063f328d7951cb2e2a6678b06dcbdf8dd599fad7](https://github.com/iamcal/emoji-data/tree/063f328d7951cb2e2a6678b06dcbdf8dd599fad7/sheets-clean),
matching the existing Unicode CSV's coordinates. The pinned upstream README
explicitly documents that these sheets contain Google images only, without Apple
fallbacks. Full-fidelity/mixed sheets are not used. The artwork is under the
[Apache License 2.0](https://github.com/googlefonts/noto-emoji/blob/v2.038/LICENSE).
Noto's [region-flag notice](https://github.com/googlefonts/noto-emoji/blob/v2.038/third_party/region-flags/LICENSE)
identifies the flag images as public domain or otherwise exempt from copyright;
that original notice is included separately, rather than describing all flags as
Apache-licensed. No Noto font is bundled.

**emoji-data** — Copyright (c) 2013 Cal Henderson. MIT License.
The original MIT notice covers the data and sheet layout; it does not replace
the distinct Noto image license.

All full original notices, source README files and the pinned SHA-256 manifest
are bundled in `com/gluonhq/emoji/noto/`. The licensing window shows Noto and
emoji-data as separate components with their original license texts; both are
also included in the thank-you window. `packaging/audit-emoji.py` checks the
reviewed resource bytes and refuses old loaders, old dependency metadata,
Apple sheets, mixed sheets and missing notices. All three release scripts run
this gate on the actual Fat-JAR or the complete runtime dependency directory.
Native-image resource configuration includes all emoji assets and license files.
See [EMOJI_LICENSE_AUDIT.md](editor/EMOJI_LICENSE_AUDIT.md).

## Redistribution

When redistributing the application or a binary package, review the exact
artifacts that are shipped, retain their copyright and license notices, include
the applicable license texts, and satisfy each license's source and offer
requirements. The current Mardexas project license is in [LICENSE](LICENSE).
The full GPL-3.0 text is also available at
https://www.gnu.org/licenses/gpl-3.0.html.

This inventory is not legal advice and is not a substitute for reviewing the
specific artifacts and the way they are combined or distributed.
