package com.aurora.player

import com.aurora.player.alarm.nextAlarmTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class MusicAlarmTest {

    private fun withZone(zoneId: String, block: () -> Unit) {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
            block()
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun proximaAlarma_mismoDia() {
        val now = millis(2026, Calendar.FEBRUARY, 10, 6, 0)
        assertEquals(
            millis(2026, Calendar.FEBRUARY, 10, 7, 30),
            nextAlarmTime(7, 30, now),
        )
    }

    @Test
    fun proximaAlarma_manana() {
        val now = millis(2026, Calendar.FEBRUARY, 10, 8, 0)
        assertEquals(
            millis(2026, Calendar.FEBRUARY, 11, 7, 30),
            nextAlarmTime(7, 30, now),
        )
    }

    @Test
    fun proximaAlarma_adelantoHorario_noSeDesplaza() {
        // EEUU adelanta el reloj el 2026-03-08 (día de 23 h): la alarma
        // de las 07:30 debe seguir sonando a las 07:30 del día siguiente,
        // no a las 08:30 (ronda 13).
        withZone("America/New_York") {
            val now = millis(2026, Calendar.MARCH, 8, 8, 0)
            assertEquals(
                millis(2026, Calendar.MARCH, 9, 7, 30),
                nextAlarmTime(7, 30, now),
            )
        }
    }

    @Test
    fun proximaAlarma_atrasoHorario_noSeDesplaza() {
        // EEUU atrasa el reloj el 2026-11-01 (día de 25 h): la alarma
        // no debe sonar a las 06:30 del día siguiente (ronda 13).
        withZone("America/New_York") {
            val now = millis(2026, Calendar.NOVEMBER, 1, 8, 0)
            assertEquals(
                millis(2026, Calendar.NOVEMBER, 2, 7, 30),
                nextAlarmTime(7, 30, now),
            )
        }
    }
}
