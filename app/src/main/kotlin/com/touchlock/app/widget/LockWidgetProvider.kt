package com.touchlock.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.touchlock.app.R
import com.touchlock.app.services.LockOverlayService

/**
 * LockWidgetProvider — Phase 4 (Task 4.2)
 *
 * 1×1 home-screen widget. Tapping it starts the lock overlay immediately
 * without opening the app. The widget is purely a launcher shortcut — it does
 * not reflect the current lock state (that would require a live data binding
 * which is a Phase 5 enhancement).
 */
class LockWidgetProvider : AppWidgetProvider() {

    companion object {
        /** Action sent when the widget is tapped — caught by this provider. */
        const val ACTION_WIDGET_LOCK = "com.touchlock.app.ACTION_WIDGET_LOCK"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            val views = buildRemoteViews(context, widgetId)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_LOCK) {
            // Activate the lock overlay directly — no need to open MainActivity
            LockOverlayService.startLock(context)
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun buildRemoteViews(context: Context, widgetId: Int): RemoteViews {
        // PendingIntent broadcast → caught by onReceive above
        val lockIntent = Intent(context, LockWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_LOCK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            widgetId,
            lockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return RemoteViews(context.packageName, R.layout.widget_lock).apply {
            setOnClickPendingIntent(R.id.widgetIcon, pendingIntent)
            setOnClickPendingIntent(R.id.widgetLabel, pendingIntent)
        }
    }
}
