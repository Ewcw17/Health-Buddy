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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.terrabull.healthbuddy.api.GeminiApiWrapper
import com.terrabull.healthbuddy.ui.theme.HealthBuddyTheme
import kotlinx.coroutines.launch
import java.io.File
// Added Imports
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HealthBuddyTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RecordingScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun RecordingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var transcription by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Animation state
    val infiniteTransition = rememberInfiniteTransition()
    val rockAngle by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        )
    )

    // where we'll store the recording
    val cacheFile = remember { File(context.cacheDir, "recording.wav") }
    val recorder = remember { PcmWavRecorder(cacheFile) }

    // audio-permission handling
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
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Main horizontal row (image + text/button stack)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Robot Image (left side)
            Image(
                painter = painterResource(id = R.drawable.health_buddy),
                contentDescription = "Health Buddy Robot",
                modifier = Modifier
                    .size(120.dp)
                    .padding(end = 16.dp)
                    .graphicsLayer {
                        rotationZ = rockAngle
                        transformOrigin = TransformOrigin(0.5f, 1f) // Pivot at bottom center
                    },
                contentScale = ContentScale.Fit
            )
            // Vertical stack (text bubble + button)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Text Bubble with matching button color
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.primary, // Matches button color
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        "Hey! I'm Buddy, your Health Assistant. Let's talk! \uD83D\uDE0A",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary // Contrasting text color
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Recording Button (now color-matched with bubble)
                Button(
                    onClick = { if (!isRecording) {
                        // start recording
                        if (!permissionGranted) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@Button
                        }
                        recorder.start()
                        isRecording = true
                        transcription = ""
                    } else {
                        // stop and send to Gemini
                        recorder.stop()
                        isRecording = false

                        scope.launch {
                            transcription = "Thinking…"
                            try {
                                val result = GeminiApiWrapper.sendWavWithHistory(cacheFile, "Your name is Buddy, a helpful assistant." +
                                        "Your goal is to help the user create a regimen for healthy living. " +
                                        "Talk in a casual, friendly, and conversational manner, the same way you would take one on one in real life." +
                                        "Ask one question at a time and wait for the user's response." +
                                        "Before deciding to build a regimen, ask the following questions:" +
                                        "How old is the user? What is their gender? What is their height? What is their weight? What job do they work " +
                                        "(useful for identifying sedentary lifestyles)? and when are they able to commit?" +
                                        "Your goal is not to provide a rigorous regimen but a consistent, healthy and habitual lifestyle." +
                                        "You should aim to take up no more than 20 minutes per day." +
                                        "When building a regimen, talk back and forth with the user to figure out what time and what exercises would be best" +
                                        "for him or her. When you are done, build a schedule by calling a tool. " +
                                        "The format is, {make_workout([workout name], [workout description], [time start (24h)], [time end (24h)], days:[(Mo,Tu,We,Th,Fr,Sa,Su)]}" +
                                        "When you've finished a routine and now all there is is to wait for the workout, you can end the conversation with the tool call" +
                                        "{end_conversation()}" +
                                        "The user is talking to you though a speech-to-text frontend, please keep that in mind.")
                                transcription = result.ifBlank { "No response from API." }
                            } catch (e: Exception) {
                                transcription = "Error: ${e.localizedMessage}"
                            }
                        }
                    } },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isRecording) "Stop Recording" else "Start Recording")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (transcription.isNotEmpty()) {
            Text("Transcription:", style = MaterialTheme.typography.titleMedium)
            Text(transcription)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecordingScreenPreview() {
    HealthBuddyTheme {
        RecordingScreen()
    }
}
