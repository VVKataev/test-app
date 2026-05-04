package com.ytdownloader.util

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.ConcurrentHashMap

/**
 * Утилитный класс для работы с NewPipe Extractor
 * Используется для получения информации о видео и URL для скачивания
 */
object YoutubeExtractor {

    private val streamCache = ConcurrentHashMap<String, StreamInfo>()

    /**
     * Инициализация NewPipe Extractor
     * Должна быть вызвана перед использованием других методов
     */
    fun init() {
        // Инициализируем NewPipe с дефолтными настройками
        NewPipe.init(
            DownloaderImpl.getInstance(),
            Localization.DEFAULT,
            ContentCountry.DEFAULT
        )
    }

    /**
     * Получение информации о видео по URL
     * @param url ссылка на YouTube видео
     * @return StreamInfo с информацией о видео
     */
    suspend fun getStreamInfo(url: String): StreamInfo {
        return try {
            val service = NewPipe.getService(ServiceList.YouTube.serviceId)
            val info = StreamInfo.getInfo(service, url)
            
            // Кэшируем информацию для последующего использования
            val videoId = extractVideoId(url) ?: url
            streamCache[videoId] = info
            
            info
        } catch (e: Exception) {
            throw Exception("Ошибка получения информации о видео: ${e.message}", e)
        }
    }

    /**
     * Получение списка доступных видео потоков
     * @param url ссылка на YouTube видео
     * @return список VideoStream с доступными качествами
     */
    suspend fun getVideoStreams(url: String): List<VideoStream> {
        return try {
            val info = getStreamInfo(url)
            info.videoStreams
        } catch (e: Exception) {
            throw Exception("Ошибка получения списка видео потоков: ${e.message}", e)
        }
    }

    /**
     * Поиск URL видео с нужным качеством
     * @param url ссылка на YouTube видео
     * @param targetQuality желаемое качество (например, 720, 1080, 480)
     * @return Pair с URL видео и фактическим качеством
     */
    suspend fun getVideoUrlByQuality(url: String, targetQuality: Int): Pair<String, Int> {
        return try {
            val streams = getVideoStreams(url)
            
            if (streams.isEmpty()) {
                throw Exception("Не найдено доступных видео потоков")
            }

            // Сортируем потоки по качеству (от большего к меньшему)
            val sortedStreams = streams.sortedByDescending { 
                getResolutionHeight(it.resolution) 
            }

            // Ищем поток с качеством <= целевому
            val suitableStream = sortedStreams.firstOrNull { 
                getResolutionHeight(it.resolution) <= targetQuality 
            } ?: sortedStreams.last() // Если не нашли, берем наименьшее качество

            Pair(suitableStream.content, getResolutionHeight(suitableStream.resolution))
        } catch (e: Exception) {
            throw Exception("Ошибка поиска видео с нужным качеством: ${e.message}", e)
        }
    }

    /**
     * Получение названия видео
     * @param url ссылка на YouTube видео
     * @return название видео
     */
    suspend fun getVideoTitle(url: String): String {
        return try {
            val info = getStreamInfo(url)
            info.name ?: "Unknown Video"
        } catch (e: Exception) {
            "Unknown Video"
        }
    }

    /**
     * Получение длительности видео в секундах
     * @param url ссылка на YouTube видео
     * @return длительность в секундах
     */
    suspend fun getVideoDuration(url: String): Long {
        return try {
            val info = getStreamInfo(url)
            info.length
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Извлечение высоты разрешения из строки (например, "720p" -> 720)
     */
    private fun getResolutionHeight(resolution: String): Int {
        return try {
            resolution.replace("p", "", ignoreCase = true)
                .replace("P", "")
                .toIntOrNull() ?: 360
        } catch (e: Exception) {
            360
        }
    }

    /**
     * Извлечение ID видео из URL
     */
    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            "(?:youtube\\.com/watch\\?v=|youtu\\.be/)([a-zA-Z0-9_-]{11})",
            "youtube\\.com/embed/([a-zA-Z0-9_-]{11})",
            "youtube\\.com/v/([a-zA-Z0-9_-]{11})"
        )

        for (pattern in patterns) {
            val regex = Regex(pattern)
            val matchResult = regex.find(url)
            if (matchResult != null && matchResult.groupValues.size > 1) {
                return matchResult.groupValues[1]
            }
        }

        return null
    }

    /**
     * Очистка кэша
     */
    fun clearCache() {
        streamCache.clear()
    }
}
