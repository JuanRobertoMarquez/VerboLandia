package com.example.verbolandia.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.verbolandia.MainActivity
import com.example.verbolandia.R
import com.example.verbolandia.VerbBilliardsActivity
import com.example.verbolandia.VerbDetailActivity
import com.example.verbolandia.data.VerbRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VerbAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.verbolandia.ACTION_REFRESH_WIDGET"

        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, VerbAppWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, VerbAppWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.verb_app_widget)

        // PendingIntent to open MainActivity when tapping main background
        val openAppIntent = Intent(context, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, openAppPendingIntent)

        // PendingIntent to launch VerbBilliardsActivity directly when tapping practice button
        val practiceIntent = Intent(context, VerbBilliardsActivity::class.java)
        val practicePendingIntent = PendingIntent.getActivity(
            context,
            1,
            practiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btnWidgetPractice, practicePendingIntent)

        // PendingIntent to refresh widget when tapping refresh button
        val refreshIntent = Intent(context, VerbAppWidgetProvider::class.java).apply {
            action = ACTION_REFRESH_WIDGET
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btnWidgetRefresh, refreshPendingIntent)

        // Set App Logo Icon
        views.setImageViewResource(R.id.ivWidgetIcon, R.mipmap.ic_launcher_round)

        // Set Current Date String (e.g. "MAR, 24 NOV")
        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.forLanguageTag("es-ES"))
        val dateString = dateFormat.format(Date()).uppercase()
        views.setTextViewText(R.id.tvWidgetDate, dateString)

        // Load verb and stats off main thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = VerbRepository.getInstance(context)
                val verb = repository.getVerbOfDay()
                val progressSummary = repository.getProgressSummary()

                val prefs = context.getSharedPreferences("verbolandia_prefs", Context.MODE_PRIVATE)
                val dailyGoalTarget = prefs.getInt("daily_goal_target", 10)
                val completedToday = progressSummary.todayCompletedCount
                val retentionPct = progressSummary.globalRetention30DaysPct

                // Calculate progress percentage
                val progressPct = if (dailyGoalTarget > 0) {
                    ((completedToday.toDouble() / dailyGoalTarget) * 100).coerceAtMost(100.0).toInt()
                } else 0

                // PendingIntent to open VerbDetailActivity when tapping the verb card
                val verbDetailIntent = Intent(context, VerbDetailActivity::class.java).apply {
                    putExtra(VerbDetailActivity.EXTRA_VERB, verb)
                }
                val verbDetailPendingIntent = PendingIntent.getActivity(
                    context,
                    3,
                    verbDetailIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.cardWidgetVerb, verbDetailPendingIntent)

                // Update stats
                views.setTextViewText(R.id.tvWidgetStatGoal, "Hoy: $completedToday/$dailyGoalTarget")
                views.setProgressBar(R.id.pbWidgetGoalProgress, 100, progressPct, false)
                views.setTextViewText(R.id.tvWidgetStatRetention, "Retención: $retentionPct%")

                // Update verb fields
                views.setTextViewText(R.id.tvWidgetVerbBase, verb.base.uppercase())
                views.setTextViewText(R.id.tvWidgetMeaning, "• ${verb.spanishMeaning}")

                val categoryTag = if (verb.isIrregular) "IRREGULAR" else "REGULAR"
                val levelText = if (verb.level.isNotBlank()) "${verb.level.uppercase()} · $categoryTag" else categoryTag
                views.setTextViewText(R.id.tvWidgetVerbLevel, levelText)

                views.setTextViewText(R.id.tvWidgetV1, context.getString(R.string.widget_v1_format, verb.base))
                views.setTextViewText(R.id.tvWidgetV2, context.getString(R.string.widget_v2_format, verb.past))
                views.setTextViewText(R.id.tvWidgetV3, context.getString(R.string.widget_v3_format, verb.pastParticiple))

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
