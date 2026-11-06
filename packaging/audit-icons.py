#!/usr/bin/env python3
"""Offline provenance and distribution checks for the pinned Tabler subset.

Usage: python3 packaging/audit-icons.py [--upstream release.tar.gz] [--jar app.jar]
The upstream archive is optional; no network or third-party modules are needed.
"""
import argparse
import hashlib
from pathlib import Path
import tarfile
import zipfile

PREFIX = "de/richmarkdown/ui/icons/tabler/"
ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "editor/src/main/resources" / PREFIX
ARCHIVE_SHA256 = "c9c5b022f56b9c7ff77f95206f7a324c6c00a02859a4a4e0560fec012c7adcb4"


def audit(upstream=None, jar=None):
    manifest = dict(line.split("=", 1) for line in
                    (SOURCE / "upstream-sha256.properties").read_text().splitlines()
                    if line and not line.startswith("#"))
    expected = {"LICENSE"} | {p.name for p in SOURCE.glob("*.svg") if p.name != "gallery.svg"}
    if set(manifest) != expected or len(expected) != 144:
        raise ValueError("Source inventory differs from the reviewed 143 SVGs and LICENSE")
    for name, digest in manifest.items():
        if hashlib.sha256((SOURCE / name).read_bytes()).hexdigest() != digest:
            raise ValueError("Source/license checksum mismatch: " + name)
    if upstream:
        if hashlib.sha256(Path(upstream).read_bytes()).hexdigest() != ARCHIVE_SHA256:
            raise ValueError("Unexpected upstream archive; review the release before updating provenance")
        with tarfile.open(upstream) as archive:
            for name in manifest:
                member = "tabler-icons-3.34.1/" + ("LICENSE" if name == "LICENSE" else "icons/outline/" + name)
                if archive.extractfile(member).read() != (SOURCE / name).read_bytes():
                    raise ValueError("Upstream mismatch: " + name)
    if jar:
        with zipfile.ZipFile(jar) as artifact:
            for path in SOURCE.iterdir():
                if path.is_file() and artifact.read(PREFIX + path.name) != path.read_bytes():
                    raise ValueError("Missing or modified distribution resource: " + path.name)
    print("OK: 143 original Tabler SVGs, complete MIT notice and provenance manifest"
          + (", official upstream matched" if upstream else "")
          + (", all icon resources verified in JAR" if jar else ""))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream", type=Path)
    parser.add_argument("--jar", type=Path)
    args = parser.parse_args()
    try:
        audit(args.upstream, args.jar)
    except (ValueError, OSError, KeyError, tarfile.TarError, zipfile.BadZipFile) as error:
        parser.exit(1, "Icon audit failed: " + str(error) + "\n")
