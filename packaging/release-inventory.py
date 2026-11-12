#!/usr/bin/env python3
"""Require every release target and create deterministic SHA-256 checksums."""
import argparse
import hashlib
from pathlib import Path
import re


def expected_assets(version):
    if not re.fullmatch(r'\d+(?:\.\d+){0,2}', version):
        raise ValueError(f'Invalid numeric release version: {version}')
    suffixes = ['linux-x86_64-portable.tar.gz', 'linux-x86_64.deb',
                'linux-x86_64.rpm', 'linux-x86_64.flatpak', 'source.tar.gz',
                'windows-x86_64-portable.zip', 'windows-x86_64.msi', 'windows-x86_64.exe', 'library.zip']
    suffixes.extend(f'macos-x86_64{suffix}' for suffix in ('-portable.zip', '.dmg', '.pkg'))
    for target in ('linux-x86_64', 'windows-x86_64', 'macos-x86_64'):
        suffixes.append(f'{target}-fat.jar')
    for target in ('linux-x86_64', 'windows-x86_64'):
        suffixes.append(f'{target}-native.tar.gz')
    return {f'Mardexas-{version}-{suffix}' for suffix in suffixes}


def inventory(directory, version):
    directory = Path(directory)
    expected = expected_assets(version)
    present = {entry.name for entry in directory.iterdir() if entry.name != 'SHA256SUMS'}
    if expected != present:
        raise ValueError(f'Incomplete release: missing={sorted(expected - present)}, unexpected={sorted(present - expected)}')
    lines = []
    for name in sorted(expected):
        path = directory / name
        if not path.is_file() or path.is_symlink() or path.stat().st_size == 0:
            raise ValueError(f'Empty or invalid release asset: {name}')
        with path.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        lines.append(f'{digest}  {name}\n')
    (directory / 'SHA256SUMS').write_text(''.join(lines), encoding='utf-8')
    return len(lines)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('version')
    args = parser.parse_args()
    print(f'Complete release: {inventory(args.directory, args.version.removeprefix("v"))} assets')
