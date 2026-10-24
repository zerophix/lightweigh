package com.xcwk.scalehub.feature.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkGlyphTone
import com.xcwk.scalehub.ui.components.QkIcon
import com.xcwk.scalehub.ui.components.QkIconBubble
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkListRow
import com.xcwk.scalehub.ui.components.QkSegmented
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkRadius
import com.xcwk.scalehub.ui.theme.QkSpacing
import com.xcwk.scalehub.ui.theme.QkStroke

/**
 * guide — 首次引导六屏（T-076 · Wave 2）。
 *
 * ## 原型
 * `design/prototypes/interactive/19-onboarding-guide.html`：
 * - `v-intro`     第 1 步 · 认识你（昵称 / 性别 / 出生日期 / 身高，1/3）
 * - `v-goal`      第 2 步 · 设定健康方向（三个目标选项 + 目标体重，2/3）
 * - `v-prepare`   第 3 步 · 准备第一次测量（三条准备动作 + 饮水提示，3/3）
 * - `v-connected` 搜索设备（跳过 / 模拟连接成功）
 * - `v-done`      一切就绪（开始第一次测量 / 重新走一遍引导）
 * - `v-skip`      跳过连接（完成引导）
 *
 * ## 验收（AC-J1-03 新手引导三步完成）
 * - 六屏文案逐字取自原型，常量全部收在 [GuideText]，JVM 单测钉死
 * - 状态推进是纯函数 [guideReduce]，UI 只渲染 [GuideUiState]；意图由 [GuideIntent] 表达
 * - 性别选项复用 [GenderOption]（男 / 女 / 不告知），与资料完善同源
 * - 颜色、间距、圆角一律 Qk token；按钮、卡片、列表行、分段控件复用 ui/components
 *
 * ## 边界（呈现式组件）
 * 不建 ViewModel、不碰蓝牙、不落库；连接与落库由宿主在回调里完成，
 * 「模拟连接成功」只是把状态推到 [GuideStep.Done]，不代表真实设备已连上。
 */

// ---- 静态文案（逐字取自原型，JVM 单测校验；勿改写） ----

/** 六屏文案。分组顺序与原型 view 顺序一致。 */
object GuideText {
    // v-intro
    const val IntroTitle = "完善资料"
    const val IntroCounter = "1/3"
    const val IntroStepLabel = "第 1 步 · 认识你"
    const val IntroHeadline = "认识一下你"
    const val IntroDesc = "这些信息用于计算你的身体指标参考区间。"
    const val IntroNicknameLabel = "昵称"
    const val IntroNicknamePlaceholder = "怎么称呼你？"
    const val IntroGenderLabel = "性别"
    const val IntroGenderHint = "性别影响体脂率参考区间"
    const val IntroBirthDateLabel = "出生日期"
    const val IntroBirthDatePlaceholder = "1996-05-12"
    const val IntroHeightLabel = "身高"
    const val IntroHeightUnit = "cm"

    // v-goal
    const val GoalTitle = "健康目标"
    const val GoalCounter = "2/3"
    const val GoalStepLabel = "第 2 步 · 设定健康方向"
    const val GoalHeadline = "你想达成什么？"
    const val GoalDesc = "目标可以随时调整，\n先选一个方向就好。"
    const val GoalWeightLabel = "目标体重"
    const val GoalWeightUnit = "kg"
    const val GoalWeightHint = "从 62.4 kg 到 58.0 kg，需减 4.4 kg"
    const val GoalSkip = "跳过，稍后设置"

    // v-prepare
    const val PrepareTitle = "准备测量"
    const val PrepareCounter = "3/3"
    const val PrepareStepLabel = "第 3 步 · 准备第一次测量"
    const val PrepareHeadline = "准备好第一次测量"
    const val PrepareDesc = "连接体脂秤，完成第一次测量，\n就能看到你的身体概览。"
    const val PrepareRow1Title = "轻踩体脂秤唤醒"
    const val PrepareRow1Detail = "屏幕点亮即可连接"
    const val PrepareRow2Title = "打开蓝牙，靠近秤"
    const val PrepareRow2Detail = "手机距秤 1 米以内"
    const val PrepareRow3Title = "赤脚站上秤面"
    const val PrepareRow3Detail = "双脚踩住电极，保持静止"
    const val PrepareBanner = "测量前请正常饮水，避免剧烈运动后立即测量。"
    const val PrepareConnect = "连接体脂秤，开始测量"
    const val PrepareSkip = "跳过，稍后连接"

    // 三步共用
    const val Next = "下一步"

    // v-connected
    const val ConnectedTitle = "连接设备"
    const val ConnectedHeadline = "正在搜索设备…"
    const val ConnectedDesc = "请轻踩体脂秤唤醒，保持手机靠近"
    const val ConnectedSkip = "跳过"
    const val ConnectedSimulate = "模拟连接成功"

    // v-done
    const val DoneHeadline = "一切就绪"
    const val DoneDesc = "资料已完善，体脂秤已连接。\n现在就可以开始第一次测量了。"
    const val DoneStart = "开始第一次测量"
    const val DoneRestart = "重新走一遍引导"

    // v-skip
    const val SkipHeadline = "跳过连接"
    const val SkipDesc = "你可以随时在「设备管理」中连接体脂秤。\n引导已完成。"
    const val SkipFinish = "完成引导"

    /**
     * 原型页脚（`19-onboarding-guide.html` 流外标注，不属于 App 界面）。
     * 保留常量只为原型追溯校验逐字可查，不在任何屏幕上渲染。
     */
    const val PrototypeFooter =
        "轻衡 · 首次引导交互原型 · 5 屏（补 Figma A11–A13 引导三部曲缺口）"
}

// ---- 目标选项（顺序与文案取自原型 v-goal） ----

/** 健康目标方向：标题 + 一句解释，选中态由 [GuideUiState.goal] 表达。 */
enum class GuideGoalOption(val title: String, val detail: String) {
    LoseWeight("减轻体重", "设定目标体重，跟踪减重进度"),
    MaintainWeight("保持体重", "维持当前体重，关注身体成分变化"),
    BuildMuscle("增加肌肉", "提升肌肉量，改善身体成分比例"),
}

// ---- 状态机（纯函数，JVM 单测钉死） ----

/** 引导六屏状态，与原型 view 一一对应。 */
enum class GuideStep { Intro, Goal, Prepare, Connected, Done, Skipped }

/**
 * 引导呈现态。
 *
 * 输入字段保留原始文本（不提前转换），校验与落库都不在本层：
 * 引导允许一路「下一步 / 跳过」走完，硬校验交给资料完善与设备页。
 */
data class GuideUiState(
    val step: GuideStep = GuideStep.Intro,
    val nickname: String = "",
    val gender: GenderOption? = null,
    val birthDate: String = "",
    val heightText: String = "",
    val goal: GuideGoalOption = GuideGoalOption.LoseWeight,
    val targetWeightText: String = "",
)

/** 引导意图：输入变更 + 步进动作。 */
sealed interface GuideIntent {
    data class NicknameChanged(val text: String) : GuideIntent
    data class GenderSelected(val gender: GenderOption) : GuideIntent
    data class BirthDateChanged(val text: String) : GuideIntent
    data class HeightChanged(val text: String) : GuideIntent
    data class GoalSelected(val goal: GuideGoalOption) : GuideIntent
    data class TargetWeightChanged(val text: String) : GuideIntent

    /** 主按钮「下一步」：Intro → Goal → Prepare → Connected → Done。 */
    data object Next : GuideIntent

    /** 返回：Goal → Intro、Prepare → Goal、Connected → Prepare，其余原地不动。 */
    data object Back : GuideIntent

    /** v-goal「跳过，稍后设置」与主按钮同去 v-prepare。 */
    data object SkipGoal : GuideIntent

    /** v-prepare「连接体脂秤，开始测量」。 */
    data object StartConnecting : GuideIntent

    /** v-connected「模拟连接成功」。 */
    data object SimulatedConnected : GuideIntent

    /** v-prepare「跳过，稍后连接」/ v-connected「跳过」：直接进跳过页。 */
    data object SkipConnection : GuideIntent

    /** v-done / v-skip 的收尾按钮；终态不变，由宿主回调决定后续。 */
    data object Finish : GuideIntent

    /** v-done「重新走一遍引导」：整段状态归零回 v-intro。 */
    data object Restart : GuideIntent
}

/**
 * 纯状态转移。
 *
 * 口径：
 * - 「下一步」只沿 Intro → Goal → Prepare → Connected → Done 前进，
 *   v-prepare 必须走 [GuideIntent.StartConnecting] 或 [GuideIntent.SkipConnection]；
 * - 跳过连接是终态，不再接受前进，但仍可 [GuideIntent.Restart]；
 * - [GuideIntent.Finish] 不改变状态——落库/出栈是宿主的事。
 */
fun guideReduce(state: GuideUiState, intent: GuideIntent): GuideUiState = when (intent) {
    is GuideIntent.NicknameChanged -> state.copy(nickname = intent.text)
    is GuideIntent.GenderSelected -> state.copy(gender = intent.gender)
    is GuideIntent.BirthDateChanged -> state.copy(birthDate = intent.text)
    is GuideIntent.HeightChanged -> state.copy(heightText = intent.text)
    is GuideIntent.GoalSelected -> state.copy(goal = intent.goal)
    is GuideIntent.TargetWeightChanged -> state.copy(targetWeightText = intent.text)

    GuideIntent.Next -> when (state.step) {
        GuideStep.Intro -> state.copy(step = GuideStep.Goal)
        GuideStep.Goal -> state.copy(step = GuideStep.Prepare)
        GuideStep.Prepare -> state.copy(step = GuideStep.Connected)
        GuideStep.Connected -> state.copy(step = GuideStep.Done)
        GuideStep.Done, GuideStep.Skipped -> state
    }

    GuideIntent.Back -> when (state.step) {
        GuideStep.Goal -> state.copy(step = GuideStep.Intro)
        GuideStep.Prepare -> state.copy(step = GuideStep.Goal)
        GuideStep.Connected -> state.copy(step = GuideStep.Prepare)
        GuideStep.Intro, GuideStep.Done, GuideStep.Skipped -> state
    }

    GuideIntent.SkipGoal ->
        if (state.step == GuideStep.Goal) state.copy(step = GuideStep.Prepare) else state

    GuideIntent.StartConnecting ->
        if (state.step == GuideStep.Prepare) state.copy(step = GuideStep.Connected) else state

    GuideIntent.SimulatedConnected ->
        if (state.step == GuideStep.Connected) state.copy(step = GuideStep.Done) else state

    GuideIntent.SkipConnection ->
        if (state.step == GuideStep.Prepare || state.step == GuideStep.Connected) {
            state.copy(step = GuideStep.Skipped)
        } else {
            state
        }

    GuideIntent.Finish -> state

    GuideIntent.Restart -> GuideUiState()
}

// ---- 呈现层 ----

/**
 * 引导入口（保持既有装配签名：宿主只挂一个入口）。
 *
 * - [onDone] 走完 v-done「开始第一次测量」
 * - [onSkip] 走完 v-skip「完成引导」（连接被跳过，引导本身完成）
 */
@Composable
fun GuideScreen(
    onDone: () -> Unit = {},
    onSkip: () -> Unit = {},
) {
    var state by remember { mutableStateOf(GuideUiState()) }
    GuideFlow(
        state = state,
        onIntent = { intent ->
            state = guideReduce(state, intent)
            if (intent == GuideIntent.Finish) {
                if (state.step == GuideStep.Skipped) onSkip() else onDone()
            }
        },
    )
}

/** 按状态渲染六屏之一；宿主可用它接管状态（测试或更高层装配）。 */
@Composable
fun GuideFlow(
    state: GuideUiState,
    onIntent: (GuideIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.step) {
        GuideStep.Intro -> GuideIntroScreen(state, onIntent, modifier)
        GuideStep.Goal -> GuideGoalScreen(state, onIntent, modifier)
        GuideStep.Prepare -> GuidePrepareScreen(state, onIntent, modifier)
        GuideStep.Connected -> GuideConnectedScreen(onIntent, modifier)
        GuideStep.Done -> GuideDoneScreen(onIntent, modifier)
        GuideStep.Skipped -> GuideSkippedScreen(onIntent, modifier)
    }
}

/** v-intro 第 1/3 步：认识你。 */
@Composable
fun GuideIntroScreen(
    state: GuideUiState,
    onIntent: (GuideIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    GuidePageScaffold(
        title = GuideText.IntroTitle,
        counter = GuideText.IntroCounter,
        stepLabel = GuideText.IntroStepLabel,
        activeSteps = 1,
        icon = QkIcons.Account,
        headline = GuideText.IntroHeadline,
        desc = GuideText.IntroDesc,
        modifier = modifier,
        footer = {
            QkButton(
                text = GuideText.Next,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onIntent(GuideIntent.Next) },
            )
        },
    ) {
        QkCard {
            QkTextField(
                value = state.nickname,
                onValueChange = { onIntent(GuideIntent.NicknameChanged(it)) },
                label = GuideText.IntroNicknameLabel,
                placeholder = GuideText.IntroNicknamePlaceholder,
            )

            Spacer(Modifier.height(QkSpacing.Lg))
            Text(
                text = GuideText.IntroGenderLabel,
                style = MaterialTheme.typography.labelMedium,
                color = QkColor.FieldLabel,
            )
            Spacer(Modifier.height(QkSpacing.Sm))
            // 性别选项顺序：男 / 女 / 不告知（「不告知」是合法取值，不阻塞下一步）
            QkSegmented(
                items = GenderOption.entries.map { it.label },
                selectedIndex = state.gender?.ordinal ?: GUIDE_NO_SELECTION,
                onSelect = { index -> onIntent(GuideIntent.GenderSelected(GenderOption.entries[index])) },
            )
            Text(
                text = GuideText.IntroGenderHint,
                modifier = Modifier.padding(top = QkSpacing.Xs, start = QkSpacing.Xs),
                style = MaterialTheme.typography.bodySmall,
                color = QkColor.Muted,
            )

            Spacer(Modifier.height(QkSpacing.Lg))
            QkTextField(
                value = state.birthDate,
                onValueChange = { onIntent(GuideIntent.BirthDateChanged(it)) },
                label = GuideText.IntroBirthDateLabel,
                placeholder = GuideText.IntroBirthDatePlaceholder,
            )

            Spacer(Modifier.height(QkSpacing.Lg))
            Row(verticalAlignment = Alignment.Bottom) {
                QkTextField(
                    value = state.heightText,
                    onValueChange = { onIntent(GuideIntent.HeightChanged(it)) },
                    modifier = Modifier.weight(1f),
                    label = GuideText.IntroHeightLabel,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    text = GuideText.IntroHeightUnit,
                    modifier = Modifier.padding(start = QkSpacing.Sm, bottom = QkSpacing.Md),
                    style = MaterialTheme.typography.bodyMedium,
                    color = QkColor.Sub,
                )
            }
        }
    }
}

/** v-goal 第 2/3 步：设定健康方向。 */
@Composable
fun GuideGoalScreen(
    state: GuideUiState,
    onIntent: (GuideIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    GuidePageScaffold(
        title = GuideText.GoalTitle,
        counter = GuideText.GoalCounter,
        stepLabel = GuideText.GoalStepLabel,
        activeSteps = 2,
        icon = QkIcons.Goal,
        headline = GuideText.GoalHeadline,
        desc = GuideText.GoalDesc,
        modifier = modifier,
        onBack = { onIntent(GuideIntent.Back) },
        footer = {
            QkButton(
                text = GuideText.Next,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onIntent(GuideIntent.Next) },
            )
            Spacer(Modifier.height(QkSpacing.Md))
            QkButton(
                text = GuideText.GoalSkip,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Ghost,
                onClick = { onIntent(GuideIntent.SkipGoal) },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(QkSpacing.Md)) {
            GuideGoalOption.entries.forEach { option ->
                GoalOptionCard(
                    option = option,
                    selected = option == state.goal,
                    onClick = { onIntent(GuideIntent.GoalSelected(option)) },
                )
            }
        }

        Spacer(Modifier.height(QkSpacing.Md))
        Row(verticalAlignment = Alignment.Bottom) {
            QkTextField(
                value = state.targetWeightText,
                onValueChange = { onIntent(GuideIntent.TargetWeightChanged(it)) },
                modifier = Modifier.weight(1f),
                label = GuideText.GoalWeightLabel,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Text(
                text = GuideText.GoalWeightUnit,
                modifier = Modifier.padding(start = QkSpacing.Sm, bottom = QkSpacing.Md),
                style = MaterialTheme.typography.bodyMedium,
                color = QkColor.Sub,
            )
        }
        Text(
            text = GuideText.GoalWeightHint,
            modifier = Modifier.padding(top = QkSpacing.Xs, start = QkSpacing.Xs),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
        )
    }
}

/** v-prepare 第 3/3 步：准备第一次测量。 */
@Composable
fun GuidePrepareScreen(
    state: GuideUiState,
    onIntent: (GuideIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    GuidePageScaffold(
        title = GuideText.PrepareTitle,
        counter = GuideText.PrepareCounter,
        stepLabel = GuideText.PrepareStepLabel,
        activeSteps = 3,
        icon = QkIcons.Device,
        headline = GuideText.PrepareHeadline,
        desc = GuideText.PrepareDesc,
        modifier = modifier,
        onBack = { onIntent(GuideIntent.Back) },
        footer = {
            QkButton(
                text = GuideText.PrepareConnect,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onIntent(GuideIntent.StartConnecting) },
            )
            Spacer(Modifier.height(QkSpacing.Md))
            QkButton(
                text = GuideText.PrepareSkip,
                modifier = Modifier.fillMaxWidth(),
                style = QkButtonStyle.Ghost,
                onClick = { onIntent(GuideIntent.SkipConnection) },
            )
        },
    ) {
        QkCard {
            QkListRow(
                title = GuideText.PrepareRow1Title,
                subtitle = GuideText.PrepareRow1Detail,
                glyph = "1",
            )
            QkListRow(
                title = GuideText.PrepareRow2Title,
                subtitle = GuideText.PrepareRow2Detail,
                glyph = "2",
            )
            QkListRow(
                title = GuideText.PrepareRow3Title,
                subtitle = GuideText.PrepareRow3Detail,
                glyph = "3",
                showDivider = false,
            )
        }

        Spacer(Modifier.height(QkSpacing.Md))
        QkCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QkIconBubble(
                    icon = QkIcons.About,
                    tone = QkGlyphTone.Gray,
                    size = QkSpacing.Xl,
                )
                Text(
                    text = GuideText.PrepareBanner,
                    modifier = Modifier.padding(start = QkSpacing.Sm),
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Sub,
                )
            }
        }
    }
}

/** v-connected：搜索设备（不扫真蓝牙，两个按钮由宿主决定含义）。 */
@Composable
fun GuideConnectedScreen(
    onIntent: (GuideIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = QkSpacing.Page),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QkBackButton(onClick = { onIntent(GuideIntent.Back) })
            Text(
                text = GuideText.ConnectedTitle,
                modifier = Modifier.weight(1f).padding(start = QkSpacing.Md),
                style = MaterialTheme.typography.titleMedium,
                color = QkColor.Fg,
            )
        }

        Spacer(Modifier.weight(1f))
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.CenterHorizontally).size(QkSpacing.Xl),
            color = QkColor.Primary,
            strokeWidth = QkStroke.Selected,
        )
        Text(
            text = GuideText.ConnectedHeadline,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Lg),
            style = MaterialTheme.typography.titleLarge,
            color = QkColor.Fg,
            textAlign = TextAlign.Center,
        )
        Text(
            text = GuideText.ConnectedDesc,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Sm),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(QkSpacing.Sm)) {
            QkButton(
                text = GuideText.ConnectedSkip,
                modifier = Modifier.weight(1f),
                style = QkButtonStyle.Secondary,
                onClick = { onIntent(GuideIntent.SkipConnection) },
            )
            QkButton(
                text = GuideText.ConnectedSimulate,
                modifier = Modifier.weight(1f),
                onClick = { onIntent(GuideIntent.SimulatedConnected) },
            )
        }
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

/** v-done：一切就绪。 */
@Composable
fun GuideDoneScreen(
    onIntent: (GuideIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    GuideStatePage(
        icon = QkIcons.Habit,
        headline = GuideText.DoneHeadline,
        desc = GuideText.DoneDesc,
        modifier = modifier,
    ) {
        QkButton(
            text = GuideText.DoneStart,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onIntent(GuideIntent.Finish) },
        )
        Spacer(Modifier.height(QkSpacing.Md))
        QkButton(
            text = GuideText.DoneRestart,
            modifier = Modifier.fillMaxWidth(),
            style = QkButtonStyle.Ghost,
            onClick = { onIntent(GuideIntent.Restart) },
        )
    }
}

/** v-skip：跳过连接。 */
@Composable
fun GuideSkippedScreen(
    onIntent: (GuideIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    GuideStatePage(
        icon = QkIcons.Device,
        headline = GuideText.SkipHeadline,
        desc = GuideText.SkipDesc,
        modifier = modifier,
    ) {
        QkButton(
            text = GuideText.SkipFinish,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onIntent(GuideIntent.Finish) },
        )
    }
}

/** 分段控件「未选中」占位：QkSegmented 用 -1 表示全不选。 */
private const val GUIDE_NO_SELECTION = -1

/** 目标选项卡：选中态 2px 主绿边 + 薄荷底（原型 v-goal 的第一张卡）。 */
@Composable
private fun GoalOptionCard(
    option: GuideGoalOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(QkRadius.Tile)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) QkColor.Mint else QkColor.Card)
            .border(
                width = if (selected) QkStroke.Selected else QkStroke.Thin,
                color = if (selected) QkColor.Primary else QkColor.Border,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(QkSpacing.CardPad),
    ) {
        Text(
            text = option.title,
            style = MaterialTheme.typography.titleMedium,
            color = QkColor.Fg,
        )
        Text(
            text = option.detail,
            modifier = Modifier.padding(top = QkSpacing.Xs),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
        )
    }
}

/**
 * 引导三步共用版式：返回 + 标题 + 进度、步骤标签、图标 + 标题 + 说明、
 * 内容（[content]）、底部按钮（[footer]）。
 */
@Composable
private fun GuidePageScaffold(
    title: String,
    counter: String?,
    stepLabel: String,
    activeSteps: Int,
    icon: ImageVector,
    headline: String,
    desc: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
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
            if (onBack != null) {
                QkBackButton(onClick = onBack)
            }
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onBack != null) QkSpacing.Md else QkSpacing.Xs),
                style = MaterialTheme.typography.titleMedium,
                color = QkColor.Fg,
            )
            if (counter != null) {
                Text(
                    text = counter,
                    style = MaterialTheme.typography.bodySmall,
                    color = QkColor.Muted,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(QkSpacing.Xs),
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(QkSpacing.Xs)
                        .clip(RoundedCornerShape(QkRadius.SegmentItem))
                        .background(if (index < activeSteps) QkColor.Primary else QkColor.Border),
                )
            }
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
                .size(QkSpacing.Xl * 2f)
                .clip(CircleShape)
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

/** 终态页版式（v-done / v-skip）：居中图标 + 标题 + 说明 + 底部按钮。 */
@Composable
private fun GuideStatePage(
    icon: ImageVector,
    headline: String,
    desc: String,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit,
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
                .size(QkSpacing.Xl * 2f)
                .clip(CircleShape)
                .background(QkColor.Mint),
            contentAlignment = Alignment.Center,
        ) {
            QkIcon(icon, tint = QkColor.Primary, size = QkSpacing.Xl)
        }
        Text(
            text = headline,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Xl),
            style = MaterialTheme.typography.headlineSmall,
            color = QkColor.Fg,
            textAlign = TextAlign.Center,
        )
        Text(
            text = desc,
            modifier = Modifier.fillMaxWidth().padding(top = QkSpacing.Md),
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Sub,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        footer()
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}
