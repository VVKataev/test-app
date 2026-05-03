package com.ytdownloader.model

data class VideoInfo(
    val title: String,
    val duration: String,
    val thumbnailUrl: String,
    val videoId: String
)

data class VideoQuality(
    val quality: Int,
    val label: String,
    val url: String,
    val format: String = "mp4"
)

sealed class DownloadStatus {
    object Waiting : DownloadStatus()
    data class Downloading(val progress: Int) : DownloadStatus()
    data class Completed(val filePath: String) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}
