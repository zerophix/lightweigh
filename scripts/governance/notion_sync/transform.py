"""任务分解器 - Notion 产品任务 → 仓库工程任务"""
import re
from pathlib import Path
from typing import List, Dict, Optional, Any, Set
from dataclasses import dataclass, asdict
from datetime import datetime

from .client import NotionClient, NotionPage
from .prototype_parser import PrototypeParser, PrototypeView
from .config import get_config


@dataclass
class ProductEpic:
    """产品 Epic"""
    id: str
    notion_id: str
    title: str
    prd_ref: str
    status: str
    priority: str
    milestone: str
    features: List[str] = None
    
    def __post_init__(self):
        if self.features is None:
            self.features = []


@dataclass
class ProductFeature:
    """产品 Feature"""
    id: str
    notion_id: str
    epic_id: str
    title: str
    prototype_file: str
    prototype_views: List[str]
    status: str
    stories: List[str] = None
    
    def __post_init__(self):
        if self.stories is None:
            self.stories = []


@dataclass
class ProductStory:
    """产品 Story（单 View）"""
    id: str
    notion_id: str
    feature_id: str
    title: str
    prototype_view: str
    prototype_file: str
    acceptance_text: str
    ac_refs: List[str]
    status: str
    story_points: int = 3


@dataclass
class EngineeringTask:
    """工程任务"""
    id: str
    type: str  # prototype-driven | integration | domain-logic | tech-debt | incident-fix | governance
    parent_story: Optional[str]
    parent_feature: Optional[str]
    parent_epic: Optional[str]
    domain: str
    wave: int
    lane: str  # dev | test | gov | ops
    target_files: List[str]
    acceptance: Dict
    status: str = "ready"
    priority: str = "P1"
    created_at: str = ""
    updated_at: str = ""
    evidence: Dict = None
    
    def __post_init__(self):
        if not self.created_at:
            self.created_at = datetime.now().isoformat()
        if not self.updated_at:
            self.updated_at = self.created_at
        if self.evidence is None:
            self.evidence = {}


class TaskDecomposer:
    """任务分解器"""
    
    # 域名映射
    DOMAIN_MAP = {
        "auth": "auth",
        "account": "auth",
        "login": "auth",
        "register": "auth",
        "password": "auth",
        "guide": "guide",
        "onboard": "guide",
        "permission": "guide",
        "measure": "measure",
        "report": "measure",
        "result": "measure",
        "device": "device",
        "firmware": "device",
        "pair": "device",
        "connect": "device",
        "history": "history",
        "trend": "trend",
        "metric": "trend",
        "compare": "history",
        "ai": "ai",
        "chat": "ai",
        "weekly": "ai",
        "boundary": "ai",
        "member": "member",
        "family": "member",
        "invite": "member",
        "plan": "member",
        "remind": "member",
        "mine": "mine",
        "setting": "settings",
        "privacy": "privacy",
        "closure": "mine",
        "security": "mine",
        "sdk": "settings",
        "recovery": "settings",
        "maintenance": "settings",
        "sync": "sync",
        "network": "sync",
        "conflict": "sync",
        "help": "sync",
        "feedback": "sync",
        "component": "components",
        "state": "states",
        "error": "states",
        "skeleton": "components",
    }
    
    # View ID 到目标文件的映射（启发式）
    VIEW_TO_FILE_PATTERNS = {
        r"v-phone": "feature/auth/PhoneLoginScreen.kt",
        r"v-verify": "feature/auth/VerifyCodeScreen.kt",
        r"v-password": "feature/auth/PasswordLoginScreen.kt",
        r"v-email": "feature/auth/EmailLoginScreen.kt",
        r"v-wechat": "feature/auth/WeChatLoginScreen.kt",
        r"v-guest": "feature/auth/GuestModeScreen.kt",
        r"v-reset": "feature/auth/PasswordResetScreen.kt",
        r"v-choose": "feature/auth/AccountRecoveryChooseScreen.kt",
        r"v-set-password": "feature/auth/SetPasswordScreen.kt",
        r"v-splash": "feature/guide/SplashScreen.kt",
        r"v-terms": "feature/guide/TermsScreen.kt",
        r"v-permission": "feature/guide/PermissionFlowScreen.kt",
        r"v-intro": "feature/guide/IntroScreen.kt",
        r"v-goal": "feature/guide/GoalScreen.kt",
        r"v-prepare": "feature/guide/PrepareScreen.kt",
        r"v-connected": "feature/guide/ConnectedScreen.kt",
        r"v-onboard-age": "feature/guide/ProfileSetupScreen.kt",
        r"v-onboard-height": "feature/guide/ProfileSetupScreen.kt",
        r"v-check": "feature/measure/MeasureCheckScreen.kt",
        r"v-prep": "feature/measure/MeasurePrepScreen.kt",
        r"v-stable": "feature/measure/MeasureStableScreen.kt",
        r"v-calc": "feature/measure/MeasureCalcScreen.kt",
        r"v-left": "feature/measure/MeasureLeftScreen.kt",
        r"v-invalid": "feature/measure/MeasureInvalidScreen.kt",
        r"v-savefail": "feature/measure/MeasureSaveFailScreen.kt",
        r"v-success": "feature/measure/MeasureSuccessScreen.kt",
        r"v-share": "feature/measure/ResultShareScreen.kt",
        r"v-export": "feature/measure/ResultExportScreen.kt",
        r"v-search": "feature/device/DeviceSearchScreen.kt",
        r"v-bt-permission": "feature/device/BtPermissionScreen.kt",
        r"v-confirm-device": "feature/device/ConfirmDeviceScreen.kt",
        r"v-connecting": "feature/device/ConnectingScreen.kt",
        r"v-connected": "feature/device/ConnectedScreen.kt",
        r"v-firmware-check": "feature/device/FirmwareManagerScreen.kt",
        r"v-rename-device": "feature/device/DeviceRenameScreen.kt",
        r"v-member-device": "feature/device/MemberDeviceLinkScreen.kt",
        r"v-pairing": "feature/device/PairingScreen.kt",
        r"v-pair-success": "feature/device/PairSuccessScreen.kt",
        r"v-pair-fail": "feature/device/PairFailScreen.kt",
        r"v-detail": "feature/history/HistoryDetailScreen.kt",
        r"v-edit": "feature/history/RecordEditScreen.kt",
        r"v-trend": "feature/trend/TrendScreen.kt",
        r"v-compare": "feature/history/HistoryCompareScreen.kt",
        r"v-weight": "feature/trend/MetricDetailScreen.kt",
        r"v-bmi": "feature/trend/MetricDetailScreen.kt",
        r"v-muscle": "feature/trend/MetricDetailScreen.kt",
        r"v-water": "feature/trend/MetricDetailScreen.kt",
        r"v-metabolism": "feature/trend/MetricDetailScreen.kt",
        r"v-bone": "feature/trend/MetricDetailScreen.kt",
        r"v-visceral": "feature/trend/MetricDetailScreen.kt",
        r"v-generating": "feature/ai/AiGeneratingScreen.kt",
        r"v-failed": "feature/ai/AiFailedScreen.kt",
        r"v-unauth": "feature/ai/AiUnauthScreen.kt",
        r"v-plan": "feature/ai/AiPlanScreen.kt",
        r"v-weekly": "feature/ai/AiWeeklyScreen.kt",
        r"v-member-detail": "feature/ai/MemberDetailScreen.kt",
        r"v-ai-unavailable": "feature/ai/AiUnavailableScreen.kt",
        r"v-report-expired": "feature/ai/ReportExpiredScreen.kt",
        r"v-reanalyzing": "feature/ai/ReanalyzingScreen.kt",
        r"v-report-done": "feature/ai/ReportDoneScreen.kt",
        r"v-unauth-consult": "feature/ai/UnauthConsultScreen.kt",
        r"v-member-unauth": "feature/ai/MemberUnauthScreen.kt",
        r"v-edit-member": "feature/member/MemberEditScreen.kt",
        r"v-savefail": "feature/member/SaveFailScreen.kt",
        r"v-member-norecord": "feature/member/NoRecordScreen.kt",
        r"v-member-nodevice": "feature/member/NoDeviceScreen.kt",
        r"v-plans": "feature/member/ActionPlansScreen.kt",
        r"v-edit-plan": "feature/member/EditPlanScreen.kt",
        r"v-edit-task": "feature/member/EditTaskScreen.kt",
        r"v-plan-empty": "feature/member/EmptyPlanScreen.kt",
        r"v-remind": "feature/member/RemindScreen.kt",
        r"v-notif-denied": "feature/member/NotifDeniedScreen.kt",
        r"v-invite": "feature/member/FamilyInviteScreen.kt",
        r"v-accept": "feature/member/AcceptInviteScreen.kt",
        r"v-revoke": "feature/member/RevokeScreen.kt",
        r"v-closure-apply": "feature/mine/ClosureApplyScreen.kt",
        r"v-closure-verify": "feature/mine/ClosureVerifyScreen.kt",
        r"v-closure-processing": "feature/mine/ClosureProcessingScreen.kt",
        r"v-closure-result": "feature/mine/ClosureResultScreen.kt",
        r"v-about": "feature/mine/AboutScreen.kt",
        r"v-privacy": "feature/mine/PrivacyDetailScreen.kt",
        r"v-account": "feature/mine/AccountSecurityScreen.kt",
        r"v-sdk-list": "feature/settings/SdkListScreen.kt",
        r"v-recovery": "feature/settings/DataRecoveryScreen.kt",
        r"v-maintenance": "feature/settings/MaintenanceScreen.kt",
        r"v-language": "feature/settings/LanguageScreen.kt",
        r"v-appearance": "feature/settings/AppearanceScreen.kt",
        r"v-sync": "feature/sync/SyncCenterScreen.kt",
        r"v-network": "feature/sync/NetworkStatusScreen.kt",
        r"v-service": "feature/sync/ServiceStatusScreen.kt",
        r"v-sync-conflict": "feature/sync/ConflictScreen.kt",
        r"v-offline-switch": "feature/sync/OfflineSwitchScreen.kt",
        r"v-help": "feature/sync/HelpCenterScreen.kt",
        r"v-faq": "feature/sync/FaqScreen.kt",
        r"v-feedback": "feature/sync/FeedbackScreen.kt",
        r"v-auth": "feature/privacy/PrivacyAuthScreen.kt",
        r"v-granted": "feature/privacy/PrivacyGrantedScreen.kt",
        r"v-consult": "feature/privacy/PrivacyConsultScreen.kt",
        r"v-rules": "feature/privacy/PrivacyRulesScreen.kt",
        r"v-pull-refresh": "ui/components/QkPullRefresh.kt",
        r"v-swipe-delete": "ui/components/QkSwipeDelete.kt",
        r"v-infinite-scroll": "ui/components/QkInfiniteScroll.kt",
        r"v-search-filter": "ui/components/QkSearchBar.kt",
        r"v-skeleton": "ui/components/QkSkeleton.kt",
        r"v-longpress": "ui/components/QkLongPressMenu.kt",
        r"v-rec-edit": "ui/states/RecordEditState.kt",
        r"v-score": "ui/states/ScoreState.kt",
        r"v-anomaly": "ui/states/AnomalyState.kt",
        r"v-multimetric": "ui/states/MultiMetricState.kt",
        r"v-pair-timeout": "ui/states/PairTimeoutState.kt",
        r"v-report-stale": "ui/states/ReportStaleState.kt",
        r"v-sync-fail": "ui/states/SyncFailState.kt",
        r"v-fb-fail": "ui/states/FeedbackFailState.kt",
        r"v-connect-guide": "ui/states/ConnectGuideState.kt",
        r"v-troubleshoot": "ui/states/TroubleshootState.kt",
        r"v-privacy-qa": "ui/states/PrivacyQaState.kt",
        r"v-notif-landing": "ui/states/NotifLandingState.kt",
        r"v-fullscreen-load": "ui/states/FullscreenLoadState.kt",
    }
    
    def __init__(self, client: NotionClient, parser: PrototypeParser):
        self.client = client
        self.parser = parser
        self.config = get_config()
        self._task_counter = 0
    
    def decompose_epic(self, page: NotionPage) -> ProductEpic:
        """解析 Epic 页面"""
        props = page.properties
        return ProductEpic(
            id=self._generate_id("E"),
            notion_id=page.id,
            title=page.title,
            prd_ref=props.get("PRD页面", {}).get("url", "") if isinstance(props.get("PRD页面"), dict) else str(props.get("PRD页面", "")),
            status=props.get("状态", {}).get("select", {}).get("name", "规划") if isinstance(props.get("状态"), dict) else "规划",
            priority=props.get("优先级", {}).get("select", {}).get("name", "P1") if isinstance(props.get("优先级"), dict) else "P1",
            milestone=props.get("目标里程碑", {}).get("select", {}).get("name", "") if isinstance(props.get("目标里程碑"), dict) else "",
            features=props.get("包含Feature", []) if isinstance(props.get("包含Feature"), list) else []
        )
    
    def decompose_feature(self, page: NotionPage) -> ProductFeature:
        """解析 Feature 页面"""
        props = page.properties
        
        # 获取原型文件
        prototype_file = ""
        proto_prop = props.get("原型页面")
        if isinstance(proto_prop, dict) and proto_prop.get("url"):
            prototype_file = proto_prop["url"].split("/")[-1]
        elif isinstance(proto_prop, str):
            prototype_file = proto_prop
        
        # 获取 View 列表
        prototype_views = []
        views_prop = props.get("原型Views")
        if isinstance(views_prop, list):
            prototype_views = views_prop
        elif isinstance(views_prop, str):
            prototype_views = [v.strip() for v in views_prop.split(",")]
        
        return ProductFeature(
            id=self._generate_id("F"),
            notion_id=page.id,
            epic_id=props.get("所属Epic", {}).get("relation", [{}])[0].get("id", "") if isinstance(props.get("所属Epic"), dict) else "",
            title=page.title,
            prototype_file=prototype_file,
            prototype_views=prototype_views,
            status=props.get("状态", {}).get("select", {}).get("name", "草稿") if isinstance(props.get("状态"), dict) else "草稿",
            stories=props.get("包含Story", []) if isinstance(props.get("包含Story"), list) else []
        )
    
    def decompose_story(self, page: NotionPage) -> ProductStory:
        """解析 Story 页面"""
        props = page.properties
        
        return ProductStory(
            id=self._generate_id("S"),
            notion_id=page.id,
            feature_id=props.get("所属Feature", {}).get("relation", [{}])[0].get("id", "") if isinstance(props.get("所属Feature"), dict) else "",
            title=page.title,
            prototype_view=props.get("原型View", ""),
            prototype_file=props.get("原型文件", ""),
            acceptance_text=props.get("验收文案", {}).get("rich_text", [{}])[0].get("plain_text", "") if isinstance(props.get("验收文案"), dict) else str(props.get("验收文案", "")),
            ac_refs=props.get("AC引用", []) if isinstance(props.get("AC引用"), list) else [],
            status=props.get("状态", {}).get("select", {}).get("name", "待办") if isinstance(props.get("状态"), dict) else "待办",
            story_points=props.get("Story Points", {}).get("number", 3) if isinstance(props.get("Story Points"), dict) else 3
        )
    
    def decompose_all_stories(self, features: List[ProductFeature]) -> List[ProductStory]:
        """从 Feature 的 prototype_views 自动生成 Story"""
        stories = []
        for feature in features:
            for view_id in feature.prototype_views:
                # 查找原型文件中的 View
                proto_view = self.parser.get_view(feature.prototype_file, view_id)
                acceptance = proto_view.text_content if proto_view else f"实现 {view_id} 视图"
                
                story = ProductStory(
                    id=self._generate_id("S"),
                    notion_id="",  # 自动生成，无 Notion 页面
                    feature_id=feature.id,
                    title=f"{feature.title} - {view_id}",
                    prototype_view=view_id,
                    prototype_file=feature.prototype_file,
                    acceptance_text=acceptance[:500],
                    ac_refs=[f"AC-{feature.id}-{view_id}"],
                    status="ready" if feature.status == "就绪" else "草稿",
                    story_points=3
                )
                stories.append(story)
        return stories
    
    def create_engineering_tasks(self, stories: List[ProductStory], 
                                  features: List[ProductFeature],
                                  epics: List[ProductEpic]) -> List[EngineeringTask]:
        """Story → Engineering Tasks"""
        tasks = []
        
        # 建立映射
        feature_map = {f.id: f for f in features}
        epic_map = {e.id: e for e in epics}
        
        for story in stories:
            feature = feature_map.get(story.feature_id)
            epic = epic_map.get(feature.epic_id) if feature else None
            
            if not feature:
                continue
            
            domain = self._infer_domain(feature, story)
            wave = self._infer_wave(feature, story)
            lane = self._infer_lane(domain, story)
            
            # 主任务：prototype-driven
            target_files = self._infer_target_files(story.prototype_view, feature.prototype_file)
            
            main_task = EngineeringTask(
                id=self._generate_task_id("ENG"),
                type="prototype-driven",
                parent_story=story.id,
                parent_feature=feature.id,
                parent_epic=epic.id if epic else None,
                domain=domain,
                wave=wave,
                lane=lane,
                target_files=target_files,
                acceptance={
                    "verify_commands": [
                        f"python3 scripts/governance/gov-prototype-verify.py {self._generate_task_id('ENG')} --strict",
                        "./gradlew :app:testDebugUnitTest"
                    ],
                    "definition_of_done": [
                        "target_files 存在且可编译",
                        "视图文案与原型一致",
                        "单测通过",
                        "gov-prototype-verify --strict 通过",
                        "local-ci PASS"
                    ],
                    "forbidden": [
                        "硬编码颜色/间距",
                        "静默改黄金向量",
                        "删除或放宽既有测试"
                    ]
                },
                priority=self._infer_priority(epic, feature)
            )
            tasks.append(main_task)
            
            # 衍生任务
            derived = self._create_derived_tasks(story, feature, domain, wave, lane)
            tasks.extend(derived)
        
        return tasks
    
    def _infer_domain(self, feature: ProductFeature, story: ProductStory) -> str:
        """推断所属域"""
        # 从 Feature 标题/原型文件推断
        text = f"{feature.title} {feature.prototype_file} {story.prototype_view}".lower()
        for keyword, domain in self.DOMAIN_MAP.items():
            if keyword in text:
                return domain
        return "misc"
    
    def _infer_wave(self, feature: ProductFeature, story: ProductStory) -> int:
        """推断波次"""
        # 基于原型文件前缀或 Feature 优先级
        if "01-" in feature.prototype_file or "02-" in feature.prototype_file:
            return 1
        elif "03-" in feature.prototype_file or "04-" in feature.prototype_file or "12-" in feature.prototype_file:
            return 2
        elif "05-" in feature.prototype_file or "17-" in feature.prototype_file:
            return 2
        elif "06-" in feature.prototype_file or "10-" in feature.prototype_file or "20-" in feature.prototype_file:
            return 2
        elif "07-" in feature.prototype_file or "24-" in feature.prototype_file:
            return 2
        elif "08-" in feature.prototype_file or "22-" in feature.prototype_file:
            return 2
        elif "09-" in feature.prototype_file or "15-" in feature.prototype_file or "26-" in feature.prototype_file:
            return 0
        return 1
    
    def _infer_lane(self, domain: str, story: ProductStory) -> str:
        """推断泳道"""
        if domain in ["components", "states", "gov"]:
            return "gov"
        return "dev"
    
    def _infer_priority(self, epic: Optional[ProductEpic], feature: ProductFeature) -> str:
        """推断优先级"""
        if epic and epic.priority:
            return epic.priority
        return "P1"
    
    def _infer_target_files(self, view_id: str, prototype_file: str) -> List[str]:
        """推断目标文件"""
        # 精确匹配
        for pattern, file_path in self.VIEW_TO_FILE_PATTERNS.items():
            if re.match(pattern, view_id):
                return [f"app/src/main/kotlin/com/xcwk/scalehub/{file_path}"]
        
        # 启发式：从 prototype_file 和 view_id 推断
        base_module = self._prototype_file_to_module(prototype_file)
        view_name = view_id.replace("v-", "").replace("-", " ").title().replace(" ", "")
        return [f"app/src/main/kotlin/com/xcwk/scalehub/{base_module}/{view_name}Screen.kt"]
    
    def _prototype_file_to_module(self, prototype_file: str) -> str:
        """原型文件名 -> 模块路径"""
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
        }
        return mapping.get(prototype_file, "feature/misc")
    
    def _create_derived_tasks(self, story: ProductStory, feature: ProductFeature, 
                               domain: str, wave: int, lane: str) -> List[EngineeringTask]:
        """创建衍生任务（集成/领域/测试）"""
        tasks = []
        base_id = self._generate_task_id("ENG")
        
        # Integration 任务：跨 Feature 的接线
        if self._needs_integration(story, feature):
            tasks.append(EngineeringTask(
                id=f"{base_id}-I",
                type="integration",
                parent_story=story.id,
                parent_feature=feature.id,
                domain=domain,
                wave=wave + 1,
                lane="dev",
                target_files=[],
                acceptance={
                    "verify_commands": ["./gradlew :app:testDebugUnitTest"],
                    "definition_of_done": ["接线完成", "编译通过", "单测通过"]
                }
            ))
        
        # Domain-Logic 任务：涉及算法/领域服务
        if self._needs_domain_logic(story, feature):
            tasks.append(EngineeringTask(
                id=f"{base_id}-D",
                type="domain-logic",
                parent_story=story.id,
                parent_feature=feature.id,
                domain=domain,
                wave=wave,
                lane="dev",
                target_files=self._infer_domain_files(domain),
                acceptance={
                    "verify_commands": ["./gradlew :app:testDebugUnitTest", "bash scripts/dev/dev-verify.sh --compile-only"],
                    "definition_of_done": ["领域服务创建", "编译通过", "单测覆盖≥80%"]
                }
            ))
        
        return tasks
    
    def _needs_integration(self, story: ProductStory, feature: ProductFeature) -> bool:
        """判断是否需要集成任务"""
        # 简单规则：涉及多个域的 Feature
        return False  # 后续可根据依赖图判断
    
    def _needs_domain_logic(self, story: ProductStory, feature: ProductFeature) -> bool:
        """判断是否需要领域逻辑任务"""
        domain_keywords = ["calculate", "assess", "classify", "analyze", "algorithm"]
        text = f"{story.title} {story.acceptance_text}".lower()
        return any(kw in text for kw in domain_keywords)
    
    def _infer_domain_files(self, domain: str) -> List[str]:
        """推断领域服务文件"""
        base = f"domain/src/main/kotlin/com/xcwk/scalehub/domain"
        return [
            f"{base}/service/{domain.title()}Service.kt",
            f"{base}/model/{domain.title()}.kt"
        ]
    
    def _generate_id(self, prefix: str) -> str:
        """生成 ID"""
        self._task_counter += 1
        return f"{prefix}-{self._task_counter:03d}"
    
    def _generate_task_id(self, prefix: str) -> str:
        """生成工程任务 ID"""
        return f"T-{prefix}-{self._task_counter:03d}"


# 兼容旧数据结构转换
def to_layer1_json(epics: List[ProductEpic], 
                    features: List[ProductFeature], 
                    stories: List[ProductStory]) -> Dict:
    """转换为 Layer1 JSON 格式"""
    return {
        "version": 2,
        "synced_at": datetime.now().isoformat(),
        "product_tasks": {
            "epics": [asdict(e) for e in epics],
            "features": [asdict(f) for f in features],
            "stories": [asdict(s) for s in stories]
        }
    }


def to_layer2_json(tasks: List[EngineeringTask], existing: Dict = None) -> Dict:
    """转换为 Layer2 JSON 格式（合并到 tasks.json）"""
    result = existing or {"version": 2, "tasks": []}
    
    # 建立现有任务索引
    existing_map = {t["id"]: t for t in result.get("tasks", [])}
    
    for task in tasks:
        task_dict = asdict(task)
        # 保留现有证据/状态
        if task.id in existing_map:
            old = existing_map[task.id]
            task_dict["evidence"] = old.get("evidence", task_dict["evidence"])
            task_dict["status"] = old.get("status", task_dict["status"])
        existing_map[task.id] = task_dict
    
    result["tasks"] = list(existing_map.values())
    result["updated_at"] = datetime.now().isoformat()
    return result