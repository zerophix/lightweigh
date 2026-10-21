# ProGuard/R8 规则（T-018/DEC-043）

# ---- 保留序列化模型（kotlinx.serialization / org.json）----
-keep class com.xcwk.scalehub.domain.model.** { *; }
-keep class com.xcwk.scalehub.application.sync.CloudRow { *; }

# ---- org.json（Android 内置，但 R8 可能误删）----
-keep class org.json.** { *; }

# ---- 蓝牙回调（反射调用）----
-keep class com.xcwk.scalehub.infrastructure.ble.** { *; }

# ---- Room 实体与 DAO ----
-keep class com.xcwk.scalehub.infrastructure.persistent.po.** { *; }
-keep interface com.xcwk.scalehub.infrastructure.persistent.dao.** { *; }

# ---- Kotlin 反射（data class copy/equals 等）----
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# ---- Compose（官方推荐）----
-dontwarn androidx.compose.**

# ---- kotlinx-coroutines ----
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ---- zxing（扫码）----
-keep class com.journeyapps.barcodescanner.** { *; }

# ---- 移除日志（release 不输出 Log.d）----
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}