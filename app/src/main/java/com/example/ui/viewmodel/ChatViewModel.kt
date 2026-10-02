package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.ChatMessageEntity
import com.example.data.db.ConversationEntity
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AiSearchUiState(
    val query: String = "",
    val result: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ChatRepository

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ChatRepository(db.chatDao())
    }

    private val _selectedConversationId = MutableStateFlow<Long?>(null)
    val selectedConversationId: StateFlow<Long?> = _selectedConversationId.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _selectedPersona = MutableStateFlow("assistant")
    val selectedPersona: StateFlow<String> = _selectedPersona.asStateFlow()

    private val _temperature = MutableStateFlow(0.7f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    private val _aiSearchState = MutableStateFlow(AiSearchUiState())
    val aiSearchState: StateFlow<AiSearchUiState> = _aiSearchState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversations: StateFlow<List<ConversationEntity>> = _historySearchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.allConversations
            } else {
                repository.searchConversations(query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentMessages: StateFlow<List<ChatMessageEntity>> = _selectedConversationId
        .flatMapLatest { convId ->
            if (convId != null) {
                repository.getMessages(convId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Automatically start or select initial chat
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(application)
            val initial = db.chatDao().getAllConversations()
            // We let UI handle selecting or creating on first launch
        }
    }

    fun startNewChat(title: String = "New Chat") {
        viewModelScope.launch {
            val newId = repository.createConversation(title, _selectedPersona.value)
            _selectedConversationId.value = newId
        }
    }

    fun selectConversation(id: Long) {
        _selectedConversationId.value = id
        viewModelScope.launch {
            val conv = repository.getConversation(id)
            if (conv != null) {
                _selectedPersona.value = conv.persona
            }
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_selectedConversationId.value == id) {
                _selectedConversationId.value = null
            }
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            repository.deleteAllConversations()
            _selectedConversationId.value = null
        }
    }

    fun setHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
    }

    fun setPersona(persona: String) {
        _selectedPersona.value = persona
    }

    fun setTemperature(temp: Float) {
        _temperature.value = temp
    }

    fun sendMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isBlank() || _isGenerating.value) return

        viewModelScope.launch {
            var convId = _selectedConversationId.value
            if (convId == null) {
                convId = repository.createConversation("New Chat", _selectedPersona.value)
                _selectedConversationId.value = convId
            }

            // 1. Save user message locally
            repository.saveUserMessage(convId, trimmed)

            // 2. Trigger Gemini API in background
            _isGenerating.value = true
            try {
                repository.sendChatMessage(
                    conversationId = convId,
                    persona = _selectedPersona.value,
                    temperature = _temperature.value
                )
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun regenerateLastResponse() {
        val convId = _selectedConversationId.value ?: return
        if (_isGenerating.value) return

        viewModelScope.launch {
            _isGenerating.value = true
            try {
                repository.sendChatMessage(
                    conversationId = convId,
                    persona = _selectedPersona.value,
                    temperature = _temperature.value
                )
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun performAiSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        _aiSearchState.value = AiSearchUiState(query = trimmed, isLoading = true)
        viewModelScope.launch {
            val result = repository.performAiSearchOverview(trimmed)
            result.fold(
                onSuccess = { overview ->
                    _aiSearchState.value = AiSearchUiState(
                        query = trimmed,
                        result = overview,
                        isLoading = false
                    )
                },
                onFailure = { error ->
                    _aiSearchState.value = AiSearchUiState(
                        query = trimmed,
                        result = null,
                        isLoading = false,
                        error = error.message ?: "Failed to generate search overview"
                    )
                }
            )
        }
    }

    fun clearAiSearch() {
        _aiSearchState.value = AiSearchUiState()
    }
}
