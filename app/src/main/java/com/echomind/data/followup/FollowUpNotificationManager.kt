package com.echomind.data.followup

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.echomind.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FollowUpNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun show(decisionId: Long): Boolean {
        if (!canPostNotifications()) return false
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("EchoMind follow-up")
            .setContentText("A follow-up is ready for your decision.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(decisionId))
            .addAction(
                0,
                "Postpone",
                actionIntent(FollowUpActionReceiver.ACTION_POSTPONE, decisionId)
            )
            .addAction(
                0,
                "Cancel",
                actionIntent(FollowUpActionReceiver.ACTION_CANCEL, decisionId)
            )
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_TAG,
                decisionId.toInt(),
                notification
            )
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancel(decisionId: Long) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_TAG, decisionId.toInt())
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Follow-ups",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Optional local EchoMind follow-ups"
            }
        )
    }

    private fun contentIntent(decisionId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(FollowUpActionReceiver.DECISION_ID_KEY, decisionId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            decisionId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionIntent(action: String, decisionId: Long): PendingIntent {
        val intent = Intent(context, FollowUpActionReceiver::class.java).apply {
            this.action = action
            putExtra(FollowUpActionReceiver.DECISION_ID_KEY, decisionId)
        }
        return PendingIntent.getBroadcast(
            context,
            action.hashCode() xor decisionId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val CHANNEL_ID = "follow-ups"
        const val NOTIFICATION_TAG = "echomind-follow-up"
    }
}
