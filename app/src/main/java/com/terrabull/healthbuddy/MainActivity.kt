package com.terrabull.healthbuddy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.terrabull.healthbuddy.api.GeminiApiWrapper
import com.terrabull.healthbuddy.ui.theme.HealthBuddyTheme
import kotlinx.coroutines.launch
import java.io.File
import android.os.Build
import android.app.NotificationManager
import android.app.NotificationChannel
import android.util.Log
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import com.terrabull.healthbuddy.api.GoogleTtsPlayer
import java.time.LocalDate
import java.time.LocalTime

// For animations
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

class MainActivity : ComponentActivity() {

    private val CHANNEL_ID = "channel_id_example_01"
    private val notificationId = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel()
        sendNotification()
        setContent {
            HealthBuddyTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RecordingScreen(modifier = Modifier.padding(innerPadding), sendNotification = {sendNotification()})
                }
            }
        }

    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Title"
            val desc = "desc"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = desc
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }


    private fun sendNotification() {

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("YOU HAVENT BEEN SLEEPING ENOUGH!!!")
            .setContentText("Heyyyy uhhh, wouldn't it be cute if you got some sleep?")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(this)) {
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return@with
            }
            notify(notificationId, builder.build())
        }
    }
}


@Composable
fun RecordingScreen(modifier: Modifier = Modifier, sendNotification: () -> Unit) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var displayedText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Chat history state
    val chatHistory = remember { mutableStateListOf<ChatMessage>().apply {
        addAll(ChatHistoryManager.loadChatHistory(context))
    }}



    // Animation for robot
    val infiniteTransition = rememberInfiniteTransition()
    val rockAngle by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Audio setup
    val cacheFile = remember { File(context.cacheDir, "recording.wav") }
    val recorder = remember { PcmWavRecorder(cacheFile) }

    // Permissions
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
    }

    // Add message to history
    fun addMessage(text: String, isFromUser: Boolean) {
        val message = ChatMessage(text, isFromUser)
        chatHistory.add(message)
        // Save after each addition
        scope.launch {
            ChatHistoryManager.saveChatHistory(context, chatHistory)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Chat history display
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Bottom
        ) {
            items(chatHistory) { message ->
                MessageBubble(message)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Main interaction area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color(0xFF4CAF50),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Robot image
                Image(
                    painter = painterResource(id = R.drawable.health_buddy),
                    contentDescription = "Health Buddy",
                    modifier = Modifier
                        .size(120.dp)
                        .padding(end = 16.dp)
                        .graphicsLayer {
                            rotationZ = rockAngle
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        },
                    contentScale = ContentScale.Fit
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Welcome message
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFFA6E253),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            "Hey! I'm Buddy, your Health Assistant. Let's talk! \uD83D\uDE0A",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Black
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Record button
                    Button(
                        onClick = {
                            sendNotification()
                            if (!isRecording) {
                                if (!permissionGranted) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@Button
                                }

                                val hasAlarmPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.SCHEDULE_EXACT_ALARM) == PackageManager.PERMISSION_GRANTED;
                                if(!hasAlarmPermission){
                                    permissionLauncher.launch(Manifest.permission.SCHEDULE_EXACT_ALARM)
                                }

                                recorder.start()
                                isRecording = true
                                displayedText = ""
                            } else {
                                recorder.stop()
                                isRecording = false

                                scope.launch {
                                    displayedText = "Thinking..."
                                    try {
                                        val result = GeminiApiWrapper.sendWavWithHistory(
                                            cacheFile,
                                            getSetupPrompt() // Pass history here
                                        )
                                        addMessage(result.first, true)
                                        displayedText = result.second.ifBlank { "No response" }
                                        addMessage(displayedText, false)
                                        GoogleTtsPlayer.speak(displayedText, context)
                                    } catch (e: Exception) {
                                        displayedText = "Error: ${e.message}"
                                        addMessage(displayedText, false)
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFA6E253),
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isRecording) "Stop" else "Start")
                    }
                }
            }
        }
    }
}

private fun getSetupPrompt(): String {

    return """
        Your name is Buddy, a helpful assistant.
        
        Your goal is to help the user create a regimen for healthy living.
        Talk in a casual, friendly, and conversational manner.
        Your responses should be no more than one or two sentences long.
        Ask one question at a time and wait for the user's response.
        Before deciding to build a regimen, ask:
        - How old is the user?
        - What is their gender?
        - What is their height?
        - What is their weight?
        - What job do they work?
        - When are they able to commit?
        
        Your goal is to create a consistent, healthy lifestyle (max 20 mins/day).
        When building a regimen, determine:
        - Best exercises
        - Optimal times

        Here are the available tools:
        Make a workout with: {make_workout([workout name],[workout description],[start time in 24h format like 1930 or 600],[end time],days:[(Mo,Tu,We,Th,Fr,Sa,Su)])}
        End conversation with: {end_conversation()}
        
        Current context:
        Date: ${LocalDate.now()}
        Time: ${LocalTime.now()}
        Day: ${LocalDate.now().dayOfWeek}
        
        Remember:
        - User interacts via speech-to-text
        - Your responses will be text-to-speech
        - Keep responses concise but natural
    """.trimIndent()
}





@Composable
fun MessageBubble(message: ChatMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(
                color = if (message.isFromUser) Color(0xFFA6E253) else Color(0xFF4CAF50),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        contentAlignment = if (message.isFromUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Text(text = message.text, color = Color.Black)
    }
}

data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
//@Composable
//fun RecordingScreenPreview() {
//    HealthBuddyTheme {
//        RecordingScreen()
//    }
//}

