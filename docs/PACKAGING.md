# Packaging and release validation

Work takes place on `Packaging_and_IntegrationAPI`. GitHub authentication,
pushing this branch, and executing the Windows/macOS runners are deferred to
the maintainer. Local success on Linux is not evidence of a Windows/macOS pass.

## Required release matrix

All builds use Java 25 and JavaFX 25.0.4. Native builds use GraalVM CE 25.0.2
and support Linux x86-64 and Windows x86-64 only. GraalVM Native Image targets
for macOS Intel and Apple Silicon have been removed; macOS continues to use
the JVM packages and its regular JavaFX test matrix.
All workflow jobs select Python 3.12 explicitly with `setup-python`, including its
Windows `python3` alias, rather than depending on the runner's default interpreter.
See [GitHub's Python setup guidance](https://docs.github.com/en/actions/tutorials/build-and-test-code/python)
and the [Windows tool-cache installer](https://github.com/actions/python-versions/blob/main/installers/win-setup-template.ps1).

| Target | Runner | Artifacts |
| --- | --- | --- |
| Linux x86-64 | Ubuntu 24.04 | Portable TAR.GZ, DEB, RPM, Flatpak, FAT JAR, native TAR.GZ |
| Windows x86-64 | Windows 2025 | Portable ZIP, MSI, EXE, FAT JAR, native TAR.GZ |
| macOS Intel | macOS 15 Intel | Portable ZIP, DMG, PKG, FAT JAR |
| macOS Apple Silicon | macOS 15 ARM | Portable ZIP, DMG, PKG, FAT JAR |
| Sources | Ubuntu 24.04 | Source TAR.GZ including the actual RichTextArea fork sources |
| Integration library | Ubuntu 24.04 | ZIP with custom Maven artifacts, Javadoc, command reference and demo sources |

This produces 21 binary/source assets. `release-inventory.py` requires every
one, rejects stale versions, unexpected or empty files, and generates
`SHA256SUMS`. The release workflow requires all jobs to succeed before
publication; rendering evidence is retained separately and is not published
as a user package. A missing member of an upload glob cannot silently pass
the final inventory check.

## Build and test locally

Initialize the pinned Git submodule and select the same JDK for Maven and all
packaging tools:

```bash
git submodule update --init --recursive
export JAVA_HOME=/path/to/jdk-25
export PATH="$JAVA_HOME/bin:$PATH"
mvn --batch-mode --no-transfer-progress verify
python3 -B -m unittest discover -s packaging -p 'test_*.py'

export JAVAFX_PLATFORM=linux # win, mac, or mac-aarch64 on matching hosts
bash packaging/build-app-image.sh
bash packaging/build-installers.sh     # optionally: deb, rpm, msi, exe, dmg, pkg
bash packaging/build-fat-jar.sh
bash packaging/build-native-image.sh   # linux or win only; JAVA_HOME must contain native-image
bash packaging/build-library.sh       # thin JARs, Javadoc, commands and demos
```

The installer script builds only types supported by the chosen platform.
Linux needs `dpkg-deb`, `fakeroot`, and RPM tooling; Windows needs WiX;
macOS needs the system installer tooling. Installer formats cannot be
cross-compiled from another operating system. The app image must be built
first, so installers reuse exactly its runtime, launcher options, and notices.

The dedicated `verify` job runs tests, static analysis, and coverage checks
before any package job. Packaging commands intentionally skip tests and
JaCoCo: they must not evaluate stale execution data against classes rebuilt
with a different compiler. They retain SpotBugs checks during `install` and
perform their own executable GUI checks. A packaging invocation alone does
not replace `mvn verify`.

The RichTextArea fork's inherited upstream tests were already disabled and
do not compile against this fork's current API. Both test execution and test
compilation are skipped in that module; application tests remain enabled.

FAT JARs contain only the host's JavaFX natives. A combined Intel/ARM macOS
JAR would overwrite identically named `.dylib` entries. `verify-fat-jar.py`
checks ELF, PE, or Mach-O CPU headers and rejects other architectures.
Each FAT JAR still requires an installed Java 25 runtime.

## Executable and font verification

`--verify-package <directory>` is an explicit, opt-in application mode. It
opens the actual workspace, Ribbon, editor document and test text in an
isolated configuration profile, takes snapshots, writes `fonts.properties`
and `window.ppm`, disposes the workspace and exits. Normal startup does not
execute this probe. Linux checks run under Xvfb. No mouse resize tests are used.

`verify-package.py` runs each package with both the default Prism pipeline
and the software fallback. It compares seven samples covering regular, bold,
italic, bold italic, monospace, and heading text against the target host's
JVM baseline. Font names, metrics, output scale, and visible text pixels at
1x/1.5x/2x must agree within small tolerances. An empty document, unreadable
sample, missing report, process failure, native registration error, or
120-second timeout fails the build. A timeout terminates the whole process
tree; a stale report cannot count as a pass. Complete logs remain in the
report directory, while failures print a bounded excerpt.

This catches font selection and rendering regressions on the build host;
it does not simulate every graphics driver, installed font set, remote
desktop session, or real monitor DPI combination. The 1.5x/2x samples are
snapshot scaling checks, not separate physical monitor tests.

Flatpak additionally starts the verified app image inside its actual Platform
runtime with both renderers. Font installations may differ from the host, so
this check validates complete, positive font metrics and pixel counts rather
than comparing sandbox font names with host font names. It is a runtime smoke
check and does not install the exported bundle into the user's application list.

The old Windows native metadata contained no reflection registrations.
JavaFX selects its DirectWrite font factory reflectively and uses JNI font
structures; target-specific metadata now includes these types and the D3D
backend. This is a concrete packaging defect, but the historical Windows
font issue is not confirmed resolved until the actual Windows native run
passes. No global font replacement or forced software rendering is applied.

Native builds combine the target JVM agent traces with backend registrations,
JavaFX resources, optional CSS `.bss` queries, Marlin memory-segment accessors,
localized logging lookups and shutdown-time XML service lookups. They use
exact reachability metadata and fail on missing registrations. These extra
registrations address paths a single virtual display trace cannot cover.

Reports are stored under `editor/target/package-check/<platform>`,
`portable-check/<platform>`, and `fat-check/<platform>`.

## Notices and source provenance

Every app image/native directory includes application, RichTextArea, Tabler,
Noto Emoji, and runtime legal notices. The exact build JDK's `legal/` tree and
`release` file are copied; internal legal symlinks are resolved so notices
remain self-contained. Read-only source permissions do not break rebuilds.

`packaging/licenses/javafx-25.0.4/ORIGIN.json` records the official SDK URL,
archive SHA-256, and original notice SHA-256 for each platform. Only the
three runtime modules used by Mardexas are retained. The build verifies
their version, completeness, and original bytes. Their full original texts
are included in application/FAT JARs and alongside other package notices.

`source-archive.py` archives the actual tracked and non-ignored working-tree
sources, recursively including initialized submodules. It includes local
changes used for compilation and excludes Git internals, ignored targets
and build outputs. Missing submodules fail. Do not use `git archive HEAD`
for a dirty local release: that would omit the very changes being shipped.

## Maintainer's later GitHub run

1. Review the changes on `Packaging_and_IntegrationAPI`. The parent
   repository's submodule pointer must refer to a commit already pushed to
   `RainerGa/rich-text-area`; push that fork commit first if needed.
2. Run the unit/GUI/static checks above and commit the parent repository
   including the corrected submodule pointer.
3. Push the branch. Its CI workflow now runs on this branch as well as main.
4. Run **Build release packages** against this branch with an empty
   `release_tag`. This builds and retains artifacts without publishing a
   release. Inspect every matrix job and its rendering evidence.
5. After all target runs pass, use a matching numeric version tag (for
   example `v1.0.0`) or explicitly supply the matching existing tag to
   publish. The POM, archive version and release tag must agree.

Packages are not code-signed or notarized by these scripts. Signing keys
and platform signing configuration are not present in this repository.
