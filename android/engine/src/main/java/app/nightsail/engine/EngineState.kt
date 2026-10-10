package app.nightsail.engine

import android.os.SystemClock

/**
 * 引擎状态机 —— 全应用唯一真相源。
 *
 * 四个状态，所有组件（无障碍服务 / 闹钟 / 前台服务 / 锁屏页 / Flutter）
 * 只与状态机对话，不互相直接通信。
 *
 * IDLE → ARMING → LOCKED → ENDING → IDLE
 *
 * 状态机不依赖任何 Android Context，可纯 JVM 单测。
 */
enum class EngineState {
    IDLE,
    /** 临锁提醒期（就寝前 30 分钟的可选提醒，引擎此刻不拦应用） */
    ARMING,
    LOCKED,
    /** 解锁过渡期：Flutter 播放拉帘晨安动画的窗口 */
    ENDING,
}

enum class LockMode { SLEEP, ZEN, POMODORO }

/** 一次锁机会话的全部事实。immutable，变更即整体替换。 */
data class Session(
    val mode: LockMode,
    val startedAtMillis: Long,
    val lockUntilMillis: Long,
    val whitelist: Set<String>,
    val emergencyRequested: Boolean = false,
    val endedEarlyByPartner: Boolean = false,
)

class EngineStateMachine(
    private var now: () -> Long = System::currentTimeMillis,
    /** 单调时钟，专供短期放行表：系统改时间不影响放行窗口。可注入，保持纯 JVM 可测。 */
    private var elapsed: () -> Long = { SystemClock.elapsedRealtime() },
) {
    var state: EngineState = EngineState.IDLE
        private set

    var session: Session? = null
        private set

    /** 锁屏页 5 秒短暂放行表：锁屏页上点击打开的白名单应用 */
    private val temporaryPasses = HashMap<String, Long>()

    private val listeners = mutableListOf<(EngineState, Session?) -> Unit>()

    fun addListener(l: (EngineState, Session?) -> Unit) {
        listeners += l
    }

    fun arm(mode: LockMode, lockUntilMillis: Long, whitelist: Set<String>): Boolean {
        if (state == EngineState.LOCKED) return false
        session = Session(mode, now(), lockUntilMillis, whitelist)
        transition(EngineState.LOCKED)
        return true
    }

    /** 禅定模式拒绝手动解除；其余模式允许（供测试壳与 M3 问责审批用）。 */
    fun disarm(): Boolean {
        val s = session ?: return false
        if (s.mode == LockMode.ZEN) return false
        transition(EngineState.ENDING)
        return true
    }

    fun finishNaturally() {
        if (state == EngineState.LOCKED) transition(EngineState.ENDING)
    }

    fun acknowledgeEnded() {
        session = null
        temporaryPasses.clear()
        transition(EngineState.IDLE)
    }

    fun isLocked(): Boolean = state == EngineState.LOCKED && !isExpired()

    fun isExpired(): Boolean {
        val s = session ?: return false
        return now() >= s.lockUntilMillis
    }

    fun remainingMillis(): Long {
        val s = session ?: return 0
        return (s.lockUntilMillis - now()).coerceAtLeast(0)
    }

    fun isWhitelisted(pkg: String): Boolean = session?.whitelist?.contains(pkg) == true

    /** 锁屏页请求打开白名单应用：登记 5 秒放行。 */
    fun grantTemporaryPass(pkg: String, durationMs: Long = 5000) {
        temporaryPasses[pkg] = elapsed() + durationMs
    }

    fun hasTemporaryPass(pkg: String): Boolean {
        val until = temporaryPasses[pkg] ?: return false
        return elapsed() < until
    }

    fun requestEmergencyUnlock(): Boolean {
        val s = session ?: return false
        if (s.emergencyRequested) return false
        session = s.copy(emergencyRequested = true)
        return true
    }

    fun markEndedByPartner() {
        val s = session ?: return
        session = s.copy(endedEarlyByPartner = true)
        transition(EngineState.ENDING)
    }

    private fun transition(next: EngineState) {
        state = next
        listeners.forEach { it(state, session) }
    }
}
