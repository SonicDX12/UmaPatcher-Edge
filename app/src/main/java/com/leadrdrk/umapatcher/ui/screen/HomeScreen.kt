package com.leadrdrk.umapatcher.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.leadrdrk.umapatcher.R
import com.leadrdrk.umapatcher.core.GameChecker
import com.leadrdrk.umapatcher.core.GameUpdateChecker
import com.leadrdrk.umapatcher.core.PluginEntry
import com.leadrdrk.umapatcher.core.PluginManager
import com.leadrdrk.umapatcher.core.PrefKey
import com.leadrdrk.umapatcher.core.getPrefValue
import com.leadrdrk.umapatcher.ui.component.ActionButton
import com.leadrdrk.umapatcher.ui.component.BottomBarClearance
import com.leadrdrk.umapatcher.ui.component.ChoiceRow
import com.leadrdrk.umapatcher.ui.component.DividedCard
import com.leadrdrk.umapatcher.ui.component.GradientCard
import com.leadrdrk.umapatcher.ui.component.Pill
import com.leadrdrk.umapatcher.ui.component.ScreenHeader
import com.leadrdrk.umapatcher.ui.component.SectionTitle
import com.leadrdrk.umapatcher.ui.component.isWideScreen
import com.leadrdrk.umapatcher.ui.patcher.AppPatcherCard
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
    @Composable
    fun ScrollingColumn(modifier: Modifier, content: @Composable () -> Unit) = Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) { content() }

    if (isWideScreen()) {
        // Game and plugins on the left, the patcher itself on the right
        Row(Modifier.fillMaxSize()) {
            ScrollingColumn(Modifier.weight(1f)) {
                ScreenHeader(stringResource(R.string.app_name))
                InstallStatusCard(navigator)
                PluginSection()
                Spacer(Modifier.height(16.dp))
            }
            ScrollingColumn(Modifier.weight(1f)) {
                Spacer(Modifier.height(24.dp))
                AppPatcherCard(navigator)
                Spacer(Modifier.height(16.dp))
            }
        }
    } else {
        ScrollingColumn(Modifier.fillMaxSize()) {
            ScreenHeader(stringResource(R.string.app_name))
            InstallStatusCard(navigator)
            AppPatcherCard(navigator)
            PluginSection()
            Spacer(Modifier.height(BottomBarClearance))
        }
    }
}

@Composable
fun InstallStatusCard(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val pm = context.packageManager
    val packageInfo = GameChecker.getPackageInfo(pm)
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestVersion = GameUpdateChecker.latestVersion.value
    val icon = remember(packageInfo) {
        packageInfo?.applicationInfo?.let { pm.getApplicationIcon(it).toBitmap().asImageBitmap() }
    }
    var hachimiVersion by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        hachimiVersion = context.getPrefValue(PrefKey.APP_LIBS_VERSION) as String
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
                Text(
                    text = packageInfo.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hachimiVersion.isNotEmpty()) {
                        Pill("Hachimi Edge " + hachimiVersion, Icons.Filled.Star)
                    }
                    if (latestVersion != null) {
                        Pill(
                            text = "v$latestVersion",
                            icon = Icons.Outlined.Warning,
                            containerColor = colors.errorContainer,
                            contentColor = colors.onErrorContainer
                        )
                    }
                }
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
    val plugins = remember { mutableStateOf(listOf<PluginEntry>()) }

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

    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.plugins_title), Icons.Filled.Add)

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.errorContainer,
            contentColor = colors.onErrorContainer,
            border = BorderStroke(1.dp, colors.error.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Warning, null)
                Text(
                    text = stringResource(R.string.plugin_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }

        if (plugins.value.isNotEmpty()) {
            DividedCard(plugins.value.map { plugin ->
                {
                    ChoiceRow(
                        title = plugin.name,
                        subtitle = plugin.fileName,
                        selected = plugin.enabled,
                        onClick = {
                            PluginManager.setEnabled(context, plugin.fileName, !plugin.enabled)
                            plugins.value = PluginManager.listPlugins(context)
                        },
                        badge = {
                            IconButton(onClick = {
                                PluginManager.removePlugin(context, plugin.fileName)
                                plugins.value = PluginManager.listPlugins(context)
                                context.showToast(
                                    context.getString(R.string.plugin_removed),
                                    Toast.LENGTH_SHORT
                                )
                            }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.remove_plugin), tint = colors.error)
                            }
                        }
                    )
                }
            })
        } else {
            Text(
                text = stringResource(R.string.no_plugins),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        ActionButton(
            text = stringResource(R.string.add_plugin),
            icon = Icons.Filled.Add,
            onClick = { importPluginLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
