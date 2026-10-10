package app.nightsail.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Engine —— 进程级单例，把四个系统组件（无障碍 / 前台服务 / 闹钟 / 状态机）
 * 缝合成一个对外门面。它不 import 任何 Flutter 代码，也不 import 任何网络代码。
 *
 * 对 Flutter：app 模块用 MethodChannel 调 [EngineApi]（见 EngineApi.kt）。
 * 对锁屏 UI：通过 [LockScreenLauncher] 接口拉起，lockscreen 模块实现它。
 */
class Engine private constructor() {

    companion object {
        val instance = Engine()
        private const val TAG = "NightsailEngine"

        const val ACTION_LOCK = "app.nightsail.engine.LOCK"
        const val ACTION_UNLOCK = "app.nightsail.engine.UNLOCK"

        /** Flutter 改白名单/配置时的统一存储 key */
        const val PREFS = "engine_prefs"
        const val KEY_WHITELIST = "whitelist"
    }

    val stateMachine = EngineStateMachine()

    /** 由 lockscreen 模块注入，engine 不感知锁屏长什么样 */
    @Volatile var lockScreenLauncher: LockScreenLauncher? = null

    @Volatile private lateinit var appContext: Context
    private var foregroundServiceUp = false
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return   // 幂等：重复调用会重复挂监听，导致状态跃迁翻倍触发
        initialized = true
        appContext = context.applicationContext
        stateMachine.addListener { state, _ -> onStateChanged(state) }
        restoreFromStore()
    }

    /** 进程重启/开机后从磁盘重建会话，并重新武装前台服务与解锁闹钟。 */
    fun restoreFromStore() {
        val s = SessionStore.load(appContext) ?: return
        stateMachine.restore(s)
        ensureForegroundService()
        scheduleAlarms(s.lockUntilMillis)
    }

    /** 开机 / 应用升级广播入口。init 幂等，故可安全重复调用。 */
    fun onBoot(context: Context) {
        init(context)
        restoreFromStore()
        EngineWatchdog.schedule(context)
    }

    /* ---------- 对外门面：发动 / 解除 ---------- */

    fun arm(mode: LockMode, durationMillis: Long) {
        val whitelist = loadWhitelist()
        val until = System.currentTimeMillis() + durationMillis
        if (stateMachine.arm(mode, until, whitelist)) {
            SessionStore.save(appContext, stateMachine.session!!)
            scheduleAlarms(until)
            ensureForegroundService()
        }
    }

    fun disarm() {
        if (stateMachine.disarm()) cancelAlarms()
    }

    fun requestEmergencyUnlock() {
        stateMachine.requestEmergencyUnlock()
        // M3：此处发布本地 UnlockRequested 事件，comm 模块订阅后生成审批码并轮询。
        // 引擎对传输方式零感知；无 comm 模块时功能静默缺省。
    }

    /* ---------- 感知层回调 ---------- */

    fun onAccessibilityConnected() {
        // 心跳起点。ColorOS 存在熄屏回收无障碍服务的报告，Flutter 侧据此做体检。
        if (stateMachine.isLocked()) ensureForegroundService()
    }

    fun onAccessibilityDisconnected() {
        Log.w(TAG, "accessibility disconnected — engine blind until user re-enables")
    }

    /** 无障碍服务对每个前台应用调用，返回处置决定。 */
    fun evaluate(pkg: String): Decision {
        val sm = stateMachine
        if (LockPolicy.shouldHardBlock(pkg) && sm.isLocked()) return Decision.INTERCEPT
        val intercept = LockPolicy.shouldIntercept(
            pkg = pkg,
            selfPackage = appContext.packageName,
            whitelisted = sm.isWhitelisted(pkg),
            hasTemporaryPass = sm.hasTemporaryPass(pkg),
            launcherPackages = queryLaunchers(),
        )
        return if (intercept) Decision.INTERCEPT else Decision.ALLOW
    }

    /** 锁屏页请求打开白名单应用：短暂放行后由无障碍服务放行这次启动。 */
    fun openFromLockscreen(context: Context, pkg: String) {
        stateMachine.grantTemporaryPass(pkg)
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent != null) context.startActivity(intent)
    }

    /* ---------- 内部 ---------- */

    private fun onStateChanged(state: EngineState) {
        when (state) {
            EngineState.LOCKED -> ensureForegroundService()
            EngineState.ENDING -> {
                // 引擎只负责翻状态；拉帘动画/统计落库由 Flutter 订阅事件后完成
                stateMachine.acknowledgeEnded()
                SessionStore.clear(appContext)
            }
            else -> Unit
        }
    }

    private fun ensureForegroundService() {
        if (foregroundServiceUp) return
        val intent = Intent(appContext, EngineForegroundService::class.java)
        // 进程可能是在后台被拉起的（开机广播 / 无障碍重绑），此时 Android 12+ 可能拒绝 FGS 启动。
        // 拒绝不致命：会话已落盘、闹钟已排，等下次合法入口（应用打开 / 无障碍连接）再拉起。
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) appContext.startForegroundService(intent)
            else appContext.startService(intent)
            foregroundServiceUp = true
        }.onFailure { Log.w(TAG, "foreground service start deferred: ${it.message}") }
    }

    private fun scheduleAlarms(lockUntilMillis: Long) {
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // 解锁闹钟：锁屏页在屏时自己看表（轮询本地时间，分秒级准时）；
        // 闹钟是"锁屏页不在场"（如重启）时的保底。
        // setAlarmClock 无需精确闹钟权限，且被允许从后台拉起前台服务，
        // 因此用它作为锁机结束边界，替代旧的 setExactAndAllowWhileIdle + 权限降级分支。
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(lockUntilMillis, null),
            pendingIntent(ACTION_UNLOCK, 1),
        )
    }

    private fun cancelAlarms() {
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(ACTION_UNLOCK, 1))
    }

    private fun pendingIntent(action: String, reqCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            reqCode,
            Intent(action).setPackage(appContext.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // ponytail: 启动器集合每次前台切换都查，跨进程查询代价高 —— 首次算一次并缓存。
    // 锁机途中换桌面（极罕见）会短暂失准，进程重启即刷新；届时再挂 PackageMonitor。
    private var launcherCache: Set<String>? = null

    private fun queryLaunchers(): Set<String> = launcherCache ?: run {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        appContext.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .toSet()
            .also { launcherCache = it }
    }

    fun loadWhitelist(): Set<String> =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_WHITELIST, defaultWhitelist())!!
            .toSet()

    fun saveWhitelist(apps: Set<String>) {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_WHITELIST, apps)
            .apply()
    }

    private fun defaultWhitelist() = setOf(
        "com.tencent.mm",          // 微信
        "com.tencent.mobileqq",    // QQ
        "com.android.incallui",
        "com.android.dialer",
        "com.android.mms",
        "com.heytap.clock",        // ColorOS 时钟（闹钟必须可靠）
        "com.coloros.alarmclock",
    )
}

enum class Decision { ALLOW, INTERCEPT }

/** lockscreen 模块实现此接口并注入 Engine，engine 不反向依赖 lockscreen。 */
interface LockScreenLauncher {
    fun launch(context: Context, interceptedPackage: String)
}

/** 闹钟落地：到点翻状态机。静态注册（Manifest），进程死了也会响。 */
class LockAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Engine.ACTION_UNLOCK -> Engine.instance.stateMachine.finishNaturally()
            Engine.ACTION_LOCK -> Unit // M2：排程到点自动进入睡眠锁
        }
    }
}
