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
from scripts.governance.modules.gov_validate_android_tests import check_all as check_android_tests

from scripts.governance.modules.gov_validate_core import errors


def main() -> int:
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

    # 摘要行恒定输出（两空格缩进匹配 gov-update-metrics.sh 的 `^  违规:` 锚点），
    # 成功时附加「全部通过」——DEC-005：输出契约对全部消费者稳定可用
    print(f"\n  违规: {len(errors)} 项")
    if errors:
        return 1
    print("全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
