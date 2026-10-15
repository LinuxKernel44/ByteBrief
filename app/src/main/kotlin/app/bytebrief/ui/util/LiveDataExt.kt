package app.bytebrief.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

/** Observes a [LiveData] for as long as the calling composable is in the composition. */
@Composable
fun <T> LiveData<T>.observeAsState(): State<T?> {
    val state = remember(this) { mutableStateOf(value) }
    DisposableEffect(this) {
        val observer = Observer<T> { state.value = it }
        observeForever(observer)
        onDispose { removeObserver(observer) }
    }
    return state
}

/** Convenience for "version" counters used to force re-reading settings. */
@Composable
fun LiveData<Long>.observeAsStateLong(): State<Long?> = observeAsState()
