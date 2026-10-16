package com.xcwk.scalehub.infrastructure.persistent

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * 迁移 2→3 建表 DDL 与 Room 导出 schema 3.json 的静态对拍（DEC-022 组 C）。
 *
 * 验证边界（如实声明）：本测试证明"MIGRATION_2_3 写的 SQL 与实体导出的期望
 * 结构逐列一致"（列名/类型/主键），排除手写 DDL 拼写漂移；
 * 它不执行真实 SQLite 迁移——v2 旧库升级与数据保留由 T-005 androidTest（G3）关闭。
 */
class Migration23SchemaAgreementTest {

    private fun findSchema3(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val f = File(dir, "infrastructure/schemas/com.xcwk.scalehub.infrastructure.persistent.AppDatabase/3.json")
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("schema 3.json 未找到（从 ${System.getProperty("user.dir")} 向上探测失败）")
    }

    /** 从 createSql/DDL 文本提取 列名→类型 与主键列（统一归一化后比较）。 */
    private fun shapeOf(sql: String): Pair<Map<String, String>, List<String>> {
        val normalized = sql.replace("\${TABLE_NAME}", "health_logs")
        val columns = Regex("`(\\w+)` (INTEGER|TEXT|REAL) NOT NULL")
            .findAll(normalized).associate { it.groupValues[1] to it.groupValues[2] }
        val pk = Regex("PRIMARY KEY\\(([^)]*)\\)")
            .find(normalized)?.groupValues[1]
            ?.split(",")?.map { it.trim().trim('`') } ?: emptyList()
        return columns to pk
    }

    @Test
    fun `迁移建表与导出schema声明逐列一致`() {
        val json = findSchema3().readText(Charsets.UTF_8)
        val match = Regex(""""tableName": "health_logs"[\s\S]*?"createSql": "(.*?)"""")
            .find(json) ?: error("3.json 中未找到 health_logs 的 createSql")
        val expectedSql = match.groupValues[1].replace("\\\"", "\"")

        val (expectedCols, expectedPk) = shapeOf(expectedSql)
        val (actualCols, actualPk) = shapeOf(AppDatabase.CREATE_HEALTH_LOGS_TABLE)

        assertWithMessage("列集合漂移") { assertThat(actualCols).isEqualTo(expectedCols) }
        assertWithMessage("主键漂移") { assertThat(actualPk).isEqualTo(expectedPk) }
        assertThat(expectedCols.keys).isNotEmpty()
    }

    @Test
    fun `迁移版本边正确`() {
        assertThat(AppDatabase.MIGRATION_2_3.startVersion).isEqualTo(2)
        assertThat(AppDatabase.MIGRATION_2_3.endVersion).isEqualTo(3)
    }

    private fun assertWithMessage(msg: String, block: () -> Unit) {
        try {
            block()
        } catch (e: AssertionError) {
            throw AssertionError("$msg: ${e.message}", e)
        }
    }
}
