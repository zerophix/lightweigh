package com.xcwk.scalehub.infrastructure.ble

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat

/**
 * 权限契约：所有蓝牙调用的运行期权限由调用方入口门禁保证；
 * 运行期 SecurityException 另有实现内 try/catch 兜底（旧库 BlePermission.java 移植）。
 * 使用 ActivityCompat 以兼容厂商定制 ROM（小米/华为/OPPO/vivo 等）的权限检查差异。
 */
object BlePermission {

    fun required(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun granted(activity: Activity): Boolean = required().all {
        ActivityCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
    }

    fun request(activity: Activity, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, required(), requestCode)
    }

    /** 用户拒绝过一次、系统仍允许再次弹出授权框。 */
    fun canAskAgain(activity: Activity): Boolean = required().any {
        ActivityCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
    }
}
