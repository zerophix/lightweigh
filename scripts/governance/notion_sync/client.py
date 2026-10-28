"""Notion API 客户端 - 支持缓存、重试、限流、OAuth 认证"""
import os
import time
import hashlib
import logging
from pathlib import Path
from typing import Optional, Dict, List, Any, Iterator
from dataclasses import dataclass
import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

from .config import get_config, NotionConfig

logger = logging.getLogger(__name__)


@dataclass
class NotionPage:
    """Notion 页面模型"""
    id: str
    url: str
    title: str
    properties: Dict[str, Any]
    last_edited_time: str
    created_time: str
    parent: Dict[str, Any]
    content_hash: str = ""
    
    def __post_init__(self):
        if not self.content_hash:
            self.content_hash = self._compute_hash()
    
    def _compute_hash(self) -> str:
        """计算页面内容哈希（用于变更检测）"""
        key_content = f"{self.title}|{self.last_edited_time}|{json.dumps(self.properties, sort_keys=True)}"
        return hashlib.sha256(key_content.encode()).hexdigest()[:16]


@dataclass
class NotionDatabase:
    """Notion 数据库模型"""
    id: str
    title: str
    properties: Dict[str, Any]
    url: str


class NotionOAuth:
    """Notion OAuth 认证管理"""
    
    def __init__(self, config: NotionConfig):
        self.config = config
        self.token_file = config.cache_dir / "oauth_token.json"
        self._access_token: Optional[str] = None
        self._refresh_token: Optional[str] = None
        self._expires_at: float = 0
    
    def load_tokens(self) -> bool:
        """从文件加载 tokens"""
        if not self.token_file.exists():
            return False
        try:
            import json
            data = json.loads(self.token_file.read_text())
            self._access_token = data.get("access_token")
            self._refresh_token = data.get("refresh_token")
            self._expires_at = data.get("expires_at", 0)
            return bool(self._access_token)
        except Exception:
            return False
    
    def save_tokens(self):
        """保存 tokens 到文件"""
        import json
        self.token_file.parent.mkdir(parents=True, exist_ok=True)
        data = {
            "access_token": self._access_token,
            "refresh_token": self._refresh_token,
            "expires_at": self._expires_at
        }
        tmp = self.token_file.with_suffix(".tmp")
        tmp.write_text(json.dumps(data))
        tmp.replace(self.token_file)
    
    def is_token_valid(self) -> bool:
        """检查 access token 是否有效（提前 5 分钟刷新）"""
        return self._access_token and time.time() < (self._expires_at - 300)
    
    def get_auth_url(self, state: str = "") -> str:
        """获取 OAuth 授权 URL"""
        params = {
            "client_id": self.config.oauth_client_id,
            "redirect_uri": self.config.oauth_redirect_uri,
            "response_type": "code",
            "owner": "user"
        }
        if state:
            params["state"] = state
        query = "&".join(f"{k}={v}" for k, v in params.items())
        return f"https://api.notion.com/v1/oauth/authorize?{query}"
    
    def exchange_code(self, code: str) -> bool:
        """用授权码换取 access token"""
        resp = requests.post(
            "https://api.notion.com/v1/oauth/token",
            auth=(self.config.oauth_client_id, self.config.oauth_client_secret),
            json={
                "grant_type": "authorization_code",
                "code": code,
                "redirect_uri": self.config.oauth_redirect_uri
            },
            timeout=30
        )
        resp.raise_for_status()
        data = resp.json()
        self._access_token = data["access_token"]
        self._refresh_token = data.get("refresh_token")
        self._expires_at = time.time() + data.get("expires_in", 3600)
        self.save_tokens()
        return True
    
    def refresh_access_token(self) -> bool:
        """刷新 access token"""
        if not self._refresh_token:
            return False
        resp = requests.post(
            "https://api.notion.com/v1/oauth/token",
            auth=(self.config.oauth_client_id, self.config.oauth_client_secret),
            json={
                "grant_type": "refresh_token",
                "refresh_token": self._refresh_token
            },
            timeout=30
        )
        if resp.status_code == 400:
            return False
        resp.raise_for_status()
        data = resp.json()
        self._access_token = data["access_token"]
        self._refresh_token = data.get("refresh_token", self._refresh_token)
        self._expires_at = time.time() + data.get("expires_in", 3600)
        self.save_tokens()
        return True
    
    def get_access_token(self) -> Optional[str]:
        """获取有效的 access token（自动刷新）"""
        if self.is_token_valid():
            return self._access_token
        if self._refresh_token and self.refresh_access_token():
            return self._access_token
        return None


class NotionClient:
    """Notion API 客户端（支持 OAuth 和内部集成 Token）"""
    
    def __init__(self, config: Optional[NotionConfig] = None):
        self.config = config or get_config()
        self.oauth = NotionOAuth(self.config)
        self.session = self._create_session()
        self._page_cache: Dict[str, NotionPage] = {}
        self._database_cache: Dict[str, NotionDatabase] = {}
    
    def _create_session(self) -> requests.Session:
        """创建带重试策略的 Session"""
        session = requests.Session()
        retry_strategy = Retry(
            total=self.config.max_retries,
            backoff_factor=self.config.retry_backoff,
            status_forcelist=[429, 500, 502, 503, 504],
            allowed_methods=["HEAD", "GET", "POST", "PATCH"]
        )
        adapter = HTTPAdapter(max_retries=retry_strategy)
        session.mount("https://", adapter)
        session.mount("http://", adapter)
        
        # 初始 headers（不含 Authorization，由 _ensure_auth 动态注入）
        session.headers.update({
            "Notion-Version": self.config.api_version,
            "Content-Type": "application/json",
        })
        return session
    
    def _ensure_auth(self) -> bool:
        """确保认证有效（优先 OAuth，回退 Token）"""
        # 尝试 OAuth
        if self.config.oauth_client_id and self.config.oauth_client_secret:
            if self.oauth.load_tokens():
                token = self.oauth.get_access_token()
                if token:
                    self.session.headers["Authorization"] = f"Bearer {token}"
                    return True
        
        # 回退内部集成 Token
        if self.config.token:
            self.session.headers["Authorization"] = f"Bearer {self.config.token}"
            return True
        
        raise RuntimeError("未配置认证：需设置 OAuth (client_id/secret) 或内部集成 Token")
    
    def _request(self, method: str, endpoint: str, **kwargs) -> requests.Response:
        """发送请求（带错误处理和自动重新认证）"""
        self._ensure_auth()
        url = f"{self.config.base_url}{endpoint}"
        try:
            resp = self.session.request(method, url, timeout=self.config.request_timeout, **kwargs)
            if resp.status_code == 401:
                # Token 可能过期，强制刷新重试一次
                self.oauth._access_token = None
                self._ensure_auth()
                resp = self.session.request(method, url, timeout=self.config.request_timeout, **kwargs)
            resp.raise_for_status()
            return resp
        except requests.exceptions.HTTPError as e:
            if e.response.status_code == 401:
                raise RuntimeError("Notion 认证失败：OAuth token 无效或已过期，需重新授权")
            elif e.response.status_code == 403:
                raise RuntimeError("Notion 权限不足：集成未被授权访问该资源")
            elif e.response.status_code == 404:
                raise RuntimeError(f"资源不存在: {endpoint}")
            raise
    
    # ========== 数据库查询 ==========
    
    def query_database(self, database_id: str, 
                       filter_: Optional[Dict] = None,
                       sorts: Optional[List[Dict]] = None,
                       start_cursor: Optional[str] = None,
                       page_size: Optional[int] = None) -> Dict[str, Any]:
        """查询数据库"""
        payload = {}
        if filter_:
            payload["filter"] = filter_
        if sorts:
            payload["sorts"] = sorts
        if start_cursor:
            payload["start_cursor"] = start_cursor
        if page_size:
            payload["page_size"] = min(page_size, 100)
        
        return self._request("POST", f"/databases/{database_id}/query", json=payload).json()
    
    def iter_database(self, database_id: str,
                      filter_: Optional[Dict] = None,
                      sorts: Optional[List[Dict]] = None,
                      page_size: int = 100) -> Iterator[Dict[str, Any]]:
        """分页迭代数据库所有结果"""
        has_more = True
        start_cursor = None
        
        while has_more:
            result = self.query_database(database_id, filter_, sorts, start_cursor, page_size)
            for item in result.get("results", []):
                yield item
            
            has_more = result.get("has_more", False)
            start_cursor = result.get("next_cursor")
            
            if has_more:
                time.sleep(0.1)
    
    # ========== 页面操作 ==========
    
    def get_page(self, page_id: str) -> NotionPage:
        """获取页面详情（含属性）"""
        if page_id in self._page_cache:
            return self._page_cache[page_id]
        
        data = self._request("GET", f"/pages/{page_id}").json()
        page = self._parse_page(data)
        self._page_cache[page_id] = page
        return page
    
    def get_page_content(self, page_id: str) -> List[Dict]:
        """获取页面块内容（用于解析原型 HTML）"""
        blocks = []
        has_more = True
        start_cursor = None
        
        while has_more:
            params = {}
            if start_cursor:
                params["start_cursor"] = start_cursor
            resp = self._request("GET", f"/blocks/{page_id}/children", params=params).json()
            blocks.extend(resp.get("results", []))
            has_more = resp.get("has_more", False)
            start_cursor = resp.get("next_cursor")
        
        return blocks
    
    def update_page(self, page_id: str, properties: Dict) -> NotionPage:
        """更新页面属性（用于状态回写）"""
        self._request("PATCH", f"/pages/{page_id}", json={"properties": properties})
        self._page_cache.pop(page_id, None)
        return self.get_page(page_id)
    
    # ========== 数据库元数据 ==========
    
    def get_database(self, database_id: str) -> NotionDatabase:
        """获取数据库元数据"""
        if database_id in self._database_cache:
            return self._database_cache[database_id]
        
        data = self._request("GET", f"/databases/{database_id}").json()
        db = NotionDatabase(
            id=data["id"],
            title=self._extract_title(data.get("title", [])),
            properties=data.get("properties", {}),
            url=data.get("url", "")
        )
        self._database_cache[database_id] = db
        return db
    
    # ========== 搜索 ==========
    
    def search(self, query: str, filter_: Optional[Dict] = None) -> List[Dict]:
        """搜索页面/数据库"""
        payload = {"query": query}
        if filter_:
            payload["filter"] = filter_
        return self._request("POST", "/search", json=payload).json().get("results", [])
    
    # ========== 解析辅助 ==========
    
    def _parse_page(self, data: Dict) -> NotionPage:
        props = {}
        for key, value in data.get("properties", {}).items():
            props[key] = self._extract_property_value(value)
        
        return NotionPage(
            id=data["id"],
            url=data.get("url", ""),
            title=self._extract_title(data.get("properties", {})),
            properties=props,
            last_edited_time=data.get("last_edited_time", ""),
            created_time=data.get("created_time", ""),
            parent=data.get("parent", {})
        )
    
    def _extract_title(self, properties: Dict) -> str:
        """从属性中提取标题"""
        for value in properties.values():
            if value.get("type") == "title":
                return "".join([t.get("plain_text", "") for t in value.get("title", [])])
        return ""
    
    def _extract_property_value(self, prop: Dict) -> Any:
        """提取属性值（简化版）"""
        prop_type = prop.get("type")
        value = prop.get(prop_type)
        
        if value is None:
            return None
        
        if prop_type == "title":
            return "".join([t.get("plain_text", "") for t in value])
        elif prop_type == "rich_text":
            return "".join([t.get("plain_text", "") for t in value])
        elif prop_type == "select":
            return value.get("name") if value else None
        elif prop_type == "multi_select":
            return [v.get("name") for v in value]
        elif prop_type == "date":
            return value.get("start") if value else None
        elif prop_type == "checkbox":
            return value
        elif prop_type == "number":
            return value
        elif prop_type == "url":
            return value
        elif prop_type == "email":
            return value
        elif prop_type == "phone_number":
            return value
        elif prop_type == "relation":
            return [v.get("id") for v in value]
        elif prop_type == "rollup":
            return value
        else:
            return value
    
    def clear_cache(self):
        """清空缓存"""
        self._page_cache.clear()
        self._database_cache.clear()


# 需要导入 json
import json