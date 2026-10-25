package com.entry.biz.dashboard.presentation.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.entry.biz.dashboard.domain.pages.*
import com.entry.lib.log.MLog
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 顶层只有隐私和共享依赖，不吞并四页业务状态。 */
class DashboardCoordinatorViewModel(val repositories: RootPageRepositorySet) : ViewModel() {
    data class RenderSession(
        val tab: Int = 1,
        val authenticated: Boolean = false,
        val epoch: Long = -1,
    )

    private val rendered = MutableStateFlow(RenderSession())
    val renderSession = rendered.asStateFlow()
    private val visible = MutableStateFlow(false)
    val assetsVisible = visible.asStateFlow()
    private var epoch = -1L

    fun session(version: Long, authenticated: Boolean, tab: Int) {
        if (epoch != version || !authenticated) visible.value = false
        epoch = version
        rendered.value = RenderSession(tab, authenticated, version)
    }

    fun toggle() {
        if (!rendered.value.authenticated) return
        visible.value = !visible.value
        MLog.i("EntryTask.NativeDashboard", "privacy.toggled")
    }
}

class HomePageViewModel(private val repository: HomeRepository) : RootPageViewModel() {
    val content = resource<HomeContent>()
    val unread = resource<Int>()
    val products = resource<ProductPage>()
    private val selection = MutableStateFlow("all")
    val category = selection.asStateFlow()

    override fun resetSession() {
        selection.value = "all"
    }

    /** 切换分类立即清理旧结果；PageResource取消旧请求并阻止迟到响应回写。 */
    fun select(id: String) {
        if (selection.value == id) return
        selection.value = id
        products.clear()
        if (active) products.load { repository.products(id) }
        MLog.i("EntryTask.NativeDashboard", "home.category.changed")
    }

    override fun refresh() {
        if (active) {
            content.load { repository.home() }
            unread.load { repository.unread() }
            val id = selection.value
            products.load { repository.products(id) }
        }
    }
}

class WealthPageViewModel(private val repository: WealthRepository) : RootPageViewModel() {
    val content = resource<WealthContent>()
    val assets = resource<AssetOverview>()
    val products = resource<ProductPage>()
    private val selection = MutableStateFlow("fund")
    val category = selection.asStateFlow()

    override fun resetSession() {
        selection.value = "fund"
    }

    fun select(id: String) {
        if (selection.value == id) return
        selection.value = id
        products.clear()
        if (active) products.load { repository.products(id) }
    }

    override fun refresh() {
        if (active) {
            content.load { repository.wealth() }
            assets.load { repository.assets() }
            val id = selection.value
            products.load { repository.products(id) }
        }
    }
}

class MemberPageViewModel(private val repository: MemberRepository) : RootPageViewModel() {
    val content = resource<MemberContent>()
    val identity = resource<Identity>()
    val checkin = resource<CheckinStatus>()

    data class Operation(val submitting: Boolean = false, val message: String = "")

    private val operation = MutableStateFlow(Operation())
    val submission = operation.asStateFlow()
    private var writing: Job? = null
    private var intentDate = ""
    private var key = ""
    private var confirmationRequired = false

    override fun resetSession() {
        writing?.cancel()
        writing = null
        operation.value = Operation()
        intentDate = ""
        confirmationRequired = false
    }

    override fun onInactive() {
        if (writing?.isActive == true) confirmationRequired = true
        writing?.cancel()
        writing = null
        operation.value = Operation()
    }

    override fun refresh() {
        if (active) {
            content.load { repository.member() }
            identity.load { repository.identity(true) }
            checkin.load { repository.checkin() }
        }
    }

    fun submit() {
        val current = checkin.state.value.data ?: return
        if (!active || operation.value.submitting || current.checked) return
        if (intentDate != current.date) {
            intentDate = current.date
            key = UUID.randomUUID().toString()
        }
        val version = epoch
        operation.value = Operation(true, "正在签到")
        MLog.i("EntryTask.NativeDashboard", "checkin.submit.started flowId=$key")
        writing =
            viewModelScope.launch {
                try {
                    if (confirmationRequired) {
                        val confirmed = repository.checkin()
                        ensureActive()
                        if (version == epoch) {
                            checkin.replace(confirmed)
                            confirmationRequired = false
                            operation.value =
                                Operation(false, if (confirmed.checked) "已确认今日签到成功" else "暂未签到，可重试")
                        }
                        return@launch
                    }
                    val result = repository.submit(current.date, key)
                    ensureActive()
                    if (version == epoch) {
                        checkin.replace(result)
                        operation.value =
                            Operation(
                                false,
                                if (result.already) "今日已签到" else "签到成功，本次获得${result.awarded}能量值",
                            )
                        identity.load { repository.identity(true) }
                    }
                    MLog.i("EntryTask.NativeDashboard", "checkin.submit.completed flowId=$key")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    confirmationRequired = true
                    operation.value = Operation(true, "结果待确认，正在查询签到状态")
                    try {
                        val result = repository.checkin()
                        ensureActive()
                        if (version == epoch) {
                            checkin.replace(result)
                            confirmationRequired = false
                            operation.value =
                                Operation(false, if (result.checked) "已确认今日签到成功" else "暂未签到，可重试")
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failed: Exception) {
                        if (version == epoch) operation.value = Operation(false, "结果尚未确认，请先刷新签到状态")
                    }
                    MLog.e(
                        "EntryTask.NativeDashboard",
                        "checkin.submit.failed flowId=$key code=RESULT_CONFIRMATION",
                    )
                }
            }
    }
}

class ProfilePageViewModel(private val repository: ProfileRepository) : RootPageViewModel() {
    val identity = resource<Identity>()
    val assets = resource<AssetOverview>()
    val financial = resource<FinancialOverview>()

    override fun refresh() {
        if (active) {
            identity.load { repository.identity(false) }
            assets.load { repository.assets() }
            financial.load { repository.financial() }
        }
    }
}
