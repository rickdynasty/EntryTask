package com.entrytask.bridge

import com.entrytask.shell.domain.ShellCommand
import com.entrytask.shell.domain.ShellRepository
import com.entrytask.shell.domain.ShellSnapshot
import com.entrytask.shell.host.ForegroundHostConnection
import org.json.JSONObject

/** Bridge DTO 与 Shell 契约的边界；由 HostModule 在主线程调用状态变更。 */
class ShellBridgeAdapter(
    private val shell: ShellRepository,
    private val foreground: ForegroundHostConnection,
) {
    fun decode(raw: String): ShellCommand {
        require(raw.toByteArray().size <= 65536)
        val json = JSONObject(raw)
        return when (json.getString("type")) {
            "selectTab" -> ShellCommand.SelectTab(boundedInt(json, "tabId", 1..4))
            "navigation" -> ShellCommand.Navigation(boundedInt(json, "depth", 0..20))
            "authenticate" -> ShellCommand.Authenticate
            "logout" -> ShellCommand.Logout
            else -> throw IllegalArgumentException("INVALID_HOST_COMMAND")
        }
    }
    fun execute(command: ShellCommand) {
        when (command) {
            is ShellCommand.SelectTab -> shell.selectTab(command.tabId)
            is ShellCommand.Navigation -> shell.updateDepth(command.depth)
            ShellCommand.Logout -> shell.logout()
            ShellCommand.Authenticate -> {
                shell.refreshSession()
                if (shell.snapshots.value.authenticated) shell.refreshSession(forcePublish = true)
                else foreground.authenticate()
            }
        }
    }
    fun snapshot(): JSONObject {
        shell.refreshSession()
        return encode(shell.snapshots.value)
    }
    fun encode(value: ShellSnapshot): JSONObject = JSONObject()
        .put("tabId", value.tabId).put("depth", value.depth)
        .put("authenticated", value.authenticated).put("sessionEpoch", value.sessionEpoch)
        .put("revision", value.revision)

    private fun boundedInt(json: JSONObject, key: String, range: IntRange): Int {
        val number = json.get(key) as? Number ?: throw IllegalArgumentException("INVALID_$key")
        val value = number.toDouble()
        require(value.isFinite() && value == number.toInt().toDouble() && number.toInt() in range)
        return number.toInt()
    }
}
