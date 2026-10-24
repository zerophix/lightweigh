package com.xcwk.scalehub.feature.member

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xcwk.scalehub.ui.components.QkBackButton
import com.xcwk.scalehub.ui.components.QkButton
import com.xcwk.scalehub.ui.components.QkButtonStyle
import com.xcwk.scalehub.ui.components.QkCard
import com.xcwk.scalehub.ui.components.QkGlyph
import com.xcwk.scalehub.ui.components.QkIcons
import com.xcwk.scalehub.ui.components.QkPageTitle
import com.xcwk.scalehub.ui.components.QkSegmented
import com.xcwk.scalehub.ui.components.QkTextField
import com.xcwk.scalehub.ui.states.QkEmptyState
import com.xcwk.scalehub.ui.states.QkLoadErrorState
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * member — 成员管理扩展四态（T-086 · 原型13）。
 *
 * ## 来源
 * - 原型：`design/prototypes/interactive/13-members-plans.html`
 *   v-edit-member / v-savefail / v-member-norecord / v-member-nodevice
 * - 验收：AC-J1-13「成员档案编辑与空态」——四个 view 文案逐字取自原型
 *
 * ## 结构
 * 1. [MemberProfileText]：四屏全部文案的唯一来源（逐字取原型，勿改）；
 * 2. [reduceMemberProfile]：纯函数状态机（表单编辑 → 保存中 → 保存失败 → 重试 → 成功），
 *    不发起任何 IO，保存结果以 [MemberProfileIntent.SaveResult] 注入，故可在纯 JVM 单测里穷举；
 * 3. 三个空/错屏 + 编辑屏：纯渲染，所有动作经回调传回宿主（[MembersScreen]）；
 * 4. 颜色与间距一律 Qk token，页面不写 `NN.dp`、不写 `Color(0x...)`。
 *
 * ## 已知边界（不得当作已实现）
 * - 出生日期只支持 `yyyy-MM-dd` 单行输入，未接日期滚轮（原型此处为单行输入框，一致）；
 * - 体重目标在原型里有值，但本机档案端口（[com.xcwk.scalehub.domain.model.MemberProfile]）
 *   没有该字段，故只做表单校验、不落库，落库属目标模块的后续切片；
 * - 性别三态里「不告知」在本机档案里没有存储位（端口只有 `male: Boolean`），
 *   保存时沿用原值、不改性别，避免把「不告知」失真成「女」。
 */

/** 宿主页（成员列表）内的导航目标。 */
enum class MemberPage {
    /** 成员列表本身。 */
    List,

    /** 资料编辑（含其保存失败态）。 */
    Profile,

    /** 测量记录空态。 */
    NoRecord,

    /** 设备管理空态。 */
    NoDevice,
}

/** 性别三态（原型 v-edit-member 的 `.seg`：男 / 女 / 不告知）。 */
enum class MemberGender(val label: String) {
    Male("男"),
    Female("女"),
    Undisclosed("不告知"),
    ;

    companion object {
        /** 分段控件文案顺序（与 [entries] 顺序一致，索引即选中位）。 */
        val labels: List<String> = entries.map { it.label }

        /** 由 `male: Boolean` 档案值反查（档案无「不告知」位，故只映射男/女）。 */
        fun ofMale(male: Boolean): MemberGender = if (male) Male else Female
    }
}

/** 原型 13 四个 view 的全部文案（逐字取自 HTML，勿改）。 */
object MemberProfileText {
    // ---- v-edit-member（编辑资料）----
    const val EditTitlePrefix = "编辑资料 · "
    const val Nickname = "昵称"
    const val BirthDate = "出生日期"
    const val Gender = "性别"
    const val Height = "身高"
    const val HeightUnit = "cm"
    const val WeightTarget = "体重目标"
    const val WeightTargetUnit = "kg"
    const val ProfileHint = "档案用于计算该成员的 BMI、体脂率参考区间。"
    const val Save = "保存"

    // ---- v-savefail（资料保存失败）----
    const val SaveFailTitle = "资料保存失败"
    const val SaveFailLine1 = "填写内容仍保留在本页。"
    const val SaveFailLine2 = "请检查网络后重试。"
    const val RetrySave = "重试保存"
    const val BackToEdit = "返回修改"
    const val SavedToast = "资料已保存"

    // ---- v-member-norecord（无测量记录）----
    const val NavRecord = "测量记录"
    const val ViewerPrefix = "当前查看："
    const val DataIndependent = "数据独立保存"

    // ---- v-member-nodevice（无绑定设备）----
    const val NavDevice = "设备管理"
    const val DeviceShared = "设备可全家共用"

    /** 二级页标题「编辑资料 · 林悦」。 */
    fun editTitle(name: String): String = EditTitlePrefix + displayName(name)

    /** 「当前查看：妈妈」。 */
    fun viewerLine(name: String): String = ViewerPrefix + displayName(name)

    /** 「当前查看：爸爸 · 设备可全家共用」。 */
    fun deviceViewerLine(name: String): String =
        ViewerPrefix + displayName(name) + " · " + DeviceShared

    /** 「妈妈还没有测量记录」。 */
    fun noRecordTitle(name: String): String = displayName(name) + "还没有测量记录"

    /** 「用同一台体脂秤为妈妈测一次，」。 */
    fun noRecordLine1(name: String): String = "用同一台体脂秤为" + displayName(name) + "测一次，"

    /** 「数据会自动归到她名下。」（代词随成员性别，原型以「妈妈」示范，默认她）。 */
    fun noRecordLine2(pronoun: String = "她"): String = "数据会自动归到" + pronoun + "名下。"

    /** 「还没有为爸爸测量过」。 */
    fun noDeviceTitle(name: String): String = "还没有为" + displayName(name) + "测量过"

    /** 「同一台体脂秤可为全家测量，」。 */
    const val NoDeviceLine1 = "同一台体脂秤可为全家测量，"

    /** 「爸爸上秤一次即可。」 */
    fun noDeviceLine2(name: String): String = displayName(name) + "上秤一次即可。"

    /** 「为妈妈测量」/「为爸爸测量」。 */
    fun measureAction(name: String): String = "为" + displayName(name) + "测量"

    /** 空名兜底：列表与档案都允许「未命名」，空态文案不得以空白开头。 */
    fun displayName(name: String): String = name.ifBlank { "未命名" }
}

/**
 * 编辑表单草稿。
 *
 * 全部字段用字符串承载（原型是输入框），数字校验在 [validateMemberProfileDraft] 里做，
 * 使「输入中」与「非法值」都能表达，不必让 UI 层兜异常。
 */
data class MemberProfileDraft(
    val nickname: String = "",
    val birthDate: String = "",
    val gender: MemberGender = MemberGender.Undisclosed,
    val heightCm: String = "",
    val weightTargetKg: String = "",
)

/** 字段级错误（null 表示该字段合法）。 */
data class MemberProfileErrors(
    val nickname: String? = null,
    val birthDate: String? = null,
    val heightCm: String? = null,
    val weightTargetKg: String? = null,
) {
    val isValid: Boolean
        get() = nickname == null && birthDate == null && heightCm == null && weightTargetKg == null

    companion object {
        val None = MemberProfileErrors()
    }
}

/** 昵称上限（与 MemberProfile 构造守卫一致）。 */
const val MEMBER_NICKNAME_MAX = 20

/** 身高范围（与 MemberProfile 构造守卫一致：100..230）。 */
const val MEMBER_HEIGHT_MIN = 100
const val MEMBER_HEIGHT_MAX = 230

/** 体重目标范围（原型示例 58kg；仅表单校验，不落库）。 */
const val MEMBER_WEIGHT_TARGET_MIN = 20
const val MEMBER_WEIGHT_TARGET_MAX = 300

/**
 * 表单校验（纯函数）。
 *
 * - 昵称去空白后非空、≤20 字；
 * - 出生日期必须能按 `yyyy-MM-dd` 解析成真实日期，且在 1930 年至今之间
 *   （与 [com.xcwk.scalehub.domain.model.MemberProfile] 的出生年守卫同口径）；
 * - 身高必填且落在 100..230；
 * - 体重目标可留空；填了就必须落在 20..300。
 */
fun validateMemberProfileDraft(
    draft: MemberProfileDraft,
    today: LocalDate = LocalDate.now(),
): MemberProfileErrors {
    val nickname = draft.nickname.trim()
    val nicknameError = when {
        nickname.isEmpty() -> "昵称不能为空"
        nickname.length > MEMBER_NICKNAME_MAX -> "昵称最多 $MEMBER_NICKNAME_MAX 个字"
        else -> null
    }

    val birth = parseBirthDate(draft.birthDate)
    val birthError = when {
        draft.birthDate.isBlank() -> "请选择出生日期"
        birth == null -> "请按 1996-05-12 的格式填写"
        birth.first < 1930 || birth.first > today.year -> "出生日期超出范围"
        else -> null
    }

    val height = draft.heightCm.trim().toIntOrNull()
    val heightError = when {
        draft.heightCm.isBlank() -> "请填写身高"
        height == null -> "身高请填数字"
        height < MEMBER_HEIGHT_MIN || height > MEMBER_HEIGHT_MAX ->
            "身高需在 $MEMBER_HEIGHT_MIN-$MEMBER_HEIGHT_MAX cm"
        else -> null
    }

    val weightRaw = draft.weightTargetKg.trim()
    val weight = weightRaw.toIntOrNull()
    val weightError = when {
        weightRaw.isEmpty() -> null
        weight == null -> "体重目标请填数字"
        weight < MEMBER_WEIGHT_TARGET_MIN || weight > MEMBER_WEIGHT_TARGET_MAX ->
            "体重目标需在 $MEMBER_WEIGHT_TARGET_MIN-$MEMBER_WEIGHT_TARGET_MAX kg"
        else -> null
    }

    return MemberProfileErrors(nicknameError, birthError, heightError, weightError)
}

/** 出生日期解析：`yyyy-MM-dd` → (年, 月, 日)；非法返回 null。 */
fun parseBirthDate(raw: String): Triple<Int, Int, Int>? {
    val text = raw.trim()
    if (text.length != 10 || text[4] != '-' || text[7] != '-') return null
    return try {
        val date = LocalDate.parse(text)
        Triple(date.year, date.monthValue, date.dayOfMonth)
    } catch (e: DateTimeParseException) {
        null
    }
}

/** 档案字段 → 表单文本（缺失的月日为 0，回退空串表示「未填写」）。 */
fun formatBirthDate(year: Int, month: Int, day: Int): String =
    if (year <= 0 || month !in 1..12 || day !in 1..31) {
        ""
    } else {
        "%04d-%02d-%02d".format(year, month, day)
    }

/**
 * 资料编辑的三态：编辑中 / 保存失败 / 已保存。
 *
 * 「保存中」不是独立页面而是一个进行标志（[MemberProfileState.saving]）：
 * 首次保存时留在编辑页、重试时留在失败页，两处都只把按钮置灰，避免页面闪回。
 */
enum class MemberSavePhase { Editing, Failed, Saved }

/**
 * 编辑页整体状态。
 *
 * [draft] 在失败态与返回修改后原样保留——对应原型文案「填写内容仍保留在本页。」。
 */
data class MemberProfileState(
    val draft: MemberProfileDraft = MemberProfileDraft(),
    val errors: MemberProfileErrors = MemberProfileErrors.None,
    val phase: MemberSavePhase = MemberSavePhase.Editing,
    /** 保存请求在途（首次保存或失败重试），按钮置灰防重复提交。 */
    val inFlight: Boolean = false,
    /** 失败次数（重试计数，用于单测与埋点；不影响 UI 文案）。 */
    val failedAttempts: Int = 0,
    val message: String? = null,
) {
    /** 正在保存：按钮置灰，防重复提交。 */
    val saving: Boolean get() = inFlight

    /** 失败页：展示 v-savefail（重试在途时仍停在失败页）。 */
    val showSaveFail: Boolean get() = phase == MemberSavePhase.Failed

    /** 已保存：宿主据此返回成员列表。 */
    val saved: Boolean get() = phase == MemberSavePhase.Saved
}

/** 编辑页意图（全部可由 UI 直接发出，无副作用）。 */
sealed interface MemberProfileIntent {
    data class NicknameChanged(val value: String) : MemberProfileIntent
    data class BirthDateChanged(val value: String) : MemberProfileIntent
    data class GenderChanged(val gender: MemberGender) : MemberProfileIntent
    data class HeightChanged(val value: String) : MemberProfileIntent
    data class WeightTargetChanged(val value: String) : MemberProfileIntent

    /** 点「保存」：校验通过则进入保存中，校验失败停在编辑页并回填字段错误。 */
    data object Submit : MemberProfileIntent

    /** 失败页点「重试保存」：用同一份草稿重发，不重新校验（草稿已校验过）。 */
    data object Retry : MemberProfileIntent

    /** 失败页点「返回修改」：回到编辑页，草稿原样保留。 */
    data object BackToEdit : MemberProfileIntent

    /** 保存结果回填（由宿主/ViewModel 在 IO 结束后发出）。 */
    data class SaveResult(val ok: Boolean) : MemberProfileIntent
}

/**
 * 纯函数状态机：编辑 → 保存中 → 失败 → 重试 → 成功。
 *
 * 只做状态推进，不发 IO：进入 [MemberSavePhase.Saving] 后由调用方执行保存，
 * 再把结果以 [MemberProfileIntent.SaveResult] 注入。这样「失败可重试」这条验收
 * 可以在纯 JVM 单测里完整走一遍，不需要协程与数据库。
 *
 * 输入字段的任何修改都会清掉该字段的错误并退出失败态（等价于「返回修改」）。
 */
fun reduceMemberProfile(
    state: MemberProfileState,
    intent: MemberProfileIntent,
    today: LocalDate = LocalDate.now(),
): MemberProfileState = when (intent) {
    is MemberProfileIntent.NicknameChanged -> state.edit {
        copy(nickname = intent.value.take(MEMBER_NICKNAME_MAX))
    }

    is MemberProfileIntent.BirthDateChanged -> state.edit { copy(birthDate = intent.value.take(10)) }
    is MemberProfileIntent.GenderChanged -> state.edit { copy(gender = intent.gender) }
    is MemberProfileIntent.HeightChanged -> state.edit { copy(heightCm = intent.value.take(3)) }
    is MemberProfileIntent.WeightTargetChanged -> state.edit { copy(weightTargetKg = intent.value.take(3)) }

    MemberProfileIntent.Submit -> {
        if (state.inFlight) {
            state
        } else {
            val errors = validateMemberProfileDraft(state.draft, today)
            if (errors.isValid) {
                state.copy(errors = MemberProfileErrors.None, inFlight = true, message = null)
            } else {
                state.copy(errors = errors, phase = MemberSavePhase.Editing, message = null)
            }
        }
    }

    MemberProfileIntent.Retry -> {
        if (state.showSaveFail && !state.inFlight) {
            state.copy(inFlight = true, message = null)
        } else {
            state
        }
    }

    MemberProfileIntent.BackToEdit ->
        if (state.showSaveFail && !state.inFlight) {
            state.copy(phase = MemberSavePhase.Editing, message = null)
        } else {
            state
        }

    is MemberProfileIntent.SaveResult ->
        if (intent.ok) {
            state.copy(phase = MemberSavePhase.Saved, inFlight = false, message = MemberProfileText.SavedToast)
        } else {
            state.copy(
                phase = MemberSavePhase.Failed,
                inFlight = false,
                failedAttempts = state.failedAttempts + 1,
                message = null,
            )
        }
}

/** 改字段的统一收口：改完即回到编辑态（失败页上改字段等价于返回修改）。 */
private inline fun MemberProfileState.edit(block: MemberProfileDraft.() -> MemberProfileDraft): MemberProfileState =
    copy(
        draft = draft.block(),
        errors = MemberProfileErrors.None,
        phase = if (phase == MemberSavePhase.Failed) MemberSavePhase.Editing else phase,
        inFlight = false,
        message = null,
    )

// ---------------------------------------------------------------------------
// 屏（纯渲染；动作一律经回调交给宿主）
// ---------------------------------------------------------------------------

/** 资料编辑路由：编辑页与保存失败页共用同一份状态。 */
@Composable
fun MemberProfileRoute(
    memberName: String,
    onBack: () -> Unit,
    vm: MemberProfileViewModel = viewModel(factory = MemberProfileViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // 保存成功即回成员列表（原型「保存 → v-members + 资料已保存」）
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    if (state.showSaveFail) {
        MemberSaveFailScreen(
            memberName = memberName,
            state = state,
            onIntent = vm::onIntent,
            onBackToEdit = { vm.onIntent(MemberProfileIntent.BackToEdit) },
        )
    } else {
        MemberEditScreen(
            memberName = memberName,
            state = state,
            onIntent = vm::onIntent,
            onBack = onBack,
        )
    }
}

/** v-edit-member：昵称行 + 出生日期 / 性别 / 身高 / 体重目标 + 保存。 */
@Composable
fun MemberEditScreen(
    memberName: String,
    state: MemberProfileState,
    onIntent: (MemberProfileIntent) -> Unit,
    onBack: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        ProfileNavBar(title = MemberProfileText.editTitle(memberName), onBack = onBack)

        // 昵称：原型是「行 + 当前值」，点开才编辑；此处直接用输入框承载同一事实
        QkCard(modifier = Modifier.padding(bottom = QkSpacing.CardGap)) {
            QkTextField(
                value = state.draft.nickname,
                onValueChange = { onIntent(MemberProfileIntent.NicknameChanged(it)) },
                label = MemberProfileText.Nickname,
                modifier = Modifier.fillMaxWidth(),
            )
            FieldError(state.errors.nickname)
        }

        QkCard(modifier = Modifier.padding(bottom = QkSpacing.CardGap)) {
            QkTextField(
                value = state.draft.birthDate,
                onValueChange = { onIntent(MemberProfileIntent.BirthDateChanged(it)) },
                label = MemberProfileText.BirthDate,
                placeholder = "1996-05-12",
                modifier = Modifier.fillMaxWidth(),
            )
            FieldError(state.errors.birthDate)

            Spacer(Modifier.height(QkSpacing.Lg))
            Text(
                text = MemberProfileText.Gender,
                style = MaterialTheme.typography.labelMedium,
                color = QkColor.FieldLabel,
            )
            Spacer(Modifier.height(QkSpacing.Xs))
            QkSegmented(
                items = MemberGender.labels,
                selectedIndex = MemberGender.entries.indexOf(state.draft.gender),
                onSelect = { index -> onIntent(MemberProfileIntent.GenderChanged(MemberGender.entries[index])) },
            )

            Spacer(Modifier.height(QkSpacing.Lg))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                UnitField(
                    label = MemberProfileText.Height,
                    value = state.draft.heightCm,
                    unit = MemberProfileText.HeightUnit,
                    error = state.errors.heightCm,
                    onValueChange = { onIntent(MemberProfileIntent.HeightChanged(it)) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(QkSpacing.Md))
                UnitField(
                    label = MemberProfileText.WeightTarget,
                    value = state.draft.weightTargetKg,
                    unit = MemberProfileText.WeightTargetUnit,
                    error = state.errors.weightTargetKg,
                    onValueChange = { onIntent(MemberProfileIntent.WeightTargetChanged(it)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Text(
            text = MemberProfileText.ProfileHint,
            style = MaterialTheme.typography.bodySmall,
            color = QkColor.Muted,
            modifier = Modifier.padding(horizontal = QkSpacing.Xs, vertical = QkSpacing.Sm),
        )

        QkButton(
            text = MemberProfileText.Save,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.saving,
            onClick = { onIntent(MemberProfileIntent.Submit) },
        )
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

/**
 * v-savefail：保存失败页。
 *
 * 「填写内容仍保留在本页」由 [MemberProfileState.draft] 承载——失败时状态机不改草稿，
 * 点「返回修改」原样回到编辑页。
 */
@Composable
fun MemberSaveFailScreen(
    memberName: String,
    state: MemberProfileState,
    onIntent: (MemberProfileIntent) -> Unit,
    onBackToEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        ProfileNavBar(title = MemberProfileText.editTitle(memberName), onBack = onBackToEdit)

        QkLoadErrorState(
            title = MemberProfileText.SaveFailTitle,
            description = MemberProfileText.SaveFailLine1 + "\n" + MemberProfileText.SaveFailLine2,
        )

        QkButton(
            text = MemberProfileText.RetrySave,
            modifier = Modifier.fillMaxWidth().padding(bottom = QkSpacing.Md),
            enabled = !state.saving,
            onClick = { onIntent(MemberProfileIntent.Retry) },
        )
        QkButton(
            text = MemberProfileText.BackToEdit,
            modifier = Modifier.fillMaxWidth(),
            style = QkButtonStyle.Secondary,
            onClick = onBackToEdit,
        )
        Spacer(Modifier.height(QkSpacing.Xl))
    }
}

/**
 * v-member-norecord：该成员还没有测量记录。
 *
 * 「为妈妈测量」在原型里回到成员列表并提示（`data-goto="v-members"`），
 * 故 [onMeasure] 的默认落点就是返回列表，不伪造测量动作。
 */
@Composable
fun MemberNoRecordScreen(
    memberName: String,
    onBack: () -> Unit,
    onMeasure: () -> Unit = onBack,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        ProfileNavBar(title = MemberProfileText.NavRecord, onBack = onBack)
        ViewerBanner(
            name = memberName,
            line = MemberProfileText.viewerLine(memberName),
            subtitle = MemberProfileText.DataIndependent,
        )
        QkEmptyState(
            title = MemberProfileText.noRecordTitle(memberName),
            description = MemberProfileText.noRecordLine1(memberName) + "\n" +
                MemberProfileText.noRecordLine2(),
            icon = QkIcons.Trend,
            actionText = MemberProfileText.measureAction(memberName),
            onAction = onMeasure,
        )
    }
}

/** v-member-nodevice：该成员还没有上过秤（设备可全家共用）。 */
@Composable
fun MemberNoDeviceScreen(
    memberName: String,
    onBack: () -> Unit,
    onMeasure: () -> Unit = onBack,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QkSpacing.Page),
    ) {
        ProfileNavBar(title = MemberProfileText.NavDevice, onBack = onBack)
        ViewerBanner(
            name = memberName,
            line = MemberProfileText.deviceViewerLine(memberName),
            subtitle = null,
        )
        QkEmptyState(
            title = MemberProfileText.noDeviceTitle(memberName),
            description = MemberProfileText.NoDeviceLine1 + "\n" +
                MemberProfileText.noDeviceLine2(memberName),
            icon = QkIcons.Device,
            actionText = MemberProfileText.measureAction(memberName),
            onAction = onMeasure,
        )
    }
}

/** 二级页导航条（返回 + 标题），四屏共用。 */
@Composable
private fun ProfileNavBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = QkSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QkBackButton(onClick = onBack)
        QkPageTitle(title = title, modifier = Modifier.weight(1f).padding(start = QkSpacing.Md))
    }
}

/** 「当前查看」横幅：头像字块 + 成员名（+ 可选副标题）。 */
@Composable
private fun ViewerBanner(name: String, line: String, subtitle: String?) {
    QkCard(modifier = Modifier.padding(bottom = QkSpacing.CardGap), padding = QkSpacing.Md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QkGlyph(text = MemberProfileText.displayName(name).take(1))
            Column(modifier = Modifier.weight(1f).padding(start = QkSpacing.Md)) {
                Text(text = line, style = MaterialTheme.typography.labelLarge, color = QkColor.Fg)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = QkColor.Muted,
                        modifier = Modifier.padding(top = QkSpacing.Xs),
                    )
                }
            }
        }
    }
}

/** 带单位的数字输入（原型 .field + .unit）。 */
@Composable
private fun UnitField(
    label: String,
    value: String,
    unit: String,
    error: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        QkTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = value,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = QkSpacing.Xs)) {
            Text(text = unit, style = MaterialTheme.typography.labelSmall, color = QkColor.Muted)
            if (error != null) {
                Spacer(Modifier.width(QkSpacing.Sm))
                Text(text = error, style = MaterialTheme.typography.labelSmall, color = QkColor.Danger)
            }
        }
    }
}

/** 字段错误行（无错误时不占位）。 */
@Composable
private fun FieldError(error: String?) {
    if (error == null) return
    Text(
        text = error,
        style = MaterialTheme.typography.labelSmall,
        color = QkColor.Danger,
        modifier = Modifier.padding(top = QkSpacing.Xs),
    )
}
