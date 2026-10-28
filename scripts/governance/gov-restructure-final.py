#!/usr/bin/env python3
"""彻底重构任务：统一命名、清理重复、分配批次、修正波次"""
import json
from pathlib import Path
from collections import defaultdict

with open('.ai/tasks.json') as f:
    data = json.load(f)

tasks = data.get('tasks', [])
print(f"原始任务数: {len(tasks)}")

# ========== 1. 定义标准批次（与 wf-autopilot 波次对齐）==========
BATCHES = {
    "B-w0-foundation": {"wave": 0, "title": "基础设施/组件/状态", "lane": "gov"},
    "B-w1-auth-guide": {"wave": 1, "title": "认证/引导/基础页面", "lane": "dev"},
    "B-w2-core": {"wave": 2, "title": "核心业务功能", "lane": "dev"},
    "B-w3-integration": {"wave": 3, "title": "接入波（接线/编排）", "lane": "dev"},
    "B-w4-domain": {"wave": 4, "title": "DDD 领域重构", "lane": "dev"},
    "B-gov": {"wave": 0, "title": "治理/工具链", "lane": "gov"},
}

# ========== 2. 任务规范映射：原始 ID -> 新规范 ==========
# 格式: 原ID -> {新ID前缀, batch, type, domain, wave, lane, status, keep}

TASK_MAP = {
    # === Wave 0: 基础组件/状态 (B-w0-foundation) ===
    "ENG-031": {"batch": "B-w0-foundation", "type": "prototype-driven", "domain": "components", "wave": 0, "lane": "gov", "keep": True, "status": "done"},
    "ENG-032": {"batch": "B-w0-foundation", "type": "prototype-driven", "domain": "states", "wave": 0, "lane": "gov", "keep": True, "status": "done"},
    "G-PROTO-VERIFY": {"batch": "B-gov", "type": "governance", "domain": "gov", "wave": 0, "lane": "gov", "keep": True, "status": "done"},
    "G-DEC-72-80": {"batch": "B-gov", "type": "governance", "domain": "gov", "wave": 0, "lane": "gov", "keep": True, "status": "done"},
    "G-SHIP-EVIDENCE": {"batch": "B-gov", "type": "governance", "domain": "gov", "wave": 0, "lane": "gov", "keep": True, "status": "done"},
    "G-PYTEST-FIX": {"batch": "B-gov", "type": "governance", "domain": "gov", "wave": 0, "lane": "test", "keep": True, "status": "done"},
    "G-GOV-TESTS": {"batch": "B-gov", "type": "governance", "domain": "gov", "wave": 0, "lane": "test", "keep": True, "status": "done"},
    "G-PRE-RECEIVE": {"batch": "B-gov", "type": "governance", "domain": "gov", "wave": 0, "lane": "gov", "keep": True, "status": "done"},
    "G-FUNC-ACCEPT": {"batch": "B-gov", "type": "governance", "domain": "gov", "wave": 0, "lane": "test", "keep": True, "status": "done"},
    "G-DDD-EVIDENCE": {"batch": "B-gov", "type": "governance", "domain": "gov", "wave": 0, "lane": "gov", "keep": True, "status": "done"},
    "G-PROTO-COVERAGE": {"batch": "B-gov", "type": "governance", "domain": "gov", "wave": 0, "lane": "gov", "keep": True, "status": "done"},

    # === Wave 1: 认证/引导 (B-w1-auth-guide) ===
    "ENG-001": {"batch": "B-w1-auth-guide", "type": "prototype-driven", "domain": "auth", "wave": 1, "lane": "dev", "keep": True, "status": "done"},
    "ENG-002": {"batch": "B-w1-auth-guide", "type": "prototype-driven", "domain": "auth", "wave": 1, "lane": "dev", "keep": True, "status": "done"},
    "ENG-003": {"batch": "B-w1-auth-guide", "type": "prototype-driven", "domain": "auth", "wave": 1, "lane": "dev", "keep": True, "status": "done"},
    "ENG-004": {"batch": "B-w1-auth-guide", "type": "prototype-driven", "domain": "guide", "wave": 1, "lane": "dev", "keep": True, "status": "done"},
    "ENG-005": {"batch": "B-w1-auth-guide", "type": "prototype-driven", "domain": "guide", "wave": 1, "lane": "dev", "keep": True, "status": "done"},
    "ENG-026": {"batch": "B-w1-auth-guide", "type": "prototype-driven", "domain": "guide", "wave": 1, "lane": "dev", "keep": True, "status": "ready"},

    # === Wave 2: 核心业务 (B-w2-core) ===
    "ENG-006": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "measure", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-007": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "measure", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-008": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "measure", "wave": 2, "lane": "dev", "keep": True, "status": "ready"},
    "ENG-009": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "history", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-010": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "trend", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-011": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "trend", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-012": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "device", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-013": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "device", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-014": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "device", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-015": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "member", "wave": 2, "lane": "dev", "keep": True, "status": "done"},  # 修正: member 也在 wave 2
    "ENG-016": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "member", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-017": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "member", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-018": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "ai", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-019": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "sync", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-020": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "sync", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-021": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "sync", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-022": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "mine", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-023": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "mine", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-024": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "settings", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-025": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "settings", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-027": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "privacy", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-028": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "ai", "wave": 2, "lane": "dev", "keep": True, "status": "done"},
    "ENG-029": {"batch": "B-w2-core", "type": "prototype-driven", "domain": "ai", "wave": 2, "lane": "dev", "keep": True, "status": "done"},

    # === DDD 领域任务归入 Wave 4 (B-w4-domain) ===
    "D-BODY-COMP": {"batch": "B-w4-domain", "type": "domain-logic", "domain": "measure", "wave": 4, "lane": "dev", "keep": True, "status": "done"},
    "D-HEALTH-RISK": {"batch": "B-w4-domain", "type": "domain-logic", "domain": "measure", "wave": 4, "lane": "dev", "keep": True, "status": "done"},
    "D-BODY-SHAPE": {"batch": "B-w4-domain", "type": "domain-logic", "domain": "measure", "wave": 4, "lane": "dev", "keep": True, "status": "done"},
    "D-HEALTH-ADVICE": {"batch": "B-w4-domain", "type": "domain-logic", "domain": "ai", "wave": 4, "lane": "dev", "keep": True, "status": "done"},
    "D-MEASURE-RECORD": {"batch": "B-w4-domain", "type": "domain-logic", "domain": "measure", "wave": 4, "lane": "dev", "keep": True, "status": "done"},
    "D-HEALTH-LOG": {"batch": "B-w4-domain", "type": "domain-logic", "domain": "history", "wave": 4, "lane": "dev", "keep": True, "status": "done"},
    "D-MEMBER": {"batch": "B-w4-domain", "type": "domain-logic", "domain": "member", "wave": 4, "lane": "dev", "keep": True, "status": "done"},
    "D-HABIT": {"batch": "B-w4-domain", "type": "domain-logic", "domain": "member", "wave": 4, "lane": "dev", "keep": True, "status": "done"},
    "D-BODY-ANALYSIS": {"batch": "B-w4-domain", "type": "domain-logic", "domain": "measure", "wave": 4, "lane": "dev", "keep": True, "status": "done"},
    "D-MEASURE-SESSION": {"batch": "B-w4-domain", "type": "domain-logic", "domain": "measure", "wave": 4, "lane": "dev", "keep": True, "status": "done"},

    # === Integration 任务：检查去重 ===
    # 这些被标记为 blocked，但有些与 ENG 任务重复，需要清理
    "I-DATA-RECOVERY": {"batch": "B-w3-integration", "type": "integration", "domain": "settings", "wave": 3, "lane": "dev", "keep": True, "status": "blocked", "note": "需 DEC-081"},
    "I-PROFILE-SETUP": {"batch": "B-w3-integration", "type": "integration", "domain": "guide", "wave": 3, "lane": "dev", "keep": True, "status": "blocked", "note": "需 DEC-081"},
    "I-PERMISSION-FLOW": {"batch": "B-w3-integration", "type": "integration", "domain": "guide", "wave": 3, "lane": "dev", "keep": True, "status": "blocked"},
    "I-GUIDE-SCREEN": {"batch": "B-w3-integration", "type": "integration", "domain": "guide", "wave": 3, "lane": "dev", "keep": False, "note": "与 I-PROFILE-SETUP 重复"},  # 删除
    "I-AI-BOUNDARIES": {"batch": "B-w3-integration", "type": "integration", "domain": "ai", "wave": 3, "lane": "dev", "keep": True, "status": "blocked"},
    "I-DEVICE-RENAME": {"batch": "B-w3-integration", "type": "integration", "domain": "device", "wave": 3, "lane": "dev", "keep": True, "status": "blocked"},
    "I-MEMBER-DEVICE": {"batch": "B-w3-integration", "type": "integration", "domain": "device", "wave": 3, "lane": "dev", "keep": True, "status": "blocked"},
    "I-FIRMWARE": {"batch": "B-w3-integration", "type": "integration", "domain": "device", "wave": 3, "lane": "dev", "keep": True, "status": "blocked"},
    "I-FEEDBACK": {"batch": "B-w3-integration", "type": "integration", "domain": "sync", "wave": 3, "lane": "dev", "keep": True, "status": "blocked"},
    "I-PRIVACY-AUTH": {"batch": "B-w3-integration", "type": "integration", "domain": "privacy", "wave": 3, "lane": "dev", "keep": True, "status": "blocked"},
    "I-ACTION-PLANS": {"batch": "B-w3-integration", "type": "integration", "domain": "member", "wave": 3, "lane": "dev", "keep": True, "status": "blocked"},
    "I-RESULT-SHARE": {"batch": "B-w3-integration", "type": "integration", "domain": "measure", "wave": 3, "lane": "dev", "keep": True, "status": "blocked"},
    "I-DATA-CONFLICT": {"batch": "B-w3-integration", "type": "integration", "domain": "sync", "wave": 3, "lane": "dev", "keep": True, "status": "blocked"},
    "I-MAINTENANCE": {"batch": "B-w3-integration", "type": "integration", "domain": "settings", "wave": 3, "lane": "dev", "keep": True, "status": "blocked"},
    "I-AI-CARDS": {"batch": "B-w3-integration", "type": "integration", "domain": "ai", "wave": 3, "lane": "dev", "keep": True, "status": "blocked"},
    "I-MEASURE-HELP": {"batch": "B-w3-integration", "type": "integration", "domain": "measure", "wave": 3, "lane": "dev", "keep": True, "status": "blocked"},
    "I-ACCOUNT-LIFECYCLE": {"batch": "B-w3-integration", "type": "integration", "domain": "auth", "wave": 3, "lane": "dev", "keep": True, "status": "blocked"},
    "I-HEALTH-CONNECT": {"batch": "B-w3-integration", "type": "integration", "domain": "sync", "wave": 3, "lane": "dev", "keep": True, "status": "blocked"},
}

# ENG-030 (I-AI-CARDS duplicate) - 删除
# I-GUIDE-SCREEN - 删除（重复）

# ========== 3. 执行重构 ==========
new_tasks = []
removed = []

for task in tasks:
    tid = task["id"]
    if tid in TASK_MAP:
        mapping = TASK_MAP[tid]
        if not mapping.get("keep", True):
            removed.append(f"{tid}: {mapping.get('note', 'removed')}")
            continue
        
        # 创建新任务
        new_task = dict(task)  # 保留原有字段
        new_task["batch"] = mapping["batch"]
        new_task["type"] = mapping["type"]
        new_task["domain"] = mapping["domain"]
        new_task["wave"] = mapping["wave"]
        new_task["lane"] = mapping["lane"]
        if "status" in mapping:
            new_task["status"] = mapping["status"]
        
        # 统一 ID 命名：使用统一的 T- 前缀 + 顺序号（最后重新编号）
        new_tasks.append(new_task)
    else:
        # 未在映射中的任务（ENG-030 等）
        removed.append(f"{tid}: not in map")
        print(f"  ⚠️ 未映射: {tid}")

print(f"保留: {len(new_tasks)}, 移除: {len(removed)}")

# ========== 4. 重新编号 ID（按 batch + wave 排序）==========
batch_order = {
    "B-w0-foundation": 0,
    "B-w1-auth-guide": 1,
    "B-w2-core": 2,
    "B-w3-integration": 3,
    "B-w4-domain": 4,
    "B-gov": 5,
}

new_tasks.sort(key=lambda t: (batch_order.get(t.get("batch", ""), 99), t.get("wave", 99), t.get("domain", ""), t.get("id", "")))

# 重新分配统一 ID
for i, task in enumerate(new_tasks, 1):
    batch_prefix = task.get("batch", "B-misc").replace("B-", "").replace("-", "")
    # 简化：统一用 T- 三位数
    task["id"] = f"T-{i:03d}"

# ========== 5. 补充缺失字段 ==========
for task in new_tasks:
    # 确保 acceptance 结构
    if "acceptance" not in task or not isinstance(task.get("acceptance"), dict):
        task["acceptance"] = {
            "verify_commands": ["./gradlew :app:testDebugUnitTest"],
            "definition_of_done": ["target_files 存在且可编译", "单测通过", "local-ci PASS"],
            "forbidden": ["硬编码颜色/间距", "静默改黄金向量", "删除或放宽既有测试"]
        }
    # 确保 evidence 字段
    if "evidence" not in task:
        task["evidence"] = {}
    # 确保 priority
    if "priority" not in task:
        task["priority"] = "P1" if task.get("wave", 0) <= 2 else "P0"
    # 清理旧字段
    for old_key in ["group_id", "overlaps_with", "source", "source_ref", "prd_ref", "ac_refs", "depends_on", "progress"]:
        task.pop(old_key, None)

# ========== 6. 保存 ==========
data["version"] = 2
data["schema"] = "task-flow@v2"
data["updated_at"] = "2026-10-27T18:00:00Z"
data["tasks"] = new_tasks
data["batches"] = [{"id": k, "title": v["title"]} for k, v in BATCHES.items()]

Path(".ai/tasks.json").write_text(json.dumps(data, ensure_ascii=False, indent=2))
print(f"\n✅ 重构完成: {len(new_tasks)} 个任务")

# 统计
from collections import Counter
print(f"Status: {Counter(t['status'] for t in new_tasks)}")
print(f"Type: {Counter(t['type'] for t in new_tasks)}")
print(f"Domain: {Counter(t['domain'] for t in new_tasks)}")
print(f"Batch: {Counter(t['batch'] for t in new_tasks)}")
print(f"Lane: {Counter(t['lane'] for t in new_tasks)}")

# 列出前几个
for t in new_tasks[:10]:
    print(f"  {t['id']}: {t['status']} | {t['type']} | {t['domain']} | wave={t['wave']} | {t['batch']}")