#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"
JAVA_HOME="${JAVA_HOME:?Set JAVA_HOME to JDK 25}"
if command -v cygpath >/dev/null 2>&1; then JAVA_HOME="$(cygpath -u "$JAVA_HOME")"; fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
mvn --batch-mode --no-transfer-progress -pl integration-demos -am -Dmaven.test.skip=true -Djacoco.skip=true install
INPUT="$PROJECT_DIR/editor/target/library-input"
rm -rf "$INPUT" "$PROJECT_DIR/editor/target/api-docs"
mkdir -p "$INPUT"
mvn --batch-mode --no-transfer-progress -f editor/pom.xml -DincludeScope=runtime -DoutputDirectory="$INPUT" dependency:copy-dependencies
MODULE_PATH="$(python3 -c 'import os,pathlib,sys; print(os.pathsep.join(str(p) for p in sorted(pathlib.Path(sys.argv[1]).glob("*.jar"))))' "$INPUT")"
"$JAVA_HOME/bin/javadoc" -quiet -Xdoclint:none -notimestamp \
    --module-path "$MODULE_PATH" --module-source-path "RichMarkDownEditor=$PROJECT_DIR/editor/src/main/java" \
    --module RichMarkDownEditor -d "$PROJECT_DIR/editor/target/api-docs"
APP_VERSION="$(sed -n 's/^[[:space:]]*<version>\([^<]*\)<\/version>[[:space:]]*$/\1/p' editor/pom.xml | head -n1)"
DEMO=(python3 "$PROJECT_DIR/packaging/run-demo.py" --case=CLASSIC --catalog="$PROJECT_DIR/docs/COMMANDS.md")
if [[ "$(uname -s)" == Linux && -z "${DISPLAY:-}" ]]; then DEMO=(xvfb-run -a "${DEMO[@]}"); fi
"${DEMO[@]}"
python3 packaging/library-bundle.py "dist/Mardexas-${APP_VERSION}-library.zip"
