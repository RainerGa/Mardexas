#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAVAFX_PLATFORM="${JAVAFX_PLATFORM:?Set JAVAFX_PLATFORM to linux, win, mac, or mac-aarch64}"
APP_POM_VERSION="$(sed -n 's/^[[:space:]]*<version>\([^<]*\)<\/version>[[:space:]]*$/\1/p' "$PROJECT_DIR/editor/pom.xml" | head -n1)"
APP_VERSION="${APP_VERSION:-$APP_POM_VERSION}"
APP_JAR="RichMarkDownEditor-$APP_POM_VERSION.jar"
INPUT_DIR="$PROJECT_DIR/editor/target/jpackage-input"
OUTPUT_DIR="$PROJECT_DIR/dist/$JAVAFX_PLATFORM"
LICENSE_DIR="$PROJECT_DIR/editor/target/package-licenses"
JPACKAGE=""

case "$JAVAFX_PLATFORM" in
    linux) [[ "$(uname -s)" == Linux && "$(uname -m)" == x86_64 ]] || exit 2 ;;
    win) [[ "${OS:-}" == Windows_NT ]] || exit 2 ;;
    mac) [[ "$(uname -s)" == Darwin && "$(uname -m)" == x86_64 ]] || exit 2 ;;
    mac-aarch64) [[ "$(uname -s)" == Darwin && "$(uname -m)" == arm64 ]] || exit 2 ;;
    *)
        echo "Unsupported JavaFX platform: $JAVAFX_PLATFORM" >&2
        exit 2
        ;;
esac

if [[ -z "$APP_POM_VERSION" || "$APP_VERSION" != "$APP_POM_VERSION" ]]; then
    echo "APP_VERSION ($APP_VERSION) must match editor/pom.xml version ($APP_POM_VERSION)." >&2
    exit 2
fi

if [[ "${JAVA_HOME:-}" == /* && -x "$JAVA_HOME/bin/jpackage" ]]; then
    JPACKAGE="$JAVA_HOME/bin/jpackage"
elif command -v jpackage >/dev/null 2>&1; then
    JPACKAGE="$(command -v jpackage)"
else
    echo "jpackage was not found. Install JDK 25 or newer." >&2
    exit 1
fi

cd "$PROJECT_DIR"
mvn --batch-mode --no-transfer-progress \
    -Dmaven.test.skip=true \
    -pl editor -am install

rm -rf "$INPUT_DIR"
rm -rf "$LICENSE_DIR"
mkdir -p "$INPUT_DIR" "$OUTPUT_DIR" "$LICENSE_DIR"
cp "$PROJECT_DIR/editor/target/$APP_JAR" "$INPUT_DIR/"
python3 "$PROJECT_DIR/packaging/audit-icons.py" --jar "$INPUT_DIR/$APP_JAR"
cp "$PROJECT_DIR/LICENSE" "$LICENSE_DIR/Mardexas-GPL-3.0.txt"
cp "$PROJECT_DIR/rich-text-area/LICENSE" "$LICENSE_DIR/RichTextArea-GPL-3.0.txt"
cp "$PROJECT_DIR/editor/src/main/resources/de/richmarkdown/ui/icons/tabler/LICENSE" "$LICENSE_DIR/Tabler-Icons-MIT.txt"
cp "$PROJECT_DIR/emoji-noto/src/main/resources/com/gluonhq/emoji/noto/"*.txt "$LICENSE_DIR/"

mvn --batch-mode --no-transfer-progress \
    -f "$PROJECT_DIR/editor/pom.xml" \
    -DincludeScope=runtime \
    -DoutputDirectory="$INPUT_DIR" \
    dependency:copy-dependencies

python3 "$PROJECT_DIR/packaging/audit-emoji.py" --input-dir "$INPUT_DIR"

rm -rf "$OUTPUT_DIR/Mardexas" "$OUTPUT_DIR/Mardexas.app"
JPACKAGE_ARGS=(
    --type app-image
    --name Mardexas
    --app-version "$APP_VERSION"
    --vendor RainerGa
    --description "Visual Markdown editor"
    --module-path "$INPUT_DIR"
    --dest "$OUTPUT_DIR"
    --module RichMarkDownEditor/de.richmarkdown.Launcher
    --java-options "--add-exports=com.gluonhq.richtextarea/com.gluonhq.richtextarea.viewmodel=RichMarkDownEditor"
)

case "$JAVAFX_PLATFORM" in
    mac|mac-aarch64)
        # Keep extra files outside the app bundle so jpackage can sign it.
        ;;
    *)
        JPACKAGE_ARGS+=(--app-content "$LICENSE_DIR")
        JPACKAGE_ARGS+=(--app-content "$PROJECT_DIR/THIRD-PARTY-NOTICES.md")
        ;;
esac

"$JPACKAGE" \
    "${JPACKAGE_ARGS[@]}"

case "$JAVAFX_PLATFORM" in
    mac|mac-aarch64)
        mkdir -p "$OUTPUT_DIR/Mardexas.app/Contents/Resources/licenses"
        cp "$LICENSE_DIR/"* "$OUTPUT_DIR/Mardexas.app/Contents/Resources/licenses/"
        cp "$PROJECT_DIR/THIRD-PARTY-NOTICES.md" "$OUTPUT_DIR/Mardexas.app/Contents/Resources/licenses/"
        cp "$LICENSE_DIR/"* "$OUTPUT_DIR/"
        cp "$PROJECT_DIR/THIRD-PARTY-NOTICES.md" "$OUTPUT_DIR/"
        ;;
esac
