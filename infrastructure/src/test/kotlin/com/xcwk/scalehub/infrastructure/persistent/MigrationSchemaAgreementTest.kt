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
 * 历史盲区记录：本测试初版只对拍 2→3，漏掉 1→2 的蛇形/驼峰列名事故（G3 连接测试
 * 2026-10-16 当场抓获），现覆盖两个迁移段。
 */
class MigrationSchemaAgreementTest {

    private fun findSchema(version: Int): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val f = File(dir, "infrastructure/schemas/com.xcwk.scalehub.infrastructure.persistent.AppDatabase/$version.json")
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("schema $version.json 未找到（从 ${System.getProperty("user.dir")} 向上探测失败）")
    }

    /** 从 createSql/DDL 文本提取 列名→类型 与主键列（统一归一化后比较）。 */
    private fun shapeOf(sql: String): Pair<Map<String, String>, List<String>> {
        val normalized = sql.replace("\${TABLE_NAME}", "health_logs")
        val columns = Regex("`(\\w+)` (INTEGER|TEXT|REAL)(?: NOT NULL)?")
            .findAll(normalized).associate { it.groupValues[1] to it.groupValues[2] }
        val pk = Regex("PRIMARY KEY\\(([^)]*)\\)")
            .find(normalized)?.groupValues[1]
            ?.split(",")?.map { it.trim().trim('`') } ?: emptyList()
        return columns to pk
    }

    @Test
    fun `迁移建表与导出schema声明逐列一致`() {
        val json = findSchema(3).readText(Charsets.UTF_8)
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
    fun `迁移1_2新增列与schema2声明逐列一致`() {
        val json = findSchema(2).readText(Charsets.UTF_8)
        val match = Regex(""""tableName": "measurements"[\s\S]*?"createSql": "(.*?)"""")
            .find(json) ?: error("schema 2.json 中未找到 measurements 的 createSql")
        val (expectedCols, _) = shapeOf(match.groupValues[1])

        val alters = Regex("ADD COLUMN (\\w+) (\\w+)")
            .findAll(AppDatabase.MIGRATION_1_2_SQL)
            .associate { it.groupValues[1] to it.groupValues[2] }
        assertThat(alters).hasSize(3)
        for ((name, type) in alters) {
            assertWithMessage("1→2 新增列 $name 在 schema 2 中缺失或类型不符") {
                assertThat(expectedCols[name]).isEqualTo(type)
            }
        }
        val sql2 = match.groupValues[1]
        for (name in alters.keys) {
            val notNullInSql = Regex("`$name` \\w+ NOT NULL").containsMatchIn(sql2)
            assertWithMessage("1→2 新增列 $name 应可空（旧行未记录语义）") {
                assertThat(notNullInSql).isFalse()
            }
        }
    }

    @Test
    fun `迁移3_4建devices表与schema4声明逐列一致`() {
        val json = findSchema(4).readText(Charsets.UTF_8)
        val match = Regex(""""tableName": "devices"[\s\S]*?"createSql": "(.*?)"""")
            .find(json) ?: error("4.json 中未找到 devices 的 createSql")
        val expectedSql = match.groupValues[1].replace("\\\"", "\"")
        val expectedShape = shapeOfSql(expectedSql, "devices")
        val actualShape = shapeOfSql(AppDatabase.CREATE_DEVICES_TABLE, "devices")
        assertWithMessage("devices 迁移 DDL 与 schema 4.json 逐列一致") {
            assertThat(actualShape.first).isEqualTo(expectedShape.first)
            assertThat(actualShape.second).isEqualTo(expectedShape.second)
        }
        assertThat(actualShape.second).isEqualTo(listOf("mac"))
    }

    /** shapeOf 的表名参数化版本（原版硬编码 health_logs）。 */
    private fun shapeOfSql(sql: String, table: String): Pair<Map<String, String>, List<String>> {
        val normalized = sql.replace("\${TABLE_NAME}", table)
        val columns = Regex("`(\\w+)` (INTEGER|TEXT|REAL)(?: NOT NULL)?")
            .findAll(normalized).associate { it.groupValues[1] to it.groupValues[2] }
        val pk = Regex("PRIMARY KEY\\(([^)]*)\\)")
            .find(normalized)?.groupValues[1]
            ?.split(",")?.map { it.trim().trim('`') } ?: emptyList()
        return columns to pk
    }

    @Test
    fun `迁移4_5补memberId列与schema5声明一致`() {
        val json = findSchema(5).readText(Charsets.UTF_8)
        // 两表 schema 均含 memberId 且默认值为 '1'（多成员维升级语义）
        for (table in listOf("measurements", "health_logs")) {
            val match = Regex(""""tableName": "$table"[\s\S]*?"createSql": "(.*?)"""")
                .find(json) ?: error("5.json 中未找到 $table 的 createSql")
            val sql = match.groupValues[1].replace("\\\"", "\"")
            assertWithMessage("5.json $table 应含 memberId 列") {
                assertThat(sql).contains("memberId")
            }
        }
        // 迁移 DDL 两表各一条 ALTER，默认 '1'
        val alters = Regex("ALTER TABLE (\\w+) ADD COLUMN (\\w+) (\\w+)(?: NOT NULL)?(?: DEFAULT '([^']*)')?")
            .findAll(AppDatabase.MIGRATION_4_5_SQL)
            .map { it.groupValues[1] to (it.groupValues[4] to it.groupValues[2]) }
            .toList()
        assertThat(alters.map { it.first }).containsExactly("measurements", "health_logs")
        alters.forEach { (table, pair) ->
            assertWithMessage("$table 的 memberId 默认值应为 1") {
                assertThat(pair.first).isEqualTo("1")
                assertThat(pair.second).isEqualTo("memberId")
            }
        }
    }

    @Test
    fun `迁移版本边正确`() {
        assertThat(AppDatabase.MIGRATION_2_3.startVersion).isEqualTo(2)
        assertThat(AppDatabase.MIGRATION_2_3.endVersion).isEqualTo(3)
        assertThat(AppDatabase.MIGRATION_3_4.startVersion).isEqualTo(3)
        assertThat(AppDatabase.MIGRATION_3_4.endVersion).isEqualTo(4)
        assertThat(AppDatabase.MIGRATION_4_5.startVersion).isEqualTo(4)
        assertThat(AppDatabase.MIGRATION_4_5.endVersion).isEqualTo(5)
        assertThat(AppDatabase.MIGRATION_5_6.startVersion).isEqualTo(5)
        assertThat(AppDatabase.MIGRATION_5_6.endVersion).isEqualTo(6)
        assertThat(AppDatabase.MIGRATION_6_7.startVersion).isEqualTo(6)
        assertThat(AppDatabase.MIGRATION_6_7.endVersion).isEqualTo(7)
        assertThat(AppDatabase.MIGRATION_7_8.startVersion).isEqualTo(7)
        assertThat(AppDatabase.MIGRATION_7_8.endVersion).isEqualTo(8)
    }

    @Test
    fun `迁移5_6建sync_state表与schema6声明逐列一致`() {
        val json = findSchema(6).readText(Charsets.UTF_8)
        val match = Regex(""""tableName": "sync_state"[\s\S]*?"createSql": "(.*?)"""")
            .find(json) ?: error("6.json 中未找到 sync_state 的 createSql")
        val expectedSql = match.groupValues[1].replace("\\\"", "\"")
        val expectedShape = shapeOfSql(expectedSql, "sync_state")
        val actualShape = shapeOfSql(AppDatabase.CREATE_SYNC_STATE_TABLE, "sync_state")
        assertWithMessage("sync_state 迁移 DDL 与 schema 6.json 逐列一致") {
            assertThat(actualShape.first).isEqualTo(expectedShape.first)
            assertThat(actualShape.second).isEqualTo(expectedShape.second)
        }
        assertThat(actualShape.second).isEqualTo(listOf("tableName", "memberId"))
    }

    @Test
    fun `迁移6_7建habits表与schema7声明逐列一致`() {
        val json = findSchema(7).readText(Charsets.UTF_8)
        val match = Regex(""""tableName": "habits"[\s\S]*?"createSql": "(.*?)"""")
            .find(json) ?: error("7.json 中未找到 habits 的 createSql")
        val expectedSql = match.groupValues[1].replace("\\\"", "\"")
        val expectedShape = shapeOfSql(expectedSql, "habits")
        val actualShape = shapeOfSql(AppDatabase.CREATE_HABITS_TABLE, "habits")
        assertWithMessage("habits 迁移 DDL 与 schema 7.json 逐列一致") {
            assertThat(actualShape.first).isEqualTo(expectedShape.first)
            assertThat(actualShape.second).isEqualTo(expectedShape.second)
        }
        assertThat(actualShape.second).isEqualTo(listOf("id"))
    }

    @Test
    fun `迁移7_8重建表与schema8声明逐列一致`() {
        val json = findSchema(8).readText(Charsets.UTF_8)
        val cases = listOf(
            "measurements" to AppDatabase.MIGRATION_7_8_MEASUREMENTS,
            "health_logs" to AppDatabase.MIGRATION_7_8_HEALTH_LOGS,
            "goals" to AppDatabase.CREATE_GOALS_TABLE_V8,
            "members" to AppDatabase.CREATE_MEMBERS_TABLE_V8,
            "pending_sync" to AppDatabase.CREATE_PENDING_SYNC_TABLE_V8,
        )
        for ((table, ddl) in cases) {
            val match = Regex(""""tableName": "$table"[\s\S]*?"createSql": "(.*?)"""")
                .find(json) ?: error("8.json 中未找到 $table 的 createSql")
            val expectedSql = match.groupValues[1].replace("\\\"", "\"")
            val expectedShape = shapeOfSql(expectedSql, table)
            val actualShape = shapeOfSql(ddl, table)
            assertWithMessage("$table 迁移 DDL 与 schema 8.json 逐列一致") {
                assertThat(actualShape.first).isEqualTo(expectedShape.first)
                assertThat(actualShape.second).isEqualTo(expectedShape.second)
            }
        }
        assertThat(shapeOfSql(AppDatabase.MIGRATION_7_8_MEASUREMENTS, "measurements").second)
            .isEqualTo(listOf("recordId"))
        assertThat(shapeOfSql(AppDatabase.MIGRATION_7_8_HEALTH_LOGS, "health_logs").second)
            .isEqualTo(listOf("memberId", "loggedAt", "type"))
        val measurementsSql = Regex(""""tableName": "measurements"[\s\S]*?"createSql": "(.*?)"""")
            .find(json)!!.groupValues[1]
        assertThat(measurementsSql).contains("DEFAULT ''")
        assertThat(AppDatabase.MIGRATION_7_8_MEASUREMENTS).contains("DEFAULT ''")
        assertThat(measurementsSql).contains("syncStatus")
        val devicesSql = Regex(""""tableName": "devices"[\s\S]*?"createSql": "(.*?)"""")
            .find(json)!!.groupValues[1]
        assertThat(devicesSql).contains("`updatedAt` INTEGER NOT NULL DEFAULT 0")
        assertThat(devicesSql).contains("`unboundAt` INTEGER")
        assertThat(json).contains("index_habits_memberId")
        assertThat(json).contains("index_measurements_memberId_measuredAt")
        assertThat(json).contains("index_members_slot")
    }

    private fun assertWithMessage(msg: String, block: () -> Unit) {
        try {
            block()
        } catch (e: AssertionError) {
            throw AssertionError("$msg: ${e.message}", e)
        }
    }
}
