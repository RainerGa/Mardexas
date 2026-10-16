#!/usr/bin/env bash
set -euo pipefail

APP_PATH="${1:-dist/native/linux/Mardexas}"

if [[ ! -x "$APP_PATH" ]]; then
    echo "Native executable is missing or not executable: $APP_PATH" >&2
    exit 1
fi

xvfb-run -a --server-args="-screen 0 800x600x24" bash -euo pipefail -c '
    app_path="$1"
    log_file="$(mktemp)"
    "$app_path" >"$log_file" 2>&1 &
    app_pid=$!
    trap '\''kill -TERM "$app_pid" 2>/dev/null || true; wait "$app_pid" 2>/dev/null || true; rm -f "$log_file"'\'' EXIT

    attempt=0
    while (( attempt < 20 )); do
        window_id="$(xdotool search --onlyvisible --name "Mardexas$" 2>/dev/null | head -n 1 || true)"
        if [[ -n "$window_id" ]]; then
            break
        fi
        if ! kill -0 "$app_pid" 2>/dev/null; then
            wait "$app_pid"
        fi
        sleep 1
        attempt=$((attempt + 1))
    done

    if [[ -z "${window_id:-}" ]]; then
        echo "Mardexas GUI window did not appear within 20 seconds." >&2
        exit 1
    fi

    xdotool mousemove --window "$window_id" 25 15 click 1
    attempt=0
    while (( attempt < 10 )); do
        popup_count="$(xwininfo -root -tree 2>/dev/null \
            | grep -Fc "de.richmarkdown.RichMarkDownEditorApp" || true)"
        if (( popup_count > 1 )); then
            break
        fi
        if ! kill -0 "$app_pid" 2>/dev/null; then
            wait "$app_pid"
        fi
        sleep 1
        attempt=$((attempt + 1))
    done

    if (( popup_count <= 1 )); then
        echo "Mardexas File menu did not open within 10 seconds." >&2
        cat "$log_file" >&2
        exit 1
    fi

    sleep 2
    if grep -Eq "NullPointerException|Exception in thread|Graphics Device initialization failed|Error initializing QuantumRenderer" "$log_file"; then
        echo "Mardexas reported a rendering or startup exception after opening the File menu." >&2
        cat "$log_file" >&2
        exit 1
    fi
    echo "Mardexas GUI window appeared and its File menu opened without rendering errors."
' bash "$APP_PATH"
