package app.nightsail.lockscreen

import android.content.Context
import app.nightsail.engine.Engine
import app.nightsail.engine.LockScreenLauncher

/**
 * lockscreen 模块的安装入口：宿主 app 模块在 Application.onCreate 里调一次 [install]。
 * engine 由此获得拉起锁屏的能力，而仍然不感知锁屏的实现细节。
 */
object LockScreenEntry {

    fun install() {
        Engine.instance.lockScreenLauncher = object : LockScreenLauncher {
            override fun launch(context: Context, interceptedPackage: String) {
                LockActivity.start(context, interceptedPackage)
            }
        }
    }
}
