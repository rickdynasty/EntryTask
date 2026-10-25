package com.entry.lib.stroage.room

import androidx.room.Database
import androidx.room.RoomDatabase

/** 首个数据库版本；后续变更必须升版本并提供Migration，禁止破坏式回退。 */
@Database(entities = [CredentialEntity::class], version = 1, exportSchema = true)
internal abstract class StorageDatabase : RoomDatabase() {
    abstract fun credentials(): CredentialDao
}
