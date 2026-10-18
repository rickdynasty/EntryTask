package com.entry.lib.net.download

/**
 * 下载任务请求参数
 */
data class DownloadRequest(
    val url: String,            // 下载链接
    val targetPath: String,     // 目标文件完整保存路径
    val tag: String = url       // 任务唯一标识（默认用 URL）
)