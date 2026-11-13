package com.aurora.player.tags

import java.io.File

/**
 * Escritura segura de etiquetas (paso 2 del slice 6): copiar a
 * temporal → escribir tags en el temporal → reemplazar el original.
 * Si algo falla a mitad, el original queda intacto: la escritura
 * nunca toca el archivo del usuario directamente.
 *
 * Solo trabaja con File (archivos propios, como los de
 * Descargas/AuroraPlayer del slice 4). Los archivos de la
 * biblioteca del usuario se escriben por ContentResolver con
 * MediaStore.createWriteRequest (paso 3, necesita Android); el
 * reescaneo con MediaScannerConnection también va en el paso 3.
 * Bloqueante: llamar desde IO.
 */
object TagWriter {

    /**
     * Copia [source] a un temporal en su mismo directorio (así el
     * reemplazo es un rename en el mismo sistema de archivos).
     * Null si no se puede leer el original.
     */
    fun prepare(source: File): File? {
        if (!source.isFile || !source.canRead()) return null
        val parent = source.parentFile ?: return null
        val ext = source.extension
        val temp = File(parent, ".${source.nameWithoutExtension}.tagtmp-${System.nanoTime()}.$ext")
        return try {
            source.copyTo(temp, overwrite = true)
            temp
        } catch (_: Exception) {
            temp.delete()
            null
        }
    }

    /**
     * Reemplaza [target] con el contenido de [temp]: mueve el
     * original a .bak, renombra el temporal y borra el .bak. Si el
     * rename falla, restaura el original desde el .bak. False si no
     * se pudo (el original queda como estaba).
     *
     * Ahora soporta casos donde [temp] y [target] están en
     * sistemas de archivos diferentes: si renameTo falla, se
     * recurre a una copia de streams.
     */
    fun commit(temp: File, target: File): Boolean {
        if (!temp.isFile || !target.isFile) return false
        val backup = File(target.parentFile, "${target.name}.bak")
        return try {
            if (backup.exists()) backup.delete()
            // Mover el original a .bak
            if (!target.renameTo(backup)) return false

            // Intentar renombrar el temporal al destino
            if (temp.renameTo(target)) {
                // Éxito directo
                backup.delete()
                true
            } else {
                // Fallback: copiar el contenido del temporal al destino
                var copySuccess = false
                try {
                    temp.inputStream().use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    copySuccess = true
                } catch (_: Exception) {
                    // La copia falló
                }

                if (copySuccess) {
                    // Copia exitosa: limpiar temporales y backup
                    temp.delete()
                    backup.delete()
                    true
                } else {
                    // Copia falló: intentar restaurar el backup
                    if (target.exists()) target.delete()
                    backup.renameTo(target)
                    false
                }
            }
        } catch (_: Exception) {
            // En caso de cualquier excepción inesperada, intentar restaurar
            try {
                if (!target.exists() && backup.exists()) backup.renameTo(target)
            } catch (_: Exception) {
                // No se pudo restaurar: el .bak sigue junto al original.
            }
            false
        }
    }

    /** Borra un temporal que ya no hace falta (tras guardar o cancelar). */
    fun discard(temp: File) {
        try {
            temp.delete()
        } catch (_: Exception) {
            // Si no se puede borrar, queda un .tagtmp huérfano y pequeño.
        }
    }
}
