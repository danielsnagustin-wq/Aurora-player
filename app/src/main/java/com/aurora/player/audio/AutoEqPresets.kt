package com.aurora.player.audio

/**
 * AutoEq (Fase 2 audio): correcciones de 10 bandas medidas para
 * auriculares concretos, del proyecto AutoEq de Jaakko Pasanen
 * (github.com/jaakkopasanen/AutoEq, mediciones oratory1990).
 *
 * Los valores son los FixedBandEQ oficiales de cada modelo (bandas
 * 31/62/125/250/500/1000/2000/4000/8000/16000 Hz, Q 1.41), leídos
 * tal cual de results/oratory1990 — no inventados. Se aplican en el
 * orden de las bandas del ecualizador del sistema: en los teléfonos
 * las frecuencias centrales reales no son exactamente esas, así que
 * la corrección es una aproximación honesta, no una medida exacta
 * del aparato. La UI lo dice así.
 *
 * Como cualquier preset, al tocar un modelo se enciende el EQ y se
 * guarda; si el EQ por salida está activado, se guarda para la
 * salida actual.
 */
data class AutoEqModel(val name: String, val levelsDb: List<Float>)

val AUTO_EQ_MODELS: List<AutoEqModel> = listOf(

    AutoEqModel("Sony WH-1000XM4", listOf(-4.3f, -1.8f, -5.8f, -1.4f, 0.5f, -0.6f, 6.0f, -0.8f, 1.0f, -2.2f)),
    AutoEqModel("Sony WH-1000XM5", listOf(-3.7f, -1.6f, -5.2f, -3.3f, 1.3f, 1.8f, 5.3f, -2.4f, 1.3f, 9.0f)),
    AutoEqModel("Bose QuietComfort 45", listOf(-1.4f, -1.2f, -2.1f, -0.9f, 0.7f, 2.0f, -0.9f, -1.3f, 1.0f, -10.0f)),
    AutoEqModel("Sennheiser HD 600", listOf(6.9f, 3.3f, -1.1f, -1.6f, 0.6f, -0.8f, 0.1f, -1.0f, 3.9f, -6.5f)),
    AutoEqModel("Sennheiser HD 650", listOf(7.0f, 2.6f, -1.2f, -2.2f, 0.5f, -1.0f, 0.7f, -1.2f, 5.1f, -3.6f)),
    AutoEqModel("Beyerdynamic DT 770 Pro", listOf(-4.1f, -1.4f, -1.8f, 2.6f, -0.4f, -0.5f, 0.1f, 3.2f, -4.1f, -10.2f)),
    AutoEqModel("Audio-Technica ATH-M50x", listOf(-0.0f, 0.4f, -5.3f, 0.7f, 1.9f, -1.0f, 0.6f, 0.4f, 2.8f, -9.3f)),
    AutoEqModel("AKG K371", listOf(-3.0f, 1.2f, -1.4f, -1.8f, 0.8f, -0.8f, -0.3f, 3.0f, -0.2f, 4.8f)),
    AutoEqModel("Anker Soundcore Life Q30 (ANC on)", listOf(-4.4f, -5.4f, -6.3f, 1.0f, -2.0f, 3.2f, -1.2f, 5.2f, -1.1f, -12.0f)),
    AutoEqModel("Apple AirPods Max", listOf(-4.1f, -0.9f, -1.6f, -2.1f, -0.4f, -2.9f, 1.6f, 2.9f, 4.3f, -10.6f)),
    AutoEqModel("Samsung Galaxy Buds2 Pro", listOf(-2.4f, 0.5f, -0.3f, -0.7f, 0.0f, 0.2f, 0.6f, -1.9f, 2.6f, -7.9f)),
    AutoEqModel("Samsung Galaxy Buds Pro", listOf(0.2f, 0.0f, -2.3f, -2.2f, -0.1f, 2.7f, 1.7f, 0.7f, -1.4f, -10.5f)),
    AutoEqModel("Sony WF-1000XM4", listOf(-0.8f, -1.2f, -3.4f, -3.3f, -1.8f, -0.5f, 1.0f, 1.6f, 6.4f, 10.8f)),
    AutoEqModel("Sony WF-1000XM5", listOf(2.0f, 0.1f, -2.2f, -1.9f, 1.0f, 1.2f, -1.5f, 1.5f, 0.9f, 4.8f)),
    AutoEqModel("Sennheiser IE 200", listOf(0.3f, 1.3f, -0.5f, -1.1f, 0.7f, -0.5f, -1.8f, 5.0f, -1.5f, -11.2f)),
    AutoEqModel("Moondrop Aria", listOf(-0.6f, 0.4f, -2.4f, -1.7f, 1.0f, 0.5f, -0.3f, 1.0f, 2.9f, -10.9f)),
)
