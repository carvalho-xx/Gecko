package io.github.carvalhoxx.gecko.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.carvalhoxx.gecko.domain.repository.CatalogSyncRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface LibraryDeltaSyncScheduler {
    fun scheduleOneTimeSync()
}

class WorkManagerLibraryDeltaSyncScheduler @Inject constructor(@ApplicationContext private val context: Context) :
    LibraryDeltaSyncScheduler {

    override fun scheduleOneTimeSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<LibraryDeltaSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName = LibraryDeltaSyncWorker.UNIQUE_WORK_NAME,
            existingWorkPolicy = ExistingWorkPolicy.KEEP,
            request = request
        )
    }
}

/**
 * Worker de sincronização Delta Baseline (RF07, RNF01):
 * Atualiza apenas platform_reported_time_ms via CatalogSyncRepository,
 * preservando integralmente local_logged_time_ms.
 */
@HiltWorker
class LibraryDeltaSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val catalogSyncRepository: CatalogSyncRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val syncResult = catalogSyncRepository.syncPlatformPlaytimes()
        return syncResult.fold(
            onSuccess = { updatedCount ->
                Result.success(workDataOf(KEY_UPDATED_COUNT to updatedCount))
            },
            onFailure = {
                if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "library_delta_sync"
        const val KEY_UPDATED_COUNT = "updated_games_count"
        const val MAX_RETRY_ATTEMPTS = 3
    }
}
