package com.hippo.ehviewer.coil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.paging.compose.LazyPagingItems
import coil3.decode.BlackholeDecoder
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.SizeResolver
import com.ehviewer.core.database.model.DownloadInfo
import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.model.GalleryPreview
import com.ehviewer.core.model.V2GalleryPreview
import com.hippo.ehviewer.client.getThumbKey
import com.hippo.ehviewer.client.getV2PreviewKey
import com.hippo.ehviewer.client.thumbUrl
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.ktbuilder.execute
import kotlin.math.roundToInt
import splitties.init.appCtx

private val listThumbMaxDimensionPx: Int by lazy {
    (200f * appCtx.resources.displayMetrics.density).roundToInt().coerceIn(480, 600)
}

// 列表小卡片缩略图限制解码长边上限（480px~600px + INEXACT 不放大），防止本地超大封面原图撑爆显存；
// 详情页头部封面传入 constrainSize = false 严格保留 SizeResolver.ORIGINAL 原始分辨率，且共用同一 memoryCacheKey。
fun ImageRequest.Builder.ehUrl(info: GalleryInfo, constrainSize: Boolean = true) = apply {
    val key = info.thumbKey!!
    data(info.thumbUrl)
    if (constrainSize) {
        size(listThumbMaxDimensionPx, listThumbMaxDimensionPx)
        precision(Precision.INEXACT)
    } else {
        size(SizeResolver.ORIGINAL)
    }
    val downloadInfo = (info as? DownloadInfo) ?: DownloadManager.getDownloadInfo(info.gid)
    if (downloadInfo != null) {
        downloadInfo(downloadInfo)
    }
    memoryCacheKey(key)
    diskCacheKey(key)
}

// Load in original size so the memory cache can be reused for preload requests
fun ImageRequest.Builder.ehPreview(preview: GalleryPreview) = apply {
    with(preview) {
        val key = if (this is V2GalleryPreview) getV2PreviewKey(url) else getThumbKey(url)
        data(url)
        size(SizeResolver.ORIGINAL)
        memoryCacheKey(key)
        diskCacheKey(key)
    }
}

fun ImageRequest.Builder.justDownload() = apply {
    memoryCachePolicy(CachePolicy.DISABLED)
    decoderFactory(BlackholeDecoder.Factory())
}

@Composable
inline fun <T : Any> PrefetchAround(data: LazyPagingItems<T>, index: Int, distance: Int, crossinline f: (T) -> ImageRequest) {
    data.peek((index - distance).coerceAtLeast(0))?.let { fetchBefore ->
        LaunchedEffect(fetchBefore) {
            f(fetchBefore).execute()
        }
    }
    data.peek((index + distance).coerceAtMost(data.itemCount - 1))?.let { fetchAhead ->
        LaunchedEffect(fetchAhead) {
            f(fetchAhead).execute()
        }
    }
}
