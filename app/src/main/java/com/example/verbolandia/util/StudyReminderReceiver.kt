package com.example.verbolandia.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.verbolandia.data.VerbRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StudyReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STUDY_REMINDER = "com.example.verbolandia.ACTION_STUDY_REMINDER"
        const val REMINDER_REQUEST_CODE = 2001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = VerbRepository.getInstance(context)
                val summary = repository.getProgressSummary()
                val weakBase = summary.topWeakVerbs.firstOrNull()?.base
                val targetVerb = (if (weakBase != null) repository.getVerbByBase(weakBase) else null)
                    ?: repository.getVerbOfDay()

                NotificationHelper.showVerbOfDayNotification(context, targetVerb)
                com.example.verbolandia.widget.VerbAppWidgetProvider.updateAllWidgets(context)
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
