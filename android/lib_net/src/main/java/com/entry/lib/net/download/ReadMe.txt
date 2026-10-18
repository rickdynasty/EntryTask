使用案例：

val request = DownloadRequest(
    url = "https://example.com/large_file.apk",
    targetPath = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath + "/update.apk",
    tag = "update_apk"
)

// 传入 NetManager 共享的 OkHttpClient
val task = DownloadManager.enqueue(request, NetManager.getOkHttpClient())

// 监听状态流
viewModelScope.launch {
    task.statusFlow.collect { status ->
        when (status) {
            is DownloadStatus.Downloading -> {
                // 更新 UI 进度条：status.progress
            }
            is DownloadStatus.Success -> {
                // 下载完成，status.filePath
            }
            is DownloadStatus.Failed -> {
                // 处理错误：status.throwable
            }
            else -> {}
        }
    }
}