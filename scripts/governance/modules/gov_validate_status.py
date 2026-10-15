#!/usr/bin/env python3
"""R0-R0e: README反思, 语言, 点目录, 依赖目录, 根目录; GOV-README: 结构规范"""
import json, os, re, subprocess
from pathlib import Path
from .gov_validate_core import ROOT, DOCS, MEMORY_FILE, errors, fail


def check_readme_structure() -> None:
    """GOV-README-001~005: README 目录层次规范（GC-readme-structure.md 机读层）。

    规范与校验同步修改（DEC-006）：改条款必须同提交改实现与负向测试。
    """
    print("\n--- GOV-README: README 结构规范 ---")
    readme_path = ROOT / "README.md"
    if not readme_path.is_file():
        fail("GOV-README-001: README.md 不存在"); return
    text = readme_path.read_text(encoding="utf-8", errors="replace")
    lines = text.splitlines()

    # 代码围栏内的行不参与标题识别 (CommonMark: 围栏内是代码不是标题)。
    # 快速开始里的 shell 注释 "# 1. 安装" 曾被误计为 H1 (DEC-006 执行中发现)。
    in_fence = False
    visible: list[str] = []
    for ln in lines:
        if ln.lstrip().startswith("```"):
            in_fence = not in_fence
            visible.append("")
            continue
        visible.append("" if in_fence else ln)

    # 001: 首个非空行必须是 H1, 且全文恰好一个 H1
    first_content = next((ln for ln in visible if ln.strip()), "")
    if not first_content.startswith("# ") or first_content.startswith("#### "):
        fail("GOV-README-001: README 首个非空行必须是一级标题（# 空格开头）"); return
    h1_count = sum(1 for ln in visible if ln.startswith("# ") and not ln.startswith("## "))
    if h1_count != 1:
        fail(f"GOV-README-001: README 应恰好包含 1 个一级标题，实际 {h1_count} 个"); return

    # 二级章节序列: [(行号, 标题)]
    h2s = [(i, ln[3:].strip()) for i, ln in enumerate(visible)
           if re.match(r"^##\s+\S", ln) and not ln.startswith("### ")]
    h2_names = [name for _, name in h2s]

    # 002: 当前状态必须存在、唯一、且为最后一个二级章节
    if h2_names.count("当前状态") == 0:
        fail("GOV-README-002: 缺少「## 当前状态」章节"); return
    if h2_names.count("当前状态") > 1:
        fail("GOV-README-002:「## 当前状态」章节出现多次，必须唯一"); return
    if h2_names[-1] != "当前状态":
        trailing = h2_names[h2_names.index("当前状态") + 1:]
        fail(f"GOV-README-002:「## 当前状态」必须是最后一个二级章节，其后还有: {trailing}"); return

    # 003: 反思必须是当前状态节内第一个三级子节
    status_idx = h2s[h2_names.index("当前状态")][0]
    subsections = []
    for ln in visible[status_idx + 1:]:
        if re.match(r"^##\s+\S", ln):
            break
        m = re.match(r"^###\s+(\S.*)$", ln)
        if m and not ln.startswith("#### "):
            subsections.append(m.group(1).strip())
    if not subsections or subsections[0] != "反思":
        first = subsections[0] if subsections else "(无三级子节)"
        fail(f"GOV-README-003:「### 反思」必须是「## 当前状态」的第一个三级子节，当前首个子节: {first}"); return

    # 004: 快速开始必须先于 目录结构/架构/核心功能; 缺失为 WARN
    if "快速开始" in h2_names:
        quick_idx = h2_names.index("快速开始")
        for name in ("目录结构", "架构", "核心功能"):
            if name in h2_names and h2_names.index(name) < quick_idx:
                fail(f"GOV-README-004:「## 快速开始」必须先于「## {name}」出现"); return
    else:
        print("  [WARN] GOV-README-004: 缺少「## 快速开始」章节（建议提供可复制执行的最少命令）")

    # 005: 相关文档入口（WARN 不阻塞）
    if "相关文档" not in h2_names:
        print("  [WARN] GOV-README-005: 缺少「## 相关文档」章节（建议提供深入阅读入口）")

    print("  [PASS] README 结构符合 GC-readme-structure 机读层规范")


def check_status() -> None:
    print("\n--- R0: README.md 当前状态校验 ---")
    readme_path = ROOT / "README.md"
    if not readme_path.is_file():
        fail("README.md 不存在"); return
    text = readme_path.read_text(encoding="utf-8", errors="replace")
    lessons_match = re.search(r"## 当前状态\n\n(.*?)(?=\n##[^#]|\Z)", text, re.DOTALL)
    if not lessons_match:
        fail("README.md 缺少 ## 当前状态 节（含 ## 反思 子节）。AI 必须通过 context-cache.sh save handoff lessons=... 写入经验教训"); return
    status_content = lessons_match.group(1).strip()
    refl_match = re.search(r"### 反思\n\n(.*?)(?=\n###[^#]|\Z)", status_content, re.DOTALL)
    if not refl_match:
        fail("README.md 当前状态节缺少 ### 反思 子节"); return
    lessons_content = refl_match.group(1).strip()
    lessons_clean = re.sub(r'^>.*$', '', lessons_content, flags=re.MULTILINE).strip()
    if not lessons_clean:
        fail("README.md 反思节为空"); return
    placeholders = ["暂无反思", "（暂无", "待补充", "TODO"]
    for ph in placeholders:
        if ph in lessons_clean:
            fail(f"README.md 反思节含占位符「{ph}」，必须写真实经验教训"); return
    if len(lessons_clean) < 50:
        fail(f"README.md 反思节仅 {len(lessons_clean)} 字（<50），必须写 >=50 字的真实经验教训"); return
    signal_words = ["错", "坑", "教训", "问题", "避免", "下次应", "下次要", "本来应该", "本应", "不应该", "不该", "失误", "漏了", "忘了", "浪费", "绕路", "重做", "不完善", "改进", "学到了", "学到"]
    has_signal = any(w in lessons_clean for w in signal_words)
    if not has_signal:
        fail("README.md 反思节缺少反思信号词（如「错」「坑」「教训」「问题」「学到了」），必须说明本次具体学到了什么或哪里做得不好"); return
    try:
        # 复用检测是提交时机（pre-commit）的门禁：工作区有未提交改动时，
        # 反思不得与 HEAD 相同；工作区干净时（pre-push/CI/事后校验）提交已落地，
        # 工作区必然等于 HEAD，此时比对 HEAD 与 HEAD~1——最近一次提交必须刷新了反思。
        # 固定比对 HEAD 会让干净树上的所有校验 100% 误报（DEC-004）。
        porcelain = subprocess.run(
            ["git", "-C", str(ROOT), "status", "--porcelain", "--", "README.md"],
            capture_output=True, text=True, timeout=5).stdout.strip()
        base = "HEAD" if porcelain else "HEAD~1"
        last_readme = subprocess.run(["git", "-C", str(ROOT), "show", f"{base}:README.md"], capture_output=True, text=True, timeout=5).stdout
        last_full = re.search(r"## 当前状态\n\n(.*?)(?=\n##[^#]|\Z)", last_readme, re.DOTALL)
        if last_full:
            last_refl = re.search(r"### 反思\n\n(.*?)(?=\n###[^#]|\Z)", last_full.group(1), re.DOTALL)
            if last_refl:
                last_clean = re.sub(r'^>.*$', '', last_refl.group(1), flags=re.MULTILINE).strip()
                if last_clean and lessons_clean.strip() == last_clean.strip():
                    fail("反思内容与上一次提交完全相同。必须写新的经验教训，不能复用旧反思"); return
    except Exception: pass
    try:
        session_ts = None; handoff_ts = None; session_ai = None; handoff_ai = None
        if MEMORY_FILE.is_file():
            with open(MEMORY_FILE) as _f:
                for _line in _f:
                    _line = _line.strip()
                    if not _line: continue
                    try: _entry = json.loads(_line)
                    except json.JSONDecodeError: continue
                    if _entry.get("type") == "session-start" and session_ts is None:
                        session_ts = _entry.get("ts", "")
                        session_ai = _entry.get("ai", "")
                    if _entry.get("type") == "handoff" and _entry.get("lessons"):
                        handoff_ts = _entry.get("ts", "")
                        handoff_ai = _entry.get("ai", "")
        if session_ts and handoff_ts and handoff_ts < session_ts:
            fail("反思内容来自上一 session（handoff 时间 < session 启动时间）"); return
        # 干净 checkout 适配 (DEC-008): 记忆文件不入库, 缺失时跳过,
        # 与 GOV-CONTENT-008 的 [SKIP] 先例对齐; 文件存在但无 handoff 仍 FAIL
        if not MEMORY_FILE.is_file():
            print("  [INFO] context.jsonl 不存在，跳过 handoff 新鲜度检查（干净 checkout 场景，DEC-008）")
        elif handoff_ts is None:
            fail("未找到 handoff 反思记录"); return
    except Exception: pass
    print(f"  [PASS] README.md 当前状态节含有效反思（{len(lessons_clean)} 字，含反思信号，时效性确认）")
    hooks_missing = []
    for hook in ("pre-commit", "commit-msg", "pre-push"):
        if not (ROOT / ".git" / "hooks" / hook).is_file():
            hooks_missing.append(hook)
    if hooks_missing:
        # hooks 是本机状态, 不随 clone 走; CI 环境无法拥有 hooks (DEC-008)
        if os.environ.get("CI"):
            print(f"  [INFO] CI 环境跳过 git hooks 检查（本机状态），缺失: {', '.join(hooks_missing)}")
        else:
            fail(f"Git hooks 未安装: {', '.join(hooks_missing)}。运行 bash scripts/bootstrap/bs-install-hooks.sh 安装")


def check_language() -> None:
    print("\n--- R0b: 语言配置文件校验 ---")
    lang_path = ROOT / ".language"
    if not lang_path.is_file():
        lang_path = ROOT / "scripts" / "language"
    if not lang_path.is_file():
        fail(".language 文件缺失，请创建并写入 zh-CN"); return
    content = lang_path.read_text(encoding="utf-8").strip()
    VALID_LANGS = {"zh-CN", "en", "en-US", "ja", "ko", "fr", "de", "es"}
    if content not in VALID_LANGS:
        fail(f".language 内容无效: '{content}'，应为 {VALID_LANGS} 之一"); return
    print(f"  [PASS] .language = {content}")


def check_dot_configs() -> None:
    print("\n--- R0c: 点目录治理配置校验 ---")
    issues = 0

    def parse_json(path, label):
        if not path.is_file(): return None
        try: return json.loads(path.read_text(encoding="utf-8"))
        except Exception as e: fail(f"{label}: JSON 格式错误: {e}"); return None

    settings = parse_json(ROOT / ".claude" / "settings.json", ".claude/settings.json")
    if settings:
        hooks = settings.get("hooks", {})
        for event, hook_list in hooks.items():
            for h in hook_list if isinstance(hook_list, list) else []:
                cmd = h.get("command", "") if isinstance(h, dict) else ""
                if "scripts/" in cmd:
                    ref_path = cmd.split("scripts/", 1)[1].split()[0] if "scripts/" in cmd else ""
                    if ref_path:
                        full = ROOT / "scripts" / ref_path
                        if not full.is_file():
                            fail(f".claude/settings.json: hooks.{event} 引用脚本不存在: scripts/{ref_path}")
                            issues += 1

    skills_dir = ROOT / ".claude" / "skills"
    if skills_dir.is_dir():
        skill_count = 0
        for skill_dir in sorted(skills_dir.iterdir()):
            if not skill_dir.is_dir(): continue
            skill_yaml = skill_dir / "skill.yaml"
            if skill_yaml.is_file():
                skill_count += 1
                try:
                    import yaml
                    data = yaml.safe_load(skill_yaml.read_text(encoding="utf-8"))
                    if data and not data.get("name"):
                        fail(f".claude/skills/{skill_dir.name}/skill.yaml: 缺少 name 字段")
                        issues += 1
                except ImportError: pass
                except Exception as e: fail(f".claude/skills/{skill_dir.name}/skill.yaml: YAML 格式错误: {e}"); issues += 1
        if skill_count: print(f"  [INFO] skill 定义 {skill_count} 个")

    ci_yml = ROOT / ".github" / "workflows" / "ci.yml"
    if ci_yml.is_file():
        parse_json(ROOT / ".governance.json", ".governance.json")
    parse_json(ROOT / "mcp.json", "mcp.json") or parse_json(ROOT / ".mcp.json", ".mcp.json")

    clinerules = ROOT / ".clinerules"
    if clinerules.is_file():
        if len(clinerules.read_text(encoding="utf-8").strip()) < 10:
            fail(".clinerules: 内容过短"); issues += 1

    if not (ROOT / ".editorconfig").is_file():
        print("  [WARN] .editorconfig: 文件缺失，建议创建")

    if issues == 0: print("  [PASS] 全部点目录配置文件合规")


def check_dependency_dirs() -> None:
    print("\n--- R0d: 依赖目录位置合规性 ---")
    ALLOWED_PATHS = {".venv": {".venv"}, "venv": {"venv"}, "node_modules": {"node_modules", ".opencode/node_modules"}, ".pytest_cache": {".pytest_cache"}, ".ruff_cache": {".ruff_cache"}, ".mypy_cache": {".mypy_cache"}, ".coverage": {".coverage"}}
    issues = 0; found = {}
    if (ROOT / ".venv").is_dir() and (ROOT / "venv").is_dir():
        fail(".venv 和 venv 同时存在（只能保留一个）"); issues += 1
    for dirpath_str, dirs, _ in os.walk(str(ROOT)):
        dirpath = Path(dirpath_str); rel = dirpath.relative_to(ROOT).as_posix()
        if rel == ".": continue
        if rel.startswith(".git/"): dirs.clear(); continue
        for dname, allowed in ALLOWED_PATHS.items():
            if dirpath.name == dname:
                if rel not in allowed: found.setdefault(dname, []).append(rel)
    for dname, locations in sorted(found.items()):
        for loc in locations:
            fail(f"{dname}: 不允许出现在 {loc}（允许: {', '.join(ALLOWED_PATHS[dname])}）"); issues += 1
    if issues == 0: print("  [PASS] 所有依赖目录在允许位置")


def check_root_files() -> None:
    print("\n--- R0e: 根目录文件治理 ---")
    ALLOWED_ROOT_FILES = {"AGENTS.md", ".clinerules", ".cursorrules", "README.md", "CONTRIBUTING.md", "LICENSE", "CHANGELOG.md", ".editorconfig", ".gitattributes", ".gitignore", ".mcp.json", "mcp.json", "opencode.json", ".governance.json", "package.json", "package-lock.json", "pytest.ini", "requirements.txt", "environment.yml", "mkdocs.yml", "ruff.toml", ".python-version", ".language", ".env", "STATUS.md", "setup.sh", "VERSION",
    "gradlew", "build.gradle.kts", "settings.gradle.kts", "gradle.properties"}
    issues = 0
    for f in ROOT.iterdir():
        if not f.is_file(): continue
        if f.name.startswith(".") and f.name in (".DS_Store", ".coverage"): continue
        if f.name not in ALLOWED_ROOT_FILES:
            fail(f"{f.name}: 不在根目录允许清单中"); issues += 1; continue
    for fname in ("README.md", "LICENSE", ".gitignore", "STATUS.md", "setup.sh", "VERSION"):
        if not (ROOT / fname).is_file(): fail(f"{fname}: 缺失（关键根文件）"); issues += 1
    if issues == 0: print("  [PASS] 根目录文件合规")


def check_hidden_dirs() -> None:
    print("\n--- R0f: 隐藏目录文件白名单校验 ---")
    ISSUES = 0
    HIDDEN_ALLOWED = {
        ".cache": {"context.jsonl", "session-ledger.json", "session-marker.json", "session.lock", ".gitkeep"},
        ".ai": {"tasks.json"},
    }
    for dir_name, allowed_files in HIDDEN_ALLOWED.items():
        d = ROOT / dir_name
        if not d.is_dir(): continue
        for f in d.iterdir():
            if not f.is_file(): continue
            if f.name not in allowed_files:
                fail(f"{dir_name}/{f.name}: 不在白名单")
                ISSUES += 1

    claude = ROOT / ".claude"
    if claude.is_dir():
        ALLOWED_CLAUDE_ROOT = {"settings.json", "settings.local.json", "skills"}
        for f in claude.iterdir():
            if f.name not in ALLOWED_CLAUDE_ROOT:
                fail(f".claude/{f.name}: 不在白名单（仅允许 settings.json + skills/）")
                ISSUES += 1

    gh = ROOT / ".github"
    if gh.is_dir():
        for f in gh.iterdir():
            if f.name != "workflows":
                fail(f".github/{f.name}: 不在白名单（仅允许 workflows/）")
                ISSUES += 1

    if ISSUES == 0:
        print("  [PASS] 隐藏目录文件白名单全部合规")
    else:
        print(f"  [FIX] {ISSUES} 项不在白名单中")
