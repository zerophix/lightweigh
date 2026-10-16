package com.xcwk.scalehub

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.google.common.truth.Truth.assertWithMessage
import com.xcwk.scalehub.trigger.ui.RuleViolationFixture
import org.junit.BeforeClass
import org.junit.Test

/**
 * 分层依赖守卫 — 对应 Java 版 ArchitectureTest（ArchUnit），规则按 Android 版调整。
 *
 * 依赖方向：trigger → application → domain ← infrastructure，组合根(app 根包)豁免。
 * domain/application 是纯 JVM 模块，Android 依赖由编译器先行拦截；
 * 本测试守住跨模块方向与"UI 不绕过用例"两条运行期纪律。
 *
 * 若规则确实不适用：改规则，不要删测试——否则分层重新退化为"靠自觉"。
 */
class ArchitectureTest {

    companion object {
        private lateinit var classes: JavaClasses

        /**
         * 生产类导入选项。
         * 注意：ArchUnit 内置 DoNotIncludeTests 只认 test/tests 路径段，
         * 而 AGP 单测输出在 kotlin-classes/debugUnitTest——测试类会漏进导入集。
         * 这里显式按路径补排除（UnitTest/androidTest 目录 + 负例夹具双保险）。
         */
        private val productionOnly = ImportOption { location ->
            ImportOption.DoNotIncludeTests().includes(location) &&
                !location.contains("UnitTest") &&
                !location.contains("androidTest") &&
                !location.contains("RuleViolationFixture")
        }

        @BeforeClass
        @JvmStatic
        fun importProductionClasses() {
            classes = ClassFileImporter()
                .withImportOption(productionOnly)
                .importPackages("com.xcwk.scalehub")
        }
    }

    /**
     * 守卫自检（抄自 Java 版的巧思）：ArchUnit 内置 ASM 有版本上限，
     * 解析不了当前 class 文件时会退化成"只记类名"——规则全部变成空断言、
     * 测试全绿但什么都没守。把静默退化变成显式失败。
     */
    @Test
    fun archunit能解析当前类文件() {
        val parsed = ClassFileImporter().importClass(String::class.java)
        assertWithMessage("依赖为空说明 ArchUnit 已退化成 simple import，升级 archunit 版本")
            .that(parsed.directDependenciesFromSelf)
            .isNotEmpty()
    }

    @Test
    fun domain不依赖任何外层与AndroidSDK() {
        noClasses()
            .that().resideInAPackage("com.xcwk.scalehub.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.xcwk.scalehub.application..",
                "com.xcwk.scalehub.infrastructure..",
                "com.xcwk.scalehub.trigger..",
                "android..",
                "androidx..",
            )
            .check(classes)
    }

    @Test
    fun application不依赖实现层与UI() {
        noClasses()
            .that().resideInAPackage("com.xcwk.scalehub.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.xcwk.scalehub.infrastructure..",
                "com.xcwk.scalehub.trigger..",
                "android..",
                "androidx..",
            )
            .check(classes)
    }

    @Test
    fun infrastructure不依赖UI() {
        noClasses()
            .that().resideInAPackage("com.xcwk.scalehub.infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("com.xcwk.scalehub.trigger..")
            .check(classes)
    }

    /**
     * 守卫负例 —— 用测试夹具证明规则真的会红。
     * 规则本身"能通过"不算守卫，"违规时必然失败"才算；
     * 没有负例的架构测试可能静默空转（与上面的解析自检互补）。
     */
    @Test
    fun 守卫负例_违规的trigger依赖会被规则捕获() {
        val fixture = ClassFileImporter().importClasses(RuleViolationFixture::class.java)
        val rule = noClasses()
            .that().resideInAPackage("com.xcwk.scalehub.trigger.ui..")
            .should().dependOnClassesThat().resideInAPackage("com.xcwk.scalehub.infrastructure..")
        val caughtViolation = try {
            rule.check(fixture)
            false
        } catch (expected: AssertionError) {
            true
        }
        assertWithMessage("守卫失效：夹具的违规依赖没有被规则捕获")
            .that(caughtViolation)
            .isTrue()
    }

    /** UI 不得绕过用例直接摸实现层 —— UDF 三件套之外只允许 import application/domain。 */
    @Test
    fun ui不绕过application直接依赖infrastructure() {
        noClasses()
            .that().resideInAPackage("com.xcwk.scalehub.trigger.ui..")
            .should().dependOnClassesThat().resideInAPackage("com.xcwk.scalehub.infrastructure..")
            .check(classes)
    }
}
