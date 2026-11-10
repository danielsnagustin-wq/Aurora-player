package com.aurora.player.data

import com.aurora.player.model.CardStyle
import com.aurora.player.model.CoverShape
import com.aurora.player.model.ListDensity
import com.aurora.player.model.PerformanceProfile
import com.aurora.player.model.PlayerBackground
import com.aurora.player.model.PlayerSettings
import com.aurora.player.model.StreamQuality
import com.aurora.player.model.TextSize
import com.aurora.player.model.ThemeMode
import com.aurora.player.model.ThemeSettings
import com.aurora.player.online.JsonParseException
import com.aurora.player.online.JsonParser
import com.aurora.player.online.JsonValue
import com.aurora.player.online.arr
import com.aurora.player.online.bool
import com.aurora.player.online.double
import com.aurora.player.online.long
import com.aurora.player.online.obj
import com.aurora.player.online.str
import com.aurora.player.util.toJsonString

/**
 * Aurora Player — respaldo local (SPEC 005, FR-004). Lógica pura.
 *
 * Un respaldo es UN archivo JSON con: listas, favoritos, historial,
 * ajustes de tema y de reproductor, cola y presets de EQ propios.
 * Cada canción se guarda con su id de MediaStore Y su ruta: al
 * restaurar se busca por id (mismo teléfono) y si no por ruta (los
 * mismos archivos en otro teléfono). Lo que no aparece se lista como
 * ausente; nunca se inventa una canción. Sin títulos ni nombres en
 * el archivo: solo ids y rutas, que son del propio usuario.
 *
 * Todo lo de aquí es puro y testeable en la JVM (sin Android): la
 * parte de archivos (MediaStore/SAF) vive en util/BackupManager.
 */

const val BACKUP_APP_NAME = "Aurora Player"
const val BACKUP_VERSION = 1

data class BackupSongRef(val id: Long, val path: String)
data class BackupPlaylist(
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val songs: List<BackupSongRef>,
)
data class BackupHistoryEntry(
    val song: BackupSongRef,
    val playedAt: Long,
    val durationListenedMs: Long,
)
data class BackupQueue(
    val songs: List<BackupSongRef>,
    val index: Int,
    val positionMs: Long,
)
data class BackupEqPreset(val name: String, val levelsDb: List<Float>)

data class BackupData(
    val createdAtMs: Long,
    val playlists: List<BackupPlaylist>,
    val favorites: List<BackupSongRef>,
    val history: List<BackupHistoryEntry>,
    val theme: ThemeSettings,
    val player: PlayerSettings,
    val queue: BackupQueue,
    val eqPresets: List<BackupEqPreset>,
)

/** Resultado de validar un texto de respaldo. */
sealed class BackupValidation {
    data class Valid(val data: BackupData) : BackupValidation()
    data class Invalid(val reason: String) : BackupValidation()
}

// ---------- Escritura: datos -> JsonValue ----------

private fun refToJson(r: BackupSongRef): JsonValue.Obj = JsonValue.Obj(
    linkedMapOf(
        "id" to JsonValue.Num(r.id.toDouble()),
        "path" to JsonValue.Str(r.path),
    ),
)

fun themeSettingsToJsonValue(t: ThemeSettings): JsonValue.Obj = JsonValue.Obj(
    linkedMapOf(
        "themeMode" to JsonValue.Str(t.themeMode.name),
        "amoledWhenSystemDark" to JsonValue.Bool(t.amoledWhenSystemDark),
        "accentHex" to JsonValue.Str(t.accentHex),
        "backgroundHex" to JsonValue.Str(t.backgroundHex),
        "cardHex" to JsonValue.Str(t.cardHex),
        "textHex" to JsonValue.Str(t.textHex),
        "secondaryTextHex" to JsonValue.Str(t.secondaryTextHex),
        "buttonHex" to JsonValue.Str(t.buttonHex),
        "playerBarHex" to JsonValue.Str(t.playerBarHex),
        "visualizerHex" to JsonValue.Str(t.visualizerHex),
        "useMaterialYou" to JsonValue.Bool(t.useMaterialYou),
        "textSize" to JsonValue.Str(t.textSize.name),
        "coverShape" to JsonValue.Str(t.coverShape.name),
        "listDensity" to JsonValue.Str(t.listDensity.name),
        "dynamicColor" to JsonValue.Bool(t.dynamicColor),
        "cornerRadiusDp" to JsonValue.Num(t.cornerRadiusDp.toDouble()),
        "cardStyle" to JsonValue.Str(t.cardStyle.name),
        "playerBackground" to JsonValue.Str(t.playerBackground.name),
        "albumGrid" to JsonValue.Bool(t.albumGrid),
        "albumGridColumns" to JsonValue.Num(t.albumGridColumns.toDouble()),
        "performanceProfile" to JsonValue.Str(t.performanceProfile.name),
        "animationsEnabled" to JsonValue.Bool(t.animationsEnabled),
    ),
)

private fun playerToJson(p: PlayerSettings): JsonValue.Obj = JsonValue.Obj(
    linkedMapOf(
        "crossfadeSeconds" to JsonValue.Num(p.crossfadeSeconds.toDouble()),
        "crossfadeGaplessAlbumException" to JsonValue.Bool(p.crossfadeGaplessAlbumException),
        "playbackSpeed" to JsonValue.Num(p.playbackSpeed.toDouble()),
        "playbackPitch" to JsonValue.Num(p.playbackPitch.toDouble()),
        "skipSilence" to JsonValue.Bool(p.skipSilence),
        "replayGainMode" to JsonValue.Str(p.replayGainMode),
        "replayGainPreAmpDb" to JsonValue.Num(p.replayGainPreAmpDb.toDouble()),
        "channelBalance" to JsonValue.Num(p.channelBalance.toDouble()),
        "audioOutputMode" to JsonValue.Str(p.audioOutputMode),
        "autoPauseOnDisconnect" to JsonValue.Bool(p.autoPauseOnDisconnect),
        "autoplayOnConnect" to JsonValue.Bool(p.autoplayOnConnect),
        "minDurationMs" to JsonValue.Num(p.minDurationMs.toDouble()),
        "resumeWhereLeft" to JsonValue.Bool(p.resumeWhereLeft),
        "localRadio" to JsonValue.Bool(p.localRadio),
        "onlineMode" to JsonValue.Bool(p.onlineMode),
        "streamQuality" to JsonValue.Str(p.streamQuality.name),
        "swipeToChangeTrack" to JsonValue.Bool(p.swipeToChangeTrack),
        "doubleTapToFavorite" to JsonValue.Bool(p.doubleTapToFavorite),
        "shakeToNext" to JsonValue.Bool(p.shakeToNext),
        "volumeGesture" to JsonValue.Bool(p.volumeGesture),
    ),
)

fun BackupData.toJsonValue(): JsonValue.Obj = JsonValue.Obj(
    linkedMapOf(
        "app" to JsonValue.Str(BACKUP_APP_NAME),
        "version" to JsonValue.Num(BACKUP_VERSION.toDouble()),
        "createdAtMs" to JsonValue.Num(createdAtMs.toDouble()),
        "playlists" to JsonValue.Arr(playlists.map { pl ->
            JsonValue.Obj(
                linkedMapOf(
                    "name" to JsonValue.Str(pl.name),
                    "createdAt" to JsonValue.Num(pl.createdAt.toDouble()),
                    "updatedAt" to JsonValue.Num(pl.updatedAt.toDouble()),
                    "songs" to JsonValue.Arr(pl.songs.map(::refToJson)),
                ),
            )
        }),
        "favorites" to JsonValue.Arr(favorites.map(::refToJson)),
        "history" to JsonValue.Arr(history.map { h ->
            JsonValue.Obj(
                linkedMapOf(
                    "song" to refToJson(h.song),
                    "playedAt" to JsonValue.Num(h.playedAt.toDouble()),
                    "durationListenedMs" to JsonValue.Num(h.durationListenedMs.toDouble()),
                ),
            )
        }),
        "theme" to themeSettingsToJsonValue(theme),
        "player" to playerToJson(player),
        "queue" to JsonValue.Obj(
            linkedMapOf(
                "songs" to JsonValue.Arr(queue.songs.map(::refToJson)),
                "index" to JsonValue.Num(queue.index.toDouble()),
                "positionMs" to JsonValue.Num(queue.positionMs.toDouble()),
            ),
        ),
        "eqPresets" to JsonValue.Arr(eqPresets.map { preset ->
            JsonValue.Obj(
                linkedMapOf(
                    "name" to JsonValue.Str(preset.name),
                    "levelsDb" to JsonValue.Arr(preset.levelsDb.map { JsonValue.Num(it.toDouble()) }),
                ),
            )
        }),
    ),
)

fun BackupData.toJsonString(): String = toJsonValue().toJsonString()

// ---------- Lectura: JsonValue -> datos (tolerante) ----------

private fun refFromJson(v: JsonValue?): BackupSongRef? {
    val id = v.obj("id").long() ?: return null
    val path = v.obj("path").str() ?: return null
    return BackupSongRef(id, path)
}

private fun <T : Enum<T>> enumFrom(name: String?, values: Array<T>, default: T): T =
    values.find { it.name == name } ?: default

fun themeSettingsFromJsonValue(v: JsonValue?): ThemeSettings {
    val d = ThemeSettings.DEFAULT
    val hex = { key: String, def: String ->
        ThemeSettings.normalizeHex(v.obj(key).str() ?: def) ?: def
    }
    return d.copy(
        themeMode = enumFrom(v.obj("themeMode").str(), ThemeMode.entries.toTypedArray(), d.themeMode),
        amoledWhenSystemDark = v.obj("amoledWhenSystemDark").bool() ?: d.amoledWhenSystemDark,
        accentHex = hex("accentHex", d.accentHex),
        backgroundHex = hex("backgroundHex", d.backgroundHex),
        cardHex = hex("cardHex", d.cardHex),
        textHex = hex("textHex", d.textHex),
        secondaryTextHex = hex("secondaryTextHex", d.secondaryTextHex),
        buttonHex = hex("buttonHex", d.buttonHex),
        playerBarHex = hex("playerBarHex", d.playerBarHex),
        visualizerHex = hex("visualizerHex", d.visualizerHex),
        useMaterialYou = v.obj("useMaterialYou").bool() ?: d.useMaterialYou,
        textSize = enumFrom(v.obj("textSize").str(), TextSize.entries.toTypedArray(), d.textSize),
        coverShape = enumFrom(v.obj("coverShape").str(), CoverShape.entries.toTypedArray(), d.coverShape),
        listDensity = enumFrom(v.obj("listDensity").str(), ListDensity.entries.toTypedArray(), d.listDensity),
        dynamicColor = v.obj("dynamicColor").bool() ?: d.dynamicColor,
        cornerRadiusDp = (v.obj("cornerRadiusDp").long()?.toInt() ?: d.cornerRadiusDp).coerceIn(0, 28),
        cardStyle = enumFrom(v.obj("cardStyle").str(), CardStyle.entries.toTypedArray(), d.cardStyle),
        playerBackground = enumFrom(v.obj("playerBackground").str(), PlayerBackground.entries.toTypedArray(), d.playerBackground),
        albumGrid = v.obj("albumGrid").bool() ?: d.albumGrid,
        albumGridColumns = (v.obj("albumGridColumns").long()?.toInt() ?: d.albumGridColumns).coerceIn(1, 4),
        performanceProfile = enumFrom(v.obj("performanceProfile").str(), PerformanceProfile.entries.toTypedArray(), d.performanceProfile),
        animationsEnabled = v.obj("animationsEnabled").bool() ?: d.animationsEnabled,
    )
}

private fun playerFromJson(v: JsonValue?): PlayerSettings {
    val d = PlayerSettings.DEFAULT
    return d.copy(
        crossfadeSeconds = (v.obj("crossfadeSeconds").long()?.toInt() ?: d.crossfadeSeconds).coerceIn(0, 12),
        crossfadeGaplessAlbumException = v.obj("crossfadeGaplessAlbumException").bool() ?: d.crossfadeGaplessAlbumException,
        playbackSpeed = (v.obj("playbackSpeed").double()?.toFloat() ?: d.playbackSpeed).coerceIn(0.5f, 2.0f),
        playbackPitch = (v.obj("playbackPitch").double()?.toFloat() ?: d.playbackPitch).coerceIn(0.5f, 2.0f),
        skipSilence = v.obj("skipSilence").bool() ?: d.skipSilence,
        replayGainMode = (v.obj("replayGainMode").str() ?: d.replayGainMode)
            .takeIf { it in setOf("off", "track", "album", "smart") } ?: "off",
        replayGainPreAmpDb = (v.obj("replayGainPreAmpDb").double()?.toFloat() ?: d.replayGainPreAmpDb).coerceIn(-12f, 12f),
        channelBalance = (v.obj("channelBalance").double()?.toFloat() ?: d.channelBalance).coerceIn(-1f, 1f),
        audioOutputMode = (v.obj("audioOutputMode").str() ?: d.audioOutputMode)
            .takeIf { it in setOf("standard", "direct") } ?: "standard",
        autoPauseOnDisconnect = v.obj("autoPauseOnDisconnect").bool() ?: d.autoPauseOnDisconnect,
        autoplayOnConnect = v.obj("autoplayOnConnect").bool() ?: d.autoplayOnConnect,
        minDurationMs = v.obj("minDurationMs").long() ?: d.minDurationMs,
        resumeWhereLeft = v.obj("resumeWhereLeft").bool() ?: d.resumeWhereLeft,
        localRadio = v.obj("localRadio").bool() ?: d.localRadio,
        onlineMode = v.obj("onlineMode").bool() ?: d.onlineMode,
        streamQuality = enumFrom(v.obj("streamQuality").str(), StreamQuality.entries.toTypedArray(), d.streamQuality),
        swipeToChangeTrack = v.obj("swipeToChangeTrack").bool() ?: d.swipeToChangeTrack,
        doubleTapToFavorite = v.obj("doubleTapToFavorite").bool() ?: d.doubleTapToFavorite,
        shakeToNext = v.obj("shakeToNext").bool() ?: d.shakeToNext,
        volumeGesture = v.obj("volumeGesture").bool() ?: d.volumeGesture,
    )
}

/** JsonValue -> BackupData, o null si la estructura está rota. */
fun backupFromJsonValue(root: JsonValue?): BackupData? {
    if (root !is JsonValue.Obj) return null
    val createdAt = root.obj("createdAtMs").long() ?: return null
    val playlists = root.obj("playlists").arr() ?: return null
    val favorites = root.obj("favorites").arr() ?: return null
    val history = root.obj("history").arr() ?: return null
    val queueObj = root.obj("queue") ?: return null
    val presets = root.obj("eqPresets").arr() ?: return null
    return BackupData(
        createdAtMs = createdAt,
        playlists = playlists.mapNotNull { pl ->
            val name = pl.obj("name").str() ?: return@mapNotNull null
            val songs = pl.obj("songs").arr()?.mapNotNull(::refFromJson) ?: return@mapNotNull null
            BackupPlaylist(
                name = name,
                createdAt = pl.obj("createdAt").long() ?: 0L,
                updatedAt = pl.obj("updatedAt").long() ?: 0L,
                songs = songs,
            )
        },
        favorites = favorites.mapNotNull(::refFromJson),
        history = history.mapNotNull { h ->
            val song = refFromJson(h.obj("song")) ?: return@mapNotNull null
            BackupHistoryEntry(
                song = song,
                playedAt = h.obj("playedAt").long() ?: return@mapNotNull null,
                durationListenedMs = h.obj("durationListenedMs").long() ?: 0L,
            )
        },
        theme = themeSettingsFromJsonValue(root.obj("theme")),
        player = playerFromJson(root.obj("player")),
        queue = BackupQueue(
            songs = queueObj.obj("songs").arr()?.mapNotNull(::refFromJson) ?: emptyList(),
            index = queueObj.obj("index").long()?.toInt() ?: 0,
            positionMs = queueObj.obj("positionMs").long() ?: 0L,
        ),
        eqPresets = presets.mapNotNull { p ->
            val name = p.obj("name").str() ?: return@mapNotNull null
            val levels = p.obj("levelsDb").arr()?.mapNotNull { it.double()?.toFloat() } ?: return@mapNotNull null
            BackupEqPreset(name, levels)
        },
    )
}

// ---------- Validación ----------

/**
 * Valida el texto de un archivo de respaldo. Devuelve el dato listo
 * para restaurar, o el motivo exacto por el que no vale (nunca se
 * restaura nada de un archivo inválido).
 */
fun validateBackup(text: String): BackupValidation {
    val root = try {
        JsonParser.parse(text)
    } catch (e: JsonParseException) {
        return BackupValidation.Invalid("El archivo no es un JSON válido (${e.message})")
    }
    if (root !is JsonValue.Obj) {
        return BackupValidation.Invalid("El archivo no es un respaldo (no es un objeto JSON)")
    }
    val app = root.obj("app").str()
    if (app != BACKUP_APP_NAME) {
        return BackupValidation.Invalid("Este archivo no es un respaldo de Aurora Player")
    }
    val version = root.obj("version").long()
    if (version != BACKUP_VERSION.toLong()) {
        return BackupValidation.Invalid("Versión de respaldo no compatible (versión $version, se esperaba $BACKUP_VERSION)")
    }
    val data = backupFromJsonValue(root)
        ?: return BackupValidation.Invalid("Al respaldo le faltan datos (listas, favoritos, historial, ajustes o cola)")
    return BackupValidation.Valid(data)
}

// ---------- Emparejar canciones con la biblioteca ----------

/**
 * Empareja referencias del respaldo con la biblioteca actual
 * (pares id + ruta, sin tipos de Android para poder testearlo).
 * Primero por id, luego por ruta. Devuelve (encontradas, ausentes).
 */
fun matchSongs(
    refs: List<BackupSongRef>,
    library: List<Pair<Long, String>>,
): Pair<List<Pair<Long, String>>, List<BackupSongRef>> {
    val byId = library.associateBy { it.first }
    val byPath = library.associateBy { it.second }
    val found = mutableListOf<Pair<Long, String>>()
    val missing = mutableListOf<BackupSongRef>()
    for (ref in refs) {
        val hit = byId[ref.id] ?: byPath[ref.path]
        if (hit != null) found += hit else missing += ref
    }
    return found to missing
}
