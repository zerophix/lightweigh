package com.xcwk.scalehub.feature.member

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.regex.Pattern

/**
 * T-086 成员管理扩展四态（呈现式组件）纯 JVM 单测。
 *
 * 覆盖验收中可自动判定的三类事实：
 * 1. 文案逐字取自原型13 四个 view（AC-J1-13 then 原文，改一个字即红）；
 * 2. 表单校验与状态推进为纯函数：校验失败不进保存中；保存失败保留草稿并可重试成功；
 *    「填写内容仍保留在本页」由草稿不变来保证；
 * 3. 源码守卫：不接触网络/数据库/剪贴板，无硬编码颜色与裸 dp，复用 Qk 设计系统。
 *
 * 视觉呈现（排版/配色落位）不在此测——真机走查属人类验收，不在此伪称。
 */
class MemberProfileTest {

    private val today = LocalDate.of(2026, 10, 24)

    private fun validDraft() = MemberProfileDraft(
        nickname = "林悦",
        birthDate = "1996-05-12",
        gender = MemberGender.Female,
        heightCm = "168",
        weightTargetKg = "58",
    )

    // ---- 1. 文案逐字（AC-J1-13 then 第 1..4 条）----

    @Test
    fun `v-edit-member 文案逐字取自原型`() {
        assertThat(MemberProfileText.editTitle("林悦")).isEqualTo("编辑资料 · 林悦")
        assertThat(MemberProfileText.Nickname).isEqualTo("昵称")
        assertThat(MemberProfileText.BirthDate).isEqualTo("出生日期")
        assertThat(MemberProfileText.Gender).isEqualTo("性别")
        assertThat(MemberGender.labels).containsExactly("男", "女", "不告知")
        assertThat(MemberGender.Undisclosed.label).isEqualTo("不告知")
        assertThat(MemberProfileText.Height).isEqualTo("身高")
        assertThat(MemberProfileText.HeightUnit).isEqualTo("cm")
        assertThat(MemberProfileText.WeightTarget).isEqualTo("体重目标")
        assertThat(MemberProfileText.WeightTargetUnit).isEqualTo("kg")
        assertThat(MemberProfileText.Save).isEqualTo("保存")
        // 原型昵称行的当前值即成员名
        assertThat(validDraft().nickname).isEqualTo("林悦")
        // 原型出生日期/身高/体重目标的示例值
        assertThat(validDraft().birthDate).isEqualTo("1996-05-12")
        assertThat(validDraft().heightCm).isEqualTo("168")
        assertThat(validDraft().weightTargetKg).isEqualTo("58")
    }

    @Test
    fun `v-savefail 文案逐字取自原型`() {
        assertThat(MemberProfileText.editTitle("林悦")).isEqualTo("编辑资料 · 林悦")
        assertThat(MemberProfileText.SaveFailTitle).isEqualTo("资料保存失败")
        assertThat(MemberProfileText.SaveFailLine1).isEqualTo("填写内容仍保留在本页。")
        assertThat(MemberProfileText.SaveFailLine2).isEqualTo("请检查网络后重试。")
        assertThat(MemberProfileText.RetrySave).isEqualTo("重试保存")
        assertThat(MemberProfileText.BackToEdit).isEqualTo("返回修改")
    }

    @Test
    fun `v-member-norecord 文案逐字取自原型`() {
        assertThat(MemberProfileText.NavRecord).isEqualTo("测量记录")
        assertThat(MemberProfileText.viewerLine("妈妈")).isEqualTo("当前查看：妈妈")
        assertThat(MemberProfileText.DataIndependent).isEqualTo("数据独立保存")
        assertThat(MemberProfileText.noRecordTitle("妈妈")).isEqualTo("妈妈还没有测量记录")
        assertThat(MemberProfileText.noRecordLine1("妈妈")).isEqualTo("用同一台体脂秤为妈妈测一次，")
        assertThat(MemberProfileText.noRecordLine2()).isEqualTo("数据会自动归到她名下。")
        assertThat(MemberProfileText.measureAction("妈妈")).isEqualTo("为妈妈测量")
    }

    @Test
    fun `v-member-nodevice 文案逐字取自原型`() {
        assertThat(MemberProfileText.NavDevice).isEqualTo("设备管理")
        assertThat(MemberProfileText.deviceViewerLine("爸爸")).isEqualTo("当前查看：爸爸 · 设备可全家共用")
        assertThat(MemberProfileText.noDeviceTitle("爸爸")).isEqualTo("还没有为爸爸测量过")
        assertThat(MemberProfileText.NoDeviceLine1).isEqualTo("同一台体脂秤可为全家测量，")
        assertThat(MemberProfileText.noDeviceLine2("爸爸")).isEqualTo("爸爸上秤一次即可。")
        assertThat(MemberProfileText.measureAction("爸爸")).isEqualTo("为爸爸测量")
    }

    // ---- 2. 表单校验（纯函数）----

    @Test
    fun `合法草稿通过校验`() {
        assertThat(validateMemberProfileDraft(validDraft(), today).isValid).isTrue()
    }

    @Test
    fun `空昵称与超长昵称被拦截`() {
        assertThat(validateMemberProfileDraft(validDraft().copy(nickname = "   "), today).nickname)
            .isEqualTo("昵称不能为空")
        assertThat(
            validateMemberProfileDraft(validDraft().copy(nickname = "一".repeat(21)), today).nickname,
        ).isEqualTo("昵称最多 20 个字")
        // 20 字正好合法
        assertThat(
            validateMemberProfileDraft(validDraft().copy(nickname = "一".repeat(20)), today).nickname,
        ).isNull()
    }

    @Test
    fun `出生日期必须是 yyyy-MM-dd 的真实日期`() {
        assertThat(validateMemberProfileDraft(validDraft().copy(birthDate = ""), today).birthDate)
            .isEqualTo("请选择出生日期")
        assertThat(validateMemberProfileDraft(validDraft().copy(birthDate = "1996-5-12"), today).birthDate)
            .isEqualTo("请按 1996-05-12 的格式填写")
        // 语法对但日期不存在（2 月 30 日）
        assertThat(validateMemberProfileDraft(validDraft().copy(birthDate = "1996-02-30"), today).birthDate)
            .isEqualTo("请按 1996-05-12 的格式填写")
        // 出生年超出 1930..今年
        assertThat(validateMemberProfileDraft(validDraft().copy(birthDate = "1929-12-31"), today).birthDate)
            .isEqualTo("出生日期超出范围")
        assertThat(validateMemberProfileDraft(validDraft().copy(birthDate = "2027-01-01"), today).birthDate)
            .isEqualTo("出生日期超出范围")
    }

    @Test
    fun `身高必填且在 100 到 230 之间`() {
        assertThat(validateMemberProfileDraft(validDraft().copy(heightCm = ""), today).heightCm)
            .isEqualTo("请填写身高")
        assertThat(validateMemberProfileDraft(validDraft().copy(heightCm = "abc"), today).heightCm)
            .isEqualTo("身高请填数字")
        assertThat(validateMemberProfileDraft(validDraft().copy(heightCm = "99"), today).heightCm)
            .isEqualTo("身高需在 100-230 cm")
        assertThat(validateMemberProfileDraft(validDraft().copy(heightCm = "231"), today).heightCm)
            .isEqualTo("身高需在 100-230 cm")
        assertThat(validateMemberProfileDraft(validDraft().copy(heightCm = "230"), today).heightCm).isNull()
    }

    @Test
    fun `体重目标可留空但填了要合法`() {
        assertThat(validateMemberProfileDraft(validDraft().copy(weightTargetKg = ""), today).weightTargetKg)
            .isNull()
        assertThat(validateMemberProfileDraft(validDraft().copy(weightTargetKg = "19"), today).weightTargetKg)
            .isEqualTo("体重目标需在 20-300 kg")
        assertThat(validateMemberProfileDraft(validDraft().copy(weightTargetKg = "301"), today).weightTargetKg)
            .isEqualTo("体重目标需在 20-300 kg")
        assertThat(validateMemberProfileDraft(validDraft().copy(weightTargetKg = "58"), today).weightTargetKg)
            .isNull()
    }

    @Test
    fun `出生日期与表单文本互转`() {
        assertThat(formatBirthDate(1996, 5, 12)).isEqualTo("1996-05-12")
        assertThat(formatBirthDate(0, 0, 0)).isEmpty()
        assertThat(parseBirthDate("1996-05-12")).isEqualTo(Triple(1996, 5, 12))
        assertThat(parseBirthDate("1996-05-12 ")).isEqualTo(Triple(1996, 5, 12))
        assertThat(parseBirthDate("1996/05/12")).isNull()
    }

    // ---- 3. 状态推进（编辑 → 保存中 → 失败 → 重试 → 成功）----

    @Test
    fun `保存成功推进到已保存`() {
        var state = reduceMemberProfile(MemberProfileState(draft = validDraft()), MemberProfileIntent.Submit, today)
        assertThat(state.saving).isTrue()
        assertThat(state.showSaveFail).isFalse()
        assertThat(state.saved).isFalse()

        state = reduceMemberProfile(state, MemberProfileIntent.SaveResult(ok = true), today)
        assertThat(state.saved).isTrue()
        assertThat(state.message).isEqualTo("资料已保存")
    }

    @Test
    fun `校验不通过不进入保存中`() {
        val bad = validDraft().copy(heightCm = "99")
        var state = reduceMemberProfile(MemberProfileState(draft = bad), MemberProfileIntent.Submit, today)
        assertThat(state.saving).isFalse()
        assertThat(state.showSaveFail).isFalse()
        assertThat(state.errors.heightCm).isEqualTo("身高需在 100-230 cm")

        // 改字段即清错，再提交可进保存中
        state = reduceMemberProfile(state, MemberProfileIntent.HeightChanged("168"), today)
        assertThat(state.errors.isValid).isTrue()
        state = reduceMemberProfile(state, MemberProfileIntent.Submit, today)
        assertThat(state.saving).isTrue()
    }

    @Test
    fun `保存失败保留草稿并可重试成功`() {
        val draft = validDraft()
        var state = reduceMemberProfile(MemberProfileState(draft = draft), MemberProfileIntent.Submit, today)
        state = reduceMemberProfile(state, MemberProfileIntent.SaveResult(ok = false), today)

        // 失败页：草稿一字不动（对应「填写内容仍保留在本页。」）
        assertThat(state.showSaveFail).isTrue()
        assertThat(state.saved).isFalse()
        assertThat(state.failedAttempts).isEqualTo(1)
        assertThat(state.draft).isEqualTo(draft)

        // 第一次重试仍失败
        state = reduceMemberProfile(state, MemberProfileIntent.Retry, today)
        assertThat(state.saving).isTrue()
        assertThat(state.showSaveFail).isTrue()
        state = reduceMemberProfile(state, MemberProfileIntent.SaveResult(ok = false), today)
        assertThat(state.failedAttempts).isEqualTo(2)
        assertThat(state.draft).isEqualTo(draft)

        // 第二次重试成功
        state = reduceMemberProfile(state, MemberProfileIntent.Retry, today)
        state = reduceMemberProfile(state, MemberProfileIntent.SaveResult(ok = true), today)
        assertThat(state.saved).isTrue()
        assertThat(state.showSaveFail).isFalse()
        assertThat(state.failedAttempts).isEqualTo(2)
    }

    @Test
    fun `返回修改回到编辑页且草稿不变`() {
        var state = reduceMemberProfile(MemberProfileState(draft = validDraft()), MemberProfileIntent.Submit, today)
        state = reduceMemberProfile(state, MemberProfileIntent.SaveResult(ok = false), today)
        val failed = state

        state = reduceMemberProfile(state, MemberProfileIntent.BackToEdit, today)
        assertThat(state.showSaveFail).isFalse()
        assertThat(state.saving).isFalse()
        assertThat(state.draft).isEqualTo(failed.draft)
        assertThat(state.failedAttempts).isEqualTo(1)
    }

    @Test
    fun `保存中重复提交与非法重试被忽略`() {
        var state = reduceMemberProfile(MemberProfileState(draft = validDraft()), MemberProfileIntent.Submit, today)
        val saving = state
        assertThat(reduceMemberProfile(state, MemberProfileIntent.Submit, today)).isEqualTo(saving)

        // 编辑态下发 Retry 无效（重试只在失败页可用）
        val editing = reduceMemberProfile(MemberProfileState(draft = validDraft()), MemberProfileIntent.Retry, today)
        assertThat(editing.saving).isFalse()
        assertThat(editing.saved).isFalse()

        // 失败页改字段等价于返回修改
        state = reduceMemberProfile(saving, MemberProfileIntent.SaveResult(ok = false), today)
        state = reduceMemberProfile(state, MemberProfileIntent.NicknameChanged("林小悦"), today)
        assertThat(state.showSaveFail).isFalse()
        assertThat(state.draft.nickname).isEqualTo("林小悦")
    }

    @Test
    fun `只有保存结果能推进到已保存`() {
        val start = reduceMemberProfile(
            MemberProfileState(draft = validDraft()),
            MemberProfileIntent.Submit,
            today,
        )
        assertThat(start.saved).isFalse()
        assertThat(reduceMemberProfile(start, MemberProfileIntent.Retry, today).saved).isFalse()
        assertThat(reduceMemberProfile(start, MemberProfileIntent.BackToEdit, today).saved).isFalse()
    }

    // ---- 4. 源码守卫（AC-J1-13 forbidden：硬编码颜色间距 / 直连 IO）----

    private fun sourceOf(fileName: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val current = dir
            val f = File(current, "app/src/main/kotlin/com/xcwk/scalehub/feature/member/$fileName")
            if (f.exists()) return f.readText(Charsets.UTF_8)
            dir = current.parentFile
        }
        error("$fileName 未找到（从 ${System.getProperty("user.dir")} 向上探测失败）")
    }

    /** 去掉注释后再断言：文档里允许出现约束原文，代码本身不许出现违规符号。 */
    private fun codeOnly(source: String): String =
        source
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""(?m)//.*$"""), "")

    @Test
    fun `目标文件无硬编码颜色与裸 dp`() {
        val code = codeOnly(sourceOf("MemberProfile.kt"))
        assertThat(code).doesNotMatch(Pattern.compile("""Color\(0x[0-9A-Fa-f]{6,8}\)"""))
        // 与 gov-prototype-verify 的 HARDCODED_DP 同口径：Qk token 之外不许写 NN.dp
        assertThat(code).doesNotMatch(Pattern.compile("""(?<!Qk)\b\d+\.dp\b"""))
    }

    @Test
    fun `目标文件不接触网络数据库或剪贴板`() {
        val code = codeOnly(sourceOf("MemberProfile.kt"))
        assertThat(code).doesNotContain("java.net")
        assertThat(code).doesNotContain("okhttp")
        assertThat(code).doesNotContain("retrofit")
        assertThat(code).doesNotContain("Room")
        assertThat(code).doesNotContain("SQLite")
        assertThat(code).doesNotContain("Clipboard")
        assertThat(code).doesNotContain("startActivity")
    }

    @Test
    fun `目标文件复用 Qk 设计系统组件`() {
        val source = sourceOf("MemberProfile.kt")
        assertThat(source).contains("QkCard")
        assertThat(source).contains("QkButton")
        assertThat(source).contains("QkEmptyState")
        assertThat(source).contains("QkLoadErrorState")
        assertThat(source).contains("QkColor")
        assertThat(source).contains("QkSpacing")
    }

    @Test
    fun `失败页由失败相位门控`() {
        val code = codeOnly(sourceOf("MemberProfile.kt"))
        // 保存失败页只在 showSaveFail 时渲染，不允许无条件露出重试按钮
        assertThat(code).contains("if (state.showSaveFail)")
    }
}
