# Window resizing

The application starts through `de.richmarkdown.Launcher` for Maven, FAT-JAR,
app-image and native-image launches. The launcher delegates to the application
main method so graphics defaults and native-image Java-home handling run before
`Application.launch` initializes JavaFX.

On Linux, `FxRuntimeOptions` defaults `prism.forceUploadingPainter` to `true`.
OpenJFX 25 explicitly describes this setting as a way to avoid Linux live-resize
jitter (see `PrismSettings.java` in the OpenJFX sources). Completed offscreen
frames are uploaded instead of painting directly into the changing native
window surface. This keeps the selected GPU pipeline, but adds a frame upload;
it does not force software rendering. An explicit JVM property value is retained.
Windows and macOS defaults are unchanged.

Source: https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/java/com/sun/prism/impl/PrismSettings.java

Ribbon responsive layout measures expanded groups without temporarily showing
or managing them. It applies each final presentation once. Viewport notifications
that change only height do not run responsive layout.

## Regression checks

`FxRuntimeOptionsTest` checks platform selection, explicit overrides and repeated
configuration. `WorkspaceResizeGuiTest` shows the real tab host with two document
tabs in both menu modes. It checks tab captions, chrome position and height,
scene coverage, document preservation and unnecessary ribbon visibility changes.
`RibbonLayoutGuiTest` covers responsive layout across tabs, fonts and widths.

The normal test run uses Monocle. Monocle 21 can corrupt pixel-buffer limits when
shrinking and then growing a shown JavaFX 25 window; the headless test therefore
uses descending sizes. Run the same tests on native GTK to cover growth and
shrinkage without hiding the window (requires a graphical desktop and Java 25):

```sh
mvn -o -f editor/pom.xml \
  -Dmardexas.build.directory=/tmp/mardexas-resize-gtk \
  -Dtest=WorkspaceResizeGuiTest -DforkCount=1 \
  -Dglass.platform=Gtk -Dprism.order=es2,sw \
  -Dprism.forceUploadingPainter=true \
  -Djava.awt.headless=false -Dtestfx.headless=false test
```

These assertions exercise native window geometry and rendered JavaFX layouts.
They do not simulate the compositor's interactive corner-drag protocol. A
specific window-manager/driver corner-drag fault cannot be conclusively ruled
out by the automated tests alone.
