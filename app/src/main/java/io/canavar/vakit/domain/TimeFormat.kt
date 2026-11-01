package io.canavar.vakit.domain

import java.time.LocalDateTime
import java.util.Locale

object TimeFormat {
    fun hhmm(minutes: Int): String =
        String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)

    fun hhmm(at: LocalDateTime): String = hhmm(at.hour * 60 + at.minute)
}
