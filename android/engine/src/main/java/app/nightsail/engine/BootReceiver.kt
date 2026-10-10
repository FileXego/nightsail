package app.nightsail.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机 / 应用升级后恢复锁机会话。进程死了，[Engine.instance] 的内存态归零，
 * 只能靠磁盘上的 SessionStore 重建 —— 否则锁会静默消失。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> Engine.instance.onBoot(context.applicationContext)
        }
    }
}
