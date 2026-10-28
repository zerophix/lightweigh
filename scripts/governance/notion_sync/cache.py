"""本地缓存 - 存储内容哈希、最后编辑时间、版本号"""
import json
import time
import os
from pathlib import Path
from typing import Dict, Optional, Any
from dataclasses import dataclass, asdict
from threading import Lock

from .config import get_config


@dataclass
class CacheEntry:
    """缓存条目"""
    notion_id: str
    content_hash: str
    last_edited_time: str
    version: int = 0
    cached_at: float = 0
    metadata: Dict[str, Any] = None
    
    def __post_init__(self):
        if self.cached_at == 0:
            self.cached_at = time.time()
        if self.metadata is None:
            self.metadata = {}


class NotionCache:
    """Notion 本地缓存管理"""
    
    def __init__(self, cache_dir: Optional[Path] = None, ttl_hours: int = 168):
        self.config = get_config()
        self.cache_dir = cache_dir or self.config.cache_dir
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self.cache_file = self.cache_dir / "pages_cache.json"
        self.mapping_file = self.cache_dir / "id_mapping.json"
        self.ttl_seconds = ttl_hours * 3600
        self._lock = Lock()
        self._cache: Dict[str, CacheEntry] = {}
        self._mapping: Dict[str, str] = {}  # 仓库任务ID -> Notion页面ID
        self._load()
    
    def _load(self):
        """加载缓存文件"""
        if self.cache_file.exists():
            try:
                data = json.loads(self.cache_file.read_text())
                for k, v in data.items():
                    self._cache[k] = CacheEntry(**v)
            except Exception as e:
                print(f"警告: 缓存加载失败: {e}")
        
        if self.mapping_file.exists():
            try:
                self._mapping = json.loads(self.mapping_file.read_text())
            except Exception:
                pass
    
    def _save(self):
        """保存缓存文件（原子写入）"""
        with self._lock:
            tmp_file = self.cache_file.with_suffix(".tmp")
            data = {k: asdict(v) for k, v in self._cache.items()}
            tmp_file.write_text(json.dumps(data, ensure_ascii=False, indent=2))
            tmp_file.replace(self.cache_file)
            
            tmp_map = self.mapping_file.with_suffix(".tmp")
            tmp_map.write_text(json.dumps(self._mapping, ensure_ascii=False, indent=2))
            tmp_map.replace(self.mapping_file)
    
    def get(self, notion_id: str) -> Optional[CacheEntry]:
        """获取缓存条目"""
        entry = self._cache.get(notion_id)
        if entry and (time.time() - entry.cached_at) > self.ttl_seconds:
            return None  # 过期
        return entry
    
    def set(self, notion_id: str, content_hash: str, last_edited_time: str, 
            version: int = 0, metadata: Dict = None):
        """设置缓存"""
        entry = CacheEntry(
            notion_id=notion_id,
            content_hash=content_hash,
            last_edited_time=last_edited_time,
            version=version,
            metadata=metadata or {}
        )
        self._cache[notion_id] = entry
        self._save()
    
    def update_hash(self, notion_id: str, content_hash: str, last_edited_time: str):
        """更新哈希（版本号+1）"""
        entry = self._cache.get(notion_id)
        if entry:
            entry.content_hash = content_hash
            entry.last_edited_time = last_edited_time
            entry.version += 1
            entry.cached_at = time.time()
        else:
            entry = CacheEntry(
                notion_id=notion_id,
                content_hash=content_hash,
                last_edited_time=last_edited_time,
                version=1
            )
            self._cache[notion_id] = entry
        self._save()
    
    def delete(self, notion_id: str):
        """删除缓存"""
        self._cache.pop(notion_id, None)
        self._save()
    
    def has_changed(self, notion_id: str, current_hash: str) -> bool:
        """检测是否变更"""
        entry = self.get(notion_id)
        if not entry:
            return True  # 无缓存视为新增
        return entry.content_hash != current_hash
    
    # ========== 映射管理 ==========
    
    def set_mapping(self, task_id: str, notion_id: str):
        """设置任务到 Notion 页面的映射"""
        self._mapping[task_id] = notion_id
        self._save()
    
    def get_mapping(self, task_id: str) -> Optional[str]:
        """获取任务对应的 Notion 页面ID"""
        return self._mapping.get(task_id)
    
    def get_reverse_mapping(self, notion_id: str) -> Optional[str]:
        """反向查找：Notion 页面 -> 任务ID"""
        for task_id, nid in self._mapping.items():
            if nid == notion_id:
                return task_id
        return None
    
    def remove_mapping(self, task_id: str):
        """移除映射"""
        self._mapping.pop(task_id, None)
        self._save()
    
    def get_all_mappings(self) -> Dict[str, str]:
        """获取所有映射"""
        return self._mapping.copy()
    
    # ========== 维护 ==========
    
    def cleanup_expired(self) -> int:
        """清理过期条目"""
        now = time.time()
        expired = [
            k for k, v in self._cache.items() 
            if (now - v.cached_at) > self.ttl_seconds
        ]
        for k in expired:
            del self._cache[k]
        if expired:
            self._save()
        return len(expired)
    
    def stats(self) -> Dict:
        """缓存统计"""
        return {
            "total_entries": len(self._cache),
            "total_mappings": len(self._mapping),
            "cache_file": str(self.cache_file),
            "mapping_file": str(self.mapping_file)
        }