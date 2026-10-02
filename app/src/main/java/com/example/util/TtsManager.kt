package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    var isSpeaking by mutableStateOf(false)
        private set
    var currentSpeakingId by mutableStateOf<Long?>(null)
        private set

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Support English or default system locale
            tts?.language = Locale.getDefault()
        }
    }

    fun speak(id: Long, text: String) {
        if (isSpeaking && currentSpeakingId == id) {
            stop()
            return
        }

        stop()
        val cleanText = text
            .replace(Regex("```[\\s\\S]*?```"), "Code snippet omitted.")
            .replace(Regex("[*#_`>]"), "")
            .trim()

        currentSpeakingId = id
        isSpeaking = true
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "TTS_$id")
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
        currentSpeakingId = null
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
