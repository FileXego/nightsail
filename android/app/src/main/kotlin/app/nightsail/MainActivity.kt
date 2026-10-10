package app.nightsail

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import app.nightsail.engine.EngineApi

/**
 * Flutter 宿主 —— 司令部。
 *
 * 纪律：通道处理器里**只有**对 EngineApi 的薄调用，不长任何业务逻辑。
 * 状态流用 1s 轮询引擎快照推送（引擎是唯一真相源，Flutter 随时可能
 * 被系统冻死再复活，醒来后拿一次快照即可恢复界面）。
 */
class MainActivity : FlutterActivity() {

    companion object {
        const val METHOD_CHANNEL = "app.nightsail/engine"
        const val EVENT_CHANNEL = "app.nightsail/engine_events"
    }

    private var events: EventChannel.EventSink? = null
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            events?.success(EngineApi.snapshot())
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        flutterEngine?.let { engine ->
            MethodChannel(engine.dartExecutor.binaryMessenger, METHOD_CHANNEL).setMethodCallHandler { call, result ->
                when (call.method) {
                    "arm" -> {
                        EngineApi.arm(
                            call.argument<String>("mode") ?: "sleep",
                            call.argument<Int>("durationMinutes") ?: 25,
                        )
                        result.success(null)
                    }
                    "disarm" -> {
                        EngineApi.disarm()
                        result.success(null)
                    }
                    "requestEmergencyUnlock" -> {
                        EngineApi.requestEmergencyUnlock()
                        result.success(null)
                    }
                    "setWhitelist" -> {
                        EngineApi.setWhitelist(call.argument<List<String>>("apps") ?: emptyList())
                        result.success(null)
                    }
                    "snapshot" -> result.success(EngineApi.snapshot())
                    else -> result.notImplemented()
                }
            }

            EventChannel(engine.dartExecutor.binaryMessenger, EVENT_CHANNEL).setStreamHandler(
                object : EventChannel.StreamHandler {
                    override fun onListen(arguments: Any?, sink: EventChannel.EventSink?) {
                        events = sink
                        handler.post(ticker)
                    }

                    override fun onCancel(arguments: Any?) {
                        handler.removeCallbacks(ticker)
                        events = null
                    }
                },
            )
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }
}
