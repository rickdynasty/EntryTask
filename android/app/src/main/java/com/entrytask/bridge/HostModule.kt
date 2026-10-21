package com.entrytask.bridge

import com.entry.lib.log.MLog
import com.entrytask.BuildConfig
import com.entrytask.MainApplication
import com.entrytask.shell.domain.ShellCommand
import com.entrytask.shell.host.HostNotReadyException
import com.entrytask.specs.NativeHostSpec
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.drop
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.FileNotFoundException
import java.util.UUID

/** Codegen 边界：请求校验/资源读取在工作线程，Shell/UI 命令在主线程，不直接调用 Activity。 */
class HostModule(context: ReactApplicationContext) : NativeHostSpec(context) {
    private val graph = (context.applicationContext as MainApplication).shellGraph
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private data class PendingCommand(val json: String, val promise: Promise)
    private val commands = Channel<PendingCommand>(capacity = 64)
    @Volatile private var valid = true

    init {
        // 有界单消费者保持 RN 命令受理顺序，避免 Default 并发解析后倒序写回主线程。
        scope.launch {
            for (pending in commands) processCommand(pending.json, pending.promise)
        }
        scope.launch(Dispatchers.Main.immediate) {
            // 首次状态由 bootstrap 返回；后续变化独立于 UI 订阅持续发布。
            graph.repository.snapshots.drop(1).collect {
                // 模块构造早于 JSI 事件回调安装；Reload/销毁窗口不向失效 runtime 发事件。
                if (valid && reactApplicationContext.hasActiveReactInstance() && mEventEmitterCallback != null) {
                    emitOnHostEvent(graph.bridge.encode(it).toString())
                }
            }
        }
    }
    override fun command(requestJson: String, promise: Promise) {
        if (!valid) { promise.resolve(BridgeReply.error("RUNTIME_INVALIDATED")); return }
        // 入队前限长，避免有界条数却缓存无界字符串；解码仍在工作线程执行。
        if (requestJson.length > 65536) {
            MLog.w(TAG, "host.command.rejected code=INVALID_HOST_COMMAND")
            promise.resolve(BridgeReply.error("INVALID_HOST_COMMAND"))
        } else if (!commands.trySend(PendingCommand(requestJson, promise)).isSuccess) {
            MLog.w(TAG, "host.command.rejected code=HOST_COMMAND_BUSY")
            promise.resolve(BridgeReply.error("HOST_COMMAND_BUSY"))
        }
    }
    private suspend fun processCommand(requestJson: String, promise: Promise) {
        var type = "unknown"
        try {
            val command = graph.bridge.decode(requestJson)
            type = when (command) {
                is ShellCommand.SelectTab -> "selectTab"
                is ShellCommand.Navigation -> "navigation"
                ShellCommand.Authenticate -> "authenticate"
                ShellCommand.Logout -> "logout"
            }
            MLog.i(TAG, "host.command.received type=$type")
            withContext(Dispatchers.Main.immediate) { graph.bridge.execute(command) }
            // ACK 是受理结果，不代表认证成功；认证结果以会话快照为准。
            if (valid) promise.resolve(BridgeReply.ok(true))
            MLog.i(TAG, "host.command.completed type=$type")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val code = when (error) {
                is HostNotReadyException -> "HOST_NOT_READY"
                is IllegalArgumentException, is JSONException -> "INVALID_HOST_COMMAND"
                else -> "HOST_COMMAND_FAILED"
            }
            MLog.e(TAG, "host.command.failed type=$type code=$code exception=${error.javaClass.simpleName}")
            if (valid) promise.resolve(BridgeReply.error(code))
        }
    }
    override fun bootstrap(requestJson: String, promise: Promise) {
        if (!valid) { promise.resolve(BridgeReply.error("RUNTIME_INVALIDATED")); return }
        scope.launch {
            var flowId = "unknown"
            var stage = "request.decode"
            try {
                require(requestJson.toByteArray().size <= 65536)
                val request = JSONObject(requestJson)
                flowId = request.optString("flowId").take(80).replace(Regex("[^a-zA-Z0-9-]"), "")
                MLog.i(TAG, "bootstrap.started flowId=$flowId")
                stage = "contract.read"
                val contract = JSONObject(reactApplicationContext.assets.open(CONTRACT_PATH).bufferedReader().use { it.readText() })
                stage = "contract.validate"
                if (request.optString("protocolVersion") != contract.getString("protocolVersion")) {
                    if (valid) promise.resolve(BridgeReply.error("PROTOCOL_VERSION_MISMATCH"))
                    MLog.e(TAG, "bootstrap.failed code=PROTOCOL_VERSION_MISMATCH flowId=$flowId")
                    return@launch
                }
                val shell = withContext(Dispatchers.Main.immediate) { graph.bridge.snapshot() }
                val snapshot = JSONObject().put("protocolVersion", contract.getString("protocolVersion"))
                    .put("componentName", contract.getString("componentName")).put("bootId", bootId).put("shell", shell)
                    .put("capabilities", JSONArray(listOf("host.events", "host.command", "network.execute", "storage.assetVisibility", "session.snapshot", "telemetry.emitBatch")))
                if (valid) promise.resolve(BridgeReply.ok(snapshot))
                MLog.i(TAG, "bootstrap.completed flowId=$flowId bootId=$bootId")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val code = if (error is FileNotFoundException) "HOST_RESOURCE_MISSING" else "HOST_RESOURCE_INVALID"
                MLog.e(TAG, "bootstrap.failed code=$code stage=$stage flowId=$flowId exception=${error.javaClass.simpleName}", if (BuildConfig.DEBUG) error else null)
                if (valid) promise.resolve(BridgeReply.error(code))
            }
        }
    }
    override fun invalidate() {
        valid = false
        commands.cancel()
        scope.cancel()
        super.invalidate()
    }
    companion object {
        private const val TAG = "EntryTask.Bridge"
        private const val CONTRACT_PATH = "contracts/bridge/runtime.json"
        private val bootId = UUID.randomUUID().toString()
    }
}
