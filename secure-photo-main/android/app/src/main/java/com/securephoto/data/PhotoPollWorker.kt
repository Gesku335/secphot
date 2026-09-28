package com.securephoto.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoPollWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Делается только запрос списка метаданных; фото, payload и токены не пишутся в уведомления или журнал.
        runCatching {
            val prefs = SecurePrefs(applicationContext)
            val room = prefs.roomId ?: return@runCatching
            val token = prefs.receiverToken ?: return@runCatching
            ApiClient(com.securephoto.BuildConfig.API_BASE_URL).list(token, room)
        }.fold({ Result.success() }, { Result.retry() })
    }
}
