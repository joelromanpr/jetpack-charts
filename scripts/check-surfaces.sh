#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
git diff --check
python3 - <<'PY'
from pathlib import Path
import re
import subprocess

tracked = subprocess.check_output(['git', 'ls-files', '-z']).decode().split('\0')
files = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard']).decode().split('\0')
for name in filter(None, tracked):
    path = Path(name)
    if any(part in {'build', '.gradle', '.idea', 'outputs', 'node_modules', '.astro', 'dist'} for part in path.parts) or path.suffix in {'.asc', '.gpg', '.jks', '.keystore'} or path.name in {'local.properties', '.env'}:
        raise SystemExit(f'Private or generated file tracked: {name}')
for name in filter(None, files):
    path = Path(name)
    if path.is_file() and path.suffix in {'.kt', '.kts', '.md', '.mdx', '.astro', '.mjs', '.ts', '.py', '.css', '.json', '.svg', '.yml', '.yaml', '.toml', '.properties', '.xml', '.sh'}:
        content = path.read_text()
        if not content.endswith('\n') or any(line != line.rstrip() for line in content.splitlines()):
            raise SystemExit(f'Whitespace or final newline issue: {name}')
for path in Path('.').glob('charts-*/src/main/**/*.kt'):
    package = re.search(r'^package (.+)$', path.read_text(), re.MULTILINE)
    if package is None or not package.group(1).startswith('com.joelromanpr.charts.'):
        raise SystemExit(f'Unexpected package: {path}')
print('Repository surfaces checked.')
PY
