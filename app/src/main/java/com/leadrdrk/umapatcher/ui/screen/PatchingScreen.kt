package com.leadrdrk.umapatcher.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.graphics.drawable.toBitmap
import com.leadrdrk.umapatcher.BuildConfig
import com.leadrdrk.umapatcher.core.GameChecker
import com.leadrdrk.umapatcher.core.PrefKey
import com.leadrdrk.umapatcher.core.getPrefValue
import com.leadrdrk.umapatcher.ui.component.ActionButton
import com.leadrdrk.umapatcher.ui.component.DividedCard
import com.leadrdrk.umapatcher.ui.component.InfoRow
import com.leadrdrk.umapatcher.ui.component.Pill
import com.leadrdrk.umapatcher.utils.showToast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import com.leadrdrk.umapatcher.ui.component.isWideScreen
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
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import com.leadrdrk.umapatcher.ui.component.SectionCard
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
    val succeeded = PatcherLauncher.succeeded
    val colors = MaterialTheme.colorScheme
    val pm = context.packageManager
    val packageInfo = remember { GameChecker.getPackageInfo(pm) }
    val gameName = remember(packageInfo) {
        packageInfo?.applicationInfo?.loadLabel(pm)?.toString() ?: context.getString(R.string.app_name)
    }
    val gameIcon = remember(packageInfo) {
        packageInfo?.applicationInfo?.let { pm.getApplicationIcon(it).toBitmap().asImageBitmap() }
    }
    var hachimiVersion by remember { mutableStateOf("") }
    LaunchedEffect(completed) {
        hachimiVersion = context.getPrefValue(PrefKey.APP_LIBS_VERSION) as String
    }
    val installMethodLabel = stringResource(
        when (PatcherLauncher.installMethod) {
            0 -> R.string.save_patched_file
            2 -> R.string.direct_install
            3 -> R.string.shizuku_install
            4 -> R.string.legacy_install
            else -> R.string.normal_install
        }
    )
    val duration = if (completed)
        "%.1f s".format((PatcherLauncher.finishedAt - PatcherLauncher.startedAt) / 1000f)
    else ""

    var showLogs by remember { mutableStateOf(false) }
    if (completed && succeeded && !showLogs) {
        PatchSummary(
            gameName = gameName,
            gameIcon = gameIcon,
            saved = PatcherLauncher.installMethod == 0,
            rows = listOf(
                Triple(Icons.Filled.Star, stringResource(R.string.label_hachimi), hachimiVersion),
                Triple(Icons.Filled.Settings, stringResource(R.string.install_method), installMethodLabel),
                Triple(Icons.Filled.Refresh, stringResource(R.string.label_duration), duration)
            ),
            onOpen = packageInfo?.packageName
                ?.let { pm.getLaunchIntentForPackage(it) }
                ?.let { intent -> { context.startActivity(intent) } },
            onLogs = { showLogs = true },
            onHome = {
                safeNavigate(lifecycleOwner) {
                    navigator.popBackStack()
                }
            }
        )
        return
    }

    // Keyed on completion too, and relaunched when coming back from the summary, so the result card shows
    LaunchedEffect(log.size, completed) {
        withFrameNanos {}
        scrollState.scrollTo(scrollState.maxValue)
    }

    val clipboard = LocalClipboardManager.current
    val statusColor = when {
        !completed -> colors.primary
        succeeded -> Color(0xFF81C784)
        else -> colors.error
    }

    val startedFacts = @Composable {
        FactCard(
            title = stringResource(R.string.patching_started),
            titleColor = colors.primary,
            facts = listOf(
                stringResource(R.string.label_hachimi) to hachimiVersion.ifEmpty { "-" },
                stringResource(R.string.install_method) to installMethodLabel,
                stringResource(R.string.label_android) to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                stringResource(R.string.label_device) to "${Build.MANUFACTURER} ${Build.MODEL}",
                stringResource(R.string.app_name) to BuildConfig.VERSION_NAME
            )
        )
    }

    val header = @Composable {
        Row(
            modifier = Modifier.padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(navigator, enabled = canGoBack)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(gameName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = when {
                        !completed -> currentTask
                        succeeded -> stringResource(R.string.patch_ready)
                        else -> stringResource(R.string.patching_failed_title)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (progress >= 0) {
                Pill(
                    text = "${(progress * 100).toInt()}%",
                    containerColor = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer
                )
            }
        }

        if (progress < 0 && !completed) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
            )
        } else {
            LinearProgressIndicator(
                progress = if (completed) 1f else progress,
                color = statusColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
            )
        }
    }

    // On wide screens the facts sit in the left pane, so the log card holds the log alone
    val wide = isWideScreen()
    val logCard = @Composable { modifier: Modifier ->
        SectionCard(modifier) {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!wide) startedFacts()
                    for (line in log) LogLine(line)
                    if (completed) {
                        FactCard(
                            title = stringResource(
                                if (succeeded) R.string.patching_success_title else R.string.patching_failed_title
                            ),
                            titleColor = statusColor,
                            facts = listOf(stringResource(R.string.label_duration) to duration)
                        )
                    }
                }
            }
        }
    }

    val buttons = @Composable {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(
                text = stringResource(if (completed && succeeded) R.string.done else R.string.close),
                icon = if (completed && succeeded) Icons.Filled.Check else Icons.Filled.Close,
                enabled = canGoBack,
                onClick = {
                    if (completed && succeeded) showLogs = false
                    else safeNavigate(lifecycleOwner) { navigator.popBackStack() }
                },
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                text = stringResource(R.string.copy_logs),
                icon = Icons.Filled.Share,
                onClick = {
                    clipboard.setText(AnnotatedString(log.joinToString("\n")))
                    context.showToast(context.getString(R.string.logs_copied), Toast.LENGTH_SHORT)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (wide) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                header()
                startedFacts()
                Spacer(Modifier.weight(1f))
                buttons()
            }
            logCard(
                Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            header()
            logCard(Modifier.weight(1f))
            buttons()
        }
    }
}

/** Card titled in [titleColor] listing [facts] two per row, label above value. */
@Composable
private fun FactCard(title: String, titleColor: Color, facts: List<Pair<String, String>>) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, titleColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
            Divider(color = titleColor.copy(alpha = 0.3f))
            facts.chunked(2).forEach { pair ->
                Row {
                    pair.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(value, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** One log line with a small marker; task headers ("-- …") stand out in the primary color. */
@Composable
private fun LogLine(line: String) {
    val isTask = line.startsWith("-- ")
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(width = 4.dp, height = 12.dp)
                .clip(CircleShape)
                .background(
                    if (isTask) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                )
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (isTask) line.removePrefix("-- ") else line,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isTask) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isTask) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Screen shown once patching succeeded: the game, what was done, and what to do next. */
@Composable
private fun PatchSummary(
    gameName: String,
    gameIcon: ImageBitmap?,
    saved: Boolean,
    rows: List<Triple<ImageVector, String, String>>,
    onOpen: (() -> Unit)?,
    onLogs: () -> Unit,
    onHome: () -> Unit
) {
    val green = Color(0xFF81C784)
    val identity = @Composable {
        Box(contentAlignment = Alignment.BottomEnd) {
            if (gameIcon != null) {
                Image(
                    bitmap = gameIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(128.dp)
                        .clip(RoundedCornerShape(32.dp))
                )
            }
            Surface(
                shape = CircleShape,
                color = green,
                border = BorderStroke(4.dp, MaterialTheme.colorScheme.background),
                modifier = Modifier
                    .offset(x = 12.dp, y = 12.dp)
                    .size(48.dp)
            ) {
                Icon(Icons.Filled.Check, null, Modifier.padding(10.dp), tint = Color(0xFF1B5E20))
            }
        }
        Pill(
            text = stringResource(if (saved) R.string.status_saved else R.string.status_installed),
            icon = Icons.Filled.Check,
            containerColor = Color(0xFF1B5E20),
            contentColor = Color(0xFFC8E6C9)
        )
        Text(gameName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = stringResource(if (saved) R.string.patched_saved_desc else R.string.patched_installed_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
    val details = @Composable {
        DividedCard(rows.map { (icon, label, value) -> { InfoRow(label, value, icon) } })
    }
    val openButton = @Composable { modifier: Modifier ->
        if (onOpen != null && !saved) {
            ActionButton(
                text = stringResource(R.string.open_game),
                icon = Icons.Filled.PlayArrow,
                primary = true,
                onClick = onOpen,
                modifier = modifier
            )
        }
    }
    val navButtons = @Composable { scope: RowScope ->
        with(scope) {
            ActionButton(
                text = stringResource(R.string.logs),
                icon = Icons.Filled.List,
                onClick = onLogs,
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                text = stringResource(R.string.home),
                icon = Icons.Filled.Home,
                onClick = onHome,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (isWideScreen()) {
        // Identity on the left, details on the right, every action in one row along the bottom
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) { identity() }
                Box(Modifier.weight(1f)) { details() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                navButtons(this)
                openButton(Modifier.weight(2f))
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(48.dp))
        identity()
        details()
        Spacer(Modifier.height(16.dp))
        openButton(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { navButtons(this) }
    }
}
