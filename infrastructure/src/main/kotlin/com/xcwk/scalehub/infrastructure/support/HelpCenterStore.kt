package com.xcwk.scalehub.infrastructure.support

import com.xcwk.scalehub.application.support.HelpCenterPort
import com.xcwk.scalehub.application.support.HelpArticle
import com.xcwk.scalehub.application.support.HelpCategory
import com.xcwk.scalehub.application.support.HelpArticleDetail

/**
 * 帮助中心实现（本地静态资源）。
 *
 * 帮助文档从 assets/help 目录加载，远程更新时替换实现即可。
 */
class HelpCenterStore : HelpCenterPort {

    private val localCategories = listOf(
        HelpCategory("faq", "常见问题", "🔍", 0),
        HelpCategory("usage", "使用指南", "📖", 1),
        HelpCategory("device", "设备支持", "🔌", 2),
        HelpCategory("account", "账号问题", "👤", 3),
    )

    private val localContent = mapOf(
        "faq1" to HelpArticleDetail(
            id = "faq1",
            title = "如何连接蓝牙秤",
            categoryId = "faq",
            content = "# 如何连接蓝牙秤\n\n1. 打开手机蓝牙\n2. 将秤开机进入配对模式\n3. 在 App 中点击\"添加设备\"\n4. 选择扫描到的设备",
            updatedTime = System.currentTimeMillis(),
        ),
        "faq2" to HelpArticleDetail(
            id = "faq2",
            title = "测量数据不准确怎么办",
            categoryId = "faq",
            content = "# 数据校准\n\n1. 将秤放在平整硬表面\n2. 进行 3 次重复测量取平均\n3. 检查是否校准秤体",
            updatedTime = System.currentTimeMillis(),
        ),
        "usage1" to HelpArticleDetail(
            id = "usage1",
            title = "如何进行一次测量",
            categoryId = "usage",
            content = "# 测量流程\n\n1. 脱鞋站在秤上\n2. 保持静止 30 秒\n3. 等待显示结果",
            updatedTime = System.currentTimeMillis(),
        ),
    )

    private val localArticles = mapOf(
        "faq" to listOf(
            HelpArticle("faq1", "如何连接蓝牙秤", "faq", "检查设备是否进入配对模式，确保蓝牙打开", System.currentTimeMillis()),
            HelpArticle("faq2", "测量数据不准确怎么办", "faq", "校准秤体并重新测量", System.currentTimeMillis()),
        ),
        "usage" to listOf(
            HelpArticle("usage1", "如何进行一次测量", "usage", "站在秤上静止 30 秒即可", System.currentTimeMillis()),
        ),
    )

    override suspend fun categories(): List<HelpCategory> = localCategories

    override suspend fun articles(categoryId: String): List<HelpArticle> =
        localArticles[categoryId] ?: emptyList()

    override suspend fun article(articleId: String): HelpArticleDetail? =
        localContent[articleId]

    override suspend fun search(keyword: String): List<HelpArticle> {
        val all = localArticles.values.flatten()
        return all.filter {
            it.title.contains(keyword, ignoreCase = true) ||
            it.summary.contains(keyword, ignoreCase = true)
        }
    }
}
