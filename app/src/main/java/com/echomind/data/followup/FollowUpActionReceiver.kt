package com.echomind.data.followup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FollowUpActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var coordinator: FollowUpCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val decisionId = intent.getLongExtra(DECISION_ID_KEY, -1L)
        if (decisionId <= 0L) {
            pendingResult.finish()
            return
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_POSTPONE -> coordinator.postpone(decisionId)
                    ACTION_CANCEL -> coordinator.cancel(decisionId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val DECISION_ID_KEY = "decision_id"
        const val ACTION_POSTPONE = "com.echomind.action.FOLLOW_UP_POSTPONE"
        const val ACTION_CANCEL = "com.echomind.action.FOLLOW_UP_CANCEL"
    }
}
