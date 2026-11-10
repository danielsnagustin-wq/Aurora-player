package com.aurora.player

import com.aurora.player.audio.OutputRoute
import com.aurora.player.audio.classifyOutputRoute
import com.aurora.player.audio.routeKey
import com.aurora.player.audio.routeLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * EQ por salida: la clasificación es pura sobre enteros (en los tests
 * JVM las constantes de AudioDeviceInfo valen 0, así que se usan los
 * valores reales de AOSP documentados en OutputEq.kt: BT A2DP 8,
 * cable 3/4, USB 11/12/22, altavoz 2).
 */
class OutputEqTest {

    @Test
    fun bluetoothGanaSobreAltavoz() {
        assertEquals(OutputRoute.BLUETOOTH, classifyOutputRoute(listOf(2, 8)))
    }

    @Test
    fun cableGanaSobreAltavoz() {
        assertEquals(OutputRoute.WIRED, classifyOutputRoute(listOf(2, 3)))
    }

    @Test
    fun soloAltavoz() {
        assertEquals(OutputRoute.SPEAKER, classifyOutputRoute(listOf(2)))
    }

    @Test
    fun usbSinAltavoz() {
        assertEquals(OutputRoute.USB, classifyOutputRoute(listOf(22)))
    }

    @Test
    fun sinDispositivos_esOtra() {
        assertEquals(OutputRoute.OTHER, classifyOutputRoute(emptyList()))
    }

    @Test
    fun claveBluetooth_limpiaNombre() {
        // El guion se conserva en la clave (solo los espacios y
        // otros signos se vuelven '_'): "Sony WH-1000XM4" da
        // "sony_wh-1000xm4", no "sony_wh_1000xm4".
        assertEquals("bluetooth:sony_wh-1000xm4", routeKey(OutputRoute.BLUETOOTH, "Sony WH-1000XM4"))
        assertEquals("bluetooth", routeKey(OutputRoute.BLUETOOTH, null))
        assertEquals("bluetooth", routeKey(OutputRoute.BLUETOOTH, "   "))
    }

    @Test
    fun claveFijas_sinNombre() {
        assertEquals("speaker", routeKey(OutputRoute.SPEAKER, "Altavoz"))
        assertEquals("wired", routeKey(OutputRoute.WIRED, null))
        assertEquals("usb", routeKey(OutputRoute.USB, "DAC"))
    }

    @Test
    fun etiqueta_conNombre() {
        assertEquals("Bluetooth · Sony XM4", routeLabel(OutputRoute.BLUETOOTH, "Sony XM4"))
        assertEquals("Bluetooth", routeLabel(OutputRoute.BLUETOOTH, null))
        assertEquals("Altavoz del teléfono", routeLabel(OutputRoute.SPEAKER, null))
        assertEquals("Auriculares con cable", routeLabel(OutputRoute.WIRED, null))
    }
}
