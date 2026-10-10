package app.nightsail.lockscreen

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import app.nightsail.engine.Engine
import app.nightsail.engine.EngineState

/**
 * 原生锁屏页 —— 全 App 最特殊的页面。
 *
 * 纪律：零业务逻辑。不判断该不该锁（那是引擎的事），
 * 只负责把引擎状态渲染到屏幕上 + 把用户动作转发给引擎。
 *
 * 五个武器：
 * 1. showWhenLocked —— 叠在系统 Keyguard 之上，不替换用户的指纹/密码
 * 2. singleInstance —— 任何上下文拉起都只存在一个实例
 * 3. 返回键无效
 * 4. configChanges —— 旋转/折叠不重建，倒计时不闪断
 * 5. 解锁判定轮询本地时间（分秒级准时；闹钟只作"锁屏页不在场"的保底）
 */
class LockActivity : Activity() {

    companion object {
        const val EXTRA_INTERCEPTED = "intercepted_pkg"

        fun start(context: Context, intercepted: String?) {
            val intent = Intent(context, LockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_INTERCEPTED, intercepted)
            context.startActivity(intent)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var countdown: TextView
    private lateinit var statusLine: TextView

    private val tickRunnable = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setTaskDescription(ActivityManager.TaskDescription("夜航"))

        val root = buildUi()
        setContentView(root)
        maybeDisableStatusBar(disable = true)
    }

    override fun onResume() {
        super.onResume()
        handler.post(tickRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(tickRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        maybeDisableStatusBar(disable = false)
        super.onDestroy()
    }

    override fun onBackPressed() { /* 武器三：返回键无效 */ }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        keyCode == KeyEvent.KEYCODE_BACK || super.onKeyDown(keyCode, event)

    /* ---------- UI（代码布局，保持模块零资源耦合最小化；主题图由宿主 drawable 提供） ---------- */

    private fun buildUi(): LinearLayout {
        val accent = 0xFFE6CF9A.toInt()
        val high = 0xFFF2EEE4.toInt()
        val low = 0xFF6E6858.toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0C0D12.toInt())
            setPadding(48, 160, 48, 64)
        }

        statusLine = TextView(this).apply {
            textSize = 11f
            setTextColor(low)
            letterSpacing = 0.3f
            text = "睡眠锁运行中"
        }

        countdown = TextView(this).apply {
            textSize = 44f
            setTextColor(high)
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }

        val sub = TextView(this).apply {
            textSize = 12f
            setTextColor(low)
            text = "后自动解锁"
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }

        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
        }
        center.addView(countdown)
        center.addView(sub)

        val emergency = Button(this).apply {
            text = "申请紧急解锁 →"
            setTextColor(0xFFFF8A8E.toInt())
            setBackgroundColor(0x00000000)
            setOnClickListener {
                Engine.instance.requestEmergencyUnlock()
                text = "已通知问责伙伴 · 等待审批"
            }
        }

        val demo = Button(this).apply {
            text = "演示用 · 模拟完成"
            setTextColor(low)
            setBackgroundColor(0x00000000)
            setOnClickListener { Engine.instance.disarm(); finish() }
        }

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bottom.addView(emergency)
        bottom.addView(demo)

        root.addView(statusLine)
        root.addView(center, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottom)
        return root
    }

    /* ---------- 渲染：每秒向引擎问一次 ---------- */

    private fun render() {
        val sm = Engine.instance.stateMachine
        val remainSec = sm.remainingMillis() / 1000
        countdown.text = "%02d:%02d:%02d".format(remainSec / 3600, remainSec % 3600 / 60, remainSec % 60)

        when {
            sm.state == EngineState.ENDING -> { finish() }
            sm.isExpired() -> { Engine.instance.stateMachine.finishNaturally(); finish() }
            sm.state == EngineState.IDLE -> { finish() }   // 引擎说解了，别赖着
        }
    }

    /** 设备管理员生效时，锁机期间禁下拉通知栏（防从通知跳进被锁应用）。 */
    private fun maybeDisableStatusBar(disable: Boolean) {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(this, AdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) return
        try {
            dpm.setStatusBarDisabled(admin, disable)
        } catch (_: SecurityException) {
            // 设备管理员权限范围外的表现差异，静默放过
        }
    }
}

class AdminReceiver : android.app.admin.DeviceAdminReceiver()
