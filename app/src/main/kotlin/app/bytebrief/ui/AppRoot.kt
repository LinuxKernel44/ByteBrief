package app.bytebrief.ui

import android.Manifest
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import app.bytebrief.AppContainer
import app.bytebrief.notify.Notifier
import app.bytebrief.ui.components.LocalImageLoader
import app.bytebrief.ui.glass.AtmosphericBackground
import app.bytebrief.ui.screens.AboutScreen
import app.bytebrief.ui.screens.BriefingScreen
import app.bytebrief.ui.screens.BrowseScreen
import app.bytebrief.ui.screens.DetailScreen
import app.bytebrief.ui.screens.ForYouScreen
import app.bytebrief.ui.screens.ForumBrowserScreen
import app.bytebrief.ui.screens.VerifyScreen
import app.bytebrief.ui.screens.MoreScreen
import app.bytebrief.ui.screens.ModelsScreen
import app.bytebrief.ui.screens.OnboardingScreen
import app.bytebrief.ui.screens.SavedScreen
import app.bytebrief.ui.screens.SearchScreen
import app.bytebrief.ui.screens.SettingsScreen
import app.bytebrief.ui.screens.SourcesScreen
import app.bytebrief.ui.theme.ByteBriefTheme
import app.bytebrief.ui.theme.GlassConfig
import app.bytebrief.ui.util.observeAsState
import app.bytebrief.ui.vm.SettingsViewModel
import app.bytebrief.ui.vm.VmFactory

/** Requests the Android 13+ notification permission; a no-op on devices where it is not needed. */
val LocalRequestNotifications = compositionLocalOf<() -> Unit> { {} }
val LocalContainer = compositionLocalOf<AppContainer> { error("AppContainer not provided") }
val LocalNav = compositionLocalOf<NavState> { error("NavState not provided") }
val LocalOwner = compositionLocalOf<ViewModelStoreOwner> { error("ViewModelStoreOwner not provided") }

@Composable
inline fun <reified T : ViewModel> viewModelOf(key: String? = null, itemId: Long = -1): T {
    val owner = LocalOwner.current
    val container = LocalContainer.current
    return remember(owner, key) { ViewModelProvider(owner, VmFactory(container, itemId))[key ?: T::class.java.name, T::class.java] }
}

/**
 * Glass is turned off (opaque surfaces) when the user chose so, when battery saver is on, or reduced
 * to no motion when the system animation scale is zero. Re-evaluated every time the app resumes.
 */
@Composable
private fun rememberGlassConfig(activity: ComponentActivity, c: AppContainer, settingsVersion: Long?): GlassConfig {
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(activity) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        activity.lifecycle.addObserver(obs)
        onDispose { activity.lifecycle.removeObserver(obs) }
    }
    @Suppress("UNUSED_EXPRESSION") tick; settingsVersion
    val powerSave = activity.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    val animScale = Settings.Global.getFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return GlassConfig(enabled = !c.settings.opaqueSurfaces() && !powerSave, reducedMotion = animScale == 0f)
}

@Composable
fun AppRoot(activity: ComponentActivity, container: AppContainer, pendingRoute: MutableState<String?>) {
    val nav = rememberNavState()
    CompositionLocalProvider(LocalOwner provides activity, LocalContainer provides container, LocalNav provides nav) {
        val settingsVm = viewModelOf<SettingsViewModel>()
        val version by settingsVm.version.observeAsState()
        val glass = rememberGlassConfig(activity, container, version)
        val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        val requestNotifications: () -> Unit = {
            if (Build.VERSION.SDK_INT >= 33 && !container.notifier.canPost()) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        ByteBriefTheme(glass) {
            CompositionLocalProvider(LocalImageLoader provides container.images, LocalRequestNotifications provides requestNotifications) {
                Box(Modifier.fillMaxSize()) {
                    val route = pendingRoute.value
                    LaunchedEffect(route) {
                        when (route) {
                            "briefing" -> nav.selectTab(Tab.Briefing)
                            "releases", "forums" -> nav.selectTab(Tab.Browse)
                            "models" -> { nav.selectTab(Tab.More); nav.push(Route.Models) }
                        }
                        if (route != null) pendingRoute.value = null
                    }
                    BackHandler(enabled = nav.canGoBack) { nav.pop() }
                    if (!settingsVm.onboardingDone()) {
                        OnboardingScreen(onDone = { settingsVm.finishOnboarding() })
                    } else {
                        AppContent(nav)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppContent(nav: NavState) {
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    val top = nav.current
    val detailBeside = wide && top is Route.Detail
    val home = nav.stack.first() as Route.Home
    val animMs = if (app.bytebrief.ui.theme.LocalGlassConfig.current.reducedMotion) 0 else 160

    Box(Modifier.fillMaxSize()) {
        // The home layer stays composed under overlays so list scroll positions survive navigating back.
        Row(Modifier.fillMaxSize()) {
            Box(if (detailBeside) Modifier.width(420.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                // Tabs switch instantly: each screen owns its glass bars, so cross-fading would briefly show two bars.
                when (home.tab) {
                    Tab.ForYou -> ForYouScreen()
                    Tab.Browse -> BrowseScreen()
                    Tab.Briefing -> BriefingScreen()
                    Tab.Saved -> SavedScreen()
                    Tab.More -> MoreScreen()
                }
            }
            if (detailBeside) {
                Box(Modifier.fillMaxSize()) {
                    val id = (top as Route.Detail).id
                    DetailScreen(id, showBack = false)
                }
            }
        }
        if (!detailBeside) {
            val overlay = nav.stack.drop(1).lastOrNull()
            Crossfade(overlay, animationSpec = tween(animMs), label = "overlay") { r ->
                if (r != null) {
                    Box(Modifier.fillMaxSize()) {
                        AtmosphericBackground()
                        when (r) {
                            is Route.Detail -> DetailScreen(r.id, showBack = true)
                            Route.Search -> SearchScreen()
                            Route.Sources -> SourcesScreen()
                            Route.Models -> ModelsScreen()
                            Route.Settings -> SettingsScreen()
                            Route.About -> AboutScreen()
                            Route.Verify -> VerifyScreen()
                            Route.ForumBrowser -> ForumBrowserScreen()
                            is Route.Home -> Unit
                        }
                    }
                }
            }
        }
    }
}
