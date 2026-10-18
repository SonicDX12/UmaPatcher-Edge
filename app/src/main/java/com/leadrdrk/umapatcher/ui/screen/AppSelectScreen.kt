package com.leadrdrk.umapatcher.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.leadrdrk.umapatcher.ui.component.GradientCard
import com.leadrdrk.umapatcher.ui.component.ScreenHeader
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.leadrdrk.umapatcher.R
import com.leadrdrk.umapatcher.core.GameChecker
import com.leadrdrk.umapatcher.ui.component.BackButton
import com.leadrdrk.umapatcher.utils.safeNavigate
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@Destination
@Composable
fun AppSelectScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val pm = context.packageManager
    val lifecycleOwner = LocalLifecycleOwner.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(
            title = stringResource(R.string.select_an_app),
            navigationIcon = { BackButton(navigator) }
        )
        for (packageInfo in GameChecker.getAllPackageInfo(pm)) {
            if (packageInfo == null) continue

            val appInfo = pm.getApplicationInfo(packageInfo.packageName, 0)
            AppEntry(
                appName = pm.getApplicationLabel(appInfo).toString(),
                packageName = packageInfo.packageName,
                version = packageInfo.versionName,
                icon = appInfo.loadIcon(pm).toBitmap().asImageBitmap(),
                onClick = {
                    GameChecker.currentPackageName = packageInfo.packageName
                    safeNavigate(lifecycleOwner) {
                        navigator.popBackStack()
                    }
                }
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun AppEntry(
    appName: String,
    packageName: String,
    version: String,
    icon: ImageBitmap,
    onClick: () -> Unit
) {
    GradientCard(onClick = onClick) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
        )
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = appName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "v$version",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
