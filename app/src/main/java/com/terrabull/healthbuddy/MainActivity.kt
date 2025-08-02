package com.terrabull.healthbuddy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
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
                    .padding(end = 16.dp),
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
                    onClick = { /* ... existing click handler ... */ },
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
