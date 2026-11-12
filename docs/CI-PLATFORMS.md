# JavaFX regression tests on CI

The test suite uses Monocle Headless on Linux, Windows and macOS. This exercises
JavaFX controls and Glass input, but does not replace native desktop smoke tests.

Monocle's KeyState maps `KeyCode.COMMAND` to the command modifier; it does not map
`KeyCode.META`. GuiTestSupport translates only Robot input on macOS Monocle.
Synthetic JavaFX KeyEvents still use their normal meta flag. Shortcut display
text is taken from KeyCombination because macOS uses modifier symbols.

Tests that depend on virtualized cells wait for the required rendered condition
with a bounded timeout. Queue drains alone do not wait for layout. Screen-space
size checks allow one pixel for platform-dependent snapping.
