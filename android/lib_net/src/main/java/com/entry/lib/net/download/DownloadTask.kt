package com.entry.lib.net.download

import com.entry.lib.log.MLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile

/**
 * 单个下载任务执行引擎
 * 通过 Range 请求头实现断点续传，通过 RandomAccessFile 定位追加写入，兼顾内存与性能
 */
class DownloadTask(
    private val request: DownloadRequest,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "Rick_tan:DownloadTask"
    }

    private val _statusFlow = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val statusFlow: StateFlow<DownloadStatus> = _statusFlow.asStateFlow()

    @Volatile
    private var isCancelled = false

    /**
     * 开始或恢复下载
     */
    suspend fun start() = withContext(Dispatchers.IO) {
        val file = File(request.targetPath)
        // 检查本地已有文件大小，用于断点续传
        val localFileLength = if (file.exists()) file.length() else 0L

        MLog.d(TAG, "准备下载: ${request.url}, 本地已下载字节数: $localFileLength")

        try {
            val httpRequest = Request.Builder()
                .url(request.url)
                // 核心：添加 Range 请求头实现断点续传
                .header("Range", "bytes=$localFileLength-")
                .build()

            val response = okHttpClient.newCall(httpRequest).execute()

            // 处理服务器响应码：200（全新下载） 或 206（断点续传成功）
            if (!response.isSuccessful && response.code != 206) {
                throw IllegalStateException("下载服务器响应异常: ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("响应体为空")
            val contentLength = body.contentLength()

            // 如果是 200 全新下载，总大小直接用 content length；如果是 206，总大小需要加上本地已下载大小
            val totalBytes = if (response.code == 206) {
                contentLength + localFileLength
            } else {
                // 如果服务器不支持 Range 强行返回 200，则重置本地文件
                if (localFileLength > 0) file.delete()
                contentLength
            }

            var downloadedBytes = localFileLength

            // 使用 RandomAccessFile 进行随机写入（支持断点追加）
            RandomAccessFile(file, "rw").use { raf ->
                if (response.code == 206) {
                    raf.seek(localFileLength)
                } else {
                    raf.setLength(0) // 清空重写
                }

                body.byteStream().use { inputStream ->
                    val buffer = kotlin.ByteArray(8 * 1024)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        // 协程取消检查
                        coroutineContext.ensureActive()
                        if (isCancelled) {
                            MLog.d(TAG, "下载任务被主动取消: ${request.url}")
                            _statusFlow.value = DownloadStatus.Paused(downloadedBytes)
                            return@withContext
                        }

                        raf.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        // 计算并发布进度 (0 ~ 100)
                        val progress = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt()
                        } else 0

                        _statusFlow.value =
                            DownloadStatus.Downloading(progress, downloadedBytes, totalBytes)
                    }
                }
            }

            MLog.d(TAG, "下载成功完成: ${request.targetPath}")
            _statusFlow.value = DownloadStatus.Success(request.targetPath)

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                MLog.d(TAG, "任务被协程取消")
                _statusFlow.value = DownloadStatus.Paused(file.length())
            } else {
                MLog.e(TAG, "下载异常失败: ${e.message}", e)
                _statusFlow.value = DownloadStatus.Failed(e)
            }
        }
    }

    fun cancel() {
        isCancelled = true
    }
}