#!/usr/bin/env bash
# Baut das ausführbare, plattformübergreifende FAT-JAR:
#   dist/Mardexas-<version>-fat.jar  (Start: java -jar ...; JDK 25 erforderlich)
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APP_POM_VERSION="$(sed -n 's/^[[:space:]]*<version>\([^<]*\)<\/version>[[:space:]]*$/\1/p' "$PROJECT_DIR/editor/pom.xml" | head -n1)"
APP_VERSION="${APP_VERSION:-$APP_POM_VERSION}"
BUILT_JAR="$PROJECT_DIR/editor/target/RichMarkDownEditor-$APP_POM_VERSION-fat.jar"
OUTPUT_JAR="$PROJECT_DIR/dist/Mardexas-$APP_VERSION-fat.jar"

if [[ -z "$APP_POM_VERSION" || "$APP_VERSION" != "$APP_POM_VERSION" ]]; then
    echo "APP_VERSION ($APP_VERSION) must match editor/pom.xml version ($APP_POM_VERSION)." >&2
    exit 2
fi

cd "$PROJECT_DIR"
mvn --batch-mode --no-transfer-progress \
    -DskipTests \
    -pl editor -am package -Pfatjar

if [[ ! -f "$BUILT_JAR" ]]; then
    echo "FAT-JAR wurde nicht erzeugt: $BUILT_JAR" >&2
    exit 1
fi

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
