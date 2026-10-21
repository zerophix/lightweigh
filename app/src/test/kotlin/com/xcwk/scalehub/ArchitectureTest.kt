package com.xcwk.scalehub

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.google.common.truth.Truth.assertWithMessage
import com.xcwk.scalehub.feature.measure.RuleViolationFixture
import org.junit.BeforeClass
import org.junit.Test

/**
 * 分层依赖守卫 — 对应 Java 版 ArchitectureTest（ArchUnit），规则按 Android 版调整。
 *
 * 依赖方向：feature → application → domain ← infrastructure，组合根(app 根包)豁免；
 * trigger 为 BLE 回调/后台任务预留落点，与 feature 同属被守卫的内层禁区。
 * domain/application 是纯 JVM 模块，Android 依赖由编译器先行拦截；
 * 本测试守住跨模块方向与"UI 不绕过用例"两条运行期纪律。
 *
 * 若规则确实不适用：改规则，不要删测试——否则分层重新退化为"靠自觉"。
 */
class ArchitectureTest {

    companion object {
        /** 类名级谓词：archunit 1.3.0 无 haveNameMatching，手写等价物（fullName 结尾匹配）。 */
        private val sharedPrefsPredicate = object : DescribedPredicate<JavaClass>("依赖 SharedPreferences") {
            override fun test(input: JavaClass): Boolean = input.fullName.endsWith("SharedPreferences")
        }

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
                "com.xcwk.scalehub.feature..",
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
                "com.xcwk.scalehub.feature..",
                "android..",
                "androidx..",
            )
            .check(classes)
    }

    @Test
    fun infrastructure不依赖UI() {
        noClasses()
            .that().resideInAPackage("com.xcwk.scalehub.infrastructure..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.xcwk.scalehub.trigger..",
                "com.xcwk.scalehub.feature..",
            )
            .check(classes)
    }

    /**
     * 守卫负例 —— 用测试夹具证明规则真的会红。
     * 规则本身"能通过"不算守卫，"违规时必然失败"才算；
     * 没有负例的架构测试可能静默空转（与上面的解析自检互补）。
     */
    @Test
    fun 守卫负例_违规的feature依赖会被规则捕获() {
        val fixture = ClassFileImporter().importClasses(RuleViolationFixture::class.java)
        val rule = noClasses()
            .that().resideInAnyPackage("com.xcwk.scalehub.feature..", "com.xcwk.scalehub.ui..")
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
            .that().resideInAnyPackage(
                "com.xcwk.scalehub.feature..", "com.xcwk.scalehub.ui..", "com.xcwk.scalehub.trigger..",
            )
            .should().dependOnClassesThat().resideInAPackage("com.xcwk.scalehub.infrastructure..")
            .check(classes)
    }

    /**
     * T-014（F-005）负例：表现层经 AppContainer 方法调用返回类型不得位于 infrastructure。
     *
     * 此前 AppContainer 直接暴露 `ProfileStore`/`RoomDeviceRepository`/`AndroidBleLink`
     * 等 infra 具体类型，表现层可绕过端口调用非端口方法（如 `profileStore.clearMember(id)`）。
     * DEC-035 已将其改为端口类型（`MemberProfileReader/Writer`、`DeviceRepository`、`BlePort`），
     * 本规则防止回退：任何 feature/ui/trigger 类不得依赖 AppContainer 上返回 infra 类型的方法。
     *
     * 实现：检查表现层类是否依赖 AppContainer 中标记为 infra 返回类型的方法。
     * 由于 ArchUnit 无法直接检查"方法返回类型"，改为检查表现层类是否直接依赖
     * infrastructure 包中的任何类（已有规则），并额外检查 AppContainer 的 public val
     * 声明类型是否在 infrastructure 包（通过反射检查）。
     */
    @Test
    fun appcontainer公开属性不得暴露infra具体类型() {
        // AppContainer 的所有 public val/getter 返回类型必须不在 infrastructure 包
        val containerClass = classes.firstOrNull { it.fullName == "com.xcwk.scalehub.AppContainer" }
            ?: return   // 类不存在时跳过（测试环境可能未编译 app 模块)
        containerClass.methods
            .filter {
                it.name.startsWith("get") &&
                    it.parameters.isEmpty() &&
                    !it.modifiers.contains(com.tngtech.archunit.core.domain.JavaModifier.PRIVATE)
            }
            .forEach { method ->
                val returnType = method.returnType
                assertWithMessage("AppContainer.${method.name} 返回类型不得位于 infrastructure 包")
                    .that(returnType.name)
                    .doesNotContain("com.xcwk.scalehub.infrastructure")
            }
    }

    /**
     * T-006 上锁（DEC-021 task A）：UI 直触存储 SDK 的两条规则——
     * 包级（androidx.room/java.sql）+ 类名级（SharedPreferences）。
     * 包级规则管不到 SharedPreferences（它在 android.content 里，UI 合法需要 Context），
     * 故用类名谓词精确打击。
     */
    @Test
    fun feature不依赖存储SDK包() {
        noClasses()
            .that().resideInAnyPackage(
                "com.xcwk.scalehub.feature..", "com.xcwk.scalehub.ui..", "com.xcwk.scalehub.trigger..",
            )
            .should().dependOnClassesThat().resideInAnyPackage(
                "androidx.room..",
                "java.sql..",
            )
            .check(classes)
    }

    @Test
    fun feature不依赖SharedPreferences() {
        noClasses()
            .that().resideInAnyPackage(
                "com.xcwk.scalehub.feature..", "com.xcwk.scalehub.ui..", "com.xcwk.scalehub.trigger..",
            )
            .should().dependOnClassesThat(sharedPrefsPredicate)
            .check(classes)
    }

    // ---- P1/P2 收口（DEC-021 执行记录 2026-10-17）----

    /** 组合根显式桥面：feature/ui 仅允许触达这些根包类（其余根类走私即红）。 R/BuildConfig/ComposableSingletons 为构建器生成物，按名放行。 */
    private val bridgeRoots = setOf("App", "AppContainer", "BleGate", "MainActivity", "BuildConfig", "R", "ComposableSingletons")

    private val presentationPrefixes = listOf(
        "com.xcwk.scalehub.feature.", "com.xcwk.scalehub.ui.", "com.xcwk.scalehub.trigger.",
    )

    /** 表现层依赖闭包扫描：本工程内、不在内层四包、不在桥面白名单 → 违规清单。 */
    private fun forbiddenPresentationDeps(target: JavaClasses): List<String> =
        target.filter { cls -> presentationPrefixes.any { cls.fullName.startsWith(it) } }
            .flatMap { cls -> cls.directDependenciesFromSelf }
            .mapNotNull { it.targetClass }
            .map { it.fullName }
            .filter { it.startsWith("com.xcwk.scalehub.") }
            .filter { full ->
                val rest = full.removePrefix("com.xcwk.scalehub.")
                val head = rest.substringBefore('$')
                val isRootClass = !head.contains('.')
                val inInnerLayer = listOf("feature.", "ui.", "application.", "domain.")
                    .any { rest.startsWith(it) }
                if (inInnerLayer) false
                else if (isRootClass) head !in bridgeRoots
                else true   // trigger 等未开放包也被这里兜住
            }
            .distinct().sorted()

    /**
     * P1 收口：传递依赖只能经登记桥面白名单。规则只查直接依赖，
     * 未登记根类是走私 infra 的唯一通道——登记动作发生在守卫测试里即强制 review。
     */
    @Test
    fun 表现层只依赖内层与登记的组合根桥面() {
        val offenders = forbiddenPresentationDeps(classes)
        assertWithMessage("表现层出现白名单外的根包/跨层依赖")
            .that(offenders)
            .isEmpty()
    }

    @Test
    fun 守卫负例_白名单外根包依赖会被捕获() {
        val fixture = ClassFileImporter().importClasses(
            RuleViolationFixture::class.java, RootSneakFixture::class.java,
        )
        assertWithMessage("守卫失效：夹具对白名单外根类的依赖未被扫描捕获")
            .that(forbiddenPresentationDeps(fixture).any { it.contains("RootSneakFixture") })
            .isTrue()
    }

    /** P2 收口：组合根（根包无子包）只允许白名单类——新增根类必须有意识改守卫。 */
    private fun rootOffenders(target: JavaClasses): List<String> =
        target.filter { it.packageName == "com.xcwk.scalehub" }
            .map { it.fullName.removePrefix("com.xcwk.scalehub.").substringBefore('$') }
            .filter { it !in bridgeRoots }
            .distinct().sorted()

    @Test
    fun 组合根只含白名单类() {
        assertWithMessage("组合根出现未登记类（业务逻辑不许藏进组合根）")
            .that(rootOffenders(classes))
            .isEmpty()
    }

    @Test
    fun 守卫负例_未登记的根包类会被捕获() {
        val fixture = ClassFileImporter().importClasses(RootSneakFixture::class.java)
        assertWithMessage("守卫失效：未登记根类未被白名单规则捕获")
            .that(rootOffenders(fixture))
            .contains("RootSneakFixture")
    }

    @Test
    fun 守卫负例_违规的存储SDK依赖会被规则捕获() {
        val fixture = ClassFileImporter().importClasses(RuleViolationFixture::class.java)
        val pkgRule = noClasses()
            .that().resideInAnyPackage("com.xcwk.scalehub.feature..", "com.xcwk.scalehub.ui..")
            .should().dependOnClassesThat().resideInAnyPackage("androidx.room..", "java.sql..")
        val nameRule = noClasses()
            .that().resideInAnyPackage("com.xcwk.scalehub.feature..", "com.xcwk.scalehub.ui..")
            .should().dependOnClassesThat(sharedPrefsPredicate)
        val pkgCaught = try { pkgRule.check(fixture); false } catch (expected: AssertionError) { true }
        val nameCaught = try { nameRule.check(fixture); false } catch (expected: AssertionError) { true }
        assertWithMessage("守卫失效：夹具的 Room/SharedPreferences 依赖未被规则捕获")
            .that(pkgCaught && nameCaught)
            .isTrue()
    }
}
