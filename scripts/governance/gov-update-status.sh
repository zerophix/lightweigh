#!/usr/bin/env bash
# scripts/governance/gov-update-status.sh
# 自动生成 STATUS.md — 委托 generate-status.py

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
python3 "$ROOT/scripts/governance/gov-generate-status.py"
