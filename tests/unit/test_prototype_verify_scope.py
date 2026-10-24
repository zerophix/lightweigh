#!/usr/bin/env python3
"""T-106: gov-prototype-verify.py 文案匹配率的「作用域」语义。

背景（DEC-071）
---------------
旧实现把**每个目标文件**单独拿去比对**任务声明的全部 view**文案：

- 多文件任务（薄封装组件各实现一个 view）里，单文件必然只覆盖一小部分文案，
  匹配率被稀释成假 WARN。实测 T-098（4 文件 / 4 view）4 条 WARN、
  T-083 2 条、T-095 1 条。
- 单文件任务承载多个 view 时同样被稀释：T-097（1 文件 / 15 view）匹配率 13%，
  因为其中 12 个 view 是业务屏、明确不属该任务。

新语义（两条作用域规则）
------------------------
1. **聚合**：先把任务全部已存在目标文件的文本聚成一份，再做比对；
   单个文件不再单独成率 —— 修掉多文件稀释。
2. **逐 view**：聚合文本按 view 分别求匹配率，判定「是否参考过原型」时
   看是否**至少有一个 view 达标**。单个文件承载多个 view 的部分实现不再被
   非本任务 view 的分母拖死 —— 修掉单文件稀释。

阈值不放松（仍是 30%），tasks.json 的 views 数据不改；只有「分母与执行单元」
从「文件」换到「任务 + view」。

覆盖
----
- 阈值常量未变（防「放宽阈值消 WARN」）
- 聚合拼接、逐 view 求率、缺失 view 跳过
- 多文件 / 单文件两种语义
- 完全无文案落地时仍必须 WARN（守卫不能空转）
- 真实任务回归：T-097 / T-098 假 WARN 归零，T-076 既有通过结果不变

运行: python3 -m pytest tests/unit/test_prototype_verify_scope.py -q
"""
import importlib.util
import subprocess
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "governance" / "gov-prototype-verify.py"

THRESHOLD = 0.3


def _load_module():
    """按文件路径载入闭环脚本模块（脚本以 __main__ 形式也可执行，故用 importlib 直取）。"""
    spec = importlib.util.spec_from_file_location("gov_prototype_verify", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


G = _load_module()


def make_views(mapping):
    """构造与 extract_views_from_html 一致结构的 view 表。"""
    return {vid: {"phrases": list(phrases), "raw_len": 100} for vid, phrases in mapping.items()}


def write(path: Path, text: str) -> Path:
    """写文件并返回路径（父目录自动创建）。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    return path


def copy_warnings(warnings):
    """只取文案匹配率相关的 WARN，避免被 Qk* / 硬编码颜色等无关检查干扰。"""
    return [w for w in warnings if "文案" in w]


def union_ratio(code, views):
    """旧实现口径（T-106 前）：全部 view 文案并成一个分母，再拿单个文件成率。

    仅用于在用例里复现「假 WARN 的产生条件」，证明被测样本真的被稀释过。
    """
    union = set()
    for phrases in views.values():
        union.update(phrases)
    if not union:
        return 0.0
    return sum(1 for p in union if p in code) / len(union)


# ---------------------------------------------------------------------------
# 1. 阈值不可放松
# ---------------------------------------------------------------------------


def test_threshold_is_still_thirty_percent():
    """修作用域不等于放宽阈值：阈值必须仍是 30%（否则 WARN 是「调」掉的，不是「修」掉的）。"""
    assert G.COPY_MATCH_THRESHOLD == THRESHOLD


# ---------------------------------------------------------------------------
# 2. 纯函数：聚合与逐 view 求率
# ---------------------------------------------------------------------------


def test_aggregate_target_text_joins_every_file(tmp_path):
    """聚合必须包含全部目标文件文本，缺一个文件就退化成稀释。"""
    a = write(tmp_path / "A.kt", "配对超时 重新配对")
    b = write(tmp_path / "B.kt", "同步失败 局部失败")
    merged = G.aggregate_target_text([a, b])
    for token in ("配对超时", "重新配对", "同步失败", "局部失败"):
        assert token in merged


def test_view_copy_ratios_are_per_view(tmp_path):
    """逐 view 求率：每个 view 各自成率，不跨 view 混算分母。"""
    code = "配对超时 重新配对"
    views = {"v-pair": ["配对超时", "重新配对"], "v-sync": ["同步失败", "局部失败"]}
    ratios = G.view_copy_ratios(code, make_views(views), list(views))
    by_id = {r.view_id: r for r in ratios}

    assert by_id["v-pair"].hits == 2 and by_id["v-pair"].total == 2
    assert by_id["v-pair"].ratio == pytest.approx(1.0)
    assert by_id["v-sync"].hits == 0 and by_id["v-sync"].ratio == pytest.approx(0.0)


def test_view_copy_ratios_skips_view_missing_from_html():
    """声明了但原型里没有的 view 由 schema 校验报错，匹配率不得在此处静默伪造分母。"""
    ratios = G.view_copy_ratios("任意文案", make_views({"v-a": ["任意文案"]}), ["v-a", "v-ghost"])
    assert [r.view_id for r in ratios] == ["v-a"]


# ---------------------------------------------------------------------------
# 3. 多目标文件语义：先聚合，再统一比对全部 view
# ---------------------------------------------------------------------------


def test_multi_target_files_are_aggregated_not_diluted(tmp_path):
    """T-098 形态：4 个薄封装组件各实现 1 个 view，聚合后不应有任何 WARN。"""
    views = {
        "v-pull": ["下拉刷新", "刷新失败"],
        "v-swipe": ["左滑删除", "撤销删除"],
        "v-scroll": ["加载更多", "没有更多"],
        "v-search": ["搜索筛选", "无匹配结果"],
    }
    targets = [
        write(tmp_path / "QkPullRefresh.kt", "@Composable fun A() { Text(\"下拉刷新\") }"),
        write(tmp_path / "QkSwipeDelete.kt", "@Composable fun B() { Text(\"左滑删除\") }"),
        write(tmp_path / "QkInfiniteScroll.kt", "@Composable fun C() { Text(\"加载更多\") }"),
        write(tmp_path / "QkSearchBar.kt", "@Composable fun D() { Text(\"搜索筛选\") }"),
    ]
    # 旧行为复现：任何一个文件单独比对全部 view，都必然低于阈值（这就是假 WARN 的来源）
    before = G.aggregate_target_text([targets[0]])
    assert union_ratio(before, views) < THRESHOLD, "被测样本未复现稀释，用例失效"

    warnings, info = G.check_copy_scope("T-098", targets, make_views(views), list(views))
    assert copy_warnings(warnings) == [], warnings
    assert any("T-098" in line for line in info)


def test_multi_target_aggregation_does_not_hide_missing_copy(tmp_path):
    """聚合只合并分母，不豁免责任：4 个文件都空实现时仍必须 WARN。"""
    views = {"v-a": ["下拉刷新", "刷新失败"], "v-b": ["左滑删除", "撤销删除"]}
    targets = [
        write(tmp_path / "A.kt", "@Composable fun A() {}"),
        write(tmp_path / "B.kt", "@Composable fun B() {}"),
    ]
    warnings, _ = G.check_copy_scope("T-900", targets, make_views(views), list(views))
    assert len(copy_warnings(warnings)) == 1, warnings
    assert "T-900" in copy_warnings(warnings)[0]


# ---------------------------------------------------------------------------
# 4. 单目标文件语义：逐 view 比对
# ---------------------------------------------------------------------------


def test_single_target_is_matched_view_by_view(tmp_path):
    """T-097 形态：单文件只实现 15 个 view 中的 4 个（其余明确不适用），不应告警。

    旧的「并集分母」口径下该文件匹配率仅 13% -> 假 WARN；逐 view 后
    达标的 view 能把「确实参考过原型」证明出来。
    """
    views = {
        "v-pair": ["配对超时", "重新配对"],
        "v-sync": ["同步失败", "局部失败"],
        # 以下 view 属业务屏，不在本任务范围
        "v-export": ["导出成功", "分享报告"],
        "v-score": ["身体评分", "评分说明"],
        "v-anomaly": ["数据异常", "缺测提示"],
        "v-trend": ["多指标对比", "趋势对比"],
        "v-privacy": ["隐私答疑", "数据归属"],
        "v-notif": ["通知落地", "查看详情"],
    }
    code = "@Composable fun Pair() { Text(\"配对超时\"); Text(\"重新配对\") }\n" \
           "@Composable fun Sync() { Text(\"同步失败\"); Text(\"局部失败\") }"
    target = write(tmp_path / "ErrorStates.kt", code)

    # 旧口径复现：并集分母把非本任务 view 也计入 -> 低于阈值
    assert union_ratio(code, views) < THRESHOLD, "样本未复现稀释"

    warnings, info = G.check_copy_scope("T-097", [target], make_views(views), list(views))
    assert copy_warnings(warnings) == [], warnings
    assert any("T-097" in line for line in info)


def test_single_target_without_any_copy_still_warns(tmp_path):
    """单文件任务全部 view 都不达标时必须 WARN —— 逐 view 不是「随便一个短语就算过」。"""
    views = {"v-a": ["配对超时", "重新配对"], "v-b": ["同步失败"]}
    target = write(tmp_path / "Nothing.kt", "@Composable fun X() {}")
    warnings, _ = G.check_copy_scope("T-901", [target], make_views(views), list(views))
    assert len(copy_warnings(warnings)) == 1, warnings


def test_marginally_matching_view_at_threshold_passes(tmp_path):
    """阈值边界语义不变：恰好 30% 视为达标（>=），低于 30% 才算不达标。"""
    views = {"v-a": ["甲甲", "乙乙", "丙丙", "丁丁"]}  # 4 句
    # 1/4 = 25% -> 不达标
    low = write(tmp_path / "Low.kt", "甲甲")
    warnings, _ = G.check_copy_scope("T-902", [low], make_views(views), list(views))
    assert len(copy_warnings(warnings)) == 1

    # 2/4 = 50% -> 达标
    ok = write(tmp_path / "Ok.kt", "甲甲 乙乙")
    warnings, _ = G.check_copy_scope("T-903", [ok], make_views(views), list(views))
    assert copy_warnings(warnings) == []


def test_no_declared_views_produces_no_copy_warning(tmp_path):
    """未声明 view 的任务不参与文案检查（schema 已单独告警「views 为空」）。"""
    target = write(tmp_path / "X.kt", "@Composable fun X() {}")
    warnings, info = G.check_copy_scope("T-904", [target], make_views({}), [])
    assert copy_warnings(warnings) == [] and info == []


# ---------------------------------------------------------------------------
# 5. 真实任务回归：假 WARN 消失，既有通过任务不变
# ---------------------------------------------------------------------------


def run_verify(task_id: str):
    """真实任务跑 strict 校验，返回 (exit_code, stdout)。"""
    proc = subprocess.run(
        [sys.executable, str(SCRIPT), task_id, "--strict"],
        capture_output=True,
        text=True,
        cwd=str(ROOT),
    )
    return proc.returncode, proc.stdout + proc.stderr


def warn_lines(output: str):
    """提取 [WARN] 行。"""
    return [ln.strip() for ln in output.splitlines() if "[WARN]" in ln]


@pytest.mark.parametrize(
    "task_id, warn_before",
    [
        ("T-097", 1),  # 单文件 15 view：13% 假 WARN
        ("T-098", 4),  # 4 文件 4 view：5%~28% 假 WARN
    ],
)
def test_regression_false_warnings_gone(task_id, warn_before):
    """两个被稀释出假 WARN 的任务：文案类 WARN 必须归零，且任务仍通过 strict。"""
    code, output = run_verify(task_id)
    copy_warns = [w for w in warn_lines(output) if "文案" in w]
    assert code == 0, output
    assert copy_warns == [], f"{task_id} 仍有文案 WARN（修复前 {warn_before} 条）:\n{output}"


def test_t076_unchanged_single_file_full_match():
    """T-076 修复前 100% 通过，修复后必须仍是 100% 且零文案 WARN。"""
    code, output = run_verify("T-076")
    assert code == 0, output
    assert [w for w in warn_lines(output) if "文案" in w] == [], output
    assert "100%" in output, output


@pytest.mark.parametrize("task_id", ["T-083", "T-095"])
def test_previously_passing_tasks_still_pass(task_id):
    """T-083 / T-095 修复前虽有 WARN 但判定通过；作用域修复不得让它们转红。"""
    code, output = run_verify(task_id)
    assert code == 0, output
    assert "闭环验证通过" in output, output
