#!/usr/bin/env python3
"""Validate the generated Central metadata for both publication types."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
properties = dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines()
                  if '=' in line and not line.startswith('#'))
namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
required = ['name', 'description', 'url', 'licenses/license/name', 'licenses/license/url',
            'developers/developer/id', 'developers/developer/name', 'developers/developer/email',
            'scm/url', 'scm/connection', 'scm/developerConnection']

for module, packaging in [('charts-core', 'jar'), ('charts-compose', 'aar')]:
    pom = ET.parse(root / module / 'build/publications/maven/pom-default.xml').getroot()

    def value(path):
        return (pom.findtext('/'.join(f'm:{part}' for part in path.split('/')),
                             default='', namespaces=namespace) or '').strip()

    for field, expected in [('groupId', properties['GROUP']), ('artifactId', module),
                            ('version', properties['VERSION_NAME'])]:
        if value(field) != expected:
            raise SystemExit(f'{module}: unexpected {field}: {value(field)}')
    if (value('packaging') or 'jar') != packaging:
        raise SystemExit(f'{module}: expected {packaging} packaging')
    for field in required:
        if not value(field):
            raise SystemExit(f'{module}: missing Central metadata: {field}')

print('Both Maven POMs match the current coordinates and Central metadata requirements.')
