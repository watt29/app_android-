package com.example.nongkanvelaassistant.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDateTime
import java.util.UUID

// ─── Data Models ───────────────────────────────────────────────────────────

enum class MemoryCategory(val label: String, val icon: String, val keyword: List<String>) {
    FAMILY(
        label = "ครอบครัว",
        icon = "👨‍👩‍👧",
        keyword = listOf("ลูก", "หลาน", "สามี", "ภรรยา", "พ่อ", "แม่", "พี่", "น้อง",
            "ญาติ", "ครอบครัว", "เบอร์", "โทร", "วันเกิด")
    ),
    HEALTH(
        label = "ยา & สุขภาพ",
        icon = "💊",
        keyword = listOf("ยา", "หมอ", "โรง", "พยาบาล", "นัด", "โรค", "ทาน", "กิน",
            "เม็ด", "ความดัน", "เบาหวาน", "หัวใจ", "สุขภาพ", "ออกกำลัง")
    ),
    STORY(
        label = "เรื่องราว",
        icon = "📖",
        keyword = listOf("ชอบ", "ไม่ชอบ", "ความทรงจำ", "เมื่อก่อน", "เล่า",
            "ดีใจ", "เศร้า", "ประทับใจ", "บันทึก", "วันนี้", "เกิดขึ้น")
    ),
    TASK(
        label = "สิ่งสำคัญ",
        icon = "📅",
        keyword = listOf("ซื้อ", "ทำ", "ต้อง", "อย่าลืม", "ฝาก", "บอก",
            "พรุ่งนี้", "สัปดาห์", "เดือน", "งาน", "นัด", "กุญแจ", "ของ")
    )
}

data class MemoryItem(
    val id: String = UUID.randomUUID().toString(),
    val category: String = MemoryCategory.STORY.name,
    val content: String,
    val createdAt: String = LocalDateTime.now().toString(),
    val tags: List<String> = emptyList()
) {
    fun categoryEnum(): MemoryCategory =
        runCatching { MemoryCategory.valueOf(category) }.getOrDefault(MemoryCategory.STORY)
}

// ─── Storage ────────────────────────────────────────────────────────────────

class MemoryStorage(context: Context) {

    companion object {
        private const val PREFS_NAME = "nongkanvela_memory_prefs"
        private const val KEY_MEMORIES = "memories_json"
        private const val MAX_CONTEXT_CHARS = 1200  // ~400 tokens
    }

    private val gson = Gson()

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    fun addMemory(content: String): MemoryItem {
        val category = autoDetectCategory(content)
        val tags = extractTags(content, category)
        val item = MemoryItem(
            category = category.name,
            content = content.trim(),
            tags = tags
        )
        val list = getAllMemories().toMutableList()
        list.add(0, item)           // newest first
        saveAll(list)
        return item
    }

    fun removeMemory(id: String) {
        val list = getAllMemories().filter { it.id != id }
        saveAll(list)
    }

    fun removeMemoryByContent(keyword: String): Boolean {
        val list = getAllMemories().toMutableList()
        val target = list.firstOrNull {
            it.content.contains(keyword, ignoreCase = true)
        } ?: return false
        list.remove(target)
        saveAll(list)
        return true
    }

    fun getAllMemories(): List<MemoryItem> {
        val json = prefs.getString(KEY_MEMORIES, "[]") ?: "[]"
        return runCatching {
            val type = object : TypeToken<List<MemoryItem>>() {}.type
            gson.fromJson<List<MemoryItem>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun clearAll() = saveAll(emptyList())

    // ── Context for Groq prompt ──────────────────────────────────────────

    /**
     * Returns a compact prompt-ready string of relevant memories.
     * Filters by keyword overlap and caps at MAX_CONTEXT_CHARS.
     */
    fun buildContextPrompt(query: String): String {
        val all = getAllMemories()
        if (all.isEmpty()) return ""

        // Score each memory by keyword overlap with query
        val queryWords = query.lowercase().split(" ", "　").filter { it.length > 1 }
        val scored = all.map { item ->
            val score = queryWords.count { w ->
                item.content.lowercase().contains(w) ||
                item.tags.any { t -> t.lowercase().contains(w) }
            }
            item to score
        }

        // Take top relevant (score > 0) then fill with recent ones
        val relevant = (scored.filter { it.second > 0 }.sortedByDescending { it.second }
            + scored.filter { it.second == 0 })
            .map { it.first }

        val sb = StringBuilder()
        sb.append("ข้อมูลที่ผู้ใช้บอกให้จำ:\n")
        for (item in relevant) {
            val line = "- [${item.categoryEnum().label}] ${item.content}\n"
            if (sb.length + line.length > MAX_CONTEXT_CHARS) break
            sb.append(line)
        }
        return sb.toString().trimEnd()
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun autoDetectCategory(content: String): MemoryCategory {
        val lower = content.lowercase()
        return MemoryCategory.values().maxByOrNull { cat ->
            cat.keyword.count { kw -> lower.contains(kw) }
        } ?: MemoryCategory.STORY
    }

    private fun extractTags(content: String, category: MemoryCategory): List<String> {
        return category.keyword.filter { content.lowercase().contains(it) }.take(5)
    }

    private fun saveAll(list: List<MemoryItem>) {
        prefs.edit().putString(KEY_MEMORIES, gson.toJson(list)).apply()
    }
}
