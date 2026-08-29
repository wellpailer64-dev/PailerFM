package com.pailer.localtune.util

import com.pailer.localtune.R
import java.time.LocalTime

// Compartilhado entre a UI (Compose) e o widget - garante que os dois lugares troquem de
// gif no mesmo horario em vez de terem os limites duplicados e divergindo com o tempo.
enum class DayPeriod(val gifRes: Int) {
    Morning(R.raw.day_morning),
    Afternoon(R.raw.day_afternoon),
    Night(R.raw.day_night),
    ;

    companion object {
        fun current(): DayPeriod = forHour(LocalTime.now().hour)

        fun forHour(hour: Int): DayPeriod = when {
            hour in 6..15 -> Morning
            hour in 16..18 -> Afternoon
            else -> Night
        }
    }
}
