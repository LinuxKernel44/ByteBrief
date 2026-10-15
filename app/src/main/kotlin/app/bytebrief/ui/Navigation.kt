package app.bytebrief.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

enum class Tab(val label: String) { ForYou("For You"), Browse("Browse"), Briefing("Briefing"), Saved("Saved"), More("More") }

/** A screen. Serialized as a string so the back stack survives configuration changes and process death. */
sealed class Route(val key: String) {
    data class Home(val tab: Tab) : Route("home:${tab.name}")
    data class Detail(val id: Long) : Route("detail:$id")
    data object Search : Route("search")
    data object Sources : Route("sources")
    data object Models : Route("models")
    data object Settings : Route("settings")
    data object About : Route("about")

    companion object {
        fun parse(key: String): Route = when {
            key.startsWith("home:") -> Home(runCatching { Tab.valueOf(key.removePrefix("home:")) }.getOrDefault(Tab.ForYou))
            key.startsWith("detail:") -> Detail(key.removePrefix("detail:").toLongOrNull() ?: -1)
            key == "search" -> Search
            key == "sources" -> Sources
            key == "models" -> Models
            key == "settings" -> Settings
            key == "about" -> About
            else -> Home(Tab.ForYou)
        }
    }
}

@Stable
class NavState(initial: List<Route>) {
    val stack = mutableStateListOf<Route>().apply { addAll(initial) }
    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun push(route: Route) {
        if (current != route) stack.add(route)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Switches the bottom tab without growing the back stack. */
    fun selectTab(tab: Tab) {
        stack.clear()
        stack.add(Route.Home(tab))
    }

    val currentTab: Tab get() = (stack.first() as? Route.Home)?.tab ?: Tab.ForYou
}

@Composable
fun rememberNavState(): NavState = rememberSaveable(
    saver = listSaver(
        save = { nav -> nav.stack.map { it.key } },
        restore = { keys -> NavState(keys.map { Route.parse(it) }) },
    )
) { NavState(listOf(Route.Home(Tab.ForYou))) }
