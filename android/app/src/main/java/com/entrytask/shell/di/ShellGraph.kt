package com.entrytask.shell.di

import com.entrytask.bridge.ShellBridgeAdapter
import com.entrytask.shell.data.DefaultShellRepository
import com.entrytask.shell.data.UserAuthSessionAdapter
import com.entrytask.shell.host.ForegroundHostConnection

/** app 作用域的轻量依赖组装，与 ReactHost 分开管理，无 View/Activity 引用。 */
class ShellGraph {
    val session = UserAuthSessionAdapter()
    val repository = DefaultShellRepository(session)
    val foreground = ForegroundHostConnection()
    val bridge = ShellBridgeAdapter(repository, foreground)
}
