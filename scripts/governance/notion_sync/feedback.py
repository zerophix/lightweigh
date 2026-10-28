"""反馈管道 - 仓库任务状态/证据回写 Notion"""
import json
from pathlib import Path
from typing import Dict, List, Optional, Any
from datetime import datetime

from .client import NotionClient
from .cache import NotionCache
from .config import get_config


class FeedbackPusher:
    """状态回写器"""
    
    # 状态映射：仓库状态 -> Notion 状态选项
    STATUS_MAP = {
        "ready": "就绪",
        "in_progress": "进行中",
        "blocked": "阻塞",
        "done": "完成",
        "merged": "已合入",
        "obsolete": "废弃",
        "cancelled": "取消"
    }
    
    def __init__(self, client: NotionClient, cache: NotionCache):
        self.client = client
        self.cache = cache
        self.config = get_config()
    
    def push_task_status(self, task_id: str, status: str, 
                          evidence: Optional[Dict] = None) -> bool:
        """推送任务状态到 Notion"""
        notion_id = self.cache.get_mapping(task_id)
        if not notion_id:
            # 纯技术任务无 Notion 对应页面
            return True
        
        props = {
            "状态": {"select": {"name": self.STATUS_MAP.get(status, status)}},
            "最后同步": {"date": {"start": datetime.now().isoformat()}}
        }
        
        if evidence:
            if "local_ci_report" in evidence:
                props["CI报告"] = {"url": evidence["local_ci_report"]}
            if "pr_url" in evidence:
                props["PR链接"] = {"url": evidence["pr_url"]}
            if "prototype_verify" in evidence:
                props["原型验收"] = {"url": evidence["prototype_verify"]}
        
        try:
            self.client.update_page(notion_id, properties=props)
            return True
        except Exception as e:
            print(f"回写失败 {task_id}: {e}")
            return False
    
    def push_blocker(self, task_id: str, reason: str, log_url: str = "") -> bool:
        """推送阻塞信息"""
        notion_id = self.cache.get_mapping(task_id)
        if not notion_id:
            return True
        
        props = {
            "阻塞原因": {"rich_text": [{"text": {"content": reason}}]},
            "状态": {"select": {"name": "阻塞"}},
            "最后同步": {"date": {"start": datetime.now().isoformat()}}
        }
        
        if log_url:
            props["阻塞日志"] = {"url": log_url}
        
        try:
            self.client.update_page(notion_id, properties=props)
            return True
        except Exception as e:
            print(f"推送阻塞失败 {task_id}: {e}")
            return False
    
    def push_feature_progress(self, feature_id: str, 
                               completed: int, total: int) -> bool:
        """推送 Feature 进度"""
        notion_id = self.cache.get_mapping(feature_id)
        if not notion_id:
            return True
        
        progress = f"{completed}/{total}" if total > 0 else "0/0"
        pct = int(completed * 100 / total) if total > 0 else 0
        
        props = {
            "进度": {"rich_text": [{"text": {"content": progress}}]},
            "完成度": {"number": pct},
            "最后同步": {"date": {"start": datetime.now().isoformat()}}
        }
        
        try:
            self.client.update_page(notion_id, properties=props)
            return True
        except Exception as e:
            print(f"推送进度失败 {feature_id}: {e}")
            return False
    
    def push_epic_progress(self, epic_id: str,
                            completed_features: int, total_features: int) -> bool:
        """推送 Epic 进度"""
        notion_id = self.cache.get_mapping(epic_id)
        if not notion_id:
            return True
        
        pct = int(completed_features * 100 / total_features) if total_features > 0 else 0
        
        props = {
            "完成度": {"number": pct},
            "最后同步": {"date": {"start": datetime.now().isoformat()}}
        }
        
        # 如果 100% 完成，更新状态
        if pct == 100:
            props["状态"] = {"select": {"name": "完成"}}
        
        try:
            self.client.update_page(notion_id, properties=props)
            return True
        except Exception as e:
            print(f"推送 Epic 进度失败 {epic_id}: {e}")
            return False
    
    def sync_all_status(self, tasks: Dict[str, Any]) -> Dict:
        """批量同步所有任务状态"""
        results = {"success": 0, "failed": 0, "skipped": 0}
        
        for task_id, task in tasks.items():
            notion_id = self.cache.get_mapping(task_id)
            if not notion_id:
                results["skipped"] += 1
                continue
            
            status = task.get("status", "ready")
            evidence = task.get("evidence", {})
            
            if self.push_task_status(task_id, status, evidence):
                results["success"] += 1
            else:
                results["failed"] += 1
        
        return results


class EvidenceCollector:
    """证据收集器 - 从本地收集 CI 报告、测试结果等"""
    
    def __init__(self, repo_root: Path):
        self.repo_root = repo_root
    
    def collect_task_evidence(self, task_id: str) -> Dict:
        """收集单个任务的证据"""
        evidence = {}
        
        # 1. Local CI 报告
        ci_report = self.repo_root / ".cache" / "local-ci-last.json"
        if ci_report.exists():
            evidence["local_ci_report"] = f"file://{ci_report}"
            try:
                data = json.loads(ci_report.read_text())
                evidence["ci_result"] = data.get("result")
                evidence["ci_lane"] = data.get("lane")
                evidence["ci_duration"] = data.get("duration_seconds")
            except Exception:
                pass
        
        # 2. Prototype Verify 报告
        proto_report = self.repo_root / ".cache" / "prototype-verify" / f"{task_id}.json"
        if proto_report.exists():
            evidence["prototype_verify"] = f"file://{proto_report}"
        
        # 3. 单测报告
        test_report_dir = self.repo_root / "app" / "build" / "reports" / "tests"
        if test_report_dir.exists():
            evidence["test_report"] = f"file://{test_report_dir}"
        
        # 4. 构建报告
        build_report = self.repo_root / "app" / "build" / "reports" / "lint-results.html"
        if build_report.exists():
            evidence["lint_report"] = f"file://{build_report}"
        
        return evidence
    
    def collect_pr_info(self, branch: str) -> Dict:
        """收集 PR 信息（需 GitHub CLI）"""
        import subprocess
        
        info = {}
        try:
            # 获取 PR 编号
            result = subprocess.run(
                ["gh", "pr", "list", "--head", branch, "--json", "number,url,state"],
                capture_output=True, text=True, cwd=self.repo_root
            )
            if result.returncode == 0:
                prs = json.loads(result.stdout)
                if prs:
                    info["pr_number"] = prs[0]["number"]
                    info["pr_url"] = prs[0]["url"]
                    info["pr_state"] = prs[0]["state"]
        except Exception:
            pass
        
        return info


# 便捷函数：集成到现有脚本
def push_task_done(task_id: str, repo_root: Path = None):
    """任务完成时的回写入口"""
    from .config import get_config
    from .client import NotionClient
    from .cache import NotionCache
    
    config = get_config()
    client = NotionClient(config)
    cache = NotionCache(config.cache_dir)
    pusher = FeedbackPusher(client, cache)
    
    # 收集证据
    collector = EvidenceCollector(repo_root or Path.cwd())
    evidence = collector.collect_task_evidence(task_id)
    evidence.update(collector.collect_pr_info(""))
    
    return pusher.push_task_status(task_id, "done", evidence)


def push_task_blocked(task_id: str, reason: str, log_url: str = ""):
    """任务阻塞时的回写入口"""
    from .config import get_config
    from .client import NotionClient
    from .cache import NotionCache
    
    config = get_config()
    client = NotionClient(config)
    cache = NotionCache(config.cache_dir)
    pusher = FeedbackPusher(client, cache)
    
    return pusher.push_blocker(task_id, reason, log_url)