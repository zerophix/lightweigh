#!/usr/bin/env bash
# ============================================================
# scripts/governance/gov-ci-scope.sh
# CI 变更范围判定 — 决定 build-and-guard 是否执行重型 gradle 构建
#
# 动机（DEC-058）: 原内联正则只认 docs/ 与 *.md，导致 scripts/、tests/、
# design/、.opencode/ 这类**不参与 gradle 构建**的变更被判定为 code=true，
# 白跑全量构建。pull_request 事件以 master 为基线（整条分支），
# 因此开 PR 时几乎必然触发重型构建。
#
# 用法:
#   git diff --name-only A B | bash scripts/governance/gov-ci-scope.sh
#
# 输出契约:
#   stdout 仅一行 `code=true` 或 `code=false` —— 供 CI 追加到 $GITHUB_OUTPUT
#   stderr 为诊断信息 —— 出现在 CI 日志，不污染 stdout
#
# 安全性（治理设计约束 Q4）:
#   采用**白名单**而非黑名单 —— 只有落在已知非构建路径内的变更才跳过构建。
#   gradle 模块固定为 app / application / domain / infrastructure 加根部
#   构建脚本，它们**均不在白名单内**，故只要动到任一源码或构建文件，
#   判定必为 code=true。扩展白名单只减少假阳性，不会让真源码漏过。
#
#   保守分支: 空输入或无法列举变更 → code=true（宁可多跑一轮，
#   也不留门禁空洞，与 ci.yml 中「基线不可用按代码处理」一致）。
# ============================================================
set -euo pipefail

# 变更路径全部落在本模式内 → 非构建类，跳过重型构建
ALLOW_RE='^(docs/|design/|scripts/|tests/|\.ai/|\.cache/|\.opencode/|\.claude/|mkdocs\.yml$|.*\.md$)'

FILES="$(cat || true)"

# 去空行
FILES="$(printf '%s\n' "$FILES" | sed '/^[[:space:]]*$/d')"

if [ -z "$FILES" ]; then
  echo "code=true"
  echo "[scope] 未列举到变更文件（diff 为空或失败）→ 保守按 code=true 处理" >&2
  exit 0
fi

NON_CODE="$(printf '%s\n' "$FILES" | grep -vE "$ALLOW_RE" || true)"

if [ -n "$NON_CODE" ]; then
  echo "code=true"
  echo "[scope] 判定 code=true：以下变更不属于非构建类路径" >&2
  printf '%s\n' "$NON_CODE" | sed 's/^/  /' >&2
  echo "[scope] 重型构建不可跳过（gov-local-ci 亦应本地跑过）" >&2
else
  echo "code=false"
  echo "[scope] 判定 code=false：全部变更落在非构建类路径，跳过重型 gradle 构建" >&2
  echo "[scope] 覆盖路径: docs/ design/ scripts/ tests/ .ai/ .cache/ .opencode/ .claude/ *.md mkdocs.yml" >&2
  echo "[scope] gov-validate 与 pytest 仍由 docs-validate job 执行，门禁不空缺" >&2
fi
