#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAVAFX_PLATFORM="${JAVAFX_PLATFORM:?Set JAVAFX_PLATFORM to linux, win, or mac}"
APP_POM_VERSION="$(sed -n 's/^[[:space:]]*<version>\([^<]*\)<\/version>[[:space:]]*$/\1/p' "$PROJECT_DIR/editor/pom.xml" | head -n1)"
APP_VERSION="${APP_VERSION:-$APP_POM_VERSION}"
APP_JAR="RichMarkDownEditor-$APP_POM_VERSION.jar"
INPUT_DIR="$PROJECT_DIR/editor/target/native-image-input"
OUTPUT_DIR="$PROJECT_DIR/dist/native/$JAVAFX_PLATFORM"
NATIVE_IMAGE_CONFIG_DIR="$PROJECT_DIR/packaging/native-image/$JAVAFX_PLATFORM"
NATIVE_IMAGE_CONFIG_DIRS="$NATIVE_IMAGE_CONFIG_DIR"
NATIVE_IMAGE=""

case "$JAVAFX_PLATFORM" in
    linux)
        if [[ "$(uname -s)" != Linux ]]; then
            echo "A Linux Native Image must be built on a Linux host." >&2
            exit 2
        fi
        OUTPUT_NAME="Mardexas"
        NATIVE_IMAGE_CONFIG_DIRS="$NATIVE_IMAGE_CONFIG_DIRS,$PROJECT_DIR/packaging/native-image/linux-sw"
        ;;
    win)
        if [[ "$(uname -s)" != MINGW* && "$(uname -s)" != MSYS* && "${OS:-}" != Windows_NT ]]; then
            echo "A Windows Native Image must be built on a Windows host." >&2
            exit 2
        fi
        OUTPUT_NAME="Mardexas.exe"
        ;;
    mac)
        if [[ "$(uname -s)" != Darwin ]]; then
            echo "A macOS Native Image must be built on a macOS host." >&2
            exit 2
        fi
        OUTPUT_NAME="Mardexas"
        ;;
    *)
        echo "Unsupported GraalVM platform: $JAVAFX_PLATFORM (expected linux, win, or mac)." >&2
        exit 2
        ;;
esac

if [[ -z "$APP_POM_VERSION" || "$APP_VERSION" != "$APP_POM_VERSION" ]]; then
    echo "APP_VERSION ($APP_VERSION) must match editor/pom.xml version ($APP_POM_VERSION)." >&2
    exit 2
fi

if [[ ! -f "$NATIVE_IMAGE_CONFIG_DIR/reachability-metadata.json" ]]; then
    echo "Verified Native Image reachability metadata is missing for $JAVAFX_PLATFORM." >&2
    exit 2
fi
if [[ "$JAVAFX_PLATFORM" == linux && ! -f "$PROJECT_DIR/packaging/native-image/linux-sw/reachability-metadata.json" ]]; then
    echo "Verified Linux software-renderer metadata is missing." >&2
    exit 2
fi

if [[ "${GRAALVM_HOME:-}" == /* && -x "$GRAALVM_HOME/bin/native-image" ]]; then
    NATIVE_IMAGE="$GRAALVM_HOME/bin/native-image"
elif [[ "${JAVA_HOME:-}" == /* && -x "$JAVA_HOME/bin/native-image" ]]; then
    NATIVE_IMAGE="$JAVA_HOME/bin/native-image"
elif command -v native-image >/dev/null 2>&1; then
    NATIVE_IMAGE="$(command -v native-image)"
else
    echo "native-image was not found. Install GraalVM 25 with Native Image support." >&2
    exit 1
fi

if ! "$NATIVE_IMAGE" --version | grep -qi "native-image"; then
    echo "The selected native-image command is not a GraalVM Native Image installation." >&2
    exit 1
fi

cd "$PROJECT_DIR"
mvn --batch-mode --no-transfer-progress \
    -DskipTests \
    -pl editor -am install

rm -rf "$INPUT_DIR"
mkdir -p "$INPUT_DIR" "$OUTPUT_DIR"
cp "$PROJECT_DIR/editor/target/$APP_JAR" "$INPUT_DIR/"

mvn --batch-mode --no-transfer-progress \
    -f "$PROJECT_DIR/editor/pom.xml" \
    -DincludeScope=runtime \
    -DoutputDirectory="$INPUT_DIR" \
    dependency:copy-dependencies

rm -f "$OUTPUT_DIR/Mardexas" "$OUTPUT_DIR/Mardexas.exe"
"$NATIVE_IMAGE" \
    --module-path "$INPUT_DIR" \
    --module RichMarkDownEditor/de.richmarkdown.RichMarkDownEditorApp \
    --add-exports=com.gluonhq.richtextarea/com.gluonhq.richtextarea.viewmodel=RichMarkDownEditor \
    --enable-url-protocols=http \
    --enable-native-access=javafx.graphics \
    -H:ConfigurationFileDirectories="$NATIVE_IMAGE_CONFIG_DIRS" \
    --no-fallback \
    -o "$OUTPUT_DIR/$OUTPUT_NAME"

cp "$PROJECT_DIR/LICENSE" "$OUTPUT_DIR/Mardexas-GPL-3.0.txt"
cp "$PROJECT_DIR/rich-text-area/LICENSE" "$OUTPUT_DIR/RichTextArea-GPL-3.0.txt"
cp "$PROJECT_DIR/THIRD-PARTY-NOTICES.md" "$OUTPUT_DIR/"
