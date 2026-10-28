"""冲突合并 - 本地工程任务与 Notion 同步变更的融合"""
from typing import Dict, List, Optional, Any, Set
from dataclasses import dataclass
from enum import Enum
from copy import deepcopy

from .transform import EngineeringTask, ProductStory, ProductFeature, ProductEpic


class ConflictStrategy(Enum):
    LOCAL_WINS = "local_wins"      # 保留本地改动
    REMOTE_WINS = "remote_wins"    # 接受远端变更
    MERGE = "merge"                # 尝试自动合并
    MANUAL = "manual"              # 需人工处理


@dataclass
class MergeConflict:
    """合并冲突"""
    task_id: str
    field: str
    local_value: Any
    remote_value: Any
    strategy: ConflictStrategy = ConflictStrategy.MANUAL
    resolution: Any = None


@dataclass
class MergeResult:
    """合并结果"""
    merged_tasks: Dict[str, EngineeringTask]
    conflicts: List[MergeConflict]
    created: List[str]
    updated: List[str]
    deleted: List[str]
    obsolete: List[str]


class TaskMerger:
    """任务合并器"""
    
    # 受保护字段（本地改动优先）
    PROTECTED_FIELDS = {
        "evidence",           # 证据链接
        "status",             # 执行状态
        "actual_hours",       # 实际工时
        "assignee",           # 分配人
        "worktree_path",      # worktree 路径
        "pr_url",             # PR 链接
        "local_ci_result",    # 本地 CI 结果
    }
    
    # 可自动同步字段（远端变更优先）
    SYNC_FIELDS = {
        "acceptance",         # 验收标准
        "target_files",       # 目标文件
        "priority",           # 优先级
        "wave",               # 波次
        "lane",               # 泳道
        "parent_story",       # 父 Story
        "parent_feature",     # 父 Feature
        "parent_epic",        # 父 Epic
    }
    
    def __init__(self):
        self.conflicts: List[MergeConflict] = []
    
    def merge(self, 
              local_tasks: Dict[str, EngineeringTask],
              remote_stories: List[ProductStory],
              remote_features: List[ProductFeature],
              remote_epics: List[ProductEpic],
              strategy: ConflictStrategy = ConflictStrategy.MERGE) -> MergeResult:
        """执行合并"""
        self.conflicts = []
        
        # 建立远端索引
        story_map = {s.id: s for s in remote_stories}
        feature_map = {f.id: f for f in remote_features}
        epic_map = {e.id: e for e in remote_epics}
        
        # 反向映射：Story -> Task
        story_to_task = {}
        for task_id, task in local_tasks.items():
            if task.parent_story:
                story_to_task.setdefault(task.parent_story, []).append(task_id)
        
        merged = deepcopy(local_tasks)
        created = []
        updated = []
        deleted = []
        obsolete = []
        
        # 1. 处理远端 Story 对应的任务
        for story_id, story in story_map.items():
            task_ids = story_to_task.get(story_id, [])
            
            if not task_ids:
                # 新 Story：创建任务（由 apply.py 处理，这里只标记）
                continue
            
            for task_id in task_ids:
                if task_id not in merged:
                    continue
                
                task = merged[task_id]
                remote_acceptance = self._build_acceptance(story, feature_map.get(story.feature_id))
                
                # 合并字段
                field_conflicts = self._merge_task_fields(task, story, remote_acceptance, strategy)
                self.conflicts.extend(field_conflicts)
                
                if field_conflicts:
                    updated.append(task_id)
                else:
                    # 检查是否有实质变更
                    if self._has_substantive_change(task, story, remote_acceptance):
                        updated.append(task_id)
        
        # 2. 检测过时任务（本地有但远端无对应 Story）
        remote_story_ids = set(story_map.keys())
        for task_id, task in local_tasks.items():
            if task.parent_story and task.parent_story not in remote_story_ids:
                # 远端已删除对应 Story
                if task.status in ["done", "merged"]:
                    # 已完成/合入的保留，标记 obsolete
                    task.status = "obsolete"
                    obsolete.append(task_id)
                elif task.status in ["in_progress", "blocked"]:
                    # 进行中/阻塞的标记需人工确认
                    self.conflicts.append(MergeConflict(
                        task_id=task_id,
                        field="status",
                        local_value=task.status,
                        remote_value="story_deleted",
                        strategy=ConflictStrategy.MANUAL
                    ))
        
        # 3. 检测孤儿任务（无 parent_story 的纯技术任务保留）
        # 不处理，保留原样
        
        return MergeResult(
            merged_tasks=merged,
            conflicts=self.conflicts,
            created=created,
            updated=updated,
            deleted=deleted,
            obsolete=obsolete
        )
    
    def _build_acceptance(self, story: ProductStory, feature: Optional[ProductFeature]) -> Dict:
        """从 Story 构建 acceptance"""
        return {
            "verify_commands": [
                f"python3 scripts/governance/gov-prototype-verify.py {story.id} --strict",
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
            ],
            "acceptance_text": story.acceptance_text,
            "ac_refs": story.ac_refs,
            "prototype_view": story.prototype_view,
            "prototype_file": story.prototype_file
        }
    
    def _merge_task_fields(self, task: EngineeringTask, story: ProductStory,
                            remote_acceptance: Dict, strategy: ConflictStrategy) -> List[MergeConflict]:
        """合并单个任务字段"""
        conflicts = []
        
        # 同步 acceptance
        if task.acceptance != remote_acceptance:
            if "acceptance" in self.PROTECTED_FIELDS:
                conflicts.append(MergeConflict(
                    task_id=task.id,
                    field="acceptance",
                    local_value=task.acceptance,
                    remote_value=remote_acceptance,
                    strategy=ConflictStrategy.LOCAL_WINS
                ))
            else:
                # 对比关键子字段
                if task.acceptance.get("verify_commands") != remote_acceptance.get("verify_commands"):
                    if strategy == ConflictStrategy.REMOTE_WINS:
                        task.acceptance["verify_commands"] = remote_acceptance["verify_commands"]
                    elif strategy == ConflictStrategy.MERGE:
                        # 合并去重
                        merged = list(dict.fromkeys(
                            task.acceptance.get("verify_commands", []) + 
                            remote_acceptance.get("verify_commands", [])
                        ))
                        task.acceptance["verify_commands"] = merged
                    else:
                        conflicts.append(MergeConflict(
                            task_id=task.id,
                            field="acceptance.verify_commands",
                            local_value=task.acceptance.get("verify_commands"),
                            remote_value=remote_acceptance.get("verify_commands")
                        ))
        
        # 同步 target_files
        remote_files = self._infer_target_files_from_story(story)
        if task.target_files != remote_files:
            if "target_files" in self.PROTECTED_FIELDS:
                conflicts.append(MergeConflict(
                    task_id=task.id,
                    field="target_files",
                    local_value=task.target_files,
                    remote_value=remote_files,
                    strategy=ConflictStrategy.LOCAL_WINS
                ))
            else:
                # 合并去重
                merged = list(dict.fromkeys(task.target_files + remote_files))
                task.target_files = merged
        
        # 同步优先级
        story_priority = getattr(story, 'priority', None)
        if task.priority != "P0" and story_priority:
            if task.priority != story_priority:
                task.priority = story_priority
        
        return conflicts
    
    def _infer_target_files_from_story(self, story: ProductStory) -> List[str]:
        """从 Story 推断目标文件（简化版）"""
        # 实际应用中会用 transform.py 的推断逻辑
        return []
    
    def _has_substantive_change(self, task: EngineeringTask, story: ProductStory, 
                                 remote_acceptance: Dict) -> bool:
        """判断是否有实质变更"""
        # 对比验收文案
        local_text = task.acceptance.get("acceptance_text", "")
        remote_text = remote_acceptance.get("acceptance_text", "")
        return local_text != remote_text
    
    def resolve_conflict(self, conflict: MergeConflict, resolution: Any) -> None:
        """手动解决冲突"""
        conflict.resolution = resolution
        conflict.strategy = ConflictStrategy.MANUAL
    
    def auto_resolve_all(self, strategy: ConflictStrategy) -> int:
        """自动解决所有冲突"""
        resolved = 0
        for conflict in self.conflicts:
            if conflict.strategy == ConflictStrategy.MANUAL:
                conflict.strategy = strategy
                if strategy == ConflictStrategy.LOCAL_WINS:
                    conflict.resolution = conflict.local_value
                elif strategy == ConflictStrategy.REMOTE_WINS:
                    conflict.resolution = conflict.remote_value
                resolved += 1
        return resolved