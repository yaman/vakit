package io.canavar.vakit.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

fun CoroutineScope.launchSafely(block: suspend () -> Unit): Job = launch {
    try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        // The next scheduled run retries.
    }
}
