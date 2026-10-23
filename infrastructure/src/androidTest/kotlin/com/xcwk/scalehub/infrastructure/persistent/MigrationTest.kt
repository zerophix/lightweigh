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
 * 在真实 SQLite 上执行 v1 → v5 全链（含 v4 设备表与 v5 多成员列）：
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
    fun v1到v5全链迁移_结构校验与数据保留() {
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

        // ---- v3 → v4：建 devices 表，schema 校验 + 测量与日志仍完整 ----
        helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4).use { db ->
            db.query("SELECT COUNT(*) FROM measurements").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
            db.execSQL(
                "INSERT INTO devices (mac, name, boundAt, active, protocolId, algorithmId) " +
                    "VALUES ('AA:BB:CC:DD:EE:FF', '卧室秤', '2026-10-01', 1, 'okok-1', 'okok-bia-1')"
            )
            db.query("SELECT name, active FROM devices").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("卧室秤", c.getString(0))
                assertEquals(1, c.getInt(1))
            }
        }

        // ---- v4 → v5：两表补 member_id（多成员维），存量数据与默认归属校验 ----
        helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5).use { db ->
            // v1 时代的测量行历经四步迁移后仍完整，且默认归属成员 1
            db.query("SELECT weightKg, fat, memberId FROM measurements").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(70.0, c.getDouble(0), 1e-9)
                assertEquals(18.4, c.getDouble(1), 1e-9)
                assertEquals("升级前存量测量应默认归属成员 1", "1", c.getString(2))
            }
            // v3 时代写入的健康日志行同样保留并默认归属成员 1
            db.query("SELECT COUNT(*), SUM(value) FROM health_logs WHERE type = 'water'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
                assertEquals(250.0, c.getDouble(1), 1e-9)
            }
            db.query("SELECT memberId FROM health_logs WHERE type = 'water'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("1", c.getString(0))
            }
            // 新表结构支持显式成员写入（多成员上线后的写路径）
            db.execSQL(
                "INSERT INTO measurements (measuredAt, weightKg, impedanceOhm, fat, mac, algorithmId, male, ageYears, heightCm, memberId) " +
                    "VALUES (1710000000000, 66.0, 400.0, -1.0, 'CC:DD', 'builtin-1', 1, 30, 175, '2')"
            )
            db.query("SELECT memberId FROM measurements WHERE measuredAt = 1710000000000").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("2", c.getString(0))
            }
            // devices 表跨迁移仍在
            db.query("SELECT COUNT(*) FROM devices").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
        }
    }

    /**
     * v7 → v8（DEC-057）：测量改 UUID 主键并保留采样行；健康日志主键带 memberId；
     * 设备只加 updatedAt / unboundAt，不删行。
     */
    @Test
    fun v7到v8_主键重建与存量保留() {
        helper.createDatabase(TEST_DB_V8, 7).apply {
            execSQL(
                "INSERT INTO measurements (measuredAt, weightKg, impedanceOhm, fat, mac, algorithmId, " +
                    "male, ageYears, heightCm, memberId) VALUES " +
                    "(1700000000000, 70.0, 500.0, 18.4, 'AA:BB', 'builtin-1', 1, 30, 175, '1')",
            )
            execSQL(
                "INSERT INTO health_logs (loggedAt, type, value, value2, label, note, memberId) " +
                    "VALUES (1700000000000, 'diet', 1500.0, 0.0, '午餐', '', '2')",
            )
            execSQL(
                "INSERT INTO devices (mac, name, boundAt, active, protocolId, algorithmId) " +
                    "VALUES ('AA:BB:CC:DD:EE:FF', '卧室秤', '2026-10-01', 1, 'okok-1', 'okok-bia-1')",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_V8, 8, true, AppDatabase.MIGRATION_7_8).use { db ->
            db.query(
                "SELECT weightKg, memberId, updatedAt, deletedAt, syncStatus, note, length(recordId) " +
                    "FROM measurements",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(70.0, c.getDouble(0), 1e-9)
                assertEquals("1", c.getString(1))
                assertEquals(1700000000000L, c.getLong(2))
                assertTrue(c.isNull(3))
                assertEquals(0, c.getInt(4))
                assertEquals("", c.getString(5))
                assertEquals(36, c.getInt(6))
            }
            db.query(
                "SELECT memberId, value, updatedAt, deletedAt FROM health_logs WHERE type = 'diet'",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("2", c.getString(0))
                assertEquals(1500.0, c.getDouble(1), 1e-9)
                assertEquals(1700000000000L, c.getLong(2))
                assertTrue(c.isNull(3))
            }
            db.query("SELECT name, updatedAt, unboundAt FROM devices").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("卧室秤", c.getString(0))
                assertEquals(0, c.getInt(1))
                assertTrue(c.isNull(2))
            }
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN " +
                    "('goals','members','pending_sync') ORDER BY name",
            ).use { c ->
                val names = mutableListOf<String>()
                while (c.moveToNext()) names += c.getString(0)
                assertEquals(listOf("goals", "members", "pending_sync"), names)
            }
        }
    }

    companion object {
        private const val TEST_DB = "migration-test.db"
        private const val TEST_DB_V8 = "migration-v8.db"
    }
}
