#!/usr/bin/env python3
"""Launch a real external JPMS consumer on Linux, Windows or macOS after build-library.sh."""
import os
from pathlib import Path
import subprocess
import sys


def command(project, arguments, java_home=None):
    project = Path(project)
    inputs = project / 'editor/target/library-input'
    modules = sorted(inputs.glob('*.jar'))
    for directory, pattern in (('editor', 'RichMarkDownEditor-*.jar'), ('integration-demos', 'mardexas-integration-demos-*.jar')):
        jars = [path for path in (project / directory / 'target').glob(pattern)
                if not any(path.stem.endswith('-' + classifier) for classifier in ('fat', 'sources', 'javadoc', 'tests'))]
        if len(jars) != 1:
            raise ValueError(f'Build a single thin JAR first: {directory}/target')
        modules.extend(jars)
    if not any(path.name.startswith('javafx-controls-') for path in modules):
        raise ValueError('Missing JavaFX runtime: run packaging/build-library.sh first')
    java = str(Path(java_home) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')) if java_home else 'java'
    return [java, '--enable-native-access=javafx.graphics', '--module-path', os.pathsep.join(map(str, modules)),
            '--module', 'mardexas.integration.demos/de.richmarkdown.demo.IntegrationDemo', *arguments]


if __name__ == '__main__':
    try:
        result = subprocess.run(command(Path(__file__).resolve().parent.parent, sys.argv[1:], os.environ.get('JAVA_HOME')), check=False)
        sys.exit(result.returncode)
    except (OSError, ValueError) as error:
        sys.exit(str(error))
