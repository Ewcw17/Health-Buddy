package com.terrabull.healthbuddy

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.*

object ChatHistoryManager {
    private const val FILE_NAME = "chat_history.json"

    private const val BACK_FILE_NAME = "backend_chat_history.json"
    private val gson = Gson()
    private const val MAX_HISTORY_ITEMS = 20000


    fun saveChatHistory(context: Context, history: List<ChatMessage>, isBackend: Boolean = false) {
        try {
            // Keep only the most recent messages
            val recentHistory = if (history.size > MAX_HISTORY_ITEMS) {
                history.takeLast(MAX_HISTORY_ITEMS)
            } else history

            context.openFileOutput(if(isBackend) BACK_FILE_NAME else FILE_NAME, Context.MODE_PRIVATE).use {
                it.write(gson.toJson(recentHistory).toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadChatHistory(context: Context, isBackend: Boolean = false): List<ChatMessage> {
        return try {
            val file = File(context.filesDir, if(isBackend) BACK_FILE_NAME else FILE_NAME)
            if (!file.exists()) return emptyList()

            context.openFileInput(if(isBackend) BACK_FILE_NAME else FILE_NAME).bufferedReader().use { reader ->
                val type = object : TypeToken<List<ChatMessage>>() {}.type
                gson.fromJson(reader, type) ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}