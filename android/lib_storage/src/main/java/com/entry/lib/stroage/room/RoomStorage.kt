package com.entry.lib.stroage.room

import android.content.Context
import androidx.room.Room
import java.io.Closeable

/** 封装Room构建、DAO及事务。进程实例由Application持有，页面不得各建一个数据库。 */
class RoomStorage private constructor(private val database: StorageDatabase) : Closeable {
    val credentials: CredentialStore =
        object : CredentialStore {
            override fun read(accountKey: String): String? = database.credentials().read(accountKey)

            override fun insertIfAbsent(records: Map<String, String>): Set<String> {
                require(records.all { (key, value) -> key.isNotBlank() && value.isNotBlank() })
                // 空迁移也走事务检查，不允许主线程绕过Room的线程约束。
                val entries = records.map { (key, value) -> CredentialEntity(key, value) }
                return database.runInTransaction(
                    java.util.concurrent.Callable {
                        val ids = database.credentials().insertIfAbsent(entries)
                        entries.indices
                            .filter { ids[it] != -1L }
                            .map { entries[it].accountKey }
                            .toSet()
                    }
                )
            }
        }

    override fun close() {
        database.close()
    }

    companion object {
        const val DATABASE_NAME = "entrytask_auth.db"

        /** 文件名参数用于独立业务实例/隔离测试，不允许外部路径；不开放给RN Bridge。 */
        fun open(context: Context, name: String = DATABASE_NAME): RoomStorage {
            require(name.matches(Regex("[A-Za-z0-9_-]+\\.db")))
            return RoomStorage(
                Room.databaseBuilder(context.applicationContext, StorageDatabase::class.java, name)
                    .build()
            )
        }
    }
}
