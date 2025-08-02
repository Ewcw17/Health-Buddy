package com.terrabull.healthbuddy.model  // or your package name

import java.io.Serializable
import java.util.*

data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable