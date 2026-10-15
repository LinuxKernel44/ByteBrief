package app.bytebrief.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import app.bytebrief.AppContainer

/**
 * The single Kotlin entry point used by the Java activity. Everything below it is the Compose UI shell;
 * all state and logic come from the Java ViewModels and services.
 */
object ByteBriefUi {
    private val pendingRoute = mutableStateOf<String?>(null)

    @JvmStatic
    fun setContent(activity: ComponentActivity, container: AppContainer, startRoute: String?) {
        pendingRoute.value = startRoute
        activity.setContent { AppRoot(activity, container, pendingRoute) }
    }

    /** Called when a notification opens an already-running activity. */
    @JvmStatic
    fun openRoute(route: String?) {
        pendingRoute.value = route
    }
}
