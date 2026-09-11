package com.leadrdrk.umapatcher.ui.patcher

import android.net.Uri

object QoopyDownloadResult {
    var onFilesReady: (Array<Uri>) -> Unit = {}
}
