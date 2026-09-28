package com.securephoto.data

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object PollScheduler {
    fun schedule(context: Context) { WorkManager.getInstance(context).enqueueUniquePeriodicWork("photo-metadata-poll", ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<PhotoPollWorker>(15, TimeUnit.MINUTES).build()) }
}
