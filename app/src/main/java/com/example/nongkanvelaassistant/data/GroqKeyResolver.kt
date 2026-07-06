package com.example.nongkanvelaassistant.data

object GroqKeyResolver {
    fun resolve(keysCsv: String?, fallbackKey: String? = null): List<String> {
        val normalizedCsv = keysCsv.orEmpty().trim()
        if (normalizedCsv.isNotBlank()) {
            return normalizedCsv
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }

        val normalizedFallback = fallbackKey.orEmpty().trim()
        return if (normalizedFallback.isNotBlank()) {
            listOf(normalizedFallback)
        } else {
            emptyList()
        }
    }
}
