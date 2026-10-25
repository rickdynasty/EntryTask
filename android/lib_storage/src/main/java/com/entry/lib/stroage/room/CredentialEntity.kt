package com.entry.lib.stroage.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 不保存用户名或明文密码；主键同时承担唯一索引，禁止重复账号覆盖。 */
@Entity(tableName = "auth_credentials")
internal data class CredentialEntity(
    @PrimaryKey @ColumnInfo(name = "account_key") val accountKey: String,
    @ColumnInfo(name = "password_verifier") val passwordVerifier: String,
)
