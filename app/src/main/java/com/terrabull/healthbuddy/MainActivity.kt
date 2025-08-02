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
import androidx.compose.foundation.border
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
        permissionGranted = granted
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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
                // Robot Image (left side)
                Image(
                    painter = painterResource(id = R.drawable.health_buddy),
                    contentDescription = "Health Buddy Robot",
                    modifier = Modifier
                        .size(120.dp)
                        .padding(end = 16.dp)
                        .graphicsLayer {
                            rotationZ = rockAngle
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        },
                    contentScale = ContentScale.Fit
                )

                // Vertical stack (text bubble + button)
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Text Bubble
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
                            color = Color(0xFF000000)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Recording Button
                    Button(
                        onClick = {
                            if (!isRecording) {
                                if (!permissionGranted) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@Button
                                }
                                recorder.start()
                                isRecording = true
                                transcription = ""
                            } else {
                                recorder.stop()
                                isRecording = false
                                scope.launch {
                                    transcription = "Thinking..."
                                    try {
                                        val result = GeminiApiWrapper.sendWavWithHistory(
                                            cacheFile,
                                            """Your name is Buddy, a helpful assistant.
                                            Your goal is to help the user create a regimen for healthy living.
                                            Talk in a casual, friendly, and conversational manner.
                                            Ask one question at a time and wait for the user's response.
                                            Before deciding to build a regimen, ask about:
                                            - Age
                                            - Gender
                                            - Height
                                            - Weight
                                            - Occupation
                                            - Available time commitment"""
                                        )
                                        transcription = result.ifBlank { "No response from API." }
                                    } catch (e: Exception) {
                                        transcription = "Error: ${e.localizedMessage}"
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFA6E253),
                            contentColor = Color(0xFF000000)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isRecording) "Done Talking" else "Start Talking!")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Transcription Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 6.dp,
                    color = Color(0xFF4CAF50),
                    shape = RoundedCornerShape(16.dp)
                )
                .background(
                    color = Color(0xFFA6E253),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            if (transcription.isNotEmpty()) {
                Column {
                    Text(
                        "Buddy says:",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(transcription, color = Color.Black)
                }
            } else {
                Text(
                    "Your conversation will appear here...",
                    color = Color.Black.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}


@Composable
fun RecordingScreenPreview() {
    HealthBuddyTheme {
        RecordingScreen()
    }
}

