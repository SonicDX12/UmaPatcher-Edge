package com.leadrdrk.umapatcher.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.leadrdrk.umapatcher.R
import com.leadrdrk.umapatcher.core.Qoopy
import com.leadrdrk.umapatcher.core.QoopyApiResponse
import com.leadrdrk.umapatcher.core.QoopyApp
import com.leadrdrk.umapatcher.core.QoopyCache
import com.leadrdrk.umapatcher.patcher.LegacyInstaller
import com.leadrdrk.umapatcher.ui.component.BackButton
import com.leadrdrk.umapatcher.ui.component.ScreenHeader
import com.leadrdrk.umapatcher.ui.component.SectionCard
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.leadrdrk.umapatcher.ui.patcher.QoopyDownloadResult
import com.leadrdrk.umapatcher.utils.bytesToHex
import com.leadrdrk.umapatcher.utils.downloadFileAndDigest
import com.leadrdrk.umapatcher.utils.workDir
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URL

/**
 * Bridges the QooApp/Qoopy webpage running inside the WebView back to Compose:
 * the injected script hooks window.fetch so the API JSON never has to be
 * re-requested (and re-challenged by Cloudflare) natively.
 */
private class QoopyJsBridge(private val onData: (String) -> Unit) {
    @JavascriptInterface
    fun onAppInfo(json: String) {
        onData(json)
    }
}

private fun buildInjectionScript(appId: String): String = """
(function() {
    if (window.__qoopyHooked) return;
    window.__qoopyHooked = true;

    var origFetch = window.fetch;
    window.fetch = function(input, init) {
        var url = (typeof input === 'string') ? input : (input && input.url);
        var p = origFetch(input, init);
        try {
            if (url && url.indexOf('${Qoopy.API_PATH_MARKER}') !== -1) {
                p.then(function(resp) {
                    var ct = resp.headers.get('content-type') || '';
                    if (ct.indexOf('application/json') === -1) return;
                    resp.clone().text().then(function(text) {
                        if (window.AndroidQoopyBridge) window.AndroidQoopyBridge.onAppInfo(text);
                    }).catch(function() {});
                }).catch(function() {});
            }
        } catch (e) {}
        return p;
    };

    var attempts = 0;
    var iv = setInterval(function() {
        attempts++;
        var box = document.getElementById('appDetailsBox');
        var done = box && box.classList.contains('show');
        if (done || attempts > 40) {
            clearInterval(iv);
            return;
        }
        var input = document.getElementById('mainInput');
        var btn = document.getElementById('submitButton');
        if (input && btn && !btn.classList.contains('hide')) {
            input.value = '$appId';
            btn.click();
        }
    }, 500);
})();
"""

// Cloudflare Turnstile needs a real, visible, JS-capable browser context to solve its
// challenge, so we load the actual Qoopy site instead of calling its API directly.
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun QoopySearchWebView(
    onAppInfo: (String) -> Unit,
    onLoadError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                destroy()
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                webViewRef = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(QoopyJsBridge(onAppInfo), "AndroidQoopyBridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        view.evaluateJavascript(buildInjectionScript(Qoopy.UMAMUSUME_JP_APP_ID), null)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame) onLoadError()
                    }
                }
                loadUrl(Qoopy.BASE_URL)
            }
        }
    )
}

private suspend fun downloadQoopyApp(
    context: Context,
    app: QoopyApp,
    onTask: (String) -> Unit,
    onProgress: (Float) -> Unit
): List<File> = withContext(Dispatchers.IO) {
    val workDir = context.workDir
    workDir.mkdirs()

    val files = mutableListOf<File>()

    val baseUrl = app.download ?: throw IOException("Missing download URL")
    val baseFile = workDir.resolve("qoopy_base.apk")
    onTask(context.getString(R.string.qoopy_downloading_base_apk))
    onProgress(0f)
    val baseMd5 = bytesToHex(downloadFileAndDigest(URL(baseUrl), baseFile, "MD5") { onProgress(it) })
    val expectedBaseMd5 = app.apk?.base_apk_md5
    if (expectedBaseMd5 != null && !baseMd5.equals(expectedBaseMd5, ignoreCase = true)) {
        baseFile.delete()
        throw IOException(context.getString(R.string.qoopy_checksum_mismatch))
    }
    files.add(baseFile)

    val splits = app.split_apks
    if (splits != null) {
        for ((index, split) in splits.withIndex()) {
            val splitFile = workDir.resolve("qoopy_split_$index.apk")
            onTask(context.getString(R.string.qoopy_downloading_split_apk).format(index + 1, splits.size))
            onProgress(0f)
            val md5 = bytesToHex(downloadFileAndDigest(URL(split.url), splitFile, "MD5") { onProgress(it) })
            if (!md5.equals(split.md5, ignoreCase = true)) {
                splitFile.delete()
                throw IOException(context.getString(R.string.qoopy_checksum_mismatch))
            }
            files.add(splitFile)
        }
    }

    files
}

@Destination
@Composable
fun QoopyDownloadScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val gson = remember { Gson() }

    var appInfo by remember { mutableStateOf<QoopyApp?>(null) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    var downloading by remember { mutableStateOf(false) }
    var downloadTask by remember { mutableStateOf("") }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    fun handleAppInfo(json: String) {
        val response = try {
            gson.fromJson(json, QoopyApiResponse::class.java)
        } catch (_: JsonSyntaxException) {
            lookupError = context.getString(R.string.qoopy_parse_error)
            return
        }

        val app = response.data
        if (app == null) {
            lookupError = response.message ?: context.getString(R.string.qoopy_not_available)
            return
        }
        if (!app.isDownloadable) {
            lookupError = context.getString(R.string.qoopy_not_available)
            return
        }
        appInfo = app
    }

    fun startDownload() {
        val app = appInfo ?: return
        downloading = true
        downloadError = null
        coroutineScope.launch {
            try {
                val files = downloadQoopyApp(
                    context, app,
                    onTask = { downloadTask = it },
                    onProgress = { downloadProgress = it }
                )
                val version = app.apk?.version_name
                val finalFiles = withContext(Dispatchers.IO) {
                    if (version != null) QoopyCache.commit(context, version, files) else files
                }
                val uris = finalFiles.map { LegacyInstaller.getApkUri(context, it) }.toTypedArray()
                downloading = false
                QoopyDownloadResult.onFilesReady(uris)
                navigator.popBackStack()
            } catch (ex: Exception) {
                downloading = false
                downloadError = ex.message ?: context.getString(R.string.qoopy_download_failed)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            ScreenHeader(
                title = stringResource(R.string.qoopy_download_title),
                navigationIcon = { BackButton(navigator, enabled = !downloading) }
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val app = appInfo
            if (app == null) {
                key(reloadKey) {
                    QoopySearchWebView(
                        onAppInfo = ::handleAppInfo,
                        onLoadError = { lookupError = context.getString(R.string.qoopy_load_error) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                val error = lookupError
                if (error != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            lookupError = null
                            reloadKey++
                        }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    SectionCard {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = app.display_name ?: app.app_name ?: "",
                                style = MaterialTheme.typography.titleLarge
                            )
                            app.company?.name?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            app.apk?.version_name?.let {
                                Text(
                                    text = stringResource(R.string.version_name_prefix) + it,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            app.apk?.updated_at?.let {
                                Text(
                                    text = stringResource(R.string.qoopy_updated_at_prefix) + it,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            app.split_apks?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.qoopy_includes_splits).format(it.size + 1),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    if (downloading) {
                        Text(downloadTask, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = downloadProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Button(
                            onClick = { startDownload() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(
                                stringResource(R.string.qoopy_download_button),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    downloadError?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
