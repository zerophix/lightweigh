package com.xcwk.scalehub.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DinnerDining
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Egg
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FreeBreakfast
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NoMeals
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Qk 图标表 —— 全 App 唯一的图标选型点。
 *
 * 选型口径：**Material Symbols 的语义**，落在 Compose 的 `material-icons-extended` 上。
 * 说明：Material Symbols 本身是可变字体（网页用 ligature 渲染），Compose 没有官方的
 * Symbols 产物；`material-icons-extended` 是同一套 24dp 网格、同一套隐喻的图标集，
 * 是当前 Compose 工程里语义最接近的等价物。若将来要严格对齐 Symbols 的可变字重，
 * 需引入矢量资源而非字体，届时只改本文件。
 *
 * 纪律：页面不直接 `import androidx.compose.material.icons.*`，一律经本表取图。
 * 这样换图标集只改一处，也避免同一语义在不同页面用了不同图标（此前「趋势」用
 * DateRange、「设备」用 List，与原型语义都不符）。
 */
object QkIcons {
    // 页签
    val Home: ImageVector = Icons.Outlined.Home
    val Trend: ImageVector = Icons.Outlined.BarChart
    val Device: ImageVector = Icons.Outlined.MonitorWeight
    val Mine: ImageVector = Icons.Outlined.Person

    // 壳层
    val Bell: ImageVector = Icons.Outlined.NotificationsNone
    val Add: ImageVector = Icons.Outlined.Add
    val Send: ImageVector = Icons.AutoMirrored.Outlined.Send
    val ChevronRight: ImageVector = Icons.Outlined.ChevronRight
    val Back: ImageVector = Icons.AutoMirrored.Outlined.ArrowBack

    // 身体数据
    val Weight: ImageVector = Icons.Outlined.MonitorWeight
    val BodyFat: ImageVector = Icons.Outlined.Opacity
    val Waist: ImageVector = Icons.Outlined.Straighten
    val Height: ImageVector = Icons.Outlined.Height
    val Bmi: ImageVector = Icons.Outlined.Calculate

    // 饮食
    val Meal: ImageVector = Icons.Outlined.Restaurant
    val Breakfast: ImageVector = Icons.Outlined.FreeBreakfast
    val Lunch: ImageVector = Icons.Outlined.LunchDining
    val Dinner: ImageVector = Icons.Outlined.DinnerDining
    val Protein: ImageVector = Icons.Outlined.Egg
    val Vegetable: ImageVector = Icons.Outlined.Eco
    val More: ImageVector = Icons.Outlined.MoreHoriz

    // 行为
    val Water: ImageVector = Icons.Outlined.WaterDrop
    val Exercise: ImageVector = Icons.Outlined.DirectionsRun
    val Walk: ImageVector = Icons.Outlined.DirectionsWalk
    val Run: ImageVector = Icons.Outlined.DirectionsRun
    val Bike: ImageVector = Icons.Outlined.DirectionsBike
    val Rope: ImageVector = Icons.Outlined.FitnessCenter
    val Sleep: ImageVector = Icons.Outlined.Bedtime
    val Habit: ImageVector = Icons.Outlined.CheckCircle
    val Fasting: ImageVector = Icons.Outlined.NoMeals
    val Period: ImageVector = Icons.Outlined.CalendarMonth
    val Goal: ImageVector = Icons.Outlined.Flag
    val Glucose: ImageVector = Icons.Outlined.Bloodtype
    val BloodPressure: ImageVector = Icons.Outlined.MonitorHeart

    // 功能入口
    val Ai: ImageVector = Icons.Outlined.AutoAwesome
    val Scan: ImageVector = Icons.Outlined.QrCodeScanner
    val Bluetooth: ImageVector = Icons.Outlined.Bluetooth
    val History: ImageVector = Icons.Outlined.History
    val Setting: ImageVector = Icons.Outlined.Settings
    val Account: ImageVector = Icons.Outlined.AccountCircle
    val SignOut: ImageVector = Icons.Outlined.Logout
    val DeleteAccount: ImageVector = Icons.Outlined.DeleteForever
    val Agreement: ImageVector = Icons.Outlined.Description
    val Privacy: ImageVector = Icons.Outlined.PrivacyTip
    val Collect: ImageVector = Icons.Outlined.Assignment
    val Share: ImageVector = Icons.Outlined.Share
    val Restore: ImageVector = Icons.Outlined.CloudDownload
    val Cache: ImageVector = Icons.Outlined.CleaningServices
    val About: ImageVector = Icons.Outlined.Info
}
