#!/usr/bin/env bash
# Build installers from the already audited app-image, preserving its launcher,
# runtime and complete third-party notices.
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"
PLATFORM="${JAVAFX_PLATFORM:?Set JAVAFX_PLATFORM}"
VERSION="$(sed -n 's/^[[:space:]]*<version>\([^<]*\)<\/version>[[:space:]]*$/\1/p' editor/pom.xml | head -n1)"
TOOL="${JAVA_HOME:?Set JAVA_HOME}/bin/jpackage"
if command -v cygpath >/dev/null 2>&1; then TOOL="$(cygpath -u "$TOOL").exe"; fi
IMAGE="$PROJECT_DIR/dist/$PLATFORM/Mardexas"
EXTRA=()
case "$PLATFORM" in
    linux) TYPES=(deb rpm); LABEL=linux-x86_64; EXTRA=(--linux-shortcut) ;;
    win) TYPES=(msi exe); LABEL=windows-x86_64
        EXTRA=(--win-menu --win-shortcut --win-dir-chooser --win-upgrade-uuid 76bd6ca4-fcaa-4abc-9cc2-458cb925c125) ;;
    mac) TYPES=(dmg pkg); LABEL=macos-x86_64; IMAGE="$IMAGE.app" ;;
    mac-aarch64) TYPES=(dmg pkg); LABEL=macos-aarch64; IMAGE="$IMAGE.app" ;;
    *) echo "Unsupported platform: $PLATFORM" >&2; exit 2 ;;
esac
[[ -d "$IMAGE" ]] || { echo "Build the app-image first: $IMAGE" >&2; exit 2; }
for TYPE in "${TYPES[@]}"; do
    OUTPUT="$PROJECT_DIR/editor/target/installer-$TYPE"
    rm -rf "$OUTPUT"
    mkdir -p "$OUTPUT"
    "$TOOL" --type "$TYPE" --name Mardexas --app-version "$VERSION" \
        --vendor RainerGa --description "Visual Markdown editor" \
        --app-image "$IMAGE" --dest "$OUTPUT" --license-file "$PROJECT_DIR/LICENSE" "${EXTRA[@]}"
    FOUND=0
    for PACKAGE in "$OUTPUT/"*."$TYPE"; do
        [[ -f "$PACKAGE" ]] || continue
        cp "$PACKAGE" "dist/Mardexas-$VERSION-$LABEL.$TYPE"
        FOUND=$((FOUND + 1))
    done
    [[ "$FOUND" == 1 ]] || { echo "Expected exactly one $TYPE installer, found $FOUND" >&2; exit 1; }
done
