package com.echomind.data.followup

import android.content.Context
import com.echomind.data.repository.DecisionRepository
import com.echomind.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.TimeUnit

@Singleton
class FollowUpCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val decisionRepository: DecisionRepository,
    private val store: FollowUpStore,
    private val notificationManager: FollowUpNotificationManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val mutationMutex = Mutex()

    suspend fun getFor(decisionIds: Collection<Long>): Map<Long, FollowUpRecord> =
        withContext(ioDispatcher) { store.getFor(decisionIds) }

    suspend fun schedule(decisionId: Long, days: Int): FollowUpRecord = withContext(ioDispatcher) {
        mutationMutex.withLock {
            require(days in 1..3) { "A follow-up must be scheduled one to three days ahead." }
            val decision = requireNotNull(decisionRepository.getDecision(decisionId)) {
                "Decision $decisionId does not exist."
            }
            check(decision.isDecided) { "A follow-up requires an explicit choice." }

            val now = System.currentTimeMillis()
            val triggerAt = now + TimeUnit.DAYS.toMillis(days.toLong())
            check(store.reserve(decisionId, triggerAt)) {
                "This decision already has a completed or active follow-up."
            }
            runCatching {
                FollowUpScheduler.schedule(context, decisionId, triggerAt, now)
                check(store.markScheduled(decisionId)) { "Follow-up state could not be committed." }
            }.onFailure {
                store.markFailed(decisionId)
            }.getOrThrow()
            requireNotNull(store.get(decisionId))
        }
    }

    suspend fun postpone(decisionId: Long, days: Int = 1): FollowUpRecord =
        withContext(ioDispatcher) {
            mutationMutex.withLock {
                require(days in 1..3) { "A follow-up must be postponed one to three days ahead." }
                val existing = requireNotNull(store.get(decisionId)) {
                    "Follow-up $decisionId does not exist."
                }
                check(existing.status in setOf(
                    FollowUpStatus.SCHEDULED,
                    FollowUpStatus.POSTPONED,
                    FollowUpStatus.FIRED
                )) {
                    "Only an active follow-up can be postponed."
                }
                val now = System.currentTimeMillis()
                val triggerAt = now + TimeUnit.DAYS.toMillis(days.toLong())
                check(store.markPostponed(decisionId, triggerAt)) {
                    "Follow-up $decisionId changed before it could be postponed."
                }
                runCatching {
                    FollowUpScheduler.postpone(context, decisionId, triggerAt, now)
                }.onFailure {
                    store.restoreScheduled(decisionId, existing.triggerAtMillis)
                }.getOrThrow()
                notificationManager.cancel(decisionId)
                requireNotNull(store.get(decisionId))
            }
        }

    suspend fun cancel(decisionId: Long): Boolean = withContext(ioDispatcher) {
        mutationMutex.withLock {
            val changed = store.markCanceled(decisionId)
            if (!changed) return@withLock false
            FollowUpScheduler.cancel(context, decisionId)
            notificationManager.cancel(decisionId)
            true
        }
    }
}
