package com.entry.lib.stroage.room

/** 技术凭据存储端口：只保存不可逆索引和校验值，不接收密码/Token，也不决定认证结果。 */
interface CredentialStore {
    /** 同步接口用于OkHttp拦截器，调用方必须在后台线程执行。 */
    fun read(accountKey: String): String?

    /** 整批事务提交；已有主键优先，迁移重试不能覆盖已存在的校验值。返回本次新增主键；失败抛出异常。 */
    fun insertIfAbsent(records: Map<String, String>): Set<String>
}
