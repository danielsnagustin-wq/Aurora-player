package com.aurora.player

import android.bluetooth.BluetoothA2dp
import android.content.Intent
import com.aurora.player.playback.isAudioOutputConnected
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadphoneAutoplayTest {

    @Test
    fun `headset plug state 1 connects`() {
        assertTrue(isAudioOutputConnected(Intent.ACTION_HEADSET_PLUG, 1, -1))
    }

    @Test
    fun `headset plug state 0 disconnects`() {
        assertFalse(isAudioOutputConnected(Intent.ACTION_HEADSET_PLUG, 0, -1))
    }

    @Test
    fun `a2dp connected`() {
        assertTrue(
            isAudioOutputConnected(
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                0,
                BluetoothA2dp.STATE_CONNECTED,
            ),
        )
    }

    @Test
    fun `a2dp disconnected does not connect`() {
        assertFalse(
            isAudioOutputConnected(
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                0,
                BluetoothA2dp.STATE_DISCONNECTED,
            ),
        )
    }

    @Test
    fun `other actions never connect`() {
        assertFalse(
            isAudioOutputConnected(
                "android.bluetooth.device.action.ACL_CONNECTED",
                1,
                BluetoothA2dp.STATE_CONNECTED,
            ),
        )
    }

    @Test
    fun `null action never connects`() {
        assertFalse(isAudioOutputConnected(null, 1, BluetoothA2dp.STATE_CONNECTED))
    }
}
