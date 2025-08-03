package com.terrabull.healthbuddy.gemini

import android.util.Log
import com.terrabull.healthbuddy.ChatMessage
import com.terrabull.healthbuddy.api.GeminiApiWrapper
import com.terrabull.healthbuddy.api.GeminiApiWrapper.generateGeminiReply

object LlmTools {
    fun makeWorkout(name: String,
                    desc: String,
                    start: Int,
                    end: Int,
                    days: List<String>){
        Log.d("Gemini", "Tool Call: Workout Started")
        val instruction = "Write a detailed instruction list and summary of the workout."
        val summary = generateGeminiReply(GeminiApiWrapper.inMemoryHistory, instruction)
        GeminiApiWrapper.inMemoryHistory += ChatMessage("workout summary", summary)


    }

    suspend fun endConversation(){
        Log.d("Gemini", "Tool Call: Conversation Ended")
        GeminiApiWrapper.summarizeHistory()
    }
}