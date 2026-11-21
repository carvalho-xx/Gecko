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
import io.github.carvalhoxx.gecko.domain.repository.HltbRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface HltbRetryScheduler {
    fun scheduleRetry(title: String, genreHint: String? = null)
}

class WorkManagerHltbRetryScheduler @Inject constructor(@ApplicationContext private val context: Context) :
    HltbRetryScheduler {

    override fun scheduleRetry(title: String, genreHint: String?) {
        val normalized = title.trim().lowercase()
        if (normalized.isEmpty()) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<HltbSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .setInputData(
                workDataOf(
                    HltbSyncWorker.KEY_TITLE to title,
                    HltbSyncWorker.KEY_GENRE_HINT to genreHint
                )
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName = "${HltbSyncWorker.UNIQUE_WORK_PREFIX}$normalized",
            existingWorkPolicy = ExistingWorkPolicy.KEEP,
            request = request
        )
    }
}

@HiltWorker
class HltbSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val hltbRepository: HltbRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE)?.trim().orEmpty()
        if (title.isEmpty()) return Result.failure()

        val genreHint = inputData.getString(KEY_GENRE_HINT)
        val estimate = hltbRepository.fetchEstimate(title = title, genreHint = genreHint)
        return if (!estimate.isFromFallback) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        const val KEY_TITLE = "hltb_title"
        const val KEY_GENRE_HINT = "hltb_genre_hint"
        const val UNIQUE_WORK_PREFIX = "hltb_sync_"
    }
}
