#!/usr/bin/env bash
# scripts/governance/gov-test-guard.sh
# src/main 变更缺少同模块 src/test|androidTest 时提醒（DEC-053）
#
# 用法:
#   bash scripts/governance/gov-test-guard.sh [--base <sha>] [--ci] [--strict]
#
# 默认: 警告（exit 0）；--strict 时失败，可用 allow-no-tests 放行
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "${ROOT:-}" ] || [ ! -d "$ROOT/domain" ]; then
  ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
fi
cd "$ROOT"

BASE=""
CI_MODE=0
STRICT=0
while [ $# -gt 0 ]; do
  case "$1" in
    --base) BASE="$2"; shift 2 ;;
    --ci) CI_MODE=1; shift ;;
    --strict) STRICT=1; shift ;;
    -h|--help)
      sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
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

export TEST_GUARD_BASE="$BASE"
export TEST_GUARD_CI="$CI_MODE"
export TEST_GUARD_STRICT="$STRICT"
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
BASE = os.environ["TEST_GUARD_BASE"]
CI = os.environ.get("TEST_GUARD_CI") == "1"
STRICT = os.environ.get("TEST_GUARD_STRICT") == "1"

ALLOW_LABEL = "allow-no-tests"
ALLOW_RE = re.compile(r"allow-no-tests", re.I)
MARKER = "<!-- test-guard-dec053 -->"

# Ignore non-logic main paths (resources / manifests / IDE noise)
IGNORE_MAIN = re.compile(
    r"("
    r"/src/main/res/"
    r"|/src/main/assets/"
    r"|/AndroidManifest\.xml$"
    r"|/ic_launcher[^/]*$"
    r"|\.(png|webp|xml|ttf|otf)$"
    r")",
    re.I,
)

MAIN_RE = re.compile(r"^(.+)/src/main/(?:java|kotlin|jvm)/")
# Also catch generic src/main (e.g. resources already ignored)
MAIN_ANY = re.compile(r"^(.+)/src/main/")
TEST_RE = re.compile(r"^(.+)/src/(?:test|androidTest|testDebug|androidTestDebug)/")


def git(*args: str) -> str:
    r = subprocess.run(["git", *args], capture_output=True, text=True)
    return (r.stdout or "").strip()


def changed_files(base: str):
    out = git("diff", "--name-only", f"{base}...HEAD")
    if not out:
        out = git("diff", "--name-only", base, "HEAD")
    return [l for l in out.splitlines() if l.strip()]


files = changed_files(BASE)
main_by_mod = {}
test_mods = set()

for f in files:
    m = TEST_RE.match(f)
    if m:
        test_mods.add(m.group(1))
        continue
    if IGNORE_MAIN.search(f):
        continue
    m = MAIN_RE.match(f) or (
        MAIN_ANY.match(f) if "/src/main/" in f and not IGNORE_MAIN.search(f) else None
    )
    if not m:
        continue
    # skip pure res already handled; for MAIN_ANY only keep java/kotlin-like or non-ignored
    if MAIN_RE.match(f) is None and IGNORE_MAIN.search(f):
        continue
    if MAIN_RE.match(f) is None:
        # e.g. src/main/AndroidManifest already ignored; other src/main files
        if not re.search(r"/src/main/(java|kotlin|jvm)/", f):
            # allow flagging .kt/.java under odd layouts
            if not f.endswith((".kt", ".java", ".kts")):
                continue
    mod = m.group(1)
    main_by_mod.setdefault(mod, []).append(f)

missing = {mod: paths for mod, paths in main_by_mod.items() if mod not in test_mods}

if not missing:
    print("[test-guard] 无「改 main 却无同模块测试」情形 — PASS")
    sys.exit(0)

print("[test-guard] 以下模块改了 src/main，但同范围未改 src/test|androidTest:")
for mod, paths in sorted(missing.items()):
    print(f"  module: {mod}")
    for p in paths[:12]:
        print(f"    - {p}")
    if len(paths) > 12:
        print(f"    ... +{len(paths) - 12} more")

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
        print(f"[test-guard] 无法解析 event: {e}", file=sys.stderr)

allowed = False
reasons = []
if ALLOW_LABEL in labels:
    allowed = True
    reasons.append(f"label:{ALLOW_LABEL}")
if ALLOW_RE.search(body or ""):
    allowed = True
    reasons.append("pr-body:allow-no-tests")

msg_lines = [
    "检测到 **src/main** 逻辑变更，但同模块 **src/test / androidTest** 无改动。",
    "",
    "**建议：** 为行为变更补 JVM 单测或 androidTest；纯文案/资源可忽略。",
    "",
    "**涉及模块：**",
]
for mod, paths in sorted(missing.items()):
    msg_lines.append(f"- `{mod}`（{len(paths)} 个 main 文件）")
msg_lines += [
    "",
    f"**放行（仅 --strict 失败时需要）：** 标签或正文 `{ALLOW_LABEL}`。",
    "",
    f"默认模式为**警告**（不红 CI）；加 `--strict` 才失败。",
    MARKER,
]
msg = "\n".join(msg_lines)

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
                    "User-Agent": "qk-health-test-guard",
                },
            )
            with urllib.request.urlopen(req, timeout=20) as resp:
                comments = json.loads(resp.read().decode())
            existing = next((c for c in comments if MARKER in (c.get("body") or "")), None)
            prefix = "✅ 已放行（" + ", ".join(reasons) + "）\n\n" if allowed else "⚠️ "
            payload = json.dumps({"body": prefix + msg}).encode()
            if existing:
                url, method = existing["url"], "PATCH"
            else:
                url, method = api, "POST"
            req2 = urllib.request.Request(
                url,
                data=payload,
                method=method,
                headers={
                    "Authorization": f"Bearer {token}",
                    "Accept": "application/vnd.github+json",
                    "X-GitHub-Api-Version": "2022-11-28",
                    "Content-Type": "application/json",
                    "User-Agent": "qk-health-test-guard",
                },
            )
            with urllib.request.urlopen(req2, timeout=20) as resp2:
                print(f"[test-guard] PR comment {method} HTTP {resp2.status}")
        except Exception as e:
            print(f"[test-guard] 评论失败（不阻断）: {e}", file=sys.stderr)
    else:
        print("[test-guard] 跳过评论（缺 token/repo/pr）")

if not STRICT:
    print("[test-guard] WARN（默认不失败）— 建议补测试或确认可忽略")
    sys.exit(0)

if allowed:
    print("[test-guard] 已放行: " + ", ".join(reasons))
    sys.exit(0)

print("[test-guard] FAIL（--strict）：缺同模块测试且未放行", file=sys.stderr)
print(msg, file=sys.stderr)
sys.exit(1)
PY
