package com.leadrdrk.umapatcher.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import com.leadrdrk.umapatcher.ui.component.ScreenHeader
import com.leadrdrk.umapatcher.ui.component.SectionCard
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.leadrdrk.umapatcher.R
import com.leadrdrk.umapatcher.patcher.LegacyInstaller
import com.leadrdrk.umapatcher.ui.component.BackButton
import com.leadrdrk.umapatcher.ui.component.SimpleOkCancelDialog
import com.leadrdrk.umapatcher.ui.patcher.PatcherLauncher
import com.leadrdrk.umapatcher.utils.copyTo
import com.leadrdrk.umapatcher.utils.getActivity
import com.leadrdrk.umapatcher.utils.safeNavigate
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Destination
@Composable
fun PatchingScreen(navigator: DestinationsNavigator) {
    val workingStr = stringResource(R.string.working)
    val completedStr = stringResource(R.string.completed)

    val log = PatcherLauncher.logEntries
    val currentTask = PatcherLauncher.task.ifEmpty { workingStr }
    val progress = PatcherLauncher.progress
    val completed = PatcherLauncher.completed
    val context = LocalContext.current

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val coroutineScope = rememberCoroutineScope()
    var sfFile by remember { mutableStateOf<File?>(null) }
    var sfCallback: (Boolean) -> Unit by remember { mutableStateOf({}) }
    val sfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data?.data
        if (uri == null) {
            sfCallback(false)
            sfFile = null
            return@rememberLauncherForActivityResult
        }

        coroutineScope.launch(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri).use { output ->
                if (output == null) {
                    sfCallback(false)
                    sfFile = null
                    return@launch
                }

                val file = sfFile!!
                val length = file.length().toFloat()
                PatcherLauncher.onTask(context.getString(R.string.copying_file_name).format(file.name))
                PatcherLauncher.progress = 0f
                file.inputStream().use { input ->
                    input.copyTo(output) { current ->
                        PatcherLauncher.progress = current / length
                    }
                }
            }
            sfCallback(true)
            PatcherLauncher.task = completedStr
            sfFile = null
        }
    }

    fun onSaveFile(filename: String, file: File, callback: (Boolean) -> Unit = {}) {
        sfFile = file
        sfCallback = callback
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_TITLE, filename)
            }
        sfLauncher.launch(intent)
    }

    var legacyFile by remember { mutableStateOf<File?>(null) }
    var legacyCallback by remember { mutableStateOf<(Boolean) -> Unit>({}) }
    var showLegacyPermDialog by remember { mutableStateOf(false) }


    fun launchLegacyInstall(context: Context, file: File, callback: (Boolean) -> Unit) {
        try {
            val activityContext = context.getActivity() ?: context
            val intent = LegacyInstaller.buildInstallIntent(activityContext, file)
            activityContext.startActivity(intent)
            legacyFile = null
            callback(true)
        } catch (_: ActivityNotFoundException) {
            callback(false)
        } catch (_: SecurityException) {
            callback(false)
        } catch (_: Exception) {
            callback(false)
        }
    }

    val legacyPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val file = legacyFile
        if (file == null) {
            legacyCallback(false)
            return@rememberLauncherForActivityResult
        }
        if (LegacyInstaller.canRequestPackageInstalls(context)) {
            launchLegacyInstall(context, file, legacyCallback)
        } else {
            legacyCallback(false)
            legacyFile = null
        }
    }

    fun onInstallLegacy(file: File, callback: (Boolean) -> Unit) {
        legacyFile = file
        legacyCallback = callback
        if (LegacyInstaller.canRequestPackageInstalls(context)) {
            launchLegacyInstall(context, file, callback)
        } else {
            showLegacyPermDialog = true
        }
    }

    if (showLegacyPermDialog) {
        SimpleOkCancelDialog(
            title = stringResource(R.string.legacy_install_unknown_sources_required),
            onClose = { ok ->
                showLegacyPermDialog = false
                if (ok) {
                    val permIntent = LegacyInstaller.pickUnknownSourcesIntent(context)
                    if (permIntent != null) {
                        try {
                            legacyPermLauncher.launch(permIntent)
                        } catch (_: Exception) {
                            legacyCallback(false)
                            legacyFile = null
                        }
                    } else {
                        legacyCallback(false)
                        legacyFile = null
                    }
                } else {
                    legacyCallback(false)
                    legacyFile = null
                }
            }
        ) {
            Text(stringResource(R.string.legacy_install_unknown_sources_info))
        }
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(log.size) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    BackHandler {
        if ((completed || PatcherLauncher.patcher == null) && sfFile == null && legacyFile == null) {
            safeNavigate(lifecycleOwner) {
                navigator.popBackStack()
            }
        }
    }

    LaunchedEffect(true) {
        val patcher = PatcherLauncher.patcher ?: return@LaunchedEffect
        PatcherLauncher.attachCallbacks(
            onSaveFile = ::onSaveFile,
            onInstallLegacy = ::onInstallLegacy
        )
        if (PatcherLauncher.patching || PatcherLauncher.completed) return@LaunchedEffect
        PatcherLauncher.runPatcher(context)
    }

    val canGoBack = (completed || PatcherLauncher.patcher == null) && sfFile == null && legacyFile == null
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(
            title = if (completed) completedStr else workingStr,
            navigationIcon = { BackButton(navigator, enabled = canGoBack) }
        )

        SectionCard {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    when {
                        completed -> Icon(
                            if (PatcherLauncher.succeeded) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            null,
                            tint = if (PatcherLauncher.succeeded) colors.primary else colors.error,
                            modifier = Modifier.size(64.dp)
                        )
                        progress < 0 -> CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 6.dp
                        )
                        else -> {
                            CircularProgressIndicator(
                                progress = progress,
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 6.dp,
                                trackColor = colors.surfaceVariant
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
                Spacer(Modifier.width(20.dp))
                Text(
                    text = currentTask,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        SectionCard(Modifier.weight(1f)) {
            SelectionContainer {
                Text(
                    text = log.joinToString("\n"),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = TextUnit(1.4f, TextUnitType.Em)
                    ),
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                )
            }
        }
    }
}
