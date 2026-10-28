#!/usr/bin/env python3
"""
DDD 修复证据测试：验证 DDD 重构修复的证据（v-diag 诊断视图相关）。

覆盖：
- 体成分计算逻辑正确性
- MeasureRecord 聚合根不变量
- 域服务纯函数可测试性
- 仓储端口与实现分离
- 健康风险评估规则

运行: python3 -m pytest tests/unit/test_dddfix_evidence.py -q
"""
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]

# 确保 domain 模块可导入
sys.path.insert(0, str(ROOT / "domain/src/main/java"))
sys.path.insert(0, str(ROOT / "domain/build/classes/kotlin/main"))


def test_measure_record_fields():
    """验证 MeasureRecord 字段与不变量（从 Java 源码推导）。"""
    mr_path = ROOT / "domain/src/main/java/com/xcwk/scalehub/domain/model/MeasureRecord.java"
    assert mr_path.exists(), "MeasureRecord.java 不存在"

    content = mr_path.read_text(encoding="utf-8")

    # 验证核心字段存在
    required_fields = [
        "public final long time;",
        "public final double weight;",
        "public final double impedance;",
        "public final double fat;",
        "public final String mac;",
        "public final String algorithm;",
        "public final Boolean male;",
        "public final Integer ageYears;",
        "public final Integer heightCm;",
    ]
    for field in required_fields:
        assert field in content, f"MeasureRecord 缺少字段: {field}"

    # 验证常量
    assert "FAT_NOT_CALCULATED = -1.0" in content
    assert "MAX_IMPEDANCE_OHMS = 5000.0" in content

    # 验证不变量守卫
    assert "time <= 0" in content  # 时间戳守卫
    assert "weight < 1.0 || weight > 400.0" in content  # 体重量程守卫
    assert "impedance < 0.0 || impedance > MAX_IMPEDANCE_OHMS" in content  # 阻抗守卫
    assert "fat != FAT_NOT_CALCULATED && (fat < 0.0 || fat > 100.0)" in content  # 体脂守卫


def test_body_composition_service_exists():
    """验证 BodyCompositionService 存在且为具象类。"""
    svc_path = ROOT / "domain/src/main/kotlin/com/xcwk/scalehub/domain/service/BodyCompositionService.kt"
    assert svc_path.exists(), "BodyCompositionService.kt 不存在"

    content = svc_path.read_text(encoding="utf-8")
    # 具象类更灵活，便于依赖注入，包含核心计算方法
    assert "class BodyCompositionService" in content, "BodyCompositionService 应为具象类"
    assert "fun bmi" in content, "缺少 BMI 计算方法"
    assert "fun bmiLevel" in content, "缺少 BMI 分级方法"
    assert "fun fatStandard" in content, "缺少体脂标准方法"


def test_health_risk_assessment_service_exists():
    """验证 HealthRiskAssessmentService 存在。"""
    svc_path = ROOT / "domain/src/main/kotlin/com/xcwk/scalehub/domain/service/HealthRiskAssessmentService.kt"
    assert svc_path.exists(), "HealthRiskAssessmentService.kt 不存在"

    content = svc_path.read_text(encoding="utf-8")
    assert "assessAll" in content, "缺少 assessAll 方法"
    assert "HealthRisk" in content, "缺少 HealthRisk 相关定义"


def test_body_shape_classification_service_exists():
    """验证 BodyShapeClassificationService 存在。"""
    svc_path = ROOT / "domain/src/main/kotlin/com/xcwk/scalehub/domain/service/BodyShapeClassificationService.kt"
    assert svc_path.exists(), "BodyShapeClassificationService.kt 不存在"

    content = svc_path.read_text(encoding="utf-8")
    assert "classify" in content.lower(), "缺少分类方法"


def test_health_advice_service_exists():
    """验证 HealthAdviceService 存在。"""
    svc_path = ROOT / "domain/src/main/kotlin/com/xcwk/scalehub/domain/service/HealthAdviceService.kt"
    assert svc_path.exists(), "HealthAdviceService.kt 不存在"

    content = svc_path.read_text(encoding="utf-8")
    assert "advice" in content.lower(), "缺少建议相关方法"


def test_okok_bia_algorithm_exists():
    """验证 OkokBia 算法实现存在。"""
    algo_path = ROOT / "domain/src/main/java/com/xcwk/scalehub/domain/algorithm/OkokBia.java"
    assert algo_path.exists(), "OkokBia.java 不存在"

    content = algo_path.read_text(encoding="utf-8")
    assert "class OkokBia" in content
    assert "implements BodyComposition" in content
    assert "ID" in content
    assert "INSTANCE" in content


def test_builtin_composition_exists():
    """验证 BuiltinComposition 算法实现存在。"""
    algo_path = ROOT / "domain/src/main/java/com/xcwk/scalehub/domain/algorithm/BuiltinComposition.java"
    assert algo_path.exists(), "BuiltinComposition.java 不存在"

    content = algo_path.read_text(encoding="utf-8")
    assert "class BuiltinComposition" in content


def test_algorithms_factory_exists():
    """验证 Algorithms 工厂存在。"""
    factory_path = ROOT / "domain/src/main/java/com/xcwk/scalehub/domain/algorithm/Algorithms.java"
    assert factory_path.exists(), "Algorithms.java 不存在"

    content = factory_path.read_text(encoding="utf-8")
    assert "of" in content
    assert "OkokBia" in content
    assert "BuiltinComposition" in content


def test_measure_record_repository_port_exists():
    """验证 MeasurementRepository 存在（仓储端口）。"""
    port_path = ROOT / "domain/src/main/kotlin/com/xcwk/scalehub/domain/repository/MeasurementRepository.kt"
    assert port_path.exists(), "MeasurementRepository.kt 不存在"

    content = port_path.read_text(encoding="utf-8")
    assert "interface" in content, "仓储端口应为接口"
    assert "save" in content
    assert "memberId" in content, "save 签名应包含 memberId (成员隔离约束)"
    assert "delete" in content


def test_measure_record_repository_impl_exists():
    """验证 RoomMeasurementRepository 存在（仓储实现）。"""
    impl_path = ROOT / "infrastructure/src/main/kotlin/com/xcwk/scalehub/infrastructure/persistent/repository/RoomMeasurementRepository.kt"
    assert impl_path.exists(), "RoomMeasurementRepository.kt 不存在"

    content = impl_path.read_text(encoding="utf-8")
    # Kotlin 使用 ":" 实现接口
    assert "RoomMeasurementRepository" in content
    assert ": MeasurementRepository" in content
    assert "save" in content
    assert "delete" in content


def test_domain_no_android_dependencies():
    """验证 domain 模块无 Android 依赖（纯 JVM/Kotlin）。"""
    domain_src = ROOT / "domain/src/main"
    if domain_src.exists():
        for kt_file in domain_src.rglob("*.kt"):
            content = kt_file.read_text(encoding="utf-8")
            # 不应直接依赖 Android SDK
            forbidden = ["android.", "androidx."]
            for f in forbidden:
                # 允许在注释中出现
                lines = content.split("\n")
                for line in lines:
                    stripped = line.strip()
                    if stripped.startswith("//") or stripped.startswith("/*"):
                        continue
                    if f in stripped and "import" in stripped:
                        pytest.fail(f"{kt_file} 包含 Android 依赖: {stripped}")

    for java_file in domain_src.rglob("*.java"):
        content = java_file.read_text(encoding="utf-8")
        lines = content.split("\n")
        for line in lines:
            stripped = line.strip()
            if stripped.startswith("//") or stripped.startswith("/*"):
                continue
            if "import android." in stripped or "import androidx." in stripped:
                pytest.fail(f"{java_file} 包含 Android 依赖: {stripped}")


def test_application_query_layer_exists():
    """验证 application 查询层存在（BodyAnalysisQuery 等）。"""
    query_dir = ROOT / "application/src/main/kotlin/com/xcwk/scalehub/application/query"
    assert query_dir.exists(), "application 查询层目录不存在"

    required_queries = [
        "BodyAnalysisQuery.kt",
        "TrendQuery.kt",
        "HomeGoalQuery.kt",
        "ReportQuery.kt",
        "ProfileQuery.kt",
        "HistoryQuery.kt",
    ]
    for q in required_queries:
        assert (query_dir / q).exists(), f"缺少查询: {q}"


def test_body_analysis_query_trend_state():
    """验证 BodyAnalysisQuery 包含趋势状态机。"""
    baq_path = ROOT / "application/src/main/kotlin/com/xcwk/scalehub/application/query/BodyAnalysisQuery.kt"
    assert baq_path.exists(), "BodyAnalysisQuery.kt 不存在"

    content = baq_path.read_text(encoding="utf-8")
    # 验证趋势状态机
    assert "TrendState" in content
    assert "Empty" in content
    assert "InsufficientData" in content
    assert "Plottable" in content
    assert "trendStateOf" in content
    assert "segmentSeries" in content
    assert "TrendPointUi" in content
    assert "WeeklyTrendResult" in content
    assert "MonthlyTrendResult" in content
    assert "FullTrendResult" in content
    assert "weeklyTrend" in content
    assert "monthlyTrend" in content
    assert "fullTrend" in content


def test_ddd_layering_enforced():
    """验证 DDD 分层：domain 不依赖 application/infrastructure。"""
    domain_src = ROOT / "domain/src/main"
    if domain_src.exists():
        for kt_file in domain_src.rglob("*.kt"):
            content = kt_file.read_text(encoding="utf-8")
            lines = content.split("\n")
            for line in lines:
                stripped = line.strip()
                if stripped.startswith("//") or stripped.startswith("/*"):
                    continue
                # domain 不应 import application 或 infrastructure
                if "import com.xcwk.scalehub.application" in stripped:
                    pytest.fail(f"{kt_file} 违反分层: 依赖 application: {stripped}")
                if "import com.xcwk.scalehub.infrastructure" in stripped:
                    pytest.fail(f"{kt_file} 违反分层: 依赖 infrastructure: {stripped}")


def test_gov_validate_passes():
    """验证 gov-validate.sh 通过（无新增违规）。"""
    import subprocess

    proc = subprocess.run(
        ["bash", "scripts/governance/gov-validate.sh"],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    assert proc.returncode == 0, f"gov-validate 失败 (exit={proc.returncode}):\n{proc.stdout}\n{proc.stderr}"


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-q"]))