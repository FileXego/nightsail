package app.nightsail.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * 保活层：前台服务 + 常驻通知 + 部分唤醒锁。
 *
 * 纪律：
 * - 通知是法定成本（Android 8+），把它做成入口而不是负担
 * - 只做保活与状态展示，零业务逻辑
 * - START_STICKY + onTaskRemoved 双保险，对国产 ROM 尽力而为
 */
class EngineForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "nightsail_engine"
        const val NOTIF_ID = 42
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        acquireWakeLock()
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "锁机引擎",
                NotificationManager.IMPORTANCE_MIN,   // 不打扰用户
            ).apply { description = "睡眠锁运行中" }
            manager.createNotificationChannel(channel)
        }

        // 点击通知回到锁屏页（通过 launcher 注入的 PendingIntent 由 app 模块提供太绕，
        // 这里用启动宿主 Launcher Activity 的最朴素方式，M2 换成显式锁屏 Intent）
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("睡眠锁运行中")
            .setContentText("点按返回锁屏 · 夜航")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, contentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nightsail:engine").apply {
            acquire(10 * 60 * 60 * 1000L)   // 10 小时上限，防永久持有
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务划掉也拉回来（对国产 ROM 尽力而为，真正的命门是自启动白名单）
        val restart = Intent(this, EngineForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(restart) else startService(restart)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
