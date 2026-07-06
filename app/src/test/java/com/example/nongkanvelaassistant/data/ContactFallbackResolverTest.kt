package com.example.nongkanvelaassistant.data

import com.example.nongkanvelaassistant.ui.DeviceContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactFallbackResolverTest {
    @Test
    fun extractPhoneNumber_normalizesSpacesAndHyphens() {
        val number = ContactFallbackResolver.extractPhoneNumber("โทร 081 234-5678 ให้หน่อย")
        assertEquals("0812345678", number)
    }

    @Test
    fun findBestMatchingContact_matchesThaiPrefixAndKinship() {
        val contacts = listOf(
            DeviceContact(1, 1, "แม่", "0812345678"),
            DeviceContact(2, 2, "สมชาย เล็ก", "0821111111")
        )

        val best = ContactFallbackResolver.findBestMatchingContact("โทรหาคุณแม่", contacts)
        assertEquals("แม่", best?.name)
    }

    @Test
    fun findTopMatchingContacts_marksSimilarNamesAsAmbiguous() {
        val contacts = listOf(
            DeviceContact(1, 1, "สมชาย เล็ก", "0811111111"),
            DeviceContact(2, 2, "สมชาย ใหญ่", "0822222222"),
            DeviceContact(3, 3, "สมศรี", "0833333333")
        )

        val matches = ContactFallbackResolver.findTopMatchingContacts("สมชาย", contacts, limit = 3)
        assertEquals(2, matches.size)
        assertTrue(ContactFallbackResolver.isLikelyAmbiguous(matches))
        assertEquals("สมชาย เล็ก", matches.first().contact.name)
    }
}
