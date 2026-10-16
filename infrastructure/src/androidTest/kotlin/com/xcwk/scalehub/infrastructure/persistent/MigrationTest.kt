package com.xcwk.scalehub.infrastructure.persistent

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G3 真实迁移测试（DEC-020 门禁，对抗评审终审阻断项）——
 * 在真实 SQLite 上执行 v1 → v2 → v3 全链：
 *  - 每步以 MigrationTestHelper 对导出 schema 做运行时结构校验
 *  - v1 历史测量行在两步迁移后完整保留
 *  - v2 新增快照列对旧行为 NULL（未记录语义）
 *  - v3 新表 health_logs 可写可读，复合主键冲突覆盖语义生效
 * 需要模拟器/真机：./gradlew :infrastructure:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun v1到v3全链迁移_结构校验与数据保留() {
        // ---- v1 建库并写入历史行（v1 列集：无快照三列）----
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO measurements (measuredAt, weightKg, impedanceOhm, fat, mac, algorithmId) " +
                    "VALUES (1700000000000, 70.0, 500.0, 18.4, 'AA:BB', 'builtin-1')"
            )
            close()
        }

        // ---- v1 → v2：补快照三列，schema 校验 + 历史行保留 + 旧行快照为 NULL ----
        helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT weightKg, fat, male, ageYears, heightCm FROM measurements").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(70.0, c.getDouble(0), 1e-9)
                assertEquals(18.4, c.getDouble(1), 1e-9)
                assertTrue("v1 旧行快照列应为 NULL，实际=${c.isNull(2)}", c.isNull(2))
                assertTrue(c.isNull(3))
                assertTrue(c.isNull(4))
            }
        }

        // ---- v2 → v3：建 health_logs，schema 校验 + 测量行仍完整 ----
        helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT COUNT(*), SUM(weightKg) FROM measurements").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
                assertEquals(70.0, c.getDouble(1), 1e-9)
            }

            // 新表写入 + 复合主键 (loggedAt, type) 覆盖语义
            db.execSQL(
                "INSERT INTO health_logs (loggedAt, type, value, value2, label, note) " +
                    "VALUES (1700000000000, 'diet', 1500.0, 0.0, '午餐', '')"
            )
            db.execSQL(
                "INSERT OR REPLACE INTO health_logs (loggedAt, type, value, value2, label, note) " +
                    "VALUES (1700000000000, 'diet', 1600.0, 0.0, '午餐', '')"
            )
            db.query("SELECT COUNT(*), SUM(value) FROM health_logs").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("同 (loggedAt,type) 应覆盖为 1 行", 1, c.getInt(0))
                assertEquals(1600.0, c.getDouble(1), 1e-9)
            }
            // 不同 type 并存
            db.execSQL(
                "INSERT INTO health_logs (loggedAt, type, value, value2, label, note) " +
                    "VALUES (1700000000000, 'water', 250.0, 0.0, '', '')"
            )
            db.query("SELECT COUNT(*) FROM health_logs").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(2, c.getInt(0))
            }
        }
    }

    companion object {
        private const val TEST_DB = "migration-test.db"
    }
}
