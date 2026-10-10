package app.nightsail

import android.app.Application
import app.nightsail.engine.Engine
import app.nightsail.lockscreen.LockScreenEntry

class NightsailApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 引擎初始化 + 锁屏注入。engine 仍不 import Flutter——
        // Flutter 引擎由 MainActivity 按需启动，与锁机引擎互不依赖。
        Engine.instance.init(this)
        LockScreenEntry.install()
    }
}
