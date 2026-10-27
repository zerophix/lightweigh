"""Notion 同步配置管理"""
import os
from pathlib import Path
from dataclasses import dataclass, field
from typing import Optional, Dict, List
import json


@dataclass
class NotionConfig:
    """Notion API 配置"""
    # 内部集成 Token（备选认证方式）
    token: str = ""
    workspace_id: str = ""
    
    # OAuth 认证（推荐方式）
    oauth_client_id: str = ""
    oauth_client_secret: str = ""
    oauth_redirect_uri: str = "http://localhost:8080/callback"
    
    api_version: str = "2022-06-28"
    base_url: str = "https://api.notion.com/v1"
    request_timeout: int = 30
    max_retries: int = 3
    retry_backoff: float = 1.0

    # 数据库 ID 映射 (从环境变量或配置文件读取)
    databases: Dict[str, str] = field(default_factory=lambda: {
        "epics": "",           # Product Epics
        "features": "",        # Product Features
        "stories": "",         # Product Stories
        "decisions": "",       # Decisions
        "milestones": "",      # Milestones
    })

    # 同步配置
    poll_interval_minutes: int = 15
    lookback_hours: int = 24   # 轮询时回看多少小时内的变更
    batch_size: int = 100      # 每次查询页面数

    # 原型文件映射
    prototype_base_path: str = "design/prototypes/interactive"
    prototype_index_url: str = "design/prototypes/interactive/index.html"

    # 缓存配置
    cache_dir: Path = Path(".cache/notion")
    cache_ttl_hours: int = 168  # 7天

    # 输出路径
    layer1_output: Path = Path(".ai/tasks.layer1.json")
    tasks_output: Path = Path(".ai/tasks.json")

    # 映射存储
    mapping_file: Path = Path(".cache/notion_mapping.json")

    @classmethod
    def from_env(cls) -> "NotionConfig":
        """从环境变量加载配置"""
        config = cls()
        config.token = os.getenv("NOTION_TOKEN", "")
        config.workspace_id = os.getenv("NOTION_WORKSPACE_ID", "")
        config.oauth_client_id = os.getenv("NOTION_OAUTH_CLIENT_ID", "")
        config.oauth_client_secret = os.getenv("NOTION_OAUTH_CLIENT_SECRET", "")
        config.oauth_redirect_uri = os.getenv("NOTION_OAUTH_REDIRECT_URI", "http://localhost:8080/callback")
        
        # 数据库 ID 从环境变量读取
        for key in config.databases:
            env_key = f"NOTION_DB_{key.upper()}"
            config.databases[key] = os.getenv(env_key, "")
        
        # 可选配置
        if interval := os.getenv("NOTION_POLL_INTERVAL"):
            config.poll_interval_minutes = int(interval)
        if lookback := os.getenv("NOTION_LOOKBACK_HOURS"):
            config.lookback_hours = int(lookback)
        if cache_dir := os.getenv("NOTION_CACHE_DIR"):
            config.cache_dir = Path(cache_dir)
        
        return config

    @classmethod
    def from_file(cls, path: Path) -> "NotionConfig":
        """从 JSON 文件加载配置"""
        if not path.exists():
            return cls.from_env()
        data = json.loads(path.read_text())
        config = cls()
        for key, value in data.items():
            if hasattr(config, key):
                if key in ("cache_dir", "layer1_output", "tasks_output", "mapping_file"):
                    setattr(config, key, Path(value))
                else:
                    setattr(config, key, value)
        # 环境变量覆盖
        env_config = cls.from_env()
        for key in ("token", "workspace_id"):
            if getattr(env_config, key):
                setattr(config, key, getattr(env_config, key))
        return config

    def save(self, path: Path) -> None:
        """保存配置到文件（不保存 token 和 OAuth secrets）"""
        data = {}
        for key, value in self.__dict__.items():
            if key in ("token", "oauth_client_secret"):
                continue
            if isinstance(value, Path):
                data[key] = str(value)
            elif isinstance(value, dict):
                data[key] = value
            else:
                data[key] = value
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2))

    def validate(self) -> List[str]:
        """验证配置完整性"""
        errors = []
        has_oauth = bool(self.oauth_client_id and self.oauth_client_secret)
        has_token = bool(self.token)
        if not has_oauth and not has_token:
            errors.append("未配置认证：需设置 OAuth (NOTION_OAUTH_CLIENT_ID/SECRET) 或内部集成 Token (NOTION_TOKEN)")
        if not self.workspace_id:
            errors.append("NOTION_WORKSPACE_ID 未设置")
        for name, db_id in self.databases.items():
            if not db_id:
                errors.append(f"数据库 {name} (NOTION_DB_{name.upper()}) 未设置")
        return errors

    def get_database_id(self, name: str) -> str:
        """获取数据库 ID"""
        return self.databases.get(name, "")


# 全局配置实例
_config: Optional[NotionConfig] = None


def get_config() -> NotionConfig:
    """获取全局配置实例"""
    global _config
    if _config is None:
        _config = NotionConfig.from_env()
    return _config


def set_config(config: NotionConfig) -> None:
    """设置全局配置"""
    global _config
    _config = config