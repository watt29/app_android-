package com.example.nongkanvelaassistant.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GroqKeyResolverTest {
    @Test
    fun resolve_returnsCsvKeysWhenPresent() {
        assertEquals(
            listOf("gsk_one", "gsk_two", "gsk_three"),
            GroqKeyResolver.resolve(" gsk_one, gsk_two , gsk_three ")
        )
    }

    @Test
    fun resolve_usesFallbackWhenCsvMissing() {
        assertEquals(
            listOf("gsk_fallback"),
            GroqKeyResolver.resolve("", " gsk_fallback ")
        )
    }

    @Test
    fun resolve_returnsEmptyWhenNothingProvided() {
        assertEquals(emptyList<String>(), GroqKeyResolver.resolve(null, " "))
    }
}
