package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.SettingsDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ChatViewModel
import com.example.util.TtsManager

enum class CurrentScreen {
    CHAT,
    EXPLORE
}

class MainActivity : ComponentActivity() {

    private lateinit var ttsManager: TtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ttsManager = TtsManager(this)

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NovaAiApp(ttsManager = ttsManager)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
    }
}

@Composable
fun NovaAiApp(
    ttsManager: TtsManager,
    viewModel: ChatViewModel = viewModel()
) {
    var currentScreen by remember { mutableStateOf(CurrentScreen.CHAT) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val conversations by viewModel.conversations.collectAsState()
    val selectedConversationId by viewModel.selectedConversationId.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val selectedPersona by viewModel.selectedPersona.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val historySearchQuery by viewModel.historySearchQuery.collectAsState()
    val aiSearchState by viewModel.aiSearchState.collectAsState()

    // Handle back button when on Explore screen
    if (currentScreen == CurrentScreen.EXPLORE) {
        BackHandler {
            currentScreen = CurrentScreen.CHAT
        }
    }

    when (currentScreen) {
        CurrentScreen.CHAT -> {
            ChatScreen(
                conversations = conversations,
                selectedConversationId = selectedConversationId,
                messages = messages,
                isGenerating = isGenerating,
                selectedPersona = selectedPersona,
                onSendMessage = { prompt -> viewModel.sendMessage(prompt) },
                onRegenerate = { viewModel.regenerateLastResponse() },
                onStartNewChat = { viewModel.startNewChat() },
                onSelectConversation = { id -> viewModel.selectConversation(id) },
                onDeleteConversation = { id -> viewModel.deleteConversation(id) },
                onClearAllConversations = { viewModel.clearAllConversations() },
                onSearchHistory = { query -> viewModel.setHistorySearchQuery(query) },
                historySearchQuery = historySearchQuery,
                onOpenSearchExplore = { currentScreen = CurrentScreen.EXPLORE },
                onOpenSettings = { showSettingsDialog = true },
                ttsManager = ttsManager,
                modifier = Modifier.fillMaxSize()
            )
        }

        CurrentScreen.EXPLORE -> {
            ExploreScreen(
                searchState = aiSearchState,
                onSearch = { query -> viewModel.performAiSearch(query) },
                onClearSearch = { viewModel.clearAiSearch() },
                onNavigateBack = { currentScreen = CurrentScreen.CHAT },
                onAskInChat = { query ->
                    currentScreen = CurrentScreen.CHAT
                    viewModel.startNewChat("Research: ${query.take(20)}")
                    viewModel.sendMessage("Regarding '$query', can you give me more details and insights?")
                },
                ttsManager = ttsManager,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            selectedPersona = selectedPersona,
            temperature = temperature,
            onPersonaChange = { persona -> viewModel.setPersona(persona) },
            onTemperatureChange = { temp -> viewModel.setTemperature(temp) },
            onDismiss = { showSettingsDialog = false }
        )
    }
}
