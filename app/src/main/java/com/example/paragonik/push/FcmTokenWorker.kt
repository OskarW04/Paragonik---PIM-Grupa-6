package com.example.paragonik.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.paragonik.api.Api
import com.example.paragonik.api.FcmTokenRequest
import com.example.paragonik.api.Session
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

// Wysyła serwerowi token FCM tego telefonu, żeby pushe dla zalogowanego konta trafiały właśnie tutaj.
class FcmTokenWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (Session.token == null) return Result.success()
        val token = currentFcmToken() ?: return Result.failure()
        return try {
            Api.service.saveFcmToken(FcmTokenRequest(token))
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: HttpException) {
            if (e.code() >= 500) Result.retry() else Result.failure()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<FcmTokenWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("fcm-token", ExistingWorkPolicy.REPLACE, request)
        }
    }
}

// Token FCM tego telefonu albo null, jeśli Firebase nie odpowiada.
suspend fun currentFcmToken(): String? = withContext(Dispatchers.IO) {
    try {
        Tasks.await(FirebaseMessaging.getInstance().token)
    } catch (e: Exception) {
        null
    }
}
