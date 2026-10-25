package com.entrytask.auth

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.entry.biz.user.domain.AuthRepository
import com.entry.biz.user.domain.AuthenticatedUser
import com.entry.biz.user.ui.navigation.AuthNavHost
import com.entry.biz.user.viewmodel.UserViewModel
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 用领域Fake验证真实Route：后台不导航，恢复不丢认证结果；不发网络或读写账号。 */
class AuthStateLifecycleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun backgroundSuccessIsDeliveredOnceWhenThePageResumes() {
        val result = CompletableDeferred<AuthenticatedUser>()
        val repository = object : AuthRepository {
            override suspend fun login(username: String, password: String) = result.await()
            override suspend fun register(username: String, password: String) = result.await()
        }
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        val successCount = AtomicInteger()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                AuthNavHost(
                    viewModelFactory = UserViewModel.Factory(repository),
                    onAuthSuccess = { successCount.incrementAndGet() },
                    onLoginBackPressed = {},
                    onAuthPageChanged = {},
                )
            }
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("lifecycle-demo")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("test-only")
        compose.onNodeWithText("登录").performClick()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        result.complete(AuthenticatedUser(1, "lifecycle-demo"))
        compose.waitForIdle()
        assertEquals(0, successCount.get())
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitUntil(10000) { successCount.get() == 1 }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
        assertEquals(1, successCount.get())
    }
}
