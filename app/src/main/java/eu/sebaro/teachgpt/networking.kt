package eu.sebaro.teachgpt

import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import kotlin.time.Duration.Companion.seconds

object OpenApi {
    const val API_KEY = ""

    val config = OpenAIConfig(
        token = API_KEY,
        timeout = Timeout(socket = 60.seconds),
    )

    val openAI = OpenAI(config)

    private const val GPT_4 = "gpt-4"
    private const val GPT_3_5_TURBO = "gpt-3.5-turbo"

    fun createChatCompletionRequest(teacher: Teacher, question: String): ChatCompletionRequest {
        return ChatCompletionRequest(
            model = ModelId(GPT_4),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = "${teacher.instructions} .Deine Themengebiete lauten: ${teacher.topic} Du antwortest nur auf Fragen zu deinen Themen. Auf Fragen abseits deines Themas antwortest du nicht."
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = question
                )
            )
        )
    }


    suspend fun makeCall(chatCompletionRequest: ChatCompletionRequest): ChatCompletion {
        return openAI.chatCompletion(chatCompletionRequest)
    }

}


//