# AGENTS.md — Aurora Player

> Lee este archivo antes de tocar código. Está en español simple, a propósito.
> Si algo de aquí contradice al README, manda lo de aquí.

## Qué es esta app

**Aurora Player: nuestro propio reproductor de música, gratis.** Local,
sin anuncios, sin suscripción, sin cuenta, sin internet obligatorio.

- Solo archivos locales del teléfono: MP3, FLAC, WAV, AAC, M4A, OGG,
  OPUS, MKA, ALAC. El modo online (opcional, apagado por defecto)
  añade letras online, YouTube y Spotify; nada de catálogos con
  licencia.
- Biblioteca local (MediaStore), reproducción en segundo plano con
  MediaSession, cola, playlists, favoritos, historial, letras,
  ecualizador, temporizador, estadísticas locales y personalización total.
- Identidad premium: **AMOLED total** (fondo negro puro #000000,
  tarjetas #17171B) + hielo/lavanda/turquesa. Nada genérico.

El usuario **no tiene PC**: todo se compila solo con GitHub Actions.
CI en verde **no** significa probado en el teléfono. Decirlo siempre así.

## Reglas que no se rompen

1. **Nada de APK hasta que todo sea 100% funcional**, código por código
   y función por función. Después de cada run de GitHub Actions: causa
   exacta → arreglo → nuevo run, en bucle. Reportar por arreglo
   verificado (commit + run + artefactos). Nunca "APK lista" con
   errores pendientes.
2. **Cada ajuste hace algo real o se quita.** Nada de adorno. Si un
   ajuste existe en la UI pero no aplica nada (como pasó con ReplayGain),
   es un error: conectarlo a su efecto real o quitarlo.
3. **Privacidad total.** El permiso INTERNET existe SOLO para el
   modo online (letras, YouTube, Spotify) y solo detrás del
   interruptor maestro onlineMode (apagado por defecto; sin él la
   app no toca la red). Sin cuenta, sin analítica, sin rastreo.
   Todo queda solo en el teléfono (Room/DataStore local). El reporte
   de errores nunca lleva nombres, rutas, títulos de canciones,
   tokens ni IDs: rutas a `<ruta>`, nombres a `<archivo>`.
   (Ronda 177, 2026-11-11: esta regla decía "sin permiso de
   internet" pero el manifiesto lo tiene desde la ronda 7 para las
   letras online y el usuario aprobó el modo online después; la
   regla vieja quedó obsoleta, no el permiso.)
4. **El nombre del usuario no aparece en ninguna parte.** Package
   neutro (`com.aurora.player`), ni en pantallas ni en reportes.
5. **No copiar código de otros reproductores.** Gramophone, Auxio,
   Retro Music Player, Phonograph Plus y demás son solo la **lista de
   funciones** como referencia, con código propio. Son GPL-3.0: si se
   usa una librería GPL (FFmpeg/TagLib), se documenta en
   `THIRD-PARTY.md` y se asume la licencia que toque. Código propio:
   Apache-2.0.
6. **Leer el código antes de tocarlo.** Verificar nombres, valores y
   comportamientos reales. No inventar APIs ni funciones. No quitar
   funciones sin consultar. No sustituir implementaciones reales por
   simulaciones.
7. **No mencionar el modelo del teléfono.** Solo Helio G99 / Mali-G57
   si hace falta.
8. **Servicio en primer plano correcto:** `startForeground()` inmediato
   (lección del bug del gamepad `ForegroundServiceDidNotStartInTimeException`).

## Cómo se compila (GitHub Actions, `.github/workflows/android.yml`)

- JDK 17 (temurin), Gradle 8.7. Kotlin + Media3/ExoPlayer, Room,
  DataStore. Dependencias: solo AndroidX, Media3, Room y DataStore.
- Tests: `gradle testDebugUnitTest --stacktrace`
- APK debug: `gradle assembleDebug --stacktrace`
- Artefacto: `aurora-player-apks`. La APK de CI es debug, sin firmar.
- Desarrollo por etapas (ver `PLAN-REPRODUCTOR.md` en el goal): Etapa 1
  auditoría + base, Etapa 2 visual, Etapa 3 biblioteca, Etapa 4
  reproductor, Etapa 5 letras/detalles/estadísticas, Etapa 6
  personalización avanzada, Etapa 7 optimización + tests, Etapa 8 build
  final + entrega. Solo en la Etapa 8 se ofrece APK.

## Subir cambios

- Los archivos de workflow (`.github/workflows/*`) y los binarios (PNG)
  **solo se suben por el navegador** (github.com web UI). La API de
  `push_files` da 403 con ellos.
- `push_files` exige `files` como array de `{path, content}` y `path`
  siempre relativo al repo, nunca la ruta local.
- Verificar siempre el run del commit exacto pusheado antes de reportar.

## Referencias para no inventar

- Documentación al día de librerías (Media3, Room, Compose...):
  **Context7** (ver `~/workspace/custom-skills/context7-docs/SKILL.md`
  en la computadora de la asistente; sin clave, por API REST).
- Ideas de funciones de otros reproductores (solo ideas, nunca código):
  Metrolist (cliente YouTube Music, GPL, en mantenimiento), Gramophone,
  Auxio, Retro Music Player, Phonograph Plus.
- Ejemplos oficiales de Google, clonados en local como consulta:
  `~/workspace/references/nowinandroid`,
  `~/workspace/references/compose-samples`,
  `~/workspace/references/architecture-samples`,
  `~/workspace/references/awesome-android-ui`.
- Specs y checklist del proyecto: `.specify/` (fuera del repo, no se
  pushea). Flujo: constitución → spec → plan → tareas → implementar.
