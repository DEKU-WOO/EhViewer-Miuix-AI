package com.hippo.ehviewer.sync

import android.os.ParcelFileDescriptor
import com.ehviewer.core.files.SystemFileSystem
import com.ehviewer.core.files.mkdirs
import com.ehviewer.core.files.openFileDescriptor
import com.hippo.ehviewer.Settings
import java.net.URL
import java.net.URLDecoder
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

    private const val HEX_DIGITS = "0123456789ABCDEF"

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

    /** 单文件下载单独放宽读取超时，兼容 fnOS 大文件分块传输（chunked）。 */
    private val downloadClient = client.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 当前正在执行的请求，用于「停止同步」时立即关闭网络流。 */
    @Volatile
    private var activeCall: okhttp3.Call? = null

    fun cancelActive() {
        runCatching { activeCall?.cancel() }
    }

    fun isConfigured(): Boolean = Settings.webdavUrl.value.isNotBlank()

    /**
     * RFC 3986 路径段编码：仅保留 unreserved 字符，中文、日文、方括号、空格等
     * 统一按 UTF-8 逐字节百分号编码，避免服务端收到非法字符返回 404/400。
     */
    fun encodeSegment(value: String): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            val unreserved = char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' ||
                char == '-' || char == '.' || char == '_' || char == '~'
            if (unreserved) {
                append(char)
            } else {
                append('%').append(HEX_DIGITS[code shr 4]).append(HEX_DIGITS[code and 0x0F])
            }
        }
    }

    /**
     * 解码 PROPFIND 返回 href 中的百分号编码。
     * 路径里的 '+' 代表字面加号，不能按表单规则解码成空格。
     */
    private fun decodePath(value: String): String =
        runCatching { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)

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
        val call = client.newCall(request)
        activeCall = call
        val response = try {
            call.execute()
        } finally {
            if (activeCall === call) activeCall = null
        }
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
        // 相对 href 必须基于「目录 URL + /」解析，否则会被当成同级文件替换掉最后一段
        val base = URL(if (url.endsWith('/')) url else "$url/")
        val origin = buildOrigin(base)
        val selfPath = decodePath(base.path).trimEnd('/')
        return responseRegex.findAll(text).mapNotNull { match ->
            val block = match.value
            val rawHref = hrefRegex.find(block)?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
            // href 可能是绝对 URL、绝对路径或相对路径；取出 path 后统一解码，再逐段重新编码
            val rawPath = runCatching { URL(base, rawHref).path }.getOrNull() ?: return@mapNotNull null
            val path = decodePath(rawPath).trimEnd('/')
            if (path.isBlank() || path == selfPath) return@mapNotNull null
            val name = path.substringAfterLast('/').takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val isDirectory = rawHref.endsWith('/') || collectionRegex.containsMatchIn(block)
            RemoteEntry(name = name, url = buildUrl(origin, path), isDirectory = isDirectory)
        }.toList()
    }

    private fun buildOrigin(url: URL): String = buildString {
        append(url.protocol).append("://").append(url.host)
        if (url.port != -1) append(':').append(url.port)
    }

    /** 用解码后的真实名称逐段编码重建绝对 URL，避免服务端 href 原样携带 UTF-8 或双重编码。 */
    private fun buildUrl(origin: String, path: String): String {
        val normalized = if (path.startsWith('/')) path else "/$path"
        return origin + normalized.split('/').joinToString("/") { encodeSegment(it) }
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

    /**
     * 下载到 target，并校验实际写入字节数与服务端 Content-Length 一致。
     *
     * 只接受 200/206：重定向由 OkHttp 自动跟随，其余状态码（401/403/404 等）
     * 直接抛出带状态码与 URL 的异常，禁止把空响应体当成 0 字节文件落盘。
     */
    fun download(remoteUrl: String, target: Path): Long {
        val call = downloadClient.newCall(newRequest("GET", remoteUrl))
        activeCall = call
        try {
            call.execute().use { response ->
                if (response.code != 200 && response.code != 206) {
                    val detail = runCatching { response.body.string().take(256) }.getOrNull()
                    throw WebDavException(
                        "GET HTTP ${response.code}: $remoteUrl" + detail?.let { " - $it" }.orEmpty(),
                    )
                }
                target.parent?.mkdirs()
                val expected = response.body.contentLength()
                val written = response.body.byteStream().use { input ->
                    ParcelFileDescriptor.AutoCloseOutputStream(target.openFileDescriptor("wt")).use { output ->
                        input.copyTo(output, bufferSize = 32 * 1024).also { output.flush() }
                    }
                }
                if (written <= 0L) {
                    throw WebDavException("下载内容为空（HTTP ${response.code}）：$remoteUrl")
                }
                if (expected >= 0 && written != expected) {
                    throw WebDavException("下载不完整：$remoteUrl expected=$expected actual=$written")
                }
                return written
            }
        } finally {
            if (activeCall === call) activeCall = null
        }
    }
}
