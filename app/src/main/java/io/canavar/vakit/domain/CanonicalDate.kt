package io.canavar.vakit.domain

import java.time.LocalDate
import java.time.MonthDay

fun canonicalMonthDay(date: LocalDate): MonthDay =
    if (date.monthValue == 2 && date.dayOfMonth == 29) {
        MonthDay.of(2, 28)
    } else {
        MonthDay.of(date.monthValue, date.dayOfMonth)
    }
