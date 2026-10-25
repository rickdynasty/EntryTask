package com.entry.lib.stroage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** DAO只负责SQL；加盐派生、旧SP迁移和认证语义均不进入基础存储层。 */
@Dao
internal interface CredentialDao {
    @Query("SELECT password_verifier FROM auth_credentials WHERE account_key = :key LIMIT 1")
    fun read(key: String): String?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIfAbsent(records: List<CredentialEntity>): List<Long>
}
