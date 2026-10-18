package com.leadrdrk.umapatcher.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leadrdrk.umapatcher.R
import com.leadrdrk.umapatcher.core.GameChecker
import com.leadrdrk.umapatcher.core.GameUpdateChecker
import com.leadrdrk.umapatcher.core.PluginEntry
import com.leadrdrk.umapatcher.core.PluginManager
import com.leadrdrk.umapatcher.ui.component.BottomBarClearance
import com.leadrdrk.umapatcher.ui.component.ScreenHeader
import com.leadrdrk.umapatcher.ui.component.SectionCard
import com.leadrdrk.umapatcher.ui.component.GradientCard
import com.leadrdrk.umapatcher.ui.patcher.AppPatcherCard
import com.leadrdrk.umapatcher.ui.patcher.QoopyDownloadCard
import com.leadrdrk.umapatcher.ui.screen.destinations.AppSelectScreenDestination
import com.leadrdrk.umapatcher.utils.safeNavigate
import com.leadrdrk.umapatcher.utils.showToast
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@RootNavGraph(start = true)
@Destination
@Composable
fun HomeScreen(navigator: DestinationsNavigator) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(stringResource(R.string.app_name))
        InstallStatusCard(navigator)
        QoopyDownloadCard(navigator)
        AppPatcherCard(navigator)
        PluginSection()
        Spacer(Modifier.height(BottomBarClearance))
    }
}

@Composable
fun InstallStatusCard(navigator: DestinationsNavigator) {
    val pm = LocalContext.current.packageManager
    val packageInfo = GameChecker.getPackageInfo(pm)
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestVersion = GameUpdateChecker.latestVersion.value
    val icon = remember(packageInfo) {
        packageInfo?.applicationInfo?.let { pm.getApplicationIcon(it).toBitmap().asImageBitmap() }
    }
    val colors = MaterialTheme.colorScheme

    GradientCard(
        onClick = {
            safeNavigate(lifecycleOwner) {
                navigator.navigate(AppSelectScreenDestination)
            }
        }
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        } else {
            Icon(Icons.Outlined.Info, null, Modifier.size(32.dp))
        }
        Column(Modifier.padding(start = 20.dp)) {
            if (packageInfo != null) {
                Text(
                    text = packageInfo.applicationInfo?.loadLabel(pm)?.toString()
                        ?: stringResource(R.string.game_installed),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "v" + packageInfo.versionName,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (latestVersion != null) {
                    Text(
                        text = stringResource(R.string.new_version_available).format(latestVersion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.error
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = packageInfo.packageName + " · " + stringResource(R.string.tap_to_select_app),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(R.string.game_not_installed),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.game_not_installed_info),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun PluginSection() {
    val context = LocalContext.current
    val plugins = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(listOf<PluginEntry>()) }

    val importPluginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val added = PluginManager.addPlugin(context, uri)
        if (added == null) {
            context.showToast(context.getString(R.string.failed_to_add_plugin), Toast.LENGTH_SHORT)
        } else {
            context.showToast(context.getString(R.string.plugin_added), Toast.LENGTH_SHORT)
        }
        plugins.value = PluginManager.listPlugins(context)
    }

    LaunchedEffect(Unit) {
        plugins.value = PluginManager.listPlugins(context)
    }

    SectionCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_apk_install),
                    contentDescription = null
                )
                Text(
                    text = stringResource(R.string.plugins_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Spacer(Modifier.weight(1f))
                ElevatedButton(
                    onClick = { importPluginLauncher.launch(arrayOf("*/*")) }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_file_open),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.add_plugin),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.plugin_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (plugins.value.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_plugins),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                plugins.value.forEach { plugin ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = plugin.enabled,
                                onCheckedChange = {
                                    PluginManager.setEnabled(context, plugin.fileName, it)
                                    plugins.value = PluginManager.listPlugins(context)
                                }
                            )
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = plugin.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = plugin.fileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = stringResource(R.string.remove_plugin),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clickable {
                                        PluginManager.removePlugin(context, plugin.fileName)
                                        plugins.value = PluginManager.listPlugins(context)
                                        context.showToast(
                                            context.getString(R.string.plugin_removed),
                                            Toast.LENGTH_SHORT
                                        )
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
