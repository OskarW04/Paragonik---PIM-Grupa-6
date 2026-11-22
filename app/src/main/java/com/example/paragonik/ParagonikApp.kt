package com.example.paragonik

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.example.paragonik.api.Api
import com.example.paragonik.api.Session
import kotlinx.coroutines.runBlocking

class ParagonikApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // sesja to kilka wartości w małym pliku, więc wczytujemy ją od razu, zanim pokaże się pierwszy ekran
        runBlocking { Session.load(this@ParagonikApp) }
        Api.init(this)

        // ten sam kanał, który podaje serwer w pushu
        val channel = NotificationChannel(CHANNEL_ID, "Prośby o zwrot", NotificationManager.IMPORTANCE_HIGH)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "payment_requests"
    }
}
