package com.leadrdrk.umapatcher.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.leadrdrk.umapatcher.ui.component.centeredContent
import androidx.compose.ui.res.stringResource
import com.leadrdrk.umapatcher.R
import com.leadrdrk.umapatcher.ui.component.BackButton
import com.leadrdrk.umapatcher.ui.component.ScreenHeader
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@Destination
@Composable
fun OpenSourceLicensesScreen(navigator: DestinationsNavigator) {
    Column(Modifier.centeredContent()) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            ScreenHeader(
                title = stringResource(R.string.open_source_licenses),
                navigationIcon = { BackButton(navigator) }
            )
        }
        LibrariesContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            colors = LibraryDefaults.libraryColors(
                backgroundColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                badgeBackgroundColor = MaterialTheme.colorScheme.primary,
                badgeContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}
