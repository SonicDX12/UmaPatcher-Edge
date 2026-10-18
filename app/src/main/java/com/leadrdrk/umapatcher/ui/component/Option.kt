package com.leadrdrk.umapatcher.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.leadrdrk.umapatcher.core.dataStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map

@Composable
fun rememberDataStoreStringState(
    key: androidx.datastore.preferences.core.Preferences.Key<String>,
    defaultValue: String
): MutableState<String> {
    val context = LocalContext.current
    val dataStore = context.dataStore
    val coroutineScope = rememberCoroutineScope()

    val savedFlow = remember(key) {
        dataStore.data.map { preferences ->
            preferences[key] ?: defaultValue
        }
    }
    val savedValue = savedFlow.collectAsState(initial = defaultValue).value

    val state = remember {
        mutableStateOf(savedValue)
    }

    LaunchedEffect(savedValue) {
        if (state.value != savedValue) {
            state.value = savedValue
        }
    }

    return remember {
        object : MutableState<String> {
            override var value: String
                get() = state.value
                set(newValue) {
                    if (state.value != newValue) {
                        state.value = newValue

                        coroutineScope.launch {
                            dataStore.edit { preferences ->
                                preferences[key] = newValue
                            }
                        }
                    }
                }

            override fun component1() = value
            override fun component2(): (String) -> Unit = { value = it }
        }
    }
}

/** Settings item showing the current value; tapping it edits the value in a dialog. */
@Composable
fun StringOption(
    title: String,
    state: MutableState<String>,
    icon: ImageVector? = null,
    placeholder: String? = null
) {
    var openDialog by remember { mutableStateOf(false) }
    SettingsItem(
        title = title,
        subtitle = state.value,
        icon = icon,
        onClick = { openDialog = true }
    )

    var tmpValue by remember { mutableStateOf(state.value) }
    LaunchedEffect(openDialog) {
        // Reset value every time dialog is reopened
        tmpValue = state.value
    }

    if (openDialog) {
        SimpleOkCancelDialog(
            title = title,
            onClose = { ok ->
                openDialog = false
                if (ok) state.value = tmpValue
            }
        ) {
            TextField(
                value = tmpValue,
                onValueChange = { tmpValue = it },
                placeholder = {
                    if (placeholder != null)
                        Text(placeholder)
                }
            )
        }
    }
}
