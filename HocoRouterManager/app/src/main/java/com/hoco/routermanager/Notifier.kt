package com.hoco.routermanager

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat

object Notifier {
    const val CH_SMS = "sms"
    const val CH_BATT = "battery"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SMS, "New SMS on router", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_BATT, "Router battery", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun post(ctx: Context, channel: String, id: Int, title: String, text: String, publicOnLockscreen: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_router)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setVisibility(if (publicOnLockscreen) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_PRIVATE)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}
