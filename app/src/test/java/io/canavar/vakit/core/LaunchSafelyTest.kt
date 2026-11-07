package io.canavar.vakit.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchSafelyTest {
    @Test
    fun `a failing block completes without failing the scope`() = runTest {
        val job = launchSafely { throw IllegalStateException("boom") }
        job.join()
        assertTrue(job.isCompleted)
    }

    @Test
    fun `runSafely lets a second action run after the first fails`() = runTest {
        var secondRan = false

        runSafely { throw IllegalStateException("boom") }
        runSafely { secondRan = true }

        assertTrue(secondRan)
    }
}
