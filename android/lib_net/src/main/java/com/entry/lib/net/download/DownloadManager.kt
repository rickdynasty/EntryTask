package com.entry.lib.net.download

import com.entry.lib.log.MLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

object DownloadManager {
    private const val TAG = "Rick_tan:DownloadManager"

    // 协程作用域，使用 SupervisorJob 防止单个任务崩溃影响全局
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 限制最大并发下载任务数为 3
    private val semaphore = Semaphore(3)

    // 正在运行或排队的任务集合
    private val activeTasks = ConcurrentHashMap<String, DownloadTask>()

    /**
     * 提交一个下载任务
     */
    fun enqueue(request: DownloadRequest, okHttpClient: OkHttpClient): DownloadTask {
        if (activeTasks.containsKey(request.tag)) {
            MLog.d(TAG, "任务已在队列中，直接返回现有任务: ${request.tag}")
            return activeTasks[request.tag]!!
        }

        val task = DownloadTask(request, okHttpClient)
        activeTasks[request.tag] = task

        downloadScope.launch {
            // 获取信号量，控制多任务并发上限
            semaphore.acquire()
            try {
                MLog.d(TAG, "开始执行并发下载任务: ${request.tag}")
                task.start()
            } finally {
                semaphore.release()
                activeTasks.remove(request.tag)
            }
        }

        return task
    }

    /**
     * 取消指定任务
     */
    fun cancel(tag: String) {
        activeTasks[tag]?.let { task ->
            task.cancel()
            activeTasks.remove(tag)
            MLog.d(TAG, "已取消下载任务: $tag")
        }
    }

    /**
     * 取消所有下载
     */
    fun cancelAll() {
        activeTasks.values.forEach { it.cancel() }
        activeTasks.clear()
        MLog.d(TAG, "已清空并取消所有下载任务")
    }
}