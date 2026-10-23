package com.xcwk.scalehub

import android.content.Context
import android.app.Application
import com.xcwk.scalehub.application.DataChange
import com.xcwk.scalehub.application.DataRefreshBus
import com.xcwk.scalehub.application.ai.AiService
import com.xcwk.scalehub.application.auth.AuthPort
import com.xcwk.scalehub.application.auth.AuthService
import com.xcwk.scalehub.application.ble.BlePort
import com.xcwk.scalehub.application.query.HealthLogQuery
import com.xcwk.scalehub.application.query.HealthLogQueryPort
import com.xcwk.scalehub.application.query.MeasurementQuery
import com.xcwk.scalehub.application.query.MeasurementQueryPort
import com.xcwk.scalehub.application.query.ProfileQuery
import com.xcwk.scalehub.application.query.ReportQuery
import com.xcwk.scalehub.application.query.TrendQuery
import com.xcwk.scalehub.application.service.RecordHealthLogService
import com.xcwk.scalehub.application.service.UpdateProfileService
import com.xcwk.scalehub.application.sync.CloudSessionPort
import com.xcwk.scalehub.application.sync.DeleteAccountService
import com.xcwk.scalehub.application.sync.RestoreAccountService
import com.xcwk.scalehub.application.sync.SyncService
import com.xcwk.scalehub.application.sync.SyncOnLocalWrites
import com.xcwk.scalehub.application.sync.SyncTrigger
import com.xcwk.scalehub.application.service.DeleteMeasurementService
import com.xcwk.scalehub.application.service.RecordMeasurementService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.xcwk.scalehub.domain.repository.DeviceRepository
import com.xcwk.scalehub.domain.repository.HealthLogRepository
import com.xcwk.scalehub.domain.repository.MeasurementRepository
import com.xcwk.scalehub.domain.repository.MemberProfileReader
import com.xcwk.scalehub.domain.repository.MemberProfileWriter
import com.xcwk.scalehub.infrastructure.ai.CloudFunctionAi
import com.xcwk.scalehub.infrastructure.ble.AndroidBleLink
import com.xcwk.scalehub.infrastructure.cloud.CloudBaseGateway
import com.xcwk.scalehub.infrastructure.persistent.AppDatabase
import com.xcwk.scalehub.infrastructure.persistent.repository.RoomHealthLogQuery
import com.xcwk.scalehub.infrastructure.persistent.repository.RoomHealthLogRepository
import com.xcwk.scalehub.infrastructure.persistent.repository.RoomMeasurementQuery
import com.xcwk.scalehub.infrastructure.persistent.repository.RoomMeasurementRepository
import com.xcwk.scalehub.infrastructure.persistent.dao.HabitDao
import com.xcwk.scalehub.infrastructure.persistent.repository.RoomHabitRepository
import com.xcwk.scalehub.infrastructure.habit.WorkManagerHabitReminderScheduler
import com.xcwk.scalehub.application.habit.HabitReminderScheduler
import com.xcwk.scalehub.application.query.HabitQuery
import com.xcwk.scalehub.application.service.RecordHabitService
import com.xcwk.scalehub.infrastructure.profile.ProfileStore
import com.xcwk.scalehub.infrastructure.persistent.repository.RoomDeviceRepository
import com.xcwk.scalehub.infrastructure.session.SessionStore

/**
 * app — 组合根：全工程唯一知道所有实现的地方。
 * 后续阶段的接线点：SyncGateway(云)、ScaleGateway(BLE) 都在这里装配。
 */
class App : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // T-018：BackendConfig.BASE 按 buildType 注入（release/debug 各自的 BuildConfig）
        com.xcwk.scalehub.infrastructure.config.BackendConfig.BASE = BuildConfig.BACKEND_BASE
    }
}

/** 手写轻量 DI 容器（规模化后可平移 Hilt，装配点不变）。 */
class AppContainer(context: Context) {

    /** 数据变更信号（DEC-021 支柱2 实体；写用例发、查询 VM 收）。 */
    val dataRefreshBus = DataRefreshBus()

    /** application 级后台作用域：云推送点火后在此执行（进程内存活，不随 VM 销毁）。 */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 会话与认证（M2b）：同源实现双端口（会话读写 + 认证动作）。
     * 具体类型只在容器内可见，表现层经 [authPort] / [cloudSessionPort] 取用（DEC-035）。
     */
    private val sessionStoreImpl: SessionStore by lazy { SessionStore(context) }

    val authPort: AuthPort get() = sessionStoreImpl

    val cloudSessionPort: CloudSessionPort get() = sessionStoreImpl

    private val cloudGateway: CloudBaseGateway by lazy { CloudBaseGateway(sessionStoreImpl) }

    private val database: AppDatabase by lazy { AppDatabase.get(context) }

    private val pendingSync: com.xcwk.scalehub.infrastructure.persistent.repository.RoomPendingSyncRepository by lazy {
        com.xcwk.scalehub.infrastructure.persistent.repository.RoomPendingSyncRepository(database.pendingSyncDao())
    }

    /** 本机测量用户（Room members，DEC-057 / DEC-S04）。 */
    private val memberRepository: com.xcwk.scalehub.infrastructure.member.MemberStore by lazy {
        com.xcwk.scalehub.infrastructure.member.MemberStore(context, database)
    }

    val measurementRepository: MeasurementRepository by lazy {
        RoomMeasurementRepository(database.measurementDao(), memberRepository::activeId)
    }
    val healthLogRepository: HealthLogRepository by lazy {
        RoomHealthLogRepository(database.healthLogDao(), memberRepository::activeId)
    }
    /** 档案存取：同一个实现同时充当读口与写口，容器对外按用途分别暴露（DEC-035）。 */
    private val profileStoreImpl: ProfileStore by lazy {
        ProfileStore(context, memberRepository::activeId, pendingSync)
    }

    val profileReader: MemberProfileReader get() = profileStoreImpl

    val profileWriter: MemberProfileWriter get() = profileStoreImpl

    /** 目标存储（移植补齐：首页目标进度卡）。 */
    private val goalStore: com.xcwk.scalehub.infrastructure.profile.GoalStore by lazy {
        com.xcwk.scalehub.infrastructure.profile.GoalStore(
            context, memberRepository::activeId, database.goalDao(), pendingSync,
        )
    }

    /** 目标写入口（GoalStore 藏在容器后，feature 层只见函数类型）。 */
    val setTargetWeight: (Float) -> Unit = { kg -> goalStore.targetWeight = kg }

    val recordMeasurementService: RecordMeasurementService by lazy {
        RecordMeasurementService(measurementRepository, profileStoreImpl, dataRefreshBus, memberId = activeMemberId)
    }
    /** 查询端口（T-016 读写分离）：Room DAO 直读，与仓储写口分离。 */
    val measurementQueryPort: MeasurementQueryPort by lazy {
        RoomMeasurementQuery(database.measurementDao(), activeMemberId)
    }
    val healthLogQueryPort: HealthLogQueryPort by lazy {
        RoomHealthLogQuery(database.healthLogDao(), activeMemberId)
    }
    /** 读模型壳（UI 消费）：测量/健康记录最新列表。 */
    val measurementQuery: MeasurementQuery by lazy { MeasurementQuery(measurementQueryPort) }
    val healthLogQuery: HealthLogQuery by lazy { HealthLogQuery(healthLogQueryPort) }
    val trendQuery: TrendQuery by lazy { TrendQuery(measurementQueryPort) }
    val recordHealthLogService: RecordHealthLogService by lazy {
        RecordHealthLogService(healthLogRepository, dataRefreshBus, memberId = activeMemberId)
    }
    val profileQuery: ProfileQuery by lazy { ProfileQuery(profileStoreImpl) }

    /** 习惯仓储与查询（P0-2 习惯管理页）。 */
    private val habitDao: HabitDao by lazy { database.habitDao() }
    private val habitRepository: RoomHabitRepository by lazy {
        RoomHabitRepository(habitDao, activeMemberId)
    }
    val habitRepositoryPort: com.xcwk.scalehub.domain.repository.HabitRepository get() = habitRepository
    val habitQuery: HabitQuery by lazy { HabitQuery(habitRepository) }

    /** 习惯写入用例（新建/编辑/删除/打卡）。 */
    val recordHabitService: RecordHabitService by lazy {
        RecordHabitService(
            habitRepository, dataRefreshBus, habitReminderScheduler, memberId = activeMemberId,
        )
    }

    /** 习惯提醒调度（WorkManager）。 */
    val habitReminderScheduler: HabitReminderScheduler by lazy {
        WorkManagerHabitReminderScheduler(context)
    }

    /** 首页目标进度查询（移植补齐 H1）。 */
    val homeGoalQuery: com.xcwk.scalehub.application.query.HomeGoalQuery by lazy {
        com.xcwk.scalehub.application.query.HomeGoalQuery(
            measurementQueryPort, healthLogQueryPort, goalStore, profileStoreImpl,
        )
    }
    val reportQuery: ReportQuery by lazy { ReportQuery(measurementQueryPort) }
    val updateProfileService: UpdateProfileService by lazy {
        UpdateProfileService(profileStoreImpl, dataRefreshBus)
    }
    val deleteMeasurementService: DeleteMeasurementService by lazy {
        DeleteMeasurementService(measurementRepository, dataRefreshBus)
    }

    /** 账号恢复 / 注销用例（表现层不发数据变更信号，广播在此层收口）。 */
    val restoreAccountService: RestoreAccountService by lazy {
        RestoreAccountService(syncService, dataRefreshBus)
    }
    val deleteAccountService: DeleteAccountService by lazy {
        DeleteAccountService(syncService, dataRefreshBus)
    }

    /** 头像存取口（AvatarStore 藏在容器后，feature 层只见函数类型）。 */
    private val avatarStore: com.xcwk.scalehub.infrastructure.profile.AvatarStore by lazy {
        com.xcwk.scalehub.infrastructure.profile.AvatarStore(context)
    }
    val avatarPath: () -> String = { avatarStore.path(memberRepository.activeId()) ?: "" }
    val saveAvatar: (String) -> String? =
        { s -> avatarStore.saveFromUri(android.net.Uri.parse(s), memberRepository.activeId()) }
    val newCameraTarget: () -> String? = { avatarStore.newCameraTarget() }

    /** 成员操作口（MemberStore 藏在容器后；切换/增删经信号总线广播刷新）。 */
    val memberList: () -> List<com.xcwk.scalehub.domain.model.Member> = { memberRepository.members() }
    val activeMemberId: () -> String = { memberRepository.activeId() }
    val switchMember: (String) -> Unit = { id ->
        memberRepository.switch(id)
        dataRefreshBus.notifyChanged(DataChange.MemberScopeChanged)
    }
    val addMember: (String) -> Boolean = { name ->
        memberRepository.add(name) != null
    }
    val removeMember: (String) -> Boolean = { id ->
        val ok = memberRepository.remove(id)
        if (ok) {
            removeMemberLocalData(id)
            dataRefreshBus.notifyChanged(DataChange.MemberScopeChanged)
        }
        ok
    }
    val memberAvatarPath: (String) -> String = { id -> avatarStore.path(id) ?: "" }

    /** 当前成员槽位读取口（MemberStore 藏在容器后，feature 层不直调 infra）。 */
    val activeMemberSlot: () -> Int = { memberRepository.activeSlot() }

    /**
     * 删成员：测量/日志/习惯仍留在库里但列表按 memberId 不可见；档案键保留为孤儿（DEC-S09）。
     * 头像文件删除。不清云、不清整库。
     */
    val removeMemberLocalData: (String) -> Unit = { id ->
        avatarStore.removeFor(id)
    }

    /** AI 预填问题暂存（首页提问框 → AI 页；进程内有效即可）。 */
    var aiPrefillText: String = ""
    val aiPrefill: () -> String = { aiPrefillText }

    /** 正式账号读取口（SessionStore 藏在容器后，feature 层不直调 infra）。 */
    val isFormalAccount: () -> Boolean = { sessionStoreImpl.isFormalAccount() }

    /** 已绑定秤仓储（M3c）。具体类型不出容器，表现层只见端口（DEC-035）。 */
    private val deviceRepositoryImpl: RoomDeviceRepository by lazy {
        RoomDeviceRepository(database.deviceDao(), pendingSync)
    }

    val deviceRepository: DeviceRepository get() = deviceRepositoryImpl

    private val syncStateRepo: com.xcwk.scalehub.infrastructure.persistent.repository.RoomSyncStateRepository by lazy {
        com.xcwk.scalehub.infrastructure.persistent.repository.RoomSyncStateRepository(
            database.syncStateDao(),
        ) { System.currentTimeMillis() }
    }

    private val localData: com.xcwk.scalehub.infrastructure.privacy.LocalDataController by lazy {
        com.xcwk.scalehub.infrastructure.privacy.LocalDataController(
            context, database, profileStoreImpl, goalStore, memberRepository, avatarStore,
        )
    }

    val syncService: SyncService by lazy {
        SyncService(
            sessionStoreImpl, cloudGateway, sessionStoreImpl, deviceRepositoryImpl, goalStore,
            measurementRepository, healthLogRepository, measurementQueryPort, healthLogQueryPort,
            profileStoreImpl, profileStoreImpl,
            memberId = activeMemberId,
            syncState = syncStateRepo,
            measurementSync = com.xcwk.scalehub.infrastructure.persistent.repository.RoomMeasurementSync(
                database.measurementSyncDao(),
            ),
            healthLogSync = com.xcwk.scalehub.infrastructure.persistent.repository.RoomHealthLogSync(
                database.healthLogSyncDao(),
            ),
            pendingSync = pendingSync,
            localData = localData,
        )
    }

    /** 清本机（PH-R3）：不触云、保留会话。 */
    val clearLocalData: suspend () -> com.xcwk.scalehub.application.sync.SyncOutcome = {
        syncService.clearLocal()
    }

    /**
     * 云推送触发器：本地写入事件点火（DEC-034 组 A，替代 F-002 的「写用例注入 SyncTrigger」）。
     *
     * 用例现在只发 DataChange，既不知道 UI 也不知道云；换同步策略（批量/定时/仅 Wi-Fi）
     * 只改 [SyncOnLocalWrites] 一处订阅，不必动任何用例。
     *
     * 订阅在容器构造期即启动（不能藏在 lazy 里：lazy 要等首次访问才初始化，
     * 而用例不再持有它的引用，第一个写入事件可能在订阅建立前就发出去了）。
     */
    val syncTrigger: SyncTrigger by lazy { SyncTrigger(applicationScope) { syncService.push() } }

    init {
        memberRepository.bindProfileAuthority(profileStoreImpl)
        com.xcwk.scalehub.infrastructure.profile.LegacyUnitMigration.migrate(context, profileStoreImpl)
        com.xcwk.scalehub.infrastructure.consent.ConsentStore(context)
            .seedLegacyIfEntered(sessionStoreImpl.isEntered())
        SyncOnLocalWrites(dataRefreshBus, applicationScope, syncTrigger).start()
    }

    val authService: AuthService by lazy { AuthService(sessionStoreImpl, syncService) }

    /** AI 问答（M2c）：实现随 M2 部署联调（云函数 ai-chat）。 */
    val aiService: AiService by lazy { AiService(sessionStoreImpl, CloudFunctionAi(sessionStoreImpl)) }

    /** 测量页偏好（"不再提示只存体重"开关，旧库 skipWeightOnlyPrompt）。 */
    private val measurePrefs: android.content.SharedPreferences by lazy {
        context.getSharedPreferences("measure", android.content.Context.MODE_PRIVATE)
    }

    /** "不再提示只存体重"读取口（SharedPreferences 藏在容器后，feature 层不直触）。 */
    val skipWeightOnlyPrompt: () -> Boolean = { measurePrefs.getBoolean("skip_weight_only", false) }

    /**
     * 蓝牙链路（M3c）：测量页经 MeasureSession 使用；权限门在 trigger 层。
     * 具体类型不出容器，表现层只见 [BlePort]（DEC-035）。
     */
    private val bleLinkImpl: AndroidBleLink by lazy { AndroidBleLink(context) }

    val blePort: BlePort get() = bleLinkImpl
}
