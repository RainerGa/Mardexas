#!/usr/bin/env python3
"""Archive the actual build sources, recursively including initialized submodules.

Unlike git archive HEAD, this includes the working-tree changes used to compile
local packages. Ignored caches, targets, credentials and release outputs stay out.
"""
import argparse
from pathlib import Path
import subprocess
import tarfile


def source_files(repository):
    repository = Path(repository)
    result = subprocess.run(['git', '-C', str(repository), 'ls-files', '-z', '--cached',
                             '--others', '--exclude-standard'], check=True, capture_output=True)
    indexed = subprocess.run(['git', '-C', str(repository), 'ls-files', '-z', '--stage'],
                             check=True, capture_output=True)
    submodules = set()
    for entry in indexed.stdout.decode('utf-8').split('\0'):
        if entry.startswith('160000 '):
            name = entry.split('\t', 1)[1]
            submodules.add(name)
            if not (repository / name / '.git').exists():
                raise ValueError(f'Uninitialized source submodule: {repository / name}')
    for name in sorted(set(result.stdout.decode('utf-8').split('\0')) - {''}):
        path = repository / name
        if name in submodules:
            for child in source_files(path):
                yield Path(name) / child
        elif path.exists() or path.is_symlink():
            yield Path(name)


def create_archive(repository, destination):
    repository, destination = Path(repository), Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    files = list(source_files(repository))
    if not files:
        raise ValueError('No application sources found')
    with tarfile.open(destination, 'w:gz') as archive:
        for relative in files:
            archive.add(repository / relative, arcname=str(Path('Mardexas') / relative), recursive=False)
    return len(files)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('destination', type=Path)
    parser.add_argument('--repository', type=Path, default=Path(__file__).resolve().parent.parent)
    args = parser.parse_args()
    print(f'Archived {create_archive(args.repository, args.destination)} source files: {args.destination}')


if __name__ == '__main__':
    main()
