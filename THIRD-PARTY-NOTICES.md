# Third-party software and license notices

This inventory is based on the Maven dependency tree for the current source
revision. Maven plugins and the JDK are build/runtime tooling, not application
dependencies listed here. The application is currently built from source; no
third-party binary bundle is checked into the Mardexas repository.

## Runtime dependencies

| Component | Version | Role | License and source |
|---|---:|---|---|
| RichTextArea fork (`com.gluonhq:rich-text-area`) | 3.0-RG | Rich-text editor control; source is the `RainerGa/rich-text-area` Git submodule pinned by the superproject | GNU GPL version 3, as declared in the fork POM. The fork derives from Gluon's RichTextArea; its source retains upstream notices. [Pinned fork commit](https://github.com/RainerGa/rich-text-area/commit/e3796be52aca7c553b1531eef04dbf138114912e), [upstream project](https://github.com/gluonhq/rich-text-area), [GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.html) |
| Gluon Emoji Core (`com.gluonhq.emoji:emoji`) | 1.1.0 | Runtime dependency of RichTextArea | GNU GPL version 3, according to the Gluon Emoji POM. [Source](https://github.com/gluonhq/emoji), [GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.html) |
| Gluon Emoji Offline (`com.gluonhq.emoji:offline`) | 1.1.0 | Offline emoji resources used by RichTextArea | GNU GPL version 3, according to the Gluon Emoji POM. [Source](https://github.com/gluonhq/emoji), [GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.html) |
| OpenJFX Controls, Graphics, and Base (`org.openjfx`) | 25.0.4 | JavaFX UI toolkit | GNU GPL version 2 with the Classpath Exception. [OpenJFX](https://openjfx.io/), [license](https://openjdk.org/legal/gplv2+ce.html) |

## Development and test dependencies

These dependencies are used for building or testing and are not required by
Mardexas at runtime.

| Component | Version | License and source |
|---|---:|---|
| JUnit Jupiter and JUnit Platform | 5.11.3 | Eclipse Public License 2.0. [JUnit](https://github.com/junit-team/junit5), [EPL-2.0](https://www.eclipse.org/legal/epl-2.0/) |
| OpenTest4J | 1.3.0 | Apache License 2.0. [Source](https://github.com/ota4j-team/opentest4j), [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| API Guardian | 1.1.2 | Apache License 2.0. [Source](https://github.com/apiguardian-team/apiguardian), [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| TestFX Core and JUnit 5 integration | 4.0.19-gluon | European Union Public Licence 1.1, as declared in the artifacts' POM metadata. [TestFX](https://github.com/TestFX/TestFX), [EUPL-1.1](https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-11) |
| Hamcrest | 3.0 | BSD 3-Clause. [Source](https://github.com/hamcrest/JavaHamcrest), [BSD-3-Clause](https://opensource.org/license/bsd-3-clause/) |
| AssertJ Core | 3.26.3 | Apache License 2.0. [Source](https://github.com/assertj/assertj), [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Byte Buddy | 1.14.18 | Apache License 2.0. [Source](https://github.com/raphw/byte-buddy), [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| OSGi Core | 8.0.0 | Apache License 2.0, according to the artifact POM. [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| OpenJFX Monocle | 21.0.2 | GNU GPL version 2 with the Classpath Exception. [Source and license](https://github.com/TestFX/Monocle/blob/main/LICENSE) |

## Redistribution

When redistributing the application or a binary package, review the exact
artifacts that are shipped, retain their copyright and license notices, include
the applicable license texts, and satisfy each license's source and offer
requirements. The current Mardexas project license is in [LICENSE](LICENSE).
The full GPL-3.0 text is also available at
https://www.gnu.org/licenses/gpl-3.0.html.

This inventory is not legal advice and is not a substitute for reviewing the
specific artifacts and the way they are combined or distributed.
