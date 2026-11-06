#!/usr/bin/env python3
"""Offline source/package gate for the pinned, Google-only emoji distribution."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parent.parent
PREFIX = "com/gluonhq/emoji/"
ASSETS = Path("emoji-noto/src/main/resources") / PREFIX
REQUIRED = {
    "emoji.csv", "noto/NOTICE.txt", "noto/LICENSE-NOTO-APACHE-2.0.txt",
    "noto/LICENSE-NOTO-FLAGS.txt", "noto/LICENSE-EMOJI-DATA-MIT.txt",
    "noto/LICENSE-GLUON-GPL-3.0.txt", "noto/UPSTREAM-EMOJI-DATA-README.md",
    "noto/UPSTREAM-NOTO-README.md",
    *[f"noto/sheet_google_{size}_clean.png" for size in (20, 32, 64)],
}
SHEETS = {
    "noto/sheet_google_20_clean.png": "93a0ead1cf0f82b4b4406dc372a62d75a501954d759a5b497a7de9e47d5aa78e",
    "noto/sheet_google_32_clean.png": "113896ca5f7d6e9c01edcdb5ba9aeca22bd94c9b541111da1c24616735f2e37a",
    "noto/sheet_google_64_clean.png": "12f4498fbcb18f1c7291fecdd971573bf6bca6d342041ee3676d42d3de7dc174",
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def manifest(root=ROOT):
    data = json.loads((root / ASSETS / "noto/sha256.json").read_text(encoding="utf-8"))
    if set(data) != REQUIRED or any(data.get(name) != value for name, value in SHEETS.items()):
        raise ValueError("Unexpected emoji manifest or unreviewed artwork")
    return data


def audit_sources(root=ROOT):
    expected = manifest(root)
    base = root / ASSETS
    actual = {str(p.relative_to(base)).replace("\\", "/") for p in base.rglob("*") if p.is_file()}
    if actual != REQUIRED | {"noto/sha256.json"}:
        raise ValueError("Unexpected or missing emoji source resources")
    for name, sha in expected.items():
        if digest((base / name).read_bytes()) != sha:
            raise ValueError(f"Changed emoji resource: {name}")
    return expected


def audit_jar(path, expected, require_resources=True):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError(f"Duplicate JAR entries: {path}")
        for name in names:
            lower = name.lower()
            if ("sheet_apple" in lower or lower.startswith(PREFIX + "offline/")
                    or "downloadableemojispriteloader" in lower
                    or lower.startswith("meta-inf/maven/com.gluonhq.emoji/")):
                raise ValueError(f"Forbidden old emoji dependency/artwork/loader: {name}")
            if name.startswith(PREFIX) and lower.endswith((".png", ".svg", ".jpg", ".gif", ".webp")):
                if name[len(PREFIX):] not in SHEETS:
                    raise ValueError(f"Unreviewed emoji artwork: {name}")
        present = PREFIX + "noto/sha256.json" in names
        if require_resources and not present:
            raise ValueError(f"Missing audited Noto resources: {path}")
        if present:
            if json.loads(archive.read(PREFIX + "noto/sha256.json")) != expected:
                raise ValueError("Packaged emoji manifest differs from reviewed source")
            for name, sha in expected.items():
                if PREFIX + name not in names or digest(archive.read(PREFIX + name)) != sha:
                    raise ValueError(f"Missing or changed packaged emoji resource: {name}")
            if PREFIX + "NotoEmojiSpriteLoader.class" not in names:
                raise ValueError("Missing local-only Noto loader")
        return present


def audit_directory(path, expected):
    jars = list(Path(path).glob("*.jar"))
    if not jars:
        raise ValueError("No application dependency JARs to audit")
    found = sum(audit_jar(jar, expected, False) for jar in jars)
    if found != 1:
        raise ValueError(f"Expected exactly one audited Noto provider, found {found}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", action="append", default=[], type=Path)
    parser.add_argument("--input-dir", type=Path)
    args = parser.parse_args()
    try:
        expected = audit_sources()
        for jar in args.jar:
            audit_jar(jar, expected)
        if args.input_dir:
            audit_directory(args.input_dir, expected)
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        print(f"Emoji audit FAILED: {error}", file=sys.stderr)
        return 1
    print("Emoji audit OK: pinned Google-only clean sheets, original licenses, no old emoji loader/artwork")
    return 0


if __name__ == "__main__":
    sys.exit(main())
