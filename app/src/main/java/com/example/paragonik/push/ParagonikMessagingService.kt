package com.example.paragonik.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.paragonik.ParagonikApp
import com.example.paragonik.R
import com.example.paragonik.RequestsActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ParagonikMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        FcmTokenWorker.enqueue(this)
    }

    // Przychodzi tu, gdy aplikacja jest na ekranie. Wtedy system sam nie pokaże powiadomienia,
    // więc pokazujemy je sami. Gdy aplikacja jest w tle, powiadomienie pokazuje system.
    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val id = message.messageId.hashCode()
        val intent = Intent(this, RequestsActivity::class.java)
            .putExtra(RequestsActivity.EXTRA_REQUEST_ID, message.data["requestId"])
        val pendingIntent = PendingIntent.getActivity(
            this, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val shown = NotificationCompat.Builder(this, ParagonikApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(id, shown)
    }
}
