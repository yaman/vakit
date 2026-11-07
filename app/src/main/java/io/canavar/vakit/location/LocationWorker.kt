package io.canavar.vakit.location

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.canavar.vakit.VakitApp
import kotlinx.coroutines.CancellationException

class LocationWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as VakitApp
        return try {
            app.container.locationCheck.run()
            app.container.scheduleNextEvents()
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}
