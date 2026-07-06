package com.example.nongkanvelaassistant.data

import com.example.nongkanvelaassistant.ui.DeviceContact
import kotlin.math.max
import kotlin.math.roundToInt

data class ContactMatch(
    val contact: DeviceContact,
    val score: Int
)

object ContactFallbackResolver {
    private val phoneNumberPattern = Regex("""(?<!\d)(\+?\d[\d\s().-]{5,}\d)(?!\d)""")
    private const val MIN_MATCH_SCORE = 60

    private val contactPrefixes = listOf(
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
    ).sortedByDescending { it.length }

    private val trailingDecorators = listOf("เบอร์", "โทร", "โทรศัพท์", "หมายเลข")

    fun extractPhoneNumber(text: String): String? {
        val match = phoneNumberPattern.find(text) ?: return null
        return normalizePhoneNumber(match.value)
    }

    fun stripCommandPrefixes(text: String, prefixes: List<String>): String {
        var result = text.trim().replace(Regex("\\s+"), " ")
        var changed = true
        while (changed) {
            changed = false
            for (prefix in prefixes) {
                if (result.startsWith(prefix, ignoreCase = true)) {
                    result = result.substring(prefix.length).trim()
                    changed = true
                    break
                }
            }
        }
        return stripTrailingDecorators(result)
    }

    fun findBestMatchingContact(targetName: String, contacts: List<DeviceContact>): DeviceContact? {
        return findTopMatchingContacts(targetName, contacts, limit = 1).firstOrNull()?.contact
    }

    fun findTopMatchingContacts(
        targetName: String,
        contacts: List<DeviceContact>,
        limit: Int = 3
    ): List<ContactMatch> {
        val normalizedTarget = normalizeContactName(stripQueryPrefixes(targetName))
        if (normalizedTarget.isBlank()) return emptyList()

        val scored = contacts.mapNotNull { contact ->
            val score = scoreMatch(normalizedTarget, contact.name)
            if (score >= MIN_MATCH_SCORE) ContactMatch(contact, score) else null
        }

        return scored
            .sortedWith(
                compareByDescending<ContactMatch> { it.score }
                    .thenBy { it.contact.name.length }
                    .thenBy { it.contact.name }
            )
            .take(limit)
    }

    fun isLikelyAmbiguous(matches: List<ContactMatch>): Boolean {
        if (matches.size < 2) return false
        val first = matches[0]
        val second = matches[1]
        return first.score >= 70 && second.score >= 70 && (first.score - second.score).absoluteValue <= 8
    }

    private fun scoreMatch(normalizedTarget: String, contactName: String): Int {
        val normalizedContact = normalizeContactName(contactName)
        if (normalizedContact.isBlank()) return 0

        val digitsTarget = normalizedTarget.filter { it.isDigit() }
        val digitsContact = normalizedContact.filter { it.isDigit() }
        if (digitsTarget.isNotBlank()) {
            return if (digitsContact.contains(digitsTarget)) 100 else 0
        }

        val targetWords = normalizedTarget.split(" ").filter { it.isNotBlank() }
        val contactWords = normalizedContact.split(" ").filter { it.isNotBlank() }
        if (targetWords.isEmpty() || contactWords.isEmpty()) return 0

        if (normalizedTarget == normalizedContact) return 100
        if (targetWords.size > 1 && (normalizedContact.contains(normalizedTarget) || normalizedTarget.contains(normalizedContact))) {
            return 95
        }

        if (targetWords.size == 1) {
            val targetWord = targetWords[0]
            val firstWord = contactWords.first()
            val otherWords = contactWords.drop(1)
            val isShortQuery = targetWord.length < 4

            return when {
                firstWord == targetWord -> 450
                firstWord.startsWith(targetWord) || targetWord.startsWith(firstWord) -> 420
                !isShortQuery && otherWords.any { it == targetWord } -> 400
                !isShortQuery && contactWords.any { contactWord ->
                    levenshteinDistance(contactWord, targetWord) <= 1
                } -> 330
                else -> 0
            }
        }

        val exactWordHits = targetWords.count { targetWord ->
            contactWords.any { contactWord -> contactWord == targetWord }
        }
        val fuzzyWordHits = targetWords.count { targetWord ->
            contactWords.any { contactWord ->
                contactWord.contains(targetWord) || targetWord.contains(contactWord)
            }
        }
        val overlapScore = (exactWordHits * 18) + (fuzzyWordHits * 8)
        val prefixBonus = if (
            contactWords.firstOrNull()?.let { first ->
                targetWords.firstOrNull()?.let { target -> first.contains(target) || target.contains(first) }
            } == true
        ) 10 else 0

        val strippedTarget = normalizedTarget.replace(" ", "")
        val strippedContact = normalizedContact.replace(" ", "")
        val distance = levenshteinDistance(strippedTarget, strippedContact)
        val maxLength = max(strippedTarget.length, strippedContact.length)
        val similarityScore = if (maxLength == 0) 0 else ((1.0 - distance.toDouble() / maxLength) * 100).roundToInt()

        return max(overlapScore + prefixBonus, similarityScore)
    }

    private fun normalizeContactName(value: String): String {
        var temp = value
            .replace(Regex("\\s*\\.\\s*"), ".")
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\u0e00-\\u0e7f\\s]"), " ")
            .trim()

        var changed = true
        while (changed) {
            changed = false
            for (prefix in contactPrefixes) {
                if (temp.startsWith(prefix)) {
                    temp = temp.substring(prefix.length).trim()
                    changed = true
                    break
                }
            }
        }

        temp = temp.replace(Regex("\\s+"), " ")
        temp = collapseRankAbbreviations(temp)
        return stripTrailingDecorators(temp)
    }

    private fun collapseRankAbbreviations(value: String): String {
        var result = value
        val rules = listOf(
            Regex("(^|\\s)พ\\s+ต\\s+อ(?=\\s|$)") to "พตอ",
            Regex("(^|\\s)พ\\s+ต\\s+ท(?=\\s|$)") to "พตท",
            Regex("(^|\\s)พ\\s+ต\\s+ต(?=\\s|$)") to "พตต",
            Regex("(^|\\s)ร\\s+ต\\s+อ(?=\\s|$)") to "รตอ",
            Regex("(^|\\s)ร\\s+ต\\s+ท(?=\\s|$)") to "รตท",
            Regex("(^|\\s)ร\\s+ต\\s+ต(?=\\s|$)") to "รตต",
            Regex("(^|\\s)จ\\s+ส\\s+ต(?=\\s|$)") to "จสต",
            Regex("(^|\\s)ส\\s+ต\\s+อ(?=\\s|$)") to "สตอ",
            Regex("(^|\\s)ส\\s+ต\\s+ท(?=\\s|$)") to "สตท",
            Regex("(^|\\s)ส\\s+ต\\s+ต(?=\\s|$)") to "สตต"
        )

        for ((pattern, replacement) in rules) {
            result = result.replace(pattern) { matchResult ->
                val prefix = matchResult.groupValues[1]
                if (prefix.isNotBlank()) "$prefix$replacement" else replacement
            }
        }
        return result
    }

    private fun stripTrailingDecorators(value: String): String {
        var result = value.trim()
        var changed = true
        while (changed) {
            changed = false
            for (word in trailingDecorators) {
                if (result.endsWith(" $word")) {
                    result = result.removeSuffix(" $word").trim()
                    changed = true
                    break
                }
                if (result.endsWith(word) && result.length > word.length) {
                    result = result.removeSuffix(word).trim()
                    changed = true
                    break
                }
            }
        }
        return result
    }

    private fun normalizePhoneNumber(value: String): String {
        val trimmed = value.trim()
        val digits = trimmed.filter { it.isDigit() }
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    private fun stripQueryPrefixes(value: String): String {
        var result = value.trim().replace(Regex("\\s+"), " ")
        val prefixes = listOf(
            "โทรหา",
            "โทร",
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

        var changed = true
        while (changed) {
            changed = false
            for (prefix in prefixes) {
                if (result.startsWith(prefix, ignoreCase = true)) {
                    result = result.substring(prefix.length).trim()
                    changed = true
                    break
                }
            }
        }
        return result
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

    private val Int.absoluteValue: Int
        get() = if (this < 0) -this else this
}
