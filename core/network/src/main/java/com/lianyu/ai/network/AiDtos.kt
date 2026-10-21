package com.lianyu.ai.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

import retrofit2.http.Url



interface AnthropicApi {
    @POST
    suspend fun chatCompletion(
        @Url url: String,
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: AnthropicRequest
    ): AnthropicResponse
}



@Serializable
data class Message(
    val role: String,
    val content: String? = null,
    val reasoning_content: String? = null,
    val tool_calls: List<ToolCallRaw>? = null,
    val tool_call_id: String? = null
)

@Serializable
data class ToolCallRaw(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunction
)

@Serializable
data class ToolCallFunction(
    val name: String,
    val arguments: String
)




@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice>? = null,
    val error: ErrorDetail? = null
)

@Serializable
data class Choice(
    val message: Message? = null,
    val delta: Message? = null,
    val finish_reason: String? = null
)

@Serializable
data class ErrorDetail(
    val message: String? = null
)

@Serializable
data class AnthropicRequest(
    val model: String,
    val messages: List<AnthropicMessage>,
    val system: String? = null,
    val max_tokens: Int = 4096,
    val temperature: Float = 0.7f,
    val stream: Boolean = false
)

@Serializable
data class AnthropicMessage(
    val role: String,
    val content: String
)

@Serializable
data class AnthropicResponse(
    val content: List<AnthropicContent>? = null,
    val error: AnthropicError? = null
)

@Serializable
data class AnthropicContent(
    val text: String? = null
)

@Serializable
data class AnthropicError(
    val message: String? = null
)



@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String
)



@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiError? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null
)

@Serializable
data class GeminiError(
    val message: String? = null
)

@Serializable
data class ModelsListResponse(
    val data: List<ModelInfo>? = null,
    val error: ErrorDetail? = null
)

@Serializable
data class ModelInfo(
    val id: String? = null
)
