package com.xcwk.scalehub.infrastructure.ble

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build

/**
 * 权限契约：所有蓝牙调用的运行期权限由调用方入口门禁保证；
 * 运行期 SecurityException 另有实现内 try/catch 兜底（旧库 BlePermission.java 移植）。
 * 偏差声明：用框架 API（Activity.requestPermissions）替代旧库 ActivityCompat，
 * infrastructure 模块不引入 androidx.core；minSdk ≥ 23 时框架 API 等价。
 */
object BlePermission {

    fun required(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun granted(activity: Activity): Boolean = required().all {
        activity.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    fun request(activity: Activity, requestCode: Int) {
        activity.requestPermissions(required(), requestCode)
    }

    /** 用户拒绝过一次、系统仍允许再次弹出授权框。 */
    fun canAskAgain(activity: Activity): Boolean = required().any {
        activity.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED &&
            activity.shouldShowRequestPermissionRationale(it)
    }
}
