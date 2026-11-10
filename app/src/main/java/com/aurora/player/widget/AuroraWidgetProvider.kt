package com.aurora.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Size
import android.view.View
import android.widget.RemoteViews
import com.aurora.player.MainActivity
import com.aurora.player.R

/**
 * Aurora Player — widget de inicio (idea de Auxio/Retro/Gramophone:
 * controlar la música sin abrir la app). Muestra la canción actual
 * y los botones anterior / play-pausa / siguiente. Los botones abren
 * la app y la acción se aplica al reproductor cuando está listo; el
 * título se guarda en preferencias cada vez que cambia de pista.
 * Fase 2 — estilos de widget (Retro tiene 7; aquí 4): Clásico (el
 * de siempre), Compacto (solo título + play + siguiente), Grande
 * (texto y botones más grandes) y Con portada (la portada del álbum
 * junto al título; sin portada, el hueco se esconde, nunca se finge
 * una imagen). El estilo se elige en Ajustes > Interfaz.
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

        /** Estilo elegido en Ajustes > Interfaz (Clásico por defecto). */
        fun getStyle(context: Context): WidgetStyle =
            parseWidgetStyle(
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("style", null),
                WidgetStyle.CLASSIC,
            )

        fun setStyle(context: Context, style: WidgetStyle) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("style", style.name)
                .apply()
            requestUpdate(context)
        }

        fun saveNowPlaying(
            context: Context,
            title: String?,
            artist: String?,
            isPlaying: Boolean = false,
            // Fase 2 — estilos de widget: la Uri de la portada del
            // álbum (MediaStore), para el estilo Con portada.
            artUri: android.net.Uri? = null,
        ) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", title ?: "Aurora Player")
                .putString("artist", artist ?: "")
                .putBoolean("playing", isPlaying)
                .putString("art_uri", artUri?.toString())
                .apply()
            requestUpdate(context)
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val title = prefs.getString("title", "Aurora Player") ?: "Aurora Player"
            val artist = prefs.getString("artist", "Toca una canción para empezar")
                ?: "Toca una canción para empezar"
            // AUDITORÍA (2026-11-09): el botón siempre mostraba "play",
            // sonara o no. Ahora muestra pausa mientras suena.
            val playing = prefs.getBoolean("playing", false)
            // Fase 2 — estilos de widget: cada estilo tiene su layout;
            // todos llevan los mismos ids, así que el resto del código
            // no cambia (el compacto esconde artista y anterior en su
            // propio layout, con visibility="gone").
            val style = parseWidgetStyle(prefs.getString("style", null), WidgetStyle.CLASSIC)
            val layoutRes = when (style) {
                WidgetStyle.CLASSIC -> R.layout.widget_player
                WidgetStyle.COMPACT -> R.layout.widget_player_compact
                WidgetStyle.LARGE -> R.layout.widget_player_large
                WidgetStyle.COVER -> R.layout.widget_player_cover
            }
            val views = RemoteViews(context.packageName, layoutRes)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            // Estilo Con portada: la miniatura se lee de MediaStore
            // (API 29+; en API 26-28 no hay loadThumbnail y el hueco
            // se esconde). Es una sola lectura pequeña y solo cambia
            // de pista; si falla, no hay portada, nunca una inventada.
            if (style == WidgetStyle.COVER) {
                val artUri = prefs.getString("art_uri", null)?.let { android.net.Uri.parse(it) }
                val bitmap = if (artUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching {
                        context.contentResolver.loadThumbnail(artUri, Size(144, 144), null)
                    }.getOrNull()
                } else {
                    null
                }
                if (bitmap != null) {
                    views.setViewVisibility(R.id.widget_cover, View.VISIBLE)
                    views.setImageViewBitmap(R.id.widget_cover, bitmap)
                } else {
                    views.setViewVisibility(R.id.widget_cover, View.GONE)
                }
            }
            views.setImageViewResource(
                R.id.widget_play,
                if (playing) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
            )
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
