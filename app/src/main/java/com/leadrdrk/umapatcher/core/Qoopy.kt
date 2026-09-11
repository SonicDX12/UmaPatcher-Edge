package com.leadrdrk.umapatcher.core

import com.leadrdrk.umapatcher.utils.fetchText
import java.net.URL

data class QoopyAppCompany(
    val id: Long,
    val name: String
)

data class QoopyAppApk(
    val updated_at: String? = null,
    val version_name: String? = null,
    val version_code: Long? = null,
    val file_size: String? = null,
    val base_apk_md5: String? = null
)

data class QoopySplitApk(
    val url: String,
    val md5: String,
    val size: Long
)

data class QoopyApp(
    val id: Long,
    val package_id: String? = null,
    val app_name: String? = null,
    val display_name: String? = null,
    val icon_url: String? = null,
    val company: QoopyAppCompany? = null,
    val is_apk_ready: Boolean = false,
    val apk: QoopyAppApk? = null,
    val split_apks: List<QoopySplitApk>? = null,
    val download: String? = null
) {
    val isDownloadable: Boolean
        get() = is_apk_ready && !download.isNullOrEmpty()
}

data class QoopyApiResponse(
    val code: Int,
    val message: String? = null,
    val data: QoopyApp? = null
)

object Qoopy {
    // Umamusume Pretty Derby (Japanese)
    const val UMAMUSUME_JP_APP_ID = "6172"
    const val BASE_URL = "https://qoopy.leadrdrk.com/"
    const val API_PATH_MARKER = "/api/v2/app"

    // QooApp's own public app page isn't behind Cloudflare (unlike Qoopy's API), and embeds
    // the current version in a schema.org JSON-LD block. Used for a cheap update check that
    // doesn't require the Qoopy WebView/Turnstile flow at all.
    private val appPageUrl = URL("https://apps.qoo-app.com/en/app/$UMAMUSUME_JP_APP_ID")
    private val softwareVersionRegex = Regex(""""softwareVersion"\s*:\s*"([^"]+)"""")

    fun fetchLatestVersion(): String? {
        val html = fetchText(appPageUrl, "text/html")
        return softwareVersionRegex.find(html)?.groupValues?.get(1)
    }
}
