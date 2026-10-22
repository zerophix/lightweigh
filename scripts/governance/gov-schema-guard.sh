#!/usr/bin/env bash
# scripts/governance/gov-schema-guard.sh
# 黄金向量 / Room schema 变更守卫（DEC-052）
#
# 用法:
#   bash scripts/governance/gov-schema-guard.sh [--base <sha>] [--ci]
#
# 环境变量（CI）:
#   PR_BODY, PR_LABELS（逗号分隔）, GITHUB_EVENT_PATH（可选，自动读 labels/body）
#
# 退出: 0=通过（无变更或已放行）, 1=拦截
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "${ROOT:-}" ] || [ ! -d "$ROOT/domain" ]; then
  ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
fi
cd "$ROOT"

BASE=""
CI_MODE=0
while [ $# -gt 0 ]; do
  case "$1" in
    --base) BASE="$2"; shift 2 ;;
    --ci) CI_MODE=1; shift ;;
    -h|--help)
      sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done

if [ -z "$BASE" ]; then
  if git show-ref --verify --quiet refs/remotes/origin/master; then
    BASE="$(git merge-base HEAD origin/master 2>/dev/null || echo origin/master)"
  elif git show-ref --verify --quiet refs/heads/master; then
    BASE="$(git merge-base HEAD master 2>/dev/null || echo master)"
  else
    BASE="HEAD~1"
  fi
fi

export SCHEMA_GUARD_BASE="$BASE"
export SCHEMA_GUARD_CI="$CI_MODE"
export PR_BODY="${PR_BODY:-}"
export PR_LABELS="${PR_LABELS:-}"
export GITHUB_EVENT_PATH="${GITHUB_EVENT_PATH:-}"
export GITHUB_TOKEN="${GITHUB_TOKEN:-}"
export GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-}"
export PR_NUMBER="${PR_NUMBER:-${GITHUB_PR_NUMBER:-}}"

python3 - <<'PY'
import json, os, re, subprocess, sys
from pathlib import Path

ROOT = Path(".").resolve()
BASE = os.environ["SCHEMA_GUARD_BASE"]
CI = os.environ.get("SCHEMA_GUARD_CI") == "1"

PROTECTED = (
    "domain/src/test/resources/golden/",
    "infrastructure/schemas/",
)
ALLOW_LABEL = "allow-schema-change"
DEC_RE = re.compile(r"\bDEC-\d{3}\b", re.I)
ALLOW_RE = re.compile(r"allow-schema-change", re.I)
MARKER = "<!-- schema-guard-dec052 -->"

def git(*args: str) -> str:
    r = subprocess.run(["git", *args], capture_output=True, text=True)
    return (r.stdout or "").strip()

def changed_files(base: str):
    # Prefer triple-dot from merge-base semantics
    out = git("diff", "--name-only", f"{base}...HEAD")
    if not out:
        out = git("diff", "--name-only", base, "HEAD")
    return [l for l in out.splitlines() if l.strip()]

files = changed_files(BASE)
hits = [f for f in files if any(f.startswith(p) or f"/{p}" in f for p in PROTECTED)]
# also match exact prefix
hits = [f for f in files if any(f.startswith(p) for p in PROTECTED)]

if not hits:
    print("[schema-guard] 未改黄金向量 / Room schema — PASS")
    sys.exit(0)

print("[schema-guard] 检测到受保护路径变更:")
for f in hits:
    print(f"  - {f}")

body = os.environ.get("PR_BODY") or ""
labels_raw = os.environ.get("PR_LABELS") or ""
labels = {x.strip().lower() for x in labels_raw.split(",") if x.strip()}

event_path = os.environ.get("GITHUB_EVENT_PATH")
if event_path and Path(event_path).is_file():
    try:
        ev = json.loads(Path(event_path).read_text(encoding="utf-8"))
        pr = ev.get("pull_request") or {}
        if not body:
            body = pr.get("body") or ""
        for lb in pr.get("labels") or []:
            name = (lb.get("name") if isinstance(lb, dict) else str(lb)) or ""
            if name:
                labels.add(name.lower())
        if not os.environ.get("PR_NUMBER") and pr.get("number"):
            os.environ["PR_NUMBER"] = str(pr["number"])
    except (json.JSONDecodeError, OSError) as e:
        print(f"[schema-guard] 无法解析 GITHUB_EVENT_PATH: {e}", file=sys.stderr)

# Local allow: DEC file touched in same range
dec_in_diff = [f for f in files if re.match(r"docs/ai/decisions/DEC-\d{3}\.md$", f)]
allowed = False
allow_reasons = []
if ALLOW_LABEL in labels:
    allowed = True
    allow_reasons.append(f"label:{ALLOW_LABEL}")
if ALLOW_RE.search(body or ""):
    allowed = True
    allow_reasons.append("pr-body:allow-schema-change")
if DEC_RE.search(body or ""):
    allowed = True
    allow_reasons.append("pr-body:DEC-ref")
if dec_in_diff:
    allowed = True
    allow_reasons.append("diff:" + ",".join(dec_in_diff[:5]))

msg = (
    "黄金向量或 Room schema 快照已变更。\n\n"
    "**要求：**\n"
    "1. 立或更新 **DEC**（说明为何改、旧/新对照）\n"
    "2. Room：version+1、Migration、schema JSON 入库、迁移/对拍测试\n"
    "3. 黄金向量：配套测试更新，禁止删断言凑绿\n\n"
    f"**变更文件：**\n" + "\n".join(f"- `{f}`" for f in hits) + "\n\n"
    f"**放行方式：** PR 正文含 `DEC-xxx` 或 `allow-schema-change`，"
    f"或打标签 `{ALLOW_LABEL}`；本地同范围 diff 含 DEC 文件亦可。\n"
    f"{MARKER}\n"
)

# CI: post/update comment
if CI:
    token = os.environ.get("GITHUB_TOKEN") or ""
    repo = os.environ.get("GITHUB_REPOSITORY") or ""
    pr_num = os.environ.get("PR_NUMBER") or ""
    if token and repo and pr_num:
        try:
            import urllib.request
            api = f"https://api.github.com/repos/{repo}/issues/{pr_num}/comments"
            req = urllib.request.Request(
                api,
                headers={
                    "Authorization": f"Bearer {token}",
                    "Accept": "application/vnd.github+json",
                    "X-GitHub-Api-Version": "2022-11-28",
                    "User-Agent": "lightweigh-schema-guard",
                },
            )
            with urllib.request.urlopen(req, timeout=20) as resp:
                comments = json.loads(resp.read().decode())
            existing = None
            for c in comments:
                if MARKER in (c.get("body") or ""):
                    existing = c
                    break
            payload = json.dumps({"body": ("✅ 已放行（" + ", ".join(allow_reasons) + "）\n\n" if allowed else "⛔ ") + msg}).encode()
            if existing:
                url = existing["url"]
                method = "PATCH"
            else:
                url = api
                method = "POST"
            req2 = urllib.request.Request(
                url,
                data=payload,
                method=method,
                headers={
                    "Authorization": f"Bearer {token}",
                    "Accept": "application/vnd.github+json",
                    "X-GitHub-Api-Version": "2022-11-28",
                    "Content-Type": "application/json",
                    "User-Agent": "lightweigh-schema-guard",
                },
            )
            with urllib.request.urlopen(req2, timeout=20) as resp2:
                print(f"[schema-guard] PR comment {method} HTTP {resp2.status}")
        except Exception as e:
            print(f"[schema-guard] 评论失败（不阻断逻辑）: {e}", file=sys.stderr)
    else:
        print("[schema-guard] 跳过评论（缺 token/repo/pr）")

if allowed:
    print("[schema-guard] 已放行: " + ", ".join(allow_reasons))
    sys.exit(0)

print("[schema-guard] FAIL：受保护路径变更且未放行", file=sys.stderr)
print(msg, file=sys.stderr)
sys.exit(1)
PY
