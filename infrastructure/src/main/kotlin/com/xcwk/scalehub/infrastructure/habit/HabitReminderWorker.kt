package com.xcwk.scalehub.infrastructure.habit

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 习惯提醒 Worker（每日执行，发本地通知）。
 * 输入：habit_id, member_id, habit_name
 */
class HabitReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val habitName = inputData.getString("habit_name") ?: "习惯"
        val habitId = inputData.getString("habit_id") ?: return Result.success()
        val memberId = inputData.getString("member_id") ?: return Result.success()

        showNotification(habitName, habitId, memberId)
        return Result.success()
    }

    private fun showNotification(habitName: String, habitId: String, memberId: String) {
        val channelId = "habit_reminder"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "习惯提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "每日习惯打卡提醒"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(0) // 需确保 res/drawable 有该图标
            .setContentTitle("习惯提醒")
            .setContentText("该打卡了：$habitName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(habitId.hashCode(), notification)
    }
}