package com.example.nongkanvelaassistant.data

import com.example.nongkanvelaassistant.ui.DeviceContact
import com.example.nongkanvelaassistant.ui.ScamNumberEntry
import com.example.nongkanvelaassistant.ui.SheetCommand
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class LocalAssistantBotTest {

    private fun defaultHandle(
        bot: LocalAssistantBot,
        text: String
    ): String? {
        return bot.handle(
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
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
    }

    // Helper to invoke the private normalizeContactName method using reflection
    private fun invokeNormalizeContactName(bot: LocalAssistantBot, name: String): String {
        val method = LocalAssistantBot::class.java.getDeclaredMethod("normalizeContactName", String::class.java)
        method.isAccessible = true
        return method.invoke(bot, name) as String
    }

    private fun invokePreProcessAndCorrectIntent(bot: LocalAssistantBot, text: String): String {
        val method = LocalAssistantBot::class.java.getDeclaredMethod("preProcessAndCorrectIntent", String::class.java)
        method.isAccessible = true
        return method.invoke(bot, text) as String
    }

    @Test
    fun testNormalizeContactName_preservesKinshipOnly() {
        val bot = LocalAssistantBot()
        
        // Exact kinship term/title should be preserved in its normalized form (and combine marks kept)
        assertEquals("แม่", invokeNormalizeContactName(bot, "แม่"))
        assertEquals("แม่", invokeNormalizeContactName(bot, "คุณแม่"))
        assertEquals("ตา", invokeNormalizeContactName(bot, "คุณตา"))
        assertEquals("ยาย", invokeNormalizeContactName(bot, "คุณยาย"))
        assertEquals("พ่อ", invokeNormalizeContactName(bot, "พ่อ"))
        
        // Kinship terms are not stripped from names (to prevent false matches), but matched via contains/substrings
        assertEquals("แม่แดง", invokeNormalizeContactName(bot, "แม่แดง"))
        assertEquals("ตาจัน", invokeNormalizeContactName(bot, "คุณตาจัน"))
        assertEquals("ลุงสมชาย", invokeNormalizeContactName(bot, "ลุงสมชาย"))
    }

    @Test
    fun testPreProcessAndCorrectIntent_removesIrrelevantWordsAndCorrectsMishearing() {
        val bot = LocalAssistantBot()

        assertEquals("เปิด google map", invokePreProcessAndCorrectIntent(bot, "รบกวนเปิดกูเกิลแมวหน่อยครับ"))
        assertEquals("เตือนกินยา 20:00", invokePreProcessAndCorrectIntent(bot, "ช่วยเตือนกินตา 20:00 ที"))
        assertEquals("ไปโรงเรียนจิระศาสตร์", invokePreProcessAndCorrectIntent(bot, "ขอไปโรงเรียนจิระหน่อย"))
        assertEquals("ไปโรงเรียนจิระศาสตร์", invokePreProcessAndCorrectIntent(bot, "ไปโรงเรียนชีระศาสตร์"))
        assertEquals("พาไป สภ.มหาราช", invokePreProcessAndCorrectIntent(bot, "พาไป สภ.มหาราษฎร์"))
        assertEquals("โทรหาผู้กำกับ", invokePreProcessAndCorrectIntent(bot, "โทรหาผู้กับ"))
        assertEquals("โทรหาสารวัตร", invokePreProcessAndCorrectIntent(bot, "โทรหาสารวัต"))
        assertEquals("ไปโรงพยาบาลอ่างทอง", invokePreProcessAndCorrectIntent(bot, "ไปโรงพยาบาลอ้างทอง"))
        assertEquals("ไปอยุธยา", invokePreProcessAndCorrectIntent(bot, "ไปอายุธยา"))
        assertEquals("ไปสิงห์บุรี", invokePreProcessAndCorrectIntent(bot, "ไปสิงบุรี"))
        assertEquals("โทรหาร้อยเวร", invokePreProcessAndCorrectIntent(bot, "โทรหาร้อยเวน"))
        assertEquals("แจ้งเตือนประชุม", invokePreProcessAndCorrectIntent(bot, "แจ้งเดือนประชุมม"))
        assertEquals("ว.20", invokePreProcessAndCorrectIntent(bot, "วอยี่สิบ"))
        assertEquals("ไปโรงเรียน", invokePreProcessAndCorrectIntent(bot, "ไปโลงเรียน"))
        assertEquals("เวรอำนวยการวันอาทิตย์ 5 กรกฎาคม 2569", invokePreProcessAndCorrectIntent(bot, "เวนอำนวยกานวันอาทิต 5 กะกฎาคม 2569"))
    }

    @Test
    fun testPreProcessAndCorrectIntent_preservesEmergencyMeaning() {
        val bot = LocalAssistantBot()
        assertEquals("ช่วยด้วย", invokePreProcessAndCorrectIntent(bot, "ช่วยด้วย"))
    }

    @Test
    fun testHandleDutyCommand_supportsFutureExplicitDate() {
        val bot = LocalAssistantBot()
        val response = defaultHandle(bot, "เวรอำนวยการวันที่ 6 กรกฎาคม 2569 ใคร")
        assertEquals("วันที่ 6 กรกฎาคม 2569 เวรอำนวยการคือ พ.ต.ท.วัชระ ศรีเปี่ยม (061-093-5599)", response)
    }

    @Test
    fun testHandleDutyCommand_supportsThaiShortMonthDate() {
        val bot = LocalAssistantBot()
        val response = defaultHandle(bot, "วันอาทิตย์ ที่ 5 ก.ค. เวรอำนวยการ")
        assertEquals("วันที่ 5 กรกฎาคม 2569 เวรอำนวยการคือ พ.ต.ต.คมสัน เค็งชัยภูมิ (089-0777761)", response)
    }

    @Test
    fun testHandleDutyCommand_supportsWeekendRotationSaturday() {
        val bot = LocalAssistantBot()
        val response = defaultHandle(bot, "เวรอำนวยการวันที่ 4 กรกฎาคม 2569 ใคร")
        assertEquals("วันที่ 4 กรกฎาคม 2569 เวรอำนวยการคือ พ.ต.ท.จีรวัฒน์ พลอาจ (093-532-5959)", response)
    }

    @Test
    fun testHandleDutyCommand_supportsFutureWeekdayQuery() {
        val bot = LocalAssistantBot()
        val response = defaultHandle(bot, "เวรวันอังคารหน้า")
        assertNotNull(response)
        assertTrue(response!!.contains("เวรอำนวยการคือ"))
    }

    @Test
    fun testHandleContactSearch_withKinshipQuery() {
        val bot = LocalAssistantBot()
        val contacts = listOf(
            DeviceContact(1, 1, "กองโจ", "0841164118"),
            DeviceContact(2, 2, "กองโจ1", "0837405532"),
            DeviceContact(3, 3, "กอง ดิษฐ์", "0918900630"),
            DeviceContact(4, 4, "แม่", "0812345678")
        )

        val searchContacts: (String) -> List<DeviceContact> = { query ->
            val cleanQuery = invokeNormalizeContactName(bot, query)
            if (cleanQuery.isBlank()) {
                contacts
            } else {
                contacts.filter { contact ->
                    val normName = invokeNormalizeContactName(bot, contact.name)
                    normName.contains(cleanQuery) || contact.name.contains(query, ignoreCase = true)
                }
            }
        }

        // Test searching for "ขอเบอร์แม่"
        val response1 = bot.handle(
            text = "ขอเบอร์แม่",
            contacts = contacts,
            apps = emptyList(),
            searchContacts = searchContacts,
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertNotNull(response1)
        assertEquals("พบรายชื่อ แม่ เบอร์ 0812345678 ค่ะ", response1)

        // Test searching for "ขอเบอร์โทรแม่"
        val response2 = bot.handle(
            text = "ขอเบอร์โทรแม่",
            contacts = contacts,
            apps = emptyList(),
            searchContacts = searchContacts,
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertNotNull(response2)
        assertEquals("พบรายชื่อ แม่ เบอร์ 0812345678 ค่ะ", response2)
    }

    private fun invokeParseThaiTime(bot: LocalAssistantBot, text: String): Pair<Int, Int>? {
        val method = LocalAssistantBot::class.java.getDeclaredMethod("parseThaiTime", String::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(bot, text) as Pair<Int, Int>?
    }

    private fun invokeParseReminderDateTime(bot: LocalAssistantBot, text: String, now: Calendar): Long? {
        val method = LocalAssistantBot::class.java.getDeclaredMethod(
            "parseReminderDateTime",
            String::class.java,
            Calendar::class.java
        )
        method.isAccessible = true
        return method.invoke(bot, text, now) as Long?
    }

    @Test
    fun testParseThaiTime_variousFormats() {
        val bot = LocalAssistantBot()
        
        assertEquals(Pair(8, 30), invokeParseThaiTime(bot, "แปดโมงครึ่ง"))
        assertEquals(Pair(15, 15), invokeParseThaiTime(bot, "บ่ายสามโมงสิบห้า"))
        assertEquals(Pair(19, 0), invokeParseThaiTime(bot, "ทุ่มตรง"))
        assertEquals(Pair(5, 0), invokeParseThaiTime(bot, "ตีห้า"))
        assertEquals(Pair(0, 45), invokeParseThaiTime(bot, "เที่ยงคืนสี่สิบห้า"))
        assertEquals(Pair(12, 30), invokeParseThaiTime(bot, "เที่ยงครึ่ง"))
    }

    @Test
    fun testHandleAlarmCommand_withThaiTimeWords() {
        val bot = LocalAssistantBot()
        val emptyContacts = emptyList<DeviceContact>()
        
        val response = bot.handle(
            text = "ตั้งปลุกแปดโมงครึ่ง",
            contacts = emptyContacts,
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        
        assertEquals("[ACTION:SET_ALARM:8:30] ตั้งปลุกเวลา 08:30 ให้แล้วค่ะ", response)
    }

    @Test
    fun testHandleReminderCommand_dailyMedicine() {
        val bot = LocalAssistantBot()
        val emptyContacts = emptyList<DeviceContact>()

        val response = bot.handle(
            text = "เตือนกินยา 08:00 ทุกวัน",
            contacts = emptyContacts,
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertTrue(response?.startsWith("[ACTION:SET_REMINDER:medicine|86400000|") == true)
        assertTrue(response?.contains("ตั้งเตือน กินยา ให้แล้วค่ะ") == true)
    }

    @Test
    fun testHandleReminderSummaryCommand() {
        val bot = LocalAssistantBot()
        val emptyContacts = emptyList<DeviceContact>()

        val response = bot.handle(
            text = "ดูรายการเตือน",
            contacts = emptyContacts,
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "• กินยา - 01/01/2026 08:00" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertEquals("• กินยา - 01/01/2026 08:00", response)
    }

    @Test
    fun testHandleAppointmentInquiryWithoutDateDoesNotGuess() {
        val bot = LocalAssistantBot()
        val emptyContacts = emptyList<DeviceContact>()

        val response = bot.handle(
            text = "หมอนัดวันที่",
            contacts = emptyContacts,
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "ยังไม่มีรายการเตือนค่ะ" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertEquals("ยังไม่พบวันนัดที่บันทึกไว้ค่ะ กรุณาบอกวันและเวลานัดให้ชัดเจน เช่น หมอนัด 25/06/2026 09:00 ค่ะ", response)
    }

    @Test
    fun testHandleVagueOptionsQuestionAsksForCategory() {
        val bot = LocalAssistantBot()
        val emptyContacts = emptyList<DeviceContact>()

        val response = bot.handle(
            text = "มีตัวอะไรบ้าง",
            contacts = emptyContacts,
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertEquals("อยากให้อุ่นใจช่วยเรื่องไหนคะ เช่น อาหาร การออกกำลังกาย ยา นัดหมอ หรือการใช้งานโทรศัพท์คะ", response)
    }

    @Test
    fun testParseReminderDateTime_thaiMonthDateWithoutTimeReturnsNull() {
        val bot = LocalAssistantBot()
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 22, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val parsed = invokeParseReminderDateTime(bot, "เตือนหมอนัดวันจันทร์ที่ 29 มิถุนายน", now)
        assertEquals(null, parsed)
    }

    @Test
    fun testHandleReminderCommand_thaiMonthDateWithoutTime() {
        val bot = LocalAssistantBot()
        val emptyContacts = emptyList<DeviceContact>()

        val response = bot.handle(
            text = "เตือนหมอนัดวันจันทร์ที่ 29 มิถุนายน 2099",
            contacts = emptyContacts,
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertEquals("[ACTION:ASK_CLARIFICATION:appointment|0|หมอนัด] ต้องการให้ตั้งเตือนหมอนัด วันไหนและกี่โมงคะ?", response)
    }

    @Test
    fun testHandleReminderCommand_relativeTime() {
        val bot = LocalAssistantBot()
        val emptyContacts = emptyList<DeviceContact>()

        val testInputs = listOf(
            "เตือนอีก 1 ชม. เตือนว่านอนได้แล้ว" to "นอนได้แล้ว",
            "เตือนอีก 1ชม. เตือนว่านอนได้แล้ว" to "นอนได้แล้ว",
            "เตือนอีก 30 นาที เตือนว่านอนได้แล้ว" to "นอนได้แล้ว",
            "เตือนอีก 10 นาที เตือนว่านอนได้แล้ว" to "นอนได้แล้ว",
            "อีกสิบนาที เตือนนอน" to "นอน",
            "อีกครึ่งชั่วโมง กินยา" to "กินยา",
            "อีก หนึ่ง ชม เตือนนอนได้แล้ว" to "นอนได้แล้ว"
        )

        for ((input, expectedTitle) in testInputs) {
            val expectedKind = if (input.contains("กินยา")) "medicine" else "general"
            val response = bot.handle(
                text = input,
                contacts = emptyContacts,
                apps = emptyList(),
                searchContacts = { emptyList() },
                updateContact = { _, _, _ -> "" },
                deleteContact = { "" },
                createContact = { _, _ -> "" },
                readLatestMessage = { "" },
                getBatteryStatus = { "" },
                getCurrentTimeText = { "" },
                getTodayDateText = { "" },
                getReminderSummaryText = { "" },
                consumePendingConfirmation = { null },
                getEmergencyCallResponse = { "" }
            )
            assertNotNull("Failed on input: $input", response)
            assertTrue("Expected SET_REMINDER action on input: $input. Got: $response", response?.startsWith("[ACTION:SET_REMINDER:$expectedKind|0|") == true)
            assertTrue("Expected title '$expectedTitle' on input: $input. Got: $response", response?.contains("ตั้งเตือน $expectedTitle ให้แล้วค่ะ") == true)
        }
    }

    @Test
    fun testHandleReminderCommand_relativeTimeWithFreshTitle() {
        val bot = LocalAssistantBot()
        val medicalResponse = bot.handle(
            text = "อีกครึ่งชั่วโมง กินยา",
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
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertNotNull(medicalResponse)
        assertTrue("Expected medicine reminder, got: $medicalResponse", medicalResponse!!.startsWith("[ACTION:SET_REMINDER:medicine|"))
        assertTrue("Expected title 'กินยา', got: $medicalResponse", medicalResponse.contains("ตั้งเตือน กินยา ให้แล้วค่ะ"))

        val generalResponse = bot.handle(
            text = "เตือน อีก 1ชม.ไปโรงเรียน",
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
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertNotNull(generalResponse)
        assertTrue("Expected general reminder, got: $generalResponse", generalResponse!!.startsWith("[ACTION:SET_REMINDER:general|"))
        assertTrue("Expected title 'ไปโรงเรียน', got: $generalResponse", generalResponse.contains("ตั้งเตือน ไปโรงเรียน ให้แล้วค่ะ"))
        assertFalse("Should not default to กินยา, got: $generalResponse", generalResponse.contains("กินยา"))
    }

    @Test
    fun testReminderCommandDetection_freshReminderCommands() {
        assertTrue(LocalAssistantBot.isReminderCommand("เตือน อีก 1ชม.ไปโรงเรียน"))
        assertTrue(LocalAssistantBot.isReminderCommand("ช่วยเตือน 30 นาทีไปโรงเรียน"))
    }

    @Test
    fun testReminderContinuationHelpers_detectAndStripEditPhrases() {
        assertTrue(LocalAssistantBot.isReminderContinuationCommand("เปลี่ยนเป็น 10 โมงแทน"))
        assertTrue(LocalAssistantBot.isReminderContinuationCommand("เลื่อนไปพรุ่งนี้ 9 โมง"))
        assertEquals("10โมง", LocalAssistantBot.stripReminderContinuationPhrases("เปลี่ยนเป็น 10 โมงแทน"))
        assertEquals("พรุ่งนี้9โมง", LocalAssistantBot.stripReminderContinuationPhrases("เลื่อนไปพรุ่งนี้ 9 โมง"))
        assertEquals("เตือน นัดหมอ 10โมง", LocalAssistantBot.buildReminderContinuationCommand("นัดหมอ", "เปลี่ยนเป็น 10 โมงแทน"))
    }

    @Test
    fun testReminderPendingContext_onlyReusesForRealContinuation() {
        assertTrue(LocalAssistantBot.shouldReusePendingReminderTitle("เปลี่ยนเป็น 10 โมง"))
        assertTrue(LocalAssistantBot.shouldReusePendingReminderTitle("พรุ่งนี้ 8 โมง"))
        assertFalse(LocalAssistantBot.shouldReusePendingReminderTitle("ไปรับลูก"))
        assertFalse(LocalAssistantBot.shouldReusePendingReminderTitle("ไปรับลูก 8 โมง"))
    }

    @Test
    fun testHandleReminderCommand_missingTimeAsksForClarification() {
        val bot = LocalAssistantBot()
        val emptyContacts = emptyList<DeviceContact>()

        val response = bot.handle(
            text = "เตือนกินยาแก้ไอ",
            contacts = emptyContacts,
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertNotNull(response)
        assertTrue(response?.startsWith("[ACTION:ASK_CLARIFICATION:medicine|0|กินยาแก้ไอ]") == true)
        assertTrue(response?.contains("ต้องการให้ตั้งเตือนกินยาแก้ไอ วันไหนและกี่โมงคะ?") == true)
    }

    @Test
    fun testHandleReminderCommand_withoutTitleDoesNotDefaultToMedicine() {
        val bot = LocalAssistantBot()
        val response = bot.handle(
            text = "เตือน 8 โมง",
            contacts = emptyList<DeviceContact>(),
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertNotNull(response)
        assertTrue(response?.contains("ต้องการให้ตั้งเตือนวันไหนและกี่โมงคะ") == true)
        assertFalse(response?.contains("กินยา") == true)
    }

    @Test
    fun testHandleReminderCommand_nonMedicalReminderDoesNotSayMedicine() {
        val bot = LocalAssistantBot()
        val response = bot.handle(
            text = "เตือน ไปรับลูก วันนี้ 08:00",
            contacts = emptyList<DeviceContact>(),
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertNotNull(response)
        assertTrue(response?.contains("ตั้งเตือน ไปรับลูก") == true)
        assertFalse(response?.contains("กินยา") == true)
    }

    @Test
    fun testFormatReminderConfirmation_avoidsDoubleMedicineWord() {
        val response = LocalAssistantBot.formatReminderConfirmation("medicine", "กินยา", "24/06/2026 16:56")
        assertEquals("ตั้งเตือนกินยา วันที่ 24/06/2026 16:56 ให้แล้วค่ะ", response)
    }

    @Test
    fun testHandleReminderDeleteCommand() {
        val bot = LocalAssistantBot()

        val response = bot.handle(
            text = "ลบเตือน หมอนัด",
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
            getReminderSummaryText = { "" },
            removeReminderByQuery = { query ->
                if (query == "หมอนัด") "ลบรายการเตือน หมอนัด เรียบร้อยแล้วค่ะ" else null
            },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertEquals("ลบรายการเตือน หมอนัด เรียบร้อยแล้วค่ะ", response)
    }

    @Test
    fun testHandleEmergencyCommand_newKeywords() {
        val bot = LocalAssistantBot()
        val emptyContacts = emptyList<DeviceContact>()
        
        val response1 = bot.handle(
            text = "ช่วยด้วยเจ็บหน้าอก",
            contacts = emptyContacts,
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "EMERGENCY" }
        )
        assertEquals("EMERGENCY", response1)

        val response2 = bot.handle(
            text = "ยายเป็นลม",
            contacts = emptyContacts,
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "EMERGENCY" }
        )
        assertEquals("EMERGENCY", response2)
    }

    @Test
    fun testHandleChitchat_responses() {
        val bot = LocalAssistantBot()
        val emptyContacts = emptyList<DeviceContact>()
        
        val response1 = bot.handle(
            text = "กินข้าวหรือยัง",
            contacts = emptyContacts,
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("อุ่นใจเป็นระบบผู้ช่วยดิจิทัลเลยทานอาหารไม่ได้ค่ะ แต่ขอบคุณที่เป็นห่วงนะคะ คุณตาคุณยายทานข้าวหรือยังคะ อย่าลืมทานอาหารให้อิ่มและทานยาด้วยนะคะ", response1)

        val response2 = bot.handle(
            text = "เธอชื่ออะไรนะ",
            contacts = emptyContacts,
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("หนูชื่ออุ่นใจค่ะ เป็นผู้ช่วยและเลขาส่วนตัวของคุณตาคุณยายค่ะ", response2)
    }

    @Test
    fun testHandleOpenApp_musicRedirection() {
        val bot = LocalAssistantBot()
        val apps = listOf(
            Pair("Spotify", "com.spotify.music"),
            Pair("YouTube", "com.google.android.youtube")
        )
        
        val response = bot.handle(
            text = "อยากฟังเพลง",
            contacts = emptyList(),
            apps = apps,
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        
        assertEquals("[ACTION:OPEN_APP:com.spotify.music] กำลังเปิด Spotify เพื่อเล่นเพลงให้ค่ะ", response)
    }

    @Test
    fun testHandleScamNumberQuery_returnsSource() {
        val bot = LocalAssistantBot()
        val response = bot.handle(
            text = "เช็กเบอร์ 0812345678",
            contacts = emptyList(),
            apps = emptyList(),
            scamNumbers = listOf(
                ScamNumberEntry(
                    number = "0812345678",
                    label = "แก๊งคอลเซ็นเตอร์",
                    source = "official",
                    note = "Cyber Check"
                )
            ),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertTrue(response?.contains("0812345678") == true)
        assertTrue(response?.contains("แหล่งที่มา: ฐานข้อมูลทางการ") == true)
    }

    @Test
    fun testHandleCallKnownScamNumber_warnsInsteadOfCalling() {
        val bot = LocalAssistantBot()
        val contacts = listOf(DeviceContact(1, 1, "สมชาย", "0812345678"))
        val response = bot.handle(
            text = "โทรหาสมชาย",
            contacts = contacts,
            apps = emptyList(),
            scamNumbers = listOf(
                ScamNumberEntry(
                    number = "0812345678",
                    label = "หลอกโอนเงิน",
                    source = "user_reported",
                    note = "แจ้งจากผู้ใช้"
                )
            ),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertTrue(response?.contains("เบอร์ของ สมชาย ถูกบันทึกว่าเป็นเบอร์เสี่ยง") == true)
        assertTrue(response?.contains("รายงานจากผู้ใช้") == true)
    }

    @Test
    fun testHandleContactSearch_crossLanguage_thaiToEnglish() {
        val bot = LocalAssistantBot()
        val contacts = listOf(
            DeviceContact(1, 1, "David", "0811112222"),
            DeviceContact(2, 2, "John", "0833334444")
        )
        
        val response = bot.handle(
            text = "ขอเบอร์ David",
            contacts = contacts,
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        
        assertEquals("พบรายชื่อ David เบอร์ 0811112222 ค่ะ", response)
    }

    @Test
    fun testHandleContactSearch_crossLanguage_englishToThai() {
        val bot = LocalAssistantBot()
        val contacts = listOf(
            DeviceContact(1, 1, "แม่", "0812345678")
        )
        
        val response = bot.handle(
            text = "ขอเบอร์ แม่",
            contacts = contacts,
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        
        assertEquals("พบรายชื่อ แม่ เบอร์ 0812345678 ค่ะ", response)
    }

    @Test
    fun testHandleBluetoothCommands() {
        val bot = LocalAssistantBot()
        val emptyContacts = emptyList<DeviceContact>()

        val onResponse = bot.handle(
            text = "เปิดบลูทูธ",
            contacts = emptyContacts,
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("[ACTION:BLUETOOTH_ON] กำลังขอเปิด Bluetooth ให้ค่ะ", onResponse)

        val offResponse = bot.handle(
            text = "ปิด bluetooth",
            contacts = emptyContacts,
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("[ACTION:BLUETOOTH_OFF] กำลังปิด Bluetooth ให้ค่ะ", offResponse)

        val settingsResponse = bot.handle(
            text = "ตั้งค่าบลูทูธ",
            contacts = emptyContacts,
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("[ACTION:OPEN_BLUETOOTH_SETTINGS] กำลังเปิดหน้าตั้งค่า Bluetooth ให้ค่ะ", settingsResponse)
    }

    @Test
    fun testHandleIntentRecognition_correctsCommonMapMishearing() {
        val bot = LocalAssistantBot()

        val response = bot.handle(
            text = "เปิดกูเกิลแมว",
            contacts = emptyList(),
            apps = listOf("Google Maps" to "com.google.android.apps.maps"),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertEquals("[ACTION:OPEN_MAPS] กำลังเปิด GPS ให้ค่ะ", response)
    }

    @Test
    fun testHandleIntentRecognition_correctsCommonBluetoothMishearing() {
        val bot = LocalAssistantBot()

        val response = bot.handle(
            text = "ปิดบูทูด",
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
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertEquals("[ACTION:BLUETOOTH_OFF] กำลังปิด Bluetooth ให้ค่ะ", response)
    }

    @Test
    fun testHandleIntentRecognition_supportsShortSingleWordAppCommand() {
        val bot = LocalAssistantBot()

        val response = bot.handle(
            text = "ไลน์",
            contacts = emptyList(),
            apps = listOf("LINE" to "jp.naver.line.android"),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertEquals("[ACTION:OPEN_APP:jp.naver.line.android] กำลังเปิดแอป LINE ให้ค่ะ", response)
    }

    @Test
    fun testHandleIntentRecognition_asksToClarifyAmbiguousAppName() {
        val bot = LocalAssistantBot()

        val response = bot.handle(
            text = "เปิดไลน์",
            contacts = emptyList(),
            apps = listOf(
                "LINE" to "jp.naver.line.android",
                "LINE MAN" to "com.lineman.app"
            ),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertEquals("หมายถึงเปิด LINE หรือ LINE MAN คะ กรุณาพูดชื่อแอปอีกครั้งให้ชัดเจนนะคะ", response)
    }

    @Test
    fun testHandleCallKnownContact_callsImmediatelyWithoutConfirmation() {
        val response = LocalAssistantBot().handle(
            text = "โทรหาอันดา",
            contacts = listOf(DeviceContact(1, 1, "อันดา", "0640451730")),
            apps = emptyList(),
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertEquals("[ACTION:CALL:0640451730] อุ่นใจกำลังต่อสายโทรออกหาอันดาให้ค่ะ", response)
    }

    @Test
    fun testHandleContactSearch_preventFalsePhoneticMatches() {
        val bot = LocalAssistantBot()
        val contacts = listOf(
            DeviceContact(1, 1, "ด.ต.ชนินทร์ สวัสดี", "0876631278")
        )
        
        val response = bot.handle(
            text = "ขอเบอร์อานนท์",
            contacts = contacts,
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        
        assertEquals("ไม่พบรายชื่อนี้ในโทรศัพท์และในชีทค่ะ", response)
    }

    @Test
    fun testHandleContactSearch_preventFalseSurnameContainsMatches() {
        val bot = LocalAssistantBot()
        val contacts = listOf(
            DeviceContact(1, 1, "จ.ส.ต.กิตติพงศ์ บัวสนธิ์", "0800181243")
        )
        
        val response = bot.handle(
            text = "ขอเบอร์บัว",
            contacts = contacts,
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        
        assertEquals("ไม่พบรายชื่อนี้ในโทรศัพท์และในชีทค่ะ", response)
    }

    @Test
    fun testHandleSheetCommands() {
        val bot = LocalAssistantBot()
        val sheetCommands = listOf(
            SheetCommand(command = "โทรหาลุงเปี๊ยก", name = "ลุงเปี๊ยก", number = "0812345678", status = "ใช้งาน"),
            SheetCommand(command = "เปิดยูทูป", name = "com.google.android.youtube", number = "", status = "ใช้งาน"),
            SheetCommand(command = "เปิดเพลง", name = "Spotify", number = "", status = "ใช้งาน"),
            SheetCommand(command = "กินยาหรือยัง", name = "อย่าลืมกินยาหลังอาหารเย็นนะคะ", number = "", status = "ใช้งาน"),
            SheetCommand(command = "สวัสดีวันจันทร์", name = "สวัสดีค่ะคุณตาคุณยาย", number = "", status = "ปิดใช้งาน")
        )
        val apps = listOf(
            "Spotify" to "com.spotify.music",
            "YouTube" to "com.google.android.youtube"
        )

        // 1. Test Call Command from Sheet
        val responseCall = bot.handle(
            text = "โทรหาลุงเปี๊ยก",
            contacts = emptyList(),
            apps = apps,
            sheetCommands = sheetCommands,
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("[ACTION:CALL:0812345678] อุ่นใจกำลังต่อสายโทรออกหาลุงเปี๊ยกให้ค่ะ", responseCall)

        // 2. Test Open App (Package Name) from Sheet
        val responseOpenPackage = bot.handle(
            text = "เปิดยูทูป",
            contacts = emptyList(),
            apps = apps,
            sheetCommands = sheetCommands,
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("[ACTION:OPEN_APP:com.google.android.youtube] กำลังเปิดให้ตามคำสั่งค่ะ", responseOpenPackage)

        // 3. Test Open App (App Name mapped to Package) from Sheet
        val responseOpenAppName = bot.handle(
            text = "เปิดเพลง",
            contacts = emptyList(),
            apps = apps,
            sheetCommands = sheetCommands,
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("[ACTION:OPEN_APP:com.spotify.music] กำลังเปิดSpotifyให้ตามคำสั่งค่ะ", responseOpenAppName)

        // 4. Test Text Response from Sheet
        val responseText = bot.handle(
            text = "กินยาหรือยัง",
            contacts = emptyList(),
            apps = apps,
            sheetCommands = sheetCommands,
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("อย่าลืมกินยาหลังอาหารเย็นนะคะ", responseText)

        // 5. Test Disabled Command
        val responseDisabled = bot.handle(
            text = "สวัสดีวันจันทร์",
            contacts = emptyList(),
            apps = apps,
            sheetCommands = sheetCommands,
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals(null, responseDisabled)
    }

    @Test
    fun testHandleContactSearch_withThaiRankPrefixes() {
        val bot = LocalAssistantBot()
        val contacts = listOf(
            DeviceContact(1, 1, "พ. ต. อ. สมชาย แจ้งธรรมา", "0811111111")
        )
        
        val searchContacts: (String) -> List<DeviceContact> = { query ->
            val cleanQuery = invokeNormalizeContactName(bot, query)
            if (cleanQuery.isBlank()) {
                contacts
            } else {
                contacts.filter { contact ->
                    val normName = invokeNormalizeContactName(bot, contact.name)
                    normName.contains(cleanQuery) || contact.name.contains(query, ignoreCase = true)
                }
            }
        }

        // 1. Search with exact abbreviated rank: "ขอเบอร์ พ.ต.อ.สมชาย"
        val response1 = bot.handle(
            text = "ขอเบอร์ พ.ต.อ.สมชาย",
            contacts = contacts,
            apps = emptyList(),
            searchContacts = searchContacts,
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("พบรายชื่อ พ. ต. อ. สมชาย แจ้งธรรมา เบอร์ 0811111111 ค่ะ", response1)

        // 2. Search with full rank name: "ขอเบอร์ พันตำรวจเอกสมชาย"
        val response2 = bot.handle(
            text = "ขอเบอร์ พันตำรวจเอกสมชาย",
            contacts = contacts,
            apps = emptyList(),
            searchContacts = searchContacts,
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("พบรายชื่อ พ. ต. อ. สมชาย แจ้งธรรมา เบอร์ 0811111111 ค่ะ", response2)

        // 3. Search with just name (without rank): "ขอเบอร์สมชาย"
        val response3 = bot.handle(
            text = "ขอเบอร์สมชาย",
            contacts = contacts,
            apps = emptyList(),
            searchContacts = searchContacts,
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("พบรายชื่อ พ. ต. อ. สมชาย แจ้งธรรมา เบอร์ 0811111111 ค่ะ", response3)

        // 4. Fallback search with just rank name (no prefix): "พันตำรวจเอกสมชาย"
        val response4 = bot.handle(
            text = "พันตำรวจเอกสมชาย",
            contacts = contacts,
            apps = emptyList(),
            searchContacts = searchContacts,
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("พบรายชื่อ พ. ต. อ. สมชาย แจ้งธรรมา เบอร์ 0811111111 ค่ะ", response4)

        // 5. Fallback search with just name (no prefix): "สมชาย"
        val response5 = bot.handle(
            text = "สมชาย",
            contacts = contacts,
            apps = emptyList(),
            searchContacts = searchContacts,
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("พบรายชื่อ พ. ต. อ. สมชาย แจ้งธรรมา เบอร์ 0811111111 ค่ะ", response5)

        // 6. Search with group suffix prefix: "ขอเบอร์สมชายทุกคน"
        val response6 = bot.handle(
            text = "ขอเบอร์สมชายทุกคน",
            contacts = contacts,
            apps = emptyList(),
            searchContacts = searchContacts,
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("พบรายชื่อ พ. ต. อ. สมชาย แจ้งธรรมา เบอร์ 0811111111 ค่ะ", response6)

        // 7. Direct search with group suffix: "สมชายทั้งหมดเลย"
        val response7 = bot.handle(
            text = "สมชายทั้งหมดเลย",
            contacts = contacts,
            apps = emptyList(),
            searchContacts = searchContacts,
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("พบรายชื่อ พ. ต. อ. สมชาย แจ้งธรรมา เบอร์ 0811111111 ค่ะ", response7)
    }

    @Test
    fun testHandleContactSearch_withSheetsFallback() {
        val bot = LocalAssistantBot()
        val sheetCommands = listOf(
            SheetCommand("โทรหาลุงเปี๊ยก", "ลุงเปี๊ยก", "0812345678", "ใช้งาน")
        )

        // 1. Search with prefix: "ขอเบอร์ลุงเปี๊ยก"
        val response1 = bot.handle(
            text = "ขอเบอร์ลุงเปี๊ยก",
            contacts = emptyList(),
            apps = emptyList(),
            sheetCommands = sheetCommands,
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("พบรายชื่อ ลุงเปี๊ยก เบอร์ 0812345678 ในชีทค่ะ", response1)

        // 2. Direct lookup: "ลุงเปี๊ยก"
        val response2 = bot.handle(
            text = "ลุงเปี๊ยก",
            contacts = emptyList(),
            apps = emptyList(),
            sheetCommands = sheetCommands,
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("พบรายชื่อ ลุงเปี๊ยก เบอร์ 0812345678 ในชีทค่ะ", response2)
    }

    @Test
    fun testHandleContactAdd() {
        val bot = LocalAssistantBot()
        var createdName = ""
        var createdNumber = ""
        val createContact: (String, String) -> String = { name, number ->
            createdName = name
            createdNumber = number
            "เพิ่มรายชื่อ $name เรียบร้อยแล้วค่ะ"
        }

        val response = bot.handle(
            text = "เพิ่มรายชื่อ สมชาย 0816386266",
            contacts = emptyList(),
            apps = emptyList(),
            searchContacts = { emptyList() },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = createContact,
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )

        assertEquals("เพิ่มรายชื่อ สมชาย เรียบร้อยแล้วค่ะ", response)
        assertEquals("สมชาย", createdName)
        assertEquals("0816386266", createdNumber)
    }

    @Test
    fun testExpandThaiAbbreviationsForSpeech() {
        val bot = LocalAssistantBot()
        
        // 1. Spaced dots (preserves the space before the name)
        assertEquals(
            "พบรายชื่อ พันตำรวจเอก สมชาย แจ้งธรรมา เบอร์ 0816386266 ค่ะ",
            bot.expandThaiAbbreviationsForSpeech("พบรายชื่อ พ. ต. อ. สมชาย แจ้งธรรมา เบอร์ 0816386266 ค่ะ")
        )
        
        // 2. Compact dots (no space before the name)
        assertEquals(
            "พบรายชื่อ พันตำรวจโทสมชาย เบอร์ 0811111111 ค่ะ",
            bot.expandThaiAbbreviationsForSpeech("พบรายชื่อ พ.ต.ท.สมชาย เบอร์ 0811111111 ค่ะ")
        )
        
        // 3. No dots but has space (preserves the space before the name)
        assertEquals(
            "พบรายชื่อ ร้อยตำรวจเอก สมชาย เบอร์ 0822222222 ค่ะ",
            bot.expandThaiAbbreviationsForSpeech("พบรายชื่อ รตอ สมชาย เบอร์ 0822222222 ค่ะ")
        )
        
        // 4. Other ranks/titles (no spaces before the names)
        assertEquals(
            "พบรายชื่อ นายแพทย์สมชาย และ แพทย์หญิงสมศรี และ ดอกเตอร์สมศักดิ์ ค่ะ",
            bot.expandThaiAbbreviationsForSpeech("พบรายชื่อ นพ.สมชาย และ พญ.สมศรี และ ดร.สมศักดิ์ ค่ะ")
        )
    }

    @Test
    fun testFormatContactSearchResponse_cleansThaiPrefixes() {
        val bot = LocalAssistantBot()
        
        // 1. Single prefix
        val contacts1 = listOf(DeviceContact(1, 1, "วัชระ ศรีเปี่ยม", "เบอร์ 061-093-5599"))
        val response1 = bot.handle(
            text = "ขอเบอร์วัชระ",
            contacts = contacts1,
            apps = emptyList(),
            searchContacts = { contacts1 },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("พบรายชื่อ วัชระ ศรีเปี่ยม เบอร์ 061-093-5599 ค่ะ", response1)

        // 2. Double prefix
        val contacts2 = listOf(DeviceContact(1, 1, "วัชระ ศรีเปี่ยม", "เบอร์ เบอร์ 061-093-5599"))
        val response2 = bot.handle(
            text = "ขอเบอร์วัชระ",
            contacts = contacts2,
            apps = emptyList(),
            searchContacts = { contacts2 },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("พบรายชื่อ วัชระ ศรีเปี่ยม เบอร์ 061-093-5599 ค่ะ", response2)

        // 3. Leading non-breaking space (NBSP) before prefix
        val contacts3 = listOf(DeviceContact(1, 1, "วัชระ ศรีเปี่ยม", "\u00A0เบอร์ 061-093-5599"))
        val response3 = bot.handle(
            text = "ขอเบอร์วัชระ",
            contacts = contacts3,
            apps = emptyList(),
            searchContacts = { contacts3 },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("พบรายชื่อ วัชระ ศรีเปี่ยม เบอร์ 061-093-5599 ค่ะ", response3)

        // 4. NBSP between prefix and number
        val contacts4 = listOf(DeviceContact(1, 1, "วัชระ ศรีเปี่ยม", "เบอร์\u00A0061-093-5599"))
        val response4 = bot.handle(
            text = "ขอเบอร์วัชระ",
            contacts = contacts4,
            apps = emptyList(),
            searchContacts = { contacts4 },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("พบรายชื่อ วัชระ ศรีเปี่ยม เบอร์ 061-093-5599 ค่ะ", response4)

        // 5. Multiple different prefixes
        val contacts5 = listOf(DeviceContact(1, 1, "วัชระ ศรีเปี่ยม", "เบอร์โทรศัพท์ เบอร์ 061-093-5599"))
        val response5 = bot.handle(
            text = "ขอเบอร์วัชระ",
            contacts = contacts5,
            apps = emptyList(),
            searchContacts = { contacts5 },
            updateContact = { _, _, _ -> "" },
            deleteContact = { "" },
            createContact = { _, _ -> "" },
            readLatestMessage = { "" },
            getBatteryStatus = { "" },
            getCurrentTimeText = { "" },
            getTodayDateText = { "" },
            getReminderSummaryText = { "" },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
        assertEquals("พบรายชื่อ วัชระ ศรีเปี่ยม เบอร์ 061-093-5599 ค่ะ", response5)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Delete Reminder — regression tests for bugs found during device testing
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleDelete(text: String, existing: List<String> = listOf("หมอนัด", "กินยา", "ออกกำลังกาย")): String? {
        val bot = LocalAssistantBot()
        return bot.handle(
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
            getReminderSummaryText = { "" },
            removeReminderByQuery = { query ->
                if (existing.any { it == query || query.contains(it) || it.contains(query) })
                    "ลบรายการเตือน $query เรียบร้อยแล้วค่ะ"
                else
                    null
            },
            consumePendingConfirmation = { null },
            getEmergencyCallResponse = { "" }
        )
    }

    @Test
    fun testDeleteReminder_prefixWithSpace() {
        // "ลบ เตือน หมอนัด" — spaces between words (root cause bug)
        val r = handleDelete("ลบ เตือน หมอนัด")
        assertNotNull("ลบ เตือน หมอนัด should not return null", r)
        assertTrue("Should confirm deletion, got: $r", r!!.contains("ลบ") || r.contains("เรียบร้อย"))
    }

    @Test
    fun testDeleteReminderCommandDetection_compactsWhitespace() {
        assertTrue(LocalAssistantBot.isDeleteReminderCommand("ลบ เตือน หมอนัด"))
        assertTrue(LocalAssistantBot.isDeleteReminderCommand("  ยกเลิก การเตือน กินยา "))
    }

    @Test
    fun testDeleteReminder_withDateAppended() {
        // "ลบเตือนหมอนัดวันที่ 29 มิถุนายน" — date should be stripped from title
        val r = handleDelete("ลบเตือนหมอนัดวันที่ 29 มิถุนายน")
        assertNotNull("ลบเตือนหมอนัดวันที่ 29 มิถุนายน should not return null", r)
        // Should NOT pass the full "หมอนัดวันที่29มิถุนายน" as query — stripped to "หมอนัด"
        assertTrue("Should confirm deletion (not fail), got: $r", r!!.contains("ลบ") || r.contains("เรียบร้อย"))
    }

    @Test
    fun testStripDateTimeFromTitle_removesDateAndTimeDetails() {
        assertEquals("หมอนัด", LocalAssistantBot.stripDateTimeFromTitle("หมอนัดวันที่ 29 มิถุนายน"))
        assertEquals("หมอนัด", LocalAssistantBot.stripDateTimeFromTitle("หมอนัดวันที่29มิถุนายนเวลา09:00น"))
    }

    @Test
    fun testDeleteReminder_withTimeAppended() {
        // "ลบเตือนหมอนัดวันที่29มิถุนายนเวลา09:00น" (compacted, no spaces)
        val r = handleDelete("ลบเตือนหมอนัดวันที่29มิถุนายนเวลา09:00น")
        assertNotNull(r)
        assertTrue("Should confirm deletion, got: $r", r!!.contains("ลบ") || r.contains("เรียบร้อย"))
    }

    @Test
    fun testDeleteReminder_naturalPatternEaoOok() {
        // "เอาเตือนออกกำลังกายออก"
        val r = handleDelete("เอาเตือนออกกำลังกายออก")
        assertNotNull("เอาเตือนออกกำลังกายออก should not return null", r)
        assertTrue("Should confirm deletion, got: $r", r!!.contains("ลบ") || r.contains("เรียบร้อย"))
    }

    @Test
    fun testDeleteReminder_naturalPatternMaiAo() {
        // "ไม่เอาเตือนกินยาแล้ว"
        val r = handleDelete("ไม่เอาเตือนกินยาแล้ว")
        assertNotNull("ไม่เอาเตือนกินยาแล้ว should not return null", r)
        assertTrue("Should confirm deletion, got: $r", r!!.contains("ลบ") || r.contains("เรียบร้อย"))
    }

    @Test
    fun testDeleteReminder_nonexistent() {
        // "ลบเตือนไม่มีจริง" — not in list → should return "ยังไม่พบ"
        val r = handleDelete("ลบเตือนไม่มีจริง")
        assertNotNull(r)
        assertTrue("Should report not found, got: $r", r!!.contains("ไม่พบ"))
    }

    @Test
    fun testDeleteReminder_blankTitlePromptsUser() {
        // "ลบเตือน" with nothing after — should ask which reminder
        val r = handleDelete("ลบเตือน")
        assertNotNull(r)
        assertTrue("Should ask for clarification, got: $r", r!!.contains("กรุณา") || r.contains("บอก"))
    }

    @Test
    fun testDeleteReminder_alternativeKeywords() {
        // "ยกเลิกการเตือน กินยา", "เคลียร์เตือนกินยา", "ลบตัวเตือนหมอนัด"
        for (cmd in listOf("ยกเลิกการเตือนกินยา", "เคลียร์เตือนกินยา", "ลบตัวเตือนหมอนัด")) {
            val r = handleDelete(cmd)
            assertNotNull("$cmd should not return null", r)
            assertTrue("$cmd should confirm deletion, got: $r", r!!.contains("ลบ") || r.contains("เรียบร้อย"))
        }
    }
}





