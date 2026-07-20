package com.example.nongkanvelaassistant.data

import com.example.nongkanvelaassistant.ui.DeviceContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmergencyContactResolverTest {
    @Test
    fun resolvesFamilyContactByConfiguredPriority() {
        val contacts = listOf(
            DeviceContact(1, 1, "ลูกชาย", "0811111111"),
            DeviceContact(2, 2, "แม่", "0822222222"),
            DeviceContact(3, 3, "ผู้ดูแลหลัก", "0833333333")
        )

        assertEquals("ผู้ดูแลหลัก", EmergencyContactResolver.resolve(contacts)?.name)
    }

    @Test
    fun returnsNullWhenNoFamilyContactExists() {
        val contacts = listOf(DeviceContact(1, 1, "สมชาย", "0811111111"))

        assertNull(EmergencyContactResolver.resolve(contacts))
    }
}
