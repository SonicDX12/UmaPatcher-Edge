package com.leadrdrk.umapatcher.ui.patcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leadrdrk.umapatcher.R
import com.leadrdrk.umapatcher.ui.screen.destinations.QoopyDownloadScreenDestination
import com.leadrdrk.umapatcher.utils.safeNavigate
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@Composable
fun QoopyDownloadCard(navigator: DestinationsNavigator) {
    val lifecycleOwner = LocalLifecycleOwner.current

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                safeNavigate(lifecycleOwner) {
                    navigator.navigate(QoopyDownloadScreenDestination)
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(painterResource(R.drawable.ic_cloud_download), null)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.qoopy_download_apk),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.qoopy_download_apk_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
