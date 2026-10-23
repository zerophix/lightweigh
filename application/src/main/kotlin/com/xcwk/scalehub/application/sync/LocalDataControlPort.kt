package com.xcwk.scalehub.application.sync

/**
 * 本机数据清理（DEC-S05 / DEC-S11）。
 *
 * 两个方法都只动本机，不调用云（清本机 PH-R4）。
 * 注销的云删除与会话 signOut 由 [SyncService] 编排，不在本端口里。
 */
interface LocalDataControlPort {
    /** L2：验码成功后硬删测量库业务表、档案/目标/头像/缓存，成员重置为「我」。 */
    suspend fun wipeAfterAccountDeletion()

    /** PH-R3：同上的本机业务清理，调用方保留 session，不触云。 */
    suspend fun clearLocalKeepingSession()
}
