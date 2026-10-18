package com.leadrdrk.umapatcher.ui.patcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.leadrdrk.umapatcher.core.GameUpdateChecker
import com.leadrdrk.umapatcher.ui.component.ActionButton
import com.leadrdrk.umapatcher.ui.component.ChoiceRow
import com.leadrdrk.umapatcher.ui.component.DividedCard
import com.leadrdrk.umapatcher.ui.component.Pill
import com.leadrdrk.umapatcher.ui.component.SectionTitle
import com.leadrdrk.umapatcher.ui.screen.destinations.QoopyDownloadScreenDestination
import com.leadrdrk.umapatcher.utils.safeNavigate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.leadrdrk.umapatcher.R
import com.leadrdrk.umapatcher.MainActivity
import com.leadrdrk.umapatcher.core.PrefKey
import com.leadrdrk.umapatcher.core.dataStore
import com.leadrdrk.umapatcher.core.QoopyCache
import com.leadrdrk.umapatcher.core.QoopyCacheEntry
import com.leadrdrk.umapatcher.core.getPrefValue
import com.leadrdrk.umapatcher.patcher.AppPatcher
import com.leadrdrk.umapatcher.patcher.LegacyInstaller
import com.leadrdrk.umapatcher.shizuku.ShizukuState
import com.leadrdrk.umapatcher.ui.component.SimpleOkCancelDialog
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

private const val INSTALL_METHOD_SAVE_DELAY_MILLIS = 5000L
private enum class FileSource { MANUAL, CACHE }

@Composable
fun AppPatcherCard(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    var showShizukuRationaleDialog by remember { mutableStateOf(false) }
    var showShizukuNotAvailableDialog by remember { mutableStateOf(false) }

    // Options
    // 0=Save, 1=Normal, 2=Direct, 3=Shizuku, 4=Legacy
    val installMethod = rememberSaveable { mutableIntStateOf(1) }
    var fileUris by rememberSaveable { mutableStateOf<Array<Uri>>(arrayOf()) }
    var fileSource by remember { mutableStateOf<FileSource?>(null) }
    var mergeApksPref by remember { mutableStateOf(false) }
    var cachedEntry by remember { mutableStateOf<QoopyCacheEntry?>(null) }
    LaunchedEffect(Unit) {
        cachedEntry = QoopyCache.findAny(context)
        QoopyDownloadResult.onFilesReady = { uris ->
            fileUris = uris
            fileSource = FileSource.CACHE
            cachedEntry = QoopyCache.findAny(context)
        }
    }

    var useLatestVersion by remember { mutableStateOf(true) }
    var customSoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var customSoFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var customSoError by remember { mutableStateOf<String?>(null) }
    var deepLinkMissingFiles by remember { mutableStateOf<String?>(null) }
    var stateLoaded by remember { mutableStateOf(false) }
    var installMethodLoaded by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val installMethodSaveScope = remember { CoroutineScope(SupervisorJob()) }

    // Restore options and the persisted file URIs from preferences
    LaunchedEffect(true) {
        mergeApksPref = context.getPrefValue(PrefKey.MERGE_APKS) as Boolean
        useLatestVersion = context.getPrefValue(PrefKey.USE_LATEST_VERSION) as Boolean

        if (!installMethodLoaded) {
            val savedInstallMethod = context.getPrefValue(PrefKey.INSTALL_METHOD) as Int
            if (installMethod.intValue == 1) installMethod.intValue = savedInstallMethod
            installMethodLoaded = true
        }

        if (fileUris.isEmpty()) {
            val savedFileUris = (context.getPrefValue(PrefKey.FILE_URIS) as String)
                .split('\n')
                .filter { it.isNotEmpty() }
                .map { uri -> Uri.parse(uri) }
            val existingFileUris = mutableListOf<Uri>()
            for (uri in savedFileUris) {
                if (getFileName(context, uri) != null && canOpenUri(context, uri)) {
                    existingFileUris.add(uri)
                } else {
                    releasePersistableUriPermission(context, uri)
                }
            }
            if (existingFileUris.size != savedFileUris.size)
                saveFileUris(context, existingFileUris.toTypedArray())
            if (existingFileUris.isNotEmpty()) {
                fileUris = existingFileUris.toTypedArray()
                fileSource = FileSource.MANUAL
            }
        }

        if (customSoUri == null) {
            val savedSoUri = (context.getPrefValue(PrefKey.CUSTOM_SO_URI) as String)
                .takeIf { it.isNotEmpty() }
                ?.let { Uri.parse(it) }
            if (savedSoUri != null) {
                val savedSoFileName = getFileName(context, savedSoUri)
                if (savedSoFileName != null && canOpenUri(context, savedSoUri)) {
                    customSoUri = savedSoUri
                    customSoFileName = savedSoFileName
                } else {
                    releasePersistableUriPermission(context, savedSoUri)
                    saveCustomSoUri(context, null)
                }
            }
        }

        stateLoaded = true
    }

    LaunchedEffect(installMethodLoaded) {
        if (!installMethodLoaded) return@LaunchedEffect
        snapshotFlow { installMethod.intValue }
            .drop(1)
            .collectLatest { method ->
                delay(INSTALL_METHOD_SAVE_DELAY_MILLIS)
                saveInstallMethod(context, method)
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (installMethodLoaded)
                installMethodSaveScope.launch {
                    saveInstallMethod(context, installMethod.intValue)
                }
        }
    }

    val fileSelectLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val data = it.data ?: return@rememberLauncherForActivityResult

        val clipData = data.clipData
        val newFileUris = if (clipData != null) {
            Array(clipData.itemCount) { i ->
                clipData.getItemAt(i).uri
            }
        } else {
            val uri = data.data ?: return@rememberLauncherForActivityResult
            Array(1) { uri }
        }

        for (uri in fileUris) releasePersistableUriPermission(context, uri)
        for (uri in newFileUris) tryTakePersistableUriPermission(context, uri)
        fileUris = newFileUris
        fileSource = FileSource.MANUAL
        coroutineScope.launch { saveFileUris(context, newFileUris) }
    }

    // Custom .so file picker launcher (supports external file managers)
    val customSoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val data = it.data ?: return@rememberLauncherForActivityResult

        val clipData = data.clipData
        val uri = when {
            clipData == null -> data.data ?: return@rememberLauncherForActivityResult
            clipData.itemCount == 1 -> clipData.getItemAt(0).uri
            else -> {
                customSoError = context.getString(R.string.custom_so_multiple_selected)
                return@rememberLauncherForActivityResult
            }
        }

        val fileName = getFileName(context, uri)
        if (fileName != null && !fileName.endsWith(".so", ignoreCase = true)) {
            customSoError = context.getString(R.string.custom_so_not_so_file)
            return@rememberLauncherForActivityResult
        }

        val oldSoUri = customSoUri
        if (oldSoUri != null) releasePersistableUriPermission(context, oldSoUri)
        tryTakePersistableUriPermission(context, uri)
        customSoUri = uri
        customSoFileName = fileName ?: uri.lastPathSegment
        coroutineScope.launch { saveCustomSoUri(context, uri) }
    }
    val isShizukuAvailable by ShizukuState.isAvailable

    LaunchedEffect(navigator, installMethod.intValue, fileUris) {
        MainActivity.onShizukuPermissionResult = { grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                PatcherLauncher.launch(
                    navigator,
                    AppPatcher(
                        fileUris = fileUris,
                        install = true,
                        directInstall = false,
                        shizukuInstall = true,
                        customSoUri = if (!useLatestVersion) customSoUri else null
                    )
                )
            }
        }
    }

    if(showShizukuRationaleDialog) {
        SimpleOkCancelDialog(
            title = stringResource(R.string.shizuku_permission_required),
            onClose = { ok ->
                showShizukuRationaleDialog = false
                if (ok) {
                    Shizuku.requestPermission(MainActivity.SHIZUKU_PERMISSION_REQUEST_CODE)
                }
            }
        ) {
            Text(stringResource(R.string.shizuku_permission_required))
        }
    }

    val uriHandler = LocalUriHandler.current
    if(showShizukuNotAvailableDialog) {
        SimpleOkCancelDialog(
            title = stringResource(R.string.shizuku_unavailable),
            onClose = { ok ->
                showShizukuNotAvailableDialog = false
                if (ok) {
                    uriHandler.openUri("https://shizuku.rikka.app/download")
                }
            }
        ) {
            Text(stringResource(R.string.shizuku_unavailable_info))
        }
    }

    if (customSoError != null) {
        SimpleOkCancelDialog(
            title = stringResource(R.string.custom_so_invalid_selection),
            onClose = { customSoError = null }
        ) {
            Text(customSoError!!)
        }
    }

    if (deepLinkMissingFiles != null) {
        SimpleOkCancelDialog(
            title = stringResource(R.string.deep_link_missing_files),
            onClose = { deepLinkMissingFiles = null }
        ) {
            Text(deepLinkMissingFiles!!)
        }
    }

    fun startPatching() {
        if (installMethod.intValue != 2 && fileUris.isNotEmpty()) {
            val (aliveUris, deadUris) = fileUris.partition { canOpenUri(context, it) }
            if (deadUris.isNotEmpty()) {
                for (uri in deadUris) releasePersistableUriPermission(context, uri)
                fileUris = aliveUris.toTypedArray()
                coroutineScope.launch { saveFileUris(context, aliveUris.toTypedArray()) }
                deepLinkMissingFiles = context.getString(R.string.selected_files_no_longer_available)
                return
            }
        }
        if (!useLatestVersion && customSoUri != null && !canOpenUri(context, customSoUri!!)) {
            releasePersistableUriPermission(context, customSoUri!!)
            customSoUri = null
            customSoFileName = null
            coroutineScope.launch { saveCustomSoUri(context, null) }
            customSoError = context.getString(R.string.selected_so_no_longer_available)
            return
        }

        val isShizukuOptionSelected = installMethod.intValue == 3
        if(!isShizukuAvailable && isShizukuOptionSelected) {
            showShizukuNotAvailableDialog = true
            return
        }

        if(isShizukuOptionSelected) {
            if(Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                PatcherLauncher.launch(
                    navigator,
                    AppPatcher(
                        fileUris,
                        install = true,
                        directInstall = false,
                        shizukuInstall = true,
                        customSoUri = if (!useLatestVersion) customSoUri else null
                    )
                )
            }else if (Shizuku.shouldShowRequestPermissionRationale()) {
                showShizukuRationaleDialog = true
            }else {
                Shizuku.requestPermission(MainActivity.SHIZUKU_PERMISSION_REQUEST_CODE)
            }
        }else {
            PatcherLauncher.launch(
                navigator,
                AppPatcher(
                    fileUris = if (installMethod.intValue == 2) arrayOf() else fileUris,
                    install = installMethod.intValue == 1,
                    directInstall = installMethod.intValue == 2,
                    shizukuInstall = false,
                    customSoUri = if (!useLatestVersion) customSoUri else null,
                    mergeApks = mergeApksPref && installMethod.intValue == 0,
                    legacyInstall = installMethod.intValue == 4
                )
            )
        }
    }

    // umapatcher-edge://update deep link. start patching with the last selected files
    val pendingUpdateDeepLink = MainActivity.pendingUpdateDeepLink
    LaunchedEffect(pendingUpdateDeepLink, stateLoaded) {
        if (!pendingUpdateDeepLink || !stateLoaded) return@LaunchedEffect

        MainActivity.pendingUpdateDeepLink = false

        val apksMissing = installMethod.intValue != 2 && fileUris.isEmpty()
        val soMissing = !useLatestVersion && customSoUri == null
        deepLinkMissingFiles = when {
            apksMissing && soMissing -> context.getString(R.string.deep_link_missing_apks_and_so)
            apksMissing -> context.getString(R.string.deep_link_missing_apks)
            soMissing -> context.getString(R.string.deep_link_missing_so)
            else -> null
        }
        if (deepLinkMissingFiles == null) startPatching()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val latestGameVersion = GameUpdateChecker.latestVersion.value
    val needsFiles = installMethod.intValue != 2
    val isButtonEnabled = when {
        !useLatestVersion && customSoUri == null -> false
        !needsFiles -> true
        else -> fileUris.isNotEmpty()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.apk_source_title), Icons.Filled.Search)
        DividedCard(
            listOfNotNull(
                {
                    ChoiceRow(
                        title = stringResource(R.string.qoopy_download_apk),
                        subtitle = latestGameVersion
                            ?.let { stringResource(R.string.new_version_available).format(it) }
                            ?: stringResource(R.string.qoopy_download_apk_desc),
                        selected = false,
                        enabled = needsFiles,
                        leading = { Icon(painterResource(R.drawable.ic_cloud_download), null, tint = MaterialTheme.colorScheme.primary) },
                        badge = { Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            safeNavigate(lifecycleOwner) {
                                navigator.navigate(QoopyDownloadScreenDestination)
                            }
                        }
                    )
                },
                cachedEntry?.let { entry ->
                    {
                        ChoiceRow(
                            title = stringResource(R.string.use_cached_files),
                            subtitle = stringResource(R.string.n_files_selected).format(entry.files.size),
                            selected = fileSource == FileSource.CACHE,
                            enabled = needsFiles,
                            badge = { Pill("v" + entry.version) },
                            onClick = {
                                fileUris = entry.files
                                    .map { LegacyInstaller.getApkUri(context, it) }
                                    .toTypedArray()
                                fileSource = FileSource.CACHE
                            }
                        )
                    }
                },
                {
                    ChoiceRow(
                        title = stringResource(R.string.tap_to_select_file),
                        subtitle = if (fileSource == FileSource.MANUAL)
                            stringResource(R.string.n_files_selected).format(fileUris.size)
                        else
                            stringResource(R.string.app_patcher_supported_files),
                        selected = fileSource == FileSource.MANUAL,
                        enabled = needsFiles,
                        onClick = {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                                .apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                    type = "*/*"
                                }
                            fileSelectLauncher.launch(intent)
                        }
                    )
                }
            )
        )

        // Custom .so file picker (shown when "use latest version" is disabled in settings)
        if (!useLatestVersion) {
            SectionTitle(stringResource(R.string.select_custom_so), Icons.Filled.Build)
            DividedCard(listOf {
                ChoiceRow(
                    title = customSoFileName ?: stringResource(R.string.no_custom_so_selected),
                    subtitle = stringResource(R.string.custom_so_supported_files),
                    selected = customSoFileName != null,
                    onClick = {
                        val intent = Intent(Intent.ACTION_GET_CONTENT)
                            .apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                            }
                        customSoLauncher.launch(intent)
                    }
                )
            })
        }

        SectionTitle(stringResource(R.string.install_method), Icons.Filled.Settings)
        val methods = arrayOf(
            stringResource(R.string.save_patched_file),
            stringResource(R.string.normal_install),
            stringResource(R.string.direct_install),
            stringResource(R.string.shizuku_install),
            stringResource(R.string.legacy_install)
        )
        val shizukuStatusText = if (isShizukuAvailable) stringResource(R.string.shizuku_install_available) else stringResource(R.string.shizuku_install_unavailable)
        DividedCard(methods.mapIndexed { index, label ->
            {
                ChoiceRow(
                    title = label,
                    selected = installMethod.intValue == index,
                    onClick = { installMethod.intValue = index },
                    badge = if (index == 3) {
                        {
                            Pill(
                                text = shizukuStatusText.trim('(', ')'),
                                containerColor = if (isShizukuAvailable) Color(0xFF1B5E20) else MaterialTheme.colorScheme.errorContainer,
                                contentColor = if (isShizukuAvailable) Color(0xFFC8E6C9) else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    } else null
                )
            }
        })

        Spacer(Modifier.height(8.dp))
        ActionButton(
            text = stringResource(R.string.patch),
            icon = Icons.Filled.Build,
            primary = true,
            enabled = isButtonEnabled,
            onClick = { startPatching() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    val cursor = try {
        context.contentResolver.query(uri, null, null, null, null)
    } catch (_: Exception) {
        return null
    }
    return cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) it.getString(nameIndex) else uri.lastPathSegment
        } else null
    }
}

private fun canOpenUri(context: Context, uri: Uri): Boolean {
    return try {
        context.contentResolver.openInputStream(uri)?.use { it.read() } != null
    } catch (_: Exception) {
        false
    }
}

private fun tryTakePersistableUriPermission(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: SecurityException) {
        // No persistable grant was offered for this URI
    }
}

private fun releasePersistableUriPermission(context: Context, uri: Uri) {
    try {
        context.contentResolver.releasePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: SecurityException) {
        // No persisted permission was held for this URI
    }
}

private suspend fun saveFileUris(context: Context, fileUris: Array<Uri>) {
    context.dataStore.edit {
        it[PrefKey.FILE_URIS] = fileUris.joinToString("\n") { uri -> uri.toString() }
    }
}

private suspend fun saveCustomSoUri(context: Context, uri: Uri?) {
    context.dataStore.edit {
        it[PrefKey.CUSTOM_SO_URI] = uri?.toString() ?: ""
    }
}

private suspend fun saveInstallMethod(context: Context, installMethod: Int) {
    context.dataStore.edit {
        it[PrefKey.INSTALL_METHOD] = installMethod
    }
}
