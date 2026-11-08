# Aurora Player

**Nuestro reproductor de música gratis. Local, sin anuncios, sin suscripción, sin cuenta, sin internet.**

> *Hagamos este reproductor con amor a la música.*
> *El amor por la música no tiene que tener precio.*

Aurora Player reproduce **todos tus archivos de música** en tu teléfono: MP3, FLAC, WAV, AAC, M4A, OGG, OPUS, MKA, ALAC. Nada de streaming, nada de catálogos con licencia: tus archivos, tu música.

## Lo que tiene (Etapa 1)

- Biblioteca local con MediaStore: canciones, artistas, álbumes, carpetas, búsqueda.
- Reproducción en segundo plano con MediaSession (notificación con controles).
- Cola, shuffle, repeat, velocidad 0.5x–2.0x, gapless, crossfade (OFF por defecto).
- Playlists + favoritos + historial, exportar/importar M3U.
- Ecualizador propio de 10 bandas + presets reales (Plano, Rock, Pop, Jazz, Clásica, Graves, Agudos, Vocal), bass boost y virtualizer — con aviso honesto si el dispositivo no lo soporta.
- Temporizador de apagado.
- Tema AMOLED total (negro puro #000000, tarjetas #111111) + personalización al 100%: todos los colores (acento, fondo, tarjetas, texto, botones, barra, visualizador) con paleta + HEX + presets, vista previa antes de aplicar y restablecer por defecto.

## Lo que viene (Fase 1b / 2)

Letras sincronizadas LRC/TTML/SRT + karaoke, ReplayGain 2.0, estadísticas reales, gestos, auto-pausa con auriculares, reanudar donde quedó, cola persistente, carpetas incluidas/excluidas, duplicados, widgets, Android Auto, editor de etiquetas, backup/restore, alarma. Ver `PLAN-REPRODUCTOR.md` en el goal (tabla fuente → función → fase).

## Referentes open source (solo la lista de funciones, código propio)

Gramophone, Auxio, Retro Music Player, Phonograph Plus (todos GPL-3.0). No se copia su código. Si en Fase 1b se usa una librería GPL (FFmpeg/TagLib), se documenta en `THIRD-PARTY.md`.

## Privacidad — 100% libre, no guardamos tus datos

Principio del proyecto: **100% libre, y no guardamos los datos de las personas que la usen, mucho menos en un reporte de errores.**

- **Código abierto, sin rastreo:** sin SDKs de rastreo, sin analítica, sin anuncios, sin telemetría. Dependencias: solo AndroidX, Media3, Room y DataStore.
- **Sin permiso de internet.** Sin cuenta. Sin anuncios.
- **Sin guardar datos de quien la usa:** sin nombre, sin email, sin identificadores de dispositivo. Todo (playlists, favoritos, historial, estadísticas, ajustes) queda **solo en tu teléfono** (Room/DataStore local) y se borra con "Borrar datos" o al desinstalar. Las estadísticas son locales y nunca salen del teléfono.
- **Permisos mínimos:** audio + notificación. Nada de contactos, ubicación ni teléfono.
- **Sin nombre personal** en el package (`com.aurora.player`), ni en pantallas, ni en reportes.
- **Reporte de errores sanitizado por diseño:** si exportas un reporte, solo lleva versión de la app, versión de Android, tipo de error y stack trace sin rutas. Nunca rutas de archivos, títulos de canciones, carpetas personales, tokens ni IDs: antes de exportar, cualquier ruta pasa a `<ruta>` y cualquier nombre de archivo a `<archivo>` (`util/BugReport.kt`, con test `BugReportTest.kt` que lo verifica).
- **Online opcional (Fase 2, YouTube/SoundCloud):** solo si tú lo activas. El token queda solo en tu teléfono, cifrado, nunca en logs ni reportes, y al desconectar se borra todo.

## Build

GitHub Actions (`android.yml`): tests unitarios + APK debug. La APK de CI es debug, sin firmar; CI verde ≠ probado en teléfono.

## Licencia

Código propio: Apache-2.0 (propuesta). Media3: Apache-2.0.
