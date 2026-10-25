package com.xcwk.scalehub.feature.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkSegmented
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkComponentSize
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing

/**
 * guide — 登录后资料完善两步表单 + 完成页（T-100 · Wave 1）。
 *
 * ## 原型
 * `design/prototypes/interactive/01-login.html`：
 * - `v-onboard-age` 第 1/2 步：年龄 + 性别（男 / 女 / 不告知）
 * - `v-onboard-height` 第 2/2 步：身高（示例 168 cm）
 * - `v-done` 完成页：登录成功 + 资料已完善，可开始第一次测量
 *
 * ## 验收（AC-J1-04 引导资料表单补全）
 * - 两步表单字段与文案逐字取自原型，常量见本文件顶部，JVM 单测钉死
 * - 「不告知」是合法性别选项，不阻塞下一步（[canGoNextStep]）
 * - 年龄/身高越界时下一步不可用：边界与判定都是纯函数（[isAgeValid] / [isHeightValid]）
 * - 资料保存与落库不在 UI：完成时经 [ProfileSetupFlow] 的 onProfileCompleted 上交宿主
 *
 * ## 边界（呈现式组件）
 * 不建 ViewModel、不直接落库、不读网络；输入草稿只在 [ProfileSetupUiState] 里流转。
 */

// ---- 静态文案（逐字取自原型，JVM 单测校验） ----

// 两步共用
const val PROFILE_TITLE = "完善资料"
const val PROFILE_STEP1_COUNTER = "一步完成"

// v-onboard-age
const val PROFILE_STEP1_LABEL = "身体基础信息"
const val PROFILE_STEP1_HEADLINE = "完善资料，让参考更适合你"
const val PROFILE_STEP1_DESC = "年龄与性别用于计算 BMI、体脂率的个性化参考区间"
const val PROFILE_AGE_LABEL = "年龄"
const val PROFILE_AGE_UNIT = "岁"
const val PROFILE_GENDER_LABEL = "性别"
const val PROFILE_NEXT = "下一步"

// v-onboard-height
const val PROFILE_HEIGHT_LABEL = "身高"
const val PROFILE_SKIP = "稍后在我的里补充"

// v-done
const val PROFILE_DONE_TITLE = "登录成功，欢迎回来"
const val PROFILE_DONE_BODY_LINE1 = "资料已完善，现在可以连接体脂秤"
const val PROFILE_DONE_BODY_LINE2 = "开始第一次测量了。"
const val PROFILE_DONE_RESTART = "重新走一遍流程"
const val PROFILE_DONE_BACK_TO_LOGIN = "回到登录主页"

// ---- 校验边界（纯函数，JVM 单测钉死） ----

/** 年龄合法区间（岁）：覆盖青少年到高龄，两端都含。 */
const val PROFILE_AGE_MIN = 10
const val PROFILE_AGE_MAX = 100

/** 身高合法区间（cm）：两端都含，超出是输入错误而非「不告知」。 */
const val PROFILE_HEIGHT_MIN_CM = 90
const val PROFILE_HEIGHT_MAX_CM = 220

/**
 * 性别选项。顺序与文案取自原型 v-onboard-age 的分段控件；
 * [Undisclosed]（不告知）是**合法**取值，选了它同样可以进下一步。
 */
enum class GenderOption(val label: String) {
    Male("男"),
    Female("女"),
    Undisclosed("不告知"),
}

/** 年龄文本是否合法：非数字、空串、越界都不合法。 */
fun isAgeValid(ageText: String): Boolean {
    val age = ageText.trim().toIntOrNull() ?: return false
    return age in PROFILE_AGE_MIN..PROFILE_AGE_MAX
}

/** 身高文本是否合法：口径同 [isAgeValid]。 */
fun isHeightValid(heightText: String): Boolean {
    val height = heightText.trim().toIntOrNull() ?: return false
    return height in PROFILE_HEIGHT_MIN_CM..PROFILE_HEIGHT_MAX_CM
}

/** 第 1 步「下一步」可用性：年龄合法且性别已选（含「不告知」）。 */
fun canGoNextStep(ageText: String, gender: GenderOption?): Boolean = isAgeValid(ageText) && gender != null

/** 第 2 步「完成，开始使用」可用性：身高合法才可完成（跳过走另一个按钮）。 */
fun canCompleteProfile(heightText: String): Boolean = isHeightValid(heightText)

// ---- 状态机（纯函数，JVM 单测钉死） ----

/** 资料完善三态（与原型 v-onboard-age / v-onboard-height / v-done 对应）。 */
enum class ProfileSetupStep { AgeGender, Done }

/**
 * 资料完善呈现态。
 *
 * [ageText] / [heightText] 保留原始输入文本（不提前转 Int），
 * 这样非法输入可以停在原稿让用户改，校验统一走 [canGoNextStep] / [canCompleteProfile]。
 */
data class ProfileSetupUiState(
    val step: ProfileSetupStep = ProfileSetupStep.AgeGender,
    val ageText: String = "",
    val gender: GenderOption? = null,
    val heightText: String = "",
)

/** 输入与步进意图；保存资料不是意图，是宿主在完成回调里做的事。 */
sealed interface ProfileSetupIntent {
    data class AgeChanged(val text: String) : ProfileSetupIntent
    data class GenderSelected(val gender: GenderOption) : ProfileSetupIntent
    data class HeightChanged(val text: String) : ProfileSetupIntent
    data object Next : ProfileSetupIntent
    data object Complete : ProfileSetupIntent
    data object SkipProfile : ProfileSetupIntent
}

/**
 * 纯状态转移 —— 步进口径：
 * - [ProfileSetupIntent.Next] 只在 [canGoNextStep] 通过时进第 2 步（「不告知」已选也算通过）
 * - [ProfileSetupIntent.Complete] 只在 [canCompleteProfile] 通过时进完成页
 * - [ProfileSetupIntent.SkipProfile]（稍后在我的里补充）直接进完成页，不校验身高
 */
fun ProfileSetupUiState.reduce(intent: ProfileSetupIntent): ProfileSetupUiState = when (intent) {
    is ProfileSetupIntent.AgeChanged -> copy(ageText = intent.text)
    is ProfileSetupIntent.GenderSelected -> copy(gender = intent.gender)
    is ProfileSetupIntent.HeightChanged -> copy(heightText = intent.text)
    ProfileSetupIntent.Next ->
        // 年龄/性别/身高在同一屏收齐：填全直接进完成页，不再多一步
        if (step == ProfileSetupStep.AgeGender &&
            canGoNextStep(ageText, gender) &&
            canCompleteProfile(heightText)
        ) {
            copy(step = ProfileSetupStep.Done)
        } else {
            this
        }

    ProfileSetupIntent.Complete ->
        if (canCompleteProfile(heightText) && canGoNextStep(ageText, gender)) {
            copy(step = ProfileSetupStep.Done)
        } else {
            this
        }

    ProfileSetupIntent.SkipProfile -> copy(step = ProfileSetupStep.Done)
}

// ---- 呈现层 ----

/**
 * 资料完善路由。
 *
 * 外部交接点（落库/埋点都在宿主侧）：
 * - [onProfileCompleted] 「完成，开始使用」通过校验后上交年龄、性别、身高（cm）
 * - [onSkipProfile] 「稍后在我的里补充」：宿主记「资料未完善」并进主流程
 * - [onBack] 第 2 步返回第 1 步 / 完成页无返回（由宿主决定）
 */
@Composable
fun ProfileSetupFlow(
    state: ProfileSetupUiState,
    onIntent: (ProfileSetupIntent) -> Unit,
    modifier: Modifier = Modifier,
    onProfileCompleted: (age: Int, gender: GenderOption, heightCm: Int) -> Unit = { _, _, _ -> },
    onSkipProfile: () -> Unit = {},
    onRestart: () -> Unit = {},
    onBackToLogin: () -> Unit = {},
    onFinished: () -> Unit = {},
) {
    when (state.step) {
        ProfileSetupStep.AgeGender -> ProfileAgeGenderScreen(
            state = state,
            onIntent = onIntent,
            modifier = modifier,
            onSkipProfile = onSkipProfile,
        )

        ProfileSetupStep.Done -> ProfileDoneScreen(
            onEnterApp = onFinished,
            onRestart = onRestart,
            onBackToLogin = onBackToLogin,
            modifier = modifier,
        )
    }
}

/** v-onboard-age 第 1/2 步：年龄 + 性别。 */
@Composable
fun ProfileAgeGenderScreen(
    state: ProfileSetupUiState,
    onIntent: (ProfileSetupIntent) -> Unit,
    modifier: Modifier = Modifier,
    onSkipProfile: () -> Unit = {},
) {
    ProfileStepScaffold(
        counter = PROFILE_STEP1_COUNTER,
        stepLabel = PROFILE_STEP1_LABEL,
        icon = QkIcons.Account,
        headline = PROFILE_STEP1_HEADLINE,
        desc = PROFILE_STEP1_DESC,
        modifier = modifier,
        footer = {
            // 禁用态按钮不解释原因时，用户只会反复点它：给出下一步条件 + 允许稍后补。
            if (!(canGoNextStep(state.ageText, state.gender) && canCompleteProfile(state.heightText))) {
                Text(
                    text = "填写年龄、性别和身高后即可继续",
                    modifier = Modifier.fillMaxWidth().padding(bottom = QkSpacing.Sm),
                    style = MaterialTheme.typography.labelSmall,
                    color = QkColor.Muted,
                )
            }
            QkButton(
                text = PROFILE_NEXT,
                modifier = Modifier.fillMaxWidth(),
                enabled = canGoNextStep(state.ageText, state.gender) && canCompleteProfile(state.heightText),
                onClick = { onIntent(ProfileSetupIntent.Next) },
            )
            QkButton(
                text = "稍后再说",
                style = QkButtonStyle.Ghost,
                modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
                onClick = onSkipProfile,
            )
        },
    ) {
        QkCard {
            Text(
                text = PROFILE_AGE_LABEL,
                style = MaterialTheme.typography.labelMedium,
                color = QkColor.FieldLabel,
            )
            Spacer(Modifier.height(QkSpacing.Sm))
            Row(verticalAlignment = Alignment.Bottom) {
                QkTextField(
                    value = state.ageText,
                    onValueChange = { onIntent(ProfileSetupIntent.AgeChanged(it)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    text = PROFILE_AGE_UNIT,
                    modifier = Modifier.padding(start = QkSpacing.Sm, bottom = QkSpacing.Md),
                    style = MaterialTheme.typography.bodyMedium,
                    color = QkColor.Sub,
                )
            }

            Spacer(Modifier.height(QkSpacing.Lg))
            Text(
                text = PROFILE_GENDER_LABEL,
                style = MaterialTheme.typography.labelMedium,
                color = QkColor.FieldLabel,
            )
            Spacer(Modifier.height(QkSpacing.Sm))
            QkSegmented(
                items = GenderOption.entries.map { it.label },
                selectedIndex = state.gender?.ordinal ?: NO_SELECTION,
                onSelect = { index -> onIntent(ProfileSetupIntent.GenderSelected(GenderOption.entries[index])) },
            )

            Spacer(Modifier.height(QkSpacing.Lg))
            Text(
                text = "身高",
                style = MaterialTheme.typography.labelMedium,
                color = QkColor.FieldLabel,
            )
            Spacer(Modifier.height(QkSpacing.Sm))
            Row(verticalAlignment = Alignment.Bottom) {
                QkTextField(
                    value = state.heightText,
                    onValueChange = { onIntent(ProfileSetupIntent.HeightChanged(it)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    text = "cm",
                    modifier = Modifier.padding(start = QkSpacing.Sm, bottom = QkSpacing.Md),
                    style = MaterialTheme.typography.bodyMedium,
                    color = QkColor.Sub,
                )
            }
        }
    }
}

/** v-done 完成页：登录成功 + 资料已完善。 */
@Composable
fun ProfileDoneScreen(
    modifier: Modifier = Modifier,
    onEnterApp: () -> Unit = {},
    onRestart: () -> Unit = {},
    onBackToLogin: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(QkComponentSize.SkeletonBlock)
                .clip(CircleShape)
                .background(QkColor.Mint),
            contentAlignment = Alignment.Center,
        ) {
            QkIcon(QkIcons.Habit, tint = QkColor.Primary, size = QkSpacing.Xl)
        }
        Text(
            text = PROFILE_DONE_TITLE,
            modifier = Modifier.padding(top = QkSpacing.Xl),
            style = MaterialTheme.typography.titleLarge,
            color = QkColor.Fg,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(QkSpacing.Sm))
        Text(
            text = PROFILE_DONE_BODY_LINE1,
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
            textAlign = TextAlign.Center,
        )
        Text(
            text = PROFILE_DONE_BODY_LINE2,
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        // 完成页必须有一个"进入应用"的正向出口：原实现只有"重新走一遍/回登录"，
        // 用户填完资料反而没有前进按钮。
        QkButton(
            text = "开始使用轻衡",
            modifier = Modifier.fillMaxWidth(),
            onClick = onEnterApp,
        )
        Spacer(Modifier.height(QkSpacing.Md))
        QkButton(
            text = PROFILE_DONE_RESTART,
            modifier = Modifier.fillMaxWidth(),
            style = QkButtonStyle.Ghost,
            onClick = onRestart,
        )
        Spacer(Modifier.height(QkSpacing.Sm))
        QkButton(
            text = PROFILE_DONE_BACK_TO_LOGIN,
            modifier = Modifier.fillMaxWidth(),
            style = QkButtonStyle.Ghost,
            onClick = onBackToLogin,
        )
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

/** 分段控件「未选中」占位：QkSegmented 用 -1 表示全不选。 */
private const val NO_SELECTION = -1

/**
 * 两步表单共用的版式：顶部「完善资料 + 进度」、步骤标签、图标 + 标题 + 用途、
 * 表单卡（[content]）、底部按钮（[footer]）。
 */
@Composable
private fun ProfileStepScaffold(
    counter: String,
    stepLabel: String,
    icon: ImageVector,
    headline: String,
    desc: String,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = PROFILE_TITLE,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = QkColor.Fg,
            )
            Text(
                text = counter,
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )
        }

        Text(
            text = stepLabel,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
            textAlign = TextAlign.Center,
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = QkSpacing.Md)
                .size(QkComponentSize.SkeletonBlock)
                .clip(RoundedCornerShape(QkRadius.Card))
                .background(QkColor.Mint),
            contentAlignment = Alignment.Center,
        ) {
            QkIcon(icon, tint = QkColor.Primary, size = QkSpacing.Xl)
        }
        Text(
            text = headline,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Lg),
            style = MaterialTheme.typography.titleLarge,
            color = QkColor.Fg,
            textAlign = TextAlign.Center,
        )
        Text(
            text = desc,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(QkSpacing.Xl))
        content()
        Spacer(Modifier.height(QkSpacing.Xl))
        footer()
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}
