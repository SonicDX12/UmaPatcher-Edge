package com.leadrdrk.umapatcher.ui.patcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.leadrdrk.umapatcher.R
import com.leadrdrk.umapatcher.patcher.Patcher
import com.leadrdrk.umapatcher.ui.screen.destinations.PatchingScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PatcherLauncher {
    private const val MAX_LOG_LINES = 200

    var patcher: Patcher? = null
        private set

    var patching by mutableStateOf(false)
        private set
    var completed by mutableStateOf(false)
        private set
    var succeeded by mutableStateOf(false)
        private set

    /** Index of the install method in the patcher card: 0=Save, 1=Normal, 2=Direct, 3=Shizuku, 4=Legacy. */
    var installMethod = 0
        private set
    var startedAt = 0L
        private set
    var finishedAt by mutableLongStateOf(0L)
        private set

    var task by mutableStateOf("")
    var progress by mutableFloatStateOf(-1f)
    val logEntries = mutableStateListOf<String>()

    fun launch(navigator: DestinationsNavigator, installMethod: Int, patcher: Patcher) {
        if (patching) return

        this.patcher = patcher
        this.installMethod = installMethod
        completed = false
        succeeded = false
        task = ""
        progress = -1f
        logEntries.clear()
        navigator.navigate(PatchingScreenDestination)
    }

    fun log(line: String) {
        logEntries.add(line)
        if (logEntries.size > MAX_LOG_LINES)
            logEntries.removeRange(0, logEntries.size - MAX_LOG_LINES)
    }

    fun onTask(task: String) {
        this.task = task
        log("-- $task")
    }

    fun attachCallbacks(
        onSaveFile: (String, File, (Boolean) -> Unit) -> Unit,
        onInstallLegacy: (File, (Boolean) -> Unit) -> Unit
    ) {
        patcher?.setCallbacks(
            onLog = ::log,
            onProgress = { progress = it },
            onTask = ::onTask,
            onSaveFile = onSaveFile,
            onInstallLegacy = onInstallLegacy
        )
    }

    suspend fun runPatcher(context: Context) {
        val patcher = this.patcher ?: return
        if (patching || completed) return

        patching = true
        startedAt = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            try {
                val success = patcher.safeRun(context)
                succeeded = success
                progress = 1f
                task = context.getString(R.string.completed)
                log(context.getString(
                    if (success) R.string.patch_success_msg
                    else R.string.patch_failed_msg
                ))
            } finally {
                finishedAt = System.currentTimeMillis()
                patching = false
                completed = true
            }
        }
    }
}
