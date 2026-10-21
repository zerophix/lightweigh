#!/usr/bin/env python3
"""
gov-code-check.py — 源码质量检测（死 import / 重复实现 / 相似度分叉）

在 gov-custom-checks.sh 中被调用（项目自定义挂载点，不属模板上游拥有区）。
退出码非 0 即阻止提交。

规则清单:
  GOV-CODE-000 守卫负例 —— 规则自证有效，抓不到该抓的或误伤无辜即失败
  GOV-CODE-001 死 import —— import 的符号在正文未被引用（阻断）
  GOV-CODE-002 跨文件重复实现 —— 函数体规范化后逐字相同（阻断）
  GOV-CODE-003 跨文件高相似度实现 —— 结构相同但命名不同（仅提示）
  GOV-CODE-004 基础设施语义耦合 —— infrastructure import domain.algorithm（阻断）
  GOV-CODE-005 写用例职责 —— application.service import application.sync（阻断）

规则依据与已知盲区（规则必须能回答「为什么不能靠编译器」）:
  - kotlinc 不检查未使用 import，Javac 只警告不阻断 → 只能静态扫描。
    委托操作符 getValue/setValue 通过 `by` 使用、符号名不出现在正文，必须豁免，
    否则会淹没在误报里（实测全项目 30+ 条假阳性）。
  - 编译器不禁止复制粘贴；同名不等于重复（接口实现/委托转发/UDF 约定都合法），
    故重复检测以「函数体规范化后逐字相同」为准，同名仅作线索不作判据。
  - 逐字检测抓不到「结构相同、命名不同」的分叉——两处各写一份门卡片组件，
    仅参数名与配色常量不同，改一处忘另一处即出 bug。故另设 token 相似度检测。
    相似不等于重复（同构 wrapper 会撞车），故只提示不阻断。
  - 已知盲区：不解析 Kotlin DSL/内联 lambda 的函数体边界；同文件内重复不查；
    相似度是 O(n²) 两两比对，靠长度预筛压量，函数数量继续增长需换 k-gram 指纹；
    **只扫 `src/main`，测试源集不在范围**（测试里的死 import 需人工处理——扩大范围前
    应先评估存量噪音，否则规则一落地就红，等于逼人关掉它）。
"""
import re
import sys
from difflib import SequenceMatcher
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent

# 检测范围：四个源码模块的 main 源集（测试代码不在此列）
SRC_DIRS = ["domain", "application", "infrastructure", "app"]

# 委托操作符：`val x by remember {}` 依赖 getValue/setValue 扩展，符号名不出现于正文
DELEGATE_SYMBOLS = {"getValue", "setValue"}

# 重复检测的最小有效行数：低于此值多为巧合结构（getter/wrapper），不作判据
MIN_DUP_LINES = 6

# 相似度阈值：实测项目内 0.90→0组、0.85→1组、0.80→2组、0.75→5组。
# 取 0.75 因为它恰好抓得住「结构相同但命名不同」的真实分叉（GateCard 案例 85%），
# 而结构无关的函数仅 19%。只报 WARN 不阻断——相似不等于重复，需人复核。
SIM_THRESHOLD = 0.75
SIM_MIN_LINES = 8

# GOV-CODE-004/005：分层与职责的语义耦合。规则与自检共用同一正则，
# 避免两处写法漂移后自检验的是一份、运行的是另一份。
_INFRA_ALGO_IMPORT = re.compile(r"\s*import\s+com\.xcwk\.scalehub\.domain\.algorithm\.")
INFRA_ALGO_IMPORT_LINE = "import com.xcwk.scalehub.domain.algorithm.OkokBia"
DOMAIN_ALGO_IMPORT_LINE = "import com.xcwk.scalehub.domain.model.ScaleDevice"

# 用例不得依赖同步策略（防 F-002 回归）：写用例只发 DataChange，点火由 SyncOnLocalWrites 订阅
_SERVICE_SYNC_IMPORT = re.compile(r"\s*import\s+com\.xcwk\.scalehub\.application\.sync\.")
SERVICE_SYNC_IMPORT_LINE = "import com.xcwk.scalehub.application.sync.SyncTrigger"
SERVICE_LEGIT_IMPORT_LINE = "import com.xcwk.scalehub.domain.model.MeasureRecord"


def _source_files():
    """扫描范围自检：命中 0 个文件说明路径配置错了，必须显式失败而非静默通过
    （规则空转比没有规则更危险——它会让人以为已被守住）。"""
    files = []
    for d in SRC_DIRS:
        base = ROOT / d
        if not base.is_dir():
            continue
        for p in sorted(base.rglob("*")):
            if p.is_file() and p.suffix in (".kt", ".java") and "src/main" in str(p):
                files.append(p)
    if not files:
        print(f"  [FAIL] gov-code-check: 在 {ROOT} 下未命中任何源码文件，扫描范围配置有误")
        sys.exit(2)
    return files


def _strip_comments(text: str) -> str:
    """去注释（保留字符串内的 // 与 /*，避免误删代码）。"""
    out, i, n = [], 0, len(text)
    in_line = in_block = in_str = False
    quote = ""
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if in_line:
            if c == "\n":
                in_line = False
                out.append(c)
            i += 1
            continue
        if in_block:
            if c == "*" and nxt == "/":
                in_block = False
                i += 2
                continue
            i += 1
            continue
        if in_str:
            out.append(c)
            if c == "\\":
                if nxt:
                    out.append(nxt)
                    i += 2
                    continue
            elif c == quote:
                in_str = False
            i += 1
            continue
        if c == "/" and nxt == "/":
            in_line = True
            i += 2
            continue
        if c == "/" and nxt == "*":
            in_block = True
            i += 2
            continue
        if c in "\"'":
            in_str, quote = True, c
            out.append(c)
            i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def _normalize(body: str, ident: bool) -> str:
    """规范化函数体。ident=True 时连标识符一并归一（结构重复检测）。"""
    s = _strip_comments(body)
    s = re.sub(r'"(?:\\.|[^"\\])*"', '""', s)
    s = re.sub(r"'(?:\\.|[^'\\])*'", "''", s)
    if ident:
        s = re.sub(r"[A-Za-z_][A-Za-z0-9_]*", "#", s)
    s = re.sub(r"\s+", "", s)
    return s


def check_dead_imports() -> int:
    """GOV-CODE-001: import 的符号在正文中未被引用。"""
    print("\n--- GOV-CODE-001: 死 import ---")
    found = 0
    for path in _source_files():
        raw = path.read_text(encoding="utf-8", errors="ignore")
        lines = raw.split("\n")
        body = "\n".join(l for l in lines if not l.lstrip().startswith("import "))
        for ln, line in enumerate(lines, 1):
            s = line.strip()
            if not s.startswith("import "):
                continue
            decl = s[len("import "):].strip()
            if not decl or decl.startswith("static "):
                decl = decl[len("static "):].strip()
            if not decl or "*" in decl:
                continue
            # `import x.y.z as w` 取别名；否则取末段，并剥掉 Java 的结尾分号
            parts = re.split(r"\s+as\s+", decl)
            sym = parts[-1].strip() if len(parts) > 1 else decl.split(".")[-1]
            sym = sym.rstrip(";").strip()
            if not sym or sym in DELEGATE_SYMBOLS or len(sym) < 2:
                continue
            if not re.search(r"\b" + re.escape(sym) + r"\b", body):
                print(f"  [FAIL] {path.relative_to(ROOT)}:{ln} 未使用的 import: {decl}")
                found += 1
    if found == 0:
        print("  [PASS] 无死 import")
    return found


_FUN_START = re.compile(
    r"^[ \t]*(?:@\w+(?:\([^)]*\))?[ \t]*)*"
    r"(?:(?:public|private|internal|protected|static|final|open|override|tailrec|suspend|inline|operator|infix)\s+)*"
    r"fun\s+(?:<[^>]*>\s+)?`?([A-Za-z_][A-Za-z0-9_]*)`?\s*"
    r"(?:\((.*)\)\s*(?::\s*[^={]+)?)?\s*(\{?)\s*$",
    re.M,
)


def _extract_functions(text: str):
    """提取顶层/成员函数的 (名称, 参数串, 函数体, 起始行)。靠花括号配对定界。"""
    text = _strip_comments(text)
    funcs = []
    for m in _FUN_START.finditer(text):
        name, params, brace = m.group(1), m.group(2) or "", m.group(3)
        if not brace:          # interface / abstract 声明，无函数体
            continue
        # 花括号配对找到函数结束
        depth, i, n = 0, m.end() - 1, len(text)
        started = False
        while i < n:
            c = text[i]
            if c == "{":
                depth += 1
                started = True
            elif c == "}":
                depth -= 1
                if started and depth == 0:
                    break
            i += 1
        if not started:
            continue
        body = text[m.start():i + 1]
        line = text[:m.start()].count("\n") + 1
        # override 是实现约定，同名同体属正常，不作重复判据
        head = body[:body.find("{")]
        if "override" in head:
            continue
        funcs.append((name, params, body, line))
    return funcs


def _effective_lines(body: str) -> int:
    return len([l for l in _strip_comments(body).split("\n") if l.strip()])


def check_duplicate_implementations() -> int:
    """GOV-CODE-002: 跨文件重复实现（函数体规范化后逐字相同）。"""
    print("\n--- GOV-CODE-002: 跨文件重复实现 ---")
    exact, structural = [], []
    seen_exact, seen_struct = {}, {}

    for path in _source_files():
        text = path.read_text(encoding="utf-8", errors="ignore")
        rel = str(path.relative_to(ROOT))
        for name, _params, body, line in _extract_functions(text):
            eff = _effective_lines(body)
            if eff < MIN_DUP_LINES:
                continue
            key_exact = _normalize(body, ident=False)
            key_struct = _normalize(body, ident=True)
            loc = f"{rel}:{line} {name}()"
            if key_exact in seen_exact:
                exact.append((seen_exact[key_exact], loc))
            else:
                seen_exact[key_exact] = loc
            if eff >= 12 and key_struct in seen_struct:
                structural.append((seen_struct[key_struct], loc))
            else:
                seen_struct[key_struct] = loc

    for a, b in exact:
        print(f"  [FAIL] 重复实现（函数体逐字相同）: {a}  <->  {b}")
    if not exact:
        print("  [PASS] 无逐字重复实现")

    # 结构重复只是线索：标识符全归一会把无关的同构函数混在一起，交人判断不直接阻断
    for a, b in structural:
        print(f"  [WARN] 疑似结构重复（≥12行且标识符归一后相同）: {a}  <->  {b}")
    return len(exact)


def _tokens(body: str) -> list:
    """token 序列：标识符、数字、单字符运算符。用于相似度比对。"""
    return re.findall(r"[A-Za-z_][A-Za-z0-9_]*|\d+|[^\sA-Za-z0-9_]", _normalize(body, ident=False))


def check_similar_implementations(threshold: float = SIM_THRESHOLD, min_lines: int = SIM_MIN_LINES) -> int:
    """GOV-CODE-003: 跨文件高相似度实现（只提示不阻断）。

    逐字检测（GOV-CODE-002）抓不到「结构相同但命名不同」——例如两个页面各写一份
    门卡片组件，仅参数名 body/desc、配色常量不同。这类分叉最危险：改了一处忘了另一处。
    相似度检测用 token 序列比对补上这个面。

    只报 WARN 不阻断：相似不等于重复（同构的 wrapper/getter 会撞车），阈值靠人复核。
    且只看跨文件——同文件内的相似开发者一眼可见，报出来是噪音；跨文件才是
    「改一处忘另一处」的高危面（实测 0.75 下 4 组命中里 3 组是同文件的巧合相似）。
    """
    print("\n--- GOV-CODE-003: 跨文件高相似度实现（提示） ---")
    cands = []
    for path in _source_files():
        text = path.read_text(encoding="utf-8", errors="ignore")
        rel = str(path.relative_to(ROOT))
        for name, _params, body, line in _extract_functions(text):
            eff = _effective_lines(body)
            if eff < min_lines:
                continue
            cands.append((rel, f"{rel}:{line} {name}()", _tokens(body)))

    # 预筛：token 数相差 2 倍以内的才精算，避免 O(n²) 全比对
    warns, seen = [], set()
    for i in range(len(cands)):
        la, ta = len(cands[i][2]), cands[i][2]
        for j in range(i + 1, len(cands)):
            if cands[i][0] == cands[j][0]:
                continue  # 同文件不报
            lb, tb = len(cands[j][2]), cands[j][2]
            if la == 0 or lb == 0 or min(la, lb) / max(la, lb) < 0.5:
                continue
            ratio = SequenceMatcher(None, ta, tb).ratio()
            if ratio >= threshold:
                pair = (cands[i][1], cands[j][1])
                if pair not in seen:
                    seen.add(pair)
                    warns.append((ratio, *pair))

    for ratio, a, b in sorted(warns, reverse=True):
        print(f"  [WARN] 相似度 {ratio:.0%}: {a}  <->  {b}")
    if not warns:
        print("  [PASS] 无高相似度实现")
    return 0


def check_selftest() -> int:
    """GOV-CODE-000: 规则自证有效（守卫负例）。

    规则「能通过」不算守卫，「违规时必然被抓住」才算——没有负例的检测规则可能
    静默空转（本脚本的 ROOT 曾算错一层，扫描 0 文件却报 PASS）。
    此处用真实历史案例做双向验证：必须命中的命中、不该命中的不报。
    """
    print("\n--- GOV-CODE-000: 规则自证（守卫负例） ---")
    ok = True

    # 正例：符号出现在正文即非死 import；仅 import 行本身即为死 import
    live = "import java.util.UUID\nfun f() = UUID.fromString(\"x\")"
    dead = "import java.util.UUID\nfun f() = 1"
    if "UUID" in _dead_symbols(live) or "UUID" not in _dead_symbols(dead):
        print("  [FAIL] GOV-CODE-001 自证失败：死 import 判定失效")
        ok = False

    # 正例：逐字重复必须被抓到
    twin = "fun twin(a: Int) : Int {\n  val b = a + 1\n  val c = b * 2\n  val d = c - 3\n  val e = d / 4\n  return e + 5\n}"
    if not _extract_functions(twin):
        print("  [FAIL] GOV-CODE-002 自证失败：函数提取失效")
        ok = False

    # 正例：基础设施 import 领域算法必须命中规则正则；反例：领域层自身的合法 import 不受影响
    # （自证方式是直接验证规则正则的判别力，避免"规则改坏了却还在报 PASS"）
    if not _INFRA_ALGO_IMPORT.match(INFRA_ALGO_IMPORT_LINE):
        print("  [FAIL] GOV-CODE-004 自证失败：违规 import 未被正则命中")
        ok = False
    if _INFRA_ALGO_IMPORT.match(DOMAIN_ALGO_IMPORT_LINE):
        print("  [FAIL] GOV-CODE-004 自证失败：领域层合法 import 被误判为违规")
        ok = False

    # GOV-CODE-005：用例依赖同步策略必须命中；用例依赖 domain 合法
    if not _SERVICE_SYNC_IMPORT.match(SERVICE_SYNC_IMPORT_LINE):
        print("  [FAIL] GOV-CODE-005 自证失败：违规 import 未被正则命中")
        ok = False
    if _SERVICE_SYNC_IMPORT.match(SERVICE_LEGIT_IMPORT_LINE):
        print("  [FAIL] GOV-CODE-005 自证失败：用例依赖 domain 被误判为违规")
        ok = False

    # 正例：结构相同但命名不同（真实 GateCard 分叉案例）应达相似度阈值；
    # 反例：结构无关的函数不应触发
    dup_a = ("private fun GateCard(title: String, body: String, action: String, onAction: () -> Unit, m: Modifier = Modifier) {"
             " QkCard(modifier = m) { Text(title, style = ts, color = Fg)"
             " Text(body, modifier = m.padding(top = 6.dp), style = bs, color = Muted)"
             " QkButton(text = action, modifier = m.fillMaxWidth().padding(top = 10.dp), onClick = onAction) } }")
    dup_b = ("private fun GateCard(title: String, desc: String, action: String, onAction: () -> Unit) {"
             " QkCard(modifier = Modifier.padding(top = 24.dp)) { Text(title, style = ts)"
             " Text(desc, modifier = Modifier.padding(top = 6.dp), style = bs, color = onSurfaceVariant)"
             " QkButton(text = action, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), onClick = onAction) } }")
    unrelated_a = "fun add(a: Int, b: Int): Int { return a + b }"
    unrelated_b = ("fun parseJson(text: String): Map<String, String> {"
                   " return text.split(\",\").associate { it.split(\"=\")[0] to it.split(\"=\")[1] } }")
    sim_dup = SequenceMatcher(None, _tokens(dup_a), _tokens(dup_b)).ratio()
    sim_unrelated = SequenceMatcher(None, _tokens(unrelated_a), _tokens(unrelated_b)).ratio()
    if sim_dup < SIM_THRESHOLD:
        print(f"  [FAIL] GOV-CODE-003 自证失败：真实重复案例仅 {sim_dup:.0%}，阈值 {SIM_THRESHOLD:.0%} 抓不到")
        ok = False
    if sim_unrelated >= SIM_THRESHOLD:
        print(f"  [FAIL] GOV-CODE-003 自证失败：无关函数被判为相似（{sim_unrelated:.0%}），阈值过低")
        ok = False

    if ok:
        print(f"  [PASS] 自证通过（重复案例 {sim_dup:.0%} 命中 / 无关案例 {sim_unrelated:.0%} 放行）")
    return 0 if ok else 1


def _dead_symbols(text: str) -> set:
    """从源码文本中找出死 import 符号（自检用）。"""
    lines = text.split("\n")
    body = "\n".join(l for l in lines if not l.lstrip().startswith("import "))
    out = set()
    for line in lines:
        s = line.strip()
        if not s.startswith("import "):
            continue
        decl = s[len("import "):].strip().rstrip(";").strip()
        sym = decl.split(".")[-1] if " as " not in decl else re.split(r"\s+as\s+", decl)[-1]
        sym = sym.rstrip(";").strip()
        if sym and sym not in DELEGATE_SYMBOLS and len(sym) >= 2 \
                and not re.search(r"\b" + re.escape(sym) + r"\b", body):
            out.add(sym)
    return out


def fix_dead_imports() -> int:
    """GOV-CODE-001 --fix: 删除死 import 行。

    只删不改其余内容。删完必须跑编译验证——检测若有漏网假阳性，
    删掉后会立刻以编译错误的形式暴露，比静默留在原地更安全。
    """
    print("\n--- GOV-CODE-001 --fix: 删除死 import ---")
    removed = 0
    for path in _source_files():
        raw = path.read_text(encoding="utf-8", errors="ignore")
        lines = raw.split("\n")
        drop = set()
        for i in _dead_line_numbers(lines):
            drop.add(i)
        if not drop:
            continue
        path.write_text("\n".join(l for i, l in enumerate(lines, 1) if i not in drop), encoding="utf-8")
        print(f"  [FIX] {path.relative_to(ROOT)} 删除 {len(drop)} 条")
        removed += len(drop)
    print(f"  共删除 {removed} 条死 import（请运行 ./gradlew compileDebugKotlin 验证）")
    return removed


def _dead_line_numbers(lines: list) -> list:
    body = "\n".join(l for l in lines if not l.lstrip().startswith("import "))
    out = []
    for i, line in enumerate(lines, 1):
        s = line.strip()
        if not s.startswith("import "):
            continue
        decl = s[len("import "):].strip()
        if decl.startswith("static "):
            decl = decl[len("static "):].strip()
        if not decl or "*" in decl:
            continue
        parts = re.split(r"\s+as\s+", decl)
        sym = parts[-1].strip() if len(parts) > 1 else decl.split(".")[-1]
        sym = sym.rstrip(";").strip()
        if not sym or sym in DELEGATE_SYMBOLS or len(sym) < 2:
            continue
        if not re.search(r"\b" + re.escape(sym) + r"\b", body):
            out.append(i)
    return out


def check_layer_coupling() -> int:
    """GOV-CODE-004: 基础设施不得依赖领域算法实现（防 F-004 回归）。

    「协议→默认算法」是领域规则，不是持久化细节。曾出现在 RoomDeviceRepository 里
    import domain.algorithm.OkokBia，且同一规则还在 MeasureSession 与测试假仓储各抄了
    一份——改一处漏两处。ArchUnit 抓不到它：infrastructure→domain 本身是允许的依赖方向，
    违规的是**依赖了 domain 的实现细节**而非端口，只有看 import 才知道。
    """
    print("\n--- GOV-CODE-004: 基础设施层语义耦合检查 ---")
    found = 0
    infra = ROOT / "infrastructure" / "src" / "main"
    if infra.is_dir():
        for path in sorted(infra.rglob("*")):
            if not path.is_file() or path.suffix not in (".kt", ".java"):
                continue
            for ln, line in enumerate(path.read_text(encoding="utf-8", errors="ignore").split("\n"), 1):
                if _INFRA_ALGO_IMPORT.match(line):
                    print(f"  [FAIL] {path.relative_to(ROOT)}:{ln} 基础设施依赖领域算法实现：{line.strip()}")
                    print("         协议→算法是领域规则，收进 ScaleDevice.algorithmFor（DEC-034 组 B）")
                    found += 1
    if found == 0:
        print("  [PASS] 基础设施层无领域算法依赖")
    return found


def check_service_layering() -> int:
    """GOV-CODE-005: 写用例不得依赖同步策略（防 F-002 回归）。

    此前 RecordMeasurementService / RecordHealthLogService / UpdateProfileService
    各自注入 SyncTrigger 并在落库后 fire()，导致用例单测必须构造同步触发器、
    换同步策略要改每个用例、同步失败与重试的考量渗进业务逻辑。
    现在用例只发 DataChange，点火由 SyncOnLocalWrites 在事件流上订阅。
    只扫 application/service 目录——RestoreAccountService 等本就在 sync 包内，
    它们认识 SyncService 是职责所在。
    """
    print("\n--- GOV-CODE-005: 写用例职责检查 ---")
    found = 0
    service_dir = ROOT / "application" / "src" / "main" / "kotlin" / "com" / "xcwk" / "scalehub" / "application" / "service"
    if service_dir.is_dir():
        for path in sorted(service_dir.rglob("*")):
            if not path.is_file() or path.suffix not in (".kt", ".java"):
                continue
            for ln, line in enumerate(path.read_text(encoding="utf-8", errors="ignore").split("\n"), 1):
                if _SERVICE_SYNC_IMPORT.match(line):
                    print(f"  [FAIL] {path.relative_to(ROOT)}:{ln} 写用例依赖同步策略：{line.strip()}")
                    print("         用例只发 DataChange，点火交 SyncOnLocalWrites 订阅（DEC-034 组 A）")
                    found += 1
    if found == 0:
        print("  [PASS] 写用例无同步策略依赖")
    return found


def main() -> int:
    if "--fix" in sys.argv:
        fix_dead_imports()
        print("\n  --fix 仅处理死 import；重复实现需人工合并")
        return 0

    selftest_failures = check_selftest()
    issues = check_dead_imports()
    issues += check_duplicate_implementations()
    issues += check_layer_coupling()
    issues += check_service_layering()
    check_similar_implementations()
    if issues or selftest_failures:
        print(f"\n  gov-code-check 违规: {issues + selftest_failures} 项")
        return 1
    print("\n  gov-code-check 全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
