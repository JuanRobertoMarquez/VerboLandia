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
import com.example.verbolandia.data.VerbRepository
import com.example.verbolandia.model.Verb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VerbAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.verbolandia.ACTION_REFRESH_WIDGET"
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

        // PendingIntent to open MainActivity when tapping root
        val openAppIntent = Intent(context, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, openAppPendingIntent)

        // PendingIntent to refresh widget when tapping refresh button
        val refreshIntent = Intent(context, VerbAppWidgetProvider::class.java).apply {
            action = ACTION_REFRESH_WIDGET
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btnWidgetRefresh, refreshPendingIntent)

        // Load verb off main thread
        CoroutineScope(Dispatchers.IO).launch {
            val repository = VerbRepository.getInstance(context)
            val verb = repository.getVerbOfDay()
            updateViewsWithVerb(views, verb)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun updateViewsWithVerb(views: RemoteViews, verb: Verb) {
        views.setTextViewText(R.id.tvWidgetVerbBase, verb.base.uppercase())
        views.setTextViewText(R.id.tvWidgetMeaning, verb.spanishMeaning)
        views.setTextViewText(R.id.tvWidgetV1, "V1: ${verb.base}")
        views.setTextViewText(R.id.tvWidgetV2, "V2: ${verb.past}")
        views.setTextViewText(R.id.tvWidgetV3, "V3: ${verb.pastParticiple}")
    }
}
