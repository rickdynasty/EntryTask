package com.entry.biz.user.domain

/** 认证成功只暴露业务身份；凭证由Native会话持有，不进入表单状态。 */
data class AuthenticatedUser(val id: Long, val username: String)

/** VM依赖领域契约，网络协议与会话提交由数据层实现。 */
interface AuthRepository {
    suspend fun login(username: String, password: String): AuthenticatedUser
    suspend fun register(username: String, password: String): AuthenticatedUser
}

/** 已映射的安全业务提示，不携带服务端响应或凭证。 */
class AuthRejectedException(val userMessage: String) : Exception()
