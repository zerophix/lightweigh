"""原型 HTML 解析器 - 从交互原型提取 View 列表、文案、交互"""
import re
import json
from pathlib import Path
from typing import List, Dict, Optional, Any
from dataclasses import dataclass, asdict
from bs4 import BeautifulSoup

from .config import get_config


@dataclass
class PrototypeView:
    """原型 View 模型"""
    view_id: str           # 如 v-phone, v-verify
    title: str             # 页面标题
    file: str              # 来源文件
    html_content: str      # 该 view 的 HTML 片段
    text_content: str      # 纯文本内容（用于文案验收）
    elements: List[Dict]   # 关键元素（按钮、输入框、文本等）
    metadata: Dict         # 其它元数据


@dataclass
class PrototypeFile:
    """原型文件模型"""
    file_path: str
    file_name: str
    title: str
    views: List[PrototypeView]
    global_styles: Dict
    components: List[Dict]


class PrototypeParser:
    """交互原型 HTML 解析器"""
    
    def __init__(self, prototype_dir: Optional[Path] = None):
        self.config = get_config()
        self.prototype_dir = prototype_dir or Path(self.config.prototype_base_path)
        self._cache: Dict[str, PrototypeFile] = {}
    
    def parse_all(self) -> Dict[str, PrototypeFile]:
        """解析目录下所有原型文件"""
        result = {}
        for html_file in self.prototype_dir.glob("*.html"):
            try:
                proto = self.parse_file(html_file)
                result[proto.file_name] = proto
            except Exception as e:
                print(f"警告: 解析 {html_file} 失败: {e}")
        return result
    
    def parse_file(self, file_path: Path | str) -> PrototypeFile:
        """解析单个原型文件"""
        file_path = Path(file_path)
        if str(file_path) in self._cache:
            return self._cache[str(file_path)]
        
        html = file_path.read_text(encoding="utf-8")
        soup = BeautifulSoup(html, "html.parser")
        
        # 提取页面标题
        title_tag = soup.find("title")
        title = title_tag.get_text(strip=True) if title_tag else file_path.stem
        
        # 查找所有 view（假设用 data-view 或 id 标识）
        views = self._extract_views(soup, file_path)
        
        # 提取全局样式/组件引用
        global_styles = self._extract_styles(soup)
        components = self._extract_components(soup)
        
        proto = PrototypeFile(
            file_path=str(file_path),
            file_name=file_path.name,
            title=title,
            views=views,
            global_styles=global_styles,
            components=components
        )
        
        self._cache[str(file_path)] = proto
        return proto
    
    def _extract_views(self, soup: BeautifulSoup, file_path: Path) -> List[PrototypeView]:
        """提取所有 View"""
        views = []
        
        # 策略1：查找 data-view 属性
        view_containers = soup.find_all(attrs={"data-view": True})
        
        # 策略2：查找 id 以 v- 开头的容器
        if not view_containers:
            view_containers = soup.find_all(id=re.compile(r"^v-"))
        
        # 策略3：查找 class 包含 view 的区块
        if not view_containers:
            view_containers = soup.find_all(class_=re.compile(r"view"))
        
        for container in view_containers:
            view_id = container.get("data-view") or container.get("id") or ""
            if not view_id.startswith("v-"):
                continue
            
            # 提取纯文本（用于文案验收）
            text = container.get_text(separator=" ", strip=True)
            
            # 提取关键元素
            elements = self._extract_elements(container)
            
            view = PrototypeView(
                view_id=view_id,
                title=container.get("data-title", "") or view_id,
                file=file_path.name,
                html_content=str(container),
                text_content=text,
                elements=elements,
                metadata={
                    "data-title": container.get("data-title", ""),
                    "data-description": container.get("data-description", "")
                }
            )
            views.append(view)
        
        # 去重（同一 view_id 可能出现多次）
        seen = set()
        unique_views = []
        for v in views:
            if v.view_id not in seen:
                seen.add(v.view_id)
                unique_views.append(v)
        
        return unique_views
    
    def _extract_elements(self, container) -> List[Dict]:
        """提取关键交互元素"""
        elements = []
        
        # 按钮
        for btn in container.find_all(["button", "a"], class_=re.compile(r"btn|button")):
            elements.append({
                "type": "button",
                "text": btn.get_text(strip=True),
                "attrs": {k: v for k, v in btn.attrs.items() if k in ["id", "class", "href", "data-action"]}
            })
        
        # 输入框
        for inp in container.find_all(["input", "textarea", "select"]):
            elements.append({
                "type": "input",
                "input_type": inp.get("type", "text"),
                "placeholder": inp.get("placeholder", ""),
                "attrs": {k: v for k, v in inp.attrs.items() if k in ["id", "name", "data-validate"]}
            })
        
        # 关键文本块
        for text_elem in container.find_all(["h1", "h2", "h3", "p", "span", "div"], 
                                             class_=re.compile(r"title|heading|label|caption")):
            text = text_elem.get_text(strip=True)
            if text and len(text) > 2:
                elements.append({
                    "type": "text",
                    "text": text,
                    "tag": text_elem.name
                })
        
        return elements
    
    def _extract_styles(self, soup: BeautifulSoup) -> Dict:
        """提取全局样式/Token 引用"""
        styles = {}
        
        # 查找 style 标签
        for style in soup.find_all("style"):
            content = style.get_text()
            # 简单提取 CSS 变量
            for match in re.finditer(r'(--[\w-]+)\s*:\s*([^;]+)', content):
                styles[match.group(1)] = match.group(2).strip()
        
        # 查找 link 样式表
        for link in soup.find_all("link", rel="stylesheet"):
            styles[f"stylesheet:{link.get('href', '')}"] = True
        
        return styles
    
    def _extract_components(self, soup: BeautifulSoup) -> List[Dict]:
        """提取组件引用"""
        components = []
        for elem in soup.find_all(attrs={"data-component": True}):
            components.append({
                "name": elem.get("data-component"),
                "props": {k: v for k, v in elem.attrs.items() if k.startswith("data-")}
            })
        return components
    
    # ========== 查询接口 ==========
    
    def get_view(self, file_name: str, view_id: str) -> Optional[PrototypeView]:
        """获取指定 View"""
        proto = self.parse_file(self.prototype_dir / file_name)
        for view in proto.views:
            if view.view_id == view_id:
                return view
        return None
    
    def get_views_by_file(self, file_name: str) -> List[PrototypeView]:
        """获取文件的所有 View"""
        proto = self.parse_file(self.prototype_dir / file_name)
        return proto.views
    
    def find_view_by_text(self, text: str) -> List[PrototypeView]:
        """按文案搜索 View"""
        results = []
        for proto in self.parse_all().values():
            for view in proto.views:
                if text in view.text_content:
                    results.append(view)
        return results
    
    def export_acceptance_data(self) -> Dict:
        """导出验收数据（供 gov-prototype-verify 使用）"""
        data = {}
        for proto in self.parse_all().values():
            for view in proto.views:
                key = f"{proto.file_name}::{view.view_id}"
                data[key] = {
                    "file": proto.file_name,
                    "view_id": view.view_id,
                    "title": view.title,
                    "text": view.text_content,
                    "elements": view.elements
                }
        return data


# 兼容旧接口
def parse_prototype_file(file_path: str) -> Dict:
    """兼容旧版 gov-prototype-verify.py 的接口"""
    parser = PrototypeParser()
    proto = parser.parse_file(Path(file_path))
    return {
        "file": proto.file_name,
        "title": proto.title,
        "views": [
            {
                "id": v.view_id,
                "title": v.title,
                "text": v.text_content,
                "html": v.html_content
            }
            for v in proto.views
        ]
    }