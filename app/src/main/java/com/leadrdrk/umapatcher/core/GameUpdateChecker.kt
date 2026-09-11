package com.leadrdrk.umapatcher.core

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Checks, on every app launch, whether a newer Umamusume JP version is available on QooApp
// than the one currently installed. Non-critical: failures are silently ignored.
object GameUpdateChecker {
    // Non-null only when a newer version than the installed one is available.
    val latestVersion = mutableStateOf<String?>(null)

    private val scope = CoroutineScope(Dispatchers.IO)
    private var running = false

    fun init(context: Context) {
        if (running) return
        running = true

        scope.launch {
            try {
                val installed = GameChecker.getPackageInfo(context.packageManager)?.versionName
                if (installed != null) {
                    val latest = Qoopy.fetchLatestVersion()
                    if (latest != null && latest != installed) {
                        latestVersion.value = latest
                    }
                }
            } catch (_: Exception) {
                // Ignore - this is a best-effort background check
            }
            running = false
        }
    }
}
