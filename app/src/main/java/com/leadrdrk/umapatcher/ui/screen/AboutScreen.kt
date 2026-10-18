package com.leadrdrk.umapatcher.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leadrdrk.umapatcher.BuildConfig
import com.leadrdrk.umapatcher.R
import com.leadrdrk.umapatcher.core.UpdateChecker
import com.leadrdrk.umapatcher.ui.component.BottomBarClearance
import com.leadrdrk.umapatcher.ui.component.ScreenHeader
import com.leadrdrk.umapatcher.ui.component.SectionCard
import com.leadrdrk.umapatcher.ui.component.SettingsItem
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import com.leadrdrk.umapatcher.ui.screen.destinations.OpenSourceLicensesScreenDestination
import com.leadrdrk.umapatcher.utils.safeNavigate
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@Destination
@Composable
fun AboutScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ScreenHeader(stringResource(R.string.about))
        SectionCard {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_about_logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(96.dp, 96.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "v" + BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.app_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SettingsItem(
            title = stringResource(R.string.check_for_updates),
            icon = Icons.Outlined.Refresh,
            onClick = { UpdateChecker.run(context) }
        )
        SettingsItem(
            title = stringResource(R.string.open_source_licenses),
            icon = Icons.Outlined.Info,
            onClick = {
                safeNavigate(lifecycleOwner) {
                    navigator.navigate(OpenSourceLicensesScreenDestination)
                }
            }
        )
        SettingsItem(
            title = stringResource(R.string.view_source_code),
            leadingIcon = painterResource(R.drawable.ic_github),
            onClick = { uriHandler.openUri("https://github.com/LeadRDRK/UmaPatcher") }
        )
        SettingsItem(
            title = stringResource(R.string.support_me),
            leadingIcon = painterResource(R.drawable.ic_kofi),
            onClick = { uriHandler.openUri("https://ko-fi.com/leadrdrk") }
        )
        Spacer(Modifier.height(BottomBarClearance))
    }
}
