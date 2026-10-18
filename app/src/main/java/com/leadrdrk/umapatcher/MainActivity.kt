package com.leadrdrk.umapatcher

import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import com.leadrdrk.umapatcher.core.GameChecker
import com.leadrdrk.umapatcher.core.GameUpdateChecker
import com.leadrdrk.umapatcher.core.UpdateChecker
import com.leadrdrk.umapatcher.ui.component.SimpleOkCancelDialog
import com.leadrdrk.umapatcher.ui.patcher.PatcherLauncher
import com.leadrdrk.umapatcher.ui.screen.BottomBarDestination
import com.leadrdrk.umapatcher.ui.screen.NavGraphs
import com.leadrdrk.umapatcher.ui.theme.UmaPatcherTheme
import com.leadrdrk.umapatcher.utils.deleteRecursive
import com.leadrdrk.umapatcher.utils.repoDir
import com.leadrdrk.umapatcher.utils.workDir
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.defaults.RootNavGraphDefaultAnimations
import com.ramcosta.composedestinations.animations.rememberAnimatedNavHostEngine
import com.ramcosta.composedestinations.navigation.popBackStack
import com.ramcosta.composedestinations.utils.isRouteOnBackStackAsState
import rikka.shizuku.Shizuku
import com.topjohnwu.superuser.Shell

private val rootInitialized = mutableStateOf(false)

class MainActivity : ComponentActivity() {
    companion object {
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 9975
        var onShizukuPermissionResult: ((grantResult: Int) -> Unit)? = null
        var pendingUpdateDeepLink by mutableStateOf(false)
        init {
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
            )
        }
    }

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                onShizukuPermissionResult?.invoke(grantResult)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        appInit()

        checkDeepLink(intent)

        setContent {
            UmaPatcherTheme {
                MainContent()
            }
        }

        UpdateChecker.init(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        checkDeepLink(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        onShizukuPermissionResult = null
    }

    private fun checkDeepLink(intent: Intent?) {
        val uri = intent?.data
        if (uri?.scheme == "umapatcher-edge" && uri?.host == "update-hachimi") {
            pendingUpdateDeepLink = true
        }
    }

    private fun appInit() {
        GameChecker.init(packageManager)
        GameUpdateChecker.init(this)

        if (!PatcherLauncher.patching) {
            workDir.mkdir()
            deleteRecursive(workDir, deleteRoot = false)
        }

        // Remove legacy repo directory (if it exists)
        deleteRecursive(repoDir, deleteRoot = true)

        // Request root permissions
        Shell.getShell { rootInitialized.value = true }
    }

    fun useKeepScreenOn(callback: () -> Unit) {
        runOnUiThread {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        callback()
        runOnUiThread {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@OptIn(ExperimentalMaterialNavigationApi::class, ExperimentalAnimationApi::class)
@Composable
private fun MainContent() {
    val navController = rememberNavController()
    val navHostEngine = rememberAnimatedNavHostEngine(
        rootDefaultAnimations = navAnimations
    )

    val bottomBarState = rememberSaveable { mutableStateOf(true) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    // The floating bar only belongs on the tab screens; sub-screens get the whole height
    bottomBarState.value = BottomBarDestination.entries.any {
        it.direction.route == navBackStackEntry?.destination?.route
    }

    var openUpdateDialog by remember { mutableStateOf(false) }
    var tagName by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current

    UpdateChecker.callback = { name ->
        tagName = name
        openUpdateDialog = true
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            if (rootInitialized.value) {
                DestinationsNavHost(
                    navGraph = NavGraphs.root,
                    navController = navController,
                    engine = navHostEngine
                )
            }
            BottomBar(navController, bottomBarState, Modifier.align(Alignment.BottomCenter))
            if (openUpdateDialog) {
                SimpleOkCancelDialog(
                    title = stringResource(R.string.update_available),
                    onClose = { ok ->
                        openUpdateDialog = false
                        if (ok) uriHandler.openUri(UpdateChecker.getReleaseUrl(tagName))
                    }
                ) {
                    Text(
                        stringResource(R.string.update_available_desc).format(tagName)
                    )
                }
            }
        }
    }
}

private val navAnimations = RootNavGraphDefaultAnimations(
    enterTransition = {
        scaleIn(
            animationSpec = tween(200),
            initialScale = 0.9f
        ) + fadeIn(
            animationSpec = tween(200)
        )
    },
    exitTransition = {
        scaleOut(
            animationSpec = tween(200),
            targetScale = 0.9f
        ) + fadeOut(
            animationSpec = tween(200)
        )
    },
    popExitTransition = {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Companion.Right,
            animationSpec = tween(200)
        )
    }
)

@Composable
private fun BottomBar(
    navController: NavHostController,
    bottomBarState: MutableState<Boolean>,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = bottomBarState.value,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 8.dp,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Row(Modifier.padding(6.dp)) {
                BottomBarDestination.entries.forEach { destination ->
                    val selected by navController.isRouteOnBackStackAsState(destination.direction)
                    BottomBarItem(destination, selected) {
                        if (selected) {
                            navController.popBackStack(destination.direction, false)
                        }

                        navController.navigate(destination.direction.route) {
                            popUpTo(NavGraphs.root.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(destination: BottomBarDestination, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(destination.label)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.width(104.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp)
        ) {
            Icon(if (selected) destination.iconSelected else destination.iconNotSelected, label)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MainContent()
}