package com.xcwk.scalehub.feature.guide

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * T-100（guide: 登录后资料完善）纯逻辑单测。
 *
 * 覆盖验收里可自动判定的两块：
 * - 原型文案逐字固定（`01-login.html` 的 v-onboard-age / v-onboard-height / v-done）
 * - 表单校验与步进：年龄/身高边界、「不告知」合法、非法输入不推进
 *
 * 真实落库与登录主路径不在本组件，故不在单测范围。
 */
class ProfileSetupTest {

    // ---- 文案（原型 v-onboard-age / v-onboard-height / v-done） ----

    @Test
    fun `第 1 步文案逐字取自原型 v-onboard-age`() {
        assertThat(PROFILE_TITLE).isEqualTo("完善资料")
        assertThat(PROFILE_STEP1_COUNTER).isEqualTo("一步完成")
        assertThat(PROFILE_STEP1_LABEL).isEqualTo("身体基础信息")
        assertThat(PROFILE_STEP1_HEADLINE).isEqualTo("完善资料，让参考更适合你")
        assertThat(PROFILE_STEP1_DESC).isEqualTo("年龄与性别用于计算 BMI、体脂率的个性化参考区间")
        assertThat(PROFILE_AGE_LABEL).isEqualTo("年龄")
        assertThat(PROFILE_AGE_UNIT).isEqualTo("岁")
        assertThat(PROFILE_GENDER_LABEL).isEqualTo("性别")
        assertThat(PROFILE_NEXT).isEqualTo("下一步")
    }

    @Test
    fun `完成页文案逐字取自原型 v-done`() {
        assertThat(PROFILE_DONE_TITLE).isEqualTo("登录成功，欢迎回来")
        assertThat(PROFILE_DONE_BODY_LINE1).isEqualTo("资料已完善，现在可以连接体脂秤")
        assertThat(PROFILE_DONE_BODY_LINE2).isEqualTo("开始第一次测量了。")
    }

    // ---- 性别选项 ----

    @Test
    fun `性别三项文案与顺序与原型一致`() {
        assertThat(GenderOption.entries.map { it.label })
            .containsExactly("男", "女", "不告知")
            .inOrder()
    }

    @Test
    fun `不告知是合法选项不阻塞下一步`() {
        assertThat(canGoNextStep("30", GenderOption.Undisclosed)).isTrue()
        assertThat(canGoNextStep("30", GenderOption.Male)).isTrue()
        assertThat(canGoNextStep("30", GenderOption.Female)).isTrue()
    }

    @Test
    fun `未选性别时下一步不可用`() {
        assertThat(canGoNextStep("30", null)).isFalse()
    }

    // ---- 年龄校验 ----

    @Test
    fun `年龄合法区间两端都含`() {
        assertThat(isAgeValid(PROFILE_AGE_MIN.toString())).isTrue()
        assertThat(isAgeValid(PROFILE_AGE_MAX.toString())).isTrue()
        assertThat(isAgeValid("30")).isTrue()
    }

    @Test
    fun `年龄越界或非数字不合法`() {
        assertThat(isAgeValid((PROFILE_AGE_MIN - 1).toString())).isFalse()
        assertThat(isAgeValid((PROFILE_AGE_MAX + 1).toString())).isFalse()
        assertThat(isAgeValid("")).isFalse()
        assertThat(isAgeValid("abc")).isFalse()
        assertThat(isAgeValid("-1")).isFalse()
        assertThat(isAgeValid("30.5")).isFalse()
    }

    @Test
    fun `年龄首尾空格不影响判定`() {
        assertThat(isAgeValid(" 30 ")).isTrue()
    }

    @Test
    fun `年龄越界时下一步不可用`() {
        assertThat(canGoNextStep("9", GenderOption.Female)).isFalse()
        assertThat(canGoNextStep("101", GenderOption.Female)).isFalse()
    }

    // ---- 身高校验 ----

    @Test
    fun `身高合法区间两端都含`() {
        assertThat(isHeightValid(PROFILE_HEIGHT_MIN_CM.toString())).isTrue()
        assertThat(isHeightValid(PROFILE_HEIGHT_MAX_CM.toString())).isTrue()
        assertThat(isHeightValid("168")).isTrue()
    }

    @Test
    fun `身高越界或非数字不合法`() {
        assertThat(isHeightValid((PROFILE_HEIGHT_MIN_CM - 1).toString())).isFalse()
        assertThat(isHeightValid((PROFILE_HEIGHT_MAX_CM + 1).toString())).isFalse()
        assertThat(isHeightValid("")).isFalse()
        assertThat(isHeightValid("一米七")).isFalse()
    }

    @Test
    fun `身高越界时完成按钮不可用`() {
        assertThat(canCompleteProfile("89")).isFalse()
        assertThat(canCompleteProfile("221")).isFalse()
        assertThat(canCompleteProfile("168")).isTrue()
    }

    // ---- 步进 ----

    @Test
    fun `年龄性别身高齐全时下一步直达完成页`() {
        val state = ProfileSetupUiState()
            .reduce(ProfileSetupIntent.AgeChanged("30"))
            .reduce(ProfileSetupIntent.GenderSelected(GenderOption.Undisclosed))
            .reduce(ProfileSetupIntent.HeightChanged("168"))
            .reduce(ProfileSetupIntent.Next)
        assertThat(state.step).isEqualTo(ProfileSetupStep.Done)
        assertThat(state.gender).isEqualTo(GenderOption.Undisclosed)
    }

    @Test
    fun `缺身高时下一步原地不动`() {
        val state = ProfileSetupUiState()
            .reduce(ProfileSetupIntent.AgeChanged("30"))
            .reduce(ProfileSetupIntent.GenderSelected(GenderOption.Male))
            .reduce(ProfileSetupIntent.Next)
        assertThat(state.step).isEqualTo(ProfileSetupStep.AgeGender)
    }

    @Test
    fun `校验未通过时下一步原地不动`() {
        val noGender = ProfileSetupUiState()
            .reduce(ProfileSetupIntent.AgeChanged("30"))
            .reduce(ProfileSetupIntent.Next)
        assertThat(noGender.step).isEqualTo(ProfileSetupStep.AgeGender)

        val badAge = ProfileSetupUiState()
            .reduce(ProfileSetupIntent.AgeChanged("200"))
            .reduce(ProfileSetupIntent.GenderSelected(GenderOption.Male))
            .reduce(ProfileSetupIntent.Next)
        assertThat(badAge.step).isEqualTo(ProfileSetupStep.AgeGender)
    }

    @Test
    fun `身高合法时完成进完成页`() {
        val state = filled("168").reduce(ProfileSetupIntent.Complete)
        assertThat(state.step).isEqualTo(ProfileSetupStep.Done)
    }

    @Test
    fun `身高非法时完成原地不动`() {
        val state = filled("300").reduce(ProfileSetupIntent.Complete)
        assertThat(state.step).isEqualTo(ProfileSetupStep.AgeGender)
    }

    @Test
    fun `稍后补充直接进完成页且不校验身高`() {
        val state = ProfileSetupUiState().reduce(ProfileSetupIntent.SkipProfile)
        assertThat(state.step).isEqualTo(ProfileSetupStep.Done)
    }

    @Test
    fun `输入意图只改草稿不影响步进`() {
        val state = ProfileSetupUiState()
            .reduce(ProfileSetupIntent.AgeChanged("41"))
            .reduce(ProfileSetupIntent.GenderSelected(GenderOption.Female))
        assertThat(state.step).isEqualTo(ProfileSetupStep.AgeGender)
        assertThat(state.ageText).isEqualTo("41")
        assertThat(state.gender).isEqualTo(GenderOption.Female)
        assertThat(state.heightText).isEmpty()
    }

    @Test
    fun `默认态停在第 1 步且无选择`() {
        val state = ProfileSetupUiState()
        assertThat(state.step).isEqualTo(ProfileSetupStep.AgeGender)
        assertThat(state.gender).isNull()
        assertThat(state.ageText).isEmpty()
    }

    /** 造一个「年龄/性别/身高都填好」的状态。 */
    private fun filled(heightText: String): ProfileSetupUiState = ProfileSetupUiState()
        .reduce(ProfileSetupIntent.AgeChanged("30"))
        .reduce(ProfileSetupIntent.GenderSelected(GenderOption.Male))
        .reduce(ProfileSetupIntent.HeightChanged(heightText))
}
