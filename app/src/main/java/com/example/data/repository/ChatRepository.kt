package com.example.data.repository

import com.example.data.api.GeminiApiClient
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiGenerationConfig
import com.example.data.api.GeminiPart
import com.example.data.api.GeminiRequest
import com.example.data.db.ChatDao
import com.example.data.db.ChatMessageEntity
import com.example.data.db.ConversationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChatRepository(private val chatDao: ChatDao) {

    val allConversations: Flow<List<ConversationEntity>> = chatDao.getAllConversations()

    fun getMessages(conversationId: Long): Flow<List<ChatMessageEntity>> {
        return chatDao.getMessagesForConversation(conversationId)
    }

    suspend fun createConversation(title: String, persona: String = "assistant"): Long {
        return withContext(Dispatchers.IO) {
            val conv = ConversationEntity(
                title = title,
                persona = persona,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            chatDao.insertConversation(conv)
        }
    }

    suspend fun getConversation(id: Long): ConversationEntity? {
        return withContext(Dispatchers.IO) {
            chatDao.getConversationById(id)
        }
    }

    suspend fun updateConversationTitle(id: Long, newTitle: String) {
        withContext(Dispatchers.IO) {
            val conv = chatDao.getConversationById(id)
            if (conv != null) {
                chatDao.updateConversation(conv.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    suspend fun deleteConversation(id: Long) {
        withContext(Dispatchers.IO) {
            chatDao.deleteConversation(id)
        }
    }

    suspend fun deleteAllConversations() {
        withContext(Dispatchers.IO) {
            chatDao.deleteAllConversations()
        }
    }

    fun searchConversations(query: String): Flow<List<ConversationEntity>> {
        return chatDao.searchConversations(query)
    }

    suspend fun saveUserMessage(conversationId: Long, text: String): Long {
        return withContext(Dispatchers.IO) {
            val msg = ChatMessageEntity(
                conversationId = conversationId,
                role = "user",
                content = text,
                timestamp = System.currentTimeMillis()
            )
            val msgId = chatDao.insertMessage(msg)
            val conv = chatDao.getConversationById(conversationId)
            if (conv != null) {
                // If it's the first message and title is "New Chat", auto-generate a smart title from the prompt
                val updatedTitle = if (conv.title == "New Chat" || conv.title.isBlank()) {
                    text.take(30).trim() + if (text.length > 30) "..." else ""
                } else {
                    conv.title
                }
                chatDao.updateConversation(conv.copy(title = updatedTitle, updatedAt = System.currentTimeMillis()))
            }
            msgId
        }
    }

    suspend fun saveModelMessage(conversationId: Long, text: String, isError: Boolean = false): Long {
        return withContext(Dispatchers.IO) {
            val msg = ChatMessageEntity(
                conversationId = conversationId,
                role = "model",
                content = text,
                timestamp = System.currentTimeMillis(),
                isError = isError
            )
            val id = chatDao.insertMessage(msg)
            val conv = chatDao.getConversationById(conversationId)
            if (conv != null) {
                chatDao.updateConversation(conv.copy(updatedAt = System.currentTimeMillis()))
            }
            id
        }
    }

    suspend fun sendChatMessage(
        conversationId: Long,
        persona: String,
        temperature: Float
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = GeminiApiClient.getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please add your GEMINI_API_KEY in AI Studio Secrets panel.")
            )
        }

        try {
            // Load message history from Room
            val pastMessages = chatDao.getMessagesList(conversationId)
            
            // Build conversation turns
            val contents = pastMessages.filter { !it.isError }.map { msg ->
                GeminiContent(
                    role = if (msg.role == "user") "user" else "model",
                    parts = listOf(GeminiPart(text = msg.content))
                )
            }

            if (contents.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No messages to send"))
            }

            val systemPrompt = getSystemInstructionForPersona(persona)
            val request = GeminiRequest(
                contents = contents,
                generationConfig = GeminiGenerationConfig(
                    temperature = temperature,
                    maxOutputTokens = 2048
                ),
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = systemPrompt))
                )
            )

            val response = GeminiApiClient.service.generateContent(
                model = "gemini-3.5-flash",
                apiKey = apiKey,
                request = request
            )

            if (response.error != null) {
                return@withContext Result.failure(
                    Exception(response.error.message ?: "Unknown Gemini API Error (${response.error.code})")
                )
            }

            val candidateText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!candidateText.isNullOrBlank()) {
                // Save model message into Room
                saveModelMessage(conversationId, candidateText)
                Result.success(candidateText)
            } else {
                val blockReason = response.promptFeedback?.blockReason ?: "Response was empty"
                Result.failure(Exception("Could not generate response: $blockReason"))
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Network error connecting to Gemini API"
            saveModelMessage(conversationId, "⚠️ Error: $errorMsg", isError = true)
            Result.failure(e)
        }
    }

    suspend fun performAiSearchOverview(query: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = GeminiApiClient.getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please configure GEMINI_API_KEY in AI Studio.")
            )
        }

        try {
            val systemInstruction = """
                You are Nova AI Search, an intelligent knowledge search engine like Google AI Overviews and ChatGPT.
                Provide a structured, insightful, and accurate overview for the user's query or topic.
                Format your answer with clear markdown:
                - **Key Overview**: A concise summary of the topic
                - **Key Points / Highlights**: Bullet points of essential facts or steps
                - **Deep Dive / Analysis**: Relevant details or context
                - **Hindi/English Friendly**: Understand Hindi/Hinglish questions and answer naturally in clear, easy language.
            """.trimIndent()

            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(GeminiPart(text = query))
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.5f,
                    maxOutputTokens = 2048
                ),
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = systemInstruction))
                )
            )

            val response = GeminiApiClient.service.generateContent(
                model = "gemini-3.5-flash",
                apiKey = apiKey,
                request = request
            )

            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!text.isNullOrBlank()) {
                Result.success(text)
            } else {
                Result.failure(Exception("No overview generated."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getSystemInstructionForPersona(persona: String): String {
        return when (persona) {
            "coding" -> """
                You are a senior software architect and coding expert. Provide clean, efficient, modern code with explanations.
                Highlight best practices, edge cases, and time/space complexity when relevant.
                Always format code blocks with appropriate language tags (e.g. ```kotlin, ```python).
            """.trimIndent()

            "creative" -> """
                You are a creative writer and storyteller. Use vivid language, compelling narratives, and engaging tone.
                Help with storytelling, poetry, marketing copy, and creative ideas.
            """.trimIndent()

            "tutor" -> """
                You are an empathetic bilingual educator and tutor fluent in English and Hindi (Hinglish).
                Explain difficult concepts simply using analogies, step-by-step breakdowns, and encouraging language.
                If the user asks in Hindi or Hinglish, reply with natural, warm Hindi or Hinglish.
            """.trimIndent()

            "concise" -> """
                You are a concise, direct AI assistant. Provide bullet points and actionable answers with zero fluff.
            """.trimIndent()

            else -> """
                You are Nova AI, a helpful, intelligent, and versatile AI assistant powered by Google's Gemini models.
                You can help with reasoning, answering questions, writing, coding, math, translation, and research.
                You are fluent in English, Hindi, and Hinglish. Answer naturally in whatever language the user talks to you.
                Use clean Markdown formatting with bullet points and bold text where helpful.
            """.trimIndent()
        }
    }
}
