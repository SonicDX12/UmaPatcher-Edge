package com.leadrdrk.umapatcher.ui.patcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.leadrdrk.umapatcher.R
import com.leadrdrk.umapatcher.ui.component.SettingsItem
import com.leadrdrk.umapatcher.ui.screen.destinations.QoopyDownloadScreenDestination
import com.leadrdrk.umapatcher.utils.safeNavigate
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@Composable
fun QoopyDownloadCard(navigator: DestinationsNavigator) {
    val lifecycleOwner = LocalLifecycleOwner.current

    SettingsItem(
        title = stringResource(R.string.qoopy_download_apk),
        subtitle = stringResource(R.string.qoopy_download_apk_desc),
        leadingIcon = painterResource(R.drawable.ic_cloud_download),
        onClick = {
            safeNavigate(lifecycleOwner) {
                navigator.navigate(QoopyDownloadScreenDestination)
            }
        }
    )
}
