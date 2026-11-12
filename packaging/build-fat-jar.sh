#!/usr/bin/env bash
# Build one executable FAT JAR for the current OS and CPU architecture.
# Start: java -jar Mardexas-<version>-<platform>-fat.jar (JDK 25 required).
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APP_POM_VERSION="$(sed -n 's/^[[:space:]]*<version>\([^<]*\)<\/version>[[:space:]]*$/\1/p' "$PROJECT_DIR/editor/pom.xml" | head -n1)"
APP_VERSION="${APP_VERSION:-$APP_POM_VERSION}"
BUILT_JAR="$PROJECT_DIR/editor/target/RichMarkDownEditor-$APP_POM_VERSION-fat.jar"
case "$(uname -s)/$(uname -m)" in
    Linux/x86_64) LABEL=linux-x86_64; PLATFORM=linux ;;
    Darwin/x86_64) LABEL=macos-x86_64; PLATFORM=mac ;;
    Darwin/arm64) LABEL=macos-aarch64; PLATFORM=mac-aarch64 ;;
    MINGW*/x86_64|MSYS*/x86_64) LABEL=windows-x86_64; PLATFORM=win ;;
    *) echo "Unsupported host: $(uname -s)/$(uname -m)" >&2; exit 2 ;;
esac
OUTPUT_JAR="$PROJECT_DIR/dist/Mardexas-$APP_VERSION-$LABEL-fat.jar"

if [[ -z "$APP_POM_VERSION" || "$APP_VERSION" != "$APP_POM_VERSION" ]]; then
    echo "APP_VERSION ($APP_VERSION) must match editor/pom.xml version ($APP_POM_VERSION)." >&2
    exit 2
fi

cd "$PROJECT_DIR"
mvn --batch-mode --no-transfer-progress \
    -Dmaven.test.skip=true -Djacoco.skip=true \
    -pl editor -am install -Pfatjar

if [[ ! -f "$BUILT_JAR" ]]; then
    echo "FAT-JAR wurde nicht erzeugt: $BUILT_JAR" >&2
    exit 1
fi

# Retain the complete MIT attribution and all reviewed icon resources.
python3 "$PROJECT_DIR/packaging/audit-icons.py" --jar "$BUILT_JAR"
python3 "$PROJECT_DIR/packaging/audit-emoji.py" --jar "$BUILT_JAR"
python3 "$PROJECT_DIR/packaging/verify-fat-jar.py" "$PLATFORM" "$BUILT_JAR"

INPUT_DIR="$PROJECT_DIR/editor/target/fat-check-input"
CHECK_DIR="$PROJECT_DIR/editor/target/fat-check/$PLATFORM"
rm -rf "$INPUT_DIR"
mkdir -p "$INPUT_DIR"
cp "$PROJECT_DIR/editor/target/RichMarkDownEditor-$APP_POM_VERSION.jar" "$INPUT_DIR/"
mvn --batch-mode --no-transfer-progress -f "$PROJECT_DIR/editor/pom.xml" \
    -DincludeScope=runtime -DoutputDirectory="$INPUT_DIR" dependency:copy-dependencies
JAVA_COMMAND="${JAVA_HOME:?Set JAVA_HOME}/bin/java"
if command -v cygpath >/dev/null 2>&1; then JAVA_COMMAND="$(cygpath -u "$JAVA_COMMAND").exe"; fi
python3 "$PROJECT_DIR/packaging/verify-package.py" jvm --java "$JAVA_COMMAND" \
    --input-dir "$INPUT_DIR" --output-dir "$CHECK_DIR" --platform "$PLATFORM"
python3 "$PROJECT_DIR/packaging/verify-package.py" jar --java "$JAVA_COMMAND" \
    --input-dir "$INPUT_DIR" --output-dir "$CHECK_DIR" --platform "$PLATFORM" --executable "$BUILT_JAR"

# Strukturprüfung: Manifest, Launcher, App-Ressourcen, JavaFX-Natives je OS.
MAIN_CLASS="$(unzip -p "$BUILT_JAR" META-INF/MANIFEST.MF | tr -d '\r' | sed -n 's/^Main-Class: //p')"
if [[ "$MAIN_CLASS" != "de.richmarkdown.Launcher" ]]; then
    echo "Unerwartete Main-Class im Manifest: '$MAIN_CLASS'." >&2
    exit 1
fi
for entry in \
    de/richmarkdown/Launcher.class \
    de/richmarkdown/RichMarkDownEditorApp.class \
    de/richmarkdown/i18n/messages_en.properties \
    de/richmarkdown/ui/app.css \
    com/gluonhq/richtextarea/RichTextArea.class ; do
    if ! unzip -l "$BUILT_JAR" "$entry" >/dev/null 2>&1; then
        echo "Eintrag fehlt im FAT-JAR: $entry" >&2
        exit 1
    fi
done
# Native Bibliotheken je Plattform (mindestens je ein Treffer pro OS).
NATIVE_COUNT="$(unzip -l "$BUILT_JAR" | grep -ciE '\.(so|dll|dylib)' || true)"
if [[ "$NATIVE_COUNT" -eq 0 ]]; then
    echo "Keine nativen Bibliotheken (.so/.dll/.dylib) im FAT-JAR." >&2
    exit 1
fi
if unzip -l "$BUILT_JAR" | grep -q 'module-info.class'; then
    echo "FAT-JAR darf keine module-info.class enthalten (Classpath-JAR)." >&2
    exit 1
fi

mkdir -p "$PROJECT_DIR/dist"
cp "$BUILT_JAR" "$OUTPUT_JAR"
echo "FAT-JAR: $OUTPUT_JAR"
