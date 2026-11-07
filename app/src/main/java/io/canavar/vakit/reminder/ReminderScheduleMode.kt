package io.canavar.vakit.reminder

object ReminderScheduleMode {
    private const val EXACT_ALARM_API = 31

    fun useExact(apiLevel: Int, canScheduleExact: Boolean): Boolean =
        apiLevel < EXACT_ALARM_API || canScheduleExact
}
