#!/usr/bin/env python3
"""Vergleicht die native-image-agent-Aufzeichnungen (app + tests) vollstaendig
gegen die verifizierten reachability-metadata.json-Dateien und meldet:

  A) Klassen, die der Agent ueber Reflection/JNI aufgezeichnet hat, aber die
     in den Metadaten fehlen (app-relevant = Kandidaten fuer die Metadaten),
  B) Klassen mit fehlenden Methoden/Feldern,
  C) Ressourcen aus resource-config.json, die nicht registriert sind.

Aufruf: trace-diff.py <trace-dir> <meta-linux.json> <meta-sw.json> [--verbose]
"""
import json
import os
import sys

# Pakete, die fuer den Native-Runtime nicht interessant sind (Test- und
# Maven-Infrastruktur). Alles ausserhalb wird als "app-relevant" markiert.
SKIP_PREFIXES = (
    "org.junit", "org.opentest4j", "org.apiguardian", "org.apache.maven",
    "org.codehaus.plexus", "org.slf4j", "org.testfx",
    "jdk.internal", "com.sun.tools", "sun.tools", "org.gradle",
    # Build-Stack von Maven selbst (Guice/Aether/Sisu – im Test-Lauf sichtbar)
    "com.google.common", "com.google.inject", "com.google.devtools",
    "org.eclipse.aether", "org.eclipse.sisu", "org.sonatype",
    "javax.inject", "com.ctc.wstx", "org.eclipse.sisu",
    "jdk.proxy", "org.apache.maven",
)



def load_agent_dir(trace_dir):
    """Klassen -> Info aus reflect+jni-config aller Trace-Laeufe."""
    classes = {}
    resources = {}  # sub-run -> resource globs

    def ingest(entries, kind, src):
        for entry in entries:
            name = entry.get("type") or entry.get("name")
            if not name or not isinstance(name, str):
                # exotische Eintraege wie {"type": {"proxy": [...]}}
                continue
            ent = classes.setdefault(name, {"kinds": set(), "methods": set(),
                                            "fields": set(), "flags": set(),
                                            "from": set()})
            ent["kinds"].add(kind)
            ent["from"].add(src)
            for m in entry.get("methods", []):
                ent["methods"].add((m.get("name", "?"),
                                    tuple(m.get("parameterTypes", []))))
            for f in entry.get("fields", []):
                ent["fields"].add(f.get("name", "?"))
            for flag in ("allDeclaredMethods", "allDeclaredFields",
                         "allDeclaredConstructors", "allPublicMethods",
                         "allPublicFields", "allPublicConstructors",
                         "allDeclaredClasses", "allPublicClasses",
                         "queryAllDeclaredMethods", "queryAllPublicMethods",
                         "jniAccessible", "methodsAccessible"):
                if entry.get(flag):
                    ent["flags"].add(flag)

    for sub in ("app", "tests"):
        # GraalVM 25 schreibt eine kombinierte reachability-metadata.json
        combined = os.path.join(trace_dir, sub, "reachability-metadata.json")
        if os.path.exists(combined):
            with open(combined) as fh:
                try:
                    data = json.load(fh)
                except json.JSONDecodeError:
                    data = {}
            for section in ("reflection", "jni"):
                ingest(data.get(section, []), section, sub)
            for r in data.get("resources", []):
                if isinstance(r, dict):
                    resources.setdefault(sub, set()).add(
                        r.get("glob", r.get("pattern", "?")))
        # Fallback: alte Einzel-Configs (aeltere Agent-Versionen)
        for fname, kind in (("reflect-config.json", "reflection"),
                            ("jni-config.json", "jni")):
            path = os.path.join(trace_dir, sub, fname)
            if not os.path.exists(path):
                continue
            with open(path) as fh:
                try:
                    entries = json.load(fh)
                except json.JSONDecodeError:
                    print(f"WARN: {path} nicht lesbar")
                    continue
            ingest(entries, kind, sub)

        res_path = os.path.join(trace_dir, sub, "resource-config.json")
        if os.path.exists(res_path):
            with open(res_path) as fh:
                try:
                    res = json.load(fh)
                except json.JSONDecodeError:
                    continue
            for r in res.get("resources", []):
                resources.setdefault(sub, set()).add(
                    r.get("glob", r.get("pattern", "?")))
    return classes, resources


def load_metadata(paths):
    """Klassen -> Info aus reachability-metadata.json (reflection+jni)."""
    meta = {}
    resources = set()
    for path in paths:
        if not os.path.exists(path):
            continue
        with open(path) as fh:
            data = json.load(fh)
        for section in ("reflection", "jni"):
            for entry in data.get(section, []):
                name = entry.get("type") or entry.get("name")
                if not name:
                    continue
                ent = meta.setdefault(name, {"sections": set(), "methods": set(),
                                             "fields": set(), "flags": set()})
                ent["sections"].add(section)
                for m in entry.get("methods", []):
                    ent["methods"].add((m.get("name", "?"),
                                        tuple(m.get("parameterTypes", []))))
                for f in entry.get("fields", []):
                    ent["fields"].add(f.get("name", "?"))
                for flag in ("allDeclaredMethods", "allDeclaredFields",
                             "allDeclaredConstructors", "allPublicMethods",
                             "allPublicFields", "allPublicConstructors",
                             "allDeclaredClasses", "allPublicClasses",
                             "jniAccessible", "methodsAccessible"):
                    if entry.get(flag):
                        ent["flags"].add(flag)
        for entry in data.get("resources", []):
            if isinstance(entry, dict):
                resources.add(entry.get("glob", entry.get("pattern", "?")))
    return meta, resources


def relevant(name, srcs):
    if name.startswith(SKIP_PREFIXES):
        return False
    if name.startswith("["):
        return False
    # JDK-/Basisreflection, die NUR im Maven-Testlauf oder aus der
    # JavaFX-Quellanalyse auftaucht, stammt nicht aus der Anwendung –
    # JDK-Klassen sind in GraalVM bereits eingebettet registriert.
    if set(srcs) <= {"tests", "jfx-src"} and name.startswith(
            ("java.", "javax.", "jdk.", "sun.", "com.sun.org")):
        return False
    return True


def main():
    trace_dir, meta_linux, meta_sw = sys.argv[1:4]
    agent_classes, agent_resources = load_agent_dir(trace_dir)
    meta_classes, meta_resources = load_metadata([meta_linux, meta_sw])

    # JNI-Scan-Ergebnis (strings auf .so/Binary) ergaenzen – der Agent deckt
    # JNI-Aufrufe nativer Bibliotheken im JVM-Betrieb nicht ab.
    # Normalisierung: Slash->Punkt, ".class"-Suffix weg, Ressourcenpfade
    # (.css/.png/...) und Paket-Präfixe (letztes Segment ohne Grossbuchstaben)
    # ausschliessen – das sind keine FindClass-Klassennamen.
    resource_suffixes = (".css", ".properties", ".png", ".jpg", ".jpeg",
                         ".gif", ".xml", ".ttf", ".otf", ".fnt", ".fxml",
                         ".html", ".txt", ".svg")
    jni_file = os.path.join(trace_dir, "jni-classes.txt")
    if os.path.exists(jni_file):
        with open(jni_file) as fh:
            for line in fh:
                name = line.strip()
                if not name:
                    continue
                if name.endswith(".class"):
                    name = name[:-len(".class")]
                name = name.replace("/", ".")
                if name.endswith(resource_suffixes) or name.endswith("."):
                    continue
                last_segment = name.rsplit(".", 1)[-1]
                if last_segment != last_segment.upper() \
                        and not any(c.isupper() for c in last_segment) \
                        and "$" not in last_segment:
                    # z.B. ...win.themes oder ...skin.modena – Paket/Resource
                    if not any(c.isupper() for c in last_segment):
                        continue
                ent = agent_classes.setdefault(
                    name, {"kinds": set(), "methods": set(), "fields": set(),
                           "flags": set(), "from": set()})
                ent["kinds"].add("jni-scan")
                ent["from"].add("so-scan")

    # FindClass-Ziele aus der JavaFX-Native-Quelle (trace-reachability.sh, JFX_SRC)
    src_file = os.path.join(trace_dir, "jni-classes-src.txt")
    if os.path.exists(src_file):
        with open(src_file) as fh:
            for line in fh:
                name = line.strip().replace("/", ".")
                if not name:
                    continue
                ent = agent_classes.setdefault(
                    name, {"kinds": set(), "methods": set(), "fields": set(),
                           "flags": set(), "from": set()})
                ent["kinds"].add("jni-src")
                ent["from"].add("jfx-src")



    missing_app = []
    missing_test_only = []
    member_gaps = []
    for name, info in sorted(agent_classes.items()):
        if name in meta_classes:
            m = meta_classes[name]
            # Nur Mitglieder-Genauigkeit pruefen, wenn keine all*-Flags vorliegen
            if not (m["flags"] & {"allDeclaredMethods", "allPublicMethods",
                                  "allDeclaredFields", "allPublicFields",
                                  "allDeclaredConstructors",
                                  "allPublicConstructors"}):
                missing_methods = info["methods"] - m["methods"]
                if missing_methods:
                    member_gaps.append((name, sorted(missing_methods),
                                        sorted(info["from"])))
            continue
        entry = (name, sorted(info["kinds"]), sorted(info["from"]))
        if relevant(name, info["from"]):
            missing_app.append(entry)
        else:
            missing_test_only.append(entry)

    app_res = agent_resources.get("app", set())
    tests_res = agent_resources.get("tests", set())
    missing_res = sorted(app_res - meta_resources)

    print("=" * 70)
    print(f"Agent: {len(agent_classes)} Klassen aufgezeichnet, "
          f"{sum(len(v) for v in agent_resources.values())} Ressourcen")
    print(f"Metadaten: {len(meta_classes)} Klassen registriert")
    print("=" * 70)
    print(f"\nA) APP-RELEVANTE Klassen OHNE Metadaten-Eintrag "
          f"({len(missing_app)}):")
    for name, kinds, src in missing_app:
        print(f"  {name}  [{'+'.join(kinds)}]  (aus: {','.join(src)})")
    print(f"\nB) MEMBER-LUECKEN bei vorhandenen Klassen ({len(member_gaps)}):")
    for name, methods, src in member_gaps:
        meths = ", ".join(f"{n}({','.join(p)})" for n, p in methods[:6])
        print(f"  {name}: {meths}  (aus: {','.join(src)})")
    print(f"\nC) APP-Ressourcen ohne Registrierung ({len(missing_res)}): "
          f"[tests nochmal: {len(tests_res)} – Build-Infrastruktur]")
    for r in missing_res:
        print(f"  {r}")
    print(f"\nD) Nur Test-/Build-Infrastruktur betroffen: "
          f"{len(missing_test_only)} Klassen (nicht native-relevant)")
    if len(sys.argv) > 4 and sys.argv[4] == "--verbose":
        for name, kinds, src in missing_test_only:
            print(f"  {name}  [{'+'.join(kinds)}]  (aus: {','.join(src)})")


if __name__ == "__main__":
    main()


