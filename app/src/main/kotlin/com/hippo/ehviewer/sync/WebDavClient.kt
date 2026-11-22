package com.hippo.ehviewer.sync

import android.util.Base64
import com.hippo.ehviewer.Settings
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 极简 WebDAV 客户端（PROPFIND / MKCOL / PUT / GET）。
 *
 * 上传使用固定长度流式写入，避免把整个画廊读进内存导致 OOM。
 */
object WebDavClient {
    class WebDavException(message: String) : Exception(message)

    data class RemoteEntry(
        val name: String,
        val url: String,
        val isDirectory: Boolean,
    )

    private val responseRegex = Regex("<[^>]*:response[\\s\\S]*?</[^>]*:response>", RegexOption.IGNORE_CASE)
    private val hrefRegex = Regex("<[^>]*:href[^>]*>([^<]+)</[^>]*:href>", RegexOption.IGNORE_CASE)
    private val collectionRegex = Regex("<[^>]*:collection\\b", RegexOption.IGNORE_CASE)

    fun isConfigured(): Boolean = Settings.webdavUrl.value.isNotBlank()

    fun encodeSegment(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun rootUrl(): String {
        val dir = Settings.webdavDir.value.trim().trim('/')
        val base = baseUrl()
        return if (dir.isEmpty()) base else base + "/" + dir.split('/').joinToString("/") { encodeSegment(it) }
    }

    private fun baseUrl(): String = Settings.webdavUrl.value.trim().trimEnd('/')

    private fun open(method: String, url: String, depth: String? = null): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        val user = Settings.webdavUser.value
        val password = Settings.webdavPassword.value
        if (user.isNotBlank() || password.isNotBlank()) {
            val raw = "$user:$password".toByteArray(Charsets.UTF_8)
            connection.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(raw, Base64.NO_WRAP))
        }
        depth?.let { connection.setRequestProperty("Depth", it) }
        return connection
    }

    fun testConnection() {
        // 只探测服务器根地址：远端目录可能尚未创建，不能因此判定连接失败
        val connection = open("PROPFIND", baseUrl(), "0")
        try {
            connection.setRequestProperty("Content-Type", "application/xml; charset=utf-8")
            connection.doOutput = true
            val body = """<?xml version="1.0" encoding="utf-8" ?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) throw WebDavException("HTTP $code")
        } finally {
            connection.disconnect()
        }
    }

    /** 列出目录下一级条目（Depth: 1），自动跳过目录自身。 */
    fun list(url: String): List<RemoteEntry> {
        val connection = open("PROPFIND", url, "1")
        val text = try {
            connection.setRequestProperty("Content-Type", "application/xml; charset=utf-8")
            connection.doOutput = true
            val body = """<?xml version="1.0" encoding="utf-8" ?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) throw WebDavException("PROPFIND HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
        val base = URL(url)
        val selfPath = runCatching { URLDecoder.decode(base.path, "UTF-8").trimEnd('/') }.getOrElse { "" }
        return responseRegex.findAll(text).mapNotNull { match ->
            val block = match.value
            val href = hrefRegex.find(block)?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
            // 服务端返回的 href 可能是绝对 URL，也可能只是以 / 开头的路径，需解析为绝对地址再请求
            val absolute = runCatching { URL(base, href).toString() }.getOrDefault(href)
            val path = runCatching {
                URLDecoder.decode(URL(absolute).path, "UTF-8").trimEnd('/')
            }.getOrNull() ?: return@mapNotNull null
            if (path.isBlank() || path == selfPath) return@mapNotNull null
            val name = path.substringAfterLast('/').takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val isDirectory = href.endsWith('/') || collectionRegex.containsMatchIn(block)
            RemoteEntry(name = name, url = absolute, isDirectory = isDirectory)
        }.toList()
    }

    /** 创建目录；已存在（405）视为成功。 */
    fun ensureDirectory(url: String) {
        val connection = open("MKCOL", url)
        try {
            val code = connection.responseCode
            if (code !in 200..299 && code != 405) {
                throw WebDavException("MKCOL HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    fun upload(file: File, remoteUrl: String) {
        val connection = open("PUT", remoteUrl)
        try {
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(file.length())
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.connect()
            file.inputStream().use { input ->
                connection.outputStream.use { output -> input.copyTo(output) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw WebDavException("PUT HTTP $code")
        } finally {
            connection.disconnect()
        }
    }

    fun download(remoteUrl: String, target: File) {
        val connection = open("GET", remoteUrl)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw WebDavException("GET HTTP $code")
            target.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }
}
