"""应用同步结果 - 写入 .ai/tasks.layer1.json 和 .ai/tasks.json"""
import json
import shutil
from pathlib import Path
from typing import Dict, List, Optional, Any
from datetime import datetime

from .config import get_config
from .transform import (ProductEpic, ProductFeature, ProductStory, 
                         EngineeringTask, to_layer1_json, to_layer2_json)
from .merge import TaskMerger, MergeResult


class SyncApplier:
    """同步结果应用器"""
    
    def __init__(self, dry_run: bool = False):
        self.config = get_config()
        self.dry_run = dry_run
        self.merger = TaskMerger()
    
    def apply(self, 
              epics: List[ProductEpic],
              features: List[ProductFeature],
              stories: List[ProductStory],
              engineering_tasks: List[EngineeringTask],
              merge_result: MergeResult) -> Dict:
        """应用同步结果"""
        
        # 1. 写入 Layer 1 产品任务
        layer1_result = self._write_layer1(epics, features, stories)
        
        # 2. 合并并写入 Layer 2 工程任务
        layer2_result = self._write_layer2(engineering_tasks, merge_result)
        
        # 3. 生成同步报告
        report = self._generate_report(layer1_result, layer2_result, merge_result)
        
        return report
    
    def _write_layer1(self, epics, features, stories) -> Dict:
        """写入 .ai/tasks.layer1.json"""
        layer1_data = to_layer1_json(epics, features, stories)
        
        if not self.dry_run:
            # 备份
            self._backup(self.config.layer1_output)
            # 原子写入
            tmp = self.config.layer1_output.with_suffix(".tmp")
            tmp.write_text(json.dumps(layer1_data, ensure_ascii=False, indent=2))
            tmp.replace(self.config.layer1_output)
        
        return {
            "epics": len(epics),
            "features": len(features),
            "stories": len(stories),
            "file": str(self.config.layer1_output)
        }
    
    def _write_layer2(self, engineering_tasks: List[EngineeringTask], 
                       merge_result: MergeResult) -> Dict:
        """写入 .ai/tasks.json（合并现有）"""
        # 读取现有 tasks.json
        existing = {}
        if self.config.tasks_output.exists():
            existing = json.loads(self.config.tasks_output.read_text())
        
        # 转换为新格式
        merged_data = to_layer2_json(engineering_tasks, existing)
        
        # 合并 merge_result 中的更新
        for task_id in merge_result.updated:
            if task_id in merged_data.get("tasks", {}):
                task = merged_data["tasks"][task_id]
                task["updated_at"] = datetime.now().isoformat()
        
        # 标记 obsolete
        for task_id in merge_result.obsolete:
            if task_id in merged_data.get("tasks", {}):
                merged_data["tasks"][task_id]["status"] = "obsolete"
                merged_data["tasks"][task_id]["updated_at"] = datetime.now().isoformat()
        
        if not self.dry_run:
            self._backup(self.config.tasks_output)
            tmp = self.config.tasks_output.with_suffix(".tmp")
            tmp.write_text(json.dumps(merged_data, ensure_ascii=False, indent=2))
            tmp.replace(self.config.tasks_output)
        
        return {
            "total_tasks": len(merged_data.get("tasks", [])),
            "updated": len(merge_result.updated),
            "obsolete": len(merge_result.obsolete),
            "conflicts": len(merge_result.conflicts),
            "file": str(self.config.tasks_output)
        }
    
    def _backup(self, file_path: Path):
        """备份文件"""
        if file_path.exists():
            backup_dir = file_path.parent / "backups"
            backup_dir.mkdir(exist_ok=True)
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            backup_name = f"{file_path.stem}_{timestamp}{file_path.suffix}"
            shutil.copy2(file_path, backup_dir / backup_name)
    
    def _generate_report(self, layer1_result: Dict, layer2_result: Dict, 
                          merge_result: MergeResult) -> Dict:
        """生成同步报告"""
        return {
            "timestamp": datetime.now().isoformat(),
            "dry_run": self.dry_run,
            "layer1": layer1_result,
            "layer2": layer2_result,
            "merge": {
                "created": merge_result.created,
                "updated": merge_result.updated,
                "deleted": merge_result.deleted,
                "obsolete": merge_result.obsolete,
                "conflicts": [
                    {
                        "task_id": c.task_id,
                        "field": c.field,
                        "strategy": c.strategy.value,
                        "resolution": str(c.resolution) if c.resolution else None
                    }
                    for c in merge_result.conflicts
                ]
            }
        }
    
    def print_report(self, report: Dict):
        """打印同步报告"""
        print("\n=== Notion 同步报告 ===")
        print(f"时间: {report['timestamp']}")
        print(f"模式: {'干运行' if report['dry_run'] else '正式'}")
        print(f"\nLayer 1 (产品任务):")
        print(f"  Epics: {report['layer1']['epics']}")
        print(f"  Features: {report['layer1']['features']}")
        print(f"  Stories: {report['layer1']['stories']}")
        print(f"\nLayer 2 (工程任务):")
        print(f"  总任务: {report['layer2']['total_tasks']}")
        print(f"  更新: {report['layer2']['updated']}")
        print(f"  废弃: {report['layer2']['obsolete']}")
        print(f"  冲突: {report['layer2']['conflicts']}")
        
        if report['merge']['conflicts']:
            print(f"\n⚠ 冲突详情:")
            for c in report['merge']['conflicts']:
                print(f"  - {c['task_id']}.{c['field']}: {c['strategy']}")


class IncrementalApplier:
    """增量应用器 - 只处理变更的任务"""
    
    def __init__(self):
        self.config = get_config()
    
    def apply_changes(self, change_events: List, 
                       local_tasks: Dict) -> Dict:
        """仅应用变更事件影响的任务"""
        # TODO: 实现增量应用逻辑
        pass