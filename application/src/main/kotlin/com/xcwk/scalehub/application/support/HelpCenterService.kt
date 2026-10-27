package com.xcwk.scalehub.application.support

/**
 * application service — 帮助中心编排。
 *
 * 职责：
 *  - 获取帮助分类
 *  - 获取分类下的文章列表
 *  - 获取文章详情
 *  - 搜索帮助内容
 *
 * 依赖 HelpCenterPort（本地资源或远程）。
 */
class HelpCenterService(
    private val port: HelpCenterPort?,
) {

    /** 获取所有帮助分类。 */
    suspend fun categories(): List<HelpCategory> {
        return port?.categories() ?: listOf(
            HelpCategory("faq", "常见问题", "🔍", 0),
            HelpCategory("usage", "使用指南", "📖", 1),
            HelpCategory("device", "设备支持", "🔌", 2),
            HelpCategory("account", "账号问题", "👤", 3),
        )
    }

    /** 获取分类下的文章列表。 */
    suspend fun articles(categoryId: String): List<HelpArticle> {
        return port?.articles(categoryId) ?: run {
            // 本地兜底
            localArticles(categoryId)
        }
    }

    /** 获取文章详情。 */
    suspend fun article(articleId: String): HelpArticleDetail? {
        return port?.article(articleId) ?: localArticleDetail(articleId)
    }

    /** 搜索帮助内容。 */
    suspend fun search(keyword: String): List<HelpArticle> {
        if (keyword.isBlank()) return emptyList()
        return port?.search(keyword) ?: run {
            val all = mutableListOf<HelpArticle>()
            categories().forEach { cat ->
                val articles = localArticles(cat.id)
                all.addAll(articles.filter { 
                    it.title.contains(keyword) || it.summary.contains(keyword) 
                })
            }
            all
        }
    }

    /** 本地兜底数据。 */
    private fun localArticles(categoryId: String): List<HelpArticle> {
        return when (categoryId) {
            "faq" -> listOf(
                HelpArticle("faq1", "common", "如何连接蓝牙秤", "检查设备是否进入配对模式，确保蓝牙打开", 0L),
                HelpArticle("faq2", "common", "测量数据不准确怎么办", "校准秤体并重新测量", 0L),
            )
            "usage" -> listOf(
                HelpArticle("usage1", "usage", "如何进行一次测量", "站在秤上静止 30 秒即可", 0L),
                HelpArticle("usage2", "usage", "如何查看历史记录", "在「历史」页查看", 0L),
            )
            else -> emptyList()
        }
    }

    private fun localArticleDetail(articleId: String): HelpArticleDetail? {
        return when (articleId) {
            "faq1" -> HelpArticleDetail("faq1", "如何连接蓝牙秤", "common", "# 如何连接蓝牙秤\n\n1. 打开手机蓝牙\n2. 将秤开机进入配对模式\n3. 在 App 中点击「添加设备」\n4. 选择扫描到的设备", 0L)
            "faq2" -> HelpArticleDetail("faq2", "测量数据不准确怎么办", "common", "# 数据校准\n\n1. 将秤放在平整硬 surface\n2. 进行 3 次重复测量取平均\n3. 检查是否校准秤体", 0L)
            "usage1" -> HelpArticleDetail("usage1", "如何进行一次测量", "usage", "# 测量流程\n\n1. 脱鞋站在秤上\n2. 保持静止 30 秒\n3. 等待显示结果", 0L)
            else -> null
        }
    }
}
