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

    # 002/003 已移除：README 改用标准结构，不再强制「## 当前状态」节与「### 反思」子节。
    # 实时状态见 STATUS.md（自动生成）；工程反思迁至 docs/guides/GC-engineering-reflections.md（自愿追加）。

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
    print("\n--- R0: git hooks 安装校验 ---")
    # 反思已迁至 docs/guides/GC-engineering-reflections.md（自愿追加，不再强制、不阻塞推送）。
    # README 改用标准结构，不再要求「当前状态/反思」节；实时状态见 STATUS.md。
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
    # Python/Node 依赖目录（按目录名精确匹配, 允许根及白名单位置）
    ALLOWED_PATHS = {
        ".venv": {".venv"}, "venv": {"venv"},
        "node_modules": {"node_modules", ".opencode/node_modules"},
        ".pytest_cache": {".pytest_cache"}, ".ruff_cache": {".ruff_cache"},
        ".mypy_cache": {".mypy_cache"}, ".coverage": {".coverage"},
    }
    # Gradle 缓存/IDE 目录（DEC-023）：仅允许项目根（不进入内部遍历, 避免命中缓存里的同名包目录）
    GRADLE_ROOT_ONLY = {".gradle", ".gradle-user", ".kotlin", ".idea", ".cxx"}
    # 项目根已有的合法 Gradle 产物目录, 不进入也不检测位置
    ROOT_PROD_DIRS = {"build", "gradle"}
    issues = 0; found = {}
    if (ROOT / ".venv").is_dir() and (ROOT / "venv").is_dir():
        fail(".venv 和 venv 同时存在（只能保留一个）"); issues += 1
    for dirpath_str, dirs, _ in os.walk(str(ROOT)):
        dirpath = Path(dirpath_str); rel = dirpath.relative_to(ROOT).as_posix()
        if rel.startswith(".git/"): dirs.clear(); continue
        # 当前层若直接含 Gradle/IDE 缓存目录: 仅项目根(rel==".")允许, 子目录一律违规
        for dname in GRADLE_ROOT_ONLY:
            if dname in dirs and rel != ".":
                found.setdefault(dname, []).append(f"{rel}/{dname}")
        # 不进入下载缓存/构建产物/IDE 缓存内部（避免命中内部同名包目录误报）
        dirs[:] = [d for d in dirs if d not in (GRADLE_ROOT_ONLY | ROOT_PROD_DIRS)]
        if rel == ".": continue
        for dname, allowed in ALLOWED_PATHS.items():
            if dirpath.name == dname:
                if rel not in allowed: found.setdefault(dname, []).append(rel)
    for dname, locations in sorted(found.items()):
        for loc in locations:
            fail(f"{dname}: 不允许出现在 {loc}（允许: {', '.join(ALLOWED_PATHS.get(dname, {dname}))}）"); issues += 1
    if issues == 0: print("  [PASS] 所有依赖目录在允许位置")


def check_root_files() -> None:
    print("\n--- R0e: 根目录文件治理 ---")
    ALLOWED_ROOT_FILES = {"AGENTS.md", ".clinerules", ".cursorrules", "README.md", "CONTRIBUTING.md", "LICENSE", "CHANGELOG.md", ".editorconfig", ".gitattributes", ".gitignore", ".mcp.json", "mcp.json", "opencode.json", ".governance.json", ".gitleaks.toml", "package.json", "package-lock.json", "pytest.ini", "requirements.txt", "environment.yml", "mkdocs.yml", "ruff.toml", ".python-version", ".language", ".env", "STATUS.md", "setup.sh", "VERSION",
    "gradlew", "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
    # Android Studio 自动生成（gitignore 内）
    "local.properties"}
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
        ".ai": {"tasks.json", "current-session.json", "staged-claims.json"},
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
        # workflows 必有；PR 模板 / Dependabot / CODEOWNERS 为常见 GitHub 元数据（DEC-048）
        ALLOWED_GITHUB = {
            "workflows",
            "PULL_REQUEST_TEMPLATE.md",
            "pull_request_template.md",
            "PULL_REQUEST_TEMPLATE",
            "ISSUE_TEMPLATE",
            "CODEOWNERS",
            "dependabot.yml",
            "CODE_OF_CONDUCT.md",
            "CONTRIBUTING.md",
        }
        for f in gh.iterdir():
            if f.name not in ALLOWED_GITHUB:
                fail(
                    f".github/{f.name}: 不在白名单（允许 workflows/ 与 PR 模板等；见 DEC-048）"
                )
                ISSUES += 1

    if ISSUES == 0:
        print("  [PASS] 隐藏目录文件白名单全部合规")
    else:
        print(f"  [FIX] {ISSUES} 项不在白名单中")
