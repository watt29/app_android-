package com.example.nongkanvelaassistant.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class InternalResponseTextTest {
    @Test
    fun hidesUnknownActionMarkerBeforeSpeakingOrDisplayingResponse() {
        assertEquals(
            "กำลังช่วยตรวจสอบให้ค่ะ",
            hideInternalActionMarkers("[ACTION:UNRECOGNIZED:details] กำลังช่วยตรวจสอบให้ค่ะ")
        )
    }

    @Test
    fun hidesUnclosedActionMarker() {
        assertEquals("", hideInternalActionMarkers("[ACTION:UNRECOGNIZED:details"))
    }

    @Test
    fun routesReminderListQuestionToLocalStorageInsteadOfAi() {
        org.junit.Assert.assertTrue(requiresLocalReminderHandling("มีรายการเตือนอะไรบ้าง"))
    }
}
