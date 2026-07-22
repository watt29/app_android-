package com.example.nongkanvelaassistant.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalAssistantBotControlVerbTest {
    private fun command(text: String): String? = LocalAssistantBot().handle(
        text = text,
        contacts = emptyList(),
        apps = emptyList(),
        searchContacts = { emptyList() },
        updateContact = { _, _, _ -> "" },
        deleteContact = { "" },
        createContact = { _, _ -> "" },
        readLatestMessage = { "" },
        getBatteryStatus = { "" },
        getCurrentTimeText = { "" },
        getTodayDateText = { "" },
        consumePendingConfirmation = { null },
        getEmergencyCallResponse = { "" }
    )

    @Test
    fun cancelAllAlarmsIsNeverTreatedAsCreatingAnAlarm() {
        val response = command("ปิดนาฬิกาปลุกทุกเวลา")

        assertEquals(
            "[ACTION:CANCEL_ALL_REMINDERS] ปิดรายการเตือนของอุ่นใจทั้งหมดแล้วค่ะ",
            response
        )
    }

    @Test
    fun creatingAnAlarmUsesTheSameReminderSystemThatCanCancelIt() {
        val response = command("ตั้งปลุก 6 โมง")

        org.junit.Assert.assertTrue(response.orEmpty().startsWith("[ACTION:SET_REMINDER:general|0|"))
    }

    @Test
    fun exactLineNameOpensLineWhenLineManIsAlsoInstalled() {
        val response = LocalAssistantBot().handle(
            text = "เปิดไลน์",
            contacts = emptyList(),
            apps = listOf("LINE Official" to "com.linecorp.line.android", "LINE MAN" to "com.linecorp.lineman"),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertEquals(
            "[ACTION:OPEN_APP:com.linecorp.line.android] กำลังเปิดแอป LINE Official ให้ค่ะ",
            response
        )
    }

    @Test
    fun directionWithDestinationAlwaysCreatesGoogleMapsNavigationAction() {
        val response = command("ไปเซ็นทรัลเวสต์เกต")

        assertEquals(
            "[ACTION:NAVIGATE:เซ็นทรัลเวสต์เกต] กำลังเปิด Google Maps ไปยัง เซ็นทรัลเวสต์เกต ให้ค่ะ",
            response
        )
    }

    @Test
    fun sevenElevenOpensItsInstalledPackage() {
        val response = LocalAssistantBot().handle(
            text = "อุ่นใจ เปิดเซเว่นอีเลฟเว่น",
            contacts = emptyList(),
            apps = listOf("7-Eleven" to "asuk.com.android.app"),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertEquals(
            "[ACTION:OPEN_APP:asuk.com.android.app] กำลังเปิดแอป 7-Eleven ให้ค่ะ",
            response
        )
    }

    @Test
    fun savedVoiceKeyOpensTheAssignedApp() {
        val app = "7-Eleven" to "asuk.com.android.app"
        val response = LocalAssistantBot().handle(
            text = "เปิดร้านใกล้บ้าน",
            contacts = emptyList(), apps = listOf(app), searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" }, deleteContact = { "" }, createContact = { _, _ -> "" },
            readLatestMessage = { "" }, getBatteryStatus = { "" }, getCurrentTimeText = { "" },
            getTodayDateText = { "" }, consumePendingConfirmation = { null }, getEmergencyCallResponse = { "" },
            appVoiceAliases = mapOf("ร้านใกล้บ้าน" to app.second)
        )

        assertEquals("[ACTION:OPEN_APP:asuk.com.android.app] กำลังเปิดแอป 7-Eleven ให้ค่ะ", response)
    }

    @Test
    fun spokenKrungthaiNextOpensTheInstalledNextPackage() {
        val response = LocalAssistantBot().handle(
            text = "เปิดกรุงไทยเน็กซ์", contacts = emptyList(), apps = listOf("Krungthai NEXT" to "ktbcs.netbank"),
            searchContacts = { emptyList() }, updateContact = { _, _, _ -> "" }, deleteContact = { "" }, createContact = { _, _ -> "" },
            readLatestMessage = { "" }, getBatteryStatus = { "" }, getCurrentTimeText = { "" }, getTodayDateText = { "" },
            consumePendingConfirmation = { null }, getEmergencyCallResponse = { "" }
        )
        assertEquals("[ACTION:OPEN_APP:ktbcs.netbank] กำลังเปิดแอป Krungthai NEXT ให้ค่ะ", response)
    }

    @Test
    fun spokenBitkubOpensItsInstalledPackageDirectly() {
        val response = LocalAssistantBot().handle(
            text = "เปิดบิทคับ", contacts = emptyList(), apps = listOf("Bitkub" to "com.bitkub"),
            searchContacts = { emptyList() }, updateContact = { _, _, _ -> "" }, deleteContact = { "" }, createContact = { _, _ -> "" },
            readLatestMessage = { "" }, getBatteryStatus = { "" }, getCurrentTimeText = { "" }, getTodayDateText = { "" },
            consumePendingConfirmation = { null }, getEmergencyCallResponse = { "" }
        )

        assertEquals("[ACTION:OPEN_APP:com.bitkub] กำลังเปิดแอป Bitkub ให้ค่ะ", response)
    }

    @Test
    fun standardCatalogWorksOnAnotherPhoneWhenThePackageIsInstalled() {
        val response = LocalAssistantBot().handle(
            text = "เปิดกสิกร", contacts = emptyList(), apps = listOf("K PLUS" to "com.kasikorn.retail.mbanking.wap"),
            searchContacts = { emptyList() }, updateContact = { _, _, _ -> "" }, deleteContact = { "" }, createContact = { _, _ -> "" },
            readLatestMessage = { "" }, getBatteryStatus = { "" }, getCurrentTimeText = { "" }, getTodayDateText = { "" },
            consumePendingConfirmation = { null }, getEmergencyCallResponse = { "" }
        )

        assertEquals("[ACTION:OPEN_APP:com.kasikorn.retail.mbanking.wap] กำลังเปิดแอป K PLUS ให้ค่ะ", response)
    }

    @Test
    fun conversationalRepliesDoNotAssumeTheUsersAge() {
        val response = command("คิดถึง")

        org.junit.Assert.assertFalse(response.orEmpty().contains("คุณตาคุณยาย"))
    }

    @Test
    fun explicitEmergencyHelpRequestUsesTheLocalSosPath() {
        val response = LocalAssistantBot().handle(
            text = "ช่วยด้วย", contacts = emptyList(), apps = emptyList(),
            searchContacts = { emptyList() }, updateContact = { _, _, _ -> "" }, deleteContact = { "" }, createContact = { _, _ -> "" },
            readLatestMessage = { "" }, getBatteryStatus = { "" }, getCurrentTimeText = { "" }, getTodayDateText = { "" },
            consumePendingConfirmation = { null }, getEmergencyCallResponse = { "กำลังติดต่อผู้ดูแลฉุกเฉินค่ะ" }
        )

        assertEquals("กำลังติดต่อผู้ดูแลฉุกเฉินค่ะ", response)
    }

    @Test
    fun reminderListQuestionReadsOnlyTheLocalReminderStore() {
        val response = LocalAssistantBot().handle(
            text = "มีรายการเตือนอะไรบ้าง", contacts = emptyList(), apps = emptyList(),
            searchContacts = { emptyList() }, updateContact = { _, _, _ -> "" }, deleteContact = { "" }, createContact = { _, _ -> "" },
            readLatestMessage = { "" }, getBatteryStatus = { "" }, getCurrentTimeText = { "" }, getTodayDateText = { "" },
            getReminderSummaryText = { "รายการเตือนจากในเครื่อง: กินยา 08:00" },
            consumePendingConfirmation = { null }, getEmergencyCallResponse = { "" }
        )

        assertEquals("รายการเตือนจากในเครื่อง: กินยา 08:00", response)
    }
}
