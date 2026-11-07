# Local validation and remaining platform checks

## Linux development checkout tooling (2026-11-07)

- `scripts/checkout-linux.sh`: Bash syntax check and 12 offline regression tests
  passed. Real local Git repositories cover default destination, branch/tag
  selection, pinned submodule commits, unavailable submodules, existing files
  and symlinks, invalid arguments, JDK selection, and Maven failure propagation.
- Live checkout from `https://github.com/RainerGa/Mardexas.git` passed, including
  its pinned RichTextArea submodule. The remote default branch was `main` at
  parent commit `3886d409130b8b5768017baeb77b5d464c3b840b`, with submodule commit
  `e3796be52aca7c553b1531eef04dbf138114912e`. This confirms retrieval of the
  published source tree; local unpublished changes are not part of that tree.
- Portable VS Code workspace JSON, local documentation links, CI YAML and
  `git diff --check` passed. Checkout regression tests are included in Linux CI.
- Eclipse compiler checks using the installed VS Code Java extension and active
  compiler preferences passed for all project main and test source sets with
  zero errors/warnings; an external JPMS consumer also compiled successfully.
  This is a compiler check, not a reading of the live VS Code Problems panel.
- JDK 25 Maven verification used per-module `target/diagnostics-check` outputs.
  The initial full run executed 1,183 editor tests: one printer-dependent test
  was skipped and `HardeningTest.editorHandlesLargeDocument` exceeded its
  JavaFX action time limit. A subsequent targeted reactor `verify` reran all
  21 hardening tests successfully and passed all 13 integration-demo tests,
  Checkstyle, SpotBugs and the coverage gate (`BUILD SUCCESS`). The initial
  full invocation therefore did not pass in one run; the timeout did not recur
  during the targeted rerun. No Java sources or test time limits were changed
  for the checkout tooling. Logs: `/tmp/mardexas-checkout-maven-verify.log` and
  `/tmp/mardexas-checkout-maven-recheck.log` (temporary local evidence).

## Earlier release validation

Validation date: 2026-11-06. Branch: `Packaging_and_IntegrationAPI`.
Local host: Linux x86-64 (Nobara/KDE). Package JDK: GraalVM CE 25.0.2;
JavaFX: 25.0.4. Application version: 1.0.0.

## Completed local checks

- Full isolated `mvn verify` with JDK 25: 1,182 editor tests and 13 external-demo tests, no failures/errors/skips; SpotBugs 0, Checkstyle and coverage gate passed.
- One additional API regression test then passed in a targeted `verify`: the final distinct total is **1,183 editor tests + 13 demo tests = 1,196 Java tests**. All main sources were unchanged between these runs.
- JaCoCo: **83.18% editor line coverage**; new `de.richmarkdown.api` package **128/128 lines**, **53/56 branches**. Defensive branches remain uncovered; line coverage is not a proof of every possible behavior.
- **35 packaging Python tests**, icon and emoji provenance audits, Bash syntax checks, YAML parsing and job dependency validation passed. `actionlint` was not installed or executed.
- Library ZIP and generated Javadoc built successfully. The external JPMS demo generated the command reference without consumer-side `--add-exports`.
- Final Linux app image, FAT JAR, GraalVM native image, RPM and DEB built successfully. Portable/FAT/native GUI and font comparisons passed for default and software renderers. Native compilation completed in about 82 seconds with roughly 4.25 GB peak RSS.
- Flatpak GUI smoke checks passed inside the actual Freedesktop Platform 25.08 runtime for both renderers, including positive text metrics and ink counts. This is not an outside-sandbox font comparison or a Flatpak installation test.

Local build logs: `/tmp/mardexas-final-jdk25-verify.log`,
`/tmp/mardexas-final-api-verify.log`, `/tmp/mardexas-final-library.log`,
`/tmp/mardexas-final-app-image.log`, `/tmp/mardexas-final-fat-jar.log`,
`/tmp/mardexas-final-native-release.log`, `/tmp/mardexas-final-rpm.log`,
`/tmp/mardexas-final-deb.log`. These temporary files are not release assets.
Coverage/test evidence is under `editor/target/final-verification/` and
`integration-demos/target/surefire-reports/`; executable rendering reports are
under `editor/target/{portable-check,fat-check,package-check,flatpak-check}/`.
The GitHub jobs retain their corresponding evidence as workflow artifacts.

## Final local deliverables

The clean handover folder is `dist/local-release/`. It contains these eight
assets and `SHA256SUMS` (the obsolete unsuffixed FAT JAR is excluded):

| Asset | Contents |
| --- | --- |
| `Mardexas-1.0.0-linux-x86_64-portable.tar.gz` | Portable application and bundled Java runtime |
| `Mardexas-1.0.0-linux-x86_64.deb` | Debian installer |
| `Mardexas-1.0.0-linux-x86_64.rpm` | RPM installer |
| `Mardexas-1.0.0-linux-x86_64.flatpak` | Flatpak bundle |
| `Mardexas-1.0.0-linux-x86_64-fat.jar` | Executable JAR, requires Java 25 |
| `Mardexas-1.0.0-linux-x86_64-native.tar.gz` | GraalVM executable and native libraries |
| `Mardexas-1.0.0-library.zip` | Three custom Maven artifacts/POMs, Javadoc, docs and ten demos |
| `Mardexas-1.0.0-source.tar.gz` | Actual application and initialized fork source tree |

DEB/RPM payload listings and portable/native archive contents were checked for
the original Tabler, JavaFX and build-runtime notices. Library ZIP CRCs and
source archive membership were checked. The final Flatpak bundle was exported
from the same build directory that passed the Platform runtime smoke checks.

## Evidence and limits

The release workflow requires tests, static checks and coverage before building.
All four target architectures must supply their expected assets before publication.
Windows/macOS runners were **not executed** in this session: authentication and
uploading the branch were explicitly deferred to the maintainer. Linux results
must not be presented as evidence that those platforms passed.

The Windows native-image metadata previously lacked reflection registrations
needed by the JavaFX font/backend factories. Metadata generation and mandatory
default/software renderer comparison now cover this path. Confirming the reported
Windows font regression is resolved still requires the real Windows runner and
its retained rendering evidence.

The source archive contains the actual worktree sources, including the initialized
RichTextArea fork at `8a081d2`. Before pushing the parent project, push the fork
commit and stage its updated submodule pointer. Local artifacts are not published,
code-signed or notarized. An old unsuffixed `dist/Mardexas-1.0.0-fat.jar` may remain
from earlier work; it is not a member of the current release inventory.

See [PACKAGING.md](PACKAGING.md) for the complete platform matrix and maintainer
steps, [API.md](API.md) for integrations, and [COMMANDS.md](COMMANDS.md) for all
registered commands. Automated tests cannot guarantee that manual QA will never
find another defect.

## VS Code Java diagnostics follow-up (2026-11-07)

The application modules (`emoji-noto`, `rich-text-area/rta`, `editor`,
`integration-demos`) and editor/demo test sources were compiled separately with
Eclipse ECJ 3.46.200 from the installed Red Hat Java extension. The checks used
the active VS Code workspace's compiler problem settings and each module's Java
release level. Result: no compiler errors or warnings. The seven existing TODO
comments in the RichTextArea fork remain development task markers.

Removed unused imports, private helpers and redundant callback fields; replaced
obsolete TextFlow calls and a raw generic array in tests. `java.prefs` is now a
transitive requirement because the exported `PrintOptions.save/load` API accepts
`Preferences`. A separate JPMS consumer compiled using only
`requires RichMarkDownEditor`, including a call to `PrintOptions.save`.

The isolated Maven regression run covered find/replace, table highlights,
localization, printing options, dialogs, editor windows, toolbars, line numbers,
change tracking, embedding and demos: **219 passed, 0 failures/errors, 1 skipped**.
The skipped printer-selection test requires an installed system printer.
Checkstyle passed. Logs: `/tmp/mardexas-vscode-ecj-check.log` and
`/tmp/mardexas-vscode-diagnostics-tests.log`.

Maven and VS Code use different compilers; a successful Maven build alone does
not establish that the IDE has no warnings. If stale diagnostics remain after
these source updates, use **Java: Clean Java Language Server Workspace** and let
the Java projects finish importing and building.
