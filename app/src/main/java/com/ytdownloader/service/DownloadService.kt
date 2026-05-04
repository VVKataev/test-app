package com.ytdownloader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ytdownloader.R
import com.ytdownloader.ui.MainActivity
import com.ytdownloader.util.YoutubeExtractor
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern

class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "yt_downloader_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_URL = "extra_url"
        const val EXTRA_QUALITY = "extra_quality"
        const val ACTION_STOP = "action_stop"
        
        private var downloadJob: Job? = null
        
        fun startDownload(context: Context, url: String, quality: Int) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_QUALITY, quality)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopDownload(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Инициализация NewPipe Extractor
        YoutubeExtractor.init()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                downloadJob?.cancel()
                stopForeground(true)
                stopSelf()
            }
            else -> {
                val url = intent?.getStringExtra(EXTRA_URL)
                val quality = intent?.getIntExtra(EXTRA_QUALITY, 720) ?: 720
                
                if (!url.isNullOrEmpty()) {
                    startForeground(NOTIFICATION_ID, createNotification(0, false))
                    downloadJob = serviceScope.launch {
                        try {
                            downloadVideo(url, quality)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            updateNotification(-1, true, e.message ?: "Ошибка загрузки")
                        }
                    }
                } else {
                    stopSelf()
                }
            }
        }
        
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        downloadJob?.cancel()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(progress: Int, isIndeterminate: Boolean): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YT Downloader")
            .setContentText(if (progress >= 0) "Загрузка: $progress%" else "Обработка...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (isIndeterminate) {
            builder.setProgress(0, 0, true)
        } else if (progress >= 0) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setContentText("Ошибка загрузки")
            builder.setSmallIcon(android.R.drawable.stat_notify_error)
            builder.setOngoing(false)
        }

        return builder.build()
    }

    private fun updateNotification(progress: Int, isError: Boolean, errorMessage: String? = null) {
        val notification = if (isError) {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("YT Downloader")
                .setContentText(errorMessage ?: "Ошибка")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .build()
        } else if (progress == 100) {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("YT Downloader")
                .setContentText("Загрузка завершена!")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .setOngoing(false)
                .setProgress(100, 100, false)
                .build()
        } else {
            createNotification(progress, false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private suspend fun downloadVideo(url: String, quality: Int) {
        val videoId = extractVideoId(url) ?: throw IllegalArgumentException("Неверная ссылка YouTube")
        
        // Получение информации о видео через NewPipe Extractor
        updateNotification(5, false)
        
        try {
            // Получаем URL видео с нужным качеством используя NewPipe Extractor
            val (videoUrl, actualQuality) = YoutubeExtractor.getVideoUrlByQuality(url, quality)
            
            updateNotification(15, false)
            
            // Загрузка файла
            downloadFile(videoUrl, videoId, actualQuality)
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Ошибка получения видео: ${e.message}")
        }
    }

    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            "(?:youtube\\.com/watch\\?v=|youtu\\.be/)([a-zA-Z0-9_-]{11})",
            "youtube\\.com/embed/([a-zA-Z0-9_-]{11})",
            "youtube\\.com/v/([a-zA-Z0-9_-]{11})"
        )
        
        for (pattern in patterns) {
            val matcher = Pattern.compile(pattern).matcher(url)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        
        return null
    }

    private suspend fun downloadFile(url: String, videoId: String, quality: Int) {
        val downloadDir = getExternalFilesDir("Downloads") ?: filesDir
        val fileName = "video_${videoId}_${quality}p.mp4"
        val file = File(downloadDir, fileName)
        
        try {
            val request = Request.Builder().url(url).build()
            
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    // Обработка ошибки будет в updateNotification
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val body = response.body ?: return
                    
                    val totalBytes = body.contentLength()
                    var downloadedBytes = 0L
                    
                    FileOutputStream(file).use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                                
                                if (totalBytes > 0) {
                                    val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                                    runCatching {
                                        updateNotification(progress.coerceIn(20, 95), false)
                                    }
                                }
                                
                                if (downloadJob?.isCancelled == true) {
                                    file.delete()
                                    return
                                }
                            }
                        }
                    }
                    
                    runCatching {
                        updateNotification(100, false)
                        stopForeground(true)
                        stopSelf()
                    }
                }
            })
            
            // Ждем завершения загрузки
            while (!file.exists() || file.length() == 0L) {
                delay(100)
                if (downloadJob?.isCancelled == true) {
                    file.delete()
                    throw CancellationException()
                }
            }
            
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }
}
