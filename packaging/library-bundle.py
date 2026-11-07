#!/usr/bin/env python3
"""Create an embeddable thin-JAR Maven repository with API documentation and demos."""
import argparse
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile


def artifacts(project):
    for module in ('editor', 'rich-text-area/rta', 'emoji-noto'):
        directory = Path(project) / module
        root = ET.parse(directory / 'pom.xml').getroot()
        group, artifact, version = (root.findtext('{*}' + name) for name in ('groupId', 'artifactId', 'version'))
        if not all((group, artifact, version)):
            raise ValueError(f'Missing Maven coordinates: {module}')
        name = f'{artifact}-{version}'
        target = Path('repository') / group.replace('.', '/') / artifact / version
        yield directory / 'target' / f'{name}.jar', target / f'{name}.jar'
        yield directory / 'pom.xml', target / f'{name}.pom'


def create_bundle(project, destination):
    project, destination = Path(project), Path(destination)
    entries = list(artifacts(project))
    for name in ('README.md', 'LICENSE', 'THIRD-PARTY-NOTICES.md', 'docs/API.md', 'docs/COMMANDS.md',
                 'docs/PACKAGING.md', 'docs/VALIDATION.md'):
        entries.append((project / name, Path(name)))
    for directory in ('integration-demos', 'editor/target/api-docs', 'packaging/licenses'):
        base = project / directory
        if not base.is_dir():
            raise ValueError(f'Missing library input: {directory}')
        for source in sorted(base.rglob('*')):
            relative = source.relative_to(base)
            if source.is_file() and not source.is_symlink() and 'target' not in relative.parts:
                prefix = 'javadoc' if directory == 'editor/target/api-docs' else directory
                entries.append((source, Path(prefix) / relative))
    if not (project / 'editor/target/api-docs/index.html').is_file():
        raise ValueError('Generate API Javadoc before building the library bundle')
    for source, _ in entries:
        if not source.is_file() or source.is_symlink() or source.stat().st_size == 0:
            raise ValueError(f'Missing or empty library input: {source}')
    jar = entries[0][0]
    with zipfile.ZipFile(jar) as archive:
        for required in ('module-info.class', 'de/richmarkdown/api/EditorPane.class', 'de/richmarkdown/api/EditorMenus.class'):
            if required not in archive.namelist():
                raise ValueError(f'Library API missing in thin JAR: {required}')
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + '.tmp')
    try:
        with zipfile.ZipFile(temporary, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
            for source, target in entries:
                archive.write(source, str(Path('Mardexas-library') / target))
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)
    return len(entries)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('destination', type=Path)
    parser.add_argument('--project', type=Path, default=Path(__file__).resolve().parent.parent)
    args = parser.parse_args()
    print(f'Bundled {create_bundle(args.project, args.destination)} files: {args.destination}')
