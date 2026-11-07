#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Standalone bootstrap: this file can be downloaded before cloning the project.
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: bash checkout-linux.sh [OPTIONS] [DIRECTORY]

Clone all Mardexas sources, including the pinned Git submodules, on Linux.
The destination must not exist. Default: ./Mardexas

  --repository URL   Clone a fork instead of RainerGa/Mardexas (HTTPS or SSH)
  --ref NAME         Check out a branch or tag (default: remote default branch)
  --verify           Also run Maven verify; requires JDK 25 and Maven 3.8+
  -h, --help         Show this help

No packages are installed and no existing checkout is modified.
For --verify, JAVA_HOME selects the JDK and MVN selects the Maven executable.
EOF
}

fail() { printf 'Error: %s\n' "$*" >&2; exit 1; }
repository=https://github.com/RainerGa/Mardexas.git
ref=
verify=false
destination=
while (($#)); do
    case "$1" in
        -h|--help) usage; exit 0 ;;
        --repository|--ref)
            option=$1
            (($# >= 2)) && [[ -n "$2" && "$2" != -* ]] || fail "$option needs a value"
            if [[ "$option" == --repository ]]; then repository=$2; else ref=$2; fi
            shift 2 ;;
        --verify) verify=true; shift ;;
        --) shift; (($# == 1)) || fail 'Expected one destination after --'
            [[ -z "$destination" ]] || fail 'Only one destination is allowed'
            destination=$1; shift ;;
        -*) fail "Unknown option: $1 (use --help)" ;;
        *) [[ -z "$destination" ]] || fail 'Only one destination is allowed'
            destination=$1; shift ;;
    esac
done
[[ $(uname -s) == Linux ]] || fail 'This script supports Linux'
command -v git >/dev/null || fail 'Install Git first'
destination=${destination:-Mardexas}
[[ "$destination" == /* ]] || destination="$PWD/$destination"
[[ ! -e "$destination" && ! -L "$destination" ]] || fail "Destination already exists: $destination"

if "$verify"; then
    if [[ -n ${JAVA_HOME:-} ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
    command -v javac >/dev/null || fail 'Install JDK 25 and set JAVA_HOME'
    javac_version=$(javac -version 2>&1)
    [[ "$javac_version" =~ ^javac[[:space:]]25([.+-]|$) ]] || fail "JDK 25 required for verified builds; found: $javac_version"
    maven=${MVN:-mvn}
    command -v "$maven" >/dev/null || fail 'Install Maven 3.8+ or set MVN to its executable'
fi

clone_args=(clone --recurse-submodules)
[[ -z "$ref" ]] || clone_args+=(--branch "$ref")
if ! git "${clone_args[@]}" -- "$repository" "$destination"; then
    fail "Checkout failed. Any partial checkout at $destination is retained for inspection; choose a new destination to retry."
fi
cd "$destination"
# Never use --remote here: the parent commit determines the dependency versions.
git submodule status --recursive
printf '\nSources: %s\nCommit: %s\n' "$PWD" "$(git rev-parse HEAD)"
if "$verify"; then
    "$maven" --batch-mode --no-transfer-progress \
        '-Dmardexas.build.directory=${project.basedir}/target/checkout-verification' verify
fi
printf '\nNext steps (from the source directory):\n'
printf '  code RichMarkDownEditor.code-workspace\n  ./editor/start.sh\n'
printf 'Development instructions: docs/DEVELOPMENT.md\n'
