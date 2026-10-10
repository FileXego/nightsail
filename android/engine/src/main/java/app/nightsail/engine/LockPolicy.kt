package app.nightsail.engine

/**
 * 白名单策略 —— 纯函数，无 Android 依赖，可单测。
 *
 * 判断链（优先级从高到低）：
 * 1. 自家包名永远放行（锁屏页/宿主本身）
 * 2. 锁屏页 5 秒短暂放行表（用户从锁屏页点开白名单应用）
 * 3. 用户白名单（会话内生效）
 * 4. 系统关键组件（Keyguard / 系统 UI / 电话来电）永远放行
 * 5. 其余命中 → 锁
 */
object LockPolicy {

    /** 系统关键组件：锁了会让手机变砖或漏接电话的东西 */
    private val SYSTEM_ALLOW = setOf(
        "com.android.systemui",            // 状态栏/导航栏/最近任务
        "com.android.keyguard",            // Keyguard（含 ColorOS 乐划锁屏宿主）
        "com.android.incallui",            // 来电界面
        "com.android.phone",               // 拨号
        "com.android.dialer",
        "com.android.server.telecom",
    )

    /** 锁机期间额外必锁：防卸载、防改时间（ColorOS 包名另行补充） */
    val ALWAYS_BLOCK_WHEN_LOCKED = setOf(
        "com.android.settings",            // 设置（改时间/卸载/清数据入口）
        "com.heytap.market",               // OPPO 软件商店
        "com.android.vending",             // Play 商店
        "com.android.packageinstaller",
    )

    fun shouldIntercept(
        pkg: String,
        selfPackage: String,
        whitelisted: Boolean,
        hasTemporaryPass: Boolean,
        launcherPackages: Set<String>,
    ): Boolean {
        if (pkg == selfPackage) return false
        if (pkg in SYSTEM_ALLOW) return false
        if (launcherPackages.contains(pkg)) return false       // 桌面是"走廊"，放行它本身
        if (hasTemporaryPass) return false                     // 从锁屏页刚点开的白名单应用
        if (whitelisted) return false                          // 用户白名单（微信/QQ/电话）
        return true
    }

    fun shouldHardBlock(pkg: String): Boolean = pkg in ALWAYS_BLOCK_WHEN_LOCKED
}
