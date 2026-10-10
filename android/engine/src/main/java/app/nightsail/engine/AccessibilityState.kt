package app.nightsail.engine

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * "用户是否仍想开启无障碍服务"的唯一真相源。
 *
 * 该读取的是 OS 的 secure 设置：系统重启、OEM 杀进程都不会抹掉它，
 * 而常驻的 [LockAccessibilityService] 进程却可能被回收 —— 于是设置说"想开"，
 * 进程却不在，正是 watchdog 要检出的裂缝。
 */
object AccessibilityState {

    fun isEnabledInSettings(context: Context): Boolean {
        val cr = context.contentResolver
        if (Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false
        val enabled = Settings.Secure.getString(
            cr,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val target = ComponentName(context, LockAccessibilityService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(target, ignoreCase = true) }
    }
}
