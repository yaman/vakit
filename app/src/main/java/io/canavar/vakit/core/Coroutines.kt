package io.canavar.vakit.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

fun CoroutineScope.launchSafely(block: suspend () -> Unit): Job = launch {
    runSafely(block)
}

suspend fun runSafely(block: suspend () -> Unit) {
    try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        // The caller decides whether the next action retries.
    }
}
