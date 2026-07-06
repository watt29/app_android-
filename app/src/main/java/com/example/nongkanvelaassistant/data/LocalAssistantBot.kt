package com.example.nongkanvelaassistant.data

import com.example.nongkanvelaassistant.ui.DeviceContact
import com.example.nongkanvelaassistant.ui.SheetCommand
import com.example.nongkanvelaassistant.ui.ScamNumberEntry
import com.example.nongkanvelaassistant.ui.ScamSourceReference
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale

class LocalAssistantBot {
    companion object {
        private const val SELF_APP_PACKAGE = "com.example.nongkanvelaassistant"
        val DELETE_REMINDER_PREFIXES = listOf(
            "ลบเตือน", "ยกเลิกเตือน", "ลบรายการเตือน", "ยกเลิกรายการเตือน",
            "ลบการเตือน", "ยกเลิกการเตือน", "ลบตัวเตือน",
            "ยกเลิกตัวเตือน", "เคลียร์เตือน", "ลบนาฬิกาเตือน", "ยกเลิกนาฬิกาเตือน"
        )
        fun compactWhitespace(text: String): String = text.replace(Regex("\\s+"), "")

        fun isDeleteReminderCommand(text: String): Boolean {
            val compacted = compactWhitespace(text.trim())
            return DELETE_REMINDER_PREFIXES.any { compacted.startsWith(it) }
        }

        fun isReminderCommand(text: String): Boolean {
            val compacted = compactWhitespace(text.trim())
            return compacted.contains("เตือน") ||
                compacted.contains("แจ้งเตือน") ||
                compacted.contains("ช่วยเตือน") ||
                compacted.contains("ตั้งเตือน") ||
                compacted.contains("ปลุก") ||
                compacted.contains("นาฬิกาปลุก") ||
                compacted.contains("หมอนัด") ||
                compacted.contains("นัดหมอ") ||
                compacted.contains("วัคซีน") ||
                Regex("อีก\\d+(?:ชั่วโมง|ชม\\.?|นาที)").containsMatchIn(compacted)
        }

        fun isReminderContinuationCommand(text: String): Boolean {
            val compacted = compactWhitespace(text.trim())
            if (compacted.isBlank()) return false
            if (listOf(
                "เปลี่ยนเป็น",
                "เปลี่ยนเวลาเป็น",
                "เปลี่ยนเวลา",
                "เลื่อนไป",
                "ย้ายไป",
                "ขอเป็น",
                "แทนเป็น",
                "เอาเป็น",
                "ปรับเป็น",
                "แทน"
            ).any { compacted.contains(it) }) {
                return true
            }
            return isStandaloneReminderTimeFragment(compacted)
        }

        fun shouldReusePendingReminderTitle(text: String): Boolean {
            val compacted = compactWhitespace(text.trim())
            if (compacted.isBlank()) return false
            if (isReminderContinuationCommand(compacted)) return true
            return isStandaloneReminderTimeFragment(compacted)
        }

        private fun isStandaloneReminderTimeFragment(text: String): Boolean {
            val normalized = compactWhitespace(text.trim())
            if (normalized.isBlank()) return false

            val remaining = normalized
                .replace(
                    Regex(
                        "(\\d{1,2}[:.]\\d{1,2}|\\d{1,2}\\s*โมง(?:\\s*\\d{1,2}\\s*นาที?)?|\\d+\\s*(?:ชั่วโมง|ชม\\.?|นาที)|พรุ่งนี้|วันนี้|มะรืน|เช้า|บ่าย|เย็น|ค่ำ|ตี\\s*\\d)"
                    ),
                    " "
                )
                .replace(Regex("[\\d/:.]+"), " ")
                .replace(Regex("\\s+"), "")

            return remaining.isBlank()
        }

        fun stripReminderContinuationPhrases(text: String): String {
            var result = compactWhitespace(text.trim())
            val phrases = listOf(
                "เปลี่ยนเวลาเป็น",
                "เปลี่ยนเป็น",
                "เปลี่ยนเวลา",
                "เลื่อนไป",
                "ย้ายไป",
                "ขอเป็น",
                "แทนเป็น",
                "เอาเป็น",
                "ปรับเป็น"
            )
            phrases.forEach { phrase ->
                result = result.replace(phrase, "")
            }
            result = result
                .replace("แทน", "")
                .replace("ด้วย", "")
                .replace("ที", "")
                .replace("หน่อย", "")
                .trim()
            return result
        }

        fun buildReminderContinuationCommand(title: String, text: String): String {
            val followUp = stripReminderContinuationPhrases(text)
            return if (followUp.isBlank()) {
                "เตือน $title"
            } else {
                "เตือน $title $followUp".trim()
            }
        }

        fun stripDateTimeFromTitle(title: String): String {
            return title
                .replace(Regex("วันที่.*"), "")
                .replace(Regex("เวลา.*"), "")
                .replace(Regex("\\d{1,2}[/:.]\\d{1,2}.*"), "")
                .replace(Regex("มกราคม|กุมภาพันธ์|มีนาคม|เมษายน|พฤษภาคม|มิถุนายน|กรกฎาคม|สิงหาคม|กันยายน|ตุลาคม|พฤศจิกายน|ธันวาคม"), "")
                .replace(Regex("\\d+"), "")
                .replace("น.", "")
                .replace(" น", "")
                .trim()
        }

        fun formatReminderConfirmation(kind: String, title: String, formattedTime: String): String {
            val cleanTitle = title.trim()
            return when (kind) {
                "medicine" -> {
                    "ตั้งเตือนกินยา วันที่ $formattedTime ให้แล้วค่ะ"
                }
                "appointment" -> "ตั้งเตือนนัดหมาย $cleanTitle วันที่ $formattedTime ให้แล้วค่ะ"
                "vaccine" -> "ตั้งเตือนฉีดวัคซีน $cleanTitle วันที่ $formattedTime ให้แล้วค่ะ"
                else -> "ตั้งเตือน $cleanTitle วันที่ $formattedTime ให้แล้วค่ะ"
            }
        }
    }

    fun handle(
        text: String,
        contacts: List<DeviceContact>,
        apps: List<Pair<String, String>>,
        sheetCommands: List<SheetCommand> = emptyList(),
        scamNumbers: List<ScamNumberEntry> = emptyList(),
        scamSources: List<ScamSourceReference> = emptyList(),
        contentPreferences: List<String> = emptyList(),
        searchContacts: (String) -> List<DeviceContact>,
        updateContact: (DeviceContact, String, String) -> String,
        deleteContact: (DeviceContact) -> String,
        createContact: (String, String) -> String,
        readLatestMessage: () -> String,
        getBatteryStatus: () -> String,
        getCurrentTimeText: () -> String,
        getTodayDateText: () -> String,
        getDateAnswerText: (String) -> String = { getTodayDateText() },
        getReminderSummaryText: () -> String = { "ยังไม่มีรายการเตือนค่ะ" },
        removeReminderByQuery: (String) -> String? = { null },
        consumePendingConfirmation: (String) -> String?,
        getEmergencyCallResponse: () -> String
    ): String? {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return null
        val canonicalText = preProcessAndCorrectIntent(cleanText)

        consumePendingConfirmation(canonicalText)?.let { return it }
        handleGreeting(canonicalText)?.let { return it }
        handleHelp(canonicalText)?.let { return it }
        handleEmergencyCommand(canonicalText, getEmergencyCallResponse)?.let { return it }
        handleBatteryCommand(canonicalText, getBatteryStatus)?.let { return it }
        handleDutyCommand(canonicalText)?.let { return it }
        handleTimeDateCommand(canonicalText, getCurrentTimeText, getTodayDateText, getDateAnswerText)?.let { return it }
        handleReminderDeleteCommand(canonicalText, removeReminderByQuery)?.let { return it }
        handleReminderSummaryCommand(canonicalText, getReminderSummaryText)?.let { return it }
        handleReminderCommand(canonicalText)?.let { return it }
        handleAppointmentInquiryCommand(canonicalText, getReminderSummaryText)?.let { return it }
        handleVolumeCommand(canonicalText)?.let { return it }
        handleAlarmCommand(canonicalText)?.let { return it }
        handleTimerCommand(canonicalText)?.let { return it }
        handleWebSearchCommand(canonicalText)?.let { return it }
        handleImageSearchCommand(canonicalText)?.let { return it }
        handleYouTubeSearchCommand(canonicalText)?.let { return it }
        handleMapCommand(canonicalText)?.let { return it }
        handleSettingsCommand(canonicalText)?.let { return it }
        handleScamNumberCommand(canonicalText, scamNumbers)?.let { return it }
        handleScamSourceCommand(canonicalText, scamSources)?.let { return it }
        handleSheetCommands(canonicalText, sheetCommands, apps)?.let { return it }
        handleImportContactsCommand(canonicalText)?.let { return it }
        handleSendSmsCommand(canonicalText, contacts)?.let { return it }
        handleReadMessageCommand(canonicalText, readLatestMessage)?.let { return it }
        handleContactCommand(canonicalText, contacts, scamNumbers, sheetCommands, searchContacts, updateContact, deleteContact, createContact)?.let { return it }
        handleCallCommand(canonicalText, contacts, scamNumbers)?.let { return it }
        handleFlashlightCommand(canonicalText)?.let { return it }
        handleCameraCommand(canonicalText)?.let { return it }
        handleOpenAppCommand(canonicalText, apps)?.let { return it }
        handleContentPreferenceCommand(canonicalText, contentPreferences)?.let { return it }
        handleVagueOptionsQuestion(canonicalText)?.let { return it }
        handleChitchat(canonicalText)?.let { return it }

        return null
    }

    private fun handleImportContactsCommand(text: String): String? {
        val normalizedText = normalizeContactName(text)
        val contactIntentWords = listOf("รายชื่อ", "ชื่อ", "เบอร์", "ติดต่อ")
        val importWords = listOf("นำเข้า", "ดึง", "ซิงก์", "ซิงค์", "อัปเดต", "อัพเดต", "เข้ารายชื่อ")
        val sheetWords = listOf("ชีท", "sheet", "ทิศ")
        val looksLikeContactImport = contactIntentWords.any { normalizedText.contains(it) } &&
            (importWords.any { normalizedText.contains(it) } || sheetWords.any { normalizedText.contains(it) })
        return when {
            normalizedText.contains("นำเข้ารายชื่อจากชีท") ||
                normalizedText.contains("ดึงรายชื่อจากชีท") ||
                normalizedText.contains("ซิงค์รายชื่อ") ||
                normalizedText.contains("ซิงก์รายชื่อ") ||
                normalizedText.contains("อัปเดตรายชื่อจากชีท") ||
                normalizedText.contains("อัพเดตรายชื่อจากชีท") ||
                looksLikeContactImport ->
                "[ACTION:IMPORT_CONTACTS_FROM_SHEET] กำลังนำเข้ารายชื่อจากชีทให้ค่ะ"
            else -> null
        }
    }

    private fun handleGreeting(text: String): String? {
        return when {
            text == "สวัสดี" || text == "สวัสดีครับ" || text == "สวัสดีค่ะ" ->
                "สวัสดีค่ะ อุ่นใจพร้อมช่วยดูแลและช่วยสั่งงานให้แล้วค่ะ"
            text.contains("ขอบคุณ") ->
                "ยินดีเสมอค่ะ"
            else -> null
        }
    }

    private fun handleSheetCommands(
        text: String,
        sheetCommands: List<SheetCommand>,
        apps: List<Pair<String, String>>
    ): String? {
        val normalizedText = normalizeContactName(text)
        if (normalizedText.isBlank()) return null
        val hasActionVerb = normalizedText.contains("โทร") ||
            normalizedText.contains("เปิด") ||
            normalizedText.contains("ส่ง") ||
            normalizedText.contains("เล่น") ||
            normalizedText.contains("ตั้ง") ||
            normalizedText.contains("หา")

        for (sc in sheetCommands) {
            val status = sc.status.trim()
            val isActive = status == "ใช้งาน" || status == "เปิดใช้งาน" || status == "active" || status == "Active" || status == "1"
            if (!isActive) continue

            val normalizedCmd = normalizeContactName(preProcessAndCorrectIntent(sc.command))
            if (normalizedCmd.isBlank()) continue

            if ((normalizedText == normalizedCmd || normalizedText.contains(normalizedCmd)) && hasActionVerb) {
                // Case 1: Call command
                if (sc.number.isNotBlank()) {
                    val targetName = if (sc.name.isNotBlank()) sc.name.trim() else sc.command.trim()
                    return "[ACTION:CALL:${sc.number.trim()}] อุ่นใจกำลังต่อสายโทรออกหา${targetName}ให้ค่ะ"
                }
            }

            if (normalizedText == normalizedCmd || normalizedText.contains(normalizedCmd)) {
                // Case 2: Open App command or Text response
                if (sc.name.isNotBlank()) {
                    val targetName = sc.name.trim()
                    if (targetName.startsWith("com.") || targetName.contains(".")) {
                        return "[ACTION:OPEN_APP:$targetName] กำลังเปิดให้ตามคำสั่งค่ะ"
                    }
                    val normalizedTarget = normalizeAppName(targetName)
                    val matchedApp = apps.find { normalizeAppName(it.first) == normalizedTarget }
                    if (matchedApp != null) {
                        return "[ACTION:OPEN_APP:${matchedApp.second}] กำลังเปิด${matchedApp.first}ให้ตามคำสั่งค่ะ"
                    }

                    // Case 3: Text response
                    return sc.name
                }
            }
        }
        return null
    }


    private fun handleHelp(text: String): String? {
        return when {
            text.contains("ทำอะไรได้บ้าง") || text.contains("ช่วยอะไรได้บ้าง") ->
                "อุ่นใจช่วยโทร ส่งข้อความ เพิ่มรายชื่อ เปิดแอป เปิดไฟฉาย เปิดกล้อง ค้นหาเบอร์ แก้ชื่อ แก้เบอร์ ลบรายชื่อ เปิดเสียง ปิดเสียง เพิ่มเสียง ลดเสียง สั่น ตั้งปลุก ตั้งเวลา ตั้งเตือนกินยา/นัด/วัคซีน ดูรายการเตือน ค้นหาเว็บ ค้นหาภาพ ค้นหายูทูป เปิดแผนที่ นำทาง เปิดหน้าตั้งค่า อ่านข้อความ ดูแบต บอกวันเวลา และช่วยฉุกเฉินได้ค่ะ"
            else -> null
        }
    }

    private fun handleEmergencyCommand(text: String, getEmergencyCallResponse: () -> String): String? {
        return if (
            text == "ช่วยด้วย" ||
            text.contains("ฉุกเฉิน") ||
            text.contains("หกล้ม") ||
            text.contains("ตกเตียง") ||
            text.contains("ไม่สบายมาก") ||
            text.contains("เรียกคนช่วย") ||
            text.contains("เจ็บหน้าอก") ||
            text.contains("หายใจไม่ออก") ||
            text.contains("เป็นลม") ||
            text.contains("หัวใจวาย") ||
            text.contains("ช่วยพยุง") ||
            text.contains("เรียกรถพยาบาล") ||
            text.contains("โทรหาหมอ") ||
            text.contains("ล้ม")
        ) {
            getEmergencyCallResponse()
        } else {
            null
        }
    }

    private fun handleBatteryCommand(text: String, getBatteryStatus: () -> String): String? {
        return if (
            text.contains("แบต") ||
            text.contains("แบตเตอรี่") ||
            text.contains("เหลือกี่เปอร์เซ็นต์")
        ) {
            getBatteryStatus()
        } else {
            null
        }
    }

    private fun handleTimeDateCommand(
        text: String,
        getCurrentTimeText: () -> String,
        getTodayDateText: () -> String,
        getDateAnswerText: (String) -> String
    ): String? {
        val cleanText = text.trim()
        val compactText = cleanText.replace(" ", "")
        val weekdayOnlyQuery = Regex("^(?:วัน)?(อาทิตย์|จันทร์|อังคาร|พุธ|พฤหัสบดี|พฤหัส|ศุกร์|เสาร์)(?:หน้า)?$").matches(compactText)
        val hasWeekdayReference = Regex("วัน(?:ที่)?\\s*(อาทิตย์|จันทร์|อังคาร|พุธ|พฤหัสบดี|พฤหัส|ศุกร์|เสาร์)(?:หน้า)?").containsMatchIn(cleanText)
        val hasDateQuestionHint = listOf("วันอะไร", "วันที่", "ตรงกับ", "คือ", "อีกกี่วัน").any { compactText.contains(it) }
        val isRelativeDateQuestion = cleanText.contains("พรุ่งนี้") || cleanText.contains("มะรืน")
        val shouldAnswerDateQuery = weekdayOnlyQuery || isRelativeDateQuestion || (hasWeekdayReference && hasDateQuestionHint)

        if (isReminderCommand(cleanText)) return null

        return when {
            cleanText.contains("กี่โมง") || cleanText.contains("เวลาเท่าไหร่") || cleanText.contains("ขอดูเวลา") || cleanText.contains("บอกเวลา") -> getCurrentTimeText()
            shouldAnswerDateQuery -> getDateAnswerText(cleanText)
            cleanText.contains("วันนี้วันอะไร") ||
                cleanText.contains("วันที่เท่าไหร่") ||
                cleanText.contains("วันนี้วันที่") ||
                cleanText.contains("ปีอะไร") ||
                cleanText.contains("พ.ศ.") ||
                cleanText.contains("พศ อะไร") ||
                cleanText.contains("เดือนอะไร") ||
                compactText == "วันอะไร" ||
                compactText == "วันที่อะไร" ||
                compactText == "วันนี้วันอะไร" ||
                compactText == "วันนี้วันที่เท่าไหร่" ||
                compactText == "วันนี้วันที่อะไร" -> getTodayDateText()
            else -> null
        }
    }

    private fun handleDutyCommand(text: String): String? {
        if (!isDutyQuestion(text)) return null
        val targetDate = parseDutyTargetDate(text) ?: return null
        val dutyName = calculateDutyOfficer(targetDate)
        return "วันที่ ${formatThaiBuddhistFullDate(targetDate)} เวรอำนวยการคือ $dutyName"
    }

    private fun isDutyQuestion(text: String): Boolean {
        if (isReminderCommand(text)) return false

        val hasDutyWord = text.contains("เวร")
        val hasDutyQuestionPhrase = text.contains("ใครเข้าเวร") || text.contains("ใครอยู่เวร")
        val hasWeekdayReference = Regex("วัน(?:อาทิตย์|จันทร์|อังคาร|พุธ|พฤหัสบดี|พฤหัส|ศุกร์|เสาร์)").containsMatchIn(text)

        return text == "เวรวันนี้" ||
            text == "เวรพรุ่งนี้" ||
            text.contains("เวรอำนวยการ") ||
            text.contains("เวรใคร") ||
            hasDutyQuestionPhrase ||
            (hasDutyWord && hasWeekdayReference)
    }

    private fun parseDutyTargetDate(text: String): Calendar? {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (text.contains("วันนี้")) {
            return now
        }
        if (text.contains("พรุ่งนี้")) {
            return (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        }
        if (text.contains("มะรืน")) {
            return (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 2) }
        }

        val numericDate = Regex("(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?").find(text)
        if (numericDate != null) {
            val day = numericDate.groupValues[1].toIntOrNull() ?: return null
            val month = numericDate.groupValues[2].toIntOrNull() ?: return null
            val yearRaw = numericDate.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
            val year = parseThaiOrGregorianYear(yearRaw, now.get(Calendar.YEAR))
            return (now.clone() as Calendar).apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
            }
        }

        val thaiMonthMap = mapOf(
            "มกราคม" to Calendar.JANUARY,
            "ม.ค." to Calendar.JANUARY,
            "กุมภาพันธ์" to Calendar.FEBRUARY,
            "ก.พ." to Calendar.FEBRUARY,
            "มีนาคม" to Calendar.MARCH,
            "มี.ค." to Calendar.MARCH,
            "เมษายน" to Calendar.APRIL,
            "เม.ย." to Calendar.APRIL,
            "พฤษภาคม" to Calendar.MAY,
            "พ.ค." to Calendar.MAY,
            "มิถุนายน" to Calendar.JUNE,
            "มิ.ย." to Calendar.JUNE,
            "กรกฎาคม" to Calendar.JULY,
            "ก.ค." to Calendar.JULY,
            "สิงหาคม" to Calendar.AUGUST,
            "ส.ค." to Calendar.AUGUST,
            "กันยายน" to Calendar.SEPTEMBER,
            "ก.ย." to Calendar.SEPTEMBER,
            "ตุลาคม" to Calendar.OCTOBER,
            "ต.ค." to Calendar.OCTOBER,
            "พฤศจิกายน" to Calendar.NOVEMBER,
            "พ.ย." to Calendar.NOVEMBER,
            "ธันวาคม" to Calendar.DECEMBER,
            "ธ.ค." to Calendar.DECEMBER
        )
        val thaiMonthDate = Regex("(\\d{1,2})\\s*(ม\\.ค\\.|มกราคม|ก\\.พ\\.|กุมภาพันธ์|มี\\.ค\\.|มีนาคม|เม\\.ย\\.|เมษายน|พ\\.ค\\.|พฤษภาคม|มิ\\.ย\\.|มิถุนายน|ก\\.ค\\.|กรกฎาคม|ส\\.ค\\.|สิงหาคม|ก\\.ย\\.|กันยายน|ต\\.ค\\.|ตุลาคม|พ\\.ย\\.|พฤศจิกายน|ธ\\.ค\\.|ธันวาคม)(?:\\s*(\\d{2,4}))?").find(text)
        if (thaiMonthDate != null) {
            val day = thaiMonthDate.groupValues[1].toIntOrNull() ?: return null
            val month = thaiMonthMap[thaiMonthDate.groupValues[2]] ?: return null
            val yearRaw = thaiMonthDate.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
            val year = parseThaiOrGregorianYear(yearRaw, now.get(Calendar.YEAR))
            return (now.clone() as Calendar).apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
        }

        val weekdayMap = linkedMapOf(
            "วันพฤหัสบดี" to Calendar.THURSDAY,
            "วันพฤหัส" to Calendar.THURSDAY,
            "พฤหัสบดี" to Calendar.THURSDAY,
            "พฤหัส" to Calendar.THURSDAY,
            "วันอาทิตย์" to Calendar.SUNDAY,
            "อาทิตย์" to Calendar.SUNDAY,
            "วันจันทร์" to Calendar.MONDAY,
            "จันทร์" to Calendar.MONDAY,
            "วันอังคาร" to Calendar.TUESDAY,
            "อังคาร" to Calendar.TUESDAY,
            "วันพุธ" to Calendar.WEDNESDAY,
            "พุธ" to Calendar.WEDNESDAY,
            "วันศุกร์" to Calendar.FRIDAY,
            "ศุกร์" to Calendar.FRIDAY,
            "วันเสาร์" to Calendar.SATURDAY,
            "เสาร์" to Calendar.SATURDAY
        )

        val weekdayEntry = weekdayMap.entries.firstOrNull { text.contains(it.key) }
        if (weekdayEntry != null) {
            val diff = (weekdayEntry.value - now.get(Calendar.DAY_OF_WEEK) + 7) % 7
            val daysToAdd = when {
                text.contains("หน้า") && diff == 0 -> 7
                text.contains("หน้า") -> diff + 7
                else -> diff
            }
            return (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, daysToAdd) }
        }

        return null
    }

    private fun parseThaiOrGregorianYear(yearRaw: String?, defaultYear: Int): Int {
        if (yearRaw.isNullOrBlank()) return defaultYear
        val parsedYear = yearRaw.toIntOrNull() ?: return defaultYear
        return when {
            yearRaw.length == 2 -> 2500 + parsedYear - 543
            parsedYear > 2500 -> parsedYear - 543
            else -> parsedYear
        }
    }

    private fun calculateDutyOfficer(targetDate: Calendar): String {
        return when (targetDate.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "พ.ต.ท.วัชระ ศรีเปี่ยม (061-093-5599)"
            Calendar.TUESDAY -> "พ.ต.ท.จีรวัฒน์ พลอาจ (093-532-5959)"
            Calendar.WEDNESDAY -> {
                if (targetDate.get(Calendar.DAY_OF_MONTH) % 2 != 0) {
                    "พ.ต.ต.คมสัน เค็งชัยภูมิ (089-0777761)"
                } else {
                    "พ.ต.ท.กัมปนาท ท้ายวัด (098-284-3577)"
                }
            }
            Calendar.THURSDAY -> "พ.ต.ท.บุญเรือง พันธนู (081-692-8034)"
            Calendar.FRIDAY -> "พ.ต.ท.จิรัชธพนธ์ ปิณฑศิริ (063-228-5558)"
            Calendar.SATURDAY, Calendar.SUNDAY -> {
                val baseDate = Calendar.getInstance().apply {
                    set(2026, Calendar.JUNE, 6, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val diffMillis = targetDate.timeInMillis - baseDate.timeInMillis
                val diffWeeks = (diffMillis / (7L * 24L * 60L * 60L * 1000L)).toInt()
                val weekendRotation = listOf(
                    Calendar.SATURDAY to "พ.ต.ท.วัชระ ศรีเปี่ยม (061-093-5599)",
                    Calendar.SUNDAY to "พ.ต.ท.กัมปนาท ท้ายวัด (098-284-3577)",
                    Calendar.SATURDAY to "พ.ต.ท.จีรวัฒน์ พลอาจ (093-532-5959)",
                    Calendar.SUNDAY to "พ.ต.ต.คมสัน เค็งชัยภูมิ (089-0777761)",
                    Calendar.SATURDAY to "พ.ต.ท.บุญเรือง พันธนู (081-692-8034)",
                    Calendar.SUNDAY to "พ.ต.ท.จิรัชธพนธ์ ปิณฑศิริ (063-228-5558)"
                )
                val pairIndex = ((diffWeeks % 3) + 3) % 3
                val targetDay = targetDate.get(Calendar.DAY_OF_WEEK)
                when (pairIndex) {
                    0 -> if (targetDay == Calendar.SATURDAY) weekendRotation[0].second else weekendRotation[1].second
                    1 -> if (targetDay == Calendar.SATURDAY) weekendRotation[2].second else weekendRotation[3].second
                    else -> if (targetDay == Calendar.SATURDAY) weekendRotation[4].second else weekendRotation[5].second
                }
            }
            else -> "ยังไม่พบข้อมูลเวรค่ะ"
        }
    }

    private fun formatThaiBuddhistFullDate(date: Calendar): String {
        val months = listOf(
            "มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน",
            "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม"
        )
        val day = date.get(Calendar.DAY_OF_MONTH)
        val month = months[date.get(Calendar.MONTH)]
        val buddhistYear = date.get(Calendar.YEAR) + 543
        return "$day $month $buddhistYear"
    }

    private fun handleVolumeCommand(text: String): String? {
        val cleanText = text.trim()
        if (cleanText.contains("ปิดเสียง") || cleanText.contains("ปิดเสียงเรียกเข้า") || cleanText.contains("ปิดเสียงโทรศัพท์") || cleanText.contains("เงียบเสียง")) {
            return "[ACTION:VOLUME_OFF] ปิดเสียงให้แล้วค่ะ"
        }
        if (cleanText.contains("เปิดเสียง") || cleanText.contains("เปิดเสียงเรียกเข้า") || cleanText.contains("เปิดเสียงโทรศัพท์") || cleanText.contains("ขอเสียงหน่อย")) {
            return "[ACTION:VOLUME_ON] เปิดเสียงให้แล้วค่ะ"
        }
        if (cleanText.contains("เพิ่มเสียง") || cleanText.contains("เร่งเสียง") || cleanText.contains("เสียงดังขึ้น") || cleanText.contains("ดังๆ หน่อย") || cleanText.contains("ดังขึ้น")) {
            return "[ACTION:VOLUME_UP] เพิ่มเสียงให้แล้วค่ะ"
        }
        if (cleanText.contains("ลดเสียง") || cleanText.contains("เบาเสียง") || cleanText.contains("เสียงเบาลง") || cleanText.contains("เบาลง") || cleanText.contains("เบาๆ หน่อย")) {
            return "[ACTION:VOLUME_DOWN] ลดเสียงให้แล้วค่ะ"
        }
        if (cleanText.contains("เปิดสั่น") || cleanText.contains("สั่นอย่างเดียว") || cleanText.contains("ตั้งเป็นสั่น") || cleanText.contains("โหมดสั่น")) {
            return "[ACTION:VIBRATE_MODE] เปิดโหมดสั่นให้แล้วค่ะ"
        }
        return null
    }

    private fun handleAlarmCommand(text: String): String? {
        if (!(text.contains("ปลุก") || text.contains("นาฬิกาปลุก"))) return null

        // 1. Try our new Thai time parser
        parseThaiTime(text)?.let { (hour, minute) ->
            return "[ACTION:SET_ALARM:$hour:$minute] ตั้งปลุกเวลา ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} ให้แล้วค่ะ"
        }

        // 2. Fall back to digits hh:mm pattern
        val hhmmPattern = Regex("(\\d{1,2})[:.](\\d{1,2})")
        hhmmPattern.find(text)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            if (hour in 0..23 && minute in 0..59) {
                return "[ACTION:SET_ALARM:$hour:$minute] ตั้งปลุกเวลา ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} ให้แล้วค่ะ"
            }
        }

        // 3. Fall back to digits o'clock pattern
        val oClockPattern = Regex("(\\d{1,2})\\s*โมง(?:\\s*(\\d{1,2})\\s*นาที?)?")
        oClockPattern.find(text)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            if (hour in 0..23 && minute in 0..59) {
                return "[ACTION:SET_ALARM:$hour:$minute] ตั้งปลุกเวลา ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} ให้แล้วค่ะ"
            }
        }

        return "กรุณาบอกเวลาที่ต้องการตั้งปลุกด้วยค่ะ เช่น ตั้งปลุก 6 โมง หรือ ตั้งปลุก 06:30 ค่ะ"
    }

    private fun handleReminderCommand(text: String): String? {
        val hasRelative = Regex("อีก\\s*\\d+\\s*(?:ชั่วโมง|ชม\\.?|นาที)").containsMatchIn(text)
        val hasReminderTriggerWord = text.contains("เตือน") || text.contains("ตั้งเตือน") || text.contains("แจ้งเตือน") || text.contains("ช่วยเตือน") || hasRelative
        val hasMedicalReminderWord = text.contains("หมอนัด") || text.contains("นัดหมอ") || text.contains("ใบนัด") || text.contains("วัคซีน") || text.contains("ฉีดวัคซีน") || text.contains("ฉีดเข็ม") || text.contains("ไปหาหมอ") || text.contains("พบหมอ")
        val hasReminderDateTime = parseReminderDateTime(text, Calendar.getInstance()) != null
        if (!(hasReminderTriggerWord || (hasMedicalReminderWord && hasReminderDateTime))) return null

        val repeatDaily = text.contains("ทุกวัน") || text.contains("ทุกวันเวลา") || text.contains("เป็นประจำ")
        val kind = when {
            text.contains("วัคซีน") || text.contains("ฉีดเข็ม") -> "vaccine"
            text.contains("นัด") || text.contains("หาหมอ") || text.contains("พบหมอ") -> "appointment"
            text.contains("ยา") || text.contains("กิน") || text.contains("ทานยา") || text.contains("รับยา") -> "medicine"
            else -> "general"
        }

        val now = Calendar.getInstance()
        val title = extractReminderTitle(text, kind)
        if (title.isBlank()) {
            return "[ACTION:ASK_CLARIFICATION:${kind}|0|] ต้องการให้ตั้งเตือนวันไหนและกี่โมงคะ?"
        }
        if (!hasExplicitReminderTime(text)) {
            return "[ACTION:ASK_CLARIFICATION:${kind}|0|${title}] ต้องการให้ตั้งเตือน$title วันไหนและกี่โมงคะ?"
        }
        if (!hasRelative) {
            if ((kind == "appointment" || kind == "vaccine") && !hasExplicitReminderDate(text)) {
                return "[ACTION:ASK_CLARIFICATION:${kind}|0|${title}] ต้องการให้ตั้งเตือน$title วันไหนและกี่โมงคะ?"
            }
            if (kind == "medicine" && !repeatDaily && !hasExplicitReminderDate(text)) {
                return "[ACTION:ASK_CLARIFICATION:${kind}|0|${title}] ต้องการให้ตั้งเตือน$title วันไหนและกี่โมงคะ?"
            }
        }

        val trigger = parseReminderDateTime(text, now) ?: return "[ACTION:ASK_CLARIFICATION:${kind}|0|${title}] ต้องการให้ตั้งเตือน$title วันไหนและกี่โมงคะ?"
        val finalTrigger = Calendar.getInstance().apply { timeInMillis = trigger }
        if (finalTrigger.timeInMillis <= now.timeInMillis) {
            if (repeatDaily) {
                finalTrigger.add(Calendar.DAY_OF_YEAR, 1)
            } else {
                finalTrigger.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val repeatMillis = if (repeatDaily) 24L * 60L * 60L * 1000L else 0L
        val encodedTitle = try {
            URLEncoder.encode(title, "UTF-8")
        } catch (_: Exception) {
            title
        }
        return "[ACTION:SET_REMINDER:${kind}|${repeatMillis}|${finalTrigger.timeInMillis}|${encodedTitle}] ตั้งเตือน $title ให้แล้วค่ะ"
    }

    private fun handleReminderDeleteCommand(
        text: String,
        removeReminderByQuery: (String) -> String?
    ): String? {
        // Normalize: ลบ space ทั้งหมดก่อน match เช่น "ลบ เตือน" == "ลบเตือน"
        val compacted = compactWhitespace(text)

        // รูปแบบที่ 1: ขึ้นต้นด้วยคำสั่งลบ เช่น "ลบเตือนกินยา", "ยกเลิกเตือน นัดหมอ"
        val matchedPrefix = DELETE_REMINDER_PREFIXES.firstOrNull { compacted.startsWith(it) }
        if (matchedPrefix != null) {
            val rawTarget = compacted.removePrefix(matchedPrefix).trim()
            // Strip วันที่/เวลา ออกจาก title เช่น "หมอนัดวันที่29มิถุนายน09:00" -> "หมอนัด"
            val target = stripDateTimeFromTitle(rawTarget)
            if (target.isBlank()) {
                return "กรุณาบอกชื่อรายการเตือนที่ต้องการลบด้วยค่ะ เช่น ลบเตือน กินยา หรือ ยกเลิกเตือน หมอนัดค่ะ"
            }
            return removeReminderByQuery(target) ?: "ยังไม่พบรายการเตือนที่ชื่อ $target ค่ะ"
        }

        // รูปแบบที่ 2: ประโยคธรรมชาติ เช่น "เอาเตือนกินยาออก", "ไม่เอาเตือนนัดหมอแล้ว"
        val naturalPatterns = listOf(
            Regex("เอา(.+)ออก"),         // greedy: "เอาเตือนออกกำลังกายออก" → "เตือนออกกำลังกาย"
            Regex("ไม่เอา(.+)แล้ว"),
            Regex("เอา(.+)ออกได้เลย")
        )
        val reminderKeywords = listOf("เตือน", "แจ้งเตือน", "นาฬิกาปลุก", "ปลุก")
        for (pattern in naturalPatterns) {
            val m = pattern.find(text) ?: continue
            val captured = m.groupValues[1].trim()
            if (reminderKeywords.any { captured.contains(it) }) {
                val title = reminderKeywords.fold(captured) { acc, kw ->
                    acc.removePrefix(kw).removeSuffix(kw).trim()
                }.let { stripDateTimeFromTitle(it) }.trim()
                if (title.isNotBlank()) {
                    return removeReminderByQuery(title) ?: "ยังไม่พบรายการเตือนที่ชื่อ $title ค่ะ"
                }
            }
        }
        return null
    }

    private fun handleReminderSummaryCommand(text: String, getReminderSummaryText: () -> String): String? {
        return if (
            text.contains("รายการเตือน") ||
            text.contains("เตือนอะไร") ||
            text.contains("ดูเตือน") ||
            text.contains("เตือนที่ตั้งไว้") ||
            text.contains("เตือนทั้งหมด")
        ) {
            getReminderSummaryText()
        } else {
            null
        }
    }

    private fun handleAppointmentInquiryCommand(text: String, getReminderSummaryText: () -> String): String? {
        val hasAppointmentWord = listOf("หมอนัด", "นัดหมอ", "ใบนัด", "วันนัด", "นัดวันที่", "นัดเมื่อไหร่", "วันหมอนัด")
            .any { text.contains(it) }
        if (!hasAppointmentWord) return null

        val hasExplicitDateOrTime = Regex("(\\d{1,2}[/-]\\d{1,2})|(\\d{1,2}[:.]\\d{1,2})|พรุ่งนี้|วันนี้").containsMatchIn(text)
        if (hasExplicitDateOrTime) return null

        val summary = getReminderSummaryText()
        return if (summary == "ยังไม่มีรายการเตือนค่ะ") {
            "ยังไม่พบวันนัดที่บันทึกไว้ค่ะ กรุณาบอกวันและเวลานัดให้ชัดเจน เช่น หมอนัด 25/06/2026 09:00 ค่ะ"
        } else {
            "ยังไม่พบวันนัดที่ชัดเจนจากข้อความนี้ค่ะ กรุณาบอกวันและเวลานัดให้ชัดเจน หรือพูดว่า ดูรายการเตือน เพื่อเช็กที่ตั้งไว้ค่ะ"
        }
    }

    private fun extractReminderTitle(text: String, kind: String): String {
        var result = text
            .replace("เตือน", "")
            .replace("ทุกวัน", "")
            .replace("เป็นประจำ", "")
            .replace("พรุ่งนี้", "")
            .replace("วันนี้", "")
            .replace("วันจันทร์ที่", "")
            .replace("วันอังคารที่", "")
            .replace("วันพุธที่", "")
            .replace("วันพฤหัสบดีที่", "")
            .replace("วันศุกร์ที่", "")
            .replace("วันเสาร์ที่", "")
            .replace("วันอาทิตย์ที่", "")
            .replace("วันจันทร์", "")
            .replace("วันอังคาร", "")
            .replace("วันพุธ", "")
            .replace("วันพฤหัสบดี", "")
            .replace("วันศุกร์", "")
            .replace("วันเสาร์", "")
            .replace("วันอาทิตย์", "")
            .replace("ตอนเช้า", "")
            .replace("ตอนกลางวัน", "")
            .replace("ตอนเย็น", "")
            .replace("ตอนค่ำ", "")
            .replace("เช้า", "")
            .replace("เย็น", "")
            .replace("ค่ำ", "")
            .replace("เวลา", "")
            .trim()

        result = result.replace(Regex("\\d{1,2}[:.]\\d{1,2}"), "")
            .replace(Regex("\\d{1,2}\\s*โมง(?:\\s*\\d{1,2}\\s*นาที?)?"), "")
            .replace(Regex("\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?"), "")
            .replace(Regex("\\d{1,2}\\s*(มกราคม|กุมภาพันธ์|มีนาคม|เมษายน|พฤษภาคม|มิถุนายน|กรกฎาคม|สิงหาคม|กันยายน|ตุลาคม|พฤศจิกายน|ธันวาคม)(?:\\s*\\d{2,4})?"), "")
            .replace(Regex("อีก\\s*\\d+\\s*(?:ชั่วโมง|ชม\\.?|นาที)"), "")
            .trim()

        if (result.startsWith("ว่าให้")) {
            result = result.removePrefix("ว่าให้").trim()
        } else if (result.startsWith("ว่า")) {
            result = result.removePrefix("ว่า").trim()
        } else if (result.startsWith("ให้")) {
            result = result.removePrefix("ให้").trim()
        }

        if (result.isBlank()) {
            return ""
        }
        return result
    }

    private fun parseReminderDateTime(text: String, now: Calendar): Long? {
        val relativeHourMatch = Regex("อีก\\s*(\\d+)\\s*(?:ชั่วโมง|ชม\\.?)").find(text)
        val relativeMinuteMatch = Regex("อีก\\s*(\\d+)\\s*นาที").find(text)
        if (relativeHourMatch != null || relativeMinuteMatch != null) {
            var addedMillis = 0L
            relativeHourMatch?.let {
                val hours = it.groupValues[1].toLongOrNull() ?: 0L
                addedMillis += hours * 60 * 60 * 1000
            }
            relativeMinuteMatch?.let {
                val minutes = it.groupValues[1].toLongOrNull() ?: 0L
                addedMillis += minutes * 60 * 1000
            }
            if (addedMillis > 0L) {
                return now.timeInMillis + addedMillis
            }
        }

        val date = Calendar.getInstance().apply { timeInMillis = now.timeInMillis }
        val parsedDate = parseReminderDate(text, now)
        if (parsedDate != null) {
            date.timeInMillis = parsedDate.timeInMillis
        } else if (text.contains("พรุ่งนี้")) {
            date.add(Calendar.DAY_OF_YEAR, 1)
        } else {
            date.set(Calendar.YEAR, now.get(Calendar.YEAR))
            date.set(Calendar.MONTH, now.get(Calendar.MONTH))
            date.set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH))
        }

        val time = parseReminderTime(text) ?: return null
        date.set(Calendar.HOUR_OF_DAY, time.first)
        date.set(Calendar.MINUTE, time.second)
        date.set(Calendar.SECOND, 0)
        date.set(Calendar.MILLISECOND, 0)
        return date.timeInMillis
    }

    private fun hasExplicitReminderDate(text: String): Boolean {
        return Regex("(\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?)|(\\d{1,2}\\s*(มกราคม|กุมภาพันธ์|มีนาคม|เมษายน|พฤษภาคม|มิถุนายน|กรกฎาคม|สิงหาคม|กันยายน|ตุลาคม|พฤศจิกายน|ธันวาคม)(?:\\s*\\d{2,4})?)|วันนี้|พรุ่งนี้|วัน(?:ที่)?\\s*(อาทิตย์|จันทร์|อังคาร|พุธ|พฤหัสบดี|พฤหัส|ศุกร์|เสาร์)")
            .containsMatchIn(text)
    }

    private fun hasExplicitReminderTime(text: String): Boolean {
        val hasRelative = Regex("อีก\\s*\\d+\\s*(?:ชั่วโมง|ชม\\.?|นาที)").containsMatchIn(text)
        return hasRelative || parseReminderTime(text) != null
    }

    private fun parseReminderDate(text: String, now: Calendar): Calendar? {
        val date = Calendar.getInstance().apply { timeInMillis = now.timeInMillis }

        val numericDate = Regex("(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?").find(text)
        if (numericDate != null) {
            val day = numericDate.groupValues[1].toIntOrNull() ?: return null
            val month = numericDate.groupValues[2].toIntOrNull() ?: return null
            val yearRaw = numericDate.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
            val year = when {
                yearRaw == null -> now.get(Calendar.YEAR)
                yearRaw.length == 2 -> 2000 + yearRaw.toIntOrNull()!!
                else -> yearRaw.toIntOrNull() ?: return null
            }
            date.set(Calendar.YEAR, year)
            date.set(Calendar.MONTH, month - 1)
            date.set(Calendar.DAY_OF_MONTH, day)
            return date
        }

        val thaiMonthMap = mapOf(
            "มกราคม" to 0,
            "กุมภาพันธ์" to 1,
            "มีนาคม" to 2,
            "เมษายน" to 3,
            "พฤษภาคม" to 4,
            "มิถุนายน" to 5,
            "กรกฎาคม" to 6,
            "สิงหาคม" to 7,
            "กันยายน" to 8,
            "ตุลาคม" to 9,
            "พฤศจิกายน" to 10,
            "ธันวาคม" to 11
        )

        val thaiMonthDate = Regex("(\\d{1,2})\\s*(มกราคม|กุมภาพันธ์|มีนาคม|เมษายน|พฤษภาคม|มิถุนายน|กรกฎาคม|สิงหาคม|กันยายน|ตุลาคม|พฤศจิกายน|ธันวาคม)(?:\\s*(\\d{2,4}))?").find(text)
        if (thaiMonthDate != null) {
            val day = thaiMonthDate.groupValues[1].toIntOrNull() ?: return null
            val monthName = thaiMonthDate.groupValues[2]
            val month = thaiMonthMap[monthName] ?: return null
            val yearRaw = thaiMonthDate.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
            val year = when {
                yearRaw == null -> now.get(Calendar.YEAR)
                yearRaw.length == 2 -> 2000 + yearRaw.toIntOrNull()!!
                else -> yearRaw.toIntOrNull() ?: return null
            }
            date.set(Calendar.YEAR, year)
            date.set(Calendar.MONTH, month)
            date.set(Calendar.DAY_OF_MONTH, day)
            if (yearRaw == null && date.timeInMillis < now.timeInMillis) {
                date.add(Calendar.YEAR, 1)
            }
            return date
        }

        val weekdayMap = mapOf(
            "อาทิตย์" to Calendar.SUNDAY,
            "จันทร์" to Calendar.MONDAY,
            "อังคาร" to Calendar.TUESDAY,
            "พุธ" to Calendar.WEDNESDAY,
            "พฤหัส" to Calendar.THURSDAY,
            "พฤหัสบดี" to Calendar.THURSDAY,
            "ศุกร์" to Calendar.FRIDAY,
            "เสาร์" to Calendar.SATURDAY
        )
        val weekdayMatch = Regex("วัน(?:ที่)?\\s*(อาทิตย์|จันทร์|อังคาร|พุธ|พฤหัสบดี|พฤหัส|ศุกร์|เสาร์)").find(text)
        if (weekdayMatch != null) {
            val targetDayOfWeek = weekdayMap[weekdayMatch.groupValues[1]] ?: return null
            val diff = ((targetDayOfWeek - now.get(Calendar.DAY_OF_WEEK)) + 7) % 7
            val daysToAdd = if (diff == 0) 7 else diff
            date.add(Calendar.DAY_OF_YEAR, daysToAdd)
            return date
        }

        if (text.contains("วันนี้")) {
            return date
        }

        if (text.contains("พรุ่งนี้")) {
            date.add(Calendar.DAY_OF_YEAR, 1)
            return date
        }

        return null
    }

    private fun parseReminderTime(text: String): Pair<Int, Int>? {
        val hhmmPattern = Regex("(\\d{1,2})[:.](\\d{1,2})")
        hhmmPattern.find(text)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            if (hour in 0..23 && minute in 0..59) return hour to minute
        }

        val oClockPattern = Regex("(\\d{1,2})\\s*โมง(?:\\s*(\\d{1,2})\\s*นาที?)?")
        oClockPattern.find(text)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            if (hour in 0..23 && minute in 0..59) return hour to minute
        }

        return parseThaiTime(text)
    }

    private fun handleTimerCommand(text: String): String? {
        if (!(text.contains("จับเวลา") || text.contains("ตั้งเวลา"))) return null

        val minuteMatch = Regex("(\\d{1,3})\\s*นาที").find(text)
        if (minuteMatch != null) {
            val minutes = minuteMatch.groupValues[1].toIntOrNull() ?: return null
            return "[ACTION:SET_TIMER:${minutes * 60}] ตั้งเวลา $minutes นาทีให้แล้วค่ะ"
        }

        val secondMatch = Regex("(\\d{1,4})\\s*วินาที").find(text)
        if (secondMatch != null) {
            val seconds = secondMatch.groupValues[1].toIntOrNull() ?: return null
            return "[ACTION:SET_TIMER:$seconds] ตั้งเวลา $seconds วินาทีให้แล้วค่ะ"
        }

        return null
    }

    private fun handleWebSearchCommand(text: String): String? {
        val prefixes = listOf("ค้นหา", "หา", "เสิร์ช", "search")
        if (!prefixes.any { text.startsWith(it, ignoreCase = true) }) return null
        if (text.startsWith("ค้นหาภาพ") || text.startsWith("ค้นหารูป") || text.startsWith("ค้นหายูทูป")) return null

        val query = extractTargetName(text, prefixes)
        if (query.isBlank()) return null
        return "[ACTION:WEB_SEARCH:$query] กำลังค้นหา $query ให้ค่ะ"
    }

    private fun handleImageSearchCommand(text: String): String? {
        val prefixes = listOf("ค้นหาภาพ", "หารูป", "ค้นหารูป", "เปิดรูป", "ดูรูป")
        if (!prefixes.any { text.startsWith(it) }) return null

        val query = extractTargetName(text, prefixes)
        if (query.isBlank()) {
            return "กรุณาบอกสิ่งที่ต้องการค้นหารูปด้วยค่ะ"
        }
        return "[ACTION:SEARCH_IMAGE:$query] กำลังค้นหารูป $query ให้ค่ะ"
    }

    private fun handleYouTubeSearchCommand(text: String): String? {
        val prefixes = listOf("ค้นหายูทูป", "เปิดยูทูปหา", "เปิด youtube หา", "หาคลิป", "ดูคลิป")
        if (!prefixes.any { text.startsWith(it, ignoreCase = true) }) return null

        val query = extractTargetName(text, prefixes)
        if (query.isBlank()) {
            return "กรุณาบอกสิ่งที่ต้องการค้นหาในยูทูปด้วยค่ะ"
        }
        return "[ACTION:SEARCH_YOUTUBE:$query] กำลังค้นหา $query ในยูทูปให้ค่ะ"
    }

    private fun handleVagueOptionsQuestion(text: String): String? {
        val vaguePhrases = listOf(
            "มีตัวอะไรบ้าง",
            "มีอะไรบ้าง",
            "มีแบบไหนบ้าง",
            "มีอะไรให้เลือกบ้าง",
            "เลือกอะไรได้บ้าง"
        )
        if (!vaguePhrases.any { text.contains(it) }) return null
        return "อยากให้อุ่นใจช่วยเรื่องไหนคะ เช่น อาหาร การออกกำลังกาย ยา นัดหมอ หรือการใช้งานโทรศัพท์คะ"
    }

    private fun handleContentPreferenceCommand(text: String, contentPreferences: List<String>): String? {
        val cleanText = text.trim()
        val learningTriggers = listOf("ชอบดู", "ชอบฟัง", "จำว่า", "จำไว้", "โปรด", "อยากดู", "อยากฟัง")
        val suggestedTopics = extractContentPreferenceTopics(cleanText)

        if (learningTriggers.any { cleanText.contains(it) } && suggestedTopics.isNotEmpty()) {
            return "รับไว้แล้วค่ะ อุ่นใจจะจำว่าอยากดู/ฟัง ${suggestedTopics.joinToString(" และ ")} นะคะ"
        }

        val queryTriggers = listOf(
            "หาอะไรดู",
            "อยากดูอะไร",
            "เปิดอะไรให้ดู",
            "มีอะไรให้ดู",
            "แนะนำอะไร",
            "ช่วยเลือกให้หน่อย",
            "หาอะไรฟัง",
            "อยากฟังอะไร",
            "เปิดอะไรให้ฟัง",
            "มีอะไรให้ฟัง"
        )
        if (!queryTriggers.any { cleanText.contains(it) }) return null
        if (contentPreferences.isEmpty()) {
            return "อยากให้อุ่นใจช่วยเลือกเรื่องไหนคะ เช่น บทสวดมนต์ นิทาน สารคดี หรือข่าวคะ"
        }

        val topTopics = contentPreferences.take(3)
        val firstTopic = topTopics.first()
        return "[ACTION:SEARCH_YOUTUBE:$firstTopic] อุ่นใจจำได้ว่าคุณชอบ ${topTopics.joinToString(" / ")} ค่ะ กำลังหาให้ดูหรือฟังนะคะ"
    }

    private fun extractContentPreferenceTopics(text: String): List<String> {
        val normalized = normalizeContactName(text)
        val topicMap = linkedMapOf(
            "บทสวดมนต์" to listOf("บทสวดมนต์", "สวดมนต์", "สวด", "มนต์"),
            "นิทานเรื่องเล่า" to listOf("นิทาน", "เรื่องเล่า", "เล่านิทาน", "นิทานเรื่อง"),
            "สารคดี" to listOf("สารคดี", "สาระคดี"),
            "ธรรมะ" to listOf("ธรรมะ", "ธรรมมะ"),
            "ข่าว" to listOf("ข่าว", "ข่าวสาร"),
            "เพลงเก่า" to listOf("เพลงเก่า", "เพลงลูกทุ่ง", "เพลงสมัยก่อน", "เพลงย้อนยุค"),
            "รายการธรรมชาติ" to listOf("ธรรมชาติ", "สัตว์", "ป่า", "ทะเล")
        )

        return topicMap.entries.mapNotNull { (topic, keywords) ->
            if (keywords.any { normalized.contains(it) }) topic else null
        }
    }

    private fun handleMapCommand(text: String): String? {
        val navPrefixes = listOf("นำทางไป", "ขอเส้นทางไป", "ขอเส้นทาง", "เส้นทางไป", "ทางไป", "ไป", "พาไป")
        if (navPrefixes.any { text.startsWith(it) } && !text.contains("โทร")) {
            val query = extractTargetName(text, navPrefixes)
            if (query.isBlank() || query.contains("gps", ignoreCase = true) || query.contains("แผนที่") || query.contains("แอพ") || query.contains("แอป")) {
                return "[ACTION:OPEN_MAPS] เปิด Google Maps ให้ค่ะ"
            }
            if (query.isNotBlank()) {
                return "[ACTION:NAVIGATE:$query] กำลังเปิด Google Maps ไปยัง $query ให้ค่ะ"
            }
            return "[ACTION:OPEN_MAPS] เปิด Google Maps ให้ค่ะ"
        }

        val nearbyHints = listOf("ใกล้ฉัน", "ใกล้ๆ", "ใกล้ ๆ", "แถวนี้", "แถวๆนี้", "แถว ๆ นี้", "แถวนี้มี", "แถวนี้มีอะไร")
        val nearbyPrefixes = listOf("หา", "ค้นหา", "ดู", "เปิด", "พาไป")
        if (nearbyHints.any { text.contains(it) }) {
            val normalizedText = normalizeContactName(text)
            val nearbyQuery = when {
                normalizedText.contains("ร้านอาหาร") -> "restaurant"
                normalizedText.contains("ปั๊มน้ำมัน") || normalizedText.contains("ปั้มน้ำมัน") || normalizedText.contains("ปัมน้ำมัน") || normalizedText.contains("ปั๊ม") || normalizedText.contains("ปั้ม") -> "gas station"
                normalizedText.contains("ร้านกาแฟ") -> "cafe"
                normalizedText.contains("ร้านยา") || normalizedText.contains("ร้านขายยา") || normalizedText.contains("เภสัช") -> "pharmacy"
                normalizedText.contains("โรงพยาบาล") || normalizedText.contains("รพ") -> "hospital"
                normalizedText.contains("ตู้เอทีเอ็ม") || normalizedText.contains("atm") -> "atm"
                normalizedText.contains("ห้องน้ำ") || normalizedText.contains("ห้องน้ำสาธารณะ") -> "public toilet"
                else -> extractTargetName(text, nearbyPrefixes)
                    .replace("ใกล้ฉัน", "")
                    .replace("ใกล้ๆ", "")
                    .replace("ใกล้ ๆ", "")
                    .replace("แถวนี้", "")
                    .replace("แถวๆนี้", "")
                    .replace("แถว ๆ นี้", "")
                    .replace("แถวนี้มี", "")
                    .replace("แถวนี้มีอะไร", "")
                    .trim()
            }

            return if (nearbyQuery.isBlank()) {
                "[ACTION:OPEN_MAPS] เปิด Google Maps ให้ค่ะ"
            } else {
                "[ACTION:MAP_NEARBY:$nearbyQuery] กำลังค้นหา $nearbyQuery ใกล้ฉันใน Google Maps ให้ค่ะ"
            }
        }

        val mapPrefixes = listOf("เปิดแผนที่", "ดูแผนที่", "ค้นหาแผนที่", "หาแผนที่", "ค้นหาทางไป", "เปิด gps", "เปิดจีพีเอส")
        if (mapPrefixes.any { text.startsWith(it) }) {
            val query = extractTargetName(text, mapPrefixes)
            return if (query.isBlank() || query.contains("gps", ignoreCase = true) || query.contains("แผนที่") || query.contains("แอพ") || query.contains("แอป")) {
                "[ACTION:OPEN_MAPS] เปิด Google Maps ให้ค่ะ"
            } else {
                "[ACTION:MAP_SEARCH:$query] กำลังค้นหา $query ใน Google Maps ให้ค่ะ"
            }
        }

        return null
    }

    private fun handleSettingsCommand(text: String): String? {
        val cleanText = text.trim()
        val normalizedText = cleanText.lowercase(Locale.ROOT)
        return when {
            cleanText.contains("เปิดไวไฟ") || cleanText.contains("ตั้งค่าไวไฟ") || normalizedText.contains("เปิด wifi") || normalizedText.contains("ตั้งค่า wifi") || cleanText.contains("หน้าไวไฟ") ->
                "[ACTION:OPEN_WIFI_SETTINGS] กำลังเปิดหน้าตั้งค่า Wi-Fi ให้ค่ะ"
            cleanText.contains("เปิดบลูทูธ") || normalizedText.contains("เปิด bluetooth") ->
                "[ACTION:BLUETOOTH_ON] กำลังขอเปิด Bluetooth ให้ค่ะ"
            cleanText.contains("ปิดบลูทูธ") || normalizedText.contains("ปิด bluetooth") ->
                "[ACTION:BLUETOOTH_OFF] กำลังปิด Bluetooth ให้ค่ะ"
            cleanText.contains("ตั้งค่าบลูทูธ") || normalizedText.contains("ตั้งค่า bluetooth") || cleanText.contains("หน้าบลูทูธ") ->
                "[ACTION:OPEN_BLUETOOTH_SETTINGS] กำลังเปิดหน้าตั้งค่า Bluetooth ให้ค่ะ"
            cleanText.contains("ตั้งค่าเสียง") || cleanText.contains("เปิดตั้งค่าเสียง") || cleanText.contains("หน้าตั้งค่าเสียง") ->
                "[ACTION:OPEN_SOUND_SETTINGS] กำลังเปิดหน้าตั้งค่าเสียงให้ค่ะ"
            cleanText.contains("ตั้งค่าหน้าจอ") || cleanText.contains("เปิดหน้าจอ") || cleanText.contains("ตั้งค่าจอ") || cleanText.contains("ปรับหน้าจอ") ->
                "[ACTION:OPEN_DISPLAY_SETTINGS] กำลังเปิดหน้าตั้งค่าหน้าจอให้ค่ะ"
            cleanText == "ตั้งค่า" || cleanText.contains("เปิดการตั้งค่า") || cleanText.contains("เปิดตั้งค่า") || cleanText.contains("เข้าตั้งค่า") || cleanText.contains("ไปหน้าตั้งค่า") ->
                "[ACTION:OPEN_SETTINGS] กำลังเปิดการตั้งค่าให้ค่ะ"
            else -> null
        }
    }

    private fun handleScamNumberCommand(text: String, scamNumbers: List<ScamNumberEntry>): String? {
        val cleanText = text.trim()
        val triggerWords = listOf("เช็กเบอร์", "เช็คเบอร์", "ตรวจเบอร์", "เบอร์มิจฉาชีพ", "มิจฉาชีพ", "สแปมเบอร์", "เบอร์อันตราย")
        if (!triggerWords.any { cleanText.contains(it) }) return null

        val activeScams = scamNumbers.filter { isScamEntryActive(it) }
        if (activeScams.isEmpty()) {
            return "ยังไม่มีฐานเบอร์มิจฉาชีพในเครื่องค่ะ ถ้าต้องการเพิ่มเบอร์เสี่ยง บอกอุ่นใจได้เลยนะคะ"
        }

        val digitsQuery = cleanText.filter { it.isDigit() }
        val labelQuery = normalizeContactName(cleanText)

        if (digitsQuery.isNotBlank() && digitsQuery.length < 7) {
            return "กรุณาบอกเลขหมายโทรศัพท์อย่างน้อย 7 หลักด้วยค่ะ"
        }

        val matches = activeScams.filter { entry ->
            val entryDigits = normalizePhoneDigits(entry.number)
            val entryLabel = normalizeContactName(entry.label)
            val entryNote = normalizeContactName(entry.note)
            when {
                digitsQuery.isNotBlank() -> entryDigits.contains(digitsQuery) || digitsQuery.contains(entryDigits)
                else -> entryLabel.contains(labelQuery) || entryNote.contains(labelQuery)
            }
        }

        if (matches.isEmpty()) {
            return if (digitsQuery.isNotBlank()) {
                "ยังไม่พบเบอร์นี้ในฐานเบอร์มิจฉาชีพค่ะ ถ้าต้องการให้บันทึกเพิ่ม บอกเบอร์และแหล่งที่มาได้เลยนะคะ"
            } else {
                "ยังไม่พบข้อมูลเบอร์นี้ในฐานเบอร์มิจฉาชีพค่ะ กรุณาบอกเลขหมายโทรศัพท์ให้ชัดเจนหน่อยนะคะ"
            }
        }

        val topMatches = matches.take(3)
        return if (topMatches.size == 1) {
            formatScamNumberResponse(topMatches.first())
        } else {
            "พบหลายรายการที่ตรงกันค่ะ\n" + topMatches.joinToString("\n") { formatScamNumberResponse(it) }
        }
    }

    private fun handleScamSourceCommand(text: String, scamSources: List<ScamSourceReference>): String? {
        val cleanText = text.trim()
        val triggerWords = listOf("แหล่งอ้างอิง", "อ้างอิง", "ที่มา", "ข้อมูลทางการ", "แหล่งทางการ")
        if (!triggerWords.any { cleanText.contains(it) }) return null

        val activeSources = scamSources.filter { isScamSourceActive(it) }
        if (activeSources.isEmpty()) {
            return "ยังไม่มีแหล่งอ้างอิงทางการในเครื่องค่ะ"
        }

        val topSources = activeSources.take(5)
        return "แหล่งอ้างอิงทางการที่มีอยู่ค่ะ\n" + topSources.joinToString("\n") { formatScamSourceResponse(it) }
    }

    private fun handleSendSmsCommand(text: String, contacts: List<DeviceContact>): String? {
        val match = Regex("^(?:ส่งข้อความหา|ส่ง sms หา|ส่งข้อความถึง)\\s+(.+?)\\s+ว่า\\s+(.+)$").find(text)
            ?: return null
        val targetName = match.groupValues[1].trim()
        val message = match.groupValues[2].trim()
        val matchedContact = findBestMatchingContact(targetName, contacts)
            ?: return "ไม่พบรายชื่อ $targetName ในโทรศัพท์ค่ะ"
        return "[ACTION:SEND_SMS:${matchedContact.number}:${message}] กำลังส่งข้อความหา ${matchedContact.name} ให้ค่ะ"
    }

    private fun handleReadMessageCommand(text: String, readLatestMessage: () -> String): String? {
        return if (
            text.contains("อ่านข้อความ") ||
            text.contains("อ่าน sms") ||
            text.contains("อ่านเอสเอ็มเอส") ||
            text.contains("มีข้อความอะไรบ้าง") ||
            text.contains("ข้อความล่าสุด")
        ) {
            readLatestMessage()
        } else {
            null
        }
    }

    private fun handleFlashlightCommand(text: String): String? {
        if (!text.contains("ไฟฉาย")) return null
        return when {
            text.contains("เปิด") || text.contains("ช่วย") ->
                "[ACTION:FLASHLIGHT_ON] เปิดไฟฉายให้แล้วค่ะ"
            text.contains("ปิด") ->
                "[ACTION:FLASHLIGHT_OFF] ปิดไฟฉายให้แล้วค่ะ"
            else -> null
        }
    }

    private fun handleCameraCommand(text: String): String? {
        return if (text.contains("กล้อง") || text.contains("ถ่ายรูป") || text.contains("ถ่ายภาพ")) {
            "[ACTION:TAKE_PHOTO] กำลังถ่ายภาพให้ค่ะ"
        } else {
            null
        }
    }

    private fun handleCallCommand(text: String, contacts: List<DeviceContact>, scamNumbers: List<ScamNumberEntry>): String? {
        if (!text.contains("โทร")) return null
        val directNumber = ContactFallbackResolver.extractPhoneNumber(text)
        if (directNumber != null) {
            return "[ACTION:CALL:$directNumber] กำลังต่อสายโทรออกให้ค่ะ"
        }

        val targetName = ContactFallbackResolver.stripCommandPrefixes(
            text,
            listOf("โทรหา", "โทร", "เบอร์", "ให้หน่อย", "หน่อย", "ช่วยโทรหา", "ช่วยโทร", "ติดต่อ")
        )
        if (targetName.isBlank()) return null
        val topMatches = ContactFallbackResolver.findTopMatchingContacts(targetName, contacts, limit = 3)
        if (topMatches.isEmpty()) return "ไม่พบชื่อ $targetName ในโทรศัพท์ค่ะ"
        if (ContactFallbackResolver.isLikelyAmbiguous(topMatches)) {
            val choices = topMatches.joinToString(" / ") { "${it.contact.name} ${it.contact.number}" }
            return "พบหลายรายชื่อที่คล้ายกัน $choices ค่ะ กรุณาระบุชื่อให้ชัดขึ้นอีกนิด"
        }
        val matchedContact = topMatches.first().contact
        findScamNumberMatch(matchedContact.number, scamNumbers)?.let { scam ->
            return "เบอร์ของ ${matchedContact.name} ถูกบันทึกว่าเป็นเบอร์เสี่ยงค่ะ แหล่งที่มา: ${displayScamSource(scam.source)}"
        }
        return "[ACTION:CONFIRM_CALL:${matchedContact.number}:${matchedContact.name}] ต้องการโทรหา ${matchedContact.name} ใช่ไหมคะ ถ้าใช่พูดว่า ยืนยัน ค่ะ"
    }

    private fun handleOpenAppCommand(text: String, apps: List<Pair<String, String>>): String? {
        val cleanText = text.trim()
        
        // 1. Check for generalized music keywords first
        if (cleanText.contains("ฟังเพลง") || cleanText.contains("เล่นเพลง") || cleanText.contains("เปิดเพลง")) {
            val spotifyPackage = "com.spotify.music"
            val youtubePackage = "com.google.android.youtube"
            
            val hasSpotify = apps.any { it.second == spotifyPackage }
            val hasYoutube = apps.any { it.second == youtubePackage }
            
            return when {
                hasSpotify -> "[ACTION:OPEN_APP:$spotifyPackage] กำลังเปิด Spotify เพื่อเล่นเพลงให้ค่ะ"
                hasYoutube -> "[ACTION:OPEN_APP:$youtubePackage] กำลังเปิด YouTube เพื่อเล่นเพลงให้ค่ะ"
                else -> {
                    val musicApp = apps.firstOrNull { it.first.contains("เพลง") || it.first.lowercase().contains("music") }
                    if (musicApp != null) {
                        "[ACTION:OPEN_APP:${musicApp.second}] กำลังเปิดแอป ${musicApp.first} ให้ค่ะ"
                    } else {
                        "ไม่พบแอปสำหรับเล่นเพลงในเครื่องค่ะ"
                    }
                }
            }
        }

        if (!(cleanText.contains("เปิด") || cleanText.contains("เล่น") || cleanText.contains("แอป") || cleanText.contains("แอพ"))) {
            return null
        }

        val targetAppName = cleanText.replace("เปิดแอป", "")
            .replace("เปิดแอพ", "")
            .replace("เปิด", "")
            .replace("เล่น", "")
            .replace("แอป", "")
            .replace("แอพ", "")
            .replace("ให้หน่อย", "")
            .replace("หน่อย", "")
            .trim()

        if (targetAppName.isBlank()) return null

        findAmbiguousAppIntent(targetAppName, apps)?.let { return it }

        if (normalizeAppName(targetAppName).contains("อุ่นใจ") ||
            normalizeAppName(targetAppName).contains("nongkanvela") ||
            normalizeAppName(targetAppName).contains("nong kan vela")
            || normalizeAppName(targetAppName).contains("gps")
            || normalizeAppName(targetAppName).contains("maps")
            || normalizeAppName(targetAppName).contains("googlemap")
            || normalizeAppName(targetAppName).contains("googlemaps")
        ) {
            return "[ACTION:OPEN_MAPS] กำลังเปิด GPS ให้ค่ะ"
        }

        val matchedApp = findBestMatchingApp(targetAppName, apps)
            ?: return "ไม่พบแอป $targetAppName ในเครื่องค่ะ"

        return "[ACTION:OPEN_APP:${matchedApp.second}] กำลังเปิดแอป ${matchedApp.first} ให้ค่ะ"
    }

    private fun handleContactCommand(
        text: String,
        contacts: List<DeviceContact>,
        scamNumbers: List<ScamNumberEntry> = emptyList(),
        sheetCommands: List<SheetCommand> = emptyList(),
        searchContacts: (String) -> List<DeviceContact>,
        updateContact: (DeviceContact, String, String) -> String,
        deleteContact: (DeviceContact) -> String,
        createContact: (String, String) -> String
    ): String? {
        val addPattern = Regex("^(?:เพิ่มรายชื่อ|บันทึกรายชื่อ|เพิ่มผู้ติดต่อ)\\s+(.+?)\\s+([0-9+\\- ]{6,})$")
        addPattern.find(text)?.let { match ->
            val name = match.groupValues[1].trim()
            val number = match.groupValues[2].trim()
            return createContact(name, number)
        }

        if (contacts.isEmpty() && sheetCommands.isEmpty()) {
            val hasContactWord = text.contains("รายชื่อ") || text.contains("เบอร์") || text.contains("ผู้ติดต่อ")
            if (hasContactWord) return "ยังไม่พบรายชื่อในโทรศัพท์ หรือยังไม่ได้รับสิทธิ์รายชื่อค่ะ"
            return null
        }

        val deletePrefixes = listOf("ลบรายชื่อ", "ลบชื่อ", "ลบเบอร์", "ลบผู้ติดต่อ")
        if (deletePrefixes.any { text.startsWith(it) }) {
            val targetName = extractTargetName(text, deletePrefixes)
            if (targetName.isBlank()) return "กรุณาบอกชื่อรายชื่อที่ต้องการลบด้วยค่ะ"
            val matchedContact = findBestMatchingContact(targetName, contacts)
                ?: return "ไม่พบรายชื่อ $targetName ในโทรศัพท์ค่ะ"
            return deleteContact(matchedContact)
        }

        val phoneEditPattern = Regex("^(?:แก้เบอร์|เปลี่ยนเบอร์|แก้ไขเบอร์)\\s+(.+?)\\s+เป็น\\s+([0-9+\\- ]+)$")
        phoneEditPattern.find(text)?.let { match ->
            val targetName = match.groupValues[1].trim()
            val newNumber = match.groupValues[2].trim()
            val matchedContact = findBestMatchingContact(targetName, contacts)
                ?: return "ไม่พบรายชื่อ $targetName ในโทรศัพท์ค่ะ"
            return updateContact(matchedContact, matchedContact.name, newNumber)
        }

        val nameEditPattern = Regex("^(?:แก้ชื่อ|เปลี่ยนชื่อ|แก้ไขชื่อ)\\s+(.+?)\\s+เป็น\\s+(.+)$")
        nameEditPattern.find(text)?.let { match ->
            val targetName = match.groupValues[1].trim()
            val newName = match.groupValues[2].trim()
            val matchedContact = findBestMatchingContact(targetName, contacts)
                ?: return "ไม่พบรายชื่อ $targetName ในโทรศัพท์ค่ะ"
            return updateContact(matchedContact, newName, matchedContact.number)
        }

        val searchPrefixes = listOf(
            "ขอเบอร์โทรศัพท์ของ",
            "ขอเบอร์โทรศัพท์",
            "เบอร์โทรศัพท์ของ",
            "เบอร์โทรศัพท์",
            "ค้นหาเบอร์โทร",
            "ค้นหารายชื่อ",
            "ค้นหาเบอร์",
            "ขอเบอร์โทร",
            "หาเบอร์โทร",
            "หารายชื่อ",
            "ขอเบอร์",
            "หาเบอร์",
            "เบอร์โทรของ",
            "เบอร์โทร",
            "เบอร์ของ",
            "เบอร์"
        )
        if (searchPrefixes.any { text.startsWith(it) }) {
            val targetName = extractTargetName(text, searchPrefixes)
            if (targetName.isBlank()) return "กรุณาบอกชื่อที่ต้องการค้นหาด้วยค่ะ"
            
            // 1. Search in device contacts
            val matchedContacts = resolveContactLookupResults(targetName, contacts)
            if (matchedContacts.isNotEmpty()) {
                if (matchedContacts.size == 1) {
                    findScamNumberMatch(matchedContacts.first().number, scamNumbers)?.let { scam ->
                        return "พบ ${matchedContacts.first().name} ในรายชื่อค่ะ แต่เบอร์นี้ถูกบันทึกว่าเป็นเบอร์เสี่ยงจาก ${displayScamSource(scam.source)} นะคะ"
                    }
                }
                return formatContactSearchResponse(matchedContacts)
            }

            val results = searchContacts(targetName)
            if (results.isNotEmpty()) {
                results.firstOrNull { findScamNumberMatch(it.number, scamNumbers) != null }?.let { scamContact ->
                    val scam = findScamNumberMatch(scamContact.number, scamNumbers)
                    if (scam != null) {
                        return "พบ ${scamContact.name} ในรายชื่อค่ะ แต่เบอร์นี้ถูกบันทึกว่าเป็นเบอร์เสี่ยงจาก ${displayScamSource(scam.source)} นะคะ"
                    }
                }
                return formatContactSearchResponse(results)
            }

            // 2. Fallback to Google Sheets contacts list
            val sheetContacts = sheetCommands
                .filter { 
                    val status = it.status.trim()
                    val isActive = status == "ใช้งาน" || status == "เปิดใช้งาน" || status == "active" || status == "Active" || status == "1"
                    isActive && it.number.isNotBlank() 
                }
                .mapIndexed { index, sc ->
                    val contactName = if (sc.name.isNotBlank()) sc.name.trim() else sc.command.trim()
                    DeviceContact(
                        contactId = -100L - index,
                        rawContactId = -100L - index,
                        name = contactName,
                        number = sc.number.trim()
                    )
                }
            val matchedSheetContacts = resolveContactLookupResults(targetName, sheetContacts)
            if (matchedSheetContacts.isNotEmpty()) {
                if (matchedSheetContacts.size == 1) {
                    findScamNumberMatch(matchedSheetContacts.first().number, scamNumbers)?.let { scam ->
                        return "พบ ${matchedSheetContacts.first().name} ในชีทค่ะ แต่เบอร์นี้ถูกบันทึกว่าเป็นเบอร์เสี่ยงจาก ${displayScamSource(scam.source)} นะคะ"
                    }
                    return "พบรายชื่อ ${matchedSheetContacts.first().name} เบอร์ ${cleanPhoneNumber(matchedSheetContacts.first().number)} ในชีทค่ะ"
                }
                return "พบหลายรายชื่อที่ตรงกันในชีท ${matchedSheetContacts.joinToString(" / ") { "${it.name} ${cleanPhoneNumber(it.number)}" }} ค่ะ"
            }

            return "ไม่พบรายชื่อนี้ในโทรศัพท์และในชีทค่ะ"
        }

        Regex("^(.+?)\\s*เบอร์อะไร$").find(text)?.let { match ->
            val targetName = match.groupValues[1].trim()
            val matchedContacts = resolveContactLookupResults(targetName, contacts)
            if (matchedContacts.isNotEmpty()) {
                if (matchedContacts.size == 1) {
                    findScamNumberMatch(matchedContacts.first().number, scamNumbers)?.let { scam ->
                        return "พบ ${matchedContacts.first().name} ในรายชื่อค่ะ แต่เบอร์นี้ถูกบันทึกว่าเป็นเบอร์เสี่ยงจาก ${displayScamSource(scam.source)} นะคะ"
                    }
                }
                return formatContactSearchResponse(matchedContacts)
            }
            
            val sheetContacts = sheetCommands
                .filter { 
                    val status = it.status.trim()
                    val isActive = status == "ใช้งาน" || status == "เปิดใช้งาน" || status == "active" || status == "Active" || status == "1"
                    isActive && it.number.isNotBlank() 
                }
                .mapIndexed { index, sc ->
                    val contactName = if (sc.name.isNotBlank()) sc.name.trim() else sc.command.trim()
                    DeviceContact(
                        contactId = -100L - index,
                        rawContactId = -100L - index,
                        name = contactName,
                        number = sc.number.trim()
                    )
                }
            val matchedSheetContacts = resolveContactLookupResults(targetName, sheetContacts)
            if (matchedSheetContacts.isNotEmpty()) {
                if (matchedSheetContacts.size == 1) {
                    findScamNumberMatch(matchedSheetContacts.first().number, scamNumbers)?.let { scam ->
                        return "พบ ${matchedSheetContacts.first().name} ในชีทค่ะ แต่เบอร์นี้ถูกบันทึกว่าเป็นเบอร์เสี่ยงจาก ${displayScamSource(scam.source)} นะคะ"
                    }
                    return "พบรายชื่อ ${matchedSheetContacts.first().name} เบอร์ ${cleanPhoneNumber(matchedSheetContacts.first().number)} ในชีทค่ะ"
                }
                return "พบหลายรายชื่อที่ตรงกันในชีท ${matchedSheetContacts.joinToString(" / ") { "${it.name} ${cleanPhoneNumber(it.number)}" }} ค่ะ"
            }
            return "ไม่พบรายชื่อนี้ในโทรศัพท์และในชีทค่ะ"
        }

        // Fallback: If the user just says a contact name (exactly or high quality match) without prefixes
        val normalizedQuery = normalizeContactName(text)
        if (normalizedQuery.isNotBlank() && !text.contains("โทร")) {
            val matchedContacts = resolveContactLookupResults(normalizedQuery, contacts)
            if (matchedContacts.isNotEmpty()) {
                if (matchedContacts.size == 1) {
                    findScamNumberMatch(matchedContacts.first().number, scamNumbers)?.let { scam ->
                        return "พบ ${matchedContacts.first().name} ในรายชื่อค่ะ แต่เบอร์นี้ถูกบันทึกว่าเป็นเบอร์เสี่ยงจาก ${displayScamSource(scam.source)} นะคะ"
                    }
                }
                return formatContactSearchResponse(matchedContacts)
            }

            // Sheet fallback check for direct name lookup
            val sheetContacts = sheetCommands
                .filter { 
                    val status = it.status.trim()
                    val isActive = status == "ใช้งาน" || status == "เปิดใช้งาน" || status == "active" || status == "Active" || status == "1"
                    isActive && it.number.isNotBlank() 
                }
                .mapIndexed { index, sc ->
                    val contactName = if (sc.name.isNotBlank()) sc.name.trim() else sc.command.trim()
                    DeviceContact(
                        contactId = -100L - index,
                        rawContactId = -100L - index,
                        name = contactName,
                        number = sc.number.trim()
                    )
                }
            val matchedSheetContacts = resolveContactLookupResults(normalizedQuery, sheetContacts)
            if (matchedSheetContacts.isNotEmpty()) {
                if (matchedSheetContacts.size == 1) {
                    return "พบรายชื่อ ${matchedSheetContacts.first().name} เบอร์ ${cleanPhoneNumber(matchedSheetContacts.first().number)} ในชีทค่ะ"
                } else {
                    return "พบหลายรายชื่อที่ตรงกันในชีท ${matchedSheetContacts.joinToString(" / ") { "${it.name} ${cleanPhoneNumber(it.number)}" }} ค่ะ"
                }
            }
        }

        return null
    }

    private fun normalizePhoneDigits(value: String): String {
        return value.filter { it.isDigit() }
    }

    private fun isScamEntryActive(entry: ScamNumberEntry): Boolean {
        val status = entry.status.trim().lowercase(Locale.getDefault())
        return status.isEmpty() ||
            status == "active" ||
            status == "ใช้งาน" ||
            status == "เปิดใช้งาน" ||
            status == "1"
    }

    private fun findScamNumberMatch(number: String, scamNumbers: List<ScamNumberEntry>): ScamNumberEntry? {
        val digits = normalizePhoneDigits(number)
        if (digits.isBlank()) return null
        return scamNumbers.firstOrNull { entry ->
            val entryDigits = normalizePhoneDigits(entry.number)
            entryDigits.isNotBlank() && (entryDigits.contains(digits) || digits.contains(entryDigits))
        }
    }

    private fun displayScamSource(source: String): String {
        val cleanSource = source.trim()
        return when (cleanSource.lowercase(Locale.getDefault())) {
            "official", "ทางการ" -> "ฐานข้อมูลทางการ"
            "user_reported", "reported", "user" -> "รายงานจากผู้ใช้"
            "manual", "admin" -> "ผู้ดูแลระบบ"
            else -> if (cleanSource.isBlank()) "ไม่ระบุแหล่งที่มา" else cleanSource
        }
    }

    private fun isScamSourceActive(entry: ScamSourceReference): Boolean {
        val status = entry.status.trim().lowercase(Locale.getDefault())
        return status.isEmpty() ||
            status == "active" ||
            status == "ใช้งาน" ||
            status == "เปิดใช้งาน" ||
            status == "1"
    }

    private fun formatScamSourceResponse(entry: ScamSourceReference): String {
        val noteText = entry.note.trim()
        return if (noteText.isNotBlank()) {
            "• ${entry.title} - ${entry.url} (หมายเหตุ: $noteText)"
        } else {
            "• ${entry.title} - ${entry.url}"
        }
    }

    private fun formatScamNumberResponse(entry: ScamNumberEntry): String {
        val sourceText = displayScamSource(entry.source)
        val numberText = cleanPhoneNumber(entry.number)
        val labelText = entry.label.trim()
        val noteText = entry.note.trim()
        return if (noteText.isNotBlank()) {
            "• $numberText - $labelText (แหล่งที่มา: $sourceText, หมายเหตุ: $noteText)"
        } else {
            "• $numberText - $labelText (แหล่งที่มา: $sourceText)"
        }
    }

    private fun cleanPhoneNumber(value: String): String {
        var temp = value.replace(Regex("[\\s\\u00A0\\u2007\\u202F]+"), " ").trim()
        val prefixes = listOf("เบอร์โทรศัพท์", "เบอร์โทร", "เบอร์มือถือ", "เบอร์บ้าน", "เบอร์", "โทร")
        var matched = true
        while (matched) {
            matched = false
            for (prefix in prefixes) {
                if (temp.startsWith(prefix)) {
                    temp = temp.substring(prefix.length).trim()
                    matched = true
                    break
                }
            }
        }
        return temp
    }

    private fun formatContactSearchResponse(results: List<DeviceContact>): String {
        if (results.isEmpty()) return "ไม่พบรายชื่อนี้ในโทรศัพท์ค่ะ"
        val topResults = results.take(3)
        return if (topResults.size == 1) {
            "พบรายชื่อ ${topResults.first().name} เบอร์ ${cleanPhoneNumber(topResults.first().number)} ค่ะ"
        } else {
            "พบหลายรายชื่อที่ตรงกัน ${topResults.joinToString(" / ") { "${it.name} ${cleanPhoneNumber(it.number)}" }} ค่ะ"
        }
    }

    private fun extractTargetName(text: String, prefixes: List<String>): String {
        var result = text
        prefixes.forEach { prefix -> 
            if (result.lowercase(Locale.ROOT).startsWith(prefix.lowercase(Locale.ROOT))) {
                result = result.drop(prefix.length)
            }
        }
        return result
            .replace("ให้หน่อย", "")
            .replace("หน่อย", "")
            .replace("ให้ที", "")
            .replace("ที", "")
            .trim()
    }

    private fun resolveContactLookupResults(
        query: String,
        contacts: List<DeviceContact>,
        limit: Int = 5
    ): List<DeviceContact> {
        val matches = ContactFallbackResolver.findTopMatchingContacts(query, contacts, limit = limit)
        if (matches.isEmpty()) return emptyList()
        if (matches.size == 1) return listOf(matches.first().contact)
        return if (ContactFallbackResolver.isLikelyAmbiguous(matches)) {
            matches.map { it.contact }
        } else {
            listOf(matches.first().contact)
        }
    }

    private fun replaceThaiWrittenNumbers(text: String): String {
        var temp = text
        temp = temp.replace("ครึ่งชั่วโมง", "30 นาที")
            .replace("ครึ่งชม", "30 นาที")
            .replace("ครึ่ง ชม", "30 นาที")

        val mappings = listOf(
            "ห้าสิบเก้า" to "59", "ห้าสิบแปด" to "58", "ห้าสิบเจ็ด" to "57", "ห้าสิบหก" to "56", "ห้าสิบห้า" to "55",
            "ห้าสิบสี่" to "54", "ห้าสิบสาม" to "53", "ห้าสิบสอง" to "52", "ห้าสิบเอ็ด" to "51", "ห้าสิบ" to "50",
            "สี่สิบเก้า" to "49", "สี่สิบแปด" to "48", "สี่สิบเจ็ด" to "47", "สี่สิบหก" to "46", "สี่สิบห้า" to "45",
            "สี่สิบสี่" to "44", "สี่สิบสาม" to "43", "สี่สิบสอง" to "42", "สี่สิบเอ็ด" to "41", "สี่สิบ" to "40",
            "สามสิบเก้า" to "39", "สามสิบแปด" to "38", "สามสิบเจ็ด" to "37", "สามสิบหก" to "36", "สามสิบห้า" to "35",
            "สามสิบสี่" to "34", "สามสิบสาม" to "33", "สามสิบสอง" to "32", "สามสิบเอ็ด" to "31", "สามสิบ" to "30",
            "ยี่สิบเก้า" to "29", "ยี่สิบแปด" to "28", "ยี่สิบเจ็ด" to "27", "ยี่สิบหก" to "26", "ยี่สิบห้า" to "25",
            "ยี่สิบสี่" to "24", "ยี่สิบสาม" to "23", "ยี่สิบสอง" to "22", "ยี่สิบเอ็ด" to "21", "ยี่สิบ" to "20",
            "สิบเก้า" to "19", "สิบแปด" to "18", "สิบเจ็ด" to "17", "สิบหก" to "16", "สิบห้า" to "15",
            "สิบสี่" to "14", "สิบสาม" to "13", "สิบสอง" to "12", "สิบเอ็ด" to "11", "สิบ" to "10",
            "เก้า" to "9", "แปด" to "8", "เจ็ด" to "7", "หก" to "6", "ห้า" to "5",
            "สี่" to "4", "สาม" to "3", "สอง" to "2", "หนึ่ง" to "1"
        )
        for ((thai, digit) in mappings) {
            temp = temp.replace(thai, digit)
        }
        return temp
    }

    private fun preProcessAndCorrectIntent(rawText: String): String {
        var normalized = rawText
            .lowercase(Locale.ROOT)
            .trim()
            .replace(Regex("\\s+"), " ")

        normalized = removeIrrelevantWords(normalized)

        val phraseCorrections = listOf(
            "กูเกิลแมว" to "google map",
            "กูเกิ้ล" to "google",
            "กูเกิล" to "google",
            "กูเกิ้ลแมว" to "google map",
            "กูเกิลแมป" to "google map",
            "กูเกิ้ลแมป" to "google map",
            "กูเกิลแมพ" to "google map",
            "กูเกิ้ลแมพ" to "google map",
            "กูเกิลแมปส์" to "google maps",
            "กูเกิ้ลแมปส์" to "google maps",
            "กูเกิลแมพส์" to "google maps",
            "กูเกิ้ลแมพส์" to "google maps",
            "googleแมพ" to "google map",
            "กูเกิลแม็พ" to "google map",
            "จีพีเอช" to "gps",
            "จีพีเอส" to "gps",
            "จีพีเอชเอส" to "gps",
            "บูทูด" to "บลูทูธ",
            "บูทูธ" to "บลูทูธ",
            "บลูทูด" to "บลูทูธ",
            "ตั้งปุก" to "ตั้งปลุก",
            "ปุก" to "ปลุก",
            "กินตา" to "กินยา",
            "กินหย่า" to "กินยา",
            "เตือนกินตา" to "เตือนกินยา",
            "เตือนกินหญ้า" to "เตือนกินยา",
            "ยูทรูป" to "youtube",
            "ยูทูป" to "youtube",
            "ยูทูบ" to "youtube",
            "เฟสบุก" to "facebook",
            "เฟสบุค" to "facebook",
            "เฟสบุ๊ค" to "facebook",
            "เฟซบุ๊ก" to "facebook",
            "แชตจีพีที" to "chatgpt",
            "แชท gpt" to "chatgpt",
            "แชต gpt" to "chatgpt",
            "ลาย" to "line",
            "กูเกิลแมปนำทาง" to "นำทาง google map",
            "เซเว้น" to "เซเว่น",
            "เซเวน" to "เซเว่น",
            "เปิดแผนที่" to "เปิด google map",
            "ไปโรงเรียนจิระ" to "ไปโรงเรียนจิระศาสตร์",
            "จิรศาสตร์" to "จิระศาสตร์",
            "ชีระศาสตร์" to "จิระศาสตร์",
            "จีระศาสตร์" to "จิระศาสตร์",
            "เวนอำนวยการ" to "เวรอำนวยการ",
            "เวนอำนวยกาน" to "เวรอำนวยการ",
            "เวรอำนวยกาน" to "เวรอำนวยการ",
            "วันอาทิต" to "วันอาทิตย์",
            "วันอาทิด" to "วันอาทิตย์",
            "วันจัน" to "วันจันทร์",
            "วันพุด" to "วันพุธ",
            "วันพฤหัด" to "วันพฤหัส",
            "กะกฎาคม" to "กรกฎาคม",
            "กกฎาคม" to "กรกฎาคม",
            "มหาราษฎร์" to "มหาราช",
            "มหาราษ" to "มหาราช",
            "อ้างทอง" to "อ่างทอง",
            "อายุธยา" to "อยุธยา",
            "สิงบุรี" to "สิงห์บุรี",
            "ลบบุรี" to "ลพบุรี",
            "สุพันบุรี" to "สุพรรณบุรี",
            "ประทุมธานี" to "ปทุมธานี",
            "นครสวรร" to "นครสวรรค์",
            "ผู้กับ" to "ผู้กำกับ",
            "สารวัต" to "สารวัตร",
            "รองสารวัต" to "รองสารวัตร",
            "พนักงานสวบสวน" to "พนักงานสอบสวน",
            "สายตวด" to "สายตรวจ",
            "ป้องกันปราบปาม" to "ป้องกันปราบปราม",
            "โลงเรียน" to "โรงเรียน",
            "ลาชการ" to "ราชการ",
            "เลียบร้อย" to "เรียบร้อย",
            "เตือนกินย่า" to "เตือนกินยา",
            "เปิดลาย" to "เปิด line",
            "เปิดยูทูป" to "เปิด youtube",
            "เปิดกูเกิ้ล" to "เปิด google",
            "เปิดเฟสบุ๊ค" to "เปิด facebook",
            "เปิดติ๊กตอก" to "เปิดติ๊กต็อก",
            "เปิดแชทจีพีที" to "เปิด chatgpt",
            "โทหา" to "โทรหา",
            "โทกลับ" to "โทรกลับ",
            "วิดีโอคอน" to "วิดีโอคอล",
            "โทด่วน" to "โทรด่วน",
            "นำทางง" to "นำทาง",
            "พาไบ" to "พาไป",
            "กลับบาน" to "กลับบ้าน",
            "ไปโรงพักค์" to "ไปโรงพัก",
            "ไปโลงเรียน" to "ไปโรงเรียน",
            "เตือนประชุมม" to "เตือนประชุม",
            "ประชุมม" to "ประชุม",
            "แจ้งเดือน" to "แจ้งเตือน",
            "สมชาญ" to "สมชาย",
            "สมสัก" to "สมศักดิ์",
            "ประยุด" to "ประยุทธ",
            "นัดวุฒิ" to "ณัฐวุฒิ",
            "ธนะกิต" to "ธนกฤต",
            "อภิสิท" to "อภิสิทธิ์",
            "เดียว" to "เดี๋ยว",
            "คับ" to "ครับ",
            "นะคับ" to "นะครับ",
            "วอหนึ่ง" to "ว.1",
            "วอสอง" to "ว.2",
            "วอสี่" to "ว.4",
            "วอแปด" to "ว.8",
            "วอยี่สิบ" to "ว.20",
            "สูนวิทยุ" to "ศูนย์วิทยุ",
            "ร้อยเวน" to "ร้อยเวร",
            "สิบเวน" to "สิบเวร"
        )
        phraseCorrections.forEach { (wrong, correct) ->
            if (wrong == "วันจัน") {
                normalized = normalized.replace("วันจันทร์", "__WAN_JAN__")
                normalized = normalized.replace(wrong, correct, ignoreCase = true)
                normalized = normalized.replace("__WAN_JAN__", "วันจันทร์")
            } else if (wrong == "วันอาทิต") {
                normalized = normalized.replace("วันอาทิตย์", "__WAN_ATIT__")
                normalized = normalized.replace(wrong, correct, ignoreCase = true)
                normalized = normalized.replace("__WAN_ATIT__", "วันอาทิตย์")
            } else {
                normalized = normalized.replace(wrong, correct, ignoreCase = true)
            }
        }

        normalized = replaceThaiWrittenNumbers(normalized)

        if (
            normalized.equals("line", ignoreCase = true) ||
            normalized == "ไลน์" ||
            normalized == "ไลน" ||
            normalized == "ไล"
        ) {
            normalized = "เปิด line"
        } else if (normalized.equals("youtube", ignoreCase = true) || normalized == "ยูทูป") {
            normalized = "เปิด youtube"
        } else if (normalized.equals("facebook", ignoreCase = true) || normalized == "เฟส" || normalized == "เฟซ" || normalized == "เฟซบุ๊ก") {
            normalized = "เปิด facebook"
        } else if (normalized.equals("google map", ignoreCase = true) || normalized.equals("google maps", ignoreCase = true) || normalized == "แผนที่" || normalized.equals("gps", ignoreCase = true)) {
            normalized = "เปิด google map"
        }

        normalized = normalized
            .replace(Regex("([\\u0E00-\\u0E7F])([a-z0-9])"), "$1 $2")
            .replace(Regex("([a-z0-9])([\\u0E00-\\u0E7F])"), "$1 $2")

        return normalized
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun removeIrrelevantWords(text: String): String {
        var normalized = text
        if (normalized.startsWith("ช่วย") && !normalized.startsWith("ช่วยด้วย")) {
            normalized = normalized.removePrefix("ช่วย").trim()
        }
        if (normalized.startsWith("ขอ") && !normalized.startsWith("ขอโทษ")) {
            normalized = normalized.removePrefix("ขอ").trim()
        }
        val removableAnywhere = listOf(
            "รบกวน", "ครับ", "ค่ะ", "คะ", "หน่อยนะคะ", "หน่อยนะ", "ให้หน่อยนะคะ",
            "ให้หน่อยนะ", "ให้หน่อย", "ให้ที", "หน่อย", "กรุณา", "ช่วยกรุณา"
        ).sortedByDescending { it.length }
        removableAnywhere.forEach { phrase ->
            normalized = normalized.replace(phrase, "")
        }

        val boundaryWords = listOf("สิ", "ที", "ขอ")
        boundaryWords.forEach { phrase ->
            normalized = normalized.replace(Regex("(^|\\s)${Regex.escape(phrase)}(?=\\s|$)"), " ")
        }

        return normalized
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeAppName(value: String): String {
        return value.lowercase(Locale.ROOT)
            .replace("ไลน์แมน", "lineman")
            .replace("ไลน์", "line")
            .replace("ลาย", "line")
            .replace("ยูทูป", "youtube")
            .replace("ยูทูบ", "youtube")
            .replace("ยูทรูป", "youtube")
            .replace("เฟซบุ๊ก", "facebook")
            .replace("เฟสบุ๊ก", "facebook")
            .replace("เฟสบุ๊ค", "facebook")
            .replace("เฟซ", "facebook")
            .replace("เฟส", "facebook")
            .replace("กูเกิลแมพ", "googlemap")
            .replace("กูเกิ้ลแมพ", "googlemap")
            .replace("กูเกิลแมป", "googlemap")
            .replace("กูเกิ้ลแมป", "googlemap")
            .replace("กูเกิล", "google")
            .replace("กูเกิ้ล", "google")
            .replace("แชตจีพีที", "chatgpt")
            .replace("แชทgpt", "chatgpt")
            .replace("แชตgpt", "chatgpt")
            .replace("แมพ", "map")
            .replace("แอป", "")
            .replace("แอพ", "")
            .replace("application", "")
            .replace(Regex("[^\\p{L}\\p{N}\\u0e00-\\u0e7f]"), "")
            .trim()
    }

    private fun normalizeContactName(value: String): String {
        val standardized = value.replace(Regex("\\s*\\.\\s*"), ".")
            .replace("ทุกคน", "")
            .replace("ทั้งหมด", "")
            .replace("ทุกเบอร์", "")
            .replace("เลย", "")
        val lowercase = standardized.lowercase(Locale.ROOT).trim()
        val rawPrefixes = listOf(
            "คุณ", "นาย", "นาง", "นางสาว", "ดช", "ดญ",
            "พันตำรวจเอก", "พันตำรวจโท", "พันตำรวจตรี",
            "ร้อยตำรวจเอก", "ร้อยตำรวจโท", "ร้อยตำรวจตรี",
            "ดาบตำรวจ", "จ่าสิบตำรวจ",
            "สิบตำรวจเอก", "สิบตำรวจโท", "สิบตำรวจตรี",
            "พลตำรวจเอก", "พลตำรวจโท", "พลตำรวจตรี",
            "พลตำรวจ", "พลเอก", "พลโท", "พลตรี",
            "พันเอก", "พันโท", "พันตรี",
            "ร้อยเอก", "ร้อยโท", "ร้อยตรี",
            "ศาสตราจารย์", "รองศาสตราจารย์", "ผู้ช่วยศาสตราจารย์",
            "พตอ", "พตท", "พตต",
            "รตอ", "รตท", "รตต",
            "ดต", "จสต",
            "สตอ", "สตท", "สตต",
            "พลตอ", "พลตท", "พลตต",
            "พลอ", "พลท", "พลต",
            "ศดร", "ศ", "รศ", "ผศ", "ดร", "นพ", "พญ", "หมอ",
            "ผู้กอง", "ผู้กำกับ", "สารวัตร", "ดาบ", "ผู้กำกับการ"
        )
        val prefixWords = rawPrefixes.sortedByDescending { it.length }

        // Normalise punctuation/symbols first (e.g. "พ.ต.อ." -> "พตอ")
        var temp = lowercase.replace(Regex("[^\\p{L}\\p{N}\\u0e00-\\u0e7f\\s]"), "").trim()

        var changed = true
        while (changed) {
            changed = false
            for (word in prefixWords) {
                if (temp.startsWith(word)) {
                    temp = temp.substring(word.length).trim()
                    changed = true
                    break
                }
            }
        }
        
        temp = temp.replace(Regex("\\s+"), " ")
        if (temp.isEmpty()) {
            return lowercase.replace(Regex("[^\\p{L}\\p{N}\\u0e00-\\u0e7f\\s]"), "").trim().replace(Regex("\\s+"), " ")
        }
        return temp
    }

    private fun getEnglishPhoneticAliases(name: String): List<String> {
        val clean = name.trim().lowercase(Locale.ROOT)
        val aliases = mutableListOf<String>()
        
        // Kinship mappings
        when (clean) {
            "แม่" -> aliases.addAll(listOf("mom", "mother"))
            "พ่อ" -> aliases.addAll(listOf("dad", "father"))
            "ตา", "ปู่" -> aliases.addAll(listOf("grandpa", "grandfather"))
            "ยาย", "ย่า" -> aliases.addAll(listOf("grandma", "grandmother"))
            "ลูก" -> aliases.addAll(listOf("son", "daughter"))
            "mom", "mother" -> aliases.add("แม่")
            "dad", "father" -> aliases.add("พ่อ")
            "grandpa", "grandfather" -> aliases.addAll(listOf("ตา", "ปู่"))
            "grandma", "grandmother" -> aliases.addAll(listOf("ยาย", "ย่า"))
            "son", "daughter" -> aliases.add("ลูก")
        }

        // Common Names transliterations (Thai to English & English to Thai)
        val transliterations = mapOf(
            "เดวิด" to "david",
            "เดฟ" to "dave",
            "จอห์น" to "john",
            "จอน" to "john",
            "แมรี่" to "mary",
            "ทอม" to "tom",
            "ปีเตอร์" to "peter",
            "ไมเคิล" to "michael",
            "แอน" to "ann",
            "ลิซ่า" to "lisa",
            "เจมส์" to "james",
            "แจ็ค" to "jack",
            
            "david" to "เดวิด",
            "dave" to "เดฟ",
            "john" to "จอห์น",
            "mary" to "แมรี่",
            "tom" to "ทอม",
            "peter" to "ปีเตอร์",
            "michael" to "ไมเคิล",
            "ann" to "แอน",
            "lisa" to "ลิซ่า",
            "james" to "เจมส์",
            "jack" to "แจ็ค"
        )
        
        transliterations[clean]?.let { aliases.add(it) }
        return aliases
    }

    private fun findBestMatchingContact(targetName: String, contacts: List<DeviceContact>): DeviceContact? {
        val normalizedTarget = normalizeContactName(targetName)
        if (normalizedTarget.isBlank()) return null

        val targetAliases = (getEnglishPhoneticAliases(targetName) + normalizedTarget)
            .map { normalizeContactName(it) }
            .filter { it.isNotBlank() }
            .distinct()

        return contacts
            .mapNotNull { contact ->
                val normalizedContact = normalizeContactName(contact.name)
                if (normalizedContact.isBlank()) return@mapNotNull null

                val contactAliases = (getEnglishPhoneticAliases(contact.name) + normalizedContact)
                    .map { normalizeContactName(it) }
                    .filter { it.isNotBlank() }
                    .distinct()

                var bestScore = 0
                for (tAlias in targetAliases) {
                    val tWords = tAlias.split(" ").filter { it.isNotBlank() }
                    if (tWords.isEmpty()) continue

                    for (cAlias in contactAliases) {
                        val cWords = cAlias.split(" ").filter { it.isNotBlank() }
                        if (cWords.isEmpty()) continue

                        val score = when {
                            cAlias == tAlias -> 500
                            
                            // If target has multiple words, do a broad contains match
                            tWords.size > 1 && cAlias.contains(tAlias) -> 420
                            
                            // If target has a single word, restrict matching:
                            // It must match the first name (either exactly or as substring),
                            // OR it must exactly match one of the surname/other words.
                            tWords.size == 1 -> {
                                val tWord = tWords[0]
                                val firstName = cWords[0]
                                val otherWords = cWords.drop(1)
                                
                                when {
                                    firstName == tWord -> 450
                                    firstName.contains(tWord) -> 420
                                    otherWords.any { it == tWord } -> 400
                                    else -> 0
                                }
                            }
                            
                            tAlias.contains(cAlias) -> 380
                            
                            // Levenshtein distance check on full string (ignoring spaces)
                            levenshteinDistance(cAlias.replace(" ", ""), tAlias.replace(" ", "")) <= 1 -> 320
                            
                            cAlias.firstOrNull() == tAlias.firstOrNull() &&
                                levenshteinDistance(cAlias.replace(" ", ""), tAlias.replace(" ", "")) <= 2 -> 260
                                
                            else -> 0
                        }
                        if (score > bestScore) {
                            bestScore = score
                        }
                    }
                }

                if (bestScore > 0) contact to bestScore else null
            }
            .sortedWith(
                compareByDescending<Pair<DeviceContact, Int>> { it.second }
                    .thenBy { it.first.name.length }
            )
            .firstOrNull()
            ?.first
    }

    private fun findBestMatchingApp(targetAppName: String, apps: List<Pair<String, String>>): Pair<String, String>? {
        val normalizedTarget = normalizeAppName(targetAppName)
        if (normalizedTarget.isBlank()) return null

        val preferredPackages = mapOf(
            "line" to listOf("jp.naver.line.android", "com.linecorp.line.android"),
            "ไลน์" to listOf("jp.naver.line.android", "com.linecorp.line.android"),
            "youtube" to listOf("com.google.android.youtube"),
            "ยูทูป" to listOf("com.google.android.youtube"),
            "facebook" to listOf("com.facebook.katana"),
            "เฟส" to listOf("com.facebook.katana"),
            "เฟซ" to listOf("com.facebook.katana"),
            "tiktok" to listOf("com.zhiliaoapp.musically"),
            "ติ๊กต๊อก" to listOf("com.zhiliaoapp.musically"),
            "ติ๊กตอก" to listOf("com.zhiliaoapp.musically"),
            "spotify" to listOf("com.spotify.music"),
            "googlemap" to listOf("com.google.android.apps.maps"),
            "googlemaps" to listOf("com.google.android.apps.maps"),
            "googleแมพ" to listOf("com.google.android.apps.maps"),
            "googlemapส์" to listOf("com.google.android.apps.maps"),
            "gmap" to listOf("com.google.android.apps.maps"),
            "gps" to listOf("com.google.android.apps.maps", "com.waze"),
            "maps" to listOf("com.google.android.apps.maps", "com.waze"),
            "map" to listOf("com.google.android.apps.maps", "com.waze"),
            "แมพ" to listOf("com.google.android.apps.maps", "com.waze"),
            "แผนที่" to listOf("com.google.android.apps.maps", "com.waze"),
            "นำทาง" to listOf("com.google.android.apps.maps", "com.waze"),
            "google" to listOf("com.google.android.apps.maps", "com.google.android.googlequicksearchbox"),
            "อุ่นใจ" to listOf(SELF_APP_PACKAGE),
            "nongkanvela" to listOf(SELF_APP_PACKAGE),
            "เซเว่น" to listOf("asuk.com.android.app"),
            "7" to listOf("asuk.com.android.app"),
            "7eleven" to listOf("asuk.com.android.app"),
            "seven" to listOf("asuk.com.android.app")
        )

        preferredPackages[normalizedTarget]?.forEach { preferredPackage ->
            apps.firstOrNull { it.second == preferredPackage }?.let { return it }
        }

        if (
            normalizedTarget.contains("googlemap") ||
            normalizedTarget.contains("googlemaps") ||
            normalizedTarget == "maps" ||
            normalizedTarget == "map" ||
            normalizedTarget == "gps" ||
            normalizedTarget.contains("แผนที่") ||
            normalizedTarget.contains("แมพ") ||
            normalizedTarget.contains("นำทาง")
        ) {
            preferredPackages["googlemap"]?.forEach { preferredPackage ->
                apps.firstOrNull { it.second == preferredPackage }?.let { return it }
            }
            preferredPackages["gps"]?.forEach { preferredPackage ->
                apps.firstOrNull { it.second == preferredPackage }?.let { return it }
            }
        }

        return apps
            .map { app ->
                val normalizedAppName = normalizeAppName(app.first)
                val score = when {
                    normalizedAppName == normalizedTarget -> 500
                    app.second.equals(normalizedTarget, ignoreCase = true) -> 450
                    app.second.contains(normalizedTarget, ignoreCase = true) -> 350
                    normalizedAppName.startsWith(normalizedTarget) -> 300
                    normalizedTarget.startsWith(normalizedAppName) -> 250
                    normalizedAppName.contains(normalizedTarget) -> 200
                    normalizedTarget.contains(normalizedAppName) -> 150
                    levenshteinDistance(normalizedAppName, normalizedTarget) <= 1 -> 140
                    else -> 0
                }
                app to score
            }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<Pair<String, String>, Int>> { it.second }
                    .thenBy { it.first.first.length }
            )
            .firstOrNull()
            ?.first
    }

    private fun findAmbiguousAppIntent(targetAppName: String, apps: List<Pair<String, String>>): String? {
        val normalizedTarget = normalizeAppName(targetAppName)
        if (normalizedTarget.isBlank()) return null

        val prefixFamily = apps.filter { normalizeAppName(it.first).startsWith(normalizedTarget) }
        if (normalizedTarget.length >= 3 && prefixFamily.size >= 2) {
            val topTwo = prefixFamily.take(2)
            return "หมายถึงเปิด ${topTwo[0].first} หรือ ${topTwo[1].first} คะ กรุณาพูดชื่อแอปอีกครั้งให้ชัดเจนนะคะ"
        }

        val candidates = apps
            .map { app ->
                val normalizedAppName = normalizeAppName(app.first)
                val score = when {
                    normalizedAppName == normalizedTarget -> 500
                    normalizedAppName.startsWith(normalizedTarget) -> 320
                    normalizedAppName.contains(normalizedTarget) -> 300
                    normalizedTarget.contains(normalizedAppName) -> 260
                    levenshteinDistance(normalizedAppName, normalizedTarget) <= 1 -> 220
                    else -> 0
                }
                app to score
            }
            .filter { it.second >= 220 }
            .sortedByDescending { it.second }

        if (candidates.size < 2) return null

        val top = candidates[0]
        val second = candidates[1]
        val tightlyMatched = top.second - second.second <= 40
        val sharedPrefix = normalizeAppName(top.first.first).startsWith(normalizedTarget) &&
            normalizeAppName(second.first.first).startsWith(normalizedTarget)

        return if (tightlyMatched && sharedPrefix) {
            "หมายถึงเปิด ${top.first.first} หรือ ${second.first.first} คะ กรุณาพูดชื่อแอปอีกครั้งให้ชัดเจนนะคะ"
        } else {
            null
        }
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }

    private fun parseThaiTime(text: String): Pair<Int, Int>? {
        val cleanText = text.trim()
        
        // Map Thai verbal numbers and combined terms to digits in descending order of precedence
        var normalized = cleanText
            // 50s
            .replace("ห้าสิบเก้า", "59")
            .replace("ห้าสิบแปด", "58")
            .replace("ห้าสิบเจ็ด", "57")
            .replace("ห้าสิบหก", "56")
            .replace("ห้าสิบห้า", "55")
            .replace("ห้าสิบสี่", "54")
            .replace("ห้าสิบสาม", "53")
            .replace("ห้าสิบสอง", "52")
            .replace("ห้าสิบเอ็ด", "51")
            .replace("ห้าสิบ", "50")
            // 40s
            .replace("สี่สิบเก้า", "49")
            .replace("สี่สิบแปด", "48")
            .replace("สี่สิบเจ็ด", "47")
            .replace("สี่สิบหก", "46")
            .replace("สี่สิบห้า", "45")
            .replace("สี่สิบสี่", "44")
            .replace("สี่สิบสาม", "43")
            .replace("สี่สิบสอง", "42")
            .replace("สี่สิบเอ็ด", "41")
            .replace("สี่สิบ", "40")
            // 30s
            .replace("สามสิบเก้า", "39")
            .replace("สามสิบแปด", "38")
            .replace("สามสิบเจ็ด", "37")
            .replace("สามสิบหก", "36")
            .replace("สามสิบห้า", "35")
            .replace("สามสิบสี่", "34")
            .replace("สามสิบสาม", "33")
            .replace("สามสิบสอง", "32")
            .replace("สามสิบเอ็ด", "31")
            .replace("สามสิบ", "30")
            // 20s
            .replace("ยี่สิบเก้า", "29")
            .replace("ยี่สิบแปด", "28")
            .replace("ยี่สิบเจ็ด", "27")
            .replace("ยี่สิบหก", "26")
            .replace("ยี่สิบห้า", "25")
            .replace("ยี่สิบสี่", "24")
            .replace("ยี่สิบสาม", "23")
            .replace("ยี่สิบสอง", "22")
            .replace("ยี่สิบเอ็ด", "21")
            .replace("ยี่สิบ", "20")
            // 10s
            .replace("สิบเก้า", "19")
            .replace("สิบแปด", "18")
            .replace("สิบเจ็ด", "17")
            .replace("สิบหก", "16")
            .replace("สิบห้า", "15")
            .replace("สิบสี่", "14")
            .replace("สิบสาม", "13")
            .replace("สิบสอง", "12")
            .replace("สิบเอ็ด", "11")
            .replace("สิบ", "10")
            // Units
            .replace("หนึ่ง", "1")
            .replace("สอง", "2")
            .replace("สาม", "3")
            .replace("สี่", "4")
            .replace("ห้า", "5")
            .replace("หก", "6")
            .replace("เจ็ด", "7")
            .replace("แปด", "8")
            .replace("เก้า", "9")

        var hour: Int? = null
        var hourEndIndex = 0

        val patterns = listOf(
            // เที่ยงคืน
            Pair(Regex("เที่ยงคืน")) { match: MatchResult -> 
                hour = 0
                hourEndIndex = match.range.last + 1
            },
            // เที่ยง
            Pair(Regex("เที่ยง")) { match: MatchResult -> 
                hour = 12
                hourEndIndex = match.range.last + 1
            },
            // ตี 1 - ตี 5
            Pair(Regex("ตี\\s*([1-5])")) { match: MatchResult -> 
                hour = match.groupValues[1].toInt()
                hourEndIndex = match.range.last + 1
            },
            // บ่าย 2-5 โมง
            Pair(Regex("บ่าย\\s*([2-5])\\s*โมง")) { match: MatchResult -> 
                hour = 12 + match.groupValues[1].toInt()
                hourEndIndex = match.range.last + 1
            },
            // บ่ายโมง
            Pair(Regex("บ่าย\\s*โมง")) { match: MatchResult -> 
                hour = 13
                hourEndIndex = match.range.last + 1
            },
            // บ่าย 2-5
            Pair(Regex("บ่าย\\s*([2-5])")) { match: MatchResult -> 
                hour = 12 + match.groupValues[1].toInt()
                hourEndIndex = match.range.last + 1
            },
            // 5-6 โมงเย็น
            Pair(Regex("([56])\\s*โมงเย็น")) { match: MatchResult -> 
                hour = 12 + match.groupValues[1].toInt()
                hourEndIndex = match.range.last + 1
            },
            // 2-5 ทุ่ม
            Pair(Regex("([2-5])\\s*ทุ่ม")) { match: MatchResult -> 
                hour = 18 + match.groupValues[1].toInt()
                hourEndIndex = match.range.last + 1
            },
            // ทุ่มนึง / ทุ่มตรง / ทุ่ม
            Pair(Regex("ทุ่ม(?:นึง|ตรง)?")) { match: MatchResult -> 
                hour = 19
                hourEndIndex = match.range.last + 1
            },
            // 6 โมงเช้า - 11 โมง
            Pair(Regex("([6-9]|10|11)\\s*โมง")) { match: MatchResult -> 
                hour = match.groupValues[1].toInt()
                hourEndIndex = match.range.last + 1
            }
        )

        for (item in patterns) {
            val match = item.first.find(normalized)
            if (match != null) {
                item.second(match)
                break
            }
        }

        if (hour == null) return null

        val remaining = normalized.substring(hourEndIndex).trim()
        var minute = 0
        if (remaining.contains("ครึ่ง")) {
            minute = 30
        } else {
            // Find the first sequence of digits in the remaining string
            Regex("(\\d{1,2})").find(remaining)?.let { match ->
                minute = match.groupValues[1].toIntOrNull() ?: 0
            }
        }

        return Pair(hour, minute)
    }

    private fun handleChitchat(text: String): String? {
        val cleanText = text.trim()
        
        // 1. How are you / well-being
        if (cleanText.contains("เป็นยังไงบ้าง") || cleanText.contains("สบายดีไหม") || cleanText.contains("เป็นอย่างไรบ้าง") || cleanText.contains("เหนื่อยไหม")) {
            return "อุ่นใจสบายดีค่ะ พร้อมดูแลและคอยช่วยเหลือคุณตาคุณยายตลอดเวลานะคะ วันนี้คุณตาคุณยายรู้สึกอย่างไรบ้างคะ?"
        }
        
        // 2. What are you doing
        if (cleanText.contains("ทำอะไรอยู่") || cleanText.contains("ทำอะไร")) {
            return "อุ่นใจกำลังรอช่วยรับคำสั่งและดูแลคุณตาคุณยายอยู่ค่ะ มีอะไรให้อุ่นใจช่วยเหลือบอกได้เลยนะคะ"
        }
        
        // 3. Lonely
        if (cleanText.contains("เหงา") || cleanText.contains("เหงาจัง") || cleanText.contains("เบื่อ") || cleanText.contains("เบื่อจัง")) {
            return "คุยกับอุ่นใจได้เสมอนะคะ อุ่นใจอยู่ตรงนี้เป็นเพื่อนคุณตาคุณยายค่ะ หรืออยากให้โทรหาลูกหลานแจ้งได้เลยนะคะ"
        }
        
        // 4. Love / Miss
        if (cleanText.contains("รักนะ") || cleanText.contains("รักอุ่นใจ") || (cleanText.contains("รัก") && cleanText.contains("อุ่นใจ"))) {
            return "อุ่นใจก็รักและดีใจที่ได้ดูแลคุณตาคุณยายค่ะ ขอบคุณมากๆ นะคะ"
        }
        if (cleanText.contains("คิดถึง") || cleanText.contains("คิดถึงจัง")) {
            return "อุ่นใจก็คิดถึงคุณตาคุณยายเช่นกันค่ะ วันนี้มีเรื่องอะไรให้อุ่นใจช่วยดูแลไหมคะ?"
        }
        
        // 5. Compliments
        if (cleanText.contains("เก่งมาก") || cleanText.contains("ดีมาก") || cleanText.contains("น่ารัก") || cleanText.contains("น่ารักจัง")) {
            return "ขอบคุณค่ะคุณตาคุณยาย อุ่นใจยินดีที่ได้ช่วยเหลือและทำหน้าที่ดูแลนะคะ"
        }
        
        // 6. Food/eating
        if (cleanText.contains("กินข้าวรึยัง") || cleanText.contains("กินข้าวหรือยัง") || cleanText.contains("ทานข้าวรึยัง") || cleanText.contains("ทานข้าวหรือยัง")) {
            return "อุ่นใจเป็นระบบผู้ช่วยดิจิทัลเลยทานอาหารไม่ได้ค่ะ แต่ขอบคุณที่เป็นห่วงนะคะ คุณตาคุณยายทานข้าวหรือยังคะ อย่าลืมทานอาหารให้อิ่มและทานยาด้วยนะคะ"
        }
        
        // 7. Tired
        if (cleanText.contains("เหนื่อย") || cleanText.contains("เหนื่อยจัง") || cleanText.contains("เพลีย")) {
            return "พักผ่อนสักหน่อยนะคะ ดื่มน้ำสะอาดเยอะๆ อุ่นใจเป็นห่วงและอยากให้คุณตาคุณยายแข็งแรงค่ะ"
        }
        
        // 8. Sleep/wake up
        if (cleanText.contains("นอนแล้วนะ") || cleanText.contains("ไปนอนแล้ว") || cleanText.contains("นอนก่อนนะ") || cleanText.contains("ฝันดี") || cleanText.contains("ราตรีสวัสดิ์")) {
            return "ฝันดีราตรีสวัสดิ์ค่ะคุณตาคุณยาย พักผ่อนให้เต็มอิ่ม อุ่นใจจะคอยเฝ้าดูแลให้ปลอดภัยนะคะ"
        }
        if (cleanText.contains("ตื่นแล้ว") || cleanText.contains("อรุณสวัสดิ์")) {
            return "อรุณสวัสดิ์ค่ะ ขอให้วันนี้เป็นวันที่ดี ร่างกายแข็งแรง และมีความสุขมากๆ นะคะคุณตาคุณยาย"
        }
        
        // 9. Identity
        if (cleanText.contains("เธอเป็นใคร") || cleanText.contains("เป็นใคร") || cleanText.contains("แกเป็นใคร") || cleanText.contains("เธอคือใคร")) {
            return "อุ่นใจคือผู้ช่วยเสียงส่วนตัวที่จะช่วยคุณตาคุณยายโทรออก ส่งข้อความ หรือสั่งงานมือถือด้วยเสียงค่ะ"
        }
        if (cleanText.contains("ชื่ออะไร") || cleanText.contains("ชื่ออะไรนะ") || cleanText.contains("แกชื่ออะไร")) {
            return "หนูชื่ออุ่นใจค่ะ เป็นผู้ช่วยและเลขาส่วนตัวของคุณตาคุณยายค่ะ"
        }
        if (cleanText.contains("ใครสร้าง") || cleanText.contains("ใครพัฒนา") || cleanText.contains("ผู้พัฒนาคือใคร")) {
            return "ทีมผู้พัฒนาสร้างหนูอุ่นใจขึ้นมาเพื่อดูแลและช่วยเหลือคุณตาคุณยายในชีวิตประจำวันค่ะ"
        }
        
        // 10. Feeling sick (but not severe enough for immediate emergency call)
        if (cleanText.contains("ปวดหัว") || cleanText.contains("ปวดท้อง") || cleanText.contains("เจ็บขา") || cleanText.contains("ไม่สบาย") || cleanText.contains("คัดจมูก") || cleanText.contains("เป็นหวัด")) {
            return "คุณตาคุณยายพักผ่อนเยอะๆ นะคะ ดื่มน้ำอุ่นและทานยาตามเวลาด้วยค่ะ ถ้าอาการไม่ดีขึ้นอยากให้โทรหาลูกหลานหรือคุณหมอบอกอุ่นใจได้ทันทีเลยนะคะ"
        }
        
        // 11. Greeting synonyms not caught by greeting command
        if (cleanText == "ฮัลโหล" || cleanText == "เฮโล" || cleanText == "หวัดดี" || cleanText == "หวัดดีครับ" || cleanText == "หวัดดีค่ะ") {
            return "สวัสดีค่ะ อุ่นใจพร้อมช่วยดูแลและช่วยสั่งงานให้แล้วค่ะ มีอะไรให้รับใช้ไหมคะ?"
        }

        return null
    }

    fun expandThaiAbbreviationsForSpeech(text: String): String {
        var result = text
        val mappings = listOf(
            // 3-letter ranks with dots/spaces
            Regex("พล\\s*\\.\\s*ต\\s*\\.\\s*อ\\s*\\.?") to "พลตำรวจเอก",
            Regex("พล\\s*\\.\\s*ต\\s*\\.\\s*ท\\s*\\.?") to "พลตำรวจโท",
            Regex("พล\\s*\\.\\s*ต\\s*\\.\\s*ต\\s*\\.?") to "พลตำรวจตรี",
            
            Regex("พ\\s*\\.\\s*ต\\s*\\.\\s*อ\\s*\\.?") to "พันตำรวจเอก",
            Regex("พ\\s*\\.\\s*ต\\s*\\.\\s*ท\\s*\\.?") to "พันตำรวจโท",
            Regex("พ\\s*\\.\\s*ต\\s*\\.\\s*ต\\s*\\.?") to "พันตำรวจตรี",
            
            Regex("ร\\s*\\.\\s*ต\\s*\\.\\s*อ\\s*\\.?") to "ร้อยตำรวจเอก",
            Regex("ร\\s*\\.\\s*ต\\s*\\.\\s*ท\\s*\\.?") to "ร้อยตำรวจโท",
            Regex("ร\\s*\\.\\s*ต\\s*\\.\\s*ต\\s*\\.?") to "ร้อยตำรวจตรี",
            
            Regex("ส\\s*\\.\\s*ต\\s*\\.\\s*อ\\s*\\.?") to "สิบตำรวจเอก",
            Regex("ส\\s*\\.\\s*ต\\s*\\.\\s*ท\\s*\\.?") to "สิบตำรวจโท",
            Regex("ส\\s*\\.\\s*ต\\s*\\.\\s*ต\\s*\\.?") to "สิบตำรวจตรี",
            
            Regex("จ\\s*\\.\\s*ส\\s*\\.\\s*ต\\s*\\.?") to "จ่าสิบตำรวจ",
            
            // 2-letter ranks with dots/spaces
            Regex("ด\\s*\\.\\s*ต\\s*\\.?") to "ดาบตำรวจ",
            Regex("พล\\s*\\.\\s*ต\\s*\\.?") to "พลตำรวจ",
            Regex("พล\\s*\\.\\s*อ\\s*\\.?") to "พลเอก",
            Regex("พล\\s*\\.\\s*ท\\s*\\.?") to "พลโท",
            Regex("พ\\s*\\.\\s*อ\\s*\\.?") to "พันเอก",
            Regex("พ\\s*\\.\\s*ท\\s*\\.?") to "พันโท",
            Regex("พ\\s*\\.\\s*ต\\s*\\.?") to "พันตรี",
            Regex("ร\\s*\\.\\s*อ\\s*\\.?") to "ร้อยเอก",
            Regex("ร\\s*\\.\\s*ท\\s*\\.?") to "ร้อยโท",
            Regex("ร\\s*\\.\\s*ต\\s*\\.?") to "ร้อยตรี",
            
            // Medical and Academic
            Regex("ศ\\s*\\.\\s*ดร\\s*\\.\\s*") to "ศาสตราจารย์ดอกเตอร์",
            Regex("ศ\\s*\\.\\s*ดร\\s*\\.?") to "ศาสตราจารย์ดอกเตอร์",
            Regex("ศ\\s*\\.\\s*") to "ศาสตราจารย์",
            Regex("รศ\\s*\\.\\s*") to "รองศาสตราจารย์",
            Regex("ผศ\\s*\\.\\s*") to "ผู้ช่วยศาสตราจารย์",
            Regex("ดร\\s*\\.\\s*") to "ดอกเตอร์",
            Regex("นพ\\s*\\.\\s*") to "นายแพทย์",
            Regex("พญ\\s*\\.\\s*") to "แพทย์หญิง",
            
            // Non-dot ranks (requires word boundary, space, or start/end boundaries)
            Regex("พตอ\\s+") to "พันตำรวจเอก ",
            Regex("พตท\\s+") to "พันตำรวจโท ",
            Regex("พตต\\s+") to "พันตำรวจตรี ",
            Regex("รตอ\\s+") to "ร้อยตำรวจเอก ",
            Regex("รตท\\s+") to "ร้อยตำรวจโท ",
            Regex("รตต\\s+") to "ร้อยตำรวจตรี ",
            Regex("พลตอ\\s+") to "พลตำรวจเอก ",
            Regex("พลตท\\s+") to "พลตำรวจโท ",
            Regex("พลตต\\s+") to "พลตำรวจตรี ",
            Regex("สตอ\\s+") to "สิบตำรวจเอก ",
            Regex("สตท\\s+") to "สิบตำรวจโท ",
            Regex("สตต\\s+") to "สิบตำรวจตรี ",
            Regex("จสต\\s+") to "จ่าสิบตำรวจ ",
            Regex("ดต\\s+") to "ดาบตำรวจ ",
            Regex("พลต\\s+") to "พลตำรวจ ",
            Regex("พลอ\\s+") to "พลเอก ",
            Regex("พลท\\s+") to "พลโท ",
            Regex("ศดร\\s+") to "ศาสตราจารย์ดอกเตอร์ ",
            Regex("รศ\\s+") to "รองศาสตราจารย์ ",
            Regex("ผศ\\s+") to "ผู้ช่วยศาสตราจารย์ ",
            Regex("นพ\\s+") to "นายแพทย์ ",
            Regex("พญ\\s+") to "แพทย์หญิง ",
            Regex("ดร\\s+") to "ดอกเตอร์ ",
            
            // End of line boundaries
            Regex("พตอ$") to "พันตำรวจเอก",
            Regex("พตท$") to "พันตำรวจโท",
            Regex("พตต$") to "พันตำรวจตรี",
            Regex("รตอ$") to "ร้อยตำรวจเอก",
            Regex("รตท$") to "ร้อยตำรวจโท",
            Regex("รตต$") to "ร้อยตำรวจตรี",
            Regex("พลตอ$") to "พลตำรวจเอก",
            Regex("พลตท$") to "พลตำรวจโท",
            Regex("พลตต$") to "พลตำรวจตรี",
            Regex("สตอ$") to "สิบตำรวจเอก",
            Regex("สตท$") to "สิบตำรวจโท",
            Regex("สตต$") to "สิบตำรวจตรี",
            Regex("จสต$") to "จ่าสิบตำรวจ",
            Regex("ดต$") to "ดาบตำรวจ",
            Regex("พลต$") to "พลตำรวจ",
            Regex("พลอ$") to "พลเอก",
            Regex("พลท$") to "พลโท",
            Regex("ศดร$") to "ศาสตราจารย์ดอกเตอร์",
            Regex("รศ$") to "รองศาสตราจารย์",
            Regex("ผศ$") to "ผู้ช่วยศาสตราจารย์",
            Regex("นพ$") to "นายแพทย์",
            Regex("พญ$") to "แพทย์หญิง",
            Regex("ดร$") to "ดอกเตอร์"
        )

        for (mapping in mappings) {
            result = result.replace(mapping.first, mapping.second)
        }
        return result
    }
}
