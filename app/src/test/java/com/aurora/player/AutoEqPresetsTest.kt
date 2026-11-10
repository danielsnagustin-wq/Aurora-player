package com.aurora.player

import com.aurora.player.audio.AUTO_EQ_MODELS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AutoEq: cada modelo trae exactamente 10 bandas y todas dentro del
 * rango real del EQ (-15..+15 dB). Los valores vienen de los
 * FixedBandEQ oficiales del proyecto AutoEq (oratory1990).
 */
class AutoEqPresetsTest {

    @Test
    fun todosLosModelos_tienen10Bandas_enRango() {
        assertTrue(AUTO_EQ_MODELS.size >= 10)
        for (model in AUTO_EQ_MODELS) {
            assertEquals("Modelo ${model.name}", 10, model.levelsDb.size)
            for (db in model.levelsDb) {
                assertTrue("Modelo ${model.name}: $db fuera de rango", db in -15f..15f)
            }
        }
    }

    @Test
    fun nombresSinDuplicados() {
        val names = AUTO_EQ_MODELS.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun modelosPopulares_presentes() {
        val names = AUTO_EQ_MODELS.map { it.name }
        assertTrue(names.contains("Sony WH-1000XM4"))
        assertTrue(names.contains("Samsung Galaxy Buds2 Pro"))
        assertTrue(names.contains("Sennheiser HD 600"))
    }
}
