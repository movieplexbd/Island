package com.dynamicisland.v3

import android.content.Context
import androidx.work.*
import com.dynamicisland.model.*
import com.dynamicisland.offline.OfflineCacheManager
import com.dynamicisland.service.DynamicIslandServiceV3
import java.util.concurrent.TimeUnit

/**
 * V3: Background WorkManager task that refreshes cached weather
 * every 30 minutes when network is available.
 * Falls back to cached data when offline.
 */
class WeatherWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val cache = OfflineCacheManager(applicationContext)
        if (!cache.isOnline()) {
            // Offline: load cache and push to island if it's showing weather
            val cached = cache.loadCachedWeather()
            if (cached != null)
                DynamicIslandServiceV3.sendEvent(IslandEvent.WeatherUpdate(cached))
            return Result.success()
        }

        // Online: in a real app you'd call a weather API here.
        // We simulate with a placeholder that apps can replace.
        // The offline cache is populated by the service whenever live data arrives.
        return Result.success()
    }

    companion object {
        private const val TAG = "WeatherWorker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WeatherWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(TAG)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }
    }
}
