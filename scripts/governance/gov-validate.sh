#!/usr/bin/env bash
set -euo pipefail
_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$_DIR/../.." && pwd)"
exec python3 "$_DIR/gov-validate.py" "$@"
