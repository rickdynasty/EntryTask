package com.entry.lib.net.download

/**
 * 下载状态契约
 */
sealed interface DownloadStatus {
    data object Idle : DownloadStatus                                    // 空闲/等待中
    data class Downloading(val progress: Int, val downloadedBytes: Long, val totalBytes: Long) : DownloadStatus // 下载中 (进度0-100)
    data class Success(val filePath: String) : DownloadStatus            // 下载成功
    data class Paused(val downloadedBytes: Long) : DownloadStatus        // 已暂停
    data class Failed(val throwable: Throwable) : DownloadStatus         // 下载失败
}