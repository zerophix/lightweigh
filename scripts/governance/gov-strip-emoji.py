#!/usr/bin/env python3
"""Strip emoji from project files. --dry-run to detect only."""
import os, sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "modules"))
from gov_emoji import EMOJI_RE, KEYCAP_RE, strip_emoji as _strip_emoji

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "../.."))
SKIP_DIRS = {'.git', 'node_modules', 'venv', '.venv', '__pycache__', '.next',
             '.cache', '.idea', 'site', 'dist', '.pytest_cache', '.mypy_cache',
             '.eggs', 'egg-info', '.ruff_cache', 'build', 'target'}
EXTENSIONS = ('.md', '.py', '.sh', '.yml', '.yaml', '.tsx', '.ts', '.js', '.css',
              '.txt', '.json', '.html', '.toml')

def scan_files(path, dry_run):
    modified, found = 0, 0
    if os.path.isfile(path):
        file_list = [path]
    else:
        file_list = []
        for dirpath, dirs, filenames in os.walk(path):
            dirs[:] = [d for d in dirs if d not in SKIP_DIRS and not d.startswith('.')]
            for fn in filenames:
                if fn.endswith(EXTENSIONS):
                    file_list.append(os.path.join(dirpath, fn))
    for fp in file_list:
        rel = os.path.relpath(fp, ROOT)
        try:
            with open(fp, 'r', encoding='utf-8') as fh:
                content = fh.read()
        except Exception:
            continue
        stripped = _strip_emoji(content)
        if stripped != content:
            found += 1
            m = EMOJI_RE.search(content)
            line_num = content[:m.start()].count('\n') + 1 if m else 1
            if dry_run:
                print(f"  [FOUND] {rel}:{line_num}")
            else:
                with open(fp, 'w', encoding='utf-8') as fh:
                    fh.write(stripped)
                modified += 1
                print(f"  [FIX] {rel}:{line_num}")
    return modified, found

def main():
    import sys
    dry = '--dry-run' in sys.argv
    m, fn = scan_files(ROOT, dry)
    if dry:
        print(f"\n{fn} files with emoji."); sys.exit(1 if fn > 0 else 0)
    else:
        print(f"\n{m} files modified (of {fn} found).")

if __name__ == '__main__':
    main()
