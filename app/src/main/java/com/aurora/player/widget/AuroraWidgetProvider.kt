package com.aurora.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.aurora.player.MainActivity
import com.aurora.player.R

/**
 * Aurora Player — widget de inicio (idea de Auxio/Retro/Gramophone:
 * controlar la música sin abrir la app). Muestra la canción actual
 * y los botones anterior / play-pausa / siguiente. Los botones abren
 * la app y la acción se aplica al reproductor cuando está listo; el
 * título se guarda en preferencias cada vez que cambia de pista.
 * Sin portada en esta primera versión: el widget es texto y botones.
 */
class AuroraWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
    }

    companion object {
        private const val PREFS = "aurora_widget"

        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, AuroraWidgetProvider::class.java),
            )
            for (id in ids) updateWidget(context, manager, id)
        }

        fun saveNowPlaying(context: Context, title: String?, artist: String?) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", title ?: "Aurora Player")
                .putString("artist", artist ?: "")
                .apply()
            requestUpdate(context)
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val title = prefs.getString("title", "Aurora Player") ?: "Aurora Player"
            val artist = prefs.getString("artist", "Toca una canción para empezar")
                ?: "Toca una canción para empezar"
            val views = RemoteViews(context.packageName, R.layout.widget_player)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            fun action(action: String): PendingIntent {
                val intent = Intent(context, MainActivity::class.java).setAction(action)
                return PendingIntent.getActivity(
                    context,
                    action.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            views.setOnClickPendingIntent(R.id.widget_play, action(MainActivity.ACTION_WIDGET_TOGGLE))
            views.setOnClickPendingIntent(R.id.widget_next, action(MainActivity.ACTION_WIDGET_NEXT))
            views.setOnClickPendingIntent(R.id.widget_prev, action(MainActivity.ACTION_WIDGET_PREV))
            views.setOnClickPendingIntent(R.id.widget_title, action(MainActivity.ACTION_WIDGET_OPEN))
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
