#!/usr/bin/env python3
"""gov-validate.py - 治理校验编排器"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT))

from scripts.governance.modules.gov_validate_status import (
    check_status, check_readme_structure, check_language, check_dot_configs, check_dependency_dirs, check_root_files, check_hidden_dirs,
)
from scripts.governance.modules.gov_validate_structure import check_all as check_structure
from scripts.governance.modules.gov_validate_content import check_all as check_content
from scripts.governance.modules.gov_validate_protocol import check_all as check_protocol
from scripts.governance.modules.gov_validate_scripts import check_all as check_scripts
from scripts.governance.modules.gov_validate_tests import check_all as check_tests
from scripts.governance.modules.gov_validate_skills import check_all as check_skills
from scripts.governance.modules.gov_validate_tests_gov import check_all as check_tests_gov
from scripts.governance.modules.gov_validate_android_tests import check_all as check_android_tests
from scripts.governance.modules.gov_validate_prototype import check_all as check_prototype
from scripts.governance.modules.gov_validate_previews import check_all as check_previews
from scripts.governance.modules.gov_validate_docs import check_all as check_docs
from scripts.governance.modules.gov_validate_hygiene import check_all as check_hygiene

from scripts.governance.modules.gov_validate_core import errors


def run_fix() -> int:
    """--fix: 自动修复可自洽验证的 docs 项，修完立刻回跑校验。

    只修得完的（004 计数、005 时间戳），需要判断的（索引描述、真源声明、
    Notion 同步）一律不碰。修完不验证等于静默改写，因此这里必须回跑。
    """
    from scripts.governance.modules import gov_validate_docs as docs_mod

    modified = docs_mod.fix_all()

    saved = list(errors)
    errors.clear()
    # check_all 内部已通过 fail()/_warn() 打印明细，这里只统计不重打
    docs_mod.check_all()
    residual = list(errors)
    warns = list(docs_mod.warnings)
    errors.clear()
    errors.extend(saved)

    if modified:
        print(f"  已修改 {len(modified)} 个文件: {', '.join(sorted(modified))}")
    if residual:
        print(f"\n  --fix 后仍有 {len(residual)} 项 docs 违规，需人工处理")
        return 1
    print(f"\n  --fix 后 docs 校验通过（{len(warns)} 条 WARN 保留提醒）")
    return 0


def main() -> int:
    if "--fix" in sys.argv:
        return run_fix()

    check_status()
    check_readme_structure()
    check_language()
    check_dot_configs()
    check_dependency_dirs()
    check_root_files()
    check_hidden_dirs()
    check_structure()
    check_content()
    check_protocol()
    check_scripts()
    check_tests()
    check_skills()
    check_tests_gov()
    check_android_tests()
    check_prototype()
    check_previews()
    check_docs()
    check_hygiene()

    # 摘要行恒定输出（两空格缩进匹配 gov-update-metrics.sh 的 `^  违规:` 锚点），
    # 成功时附加「全部通过」——DEC-005：输出契约对全部消费者稳定可用
    print(f"\n  违规: {len(errors)} 项")
    if errors:
        return 1
    print("全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
