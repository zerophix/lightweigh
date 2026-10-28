#!/usr/bin/env python3
"""
功能验收测试：针对最终补全交互原型（26-final-gaps.html）的验收测试。

覆盖：
- v-sdk-list: 第三方 SDK 清单
- v-pull-refresh: 下拉刷新
- v-swipe-delete: 滑动删除
- v-infinite-scroll: 无限滚动
- v-search-filter: 搜索筛选

运行: python3 -m pytest tests/unit/test_functional_acceptance.py -q
"""
import json
import subprocess
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]


def run(cmd, cwd=None):
    """执行命令并返回 CompletedProcess。"""
    return subprocess.run(
        cmd if isinstance(cmd, list) else ["bash", "-c", cmd],
        cwd=cwd or ROOT,
        capture_output=True,
        text=True,
    )


def test_prototype_views_exist():
    """验证 26-final-gaps.html 中声明的 6 个 view 都存在。"""
    proto_path = ROOT / "design/prototypes/interactive/26-final-gaps.html"
    assert proto_path.exists(), "原型文件不存在"

    content = proto_path.read_text(encoding="utf-8")
    expected_views = [
        "v-hub",
        "v-sdk-list",
        "v-pull-refresh",
        "v-swipe-delete",
        "v-infinite-scroll",
        "v-search-filter",
    ]

    for view in expected_views:
        assert f'id="{view}"' in content, f"原型缺少 view: {view}"


def test_sdk_list_component_exists():
    """验证 SdkListScreen.kt 存在并包含关键导出。"""
    sdk_file = ROOT / "app/src/main/kotlin/com/xcwk/scalehub/feature/settings/SdkListScreen.kt"
    assert sdk_file.exists(), "SdkListScreen.kt 不存在"

    content = sdk_file.read_text(encoding="utf-8")
    # 验证关键组件/函数存在
    assert "@Composable" in content, "缺少 @Composable"
    assert "SdkListScreen" in content, "缺少 SdkListScreen 组件"


def test_pull_refresh_component_exists():
    """验证 QkPullRefresh.kt 存在。"""
    pr_file = ROOT / "app/src/main/kotlin/com/xcwk/scalehub/ui/components/QkPullRefresh.kt"
    assert pr_file.exists(), "QkPullRefresh.kt 不存在"

    content = pr_file.read_text(encoding="utf-8")
    assert "@Composable" in content
    assert "QkPullRefresh" in content


def test_swipe_delete_component_exists():
    """验证 QkSwipeDelete.kt 存在。"""
    sd_file = ROOT / "app/src/main/kotlin/com/xcwk/scalehub/ui/components/QkSwipeDelete.kt"
    assert sd_file.exists(), "QkSwipeDelete.kt 不存在"

    content = sd_file.read_text(encoding="utf-8")
    assert "@Composable" in content
    assert "QkSwipeDelete" in content


def test_infinite_scroll_component_exists():
    """验证 QkInfiniteScroll.kt 存在。"""
    is_file = ROOT / "app/src/main/kotlin/com/xcwk/scalehub/ui/components/QkInfiniteScroll.kt"
    assert is_file.exists(), "QkInfiniteScroll.kt 不存在"

    content = is_file.read_text(encoding="utf-8")
    assert "@Composable" in content
    assert "QkInfiniteScroll" in content


def test_sdk_list_items_present():
    """验证 SDK 清单包含必需的 4 项 SDK。"""
    proto_path = ROOT / "design/prototypes/interactive/26-final-gaps.html"
    content = proto_path.read_text(encoding="utf-8")

    required_sdks = [
        "微信 OpenSDK",
        "极光推送 JPush",
        "阿里云 OSS",
        "Glide",
    ]

    for sdk in required_sdks:
        assert sdk in content, f"SDK 清单缺少: {sdk}"


def test_interaction_flow_links():
    """验证交互流程链接完整：v-hub -> 各子 view -> 返回 v-hub。"""
    proto_path = ROOT / "design/prototypes/interactive/26-final-gaps.html"
    content = proto_path.read_text(encoding="utf-8")

    # 验证每个子 view 都有返回 v-hub 的链接
    sub_views = ["v-sdk-list", "v-pull-refresh", "v-swipe-delete", "v-infinite-scroll", "v-search-filter"]
    for view in sub_views:
        # 在对应 view 中查找 data-goto="v-hub"
        view_start = content.find(f'id="{view}"')
        assert view_start >= 0, f"找不到 view: {view}"
        view_end = content.find('id="v-', view_start + 1)
        if view_end < 0:
            view_end = len(content)
        view_content = content[view_start:view_end]
        assert 'data-goto="v-hub"' in view_content, f"{view} 缺少返回 v-hub 的链接"


def test_gov_validate_passes():
    """验证 gov-validate.sh 通过（无新增违规）。"""
    proc = run(["bash", "scripts/governance/gov-validate.sh"])
    assert proc.returncode == 0, f"gov-validate 失败:\n{proc.stdout}\n{proc.stderr}"
    assert "violations" not in proc.stdout.lower() or "0 violations" in proc.stdout.lower()


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-q"]))