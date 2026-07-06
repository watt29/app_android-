package com.example.nongkanvelaassistant.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// ----------------- Models -----------------
data class GroqRequest(
    val model: String = "llama-3.1-8b-instant", // Using a fast model for voice assistant
    val messages: List<GroqMessage>,
    val max_tokens: Int = 300,
    val temperature: Double = 0.7
)

data class GroqMessage(
    val role: String,
    val content: String
)

data class GroqResponse(
    val id: String,
    val choices: List<GroqChoice>
)

data class GroqChoice(
    val message: GroqMessage
)

// ----------------- Interface -----------------
interface GroqApiService {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: GroqRequest
    ): GroqResponse
}

// ----------------- Instance -----------------
object GroqApiClient {
    private const val BASE_URL = "https://api.groq.com/openai/"

    val service: GroqApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqApiService::class.java)
    }
}
