package com.ytdownloader.util

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Реализация Downloader для NewPipe Extractor
 * Использует OkHttp для выполнения сетевых запросов
 */
class DownloaderImpl private constructor() : Downloader() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        private var instance: DownloaderImpl? = null

        @JvmStatic
        fun getInstance(): DownloaderImpl {
            if (instance == null) {
                instance = DownloaderImpl()
            }
            return instance!!
        }
    }

    override fun execute(request: NewPipeRequest): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        // Создаем запрос OkHttp
        val requestBuilder = Request.Builder()
            .url(url)
            .method(httpMethod, convertRequestBody(dataToSend))

        // Добавляем заголовки
        for ((headerName, headerValueList) in headers) {
            if (headerValueList.isNotEmpty()) {
                requestBuilder.header(headerName, headerValueList[0])
            }
        }

        // Добавляем стандартные заголовки если их нет
        if (!headers.containsKey("User-Agent")) {
            requestBuilder.header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
        }

        val okHttpRequest = requestBuilder.build()

        try {
            val response = client.newCall(okHttpRequest).execute()

            // Проверяем на ReCaptcha
            if (response.code == 429) {
                response.close()
                throw ReCaptchaException("Rate limit exceeded (HTTP 429)", url)
            }

            if (response.code >= 400) {
                response.close()
                throw IOException("HTTP error $response.code for URL: $url")
            }

            return response
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Error executing request: ${e.message}", e)
        }
    }

    /**
     * Конвертация данных из NewPipe в RequestBody для OkHttp
     */
    private fun convertRequestBody(data: ByteArray?): RequestBody? {
        return if (data != null && data.isNotEmpty()) {
            RequestBody.create(
                okhttp3.MediaType.parse("application/x-www-form-urlencoded"),
                data
            )
        } else {
            null
        }
    }

    override fun executeHead(request: NewPipeRequest): Response {
        val httpMethod = "HEAD"
        val url = request.url()
        val headers = request.headers()

        val requestBuilder = Request.Builder()
            .url(url)
            .head()

        for ((headerName, headerValueList) in headers) {
            if (headerValueList.isNotEmpty()) {
                requestBuilder.header(headerName, headerValueList[0])
            }
        }

        if (!headers.containsKey("User-Agent")) {
            requestBuilder.header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
        }

        val okHttpRequest = requestBuilder.build()

        return client.newCall(okHttpRequest).execute()
    }
}
