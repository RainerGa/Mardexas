#!/usr/bin/env bash
# Trace-Lauf für die Native-Image-Metadaten:
#   1. "app"   – startet die Anwendung als JAR/Modul im JVM-Betrieb unter dem
#                GraalVM native-image-agent und fährt einen vollständigen
#                GUI-Flow (Menüs, Öffnen, Tippen, Speichern, Save-As, Drucken)
#                durch; alle Reflection/JNI/Resource-Zugriffe werden aufgezeichnet.
#   2. "tests" – führt die Unit-Tests mit demselben Agenten aus.
#   3. "diff"  – vergleicht die Aufzeichnungen vollständig gegen die
#                verifizierten reachability-metadata.json-Dateien.
# Aufruf: packaging/trace-reachability.sh [linux|win|mac] [app|tests|jni|diff|all]
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PLATFORM="${1:-linux}"
shift || true
MODE="${1:-all}"

TRACE_BASE="$PROJECT_DIR/packaging/native-image/trace-output"
TRACE_DIR="$TRACE_BASE/$PLATFORM"
INPUT_DIR="$PROJECT_DIR/editor/target/native-image-input"

case "$PLATFORM" in
    linux)
        META_DIR="$PROJECT_DIR/packaging/native-image/linux"
        META_SW_DIR="$PROJECT_DIR/packaging/native-image/linux-sw"
        ;;
    win)
        META_DIR="$PROJECT_DIR/packaging/native-image/win"
        META_SW_DIR="$PROJECT_DIR/packaging/native-image/win"  # same as main for win
        ;;
    mac)
        META_DIR="$PROJECT_DIR/packaging/native-image/mac"
        META_SW_DIR="$PROJECT_DIR/packaging/native-image/mac"  # same as main for mac
        ;;
    *)
        echo "Unsupported platform: $PLATFORM (expected linux, win, or mac)" >&2
        exit 2
        ;;
esac

META_LINUX="$META_DIR/reachability-metadata.json"
META_SW="$META_SW_DIR/reachability-metadata.json"

GRAALVM_JAVA=""
if [[ -n "${GRAALVM_HOME:-}" && -x "$GRAALVM_HOME/bin/java" ]]; then
    GRAALVM_JAVA="$GRAALVM_HOME/bin/java"
elif [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]] && "$JAVA_HOME/bin/java" --list-modules 2>/dev/null | grep -q jdk.internal.vm.ci; then
    GRAALVM_JAVA="$JAVA_HOME/bin/java"
elif command -v native-image >/dev/null 2>&1; then
    GRAALVM_JAVA="$(dirname "$(readlink -f "$(command -v native-image)")")/java"
fi
if [[ -z "$GRAALVM_JAVA" ]]; then
    echo "GraalVM java nicht gefunden (GRAALVM_HOME/JAVA_HOME setzen)." >&2
    exit 1
fi
echo "GraalVM java: $GRAALVM_JAVA"

prepare_input() {
    if [[ ! -f "$INPUT_DIR/RichMarkDownEditor-1.0.0.jar" ]]; then
        echo "native-image-input fehlt – baue Modul-Input wie build-native-image.sh ..."
        (cd "$PROJECT_DIR" && mvn --batch-mode --no-transfer-progress -DskipTests \
            -pl editor -am install)
        mkdir -p "$INPUT_DIR"
        cp "$PROJECT_DIR/editor/target/RichMarkDownEditor-1.0.0.jar" "$INPUT_DIR/"
        mvn --batch-mode --no-transfer-progress -f "$PROJECT_DIR/editor/pom.xml" \
            -DincludeScope=runtime -DoutputDirectory="$INPUT_DIR" dependency:copy-dependencies
    fi
}

run_app_trace() {
    prepare_input
    rm -rf "$TRACE_DIR/app"
    mkdir -p "$TRACE_DIR/app"
    printf '# Titel\n\nHallo Welt, dies ist ein **Testabsatz**.\n\n- Punkt eins\n- Punkt zwei\n' > /tmp/rme-trace.md
    if [ "$PLATFORM" = "linux" ]; then
        echo "Starte App-Tracing-Lauf (xvfb + GUI-Flow) ..."
        xvfb-run -a --server-args="-screen 0 1600x1200x24" bash -c '
            set -u
            INPUT_DIR="'"$INPUT_DIR"'"
            TRACE_DIR="'"$TRACE_DIR"'"
            JAVA="'"$GRAALVM_JAVA"'"
            "$JAVA" \
                -agentlib:native-image-agent=config-output-dir="$TRACE_DIR/app" \
                --module-path "$INPUT_DIR" \
                --add-exports=com.gluonhq.richtextarea/com.gluonhq.richtextarea.viewmodel=RichMarkDownEditor \
                --module RichMarkDownEditor/de.richmarkdown.RichMarkDownEditorApp \
                >"$TRACE_DIR/app-run.log" 2>&1 &
            PID=$!
            WID=""
            for i in $(seq 1 40); do
                WID=$(xdotool search --onlyvisible --name "^RichMarkDownEditor" 2>/dev/null | head -n1 || true)
                [ -n "$WID" ] && break
                kill -0 $PID 2>/dev/null || break
                sleep 1
            done
            if [ -z "$WID" ]; then echo "APP_WINDOW_MISSING"; kill $PID; exit 1; fi
            sleep 3
            eval $(xdotool getwindowgeometry --shell "$WID")
            rootshot() { import -window root "$TRACE_DIR/shot-$1.png" 2>/dev/null; }
            findwin() {
                local id name
                for id in $(xdotool search --onlyvisible --name "." 2>/dev/null); do
                    [ "$id" = "$WID" ] && continue
                    name=$(xdotool getwindowname "$id" 2>/dev/null || true)
                    [ -n "$name" ] && { echo "$id"; return 0; }
                done
                return 1
            }
            xdotool windowfocus --sync "$WID" 2>/dev/null || true
            # Alle Menüs öffnen/schließen (zeichnet Menü-Reflexion auf)
            for MX in 35 82 157 223 303 369 427 476; do
                xdotool mousemove $((X+MX)) $((Y+24)); sleep 1
                xdotool click 1; sleep 2
                xdotool key Escape; sleep 1
            done
            # Datei öffnen (nativer Dialog + ExtensionFilter + createFileChooserResult)
            xdotool mousemove $((X+35)) $((Y+24)); sleep 1
            xdotool click 1; sleep 2
            xdotool mousemove $((X+73)) $((Y+79)); sleep 1
            xdotool click 1; sleep 3
            DLG=$(findwin || true)
            if [ -n "$DLG" ]; then
                xdotool windowfocus --sync "$DLG" 2>/dev/null || true; sleep 1
                xdotool key ctrl+l; sleep 1
                xdotool type --delay 40 "/tmp/rme-trace.md"; sleep 1
                xdotool key Return; sleep 3
                DLG2=$(findwin || true)
                if [ -n "$DLG2" ]; then
                    eval $(xdotool getwindowgeometry --shell "$DLG2")
                    xdotool mousemove $((X+WIDTH-90)) $((Y+HEIGHT-30)); sleep 1
                    xdotool click 1; sleep 3
                fi
            fi
            xdotool windowfocus --sync "$WID" 2>/dev/null || true; sleep 1
            # Tippen (Glyph-Rendering-Pfad Path2D/PangoGlyphString)
            xdotool mousemove $((X+600)) $((Y+95)); sleep 1
            xdotool click 1; sleep 1
            xdotool type --delay 60 "Trace123"; sleep 2
            rootshot typed
            # Speichern
            xdotool mousemove $((X+35)) $((Y+24)); sleep 1
            xdotool click 1; sleep 2
            xdotool mousemove $((X+73)) $((Y+109)); sleep 1
            xdotool click 1; sleep 3
            # Save-As (nativer Dialog)
            xdotool mousemove $((X+35)) $((Y+24)); sleep 1
            xdotool click 1; sleep 2
            xdotool mousemove $((X+73)) $((Y+134)); sleep 1
            xdotool click 1; sleep 3
            DLG=$(findwin || true)
            if [ -n "$DLG" ]; then
                xdotool windowfocus --sync "$DLG" 2>/dev/null || true; sleep 1
                xdotool type --delay 40 "/tmp/rme-trace-save.md"; sleep 1
                xdotool key Return; sleep 3
            fi
            rootshot saved-as
            # Drucken (eigener Print-Dialog)
            xdotool mousemove $((X+35)) $((Y+24)); sleep 1
            xdotool click 1; sleep 2
            xdotool mousemove $((X+73)) $((Y+159)); sleep 1
            xdotool click 1; sleep 3
            rootshot print-dialog
            xdotool key Escape; sleep 2
            rootshot final
            # Geladene Bibliotheken mitschreiben (Bibliotheksinventar)
            if [ -r /proc/$PID/maps ]; then
                grep -o "/[^ ]*\\.so[^ ]*" /proc/$PID/maps 2>/dev/null | sort -u > "$TRACE_DIR/app-libraries.txt" || true
            fi
            kill -TERM $PID
            for i in $(seq 1 20); do kill -0 $PID 2>/dev/null || break; sleep 1; done
            kill -0 $PID 2>/dev/null && kill -9 $PID
            wait $PID 2>/dev/null || true
            echo APP_TRACE_DONE
        '
    else
        # For win/mac: launch app without GUI automation, just let it initialize briefly
        echo "Starte App-Tracing-Lauf (ohne GUI-Automation für $PLATFORM) ..."
        INPUT_DIR="'"$INPUT_DIR"'"
        TRACE_DIR="'"$TRACE_DIR"'"
        JAVA="'"$GRAALVM_JAVA"'"
        "$JAVA" \
            -agentlib:native-image-agent=config-output-dir="$TRACE_DIR/app" \
            --module-path "$INPUT_DIR" \
            --add-exports=com.gluonhq.richtextarea/com.gluonhq.richtextarea.viewmodel=RichMarkDownEditor \
            --module RichMarkDownEditor/de.richmarkdown.RichMarkDownEditorApp \
            >"$TRACE_DIR/app-run.log" 2>&1 &
        PID=$!
        # Wait a few seconds for initialization
        sleep 5
        # Check if process still alive
        if kill -0 $PID 2>/dev/null; then
            echo "App läuft, werde sie beenden..."
            kill -TERM $PID
            for i in $(seq 1 10); do kill -0 $PID 2>/dev/null || break; sleep 1; done
            kill -0 $PID 2>/dev/null && kill -9 $PID
        fi
        wait $PID 2>/dev/null || true
        echo APP_TRACE_DONE
    fi
    echo "App-Tracing abgeschlossen: $TRACE_DIR/app"
    ls "$TRACE_DIR/app" || true
}

run_jni_scan() {
    # JNI-Inventory: Klassen-Strings, die native Bibliotheken per FindClass
    # aufrufen koennen. Der Agent sieht diese Aufrufe im JVM-Betrieb NICHT
    # (jdk JNI-Tracing nur bei "unexpected"-Calls) – deshalb Scan der
    # JavaFX-*.so und der fertigen Native-Binary.
    mkdir -p "$TRACE_DIR"
    case "$PLATFORM" in
        linux)
            BIN="$PROJECT_DIR/dist/native/linux/Mardexas"
            JFX_JAR="$PROJECT_DIR/editor/target/native-image-input/javafx-graphics-25.0.4-linux.jar"
            ;;
        win)
            BIN="$PROJECT_DIR/dist/native/win/Mardexas.exe"
            JFX_JAR="$PROJECT_DIR/editor/target/native-image-input/javafx-graphics-25.0.4-win.jar"
            ;;
        mac)
            BIN="$PROJECT_DIR/dist/native/mac/Mardexas"
            JFX_JAR="$PROJECT_DIR/editor/target/native-image-input/javafx-graphics-25.0.4-mac.jar"
            ;;
    esac
    PATTERN='^(com/sun/|com/gluonhq/|javafx/|de/richmarkdown/)[A-Za-z0-9_$/]+[A-Za-z0-9_$]'
    : > "$TRACE_DIR/jni-classes.txt"
    if [[ -f "$BIN" ]]; then
        strings -a "$BIN" | grep -E "$PATTERN" | sort -u >> "$TRACE_DIR/jni-classes.txt" || true
    fi
    if [[ -f "$JFX_JAR" ]]; then
        tmp=$(mktemp -d)
        (cd "$tmp" && unzip -qo "$JFX_JAR" '*.so' 2>/dev/null || true)
        find "$tmp" -name '*.so' -exec strings -a {} + 2>/dev/null \
            | grep -E "$PATTERN" | sort -u >> "$TRACE_DIR/jni-classes.txt" || true
        rm -rf "$tmp"
    fi
    sort -u -o "$TRACE_DIR/jni-classes.txt" "$TRACE_DIR/jni-classes.txt"
    # Optional: autoritative FindClass-Liste aus JavaFX-Native-Quellen
    # (sparse Clone: git clone --filter=blob:none --sparse --branch 25-ga \
    #  https://github.com/openjdk/jfx.git && git sparse-checkout set \
    #  modules/javafx.graphics/src/main/native-glass .../native-font .../native-prism*)
    if [[ -n "${JFX_SRC:-}" && -d "${JFX_SRC:-}" ]]; then
        grep -rhoE 'FindClass\(\s*"[^"]+"' --include='*.cpp' --include='*.c' "$JFX_SRC" 2>/dev/null \
            | sed -E 's/.*"([^"]+)".*/\1/' | sort -u > "$TRACE_DIR/jni-classes-src.txt" || true
        echo "FindClass-Quellcode-Ziele: $(wc -l < "$TRACE_DIR/jni-classes-src.txt") -> jni-classes-src.txt"
    fi
    echo "JNI-Scan: $(wc -l < "$TRACE_DIR/jni-classes.txt") Klassen-Treffer -> $TRACE_DIR/jni-classes.txt"
}

run_tests_trace() {
    rm -rf "$TRACE_DIR/tests"
    mkdir -p "$TRACE_DIR/tests"
    echo "Starte Unit-Tests mit native-image-agent (JDK_JAVA_OPTIONS) ..."
    set +e
    (cd "$PROJECT_DIR" && JDK_JAVA_OPTIONS="-agentlib:native-image-agent=config-merge-dir=$TRACE_DIR/tests" \
        mvn --batch-mode --no-transfer-progress test) \
        > "$TRACE_DIR/tests-run.log" 2>&1
    rc=$?
    set -e
    grep -E '^\[ERROR\]   [A-Za-z]' "$TRACE_DIR/tests-run.log" || true
    if [ $rc -ne 0 ]; then
        echo "HINWEIS: mvn test exit=$rc (Fehlschläge siehe oben); Trace-Dateien wurden trotzdem geschrieben."
    fi
    echo "Test-Tracing abgeschlossen: $TRACE_DIR/tests"
    ls "$TRACE_DIR/tests" || true
}

run_diff() {
    python3 "$PROJECT_DIR/packaging/trace-diff.py" "$TRACE_DIR" "$META_LINUX" "$META_SW"
}

case "$MODE" in
    app)   run_app_trace ;;
    tests) run_tests_trace ;;
    jni)   run_jni_scan ;;
    diff)  run_diff ;;
    all)   run_app_trace; run_tests_trace; run_jni_scan; run_diff ;;
    *) echo "Usage: $0 [linux|win|mac] [app|tests|jni|diff|all]" >&2; exit 2 ;;
esac