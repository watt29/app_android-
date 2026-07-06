package com.example.nongkanvelaassistant.data

import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractor
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class MlKitEntityHints(
    val refinedText: String,
    val extractedPhone: String? = null,
    val extractedAddress: String? = null,
    val extractedDateTimeText: String? = null,
    val extractedDateTimeMillis: Long? = null
) {
    fun asContextLine(): String? {
        val parts = buildList {
            extractedPhone?.takeIf { it.isNotBlank() }?.let { add("เบอร์=$it") }
            extractedAddress?.takeIf { it.isNotBlank() }?.let { add("สถานที่=$it") }
            extractedDateTimeText?.takeIf { it.isNotBlank() }?.let { add("เวลา=$it") }
        }
        return if (parts.isEmpty()) null else "[ระบบ: ML Kit พบ ${parts.joinToString(", ")}]"
    }
}

class MlKitIntentEntityHelper {
    private val entityExtractor: EntityExtractor by lazy {
        EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.THAI).build()
        )
    }

    @Volatile
    private var modelReady = false

    suspend fun warmUp() {
        ensureModelReady()
    }

    suspend fun extractHints(rawText: String): MlKitEntityHints {
        val cleanText = rawText.trim()
        if (cleanText.isBlank()) return MlKitEntityHints(refinedText = rawText)
        if (!ensureModelReady()) return MlKitEntityHints(refinedText = rawText)

        return try {
            val params = com.google.mlkit.nl.entityextraction.EntityExtractionParams.Builder(cleanText)
                .setReferenceTime(System.currentTimeMillis())
                .setReferenceTimeZone(java.util.TimeZone.getDefault())
                .build()
            val annotations = awaitTask(entityExtractor.annotate(params))
            val phoneText = firstEntityTextOfType(annotations, cleanText, Entity.TYPE_PHONE)
            val addressText = firstEntityTextOfType(annotations, cleanText, Entity.TYPE_ADDRESS)
            val dateAnnotation = firstAnnotationOfType(annotations, Entity.TYPE_DATE_TIME)
            val dateText = dateAnnotation?.let { safeSubstring(cleanText, it.start, it.end) }
            val dateMillis = dateAnnotation
                ?.entities
                ?.firstOrNull { it.type == Entity.TYPE_DATE_TIME }
                ?.asDateTimeEntity()
                ?.timestampMillis

            MlKitEntityHints(
                refinedText = refineCommandText(
                    rawText = cleanText,
                    phoneText = phoneText,
                    addressText = addressText,
                    dateTimeText = dateText,
                    dateTimeMillis = dateMillis
                ),
                extractedPhone = phoneText,
                extractedAddress = addressText,
                extractedDateTimeText = dateText,
                extractedDateTimeMillis = dateMillis
            )
        } catch (_: Exception) {
            MlKitEntityHints(refinedText = rawText)
        }
    }

    private suspend fun ensureModelReady(): Boolean {
        if (modelReady) return true
        return try {
            awaitTask(entityExtractor.downloadModelIfNeeded())
            modelReady = true
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun refineCommandText(
        rawText: String,
        phoneText: String?,
        addressText: String?,
        dateTimeText: String?,
        dateTimeMillis: Long?
    ): String {
        val cleanText = rawText.trim()
        val normalizedText = cleanText.lowercase(Locale.ROOT)

        if ((normalizedText.contains("โทร") || normalizedText.contains("โท")) && !phoneText.isNullOrBlank()) {
            return "โทร $phoneText"
        }

        if (
            (normalizedText.startsWith("ไป") ||
                normalizedText.startsWith("พาไป") ||
                normalizedText.startsWith("นำทาง") ||
                normalizedText.contains("เส้นทาง") ||
                normalizedText.contains("แผนที่")) &&
            !addressText.isNullOrBlank()
        ) {
            return if (normalizedText.startsWith("นำทาง")) {
                "นำทางไป $addressText"
            } else {
                "ไป $addressText"
            }
        }

        if (
            (normalizedText.contains("เตือน") || normalizedText.contains("ปลุก")) &&
            (!dateTimeText.isNullOrBlank() || dateTimeMillis != null)
        ) {
            val canonicalDateTime = buildCanonicalDateTime(dateTimeText, dateTimeMillis)
            if (canonicalDateTime.isNotBlank() && !cleanText.contains(canonicalDateTime)) {
                return "$cleanText $canonicalDateTime".trim()
            }
        }

        return cleanText
    }

    private fun buildCanonicalDateTime(dateTimeText: String?, dateTimeMillis: Long?): String {
        if (!dateTimeText.isNullOrBlank()) return dateTimeText.trim()
        if (dateTimeMillis == null) return ""
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(dateTimeMillis))
    }

    private fun firstEntityTextOfType(
        annotations: List<EntityAnnotation>,
        sourceText: String,
        entityType: Int
    ): String? {
        val annotation = firstAnnotationOfType(annotations, entityType) ?: return null
        return safeSubstring(sourceText, annotation.start, annotation.end)
            ?.takeIf { it.isNotBlank() }
    }

    private fun firstAnnotationOfType(
        annotations: List<EntityAnnotation>,
        entityType: Int
    ): EntityAnnotation? {
        return annotations.firstOrNull { annotation ->
            annotation.entities.any { it.type == entityType }
        }
    }

    private fun safeSubstring(sourceText: String, start: Int, end: Int): String? {
        if (start < 0 || end <= start || end > sourceText.length) return null
        return sourceText.substring(start, end).trim()
    }

    private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { continuation ->
        task.addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }.addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}
