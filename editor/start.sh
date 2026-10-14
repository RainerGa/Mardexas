#!/usr/bin/env bash
#
# start.sh – Startet Mardexas.
#
# Verwendung:
#   ./start.sh
#
# Das Skript prüft die Voraussetzungen (Java 25, Maven), baut den Maven-Reactor
# einschließlich des RichTextArea-Submoduls und startet danach den Editor.
#
# Umgebungsvariablen (optional):
#   JAVA_HOME   – Java-Installation (falls java nicht im PATH)
#   MVN         – Maven-Binary (falls mvn nicht im PATH)

set -euo pipefail

# Projektwurzel anhand dieses Skripts bestimmen (unabhängig vom Aufrufort)
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== Mardexas ==="

# ---------- Java prüfen ----------
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    PATH="$JAVA_HOME/bin:$PATH"
    export PATH
fi

if ! command -v java >/dev/null 2>&1; then
    echo "FEHLER: Keine Java-Laufzeit gefunden." >&2
    echo "        Benötigt wird Java 25 (oder neuer)." >&2
    echo "        Bei Bedarf JAVA_HOME setzen, z. B.:" >&2
    echo "        JAVA_HOME=/pfad/zu/jdk-25 ./start.sh" >&2
    exit 1
fi

JAVA_VERSION="$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')"
if [ -n "$JAVA_VERSION" ] && [ "$JAVA_VERSION" -lt 25 ]; then
    echo "FEHLER: Java $JAVA_VERSION gefunden – benötigt wird Java 25." >&2
    exit 1
fi
echo "Java:  $(java -version 2>&1 | head -1)"

# ---------- Maven prüfen ----------
MVN_BIN="${MVN:-mvn}"
if ! command -v "$MVN_BIN" >/dev/null 2>&1; then
    echo "FEHLER: Maven nicht gefunden (gesucht: '$MVN_BIN')." >&2
    echo "        Maven installieren oder den Pfad über MVN angeben:" >&2
    echo "        MVN=/pfad/zu/mvn ./start.sh" >&2
    exit 1
fi
echo "Maven: $MVN_BIN"

# ---------- Anwendung starten ----------
# Erst den Reactor bauen und die Fork-Abhängigkeit lokal verfügbar machen.
# Der Start überspringt die Tests; diese laufen separat mit `mvn test`.
# Danach startet das javafx-maven-plugin das Editor-Modul.
echo "Starte Anwendung ..."
echo

"$MVN_BIN" -f "$PROJECT_DIR/pom.xml" -pl editor -am -DskipTests install
exec "$MVN_BIN" -f "$PROJECT_DIR/editor/pom.xml" javafx:run
