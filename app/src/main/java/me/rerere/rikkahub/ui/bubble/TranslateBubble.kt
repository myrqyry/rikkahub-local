package me.rerere.rikkahub.ui.bubble

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import me.rerere.rikkahub.R
import me.rerere.rikkahub.TRANSLATE_BUBBLE_NOTIFICATION_CHANNEL_ID

object TranslateBubble {
    const val NOTIFICATION_ID = 0x5452414E

    fun show(context: Context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val intent = Intent(context, TranslateBubbleActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
        val bubbleMetadata = NotificationCompat.BubbleMetadata.Builder()
            .setDesiredHeight(600)
            .setIcon(IconCompat.createWithResource(context, R.drawable.small_icon))
            .setIntent(pendingIntent)
            .build()
        val notification = NotificationCompat.Builder(
            context,
            TRANSLATE_BUBBLE_NOTIFICATION_CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(context.getString(R.string.translate_bubble_notification_title))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setBubbleMetadata(bubbleMetadata)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun dismiss(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
