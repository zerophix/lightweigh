#!/usr/bin/env bash
# scripts/governance/gov-secrets-scan.sh
# 本地密钥扫描（DEC-049）。二进制存在 → 硬失败；缺失 → 尝试安装；仍无 → 软警告。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
CONFIG="$ROOT/.gitleaks.toml"
TOOLS_BIN="$ROOT/scripts/tools/gitleaks"
INSTALLER="$ROOT/scripts/governance/gov-install-gitleaks.sh"

resolve_bin() {
  if command -v gitleaks >/dev/null 2>&1; then
    command -v gitleaks
    return 0
  fi
  if [ -x "$TOOLS_BIN" ]; then
    echo "$TOOLS_BIN"
    return 0
  fi
  # worktree（DEC-050）下二进制被 gitignore、不随 worktree 带出；主仓已装则直接复用。
  # 否则下面会触发联网安装（实测一次约 75 秒），而 worktree 内每次 pre-push 都要付这个代价。
  # 与 DEC-062 同类：治理脚本必须感知 worktree，而不是假装自己在主树。
  local common_dir main_root
  if common_dir="$(git rev-parse --git-common-dir 2>/dev/null)" && [ -n "$common_dir" ]; then
    if main_root="$(cd "$common_dir/.." 2>/dev/null && pwd)" && [ -n "$main_root" ] \
      && [ -x "$main_root/scripts/tools/gitleaks" ]; then
      echo "$main_root/scripts/tools/gitleaks"
      return 0
    fi
  fi
  return 1
}

print_hint() {
  cat <<'HINT'
[gov-secrets-scan] 未找到 gitleaks。安装其一：
  bash scripts/governance/gov-install-gitleaks.sh
  brew install gitleaks
  go install github.com/zricethezav/gitleaks/v8@v8.30.1
当前为软警告（不阻断）。安装后本检查将变为硬失败。
HINT
}

BIN=""
if ! BIN="$(resolve_bin)"; then
  if [ -x "$INSTALLER" ]; then
    echo "[gov-secrets-scan] 尝试自动安装 gitleaks ..."
    if bash "$INSTALLER"; then
      BIN="$(resolve_bin || true)"
    fi
  fi
fi

if [ -z "${BIN:-}" ] || [ ! -x "${BIN:-}" ]; then
  print_hint
  exit 0
fi

echo "[gov-secrets-scan] using: $BIN ($("$BIN" version 2>/dev/null || true))"
ARGS=(detect --source "$ROOT" --redact --no-banner)
if [ -f "$CONFIG" ]; then
  ARGS+=(--config "$CONFIG")
fi

set +e
"$BIN" "${ARGS[@]}"
RC=$?
set -e

# gitleaks: 0=clean, 1=leaks, 其它=错误
if [ "$RC" -eq 0 ]; then
  echo "[gov-secrets-scan] PASS（无泄露）"
  exit 0
fi
if [ "$RC" -eq 1 ]; then
  echo "[gov-secrets-scan] FAIL：发现疑似密钥。见上方报告；误报请谨慎更新 .gitleaks.toml allowlist（DEC-049）。" >&2
  exit 1
fi
echo "[gov-secrets-scan] FAIL：gitleaks 退出码 $RC" >&2
exit "$RC"
