package com.aurora.player.audio

import android.media.audiofx.Visualizer

/**
 * Visualizador real (Fase 2 audio): captura la FFT de la sesión de
 * audio real de ExoPlayer y la convierte en alturas de barras con
 * [fftToBars] + [smoothBars].
 *
 * Sin permiso RECORD_AUDIO: el Visualizer sobre la sesión propia no
 * lo necesita (solo la sesión 0, la mezcla global, lo exige). Si el
 * dispositivo no lo soporta, el constructor lanza y [isAvailable]
 * queda en false: la UI no muestra nada, no se finge animación.
 * Se libera con [release] al cambiar de sesión o al parar el
 * servicio; sin liberar, el Visualizer se queda enganchado.
 */
class VisualizerCapture(
    sessionId: Int,
    private val barCount: Int = 32,
    private val onBars: (List<Float>) -> Unit,
) {
    private var visualizer: Visualizer? = null
    private var previous: List<Float> = List(barCount) { 0f }

    /** true solo si la captura real arrancó en este dispositivo. */
    val isAvailable: Boolean

    init {
        var viz: Visualizer? = null
        try {
            if (sessionId != 0) {
                viz = Visualizer(sessionId)
                viz.captureSize = Visualizer.getCaptureSizeRange()[1]
                viz.setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int,
                        ) {
                            // Solo FFT: la forma de onda no se dibuja.
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int,
                        ) {
                            val data = fft ?: return
                            val bars = smoothBars(previous, fftToBars(data, barCount))
                            previous = bars
                            onBars(bars)
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    false,
                    true,
                )
                viz.enabled = true
            }
        } catch (_: Exception) {
            viz?.release()
            viz = null
        }
        visualizer = viz
        isAvailable = viz != null
    }

    fun release() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {
        }
        visualizer = null
        previous = List(barCount) { 0f }
    }
}
