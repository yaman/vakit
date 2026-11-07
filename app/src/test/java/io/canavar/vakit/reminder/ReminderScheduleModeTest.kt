package io.canavar.vakit.reminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderScheduleModeTest {
    @Test
    fun `api 30 always uses exact alarms`() {
        assertTrue(ReminderScheduleMode.useExact(apiLevel = 30, canScheduleExact = false))
    }

    @Test
    fun `api 31 uses exact only when allowed`() {
        assertFalse(ReminderScheduleMode.useExact(apiLevel = 31, canScheduleExact = false))
        assertTrue(ReminderScheduleMode.useExact(apiLevel = 31, canScheduleExact = true))
    }
}
