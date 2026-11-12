package com.aurora.player.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList

/**
 * Notificación (2026-11-11, pedido del usuario): la X para cerrar
 * siempre visible en la vista compacta.
 *
 * Android solo deja 3 botones en la vista compacta. El proveedor de
 * Media3 pone ahí anterior/reproducir/siguiente, y los botones
 * personalizados solo salen al expandir — por eso el usuario solo
 * veía < = > (aleatorio y repetir sí estaban, pero escondidos al
 * desplegar la notificación). Aquí se fuerza la compacta a
 * [anterior, reproducir o pausa, cerrar]: aleatorio y repetir quedan
 * en la vista expandida.
 *
 * Regla de Media3 1.4.1 (DefaultMediaNotificationProvider, verificada
 * en su fuente): si UN botón personalizado pide sitio en la compacta,
 * el valor por defecto se apaga del todo y hay que llenar los 3 huecos
 * (si queda uno vacío, se recorta todo lo que sigue). Por eso se
 * rellenan los 3: anterior (0), reproducir/pausa (1) y cerrar (2).
 */
class AuroraNotificationProvider(context: Context) :
    DefaultMediaNotificationProvider(context) {

    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean,
    ): ImmutableList<CommandButton> {
        val buttons = super.getMediaButtons(
            session, playerCommands, customLayout, showPauseButton,
        )
        val rebuilt = buttons.map { button ->
            val compactIndex = when {
                button.playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> 0
                button.playerCommand == Player.COMMAND_PLAY_PAUSE -> 1
                button.sessionCommand?.customAction == PlaybackService.ACTION_STOP_ALL -> 2
                else -> C.INDEX_UNSET
            }
            if (compactIndex == C.INDEX_UNSET) {
                button
            } else {
                val extras = Bundle(button.extras).apply {
                    putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, compactIndex)
                }
                val builder = CommandButton.Builder(button.icon)
                    .setDisplayName(button.displayName)
                    .setExtras(extras)
                    .setEnabled(button.isEnabled)
                if (button.iconUri != null) {
                    builder.setIconUri(button.iconUri)
                }
                if (button.sessionCommand != null) {
                    builder.setSessionCommand(button.sessionCommand)
                } else {
                    builder.setPlayerCommand(button.playerCommand)
                }
                builder.build()
            }
        }
        return ImmutableList.copyOf(rebuilt)
    }
}
