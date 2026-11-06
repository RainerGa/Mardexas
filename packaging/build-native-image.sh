#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAVAFX_PLATFORM="${JAVAFX_PLATFORM:?Set JAVAFX_PLATFORM to linux, win, mac, or mac-aarch64}"
APP_POM_VERSION="$(sed -n 's/^[[:space:]]*<version>\([^<]*\)<\/version>[[:space:]]*$/\1/p' "$PROJECT_DIR/editor/pom.xml" | head -n1)"
APP_VERSION="${APP_VERSION:-$APP_POM_VERSION}"
APP_JAR="RichMarkDownEditor-$APP_POM_VERSION.jar"
INPUT_DIR="$PROJECT_DIR/editor/target/native-image-input"
OUTPUT_DIR="$PROJECT_DIR/dist/native/$JAVAFX_PLATFORM"
METADATA_PLATFORM="${JAVAFX_PLATFORM/mac-aarch64/mac}"
NATIVE_IMAGE_CONFIG_DIR="$PROJECT_DIR/packaging/native-image/$METADATA_PLATFORM"
CHECK_DIR="$PROJECT_DIR/editor/target/package-check/$JAVAFX_PLATFORM"
NATIVE_IMAGE_CONFIG_DIRS="$NATIVE_IMAGE_CONFIG_DIR,$PROJECT_DIR/packaging/native-image/common"
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
        OUTPUT_NAME="Mardexas"
        ;;
    mac|mac-aarch64)
        if [[ "$(uname -s)" != Darwin ]]; then
            echo "A macOS Native Image must be built on a macOS host." >&2
            exit 2
        fi
        EXPECTED_ARCH=x86_64
        [[ "$JAVAFX_PLATFORM" == mac-aarch64 ]] && EXPECTED_ARCH=arm64
        if [[ "$(uname -m)" != "$EXPECTED_ARCH" ]]; then
            echo "$JAVAFX_PLATFORM requires a $EXPECTED_ARCH host and JDK." >&2
            exit 2
        fi
        OUTPUT_NAME="Mardexas"
        ;;
    *)
        echo "Unsupported GraalVM platform: $JAVAFX_PLATFORM (expected linux, win, mac, or mac-aarch64)." >&2
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

find_native_image_in_home() {
    local home="$1"
    local candidate

    [[ -n "$home" ]] || return 1
    if command -v cygpath >/dev/null 2>&1; then
        home="$(cygpath -u "$home")"
    fi

    for candidate in \
        "$home/bin/native-image" \
        "$home/bin/native-image.cmd" \
        "$home/bin/native-image.exe"; do
        if [[ -f "$candidate" ]]; then
            NATIVE_IMAGE="$candidate"
            return 0
        fi
    done
    return 1
}

if ! find_native_image_in_home "${GRAALVM_HOME:-}" \
        && ! find_native_image_in_home "${JAVA_HOME:-}" \
        && command -v native-image >/dev/null 2>&1; then
    NATIVE_IMAGE="$(command -v native-image)"
elif [[ -z "$NATIVE_IMAGE" ]] && command -v native-image.cmd >/dev/null 2>&1; then
    NATIVE_IMAGE="$(command -v native-image.cmd)"
fi

if [[ -z "$NATIVE_IMAGE" ]]; then
    echo "native-image was not found. Install GraalVM 25 with Native Image support." >&2
    exit 1
fi

if ! "$NATIVE_IMAGE" --version 2>&1 | grep -qi "native-image"; then
    echo "The selected native-image command is not a GraalVM Native Image installation." >&2
    exit 1
fi

cd "$PROJECT_DIR"
mvn --batch-mode --no-transfer-progress \
    -Dmaven.test.skip=true \
    -pl editor -am install

rm -rf "$INPUT_DIR"
mkdir -p "$INPUT_DIR" "$OUTPUT_DIR"
cp "$PROJECT_DIR/editor/target/$APP_JAR" "$INPUT_DIR/"
python3 "$PROJECT_DIR/packaging/audit-icons.py" --jar "$INPUT_DIR/$APP_JAR"

mvn --batch-mode --no-transfer-progress \
    -f "$PROJECT_DIR/editor/pom.xml" \
    -DincludeScope=runtime \
    -DoutputDirectory="$INPUT_DIR" \
    dependency:copy-dependencies

python3 "$PROJECT_DIR/packaging/audit-emoji.py" --input-dir "$INPUT_DIR"

# A successful target-host trace is mandatory, including a real rendered GUI.
JAVA_COMMAND="$(dirname "$NATIVE_IMAGE")/java"
[[ -f "$JAVA_COMMAND.exe" ]] && JAVA_COMMAND="$JAVA_COMMAND.exe"
python3 "$PROJECT_DIR/packaging/verify-package.py" trace \
    --java "$JAVA_COMMAND" --input-dir "$INPUT_DIR" \
    --output-dir "$CHECK_DIR" --platform "$JAVAFX_PLATFORM"
python3 "$PROJECT_DIR/packaging/native-metadata.py" "$JAVAFX_PLATFORM" \
    "$INPUT_DIR" "$CHECK_DIR/backend/reachability-metadata.json"
NATIVE_IMAGE_CONFIG_DIRS="$NATIVE_IMAGE_CONFIG_DIRS,$CHECK_DIR/backend,$CHECK_DIR/trace-default,$CHECK_DIR/trace-sw"
# MSYS shell paths must be native Windows paths inside Java option values.
if command -v cygpath >/dev/null 2>&1; then
    INPUT_DIR="$(cygpath -m "$INPUT_DIR")"
    NATIVE_IMAGE_CONFIG_DIRS="$(python3 -c 'import sys; print(",".join(__import__("subprocess").check_output(["cygpath", "-m", p], text=True).strip() for p in sys.argv[1].split(",")))' "$NATIVE_IMAGE_CONFIG_DIRS")"
fi

rm -f "$OUTPUT_DIR/Mardexas" "$OUTPUT_DIR/Mardexas.exe"
"$NATIVE_IMAGE" \
    --module-path "$INPUT_DIR" \
    --module RichMarkDownEditor/de.richmarkdown.Launcher \
    --add-exports=com.gluonhq.richtextarea/com.gluonhq.richtextarea.viewmodel=RichMarkDownEditor \
    --enable-url-protocols=http,https \
    -march=compatibility \
    --exact-reachability-metadata \
    --enable-native-access=javafx.graphics \
    -H:ConfigurationFileDirectories="$NATIVE_IMAGE_CONFIG_DIRS" \
    --no-fallback \
    -o "$OUTPUT_DIR/$OUTPUT_NAME"

cp "$PROJECT_DIR/LICENSE" "$OUTPUT_DIR/Mardexas-GPL-3.0.txt"
cp "$PROJECT_DIR/rich-text-area/LICENSE" "$OUTPUT_DIR/RichTextArea-GPL-3.0.txt"
cp "$PROJECT_DIR/editor/src/main/resources/de/richmarkdown/ui/icons/tabler/LICENSE" "$OUTPUT_DIR/Tabler-Icons-MIT.txt"
cp "$PROJECT_DIR/emoji-noto/src/main/resources/com/gluonhq/emoji/noto/"*.txt "$OUTPUT_DIR/"
cp "$PROJECT_DIR/THIRD-PARTY-NOTICES.md" "$OUTPUT_DIR/"

EXECUTABLE="$OUTPUT_DIR/$OUTPUT_NAME"
[[ "$JAVAFX_PLATFORM" == win ]] && EXECUTABLE="$EXECUTABLE.exe"
python3 "$PROJECT_DIR/packaging/verify-package.py" native \
    --input-dir "$INPUT_DIR" --output-dir "$CHECK_DIR" \
    --platform "$JAVAFX_PLATFORM" --executable "$EXECUTABLE"
