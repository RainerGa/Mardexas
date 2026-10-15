#!/usr/bin/env python3
"""Fuehrt mehrere vom native-image-Agenten erzeugte reachability-metadata.json-
Dateien zu einer einzigen verifizierten Metadaten-Datei zusammen.

Die Agenten schreiben pro Lauf ein JSON-Objekt mit Eintraegen fuer Reflection,
JNI, Resourcen, Bundles usw. App- und Test-Lauf zeichnen jeweils nur den
genutzten Teil auf; fuer das native-image-Build wird die Vereinigung beider
Laeufe benoetigt.

Aufruf: merge-reachability-metadata.py <output.json> <input.json> [<input.json> ...]

Regeln:
  * Eintraege werden ueber ihren Identifizierer zusammengefuehrt
    (Reflection/JNI/Serialisierung: "type", Resourcen: "glob"/"regex",
    Bundles: "name"). Identische Eintraege erscheinen nur einmal.
  * Bool-Flags (allDeclaredMethods, jniAccessible, ...) werden ODER-verknuepft.
  * methods/fields werden als Signaturen vereinigt
    (methods: Name + ParameterTypes, fields: Name).
  * Die Ausgabe ist deterministisch sortiert, damit Diffs stabil bleiben.
"""
import json
import sys

# Abschnitte, deren Eintraege Listen sind und zeilenweise zusammengefuehrt
# werden koennen. Andere Abschnitte werden unveraendert uebernommen.
SECTIONS = ("reflection", "resources", "bundles", "jni", "serialization", "annotations")

# Bool-Flags des Agenten-Schemas; True gewinnt gegen False/fehlend.
BOOL_FLAGS = (
    "allDeclaredConstructors", "allDeclaredMethods", "allPublicConstructors",
    "allPublicMethods", "allDeclaredFields", "allPublicFields", "allPublicClasses",
    "allRecordComponents", "allPermittedSubclasses", "queryAllImplementors",
    "jniAccessible", "unsafeAllocated",
)

# Felder, die eine Liste von Untereintraegen identifizieren.
SIGNATURE_FIELDS = {
    "methods": ("name", "parameterTypes"),
    "fields": ("name",),
}


def signature(item, fields):
    """Eindeutiger, hashbarer Schluessel fuer einen Methoden-/Feld-Eintrag."""
    return tuple(json.dumps(item.get(f), sort_keys=True) for f in fields)


def entry_id(section, entry):
    """Identifizierer eines Haupteintrags oder None, wenn er nicht identifizierbar ist."""
    if not isinstance(entry, dict):
        return None
    for field in ("type", "glob", "regex", "name", "bundle"):
        if field in entry:
            # "condition" (z. B. typeReached) haelt separate Eintraege auseinander.
            return (field, json.dumps(entry[field], sort_keys=True),
                    json.dumps(entry.get("condition"), sort_keys=True))
    return None


def merge_entry(target, source):
    """Zieht source in das bereits existierende target herein."""
    for flag in BOOL_FLAGS:
        if source.get(flag):
            target[flag] = True
    for key, fields in SIGNATURE_FIELDS.items():
        if key not in source:
            continue
        merged = {signature(item, fields): item for item in target.get(key, [])}
        for item in source[key]:
            merged.setdefault(signature(item, fields), item)
        target[key] = sorted(merged.values(), key=lambda i: signature(i, fields))
    for key, value in source.items():
        if key in BOOL_FLAGS or key in SIGNATURE_FIELDS:
            continue
        # Noch nicht gesetzte Zusatzfelder (condition, ...) uebernehmen.
        target.setdefault(key, value)


def normalize_entry(entry):
    """Sortiert die Signaturlisten, damit auch direkt kopierte Eintraege
    (genau eine Quelle) dieselbe Reihenfolge haben wie zusammengefuehrte."""
    for key, fields in SIGNATURE_FIELDS.items():
        if key in entry and isinstance(entry[key], list):
            entry[key] = sorted(entry[key], key=lambda i: signature(i, fields))
    return entry


def sort_key(section, entry):
    ident = entry_id(section, entry)
    return json.dumps(ident if ident is not None else entry, sort_keys=True)


def merge_files(paths):
    merged = {}
    for path in paths:
        with open(path, "r", encoding="utf-8") as handle:
            data = json.load(handle)
        if not isinstance(data, dict):
            raise SystemExit(f"{path}: erwartetes JSON-Objekt, gefunden: {type(data).__name__}")
        for section, entries in data.items():
            if section not in SECTIONS or not isinstance(entries, list):
                # Skalar-/Fremd-Abschnitte: ersten gefundenen Wert behalten.
                merged.setdefault(section, entries)
                continue
            bucket = merged.setdefault(section, {})
            for entry in entries:
                ident = entry_id(section, entry)
                key = ident if ident is not None else ("_unkeyed_", json.dumps(entry, sort_keys=True))  # noqa: E501
                if key in bucket:
                    merge_entry(bucket[key], entry)
                else:
                    bucket[key] = json.loads(json.dumps(entry))
    return {
        section: (
            [normalize_entry(entries[key]) for key in sorted(
                entries, key=lambda k: sort_key(section, entries[k]))]
            if section in SECTIONS
            else entries
        )
        for section, entries in merged.items()
    }


def main(argv):
    if len(argv) < 3:
        print(
            "Aufruf: merge-reachability-metadata.py <output.json> <input.json> [<input.json> ...]",
            file=sys.stderr,
        )
        return 2
    output, inputs = argv[1], argv[2:]
    merged = merge_files(inputs)
    sections = {s: len(v) for s, v in merged.items() if isinstance(v, list)}
    summary = ", ".join(f"{k}={n}" for k, n in sorted(sections.items())) or "leer"
    with open(output, "w", encoding="utf-8") as handle:
        json.dump(merged, handle, indent=2, sort_keys=True)
        handle.write("\n")
    print(f"{output}: {summary}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
