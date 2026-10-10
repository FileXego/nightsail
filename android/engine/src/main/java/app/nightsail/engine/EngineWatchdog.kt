package app.nightsail.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 看门狗 —— 周期体检"设置说想开、但进程不在"的裂缝。
 * WorkManager 而非自建线程：它本身就是唯一被 OS 认可跨进程死亡存活的调度器。
 */
object EngineWatchdog {

    private const val WORK_NAME = "nightsail_engine_watchdog"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<EngineWatchdogWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class EngineWatchdogWorker(appContext: Context, params: WorkerParameters) :
    Worker(appContext, params) {

    override fun doWork(): Result {
        val context = applicationContext
        val enabled = AccessibilityState.isEnabledInSettings(context)
        val active = SessionStore.load(context) != null
        val bound = LockAccessibilityService.instance != null
        if (!enabled || !active) {
            SessionStore.resetStrikes(context)
            return Result.success()
        }
        if (bound) {
            SessionStore.resetStrikes(context)
            return Result.success()
        }
        // 连续两次巡检都发现"该开却没绑定"才告警，避免一次瞬时抖动就惊动用户。
        val strikes = SessionStore.bumpStrikes(context)
        if (strikes >= 2) notifyInterrupted(context)
        return Result.success()
    }

    private fun notifyInterrupted(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "锁机防护告警",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("夜航 · 防护已中断")
            .setContentText("无障碍服务被系统回收，锁机可能已失效，请点按重新开启")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIF_ID, notif) }   // POST_NOTIFICATIONS 可能未授予
    }

    private companion object {
        // 独立频道：不能复用前台服务的 nightsail_engine（那是 IMPORTANCE_MIN），
        // 否则告警会被压成静默通知，看门狗等于白做。
        const val CHANNEL_ID = "nightsail_alerts"
        const val NOTIF_ID = 43
    }
}
