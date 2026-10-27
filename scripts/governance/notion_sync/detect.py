"""变更检测 - 对比 Notion 远端与本地缓存"""
from typing import List, Dict, Optional, Any, Set
from dataclasses import dataclass
from enum import Enum
from datetime import datetime, timedelta

from .client import NotionClient, NotionPage
from .cache import NotionCache, CacheEntry
from .config import get_config


class ChangeType(Enum):
    CREATED = "created"
    UPDATED = "updated"
    DELETED = "deleted"
    METADATA_CHANGED = "metadata_changed"
    PROTOTYPE_CHANGED = "prototype_changed"


@dataclass
class ChangeEvent:
    """变更事件"""
    type: ChangeType
    notion_id: str
    database: str  # epics/features/stories/decisions
    page: Optional[NotionPage] = None
    old_entry: Optional[CacheEntry] = None
    diff: Dict = None
    
    def __post_init__(self):
        if self.diff is None:
            self.diff = {}


class ChangeDetector:
    """变更检测器"""
    
    def __init__(self, client: NotionClient, cache: NotionCache):
        self.client = client
        self.cache = cache
        self.config = get_config()
    
    def detect_all(self, lookback_hours: Optional[int] = None) -> List[ChangeEvent]:
        """检测所有数据库的变更"""
        lookback = lookback_hours or self.config.lookback_hours
        since = datetime.now() - timedelta(hours=lookback)
        since_iso = since.isoformat()
        
        all_events = []
        
        # 按顺序检测：Epic -> Feature -> Story -> Decision
        for db_name in ["epics", "features", "stories", "decisions"]:
            db_id = self.config.get_database_id(db_name)
            if not db_id:
                continue
            
            events = self._detect_database(db_name, db_id, since_iso)
            all_events.extend(events)
        
        return all_events
    
    def _detect_database(self, db_name: str, db_id: str, since_iso: str) -> List[ChangeEvent]:
        """检测单个数据库变更"""
        events = []
        seen_ids: Set[str] = set()
        
        # 构建过滤器：最后编辑时间在 since 之后
        filter_ = {
            "timestamp": "last_edited_time",
            "last_edited_time": {"on_or_after": since_iso}
        }
        
        # 获取远端页面
        remote_pages = {}
        try:
            for item in self.client.iter_database(db_id, filter_=filter_):
                page = self._item_to_page(item, db_name)
                remote_pages[page.id] = page
                seen_ids.add(page.id)
        except Exception as e:
            print(f"警告: 查询数据库 {db_name} 失败: {e}")
            return events
        
        # 对比缓存
        for notion_id, page in remote_pages.items():
            entry = self.cache.get(notion_id)
            
            if not entry:
                # 新增
                events.append(ChangeEvent(
                    type=ChangeType.CREATED,
                    notion_id=notion_id,
                    database=db_name,
                    page=page
                ))
            elif entry.content_hash != page.content_hash:
                # 内容变更
                diff = self._compute_diff(entry, page)
                change_type = ChangeType.UPDATED
                if db_name == "stories" and self._is_prototype_change(diff):
                    change_type = ChangeType.PROTOTYPE_CHANGED
                events.append(ChangeEvent(
                    type=change_type,
                    notion_id=notion_id,
                    database=db_name,
                    page=page,
                    old_entry=entry,
                    diff=diff
                ))
            elif entry.last_edited_time != page.last_edited_time:
                # 仅元数据变更
                events.append(ChangeEvent(
                    type=ChangeType.METADATA_CHANGED,
                    notion_id=notion_id,
                    database=db_name,
                    page=page,
                    old_entry=entry
                ))
        
        # 检测删除（缓存中有但远端查不到的）
        # 注意：只检测在 lookback 窗口内曾经存在的
        cached_ids = {k for k, v in self.cache._cache.items() 
                      if v.cached_at > (datetime.now() - timedelta(hours=lookback)).timestamp()}
        for cached_id in cached_ids - seen_ids:
            entry = self.cache.get(cached_id)
            if entry:
                events.append(ChangeEvent(
                    type=ChangeType.DELETED,
                    notion_id=cached_id,
                    database=db_name,
                    old_entry=entry
                ))
        
        return events
    
    def _item_to_page(self, item: Dict, db_name: str) -> NotionPage:
        """将数据库项转为 NotionPage"""
        return self.client._parse_page(item)
    
    def _compute_diff(self, old: CacheEntry, new: NotionPage) -> Dict:
        """计算差异（简化版：返回变更的属性名）"""
        # 这里可以做更细粒度的 diff
        return {
            "old_hash": old.content_hash,
            "new_hash": new.content_hash,
            "old_edited": old.last_edited_time,
            "new_edited": new.last_edited_time,
            "version_delta": new.content_hash != old.content_hash
        }
    
    def _is_prototype_change(self, diff: Dict) -> bool:
        """判断是否为原型相关变更（Story 专用）"""
        # 可以根据属性名判断
        return True  # 简化：Story 任何内容变更都视为原型变更


class IncrementalSync:
    """增量同步协调器"""
    
    def __init__(self, client: NotionClient, cache: NotionCache):
        self.detector = ChangeDetector(client, cache)
    
    def run(self, lookback_hours: Optional[int] = None) -> Dict:
        """执行增量同步，返回变更摘要"""
        events = self.detector.detect_all(lookback_hours)
        
        summary = {
            "total": len(events),
            "created": 0,
            "updated": 0,
            "deleted": 0,
            "metadata": 0,
            "prototype": 0,
            "by_database": {},
            "events": events
        }
        
        for event in events:
            summary[event.type.value] += 1
            db = event.database
            if db not in summary["by_database"]:
                summary["by_database"][db] = {"created": 0, "updated": 0, "deleted": 0}
            summary["by_database"][db][event.type.value] += 1
        
        return summary