package app.nightsail.engine

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * 感知层：无障碍服务监听前台应用变化。
 *
 * 设计纪律（DetoxDroid 哲学）：
 * - 只做"读包名 → 查策略 → 决定要不要锁"，这条路径上零 IO、零跨进程调用
 * - 状态判断全部委托给状态机与纯函数 LockPolicy
 * - 拉起锁屏页用显式 Intent，锁屏模块通过 [LockScreenLauncher] 接口解耦
 */
class LockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "NightsailAcc"
        /** 引擎存活心跳：每收到一次事件刷新一次，供 Flutter 体检"无障碍是否被系统回收" */
        @Volatile var lastEventAtMillis: Long = 0
            private set

        /** 进程级实例句柄：watchdog 据此判断服务是否仍绑定存活。 */
        @Volatile var instance: LockAccessibilityService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Engine.instance.onAccessibilityConnected()
        Log.i(TAG, "accessibility connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        lastEventAtMillis = System.currentTimeMillis()
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val engine = Engine.instance
        if (!engine.stateMachine.isLocked()) return

        val pkg = event.packageName?.toString() ?: return
        val decision = engine.evaluate(pkg)
        if (decision == Decision.INTERCEPT) {
            engine.lockScreenLauncher?.launch(this, pkg)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        Engine.instance.onAccessibilityDisconnected()
        super.onDestroy()
    }
}
