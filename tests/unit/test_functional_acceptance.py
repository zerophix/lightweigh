#!/usr/bin/env python3
"""Functional Acceptance Test Infrastructure Verification.

验证功能级验收测试基础设施：
1. 领域层验收测试文件存在
2. 验收测试覆盖核心业务场景（黄金向量、边界值、聚合根行为）
3. 可通过 gradle 执行

运行: python3 -m pytest tests/unit/test_functional_acceptance.py -v
"""
import subprocess
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
DOMAIN_ACCEPTANCE_DIR = ROOT / "domain" / "src" / "test" / "kotlin" / "com" / "xcwk" / "scalehub" / "acceptance"


class TestFunctionalAcceptanceInfrastructure:
    """功能级验收测试基础设施测试。"""

    def test_acceptance_test_directory_exists(self):
        """验收测试目录必须存在。"""
        assert DOMAIN_ACCEPTANCE_DIR.exists(), f"验收测试目录不存在: {DOMAIN_ACCEPTANCE_DIR}"
        assert DOMAIN_ACCEPTANCE_DIR.is_dir(), f"不是目录: {DOMAIN_ACCEPTANCE_DIR}"

    def test_body_composition_accuracy_test_exists(self):
        """体成分准确性验收测试文件必须存在。"""
        test_file = DOMAIN_ACCEPTANCE_DIR / "BodyCompositionAccuracyTest.kt"
        assert test_file.exists(), f"核心验收测试文件不存在: {test_file}"
        assert test_file.is_file(), f"不是文件: {test_file}"

    def test_acceptance_test_contains_golden_vectors(self):
        """验收测试必须包含黄金向量测试用例。"""
        test_file = DOMAIN_ACCEPTANCE_DIR / "BodyCompositionAccuracyTest.kt"
        content = test_file.read_text(encoding="utf-8")

        # 验证关键黄金向量测试存在
        assert "黄金向量" in content, "缺少黄金向量测试标记"
        assert "OKOK分析_男性_30岁_175cm_70kg_500欧姆_黄金向量" in content
        assert "OKOK分析_女性_25岁_160cm_55kg_600欧姆_黄金向量" in content
        assert "通用估算路径_男性_30岁_175cm_70kg_20%体脂_500欧姆_黄金向量" in content

    def test_acceptance_test_contains_boundary_tests(self):
        """验收测试必须包含边界值测试。"""
        test_file = DOMAIN_ACCEPTANCE_DIR / "BodyCompositionAccuracyTest.kt"
        content = test_file.read_text(encoding="utf-8")

        # 验证体型分类 3x3 矩阵边界测试
        assert "体型分类_男性_9大类全覆盖_黄金向量" in content
        assert "体型分类_女性_9大类全覆盖_黄金向量" in content
        assert "体型分类_边界值_临界点正确" in content

        # 验证关键边界值
        assert "体脂率边界" in content
        assert "BMI边界" in content

    def test_acceptance_test_contains_health_risk_tests(self):
        """验收测试必须包含 7 大健康风险评估测试。"""
        test_file = DOMAIN_ACCEPTANCE_DIR / "BodyCompositionAccuracyTest.kt"
        content = test_file.read_text(encoding="utf-8")

        # 验证 7 大健康风险
        assert "肥胖症风险" in content
        assert "脂肪肝风险" in content
        assert "高血压风险" in content
        assert "糖尿病风险" in content
        assert "骨质疏松风险" in content
        assert "高血脂风险" in content
        assert "痛风风险" in content

    def test_acceptance_test_contains_aggregate_root_tests(self):
        """验收测试必须包含 MeasureRecord 聚合根行为测试。"""
        test_file = DOMAIN_ACCEPTANCE_DIR / "BodyCompositionAccuracyTest.kt"
        content = test_file.read_text(encoding="utf-8")

        # 验证聚合根业务行为测试
        assert "MeasureRecord_计算体成分_委托域服务_OKOK路径" in content
        assert "MeasureRecord_计算体成分_委托域服务_通用估算路径" in content
        assert "MeasureRecord_计算体成分_无阻抗_返回null" in content
        assert "MeasureRecord_评估健康风险_返回7项风险" in content
        assert "MeasureRecord_体型分类_返回ShapeResult" in content
        assert "MeasureRecord_综合建议_返回非空文案" in content
        assert "MeasureRecord_指标建议_异常指标返回针对性建议" in content

    def test_acceptance_test_compiles_with_gradle(self):
        """验收测试必须能通过 gradle 编译通过。"""
        # 使用 gradle 编译测试代码（不运行，只编译）
        result = subprocess.run(
            ["./gradlew", ":domain:compileTestKotlin", "--no-daemon", "-q"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            timeout=120,
        )
        assert result.returncode == 0, f"验收测试编译失败:\nSTDOUT:\n{result.stdout}\nSTDERR:\n{result.stderr}"

    def test_acceptance_test_runs_with_gradle(self):
        """验收测试必须能通过 gradle 运行通过。"""
        # 运行特定的验收测试类
        result = subprocess.run(
            [
                "./gradlew",
                ":domain:test",
                "--tests",
                "com.xcwk.scalehub.acceptance.BodyCompositionAccuracyTest",
                "--no-daemon",
                "-q",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
            timeout=180,
        )
        assert result.returncode == 0, f"验收测试运行失败:\nSTDOUT:\n{result.stdout}\nSTDERR:\n{result.stderr}"

    def test_acceptance_test_count_matches_expectation(self):
        """验收测试用例数量符合预期（至少 20 个测试方法）。"""
        test_file = DOMAIN_ACCEPTANCE_DIR / "BodyCompositionAccuracyTest.kt"
        content = test_file.read_text(encoding="utf-8")

        # 统计 @Test 注解的方法数
        import re

        test_methods = re.findall(r"@Test\s+fun\s+`[^`]+`", content)
        assert len(test_methods) >= 20, f"验收测试用例不足: 期望 >= 20, 实际 {len(test_methods)}"


class TestFunctionalAcceptanceCoverage:
    """功能级验收测试覆盖度测试。"""

    def test_covers_domain_services(self):
        """验收测试覆盖所有核心域服务。"""
        test_file = DOMAIN_ACCEPTANCE_DIR / "BodyCompositionAccuracyTest.kt"
        content = test_file.read_text(encoding="utf-8")

        # 验证覆盖的域服务
        assert "BodyCompositionService" in content
        assert "HealthRiskAssessmentService" in content
        assert "BodyShapeClassificationService" in content
        assert "HealthAdviceService" in content

    def test_covers_okok_bia_integration(self):
        """验收测试覆盖 OkokBia 集成路径。"""
        test_file = DOMAIN_ACCEPTANCE_DIR / "BodyCompositionAccuracyTest.kt"
        content = test_file.read_text(encoding="utf-8")

        assert "OkokBia.analyze" in content
        assert "analyzeOkok" in content
        assert "okok-bia-1" in content  # 测试数据标识

    def test_covers_general_estimation_path(self):
        """验收测试覆盖通用估算路径。"""
        test_file = DOMAIN_ACCEPTANCE_DIR / "BodyCompositionAccuracyTest.kt"
        content = test_file.read_text(encoding="utf-8")

        assert "estimateGeneral" in content
        assert "builtin-1" in content  # 测试数据标识


if __name__ == "__main__":
    import sys

    sys.exit(pytest.main([__file__, "-v"]))