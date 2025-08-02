package com.terrabull.healthbuddy.gemini

import android.util.Log
import com.terrabull.healthbuddy.api.GeminiApiWrapper

object LlmTools {
    fun makeWorkout(name: String,
                    desc: String,
                    start: Int,
                    end: Int,
                    days: List<String>){
        Log.d("Gemini", "Tool Call: Workout Started")
    }

    suspend fun endConversation(){
        Log.d("Gemini", "Tool Call: Conversation Ended")
        GeminiApiWrapper.summarizeHistory()
    }
}