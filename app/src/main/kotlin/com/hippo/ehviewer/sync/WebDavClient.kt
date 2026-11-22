package com.hippo.ehviewer.sync

import android.os.ParcelFileDescriptor
import com.ehviewer.core.files.SystemFileSystem
import com.ehviewer.core.files.mkdirs
import com.ehviewer.core.files.openFileDescriptor
import com.hippo.ehviewer.Settings
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.Path

/**
 * 极简 WebDAV 客户端（PROPFIND / MKCOL / PUT / GET）。
 *
 * 直接使用 OkHttp Request，绕开 HttpURLConnection 对扩展 HTTP 方法的白名单限制。
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
    private val xmlMediaType = "application/xml; charset=utf-8".toMediaType()
    private val binaryMediaType = "application/octet-stream".toMediaType()
    private val propfindBody = """<?xml version="1.0" encoding="utf-8" ?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun isConfigured(): Boolean = Settings.webdavUrl.value.isNotBlank()

    fun encodeSegment(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun rootUrl(): String {
        val segments = Settings.webdavDir.value.trim().trim('/').split('/').filter { it.isNotEmpty() }
        return segments.fold(baseUrl()) { url, segment -> "$url/${encodeSegment(segment)}" }
    }

    private fun baseUrl(): String = Settings.webdavUrl.value.trim().trimEnd('/')

    private fun newRequest(method: String, url: String, depth: String? = null, body: RequestBody? = null): Request {
        return Request.Builder()
            .url(url)
            .method(method, body)
            .apply {
                val user = Settings.webdavUser.value
                val password = Settings.webdavPassword.value
                if (user.isNotBlank() || password.isNotBlank()) {
                    header("Authorization", Credentials.basic(user, password, Charsets.UTF_8))
                }
                depth?.let { header("Depth", it) }
            }
            .build()
    }

    private fun execute(request: Request, method: String, vararg acceptedCodes: Int): Response {
        val response = client.newCall(request).execute()
        // 207 Multi-Status 与其他 2xx 一样均为成功；部分服务器的已存在目录返回 405。
        if (!response.isSuccessful && response.code !in acceptedCodes) {
            val detail = runCatching { response.body.string().take(512) }.getOrNull()
            response.close()
            throw WebDavException("$method HTTP ${response.code}" + detail?.let { ": $it" }.orEmpty())
        }
        return response
    }

    fun testConnection() {
        execute(newRequest("PROPFIND", baseUrl(), "0", propfindBody.toRequestBody(xmlMediaType)), "PROPFIND").close()
        ensureDirectory(rootUrl())
    }

    /** 列出目录下一级条目（Depth: 1），自动跳过目录自身。 */
    fun list(url: String): List<RemoteEntry> {
        val text = execute(newRequest("PROPFIND", url, "1", propfindBody.toRequestBody(xmlMediaType)), "PROPFIND").use { response ->
            response.body.string()
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

    /** 从 WebDAV 根地址开始逐级创建目录；已存在（405）视为成功。 */
    fun ensureDirectory(url: String) {
        val base = baseUrl()
        val target = url.trimEnd('/')
        if (target != base && !target.startsWith("$base/")) {
            throw WebDavException("目录不在 WebDAV 根地址下: $url")
        }
        val segments = target.removePrefix(base).trim('/').split('/').filter { it.isNotEmpty() }
        segments.fold(base) { parent, segment ->
            "$parent/$segment".also { current ->
                execute(newRequest("MKCOL", current), "MKCOL", 405).close()
            }
        }
    }

    fun upload(file: Path, remoteUrl: String) {
        val body = object : RequestBody() {
            override fun contentType() = binaryMediaType

            override fun contentLength(): Long = file.openFileDescriptor("r").use { it.statSize }

            override fun writeTo(sink: BufferedSink) {
                SystemFileSystem.source(file).use { source -> sink.writeAll(source) }
            }
        }
        execute(newRequest("PUT", remoteUrl, body = body), "PUT").close()
    }

    fun download(remoteUrl: String, target: Path) {
        execute(newRequest("GET", remoteUrl), "GET").use { response ->
            target.parent?.mkdirs()
            response.body.byteStream().use { input ->
                ParcelFileDescriptor.AutoCloseOutputStream(target.openFileDescriptor("wt")).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
        }
    }
}
