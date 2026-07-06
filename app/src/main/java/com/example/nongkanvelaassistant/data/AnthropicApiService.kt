package com.example.nongkanvelaassistant.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// ----------------- Models -----------------
data class AnthropicMessage(
    val role: String,
    val content: String
)

data class AnthropicRequest(
    val model: String = "claude-3-5-sonnet-20241022",
    val max_tokens: Int = 1000,
    val system: String,
    val messages: List<AnthropicMessage>,
    val temperature: Double = 0.5
)

data class AnthropicContent(
    val type: String,
    val text: String
)

data class AnthropicUsage(
    val input_tokens: Int,
    val output_tokens: Int
)

data class AnthropicResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<AnthropicContent>,
    val model: String,
    val stop_reason: String?,
    val stop_sequence: String?,
    val usage: AnthropicUsage
)

// ----------------- Interface -----------------
interface AnthropicApiService {
    @POST("v1/messages")
    suspend fun createMessage(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") anthropicVersion: String = "2023-06-01",
        @Header("content-type") contentType: String = "application/json",
        @Body request: AnthropicRequest
    ): AnthropicResponse
}

// ----------------- Instance -----------------
object AnthropicApiClient {
    private const val BASE_URL = "https://api.anthropic.com/"

    val service: AnthropicApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AnthropicApiService::class.java)
    }
}
