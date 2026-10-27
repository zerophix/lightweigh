#!/usr/bin/env python3
"""Notion 数据库初始化脚本 - 创建标准数据库结构"""
import os
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from scripts.governance.notion_sync.config import NotionConfig
from scripts.governance.notion_sync.client import NotionClient


# 标准数据库定义
DATABASES = {
    "epics": {
        "title": "Product Epics",
        "description": "产品大任务/模块",
        "properties": {
            "标题": {"title": {}},
            "PRD页面": {"url": {}},
            "状态": {
                "select": {
                    "options": [
                        {"name": "规划", "color": "gray"},
                        {"name": "进行中", "color": "blue"},
                        {"name": "评审中", "color": "yellow"},
                        {"name": "完成", "color": "green"},
                        {"name": "暂停", "color": "red"}
                    ]
                }
            },
            "优先级": {
                "select": {
                    "options": [
                        {"name": "P0", "color": "red"},
                        {"name": "P1", "color": "orange"},
                        {"name": "P2", "color": "yellow"},
                        {"name": "P3", "color": "green"}
                    ]
                }
            },
            "目标里程碑": {"select": {"options": []}},
            "包含Feature": {"relation": {"database_id": ""}},  # 创建后填入
            "负责人": {"people": {}},
            "创建时间": {"created_time": {}},
            "最后更新": {"last_edited_time": {}}
        }
    },
    "features": {
        "title": "Product Features",
        "description": "产品功能/流程",
        "properties": {
            "标题": {"title": {}},
            "所属Epic": {"relation": {"database_id": ""}},
            "原型页面": {"url": {}},
            "原型Views": {"multi_select": {"options": []}},
            "状态": {
                "select": {
                    "options": [
                        {"name": "草稿", "color": "gray"},
                        {"name": "就绪", "color": "blue"},
                        {"name": "进行中", "color": "yellow"},
                        {"name": "完成", "color": "green"}
                    ]
                }
            },
            "包含Story": {"relation": {"database_id": ""}},
            "依赖Feature": {"relation": {"database_id": ""}},
            "负责人": {"people": {}},
            "创建时间": {"created_time": {}},
            "最后更新": {"last_edited_time": {}}
        }
    },
    "stories": {
        "title": "Product Stories",
        "description": "单 View/验收点",
        "properties": {
            "标题": {"title": {}},
            "所属Feature": {"relation": {"database_id": ""}},
            "原型文件": {"select": {"options": []}},
            "原型View": {"select": {"options": []}},
            "验收文案": {"rich_text": {}},
            "AC引用": {"multi_select": {"options": []}},
            "状态": {
                "select": {
                    "options": [
                        {"name": "待办", "color": "gray"},
                        {"name": "就绪", "color": "blue"},
                        {"name": "进行中", "color": "yellow"},
                        {"name": "阻塞", "color": "red"},
                        {"name": "完成", "color": "green"}
                    ]
                }
            },
            "Story Points": {"number": {"format": "number"}},
            "负责人": {"people": {}},
            "创建时间": {"created_time": {}},
            "最后更新": {"last_edited_time": {}}
        }
    },
    "decisions": {
        "title": "Decisions (DEC)",
        "description": "技术决策记录",
        "properties": {
            "标题": {"title": {}},
            "DEC编号": {"rich_text": {}},
            "状态": {
                "select": {
                    "options": [
                        {"name": "草案", "color": "gray"},
                        {"name": "评审中", "color": "yellow"},
                        {"name": "批准", "color": "green"},
                        {"name": "废弃", "color": "red"}
                    ]
                }
            },
            "关联Epic": {"relation": {"database_id": ""}},
            "关联Feature": {"relation": {"database_id": ""}},
            "架构影响": {"multi_select": {"options": ["数据库", "接口", "算法", "基础设施", "UI"]}},
            "决策文档": {"url": {}},
            "创建时间": {"created_time": {}},
            "最后更新": {"last_edited_time": {}}
        }
    },
    "milestones": {
        "title": "Milestones",
        "description": "里程碑/发布版本",
        "properties": {
            "标题": {"title": {}},
            "版本号": {"rich_text": {}},
            "目标日期": {"date": {}},
            "包含Epic": {"relation": {"database_id": ""}},
            "状态": {
                "select": {
                    "options": [
                        {"name": "规划", "color": "gray"},
                        {"name": "进行中", "color": "blue"},
                        {"name": "发布", "color": "green"},
                        {"name": "延期", "color": "red"}
                    ]
                }
            },
            "发布说明": {"url": {}},
            "创建时间": {"created_time": {}},
            "最后更新": {"last_edited_time": {}}
        }
    }
}


def create_databases(config: NotionConfig, client: NotionClient) -> Dict[str, str]:
    """创建所有数据库，返回 database_id 映射"""
    created = {}
    parent = {"type": "workspace_id", "workspace_id": config.workspace_id}
    
    # 第一轮：创建所有数据库（不含 relation）
    for key, spec in DATABASES.items():
        print(f"创建数据库: {spec['title']}...")
        
        payload = {
            "parent": parent,
            "title": [{"type": "text", "text": {"content": spec["title"]}}],
            "properties": spec["properties"]
        }
        
        try:
            resp = client._request("POST", "/databases", json=payload).json()
            db_id = resp["id"]
            created[key] = db_id
            print(f"  ✅ 创建成功: {db_id}")
        except Exception as e:
            print(f"  ❌ 创建失败: {e}")
            raise
    
    # 第二轮：更新 relation 属性的 database_id
    relation_updates = {
        "epics": {"包含Feature": "features"},
        "features": {"所属Epic": "epics", "包含Story": "stories", "依赖Feature": "features"},
        "stories": {"所属Feature": "features"},
        "decisions": {"关联Epic": "epics", "关联Feature": "features"}
    }
    
    for db_key, relations in relation_updates.items():
        db_id = created[db_key]
        props = {}
        for prop_name, target_key in relations.items():
            target_id = created[target_key]
            props[prop_name] = {"relation": {"database_id": target_id}}
        
        if props:
            try:
                client._request("PATCH", f"/databases/{db_id}", json={"properties": props})
                print(f"  🔗 更新 {db_key} relation 完成")
            except Exception as e:
                print(f"  ⚠️ 更新 relation 失败: {e}")
    
    return created


def populate_prototype_options(client: NotionClient, db_id: str):
    """填充原型文件和 View 选项"""
    # 扫描原型目录获取文件列表
    prototype_dir = Path("design/prototypes/interactive")
    if not prototype_dir.exists():
        return
    
    files = []
    views = []
    for html_file in prototype_dir.glob("*.html"):
        files.append(html_file.name)
        # 可以进一步解析 HTML 获取 view 列表
    
    # 更新数据库属性选项
    props = {}
    if files:
        props["原型文件"] = {"select": {"options": [{"name": f, "color": "default"} for f in files]}}
    if views:
        props["原型View"] = {"select": {"options": [{"name": v, "color": "default"} for v in views]}}
    
    if props:
        client._request("PATCH", f"/databases/{db_id}", json={"properties": props})
        print(f"  📝 更新原型选项: {len(files)} 文件")


def generate_env_template(db_ids: Dict[str, str]) -> str:
    """生成 .env 模板"""
    lines = [
        "# Notion 同步配置",
        "# 请将以下值填入 .env 或 GitHub Secrets",
        "",
        f"NOTION_TOKEN=your_integration_token",
        f"NOTION_WORKSPACE_ID=your_workspace_id",
        "",
        "# 数据库 IDs",
    ]
    for key, db_id in db_ids.items():
        env_key = f"NOTION_DB_{key.upper()}"
        lines.append(f"{env_key}={db_id}")
    
    return "\n".join(lines)


def main():
    # 加载配置
    config = NotionConfig.from_env()
    errors = config.validate()
    if errors:
        print("❌ 配置错误:")
        for e in errors:
            print(f"  - {e}")
        print("\n请设置环境变量或创建 .env 文件")
        sys.exit(1)
    
    client = NotionClient(config)
    
    print("🚀 开始初始化 Notion 数据库...")
    
    try:
        db_ids = create_databases(config, client)
        
        # 填充原型选项
        features_db = db_ids.get("features")
        if features_db:
            populate_prototype_options(client, features_db)
        
        stories_db = db_ids.get("stories")
        if stories_db:
            populate_prototype_options(client, stories_db)
        
        # 输出环境变量模板
        env_template = generate_env_template(db_ids)
        print("\n" + "="*50)
        print("📋 请将以下配置加入 .env 或 GitHub Secrets:")
        print("="*50)
        print(env_template)
        
        # 保存到文件
        env_file = Path(".env.notion")
        env_file.write_text(env_template)
        print(f"\n💾 已保存至: {env_file}")
        
    except Exception as e:
        print(f"\n❌ 初始化失败: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()