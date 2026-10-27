package com.xcwk.scalehub.application.support

/**
 * 帮助中心端口（M3）—— 抽象帮助文档/ FAQ 查询。
 *
 * 实现者负责从本地资源或远程获取帮助内容；应用层只编排语义。
 */
interface HelpCenterPort {
    /** 获取所有帮助分类。 */
    suspend fun categories(): List<HelpCategory>

    /** 获取指定分类下的帮助文章列表。 */
    suspend fun articles(categoryId: String): List<HelpArticle>

    /** 获取单篇帮助文章详情。 */
    suspend fun article(articleId: String): HelpArticleDetail?

    /** 搜索帮助内容。 */
    suspend fun search(keyword: String): List<HelpArticle>
}

/** 帮助分类。 */
data class HelpCategory(
    val id: String,
    val title: String,
    val icon: String?,
    val order: Int,
)

/** 帮助文章摘要。 */
data class HelpArticle(
    val id: String,
    val title: String,
    val categoryId: String,
    val summary: String,
    val updatedTime: Long,
)

/** 帮助文章详情。 */
data class HelpArticleDetail(
    val id: String,
    val title: String,
    val categoryId: String,
    val content: String,     // Markdown 内容
    val updatedTime: Long,
)
