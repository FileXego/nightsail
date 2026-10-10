package app.nightsail.engine

import android.content.Context

/**
 * 会话磁盘持久化 —— 内存单例活不过国产 ROM 的夜间杀进程，状态必须落盘。
 * 复用引擎已有的 SharedPreferences 文件 [Engine.PREFS]，不引入第二个存储。
 */
object SessionStore {

    private const val KEY_ACTIVE = "session_active"
    private const val KEY_MODE = "session_mode"
    private const val KEY_STARTED = "session_started"
    private const val KEY_UNTIL = "session_until"
    private const val KEY_WHITELIST = "session_whitelist"
    private const val KEY_STRIKES = "watchdog_strikes"

    private fun prefs(context: Context) =
        context.getSharedPreferences(Engine.PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, session: Session) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_MODE, session.mode.name)
            .putLong(KEY_STARTED, session.startedAtMillis)
            .putLong(KEY_UNTIL, session.lockUntilMillis)
            .putStringSet(KEY_WHITELIST, session.whitelist.toMutableSet())
            .apply()
    }

    // ponytail: lockUntilMillis 是墙钟值，用户把系统时间往前调即可提前结束锁机；
    // 需要防篡改时钟时，升级路径是单调时钟 + boot-id 锚点。
    fun load(context: Context): Session? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ACTIVE, false)) return null
        val until = p.getLong(KEY_UNTIL, 0L)
        if (until <= System.currentTimeMillis()) {
            clear(context)
            return null
        }
        val mode = runCatching {
            LockMode.valueOf(p.getString(KEY_MODE, null) ?: LockMode.SLEEP.name)
        }.getOrDefault(LockMode.SLEEP)
        return Session(
            mode = mode,
            startedAtMillis = p.getLong(KEY_STARTED, 0L),
            lockUntilMillis = until,
            whitelist = p.getStringSet(KEY_WHITELIST, emptySet<String>())!!.toSet(),
        )
    }

    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_ACTIVE)
            .remove(KEY_MODE)
            .remove(KEY_STARTED)
            .remove(KEY_UNTIL)
            .remove(KEY_WHITELIST)
            .apply()
    }

    fun bumpStrikes(context: Context): Int {
        val p = prefs(context)
        val n = p.getInt(KEY_STRIKES, 0) + 1
        p.edit().putInt(KEY_STRIKES, n).apply()
        return n
    }

    fun resetStrikes(context: Context) {
        prefs(context).edit().putInt(KEY_STRIKES, 0).apply()
    }
}
