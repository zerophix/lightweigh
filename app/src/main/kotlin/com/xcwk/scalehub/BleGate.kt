package com.xcwk.scalehub

import android.app.Activity
import com.xcwk.scalehub.infrastructure.ble.BlePermission

/**
 * trigger 层蓝牙权限门面——把 infrastructure 的权限契约抬到应用根包：
 * feature/ui 层不直接依赖 infrastructure（ArchUnit 已上锁），经此转发。
 */
object BleGate {
    fun required(): Array<String> = BlePermission.required()
    fun granted(activity: Activity): Boolean = BlePermission.granted(activity)
    fun request(activity: Activity, requestCode: Int) = BlePermission.request(activity, requestCode)
    fun canAskAgain(activity: Activity): Boolean = BlePermission.canAskAgain(activity)
}
