#!/usr/bin/env python3
"""任务重构脚本：将扁平的 95 个任务重构为 Layer 1 (产品) + Layer 2 (工程) 双层结构"""
import json
import re
from pathlib import Path
from typing import Dict, List, Any, Optional
from dataclasses import dataclass, asdict
from datetime import datetime
from collections import defaultdict

# ========== 原始任务加载 ==========
with open('.ai/tasks.json', 'r') as f:
    ORIGINAL = json.load(f)

ORIGINAL_TASKS = {t['id']: t for t in ORIGINAL['tasks']}

# ========== 核心映射：基于 Notion PRD/原型的 9 大域 ==========

# 9 个 Epic (对应 Notion PRD 9 大模块)
EPICS = [
    {
        "id": "E-AUTH",
        "title": "账号与建档",
        "prd_ref": "notion:PRD-账号与建档",
        "prototype_files": ["01-login.html", "14-account-lifecycle.html", "19-onboarding-guide.html", "21-account-recovery.html"],
        "priority": "P0",
        "milestone": "M-MVP",
        "description": "登录/注册/找回/注销/账号生命周期/启动引导/新手三部曲/资料完善"
    },
    {
        "id": "E-MEASURE",
        "title": "核心测量与报告",
        "prd_ref": "notion:PRD-测量与报告",
        "prototype_files": ["03-measure.html", "12-measure-full.html", "16-measure-result.html"],
        "priority": "P0",
        "milestone": "M-MVP",
        "description": "测量状态机/结果分享导出/AI报告/医生报告"
    },
    {
        "id": "E-HISTORY",
        "title": "历史趋势与指标",
        "prd_ref": "notion:PRD-历史与趋势",
        "prototype_files": ["05-history-trend.html", "17-metric-details.html"],
        "priority": "P0",
        "milestone": "M-MVP",
        "description": "历史列表/详情编辑/趋势自定义/6项指标详情/记录对比"
    },
    {
        "id": "E-DEVICE",
        "title": "设备管理",
        "prd_ref": "notion:PRD-设备管理",
        "prototype_files": ["04-device.html", "18-device-setup.html"],
        "priority": "P0",
        "milestone": "M-MVP",
        "description": "搜索/配对/连接/固件/重命名/成员关联/多设备切换"
    },
    {
        "id": "E-MEMBER",
        "title": "家庭成员与计划",
        "prd_ref": "notion:PRD-目标与提醒",
        "prototype_files": ["13-members-plans.html", "23-family-invite.html", "20-ai-members.html"],
        "priority": "P1",
        "milestone": "M-MVP",
        "description": "成员档案/行动计划/提醒/家庭邀请/AI周总结/成员权限/儿童模式"
    },
    {
        "id": "E-SYNC",
        "title": "同步网络与帮助",
        "prd_ref": "notion:PRD-弱网与离线, PRD-帮助与反馈",
        "prototype_files": ["08-sync-network-help.html", "22-data-conflict.html"],
        "priority": "P1",
        "milestone": "M-MVP",
        "description": "同步中心/网络服务状态/冲突解决/帮助FAQ/反馈/诊断"
    },
    {
        "id": "E-MINE",
        "title": "个人中心与设置",
        "prd_ref": "notion:PRD-个人中心与设置",
        "prototype_files": ["07-mine-settings.html", "24-remaining-gaps.html", "26-final-gaps.html"],
        "priority": "P1",
        "milestone": "M-MVP",
        "description": "关于/隐私/注销/账号安全/SDK清单/数据恢复/维护模式/语言外观/单位设置"
    },
    {
        "id": "E-PRIVACY",
        "title": "隐私授权",
        "prd_ref": "notion:PRD-隐私与首次同意",
        "prototype_files": ["02-onboarding-permission.html", "10-privacy-authorization.html"],
        "priority": "P0",
        "milestone": "M-MVP",
        "description": "启动权限/隐私授权五态/数据删除规则"
    },
    {
        "id": "E-AI",
        "title": "AI 智能健康",
        "prd_ref": "notion:PRD-智能健康",
        "prototype_files": ["06-ai.html", "20-ai-members.html", "25-ai-boundaries.html"],
        "priority": "P1",
        "milestone": "M-MVP",
        "description": "AI对话/报告/状态机/周总结/边界降级/成员权限/结构化卡片"
    },
    # 平台能力域
    {
        "id": "E-PLATFORM",
        "title": "平台能力 (组件/状态/基建)",
        "prd_ref": "notion:DES-Figma与HTML分工",
        "prototype_files": ["09-components-demo.html", "15-states-errors.html", "26-final-gaps.html"],
        "priority": "P0",
        "milestone": "M-MVP",
        "description": "通用组件/16错误态/交互模式/Token体系"
    }
]

# Feature 定义 (每个 Epic 下的功能模块)
FEATURES = {
    "E-AUTH": [
        {"id": "F-AUTH-LOGIN", "title": "登录注册核心", "prototype_file": "01-login.html", "views": ["v-phone", "v-verify", "v-password", "v-email", "v-wechat", "v-guest", "v-guest-done"]},
        {"id": "F-AUTH-RECOVERY", "title": "密码找回/账号恢复", "prototype_file": "21-account-recovery.html", "views": ["v-choose", "v-phone-verify", "v-email-verify", "v-set-password", "v-success", "v-done"]},
        {"id": "F-AUTH-LIFECYCLE", "title": "账号生命周期", "prototype_file": "14-account-lifecycle.html", "views": ["v-forceupdate", "v-minver", "v-privacy-lock", "v-codeerr", "v-expired", "v-multidevice", "v-healthinfo"]},
        {"id": "F-GUIDE-ONBOARD", "title": "启动引导/权限/新手三部曲", "prototype_file": "02-onboarding-permission.html", "views": ["v-splash", "v-terms", "v-permissions", "v-granted", "v-denied"]},
        {"id": "F-GUIDE-PROFILE", "title": "登录后资料完善", "prototype_file": "19-onboarding-guide.html", "views": ["v-intro", "v-goal", "v-prepare", "v-connected", "v-done", "v-skip", "v-onboard-age", "v-onboard-height"]},
    ],
    "E-MEASURE": [
        {"id": "F-MEASURE-CORE", "title": "测量核心状态机", "prototype_file": "12-measure-full.html", "views": ["v-check", "v-prep", "v-stable", "v-calc", "v-left", "v-invalid", "v-savefail", "v-success"]},
        {"id": "F-MEASURE-RESULT", "title": "测量结果/分享/导出/AI报告", "prototype_file": "16-measure-result.html", "views": ["v-result", "v-ai-report", "v-share", "v-export", "v-export-done"]},
        {"id": "F-MEASURE-BASIC", "title": "测量基础流程", "prototype_file": "03-measure.html", "views": ["v-measuring", "v-result", "v-ai-report"]},
    ],
    "E-HISTORY": [
        {"id": "F-HISTORY-LIST", "title": "历史列表/详情/编辑", "prototype_file": "05-history-trend.html", "views": ["v-history", "v-detail", "v-edit", "v-compare"]},
        {"id": "F-TREND", "title": "趋势/自定义范围/扩展指标", "prototype_file": "05-history-trend.html", "views": ["v-trend"]},
        {"id": "F-METRIC-DETAIL", "title": "6项指标详情/内脏脂肪", "prototype_file": "17-metric-details.html", "views": ["v-weight", "v-bmi", "v-muscle", "v-water", "v-metabolism", "v-bone", "v-visceral", "v-list"]},
    ],
    "E-DEVICE": [
        {"id": "F-DEVICE-PAIR", "title": "设备搜索/配对/连接", "prototype_file": "04-device.html", "views": ["v-search", "v-bt-permission", "v-confirm-device", "v-connecting", "v-connected", "v-pairing", "v-pair-success", "v-pair-fail"]},
        {"id": "F-DEVICE-DETAIL", "title": "设备详情/设置/重命名/成员关联", "prototype_file": "04-device.html", "views": ["v-settings", "v-rename-device", "v-member-device"]},
        {"id": "F-DEVICE-FIRMWARE", "title": "固件管理", "prototype_file": "18-device-setup.html", "views": ["v-firmware-check", "v-firmware-update", "v-firmware-latest"]},
    ],
    "E-MEMBER": [
        {"id": "F-MEMBER-PROFILE", "title": "成员档案/编辑/空态", "prototype_file": "13-members-plans.html", "views": ["v-members", "v-edit-member", "v-savefail", "v-member-norecord", "v-member-nodevice", "v-profile"]},
        {"id": "F-MEMBER-PLAN", "title": "行动计划/提醒/通知权限", "prototype_file": "13-members-plans.html", "views": ["v-plans", "v-edit-plan", "v-edit-task", "v-plan-empty", "v-remind", "v-notif-denied"]},
        {"id": "F-MEMBER-INVITE", "title": "家庭邀请/权限管理", "prototype_file": "23-family-invite.html", "views": ["v-invite", "v-invite-done", "v-accept", "v-accept-confirm", "v-accept-done", "v-revoke", "v-multi-device", "v-multi-done"]},
        {"id": "F-AI-WEEKLY", "title": "AI周总结/成员权限详情", "prototype_file": "20-ai-members.html", "views": ["v-weekly", "v-member-detail"]},
    ],
    "E-SYNC": [
        {"id": "F-SYNC-CENTER", "title": "同步中心/网络/服务状态", "prototype_file": "08-sync-network-help.html", "views": ["v-sync", "v-network", "v-service", "v-offline", "v-offline-done", "v-offline-switch"]},
        {"id": "F-SYNC-CONFLICT", "title": "数据冲突解决", "prototype_file": "22-data-conflict.html", "views": ["v-sync-conflict", "v-sync-done"]},
        {"id": "F-SYNC-HELP", "title": "帮助中心/FAQ/反馈/诊断", "prototype_file": "08-sync-network-help.html", "views": ["v-help", "v-faq", "v-feedback", "v-feedback-submit", "v-feedback-success", "v-feedback-fail", "v-diag"]},
    ],
    "E-MINE": [
        {"id": "F-MINE-CLOSURE", "title": "账号注销流程", "prototype_file": "07-mine-settings.html", "views": ["v-closure-apply", "v-closure-verify", "v-closure-processing", "v-closure-result"]},
        {"id": "F-MINE-ABOUT", "title": "关于/隐私详情/账号安全", "prototype_file": "07-mine-settings.html", "views": ["v-about", "v-privacy", "v-account"]},
        {"id": "F-SETTINGS-ADVANCED", "title": "SDK清单/数据恢复/维护/语言外观/单位", "prototype_file": "24-remaining-gaps.html", "views": ["v-sdk-list", "v-recovery", "v-maintenance", "v-language", "v-appearance", "v-recovery-done"]},
        {"id": "F-SETTINGS-UNIT", "title": "单位设置/通用设置", "prototype_file": "26-final-gaps.html", "views": ["v-settings", "v-privacy"]},
    ],
    "E-PRIVACY": [
        {"id": "F-PRIVACY-STARTUP", "title": "启动权限引导", "prototype_file": "02-onboarding-permission.html", "views": ["v-splash", "v-terms", "v-permissions", "v-granted", "v-denied"]},
        {"id": "F-PRIVACY-AUTH", "title": "隐私授权五态/数据删除规则", "prototype_file": "10-privacy-authorization.html", "views": ["v-auth", "v-granted", "v-unauth", "v-consult", "v-rules"]},
    ],
    "E-AI": [
        {"id": "F-AI-CORE", "title": "AI状态机/对话/报告", "prototype_file": "06-ai.html", "views": ["v-chat", "v-report", "v-generating", "v-failed", "v-unauth", "v-plan", "v-auth"]},
        {"id": "F-AI-BOUNDARY", "title": "AI边界/降级/未授权", "prototype_file": "25-ai-boundaries.html", "views": ["v-ai-unavailable", "v-report-expired", "v-reanalyzing", "v-report-done", "v-unauth-consult", "v-member-unauth"]},
        {"id": "F-AI-CARDS", "title": "AI结构化卡片", "prototype_file": "20-ai-members.html", "views": []},  # 无对应原型view
    ],
    "E-PLATFORM": [
        {"id": "F-COMPONENTS", "title": "通用组件/交互模式", "prototype_file": "09-components-demo.html", "views": ["v-demo", "v-skeleton", "v-longpress", "v-pull-refresh", "v-swipe-delete", "v-infinite-scroll", "v-search-filter"]},
        {"id": "F-STATES", "title": "16错误/空/边界态", "prototype_file": "15-states-errors.html", "views": ["v-rec-edit", "v-export", "v-score", "v-anomaly", "v-multimetric", "v-pair-timeout", "v-report-stale", "v-sync-fail", "v-fb-fail", "v-connect-guide", "v-troubleshoot", "v-privacy-qa", "v-notif-landing", "v-fullscreen-load", "v-longpress"]},
    ]
}

# ========== 原始任务 → 新结构映射 ==========

# 原始任务 ID 到新结构的映射
TASK_MAPPING = {
    # E-AUTH
    "T-044": {"epic": "E-AUTH", "feature": "F-AUTH-LOGIN", "type": "prototype-driven", "status": "done"},
    "T-072": {"epic": "E-AUTH", "feature": "F-AUTH-LOGIN", "type": "prototype-driven", "status": "done"},
    "T-073": {"epic": "E-AUTH", "feature": "F-AUTH-RECOVERY", "type": "prototype-driven", "status": "done"},
    "T-074": {"epic": "E-AUTH", "feature": "F-AUTH-LIFECYCLE", "type": "prototype-driven", "status": "done"},
    "T-075": {"epic": "E-AUTH", "feature": "F-GUIDE-ONBOARD", "type": "prototype-driven", "status": "done"},
    "T-076": {"epic": "E-AUTH", "feature": "F-GUIDE-PROFILE", "type": "prototype-driven", "status": "done"},
    "T-100": {"epic": "E-AUTH", "feature": "F-GUIDE-PROFILE", "type": "prototype-driven", "status": "done", "merged_into": "T-076"},
    "T-125": {"epic": "E-AUTH", "feature": "F-AUTH-RECOVERY", "type": "prototype-driven", "status": "merged", "merged_into": "T-073"},
    "T-117": {"epic": "E-AUTH", "feature": "F-AUTH-LOGIN", "type": "prototype-driven", "status": "done", "merged_into": "T-072"},
    
    # E-MEASURE
    "T-027": {"epic": "E-MEASURE", "feature": "F-MEASURE-CORE", "type": "domain-logic", "status": "done"},
    "T-052": {"epic": "E-MEASURE", "feature": "F-MEASURE-CORE", "type": "prototype-driven", "status": "merged", "merged_into": "T-077"},
    "T-066": {"epic": "E-MEASURE", "feature": "F-MEASURE-CORE", "type": "prototype-driven", "status": "merged", "merged_into": "T-095"},
    "T-069": {"epic": "E-MEASURE", "feature": "F-MEASURE-CORE", "type": "prototype-driven", "status": "merged", "merged_into": "T-077"},
    "T-077": {"epic": "E-MEASURE", "feature": "F-MEASURE-CORE", "type": "prototype-driven", "status": "done"},
    "T-078": {"epic": "E-MEASURE", "feature": "F-MEASURE-RESULT", "type": "prototype-driven", "status": "done"},
    "T-061": {"epic": "E-MEASURE", "feature": "F-MEASURE-RESULT", "type": "prototype-driven", "status": "merged", "merged_into": "T-078"},
    "T-118": {"epic": "E-MEASURE", "feature": "F-MEASURE-BASIC", "type": "prototype-driven", "status": "pending", "merged_into": "T-077"},
    "T-095": {"epic": "E-MEASURE", "feature": "F-MEASURE-CORE", "type": "prototype-driven", "status": "done"},
    
    # E-HISTORY
    "T-049": {"epic": "E-HISTORY", "feature": "F-HISTORY-LIST", "type": "prototype-driven", "status": "done"},
    "T-081": {"epic": "E-HISTORY", "feature": "F-HISTORY-LIST", "type": "prototype-driven", "status": "done"},
    "T-048": {"epic": "E-HISTORY", "feature": "F-TREND", "type": "prototype-driven", "status": "done"},
    "T-055": {"epic": "E-HISTORY", "feature": "F-METRIC-DETAIL", "type": "prototype-driven", "status": "merged", "merged_into": "T-120"},
    "T-120": {"epic": "E-HISTORY", "feature": "F-HISTORY-LIST", "type": "prototype-driven", "status": "pending"},
    "T-124": {"epic": "E-HISTORY", "feature": "F-METRIC-DETAIL", "type": "prototype-driven", "status": "pending", "merged_into": "T-120"},
    
    # E-DEVICE
    "T-053": {"epic": "E-DEVICE", "feature": "F-DEVICE-DETAIL", "type": "prototype-driven", "status": "merged", "merged_into": "T-105"},
    "T-058": {"epic": "E-DEVICE", "feature": "F-DEVICE-PAIR", "type": "prototype-driven", "status": "merged", "merged_into": "T-103"},
    "T-060": {"epic": "E-DEVICE", "feature": "F-DEVICE-PAIR", "type": "prototype-driven", "status": "merged", "merged_into": "T-103"},
    "T-062": {"epic": "E-DEVICE", "feature": "F-DEVICE-PAIR", "type": "domain-logic", "status": "merged", "merged_into": "T-103"},
    "T-079": {"epic": "E-DEVICE", "feature": "F-DEVICE-FIRMWARE", "type": "prototype-driven", "status": "done"},
    "T-080": {"epic": "E-DEVICE", "feature": "F-DEVICE-DETAIL", "type": "prototype-driven", "status": "done"},
    "T-103": {"epic": "E-DEVICE", "feature": "F-DEVICE-PAIR", "type": "prototype-driven", "status": "done"},
    "T-104": {"epic": "E-DEVICE", "feature": "F-DEVICE-PAIR", "type": "prototype-driven", "status": "done"},
    "T-105": {"epic": "E-DEVICE", "feature": "F-DEVICE-DETAIL", "type": "prototype-driven", "status": "done"},
    "T-119": {"epic": "E-DEVICE", "feature": "F-DEVICE-PAIR", "type": "prototype-driven", "status": "pending", "merged_into": "T-103"},
    
    # E-MEMBER
    "T-043": {"epic": "E-MEMBER", "feature": "F-MEMBER-PLAN", "type": "prototype-driven", "status": "done"},
    "T-054": {"epic": "E-MEMBER", "feature": "F-MEMBER-PROFILE", "type": "prototype-driven", "status": "merged", "merged_into": "T-086"},
    "T-068": {"epic": "E-MEMBER", "feature": "F-MEMBER-PLAN", "type": "prototype-driven", "status": "merged", "merged_into": "T-087"},
    "T-071": {"epic": "E-MEMBER", "feature": "F-MEMBER-PROFILE", "type": "domain-logic", "status": "merged", "merged_into": "T-086"},
    "T-086": {"epic": "E-MEMBER", "feature": "F-MEMBER-PROFILE", "type": "prototype-driven", "status": "done"},
    "T-087": {"epic": "E-MEMBER", "feature": "F-MEMBER-PLAN", "type": "prototype-driven", "status": "done"},
    "T-088": {"epic": "E-MEMBER", "feature": "F-MEMBER-INVITE", "type": "prototype-driven", "status": "done"},
    "T-084": {"epic": "E-MEMBER", "feature": "F-AI-WEEKLY", "type": "prototype-driven", "status": "done"},
    "T-122": {"epic": "E-MEMBER", "feature": "F-MEMBER-PROFILE", "type": "prototype-driven", "status": "pending", "merged_into": "T-086"},
    "T-129": {"epic": "E-MEMBER", "feature": "F-MEMBER-PROFILE", "type": "prototype-driven", "status": "pending", "merged_into": "T-086"},
    
    # E-SYNC
    "T-057": {"epic": "E-SYNC", "feature": "F-SYNC-CONFLICT", "type": "prototype-driven", "status": "done"},
    "T-059": {"epic": "E-SYNC", "feature": "F-SYNC-CENTER", "type": "domain-logic", "status": "done"},
    "T-093": {"epic": "E-SYNC", "feature": "F-SYNC-HELP", "type": "prototype-driven", "status": "done"},
    "T-067": {"epic": "E-SYNC", "feature": "F-SYNC-HELP", "type": "prototype-driven", "status": "merged", "merged_into": "T-093"},
    "T-102": {"epic": "E-SYNC", "feature": "F-SYNC-CENTER", "type": "prototype-driven", "status": "done"},
    "T-123": {"epic": "E-SYNC", "feature": "F-SYNC-CENTER", "type": "prototype-driven", "status": "pending", "merged_into": "T-102"},
    "T-126": {"epic": "E-SYNC", "feature": "F-SYNC-CONFLICT", "type": "prototype-driven", "status": "pending", "merged_into": "T-057"},
    
    # E-MINE
    "T-042": {"epic": "E-MINE", "feature": "F-SETTINGS-UNIT", "type": "prototype-driven", "status": "done"},
    "T-050": {"epic": "E-MINE", "feature": "F-SETTINGS-ADVANCED", "type": "prototype-driven", "status": "merged", "merged_into": "T-092"},
    "T-089": {"epic": "E-MINE", "feature": "F-MINE-CLOSURE", "type": "prototype-driven", "status": "done"},
    "T-090": {"epic": "E-MINE", "feature": "F-MINE-ABOUT", "type": "prototype-driven", "status": "done"},
    "T-092": {"epic": "E-MINE", "feature": "F-SETTINGS-ADVANCED", "type": "prototype-driven", "status": "done"},
    "T-101": {"epic": "E-MINE", "feature": "F-MINE-ABOUT", "type": "prototype-driven", "status": "done"},
    "T-130": {"epic": "E-MINE", "feature": "F-SETTINGS-ADVANCED", "type": "prototype-driven", "status": "pending", "merged_into": "T-092"},
    
    # E-PRIVACY
    "T-096": {"epic": "E-PRIVACY", "feature": "F-PRIVACY-AUTH", "type": "prototype-driven", "status": "done"},
    
    # E-AI
    "T-047": {"epic": "E-AI", "feature": "F-AI-CARDS", "type": "domain-logic", "status": "done"},
    "T-064": {"epic": "E-AI", "feature": "F-AI-CORE", "type": "domain-logic", "status": "merged", "merged_into": "T-084"},
    "T-083": {"epic": "E-AI", "feature": "F-AI-CORE", "type": "prototype-driven", "status": "done"},
    "T-084": {"epic": "E-AI", "feature": "F-AI-WEEKLY", "type": "prototype-driven", "status": "done"},
    "T-085": {"epic": "E-AI", "feature": "F-AI-BOUNDARY", "type": "prototype-driven", "status": "done"},
    "T-121": {"epic": "E-AI", "feature": "F-AI-CORE", "type": "prototype-driven", "status": "pending", "merged_into": "T-083"},
    
    # E-PLATFORM (基础设施/组件/状态)
    "T-024": {"epic": "E-PLATFORM", "feature": "F-STATES", "type": "governance", "status": "done"},  # 调试工具归平台
    "T-025": {"epic": "E-PLATFORM", "feature": "F-STATES", "type": "governance", "status": "done"},
    "T-063": {"epic": "E-PLATFORM", "feature": "F-SETTINGS-ADVANCED", "type": "prototype-driven", "status": "done"},  # 数据恢复
    "T-070": {"epic": "E-PLATFORM", "feature": "F-STATES", "type": "domain-logic", "status": "done"},
    "T-097": {"epic": "E-PLATFORM", "feature": "F-STATES", "type": "prototype-driven", "status": "done"},
    "T-098": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "prototype-driven", "status": "done"},
    "T-099": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "prototype-driven", "status": "done"},
    "T-128": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "prototype-driven", "status": "pending", "merged_into": "T-098"},
    "T-131": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "prototype-driven", "status": "pending", "merged_into": "T-098"},
    
    # 治理/工具链
    "T-106": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "governance", "status": "done"},
    "T-107": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "governance", "status": "done"},
    "T-108": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "governance", "status": "done"},
    "T-109": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "governance", "status": "done"},
    "T-110": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "governance", "status": "done"},
    "T-111": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "governance", "status": "done"},
    
    # DDD 重构
    "T-112": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "domain-logic", "status": "done"},
    "T-113": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "domain-logic", "status": "done"},
    "T-114": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "domain-logic", "status": "done"},
    "T-115": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "domain-logic", "status": "done"},
    "T-116": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "governance", "status": "done"},
    "T-136": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "domain-logic", "status": "pending"},
    "T-137": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "governance", "status": "pending"},
    "T-138": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "governance", "status": "pending"},
    
    # 接入波
    "T-132": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "integration", "status": "blocked"},
    "T-133": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "integration", "status": "blocked"},
    "T-134": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "integration", "status": "blocked"},
    "T-135": {"epic": "E-PLATFORM", "feature": "F-COMPONENTS", "type": "integration", "status": "blocked"},
}

# ========== 生成 Layer 1 产品任务 ==========

def generate_layer1():
    """生成 Layer 1: Epics + Features + Stories"""
    
    layer1 = {
        "version": 2,
        "schema": "task-flow@v2",
        "synced_at": datetime.now().isoformat(),
        "product_tasks": {
            "epics": [],
            "features": [],
            "stories": []
        }
    }
    
    # Epics
    for epic in EPICS:
        # 计算包含的 Feature IDs
        feature_ids = [f["id"] for f in FEATURES.get(epic["id"], [])]
        layer1["product_tasks"]["epics"].append({
            "id": epic["id"],
            "title": epic["title"],
            "prd_ref": epic["prd_ref"],
            "prototype_files": epic["prototype_files"],
            "priority": epic["priority"],
            "milestone": epic["milestone"],
            "description": epic["description"],
            "features": feature_ids,
            "status": "in_progress"
        })
    
    # Features
    for epic_id, features in FEATURES.items():
        for feat in features:
            # 生成 Story IDs
            story_ids = []
            for view in feat["views"]:
                story_id = f"S-{feat['id']}-{view.replace('v-', '')}"
                story_ids.append(story_id)
            
            layer1["product_tasks"]["features"].append({
                "id": feat["id"],
                "epic_id": epic_id,
                "title": feat["title"],
                "prototype_file": feat["prototype_file"],
                "prototype_views": feat["views"],
                "status": "ready" if feat["views"] else "draft",
                "stories": story_ids
            })
    
    # Stories (每个 View 一个 Story)
    for epic_id, features in FEATURES.items():
        for feat in features:
            for view in feat["views"]:
                story_id = f"S-{feat['id']}-{view.replace('v-', '')}"
                # 查找对应的原始任务获取验收文案
                acceptance_text = ""
                ac_refs = []
                for orig_id, mapping in TASK_MAPPING.items():
                    if mapping.get("feature") == feat["id"]:
                        orig = ORIGINAL_TASKS.get(orig_id)
                        if orig and "prototype_ref" in orig:
                            proto_views = orig["prototype_ref"].get("views", [])
                            if view in proto_views:
                                acc = orig.get("acceptance", {})
                                acceptance_text = acc.get("then", [""])[0] if acc.get("then") else ""
                                ac_refs = orig.get("ac_refs", [])
                                break
                
                layer1["product_tasks"]["stories"].append({
                    "id": story_id,
                    "feature_id": feat["id"],
                    "title": f"{feat['title']} - {view}",
                    "prototype_view": view,
                    "prototype_file": feat["prototype_file"],
                    "acceptance_text": acceptance_text[:500] if acceptance_text else f"实现 {view} 视图，文案与原型一致",
                    "ac_refs": ac_refs or [f"AC-{feat['id']}-{view}"],
                    "status": "ready",
                    "story_points": 3
                })
    
    return layer1

# ========== 生成 Layer 2 工程任务 ==========

def generate_layer2():
    """生成 Layer 2: 干净的工程任务"""
    
    tasks = []
    task_id_counter = 0
    
    def gen_id(prefix="T"):
        nonlocal task_id_counter
        task_id_counter += 1
        return f"{prefix}-{task_id_counter:03d}"
    
    # 为每个 Feature 创建工程任务
    for epic_id, features in FEATURES.items():
        for feat in features:
            domain = infer_domain(feat["id"])
            wave = infer_wave(feat["prototype_file"])
            lane = infer_lane(domain)
            
            # 主任务：prototype-driven (每个 Feature 一个主任务，或每个 View 一个)
            # 策略：每个 Feature 一个主任务，target_files 包含所有 View 对应文件
            target_files = []
            for view in feat["views"]:
                files = infer_target_files(view, feat["prototype_file"])
                target_files.extend(files)
            
            # 去重
            target_files = list(dict.fromkeys(target_files))
            
            # 找到对应的原始任务作为证据来源
            source_tasks = [tid for tid, m in TASK_MAPPING.items() if m.get("feature") == feat["id"]]
            primary_source = source_tasks[0] if source_tasks else None
            
            task = {
                "id": gen_id("ENG"),
                "type": "prototype-driven" if feat["views"] else "integration",
                "parent_feature": feat["id"],
                "parent_epic": epic_id,
                "domain": domain,
                "wave": wave,
                "lane": lane,
                "target_files": target_files,
                "acceptance": {
                    "verify_commands": [
                        f"python3 scripts/governance/gov-prototype-verify.py {{task_id}} --strict",
                        "./gradlew :app:testDebugUnitTest"
                    ],
                    "definition_of_done": [
                        "target_files 存在且可编译",
                        "视图文案与原型一致 (gov-prototype-verify --strict 通过)",
                        "单测通过",
                        "local-ci PASS"
                    ],
                    "forbidden": [
                        "硬编码颜色/间距 (必须用 QkToken)",
                        "静默改黄金向量/Room schema",
                        "删除或放宽既有测试"
                    ],
                    "prototype_file": feat["prototype_file"],
                    "prototype_views": feat["views"]
                },
                "status": "ready" if feat["views"] else "draft",
                "priority": "P0" if epic_id in ["E-AUTH", "E-MEASURE", "E-HISTORY", "E-DEVICE", "E-PRIVACY"] else "P1",
                "source_task": primary_source,
                "evidence": {}
            }
            
            # 如果有对应的 done 原始任务，标记为 done 并带上证据
            if primary_source:
                orig = ORIGINAL_TASKS.get(primary_source)
                if orig and orig.get("status") in ["completed", "done"]:
                    task["status"] = "done"
                    task["evidence"] = {
                        "source_task": primary_source,
                        "merged_from": [tid for tid in source_tasks if tid != primary_source]
                    }
            
            tasks.append(task)
    
    # 额外添加：集成任务、领域逻辑任务、治理任务、DDD任务、接入波任务
    extra_tasks = generate_extra_tasks(gen_id)
    tasks.extend(extra_tasks)
    
    return {
        "version": 2,
        "schema": "task-flow@v2",
        "updated_at": datetime.now().isoformat(),
        "tasks": tasks
    }

def infer_domain(feature_id: str) -> str:
    """推断所属域"""
    mapping = {
        "F-AUTH-LOGIN": "auth", "F-AUTH-RECOVERY": "auth", "F-AUTH-LIFECYCLE": "auth",
        "F-GUIDE-ONBOARD": "guide", "F-GUIDE-PROFILE": "guide",
        "F-MEASURE-CORE": "measure", "F-MEASURE-RESULT": "measure", "F-MEASURE-BASIC": "measure",
        "F-HISTORY-LIST": "history", "F-TREND": "trend", "F-METRIC-DETAIL": "trend",
        "F-DEVICE-PAIR": "device", "F-DEVICE-DETAIL": "device", "F-DEVICE-FIRMWARE": "device",
        "F-MEMBER-PROFILE": "member", "F-MEMBER-PLAN": "member", "F-MEMBER-INVITE": "member", "F-AI-WEEKLY": "ai",
        "F-SYNC-CENTER": "sync", "F-SYNC-CONFLICT": "sync", "F-SYNC-HELP": "sync",
        "F-MINE-CLOSURE": "mine", "F-MINE-ABOUT": "mine", "F-SETTINGS-ADVANCED": "settings", "F-SETTINGS-UNIT": "settings",
        "F-PRIVACY-STARTUP": "guide", "F-PRIVACY-AUTH": "privacy",
        "F-AI-CORE": "ai", "F-AI-BOUNDARY": "ai", "F-AI-CARDS": "ai",
        "F-COMPONENTS": "components", "F-STATES": "states"
    }
    return mapping.get(feature_id, "misc")

def infer_wave(prototype_file: str) -> int:
    """推断波次"""
    if not prototype_file:
        return 1
    if any(x in prototype_file for x in ["01-", "02-"]):
        return 1
    elif any(x in prototype_file for x in ["03-", "04-", "12-", "16-", "18-"]):
        return 2
    elif any(x in prototype_file for x in ["05-", "17-"]):
        return 2
    elif any(x in prototype_file for x in ["06-", "10-", "20-", "25-"]):
        return 2
    elif any(x in prototype_file for x in ["07-", "24-", "26-"]):
        return 2
    elif any(x in prototype_file for x in ["08-", "22-"]):
        return 2
    elif any(x in prototype_file for x in ["09-", "15-", "26-"]):
        return 0
    return 1

def infer_lane(domain: str) -> str:
    """推断泳道"""
    if domain in ["components", "states", "gov"]:
        return "gov"
    return "dev"

def infer_target_files(view_id: str, prototype_file: str) -> List[str]:
    """推断目标文件 (复用 transform.py 逻辑)"""
    VIEW_TO_FILE = {
        "v-phone": "feature/auth/PhoneLoginScreen.kt",
        "v-verify": "feature/auth/VerifyCodeScreen.kt",
        "v-password": "feature/auth/PasswordLoginScreen.kt",
        "v-email": "feature/auth/EmailLoginScreen.kt",
        "v-wechat": "feature/auth/WeChatLoginScreen.kt",
        "v-guest": "feature/auth/GuestModeScreen.kt",
        "v-guest-done": "feature/auth/GuestDoneScreen.kt",
        "v-reset": "feature/auth/PasswordResetScreen.kt",
        "v-choose": "feature/auth/AccountRecoveryChooseScreen.kt",
        "v-phone-verify": "feature/auth/PhoneVerifyScreen.kt",
        "v-email-verify": "feature/auth/EmailVerifyScreen.kt",
        "v-set-password": "feature/auth/SetPasswordScreen.kt",
        "v-success": "feature/auth/SuccessScreen.kt",
        "v-done": "feature/auth/DoneScreen.kt",
        "v-splash": "feature/guide/SplashScreen.kt",
        "v-terms": "feature/guide/TermsScreen.kt",
        "v-permissions": "feature/guide/PermissionFlowScreen.kt",
        "v-granted": "feature/guide/GrantedScreen.kt",
        "v-denied": "feature/guide/DeniedScreen.kt",
        "v-intro": "feature/guide/IntroScreen.kt",
        "v-goal": "feature/guide/GoalScreen.kt",
        "v-prepare": "feature/guide/PrepareScreen.kt",
        "v-connected": "feature/guide/ConnectedScreen.kt",
        "v-skip": "feature/guide/SkipScreen.kt",
        "v-onboard-age": "feature/guide/ProfileSetupScreen.kt",
        "v-onboard-height": "feature/guide/ProfileSetupScreen.kt",
        "v-check": "feature/measure/MeasureCheckScreen.kt",
        "v-prep": "feature/measure/MeasurePrepScreen.kt",
        "v-stable": "feature/measure/MeasureStableScreen.kt",
        "v-calc": "feature/measure/MeasureCalcScreen.kt",
        "v-left": "feature/measure/MeasureLeftScreen.kt",
        "v-invalid": "feature/measure/MeasureInvalidScreen.kt",
        "v-savefail": "feature/measure/MeasureSaveFailScreen.kt",
        "v-success": "feature/measure/MeasureSuccessScreen.kt",
        "v-result": "feature/measure/ResultScreen.kt",
        "v-ai-report": "feature/measure/AiReportScreen.kt",
        "v-share": "feature/measure/ResultShareScreen.kt",
        "v-export": "feature/measure/ResultExportScreen.kt",
        "v-export-done": "feature/measure/ExportDoneScreen.kt",
        "v-measuring": "feature/measure/MeasuringScreen.kt",
        "v-search": "feature/device/DeviceSearchScreen.kt",
        "v-bt-permission": "feature/device/BtPermissionScreen.kt",
        "v-confirm-device": "feature/device/ConfirmDeviceScreen.kt",
        "v-connecting": "feature/device/ConnectingScreen.kt",
        "v-pairing": "feature/device/PairingScreen.kt",
        "v-pair-success": "feature/device/PairSuccessScreen.kt",
        "v-pair-fail": "feature/device/PairFailScreen.kt",
        "v-settings": "feature/device/DeviceDetailScreen.kt",
        "v-rename-device": "feature/device/DeviceRenameScreen.kt",
        "v-member-device": "feature/device/MemberDeviceLinkScreen.kt",
        "v-firmware-check": "feature/device/FirmwareManagerScreen.kt",
        "v-firmware-update": "feature/device/FirmwareUpdateScreen.kt",
        "v-firmware-latest": "feature/device/FirmwareLatestScreen.kt",
        "v-history": "feature/history/HistoryScreen.kt",
        "v-detail": "feature/history/HistoryDetailScreen.kt",
        "v-edit": "feature/history/RecordEditScreen.kt",
        "v-compare": "feature/history/HistoryCompareScreen.kt",
        "v-trend": "feature/trend/TrendScreen.kt",
        "v-weight": "feature/trend/MetricDetailScreen.kt",
        "v-bmi": "feature/trend/MetricDetailScreen.kt",
        "v-muscle": "feature/trend/MetricDetailScreen.kt",
        "v-water": "feature/trend/MetricDetailScreen.kt",
        "v-metabolism": "feature/trend/MetricDetailScreen.kt",
        "v-bone": "feature/trend/MetricDetailScreen.kt",
        "v-visceral": "feature/trend/MetricDetailScreen.kt",
        "v-list": "feature/trend/MetricListScreen.kt",
        "v-members": "feature/member/MembersScreen.kt",
        "v-edit-member": "feature/member/MemberEditScreen.kt",
        "v-savefail": "feature/member/SaveFailScreen.kt",
        "v-member-norecord": "feature/member/NoRecordScreen.kt",
        "v-member-nodevice": "feature/member/NoDeviceScreen.kt",
        "v-profile": "feature/member/MemberProfileScreen.kt",
        "v-plans": "feature/member/ActionPlansScreen.kt",
        "v-edit-plan": "feature/member/EditPlanScreen.kt",
        "v-edit-task": "feature/member/EditTaskScreen.kt",
        "v-plan-empty": "feature/member/EmptyPlanScreen.kt",
        "v-remind": "feature/member/RemindScreen.kt",
        "v-notif-denied": "feature/member/NotifDeniedScreen.kt",
        "v-invite": "feature/member/FamilyInviteScreen.kt",
        "v-invite-done": "feature/member/InviteDoneScreen.kt",
        "v-accept": "feature/member/AcceptInviteScreen.kt",
        "v-accept-confirm": "feature/member/AcceptConfirmScreen.kt",
        "v-accept-done": "feature/member/AcceptDoneScreen.kt",
        "v-revoke": "feature/member/RevokeScreen.kt",
        "v-multi-device": "feature/member/MultiDeviceScreen.kt",
        "v-multi-done": "feature/member/MultiDoneScreen.kt",
        "v-weekly": "feature/ai/AiWeeklyScreen.kt",
        "v-member-detail": "feature/ai/MemberDetailScreen.kt",
        "v-sync": "feature/sync/SyncCenterScreen.kt",
        "v-network": "feature/sync/NetworkStatusScreen.kt",
        "v-service": "feature/sync/ServiceStatusScreen.kt",
        "v-offline": "feature/sync/OfflineScreen.kt",
        "v-offline-done": "feature/sync/OfflineDoneScreen.kt",
        "v-offline-switch": "feature/sync/OfflineSwitchScreen.kt",
        "v-sync-conflict": "feature/sync/ConflictScreen.kt",
        "v-sync-done": "feature/sync/ConflictDoneScreen.kt",
        "v-help": "feature/sync/HelpCenterScreen.kt",
        "v-faq": "feature/sync/FaqScreen.kt",
        "v-feedback": "feature/sync/FeedbackScreen.kt",
        "v-feedback-submit": "feature/sync/FeedbackSubmitScreen.kt",
        "v-feedback-success": "feature/sync/FeedbackSuccessScreen.kt",
        "v-feedback-fail": "feature/sync/FeedbackFailScreen.kt",
        "v-diag": "feature/sync/DiagScreen.kt",
        "v-closure-apply": "feature/mine/ClosureApplyScreen.kt",
        "v-closure-verify": "feature/mine/ClosureVerifyScreen.kt",
        "v-closure-processing": "feature/mine/ClosureProcessingScreen.kt",
        "v-closure-result": "feature/mine/ClosureResultScreen.kt",
        "v-about": "feature/mine/AboutScreen.kt",
        "v-privacy": "feature/mine/PrivacyDetailScreen.kt",
        "v-account": "feature/mine/AccountSecurityScreen.kt",
        "v-sdk-list": "feature/settings/SdkListScreen.kt",
        "v-recovery": "feature/settings/DataRecoveryScreen.kt",
        "v-maintenance": "feature/settings/MaintenanceScreen.kt",
        "v-language": "feature/settings/LanguageScreen.kt",
        "v-appearance": "feature/settings/AppearanceScreen.kt",
        "v-recovery-done": "feature/settings/RecoveryDoneScreen.kt",
        "v-auth": "feature/privacy/PrivacyAuthScreen.kt",
        "v-granted": "feature/privacy/PrivacyGrantedScreen.kt",
        "v-unauth": "feature/privacy/PrivacyUnauthScreen.kt",
        "v-consult": "feature/privacy/PrivacyConsultScreen.kt",
        "v-rules": "feature/privacy/PrivacyRulesScreen.kt",
        "v-chat": "feature/ai/AiChatScreen.kt",
        "v-report": "feature/ai/AiReportScreen.kt",
        "v-generating": "feature/ai/GeneratingScreen.kt",
        "v-failed": "feature/ai/FailedScreen.kt",
        "v-plan": "feature/ai/PlanScreen.kt",
        "v-ai-unavailable": "feature/ai/UnavailableScreen.kt",
        "v-report-expired": "feature/ai/ReportExpiredScreen.kt",
        "v-reanalyzing": "feature/ai/ReanalyzingScreen.kt",
        "v-report-done": "feature/ai/ReportDoneScreen.kt",
        "v-unauth-consult": "feature/ai/UnauthConsultScreen.kt",
        "v-member-unauth": "feature/ai/MemberUnauthScreen.kt",
        "v-demo": "ui/components/QkComponentsScreen.kt",
        "v-skeleton": "ui/components/QkSkeleton.kt",
        "v-longpress": "ui/components/QkLongPressMenu.kt",
        "v-pull-refresh": "ui/components/QkPullRefresh.kt",
        "v-swipe-delete": "ui/components/QkSwipeDelete.kt",
        "v-infinite-scroll": "ui/components/QkInfiniteScroll.kt",
        "v-search-filter": "ui/components/QkSearchBar.kt",
        "v-rec-edit": "ui/states/RecordEditState.kt",
        "v-export": "ui/states/ExportState.kt",
        "v-score": "ui/states/ScoreState.kt",
        "v-anomaly": "ui/states/AnomalyState.kt",
        "v-multimetric": "ui/states/MultiMetricState.kt",
        "v-pair-timeout": "ui/states/PairTimeoutState.kt",
        "v-report-stale": "ui/states/ReportStaleState.kt",
        "v-sync-fail": "ui/states/SyncFailState.kt",
        "v-fb-fail": "ui/states/FeedbackFailState.kt",
        "v-connect-guide": "ui/states/ConnectGuideState.kt",
        "v-troubleshoot": "ui/states/TroubleshootState.kt",
        "v-privacy-qa": "ui/states/PrivacyQaState.kt",
        "v-notif-landing": "ui/states/NotifLandingState.kt",
        "v-fullscreen-load": "ui/states/FullscreenLoadState.kt",
    }
    
    if view_id in VIEW_TO_FILE:
        return [f"app/src/main/kotlin/com/xcwk/scalehub/{VIEW_TO_FILE[view_id]}"]
    
    # 启发式
    base = prototype_file_to_module(prototype_file)
    name = view_id.replace("v-", "").replace("-", " ").title().replace(" ", "")
    return [f"app/src/main/kotlin/com/xcwk/scalehub/{base}/{name}Screen.kt"]

def prototype_file_to_module(prototype_file: str) -> str:
    mapping = {
        "01-login.html": "feature/auth",
        "02-onboarding-permission.html": "feature/guide",
        "03-measure.html": "feature/measure",
        "12-measure-full.html": "feature/measure",
        "16-measure-result.html": "feature/measure",
        "04-device.html": "feature/device",
        "18-device-setup.html": "feature/device",
        "05-history-trend.html": "feature/history",
        "17-metric-details.html": "feature/trend",
        "06-ai.html": "feature/ai",
        "20-ai-members.html": "feature/ai",
        "25-ai-boundaries.html": "feature/ai",
        "13-members-plans.html": "feature/member",
        "23-family-invite.html": "feature/member",
        "07-mine-settings.html": "feature/mine",
        "24-remaining-gaps.html": "feature/settings",
        "26-final-gaps.html": "ui/components",
        "08-sync-network-help.html": "feature/sync",
        "22-data-conflict.html": "feature/sync",
        "10-privacy-authorization.html": "feature/privacy",
        "14-account-lifecycle.html": "feature/auth",
        "21-account-recovery.html": "feature/auth",
        "19-onboarding-guide.html": "feature/guide",
        "11-home-states.html": "feature/measure",
        "09-components-demo.html": "ui/components",
        "15-states-errors.html": "ui/states",
    }
    return mapping.get(prototype_file, "feature/misc")

def generate_extra_tasks(gen_id):
    """生成额外任务：集成/领域/治理/DDD/接入波"""
    tasks = []
    
    # Integration tasks
    integrations = [
        ("I-DATA-RECOVERY", "integration", "F-SETTINGS-ADVANCED", "E-MINE", "settings", 3, "dev", ["feature/settings/DataRecovery.kt"]),
        ("I-PROFILE-SETUP", "integration", "F-GUIDE-PROFILE", "E-AUTH", "guide", 3, "dev", ["feature/guide/ProfileSetup.kt"]),
        ("I-PERMISSION-FLOW", "integration", "F-GUIDE-ONBOARD", "E-AUTH", "guide", 3, "dev", ["feature/guide/PermissionFlow.kt"]),
        ("I-GUIDE-SCREEN", "integration", "F-GUIDE-PROFILE", "E-AUTH", "guide", 3, "dev", ["feature/guide/GuideScreen.kt"]),
        ("I-AI-BOUNDARIES", "integration", "F-AI-BOUNDARY", "E-AI", "ai", 3, "dev", ["feature/ai/AiBoundaries.kt"]),
        ("I-DEVICE-RENAME", "integration", "F-DEVICE-DETAIL", "E-DEVICE", "device", 3, "dev", ["feature/device/DeviceRename.kt"]),
        ("I-MEMBER-DEVICE", "integration", "F-DEVICE-DETAIL", "E-DEVICE", "device", 3, "dev", ["feature/device/MemberDeviceLink.kt"]),
        ("I-FIRMWARE", "integration", "F-DEVICE-FIRMWARE", "E-DEVICE", "device", 3, "dev", ["feature/device/FirmwareManager.kt"]),
        ("I-FEEDBACK", "integration", "F-SYNC-HELP", "E-SYNC", "sync", 3, "dev", ["feature/sync/FeedbackFlow.kt", "feature/sync/HelpCenterFlow.kt"]),
        ("I-PRIVACY-AUTH", "integration", "F-PRIVACY-AUTH", "E-PRIVACY", "privacy", 3, "dev", ["feature/privacy/PrivacyAuthFlow.kt"]),
        ("I-ACTION-PLANS", "integration", "F-MEMBER-PLAN", "E-MEMBER", "member", 3, "dev", ["feature/member/ActionPlans.kt"]),
        ("I-RESULT-SHARE", "integration", "F-MEASURE-RESULT", "E-MEASURE", "measure", 3, "dev", ["feature/measure/ResultShare.kt"]),
        ("I-DATA-CONFLICT", "integration", "F-SYNC-CONFLICT", "E-SYNC", "sync", 3, "dev", ["feature/sync/DataConflict.kt"]),
        ("I-MAINTENANCE", "integration", "F-SETTINGS-ADVANCED", "E-MINE", "settings", 3, "dev", ["feature/settings/MaintenanceScreen.kt", "feature/settings/SdkListScreen.kt"]),
        ("I-AI-CARDS", "integration", "F-AI-CARDS", "E-AI", "ai", 3, "dev", ["feature/ai/AiCards.kt"]),
        ("I-MEASURE-HELP", "integration", "F-MEASURE-CORE", "E-MEASURE", "measure", 3, "dev", ["feature/measure/scale/MeasureHelp.kt"]),
        ("I-ACCOUNT-LIFECYCLE", "integration", "F-AUTH-LIFECYCLE", "E-AUTH", "auth", 3, "dev", ["feature/auth/AccountLifecycle.kt"]),
        ("I-HEALTH-CONNECT", "integration", "F-SYNC-CENTER", "E-SYNC", "sync", 3, "dev", ["feature/sync/HealthConnectPlatform.kt"]),
    ]
    
    for tid, ttype, feature, epic, domain, wave, lane, files in integrations:
        tasks.append({
            "id": tid,
            "type": ttype,
            "parent_feature": feature,
            "parent_epic": epic,
            "domain": domain,
            "wave": wave,
            "lane": lane,
            "target_files": [f"app/src/main/kotlin/com/xcwk/scalehub/{f}" for f in files],
            "acceptance": {
                "verify_commands": ["./gradlew :app:testDebugUnitTest"],
                "definition_of_done": ["接线完成", "编译通过", "单测通过"]
            },
            "status": "blocked",  # 等待 DEC-081
            "priority": "P0" if wave == 3 else "P1",
            "depends_on": ["DEC-081"],
            "evidence": {}
        })
    
    # Domain-Logic tasks (DDD)
    domain_tasks = [
        ("D-BODY-COMP", "domain-logic", "F-MEASURE-CORE", "E-MEASURE", "measure", 4, "dev", ["domain/service/BodyCompositionService.kt"]),
        ("D-HEALTH-RISK", "domain-logic", "F-MEASURE-CORE", "E-MEASURE", "measure", 4, "dev", ["domain/service/HealthRiskAssessmentService.kt"]),
        ("D-BODY-SHAPE", "domain-logic", "F-MEASURE-CORE", "E-MEASURE", "measure", 4, "dev", ["domain/service/BodyShapeClassificationService.kt"]),
        ("D-HEALTH-ADVICE", "domain-logic", "F-AI-CORE", "E-AI", "ai", 4, "dev", ["domain/service/HealthAdviceService.kt"]),
        ("D-MEASURE-RECORD", "domain-logic", "F-MEASURE-CORE", "E-MEASURE", "measure", 4, "dev", ["domain/model/MeasureRecord.kt"]),
        ("D-HEALTH-LOG", "domain-logic", "F-HISTORY-LIST", "E-HISTORY", "history", 4, "dev", ["domain/model/HealthLog.kt"]),
        ("D-MEMBER", "domain-logic", "F-MEMBER-PROFILE", "E-MEMBER", "member", 4, "dev", ["domain/model/Member.kt"]),
        ("D-HABIT", "domain-logic", "F-MEMBER-PLAN", "E-MEMBER", "member", 4, "dev", ["domain/model/Habit.kt"]),
        ("D-BODY-ANALYSIS", "domain-logic", "F-MEASURE-RESULT", "E-MEASURE", "measure", 4, "dev", ["application/BodyAnalysisService.kt"]),
        ("D-MEASURE-SESSION", "domain-logic", "F-MEASURE-CORE", "E-MEASURE", "measure", 4, "dev", ["application/MeasureSessionService.kt"]),
    ]
    
    for tid, ttype, feature, epic, domain, wave, lane, files in domain_tasks:
        tasks.append({
            "id": tid,
            "type": ttype,
            "parent_feature": feature,
            "parent_epic": epic,
            "domain": domain,
            "wave": wave,
            "lane": lane,
            "target_files": [f"app/src/main/kotlin/com/xcwk/scalehub/{f}" for f in files],
            "acceptance": {
                "verify_commands": ["./gradlew :app:testDebugUnitTest", "bash scripts/dev/dev-verify.sh --compile-only"],
                "definition_of_done": ["领域服务创建", "编译通过", "单测覆盖≥80%"]
            },
            "status": "done",
            "priority": "P0",
            "evidence": {}
        })
    
    # Governance tasks
    gov_tasks = [
        ("G-PROTO-VERIFY", "governance", "F-COMPONENTS", "E-PLATFORM", "gov", 0, "gov", ["scripts/governance/gov-prototype-verify.py"]),
        ("G-DEC-72-80", "governance", "F-COMPONENTS", "E-PLATFORM", "gov", 0, "gov", ["docs/ai/decisions/DEC-072.md", "docs/ai/decisions/DEC-073.md", "docs/ai/decisions/DEC-074.md", "docs/ai/decisions/DEC-075.md", "docs/ai/decisions/DEC-076.md", "docs/ai/decisions/DEC-077.md", "docs/ai/decisions/DEC-078.md", "docs/ai/decisions/DEC-079.md", "docs/ai/decisions/DEC-080.md"]),
        ("G-SHIP-EVIDENCE", "governance", "F-COMPONENTS", "E-PLATFORM", "gov", 0, "gov", ["scripts/dev/ship.sh"]),
        ("G-PYTEST-FIX", "governance", "F-COMPONENTS", "E-PLATFORM", "gov", 0, "test", ["tests/unit/test_board_invariants.py", "tests/unit/test_board_intake.py"]),
        ("G-GOV-TESTS", "governance", "F-COMPONENTS", "E-PLATFORM", "gov", 0, "test", ["tests/unit/test_role_system.py", "tests/unit/test_profile.py", "tests/unit/test_batch.py", "tests/unit/test_autopilot.py"]),
        ("G-PRE-RECEIVE", "governance", "F-COMPONENTS", "E-PLATFORM", "gov", 0, "gov", [".github/hooks/pre-receive", "scripts/governance/gov-audit-bypass.sh", "docs/ai/decisions/DEC-081.md"]),
        ("G-FUNC-ACCEPT", "governance", "F-COMPONENTS", "E-PLATFORM", "gov", 0, "test", ["tests/unit/test_functional_acceptance.py"]),
        ("G-DDD-EVIDENCE", "governance", "F-COMPONENTS", "E-PLATFORM", "gov", 0, "gov", ["tests/unit/test_dddfix_evidence.py"]),
        ("G-PROTO-COVERAGE", "governance", "F-COMPONENTS", "E-PLATFORM", "gov", 0, "gov", ["tests/unit/test_prototype_coverage.py"]),
    ]
    
    for tid, ttype, feature, epic, domain, wave, lane, files in gov_tasks:
        tasks.append({
            "id": tid,
            "type": ttype,
            "parent_feature": feature,
            "parent_epic": epic,
            "domain": domain,
            "wave": wave,
            "lane": lane,
            "target_files": files,
            "acceptance": {
                "verify_commands": ["bash scripts/governance/gov-validate.sh", "python3 -m pytest tests/unit -q"],
                "definition_of_done": ["脚本通过", "测试通过", "文档索引收录"]
            },
            "status": "done",
            "priority": "P0",
            "evidence": {}
        })
    
    return tasks

# ========== 执行重构 ==========

if __name__ == "__main__":
    print("🔄 开始重构任务...")
    
    # 生成 Layer 1
    layer1 = generate_layer1()
    layer1_path = Path(".ai/tasks.layer1.json")
    layer1_path.write_text(json.dumps(layer1, ensure_ascii=False, indent=2))
    print(f"✅ Layer 1 生成: {layer1_path}")
    print(f"   Epics: {len(layer1['product_tasks']['epics'])}")
    print(f"   Features: {len(layer1['product_tasks']['features'])}")
    print(f"   Stories: {len(layer1['product_tasks']['stories'])}")
    
    # 生成 Layer 2
    layer2 = generate_layer2()
    layer2_path = Path(".ai/tasks.json.new")
    layer2_path.write_text(json.dumps(layer2, ensure_ascii=False, indent=2))
    print(f"✅ Layer 2 生成: {layer2_path}")
    print(f"   总工程任务: {len(layer2['tasks'])}")
    
    # 统计类型分布
    type_count = defaultdict(int)
    status_count = defaultdict(int)
    for t in layer2['tasks']:
        type_count[t['type']] += 1
        status_count[t['status']] += 1
    print(f"   类型分布: {dict(type_count)}")
    print(f"   状态分布: {dict(status_count)}")
    
    # 生成迁移报告
    report = {
        "timestamp": datetime.now().isoformat(),
        "original_count": len(ORIGINAL_TASKS),
        "layer1": {
            "epics": len(layer1['product_tasks']['epics']),
            "features": len(layer1['product_tasks']['features']),
            "stories": len(layer1['product_tasks']['stories'])
        },
        "layer2": {
            "total": len(layer2['tasks']),
            "by_type": dict(type_count),
            "by_status": dict(status_count)
        },
        "mapping_summary": {
            "mapped": len([m for m in TASK_MAPPING.values() if m.get("status") != "merged"]),
            "merged": len([m for m in TASK_MAPPING.values() if m.get("status") == "merged"]),
            "unmapped": len([t for t in ORIGINAL_TASKS if t not in TASK_MAPPING])
        }
    }
    
    report_path = Path(".ai/restructure_report.json")
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(f"✅ 迁移报告: {report_path}")
    
    print("\n⚠️  下一步:")
    print("1. 检查 .ai/tasks.layer1.json 和 .ai/tasks.json.new")
    print("2. 确认无误后: mv .ai/tasks.json.new .ai/tasks.json")
    print("3. 运行 wf-autopilot.sh status 验证新结构")