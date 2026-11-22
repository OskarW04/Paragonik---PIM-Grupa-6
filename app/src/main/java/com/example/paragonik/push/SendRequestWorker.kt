package com.example.paragonik.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.paragonik.api.Api
import com.example.paragonik.api.CreatePaymentRequest
import com.example.paragonik.api.apiJson
import retrofit2.HttpException
import java.io.IOException

// Wysyła jedną prośbę o zwrot. WorkManager trzyma ją w kolejce, dopóki nie ma internetu,
// także po zamknięciu aplikacji albo restarcie telefonu.
class SendRequestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val json = inputData.getString(KEY_BODY) ?: return Result.failure()
        val body = apiJson.decodeFromString(CreatePaymentRequest.serializer(), json)
        return try {
            Api.service.sendRequest(body)
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: HttpException) {
            // 4xx to złe dane albo wylogowanie, ponawianie nic nie da
            if (e.code() >= 500) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val TAG = "send-request"
        private const val KEY_BODY = "body"

        fun enqueue(context: Context, body: CreatePaymentRequest) {
            var json = apiJson.encodeToString(CreatePaymentRequest.serializer(), body)
            // dane workera mogą mieć najwyżej 10 KB, a lista pozycji jest opcjonalna
            if (json.toByteArray().size > 9_000) {
                json = apiJson.encodeToString(CreatePaymentRequest.serializer(), body.copy(items = emptyList()))
            }
            val request = OneTimeWorkRequestBuilder<SendRequestWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_BODY to json))
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
