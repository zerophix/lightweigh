"""Notion 同步主入口 - 整合所有模块"""
import sys
import argparse
import json
from pathlib import Path
from typing import List, Optional

from .config import get_config, NotionConfig, set_config
from .client import NotionClient
from .cache import NotionCache
from .detect import IncrementalSync
from .transform import TaskDecomposer, ProductEpic, ProductFeature, ProductStory, EngineeringTask
from .prototype_parser import PrototypeParser
from .merge import TaskMerger
from .apply import SyncApplier
from .feedback import FeedbackPusher, EvidenceCollector


class NotionSync:
    """同步编排器"""
    
    def __init__(self, config: NotionConfig = None, dry_run: bool = False):
        self.config = config or get_config()
        self.dry_run = dry_run
        
        # 核心组件
        self.client = NotionClient(self.config)
        self.cache = NotionCache(self.config.cache_dir, self.config.cache_ttl_hours)
        self.parser = PrototypeParser()
        self.decomposer = TaskDecomposer(self.client, self.parser)
        self.merger = TaskMerger()
        self.applier = SyncApplier(dry_run=dry_run)
        self.pusher = FeedbackPusher(self.client, self.cache)
        self.collector = EvidenceCollector(Path(self.config.layer1_output).parent.parent)
    
    # ========== Pull 同步 ==========
    
    def pull(self, lookback_hours: int = 24) -> dict:
        """拉取同步：Notion -> 仓库"""
        print(f"🔄 拉取同步 (回看 {lookback_hours}h)...")
        
        # 1. 检测变更
        sync = IncrementalSync(self.client, self.cache)
        summary = sync.run(lookback_hours)
        print(f"   变更摘要: {summary['total']} 项 (新增:{summary['created']} 更新:{summary['updated']} 删除:{summary['deleted']})")
        
        if summary['total'] == 0:
            print("   无变更，跳过后续步骤")
            return {"status": "no_changes", "summary": summary}
        
        # 2. 获取完整数据（用于重建 Layer 1）
        epics = self._fetch_all_epics()
        features = self._fetch_all_features()
        stories = self._fetch_all_stories()
        
        print(f"   产品任务: {len(epics)} Epics, {len(features)} Features, {len(stories)} Stories")
        
        # 3. 分解工程任务
        engineering_tasks = self.decomposer.create_engineering_tasks(stories, features, epics)
        print(f"   生成工程任务: {len(engineering_tasks)} 个")
        
        # 4. 读取现有任务并合并
        existing_tasks = self._load_existing_tasks()
        merge_result = self.merger.merge(existing_tasks, stories, features, epics)
        print(f"   合并结果: 更新 {len(merge_result.updated)}, 废弃 {len(merge_result.obsolete)}, 冲突 {len(merge_result.conflicts)}")
        
        # 5. 应用变更
        report = self.applier.apply(epics, features, stories, engineering_tasks, merge_result)
        
        # 6. 更新缓存映射
        self._update_mappings(epics, features, stories, engineering_tasks)
        
        print("✅ 拉取同步完成")
        return {"status": "success", "summary": summary, "report": report}
    
    def _fetch_all_epics(self) -> List[ProductEpic]:
        db_id = self.config.get_database_id("epics")
        epics = []
        for item in self.client.iter_database(db_id):
            page = self.client._parse_page(item)
            epics.append(self.decomposer.decompose_epic(page))
        return epics
    
    def _fetch_all_features(self) -> List[ProductFeature]:
        db_id = self.config.get_database_id("features")
        features = []
        for item in self.client.iter_database(db_id):
            page = self.client._parse_page(item)
            features.append(self.decomposer.decompose_feature(page))
        return features
    
    def _fetch_all_stories(self) -> List[ProductStory]:
        db_id = self.config.get_database_id("stories")
        stories = []
        for item in self.client.iter_database(db_id):
            page = self.client._parse_page(item)
            stories.append(self.decomposer.decompose_story(page))
        
        # 补充：从 Feature 的 prototype_views 自动生成 Story
        features = self._fetch_all_features()
        auto_stories = self.decomposer.decompose_all_stories(features)
        
        # 合并去重
        existing_ids = {s.id for s in stories}
        for s in auto_stories:
            if s.id not in existing_ids:
                stories.append(s)
        
        return stories
    
    def _load_existing_tasks(self) -> dict:
        """加载现有工程任务"""
        tasks = {}
        if self.config.tasks_output.exists():
            data = json.loads(self.config.tasks_output.read_text())
            for t in data.get("tasks", []):
                tasks[t["id"]] = EngineeringTask(**t)
        return tasks
    
    def _update_mappings(self, epics, features, stories, tasks):
        """更新 ID 映射缓存"""
        for e in epics:
            self.cache.set_mapping(e.id, e.notion_id)
        for f in features:
            self.cache.set_mapping(f.id, f.notion_id)
        for s in stories:
            if s.notion_id:
                self.cache.set_mapping(s.id, s.notion_id)
        for t in tasks:
            if t.parent_story:
                # 任务映射到其 Story 的 Notion ID
                pass
        print(f"   映射已更新: {self.cache.stats()['total_mappings']} 个")
    
    # ========== Push 反馈 ==========
    
    def push_task(self, task_id: str) -> bool:
        """推送单个任务状态"""
        tasks = self._load_existing_tasks()
        if task_id not in tasks:
            print(f"任务不存在: {task_id}")
            return False
        
        task = tasks[task_id]
        evidence = self.collector.collect_task_evidence(task_id)
        evidence.update(self.collector.collect_pr_info(""))
        
        return self.pusher.push_task_status(task_id, task.status, evidence)
    
    def push_all(self) -> dict:
        """推送所有任务状态"""
        tasks = self._load_existing_tasks()
        return self.pusher.sync_all_status(tasks)
    
    # ========== 状态查看 ==========
    
    def status(self) -> dict:
        """查看同步状态"""
        cache_stats = self.cache.stats()
        tasks = self._load_existing_tasks()
        
        status_counts = {}
        for t in tasks.values():
            status_counts[t.status] = status_counts.get(t.status, 0) + 1
        
        return {
            "cache": cache_stats,
            "tasks_total": len(tasks),
            "tasks_by_status": status_counts,
            "layer1_exists": self.config.layer1_output.exists(),
            "tasks_exists": self.config.tasks_output.exists()
        }
    
    # ========== 测试连接 ==========
    
    def test_connection(self) -> bool:
        """测试 Notion API 连接"""
        try:
            # 尝试查询一个数据库
            db_id = self.config.get_database_id("epics")
            result = self.client.query_database(db_id, page_size=1)
            print(f"✅ 连接成功，数据库可访问")
            return True
        except Exception as e:
            print(f"❌ 连接失败: {e}")
            return False
    
    # ========== 初始化 ==========
    
    def init(self) -> dict:
        """初始化：创建目录、验证配置、OAuth授权、测试连接"""
        print("🔧 初始化 Notion 同步...")
        
        # 验证配置
        errors = self.config.validate()
        if errors:
            print("❌ 配置错误:")
            for e in errors:
                print(f"  - {e}")
            return {"status": "config_error", "errors": errors}
        
        # 如果配置了 OAuth，执行授权流程
        if self.config.oauth_client_id and self.config.oauth_client_secret:
            print("🔐 检测到 OAuth 配置，开始授权流程...")
            if not self._oauth_authorize():
                return {"status": "oauth_failed", "error": "OAuth 授权失败"}
        
        # 测试连接
        if not self.test_connection():
            return {"status": "connection_failed"}
        
        # 创建缓存目录
        self.config.cache_dir.mkdir(parents=True, exist_ok=True)
        
        print("✅ 初始化完成")
        return {"status": "success"}
    
    def _oauth_authorize(self) -> bool:
        """执行 OAuth 授权流程"""
        import http.server
        import urllib.parse
        import threading
        import webbrowser
        import time
        
        # 解析 redirect_uri 获取端口
        from urllib.parse import urlparse
        parsed = urlparse(self.config.oauth_redirect_uri)
        port = parsed.port or 8080
        
        # 生成 state 防止 CSRF
        import secrets
        state = secrets.token_urlsafe(16)
        
        # 生成授权 URL
        auth_url = self.client.oauth.get_auth_url(state=state)
        print(f"🌐 请在浏览器中打开以下链接完成授权：")
        print(f"   {auth_url}")
        
        # 创建本地回调服务器
        auth_code = {"code": None, "error": None}
        auth_event = threading.Event()
        
        class CallbackHandler(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                query = urllib.parse.urlparse(self.path).query
                params = urllib.parse.parse_qs(query)
                
                if params.get("state") != [state]:
                    self.send_response(400)
                    self.end_headers()
                    self.wfile.write(b"Invalid state parameter")
                    return
                
                if "code" in params:
                    auth_code["code"] = params["code"][0]
                    self.send_response(200)
                    self.end_headers()
                    self.wfile.write("授权成功！请关闭此窗口返回终端。".encode("utf-8"))
                elif "error" in params:
                    auth_code["error"] = params["error"][0]
                    self.send_response(400)
                    self.end_headers()
                    self.wfile.write(f"授权失败: {params['error'][0]}".encode("utf-8"))
                else:
                    self.send_response(400)
                    self.end_headers()
                    self.wfile.write(b"Missing code parameter")
                
                auth_event.set()
            
            def log_message(self, format, *args):
                pass  # 静默日志
        
        # 启动服务器
        server = http.server.HTTPServer(("localhost", port), CallbackHandler)
        server_thread = threading.Thread(target=server.serve_forever, daemon=True)
        server_thread.start()
        
        try:
            # 尝试打开浏览器
            try:
                webbrowser.open(auth_url)
            except Exception:
                pass  # 无头环境下忽略
            
            # 等待授权完成（最多 5 分钟）
            print(f"⏳ 等待授权回调（端口 {port}，最多 5 分钟）...")
            if not auth_event.wait(timeout=300):
                print("❌ 授权超时")
                return False
            
            if auth_code["error"]:
                print(f"❌ 授权失败: {auth_code['error']}")
                return False
            
            if not auth_code["code"]:
                print("❌ 未获取到授权码")
                return False
            
            print("🔄 正在交换授权码获取 Token...")
            if not self.client.oauth.exchange_code(auth_code["code"]):
                print("❌ Token 交换失败")
                return False
            
            print("✅ OAuth 授权成功！")
            return True
            
        finally:
            server.shutdown()
            server.server_close()

    def auth(self) -> dict:
        """手动触发 OAuth 授权流程"""
        print("🔐 启动 OAuth 授权流程...")
        if not self.config.oauth_client_id or not self.config.oauth_client_secret:
            return {"status": "error", "error": "未配置 OAuth 凭证 (NOTION_OAUTH_CLIENT_ID/SECRET)"}
        
        if self._oauth_authorize():
            return {"status": "success", "message": "OAuth 授权成功"}
        else:
            return {"status": "failed", "error": "OAuth 授权失败"}


def main():
    parser = argparse.ArgumentParser(description="Notion 同步工具（免费版轮询模式）")
    parser.add_argument("command", choices=["pull", "push", "status", "test", "init", "auth"])
    parser.add_argument("--lookback-hours", type=int, default=24)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--task", help="任务 ID (如 T-ENG-001)")
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--config", help="配置文件路径")
    
    args = parser.parse_args()
    
    # 加载配置
    if args.config:
        config = NotionConfig.from_file(Path(args.config))
    else:
        config = get_config()
    
    set_config(config)
    
    sync = NotionSync(config, dry_run=args.dry_run)
    
    try:
        if args.command == "pull":
            result = sync.pull(args.lookback_hours)
            print(json.dumps(result, ensure_ascii=False, indent=2))
        elif args.command == "push":
            if args.task:
                success = sync.push_task(args.task)
                print("✅ 推送成功" if success else "❌ 推送失败")
            elif args.all:
                result = sync.push_all()
                print(f"推送结果: 成功 {result['success']}, 失败 {result['failed']}, 跳过 {result['skipped']}")
            else:
                parser.error("push 需要 --task 或 --all")
        elif args.command == "status":
            result = sync.status()
            print(json.dumps(result, ensure_ascii=False, indent=2))
        elif args.command == "test":
            success = sync.test_connection()
            sys.exit(0 if success else 1)
        elif args.command == "init":
            result = sync.init()
            print(json.dumps(result, ensure_ascii=False, indent=2))
        elif args.command == "auth":
            result = sync.auth()
            print(json.dumps(result, ensure_ascii=False, indent=2))
    except Exception as e:
        print(f"❌ 执行失败: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()