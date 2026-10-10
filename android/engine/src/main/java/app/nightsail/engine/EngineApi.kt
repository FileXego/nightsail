package app.nightsail.engine

import android.util.Log

/**
 * EngineApi —— Flutter 与引擎之间的**唯一**接口面。
 *
 * app 模块的 MethodChannel 处理器只调这里；未来任何新命令都先在这里加方法，
 * 而不是在通道处理器里长业务逻辑。六个命令 + 一个状态快照，冻结面。
 */
object EngineApi {

    private const val TAG = "NightsailApi"

    fun arm(mode: String, durationMinutes: Int) {
        val lockMode = when (mode) {
            "sleep" -> LockMode.SLEEP
            "zen" -> LockMode.ZEN
            "pomodoro" -> LockMode.POMODORO
            else -> {
                Log.w(TAG, "unknown mode: $mode")
                return
            }
        }
        Engine.instance.arm(lockMode, durationMinutes * 60_000L)
    }

    fun disarm() = Engine.instance.disarm()

    fun requestEmergencyUnlock() = Engine.instance.requestEmergencyUnlock()

    fun setWhitelist(apps: List<String>) = Engine.instance.saveWhitelist(apps.toSet())

    /** Flutter 冷启动恢复界面时调用：状态的唯一真相在引擎，不在 widget 树。 */
    fun snapshot(): Map<String, Any?> {
        val sm = Engine.instance.stateMachine
        val s = sm.session
        return mapOf(
            "state" to sm.state.name,
            "mode" to s?.mode?.name,
            "remainingSeconds" to sm.remainingMillis() / 1000,
            "emergencyRequested" to (s?.emergencyRequested ?: false),
            "accessibilityAlive" to
                (System.currentTimeMillis() - LockAccessibilityService.lastEventAtMillis < 60_000),
            "whitelistedNow" to (s?.whitelist?.toList() ?: emptyList<String>()),
        )
    }

    /** 引擎心跳：Flutter 侧轮询（1s）驱动 EventChannel 状态流。 */
    fun tick() = Unit
}
