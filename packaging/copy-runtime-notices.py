#!/usr/bin/env python3
"""Retain the exact compiler/runtime vendor's original legal notices in binary distributions."""
import argparse
from pathlib import Path
import shutil
import stat
import hashlib
import json
import xml.etree.ElementTree as ET


def copy_javafx_notices(project, destination, platform):
    project, destination = Path(project), Path(destination)
    pom = ET.parse(project / 'editor/pom.xml')
    version = pom.findtext('.//{*}properties/{*}javafx.version')
    source = project / 'packaging/licenses' / f'javafx-{version}'
    origin = json.loads((source / 'ORIGIN.json').read_text(encoding='utf-8'))
    if origin['version'] != version or platform not in origin['targets']:
        raise ValueError('JavaFX license origin does not match the runtime')
    files = origin['targets'][platform]['files']
    for module in ('javafx.base', 'javafx.controls', 'javafx.graphics'):
        for notice in ('LICENSE', 'ASSEMBLY_EXCEPTION', 'ADDITIONAL_LICENSE_INFO'):
            if f'{module}/{notice}' not in files:
                raise ValueError(f'Missing JavaFX legal notice: {module}/{notice}')
    for name, expected in files.items():
        relative = Path(name)
        if relative.is_absolute() or '..' in relative.parts:
            raise ValueError(f'Invalid license path: {name}')
        contents = (source / platform / relative).read_bytes()
        if hashlib.sha256(contents).hexdigest() != expected:
            raise ValueError(f'JavaFX license differs from upstream: {name}')
    target = destination / f'javafx-{version}-licenses'
    shutil.copytree(source / platform, target, dirs_exist_ok=True)
    shutil.copy2(source / 'ORIGIN.json', target / 'ORIGIN.json')


def make_writable(directory):
    if not directory.exists():
        return
    for path in [directory, *directory.rglob('*')]:
        if path.is_symlink():
            raise ValueError(f'Unexpected symlink in packaged legal directory: {path}')
        path.chmod(path.stat().st_mode | stat.S_IWUSR)


def copy_notices(java_home, destination):
    java_home, destination = Path(java_home), Path(destination)
    legal = java_home / 'legal'
    if not (legal / 'java.base' / 'LICENSE').is_file():
        raise ValueError(f'JDK legal notices are missing: {legal}')
    destination.mkdir(parents=True, exist_ok=True)
    # Resolve the JDK's internal legal symlinks so the resulting package is
    # self-contained even after jlink or native-image removes unused modules.
    target = destination / 'runtime-licenses'
    make_writable(target)
    shutil.copytree(legal, target, dirs_exist_ok=True, symlinks=False)
    make_writable(target)
    release = java_home / 'release'
    if release.is_file():
        shutil.copy2(release, destination / 'runtime-licenses' / 'BUILD-RUNTIME.properties')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('java_home', type=Path)
    parser.add_argument('destination', type=Path)
    parser.add_argument('--javafx-platform', required=True, choices=('linux', 'win', 'mac', 'mac-aarch64'))
    parser.add_argument('--project', type=Path, default=Path(__file__).resolve().parent.parent)
    args = parser.parse_args()
    copy_notices(args.java_home, args.destination)
    copy_javafx_notices(args.project, args.destination, args.javafx_platform)


if __name__ == '__main__':
    main()
